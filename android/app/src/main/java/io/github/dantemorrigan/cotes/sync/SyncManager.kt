package io.github.dantemorrigan.cotes.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.dantemorrigan.cotes.Note
import io.github.dantemorrigan.cotes.NotesJson
import io.github.dantemorrigan.cotes.NotesRepository
import io.github.dantemorrigan.cotes.R
import io.github.dantemorrigan.cotes.newNoteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Вошедший Google-аккаунт. [avatar] — фото профиля, если удалось скачать. */
data class SyncAccount(val email: String, val name: String, val avatar: Bitmap? = null)

enum class SyncStatus {
    /** Ещё не синхронизировались при этом запуске. */
    Idle,
    Syncing,
    Synced,

    /** Нет сети — изменения уйдут, когда она появится. */
    Offline,

    /** Google отозвал доступ: нужно войти снова. */
    NeedsSignIn,

    /** Проект в Google Cloud настроен не до конца. */
    SetupError,
    Failed,
}

data class SyncState(
    val account: SyncAccount? = null,
    val status: SyncStatus = SyncStatus.Idle,
    val lastSync: Long = 0L,
    /** Доля выполненной работы 0..1, пока идёт большая синхронизация (первый вход); иначе null. */
    val progress: Float? = null,
)

/** Итог входа — для экрана, с которого входили. */
sealed interface SignInResult {
    data object Success : SignInResult
    data object Offline : SignInResult
    data object SetupError : SignInResult
    data class Failed(val error: Throwable) : SignInResult
}

/**
 * Синхронизация заметок с Google Drive. Одна на процесс.
 *
 * Синхронизируется при запуске, через пару секунд после правок и при уходе в фон
 * (тогда — через WorkManager, чтобы изменения ушли, даже если приложение закроют).
 * Без входа в Google ничего не делает и в сеть не ходит.
 */
class SyncManager private constructor(private val context: Context, private val repo: NotesRepository) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val baseFile = AtomicFile(File(context.filesDir, "sync-base.json"))
    private val avatarFile = File(context.filesDir, "avatar.png")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var again = false

    private val _state = MutableStateFlow(SyncState(account = readAccount(), lastSync = prefs.getLong("lastSync", 0L)))
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val signedIn: Boolean get() = _state.value.account != null

    init {
        watchLocalChanges()
    }

    @OptIn(FlowPreview::class)
    private fun watchLocalChanges() {
        scope.launch {
            combine(repo.notes, repo.deleted) { notes, deleted -> notes to deleted }
                .debounce(CHANGE_DELAY_MS)
                .collect { (notes, deleted) ->
                    if (signedIn && SyncMerge.hasLocalChanges(notes, deleted, readBase())) requestSync()
                }
        }
    }

    /**
     * Обновление по жесту «потянуть»: ждёт конца синхронизации (своей или уже идущей) и отдаёт итог.
     * Идёт в области менеджера: если экран закроют, синхронизация не оборвётся.
     */
    suspend fun refresh(): SyncStatus {
        if (!signedIn) return SyncStatus.Idle
        val started = System.currentTimeMillis()
        var status = scope.async { sync() }.await()
        if (status == SyncStatus.Syncing) status = state.first { it.status != SyncStatus.Syncing }.status
        // Индикатор не должен мигнуть и пропасть, даже если облако ответило мгновенно.
        val left = MIN_REFRESH_MS - (System.currentTimeMillis() - started)
        if (left > 0) delay(left)
        return status
    }

    /** Синхронизировать в фоне; повторный вызов во время синхронизации запустит ещё один проход. */
    fun requestSync() {
        if (!signedIn) return
        scope.launch { sync() }
    }

    /** Уходим в фон: если есть неотправленное — отправит WorkManager, как только будет сеть. */
    fun scheduleBackgroundSync() {
        if (!signedIn || !SyncMerge.hasLocalChanges(repo.notes.value, repo.deleted.value, readBase())) return
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    /**
     * Вход: [token] уже получен для [email]. Заметки устройства и облака объединяются —
     * ничего не удаляется ни здесь, ни там.
     */
    // В своей области: закрытие экрана входа не обрывает первую синхронизацию на полпути.
    suspend fun signIn(email: String, token: String): SignInResult = scope.async { doSignIn(email, token) }.await()

    private suspend fun doSignIn(email: String, token: String): SignInResult {
        val profile = try {
            DriveApi(token).profile()
        } catch (e: Exception) {
            return failure(e)
        }
        // Прошлый вход мог быть в другой аккаунт: начинаем сравнение с нуля.
        writeBase(emptyMap())
        val avatar = profile.photoUrl?.let { downloadAvatar(it) }
        prefs.edit()
            .putString("email", email)
            .putString("name", profile.name.ifEmpty { email })
            .remove("lastSync")
            .apply()
        _state.value = SyncState(SyncAccount(email, profile.name.ifEmpty { email }, avatar), SyncStatus.Idle, 0L)
        // Заметки догружаются в фоне и появляются в списке по мере поступления: вход не ждёт сотен запросов.
        scope.launch { sync(token) }
        return SignInResult.Success
    }

    /** Выход: заметки остаются на устройстве, облако не трогаем. */
    fun signOut() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        prefs.edit().clear().apply()
        baseFile.delete()
        avatarFile.delete()
        _state.value = SyncState()
    }

    /** Новый токен после повторного согласия: продолжаем синхронизацию. */
    fun resume(token: String) {
        if (!signedIn) return
        scope.launch { sync(token) }
    }

    /** Один проход синхронизации; [knownToken] — только что полученный при входе. */
    suspend fun sync(knownToken: String? = null): SyncStatus {
        val account = _state.value.account ?: return SyncStatus.Idle
        if (!mutex.tryLock()) {
            // Уже синхронизируемся: после текущего прохода будет ещё один.
            again = true
            return SyncStatus.Syncing
        }
        try {
            again = false
            _state.update { it.copy(status = SyncStatus.Syncing) }
            val status = try {
                val token = knownToken ?: when (val t = GoogleAuth.token(context, account.email)) {
                    is GoogleAuth.Result.Token -> t.token
                    is GoogleAuth.Result.NeedsConsent -> return finish(SyncStatus.NeedsSignIn)
                    is GoogleAuth.Result.Failed -> throw t.error
                }
                try {
                    runPass(DriveApi(token))
                } catch (e: AuthExpiredException) {
                    GoogleAuth.clearToken(context, token)
                    val fresh = GoogleAuth.token(context, account.email)
                    if (fresh !is GoogleAuth.Result.Token) return finish(SyncStatus.NeedsSignIn)
                    runPass(DriveApi(fresh.token))
                }
                SyncStatus.Synced
            } catch (e: Exception) {
                statusFor(e)
            }
            return finish(status)
        } finally {
            mutex.unlock()
            if (again) scope.launch { sync() }
        }
    }

    private fun finish(status: SyncStatus): SyncStatus {
        val now = System.currentTimeMillis()
        if (status == SyncStatus.Synced) prefs.edit().putLong("lastSync", now).apply()
        _state.update {
            it.copy(status = status, lastSync = if (status == SyncStatus.Synced) now else it.lastSync, progress = null)
        }
        return status
    }

    private suspend fun runPass(api: DriveApi) = coroutineScope {
        val remote = api.list()
        val notes = repo.notes.value
        val deleted = repo.deleted.value
        val base = readBase()
        val plan = SyncMerge.plan(notes, deleted, remote, base)
        val seen: Map<String, Long?> = notes.associate { it.id to it.changed }
        val newBase = HashMap(base)
        newBase.putAll(plan.inSync)

        val incoming = ArrayList<Note>()
        val added = ArrayList<Note>()
        val lock = Mutex()
        val limit = Semaphore(PARALLEL)
        var firstError: Exception? = null

        // Для большой работы (первый вход) показываем ход выполнения.
        val total = plan.upload.size + plan.uploadDeleted.size + plan.download.size + plan.conflicts.size
        var done = 0

        suspend fun step(block: suspend () -> Unit) {
            limit.withPermit {
                try {
                    block()
                } catch (e: Exception) {
                    lock.withLock { if (firstError == null) firstError = e }
                }
                lock.withLock {
                    done++
                    if (total >= PROGRESS_FROM) _state.update { it.copy(progress = done.toFloat() / total) }
                    // Скачанные заметки сразу кладём в список, не дожидаясь остальных.
                    if (incoming.size >= APPLY_BATCH) {
                        repo.applySync(incoming.toList(), emptyMap(), emptyList(), seen)
                        incoming.clear()
                    }
                }
            }
        }
        val jobs = ArrayList<Deferred<Unit>>()
        plan.upload.forEach { (note, r) ->
            jobs += async {
                step {
                    api.put(note.id, r?.fileId, note.changed, false, NotesJson.noteToJson(note).toString())
                    lock.withLock { newBase[note.id] = note.changed }
                }
            }
        }
        plan.uploadDeleted.forEach { (pair, r) ->
            val (id, at) = pair
            jobs += async {
                step {
                    api.put(id, r?.fileId, at, true, "{}")
                    lock.withLock { newBase[id] = at }
                }
            }
        }
        plan.download.forEach { r ->
            jobs += async {
                step {
                    val note = parse(api.download(r.fileId), r)
                    lock.withLock {
                        if (note != null) incoming += note
                        newBase[r.noteId] = r.changed
                    }
                }
            }
        }
        plan.conflicts.forEach { c ->
            jobs += async {
                step {
                    val theirs = parse(api.download(c.remote.fileId), c.remote) ?: return@step
                    val mine = c.local
                    val localWins = mine.changed >= theirs.changed
                    if (localWins) api.put(mine.id, c.remote.fileId, mine.changed, false, NotesJson.noteToJson(mine).toString())
                    lock.withLock {
                        if (!localWins) incoming += theirs
                        if (!SyncMerge.sameText(mine, theirs)) added += conflictCopy(if (localWins) theirs else mine)
                        newBase[mine.id] = if (localWins) mine.changed else theirs.changed
                    }
                }
            }
        }
        plan.duplicates.forEach { r -> jobs += async { step { api.delete(r.fileId) } } }
        jobs.awaitAll()

        plan.applyDeleted.forEach { (id, at) -> newBase[id] = at }
        repo.applySync(incoming, plan.applyDeleted, added, seen)

        // Следы удалений старше 90 дней уже дошли до всех устройств — убираем их и здесь, и в облаке.
        val cutoff = System.currentTimeMillis() - FORGET_DELETED_MS
        remote.filter { it.deleted && it.changed < cutoff }.forEach { r ->
            jobs += async { step { api.delete(r.fileId); lock.withLock { newBase.remove(r.noteId) } } }
        }
        jobs.awaitAll()
        val stale = repo.deleted.value.filter { (id, at) -> at < cutoff && newBase[id] == at }.keys
        repo.forgetDeleted(stale)
        stale.forEach { newBase.remove(it) }

        writeBase(newBase)
        firstError?.let { throw it }
    }

    private fun parse(text: String, r: RemoteEntry): Note? =
        NotesJson.decode("[$text]")?.firstOrNull()?.copy(id = r.noteId, changed = r.changed)

    private fun conflictCopy(note: Note): Note {
        val now = System.currentTimeMillis()
        val title = note.displayTitle.ifEmpty { "…" } + " " + context.getString(R.string.sync_conflict_suffix)
        return note.copy(id = newNoteId(), title = title, changed = now)
    }

    private fun failure(e: Exception): SignInResult = when (statusFor(e)) {
        SyncStatus.Offline -> SignInResult.Offline
        SyncStatus.SetupError -> SignInResult.SetupError
        else -> SignInResult.Failed(e)
    }

    private fun statusFor(e: Throwable): SyncStatus = when {
        GoogleAuth.isSetupError(e) -> SyncStatus.SetupError
        GoogleAuth.isNetworkError(e) -> SyncStatus.Offline
        e is DriveException && e.code == 403 && e.reason == "accessNotConfigured" -> SyncStatus.SetupError
        e is AuthExpiredException -> SyncStatus.NeedsSignIn
        e is DriveException -> SyncStatus.Failed
        e is IOException -> SyncStatus.Offline
        else -> SyncStatus.Failed
    }

    private fun readAccount(): SyncAccount? {
        val email = prefs.getString("email", null) ?: return null
        val avatar = runCatching { BitmapFactory.decodeFile(avatarFile.path) }.getOrNull()
        return SyncAccount(email, prefs.getString("name", null) ?: email, avatar)
    }

    private suspend fun downloadAvatar(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            // Ссылки googleusercontent принимают размер в конце: просим 192 точки вместо 64.
            val sized = url.replace(Regex("=s\\d+(-[a-z]+)?$"), "=s192-c")
            val c = URL(sized).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            val bytes = try {
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            avatarFile.writeBytes(bytes)
            bitmap
        }.getOrNull()
    }

    @Synchronized
    private fun readBase(): Map<String, Long> {
        val text = try {
            baseFile.readFully().toString(Charsets.UTF_8)
        } catch (e: FileNotFoundException) {
            return emptyMap()
        } catch (e: IOException) {
            return emptyMap()
        }
        return runCatching {
            val o = JSONObject(text)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    private fun writeBase(base: Map<String, Long>) {
        val o = JSONObject()
        base.toSortedMap().forEach { (k, v) -> o.put(k, v) }
        val out = baseFile.startWrite()
        try {
            out.write(o.toString().toByteArray(Charsets.UTF_8))
            baseFile.finishWrite(out)
        } catch (e: IOException) {
            baseFile.failWrite(out)
        }
    }

    companion object {
        private const val WORK_NAME = "cotes-sync"
        private const val CHANGE_DELAY_MS = 2_500L
        private const val PARALLEL = 8
        private const val PROGRESS_FROM = 12
        private const val APPLY_BATCH = 20
        private const val MIN_REFRESH_MS = 700L
        private const val FORGET_DELETED_MS = 90L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: SyncManager? = null

        fun get(context: Context): SyncManager =
            instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext, NotesRepository.get(context)).also { instance = it }
            }
    }
}

/** Отправляет изменения, когда приложение уже в фоне или закрыто. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (SyncManager.get(applicationContext).sync()) {
        SyncStatus.Offline, SyncStatus.Failed -> Result.retry()
        else -> Result.success()
    }
}
