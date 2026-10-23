// Cotes: список заметок и редактор на одной странице.
// Данные — массив в localStorage['notes'] (формат совместим со старыми версиями:
// у каждой заметки по-прежнему есть title и content).
(function () {
  'use strict';

  var KEY = 'notes';
  var SAVE_DELAY = 400;
  var TOAST_MS = 6000;
  var MOBILE = window.matchMedia('(max-width: 760px)');
  var IS_MAC = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);

  var $ = function (id) { return document.getElementById(id); };
  var app = $('app');
  var list = $('note-list');
  var search = $('search');
  var titleField = $('title');
  var textField = $('text');
  var statusEl = $('save-status');

  var notes = [];           // все заметки, включая ещё не сохранённый черновик
  var drafts = {};          // id заметок, которых пока нет в хранилище
  var activeId = null;
  var query = '';
  var saveTimer = null;
  var dirty = false;
  var openedFromList = false;

  // ---------- Хранилище ----------

  function uid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  function normalize(raw, fallbackTime) {
    if (!raw || typeof raw !== 'object') return null;
    var created = Number(raw.created) || fallbackTime;
    return {
      id: typeof raw.id === 'string' && raw.id ? raw.id : uid(),
      title: typeof raw.title === 'string' ? raw.title : '',
      content: typeof raw.content === 'string' ? raw.content : '',
      created: created,
      updated: Number(raw.updated) || created,
      pinned: raw.pinned === true
    };
  }

  function readStored() {
    var raw;
    try { raw = JSON.parse(localStorage.getItem(KEY) || '[]'); } catch (e) { raw = []; }
    if (!Array.isArray(raw)) raw = [];
    var now = Date.now();
    var seen = {};
    var out = [];
    var migrated = false;
    raw.forEach(function (item, i) {
      // Старые заметки без дат: чем позже добавлена, тем «новее».
      var note = normalize(item, now - (raw.length - i) * 1000);
      if (!note) { migrated = true; return; }
      if (seen[note.id]) note.id = uid();
      seen[note.id] = true;
      if (!item.id || item.id !== note.id || !item.updated) migrated = true;
      out.push(note);
    });
    if (migrated) writeStored(out);
    return out;
  }

  function writeStored(arr) {
    try {
      localStorage.setItem(KEY, JSON.stringify(arr));
      return true;
    } catch (e) {
      return false;
    }
  }

  // Каждая запись перечитывает хранилище, чтобы не затереть заметки из другой вкладки.
  function persist(note) {
    var stored = readStored();
    var i = indexById(stored, note.id);
    if (i === -1) stored.push(copy(note)); else stored[i] = copy(note);
    var ok = writeStored(stored);
    if (ok) delete drafts[note.id];
    return ok;
  }

  function unpersist(id) {
    return writeStored(readStored().filter(function (n) { return n.id !== id; }));
  }

  function copy(n) {
    return { id: n.id, title: n.title, content: n.content, created: n.created, updated: n.updated, pinned: n.pinned };
  }

  function indexById(arr, id) {
    for (var i = 0; i < arr.length; i++) if (arr[i].id === id) return i;
    return -1;
  }

  function find(id) {
    var i = indexById(notes, id);
    return i === -1 ? null : notes[i];
  }

  function isBlank(n) {
    return !n.title.trim() && !n.content.trim();
  }

  // ---------- Текст и даты ----------

  function plural(n, forms) {
    var a = Math.abs(n) % 100, b = a % 10;
    if (a > 10 && a < 20) return forms[2];
    if (b > 1 && b < 5) return forms[1];
    if (b === 1) return forms[0];
    return forms[2];
  }

  function firstLine(text) {
    var m = text.match(/^\s*(.+)/);
    return m ? m[1].trim() : '';
  }

  function displayTitle(n) {
    return n.title.trim() || firstLine(n.content);
  }

  var timeFmt = new Intl.DateTimeFormat('ru', { hour: '2-digit', minute: '2-digit' });
  var dayFmt = new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'short' });
  var fullFmt = new Intl.DateTimeFormat('ru', { day: 'numeric', month: 'short', year: 'numeric' });

  function dayStart(offsetDays) {
    var d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - offsetDays);
    return d.getTime();
  }

  function shortDate(ts) {
    if (ts >= dayStart(0)) return timeFmt.format(ts);
    if (ts >= dayStart(1)) return 'вчера';
    if (new Date(ts).getFullYear() === new Date().getFullYear()) return dayFmt.format(ts);
    return fullFmt.format(ts);
  }

  function longDate(ts) {
    var time = timeFmt.format(ts);
    if (ts >= dayStart(0)) return 'сегодня в ' + time;
    if (ts >= dayStart(1)) return 'вчера в ' + time;
    return fullFmt.format(ts) + ' в ' + time;
  }

  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function queryRegExp() {
    return query ? new RegExp(escapeRegExp(query), 'giu') : null;
  }

  // Текст с подсветкой совпадений: только textContent и <mark>, без innerHTML.
  function appendHighlighted(el, text, re) {
    if (!re) { el.textContent = text; return; }
    var last = 0;
    re.lastIndex = 0;
    var m;
    while ((m = re.exec(text)) !== null) {
      if (m.index > last) el.appendChild(document.createTextNode(text.slice(last, m.index)));
      var mark = document.createElement('mark');
      mark.textContent = m[0];
      el.appendChild(mark);
      last = m.index + m[0].length;
    }
    if (last < text.length) el.appendChild(document.createTextNode(text.slice(last)));
  }

  function previewText(n, re) {
    var body = n.content;
    if (!n.title.trim()) body = body.replace(/^\s*.+/, '');
    body = body.replace(/\s+/g, ' ').trim();
    if (re) {
      re.lastIndex = 0;
      var m = re.exec(body);
      if (m && m.index > 40) body = '…' + body.slice(m.index - 30);
    }
    return body.slice(0, 160);
  }

  // ---------- Список ----------

  function visibleNotes() {
    var re = queryRegExp();
    return notes
      .filter(function (n) {
        if (!re) return true;
        re.lastIndex = 0;
        if (re.test(n.title)) return true;
        re.lastIndex = 0;
        return re.test(n.content);
      })
      .sort(function (a, b) {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
        return b.updated - a.updated;
      });
  }

  function renderList() {
    var re = queryRegExp();
    var shown = visibleNotes();
    var focusedId = document.activeElement && document.activeElement.dataset
      ? document.activeElement.dataset.id : null;

    list.textContent = '';
    shown.forEach(function (n) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'note-item';
      btn.dataset.id = n.id;
      if (n.id === activeId) btn.setAttribute('aria-current', 'true');

      var title = document.createElement('span');
      title.className = 'note-item__title';
      var t = displayTitle(n);
      if (t) appendHighlighted(title, t, re);
      else { title.textContent = drafts[n.id] ? 'Новая заметка' : 'Без названия'; title.classList.add('is-untitled'); }
      btn.appendChild(title);

      var pv = previewText(n, re);
      if (pv) {
        var p = document.createElement('span');
        p.className = 'note-item__preview';
        appendHighlighted(p, pv, re);
        btn.appendChild(p);
      }

      var meta = document.createElement('span');
      meta.className = 'note-item__meta';
      if (n.pinned) {
        meta.insertAdjacentHTML('beforeend', '<svg class="icon" aria-hidden="true"><use href="#i-pin"/></svg>');
        btn.setAttribute('aria-label', 'Закреплена: ' + (t || 'Без названия'));
      }
      meta.appendChild(document.createTextNode(shortDate(n.updated)));
      btn.appendChild(meta);

      li.appendChild(btn);
      list.appendChild(li);
    });

    if (focusedId) {
      var again = list.querySelector('[data-id="' + CSS.escape(focusedId) + '"]');
      if (again) again.focus();
    }

    var total = notes.filter(function (n) { return !drafts[n.id]; }).length;
    $('empty-none').hidden = total > 0 || !!query || notes.length > 0;
    $('empty-search').hidden = !query || shown.length > 0;
    $('empty-search-text').textContent = 'По запросу «' + query + '» заметок нет.';
    list.hidden = shown.length === 0;

    $('count').textContent = query
      ? 'Найдено: ' + shown.length
      : total ? total + ' ' + plural(total, ['заметка', 'заметки', 'заметок']) : '';
  }

  // ---------- Редактор ----------

  function setStatus(text, isError) {
    statusEl.textContent = text;
    statusEl.classList.toggle('is-error', !!isError);
  }

  function renderMeta() {
    var n = find(activeId);
    if (!n) return;
    $('meta-date').textContent = drafts[n.id] ? 'Новая заметка' : 'Изменено ' + longDate(n.updated);
    var words = (n.content.match(/\S+/g) || []).length;
    var chars = n.content.length;
    $('meta-count').textContent = words + ' ' + plural(words, ['слово', 'слова', 'слов']) +
      ' · ' + chars + ' ' + plural(chars, ['символ', 'символа', 'символов']);
    var pin = $('pin');
    pin.setAttribute('aria-pressed', String(n.pinned));
    pin.setAttribute('aria-label', n.pinned ? 'Открепить' : 'Закрепить');
    pin.title = pin.getAttribute('aria-label');
  }

  function renderPane() {
    var n = find(activeId);
    app.classList.toggle('is-editing', !!n);
    $('editor').hidden = !n;
    $('statusbar').hidden = !n;
    $('empty-pane').hidden = !!n;
    var noteButtons = document.querySelectorAll('[data-note-action]');
    for (var i = 0; i < noteButtons.length; i++) noteButtons[i].disabled = !n;
    if (!n) { setStatus(''); return; }
    if (titleField.value !== n.title) titleField.value = n.title;
    if (textField.value !== n.content) textField.value = n.content;
    renderMeta();
  }

  function scheduleSave() {
    dirty = true;
    setStatus('Сохранение…');
    clearTimeout(saveTimer);
    saveTimer = setTimeout(flush, SAVE_DELAY);
  }

  function flush() {
    clearTimeout(saveTimer);
    if (!dirty) return true;
    dirty = false;
    var n = find(activeId);
    if (!n) return true;
    var ok;
    if (isBlank(n)) {
      // Пустую заметку не храним; в списке она остаётся как черновик, пока открыта.
      ok = drafts[n.id] ? true : unpersist(n.id);
      if (ok) drafts[n.id] = true;
    } else {
      ok = persist(n);
    }
    if (ok) setStatus('Сохранено');
    else setStatus('Не сохранено: хранилище браузера заполнено', true);
    if (!ok) toast('Не удалось сохранить. Освободите место: скачайте копию и удалите лишнее.', null, true);
    renderMeta();
    return ok;
  }

  function onEdit() {
    var n = find(activeId);
    if (!n) return;
    n.title = titleField.value;
    n.content = textField.value;
    n.updated = Date.now();
    scheduleSave();
    renderList();
    renderMeta();
  }

  // Уходя с заметки: сохранить и выбросить пустой черновик.
  function leaveActive() {
    flush();
    var n = find(activeId);
    if (n && isBlank(n)) {
      notes.splice(indexById(notes, n.id), 1);
      if (!drafts[n.id]) unpersist(n.id);
      delete drafts[n.id];
    }
  }

  function open(id) {
    if (id === activeId && find(id)) return;
    leaveActive();
    activeId = find(id) ? id : null;
    setStatus('');
    renderList();
    renderPane();
  }

  function createNote() {
    var current = find(activeId);
    if (current && isBlank(current)) return current.id;
    var now = Date.now();
    var n = { id: uid(), title: '', content: '', created: now, updated: now, pinned: false };
    notes.push(n);
    drafts[n.id] = true;
    return n.id;
  }

  // ---------- Навигация (#id в адресе) ----------

  function go(id, replace) {
    var url = location.pathname + (id ? '#' + encodeURIComponent(id) : '');
    try {
      if (replace) history.replaceState(null, '', url);
      else history.pushState(null, '', url);
    } catch (e) {
      // file:// в некоторых браузерах не даёт менять историю — переходим по якорю.
      location.hash = id || '';
      return;
    }
    route();
  }

  // Меняет адрес без перехода (при старте, до первой отрисовки).
  function setUrl(id) {
    try { history.replaceState(null, '', location.pathname + (id ? '#' + encodeURIComponent(id) : '')); }
    catch (e) { location.hash = id || ''; }
  }

  function route() {
    var hash = decodeURIComponent(location.hash.slice(1));
    if (hash === 'new') {
      var id = createNote();
      open(id);
      go(id, true);
      titleField.focus();
      return;
    }
    if (hash && !find(hash)) {
      toast('Заметка не найдена — возможно, она удалена.');
      go('', true);
      return;
    }
    open(hash || null);
  }

  function openFromList(id) {
    openedFromList = true;
    go(id, !!activeId && !MOBILE.matches);
  }

  function backToList() {
    if (openedFromList && history.length > 1) {
      openedFromList = false;
      history.back();
    } else {
      go('', false);
    }
  }

  function newNote() {
    var id = createNote();
    if (id !== activeId) {
      openedFromList = true;
      go(id, false);
    }
    titleField.focus();
  }

  // ---------- Действия ----------

  var undo = null;

  function deleteActive() {
    var n = find(activeId);
    if (!n) return;
    clearTimeout(saveTimer);
    dirty = false;
    var wasDraft = !!drafts[n.id];
    var order = visibleNotes();
    var pos = indexById(order, n.id);
    var next = order[pos + 1] || order[pos - 1];

    notes.splice(indexById(notes, n.id), 1);
    delete drafts[n.id];
    if (!wasDraft) unpersist(n.id);
    activeId = null;

    if (!wasDraft && !isBlank(n)) {
      undo = copy(n);
      toast('Заметка удалена', 'Отменить', false, function () {
        if (!undo) return;
        var restored = undo;
        undo = null;
        notes.push(restored);
        persist(restored);
        go(restored.id, !MOBILE.matches);
      });
    }
    if (next && !MOBILE.matches) go(next.id, true);
    else go('', true);
  }

  function togglePin() {
    var n = find(activeId);
    if (!n) return;
    n.pinned = !n.pinned;
    if (!isBlank(n)) persist(n);
    renderList();
    renderMeta();
  }

  function download(name, type, text) {
    var blob = new Blob([text], { type: type });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
  }

  function safeFileName(s) {
    return (s.replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ').trim().slice(0, 80) || 'Заметка');
  }

  function exportTxt() {
    var n = find(activeId);
    if (!n) return;
    flush();
    var t = displayTitle(n);
    var body = n.title.trim() ? n.title.trim() + '\n\n' + n.content : n.content;
    download(safeFileName(t) + '.txt', 'text/plain;charset=utf-8', body);
  }

  function exportJson() {
    flush();
    var stored = readStored();
    var stamp = new Date().toISOString().slice(0, 10);
    var data = { app: 'Cotes', version: 1, exported: new Date().toISOString(), notes: stored };
    download('cotes-' + stamp + '.json', 'application/json', JSON.stringify(data, null, 2));
    toast('Скачана копия: ' + stored.length + ' ' + plural(stored.length, ['заметка', 'заметки', 'заметок']));
  }

  function importJson(file) {
    var reader = new FileReader();
    reader.onload = function () {
      var data;
      try { data = JSON.parse(reader.result); } catch (e) { data = null; }
      var incoming = Array.isArray(data) ? data : data && Array.isArray(data.notes) ? data.notes : null;
      if (!incoming) { toast('Это не похоже на копию Cotes.', null, true); return; }

      flush();
      var stored = readStored();
      var byId = {};
      stored.forEach(function (n) { byId[n.id] = n; });
      var added = 0;
      var now = Date.now();
      incoming.forEach(function (raw, i) {
        var n = normalize(raw, now - (incoming.length - i) * 1000);
        if (!n || isBlank(n)) return;
        var same = byId[n.id];
        if (same && same.title === n.title && same.content === n.content) return;
        if (same) n.id = uid();
        byId[n.id] = n;
        stored.push(n);
        added++;
      });
      if (!writeStored(stored)) { toast('Не хватило места в хранилище браузера.', null, true); return; }
      syncFromStorage();
      toast(added
        ? 'Восстановлено: ' + added + ' ' + plural(added, ['заметка', 'заметки', 'заметок'])
        : 'Все заметки из файла уже есть.');
    };
    reader.onerror = function () { toast('Не удалось прочитать файл.', null, true); };
    reader.readAsText(file);
  }

  // Изменения из другой вкладки.
  function syncFromStorage() {
    var stored = readStored();
    var local = find(activeId);
    var keep = notes.filter(function (n) { return drafts[n.id] || (dirty && n.id === activeId); });
    notes = stored.filter(function (n) { return indexById(keep, n.id) === -1; }).concat(keep);

    if (local && !find(activeId)) {
      activeId = null;
      toast('Заметку удалили в другой вкладке.');
      go('', true);
    } else if (local && !dirty) {
      var editing = document.activeElement === titleField || document.activeElement === textField;
      if (!editing) renderPane();
    }
    renderList();
    renderMeta();
  }

  // ---------- Тост ----------

  var toastTimer = null;
  var toastHandler = null;

  function toast(text, actionLabel, isError, handler) {
    var el = $('toast');
    $('toast-text').textContent = text;
    $('toast-action').textContent = actionLabel || '';
    toastHandler = handler || null;
    el.classList.toggle('is-error', !!isError);
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(hideToast, TOAST_MS);
  }

  function hideToast() {
    $('toast').hidden = true;
    toastHandler = null;
  }

  // ---------- Меню ----------

  function setMenu(open) {
    $('menu').hidden = !open;
    $('menu-button').setAttribute('aria-expanded', String(open));
    if (open) {
      var first = $('menu').querySelector('.menu__item:not(:disabled)');
      if (first) first.focus();
    }
  }

  // ---------- События ----------

  function bind() {
    var mod = IS_MAC ? '⌘' : 'Ctrl';
    var kbds = document.querySelectorAll('[data-mod]');
    for (var i = 0; i < kbds.length; i++) kbds[i].textContent = kbds[i].textContent.replace('Ctrl', mod);
    var withShortcut = document.querySelectorAll('[data-shortcut]');
    for (var j = 0; j < withShortcut.length; j++) {
      var b = withShortcut[j];
      b.title = b.getAttribute('aria-label') + ' (' + b.dataset.shortcut.replace('Ctrl', mod) + ')';
    }

    titleField.addEventListener('input', onEdit);
    textField.addEventListener('input', onEdit);
    titleField.addEventListener('blur', flush);
    textField.addEventListener('blur', flush);
    titleField.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || (e.key === 'ArrowDown' && titleField.selectionStart === titleField.value.length)) {
        e.preventDefault();
        textField.focus();
        textField.setSelectionRange(0, 0);
      }
    });

    list.addEventListener('click', function (e) {
      var btn = e.target.closest('.note-item');
      if (btn) openFromList(btn.dataset.id);
    });
    list.addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
      var items = Array.prototype.slice.call(list.querySelectorAll('.note-item'));
      var i = items.indexOf(document.activeElement);
      var next = items[i + (e.key === 'ArrowDown' ? 1 : -1)];
      if (next) { e.preventDefault(); next.focus(); }
    });

    search.addEventListener('input', function () {
      query = search.value.trim();
      $('search-box').classList.toggle('has-value', search.value.length > 0);
      $('search-clear').hidden = search.value.length === 0;
      renderList();
    });
    search.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') {
        e.preventDefault();
        if (search.value) clearSearch(); else search.blur();
      } else if (e.key === 'ArrowDown' || e.key === 'Enter') {
        var first = list.querySelector('.note-item');
        if (first) {
          e.preventDefault();
          if (e.key === 'Enter') openFromList(first.dataset.id); else first.focus();
        }
      }
    });
    $('search-clear').addEventListener('click', function () { clearSearch(); search.focus(); });

    $('new-note').addEventListener('click', newNote);
    $('back').addEventListener('click', backToList);
    $('pin').addEventListener('click', togglePin);
    $('delete').addEventListener('click', deleteActive);
    $('focus-toggle').addEventListener('click', toggleFocus);

    $('menu-button').addEventListener('click', function () { setMenu($('menu').hidden); });
    $('menu').addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
      var items = Array.prototype.slice.call($('menu').querySelectorAll('.menu__item:not(:disabled)'));
      var i = items.indexOf(document.activeElement);
      var next = items[(i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length];
      e.preventDefault();
      next.focus();
    });
    document.addEventListener('click', function (e) {
      if (!$('menu').hidden && !e.target.closest('.menu')) setMenu(false);
      var actionEl = e.target.closest('[data-action]');
      if (!actionEl || actionEl.disabled) return;
      var action = actionEl.dataset.action;
      if (actionEl.closest('.menu')) setMenu(false);
      if (action === 'new') newNote();
      else if (action === 'clear-search') { clearSearch(); search.focus(); }
      else if (action === 'export-txt') exportTxt();
      else if (action === 'export-json') exportJson();
      else if (action === 'import') $('import-file').click();
    });
    $('import-file').addEventListener('change', function () {
      if (this.files && this.files[0]) importJson(this.files[0]);
      this.value = '';
    });

    $('toast-action').addEventListener('click', function () {
      var h = toastHandler;
      hideToast();
      if (h) h();
    });

    document.addEventListener('keydown', function (e) {
      var mod = e.metaKey || e.ctrlKey;
      var inField = /^(INPUT|TEXTAREA)$/.test(e.target.tagName);
      if (e.altKey && e.code === 'KeyN') { e.preventDefault(); newNote(); }
      else if (mod && e.code === 'KeyK') { e.preventDefault(); focusSearch(); }
      else if (mod && e.code === 'KeyS') { e.preventDefault(); dirty = dirty || !!find(activeId); flush(); }
      else if (mod && e.code === 'Backslash') { e.preventDefault(); toggleFocus(); }
      else if (e.key === '/' && !inField && !mod) { e.preventDefault(); focusSearch(); }
      else if (e.key === 'Escape' && !$('menu').hidden) { setMenu(false); $('menu-button').focus(); }
    });

    window.addEventListener('hashchange', route);
    window.addEventListener('popstate', route);
    window.addEventListener('storage', function (e) {
      if (e.key === KEY || e.key === null) syncFromStorage();
    });
    window.addEventListener('pagehide', flush);
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'hidden') flush();
    });
    window.addEventListener('beforeunload', function (e) {
      if (dirty && !flush()) { e.preventDefault(); e.returnValue = ''; }
    });
  }

  function clearSearch() {
    search.value = '';
    query = '';
    $('search-box').classList.remove('has-value');
    $('search-clear').hidden = true;
    renderList();
  }

  function focusSearch() {
    if (app.classList.contains('is-focus')) toggleFocus();
    if (MOBILE.matches && activeId) backToList();
    search.focus();
    search.select();
  }

  function toggleFocus() {
    var on = app.classList.toggle('is-focus');
    var btn = $('focus-toggle');
    btn.setAttribute('aria-pressed', String(on));
    btn.setAttribute('aria-label', on ? 'Показать список' : 'Скрыть список');
    btn.title = btn.getAttribute('aria-label') + ' (' + (IS_MAC ? '⌘' : 'Ctrl') + '+\\)';
  }

  // ---------- Старт ----------

  function start() {
    notes = readStored();
    bind();

    // Старые ссылки вида editor.html?index=N ведут сюда с тем же параметром.
    var legacy = new URLSearchParams(location.search).get('index');
    if (legacy !== null) {
      var target = notes[Number(legacy)];
      setUrl(target ? target.id : '');
      if (!target) toast('Заметка не найдена — возможно, она удалена.');
    }

    // На широком экране сразу открываем последнюю заметку.
    if (!location.hash && !MOBILE.matches) {
      var latest = visibleNotes()[0];
      if (latest) setUrl(latest.id);
    }

    renderList();
    route();
  }

  start();
})();
