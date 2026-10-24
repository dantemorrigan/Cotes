# R8: org.json — системный класс, Compose приносит свои правила.
# WorkManager создаёт свою базу Room через рефлексию: без этого правила R8 убирает конструктор
# и релизная сборка падает при запуске (NoSuchMethodException: WorkDatabase_Impl.<init>).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
