# TickTick Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Native TickTick tasks + habits in the Kvaesitso fork (calendar widget/search with working checkbox, edit sheet, habits widget, quick-add search action, settings).

**Architecture:** New `data/ticktick` module (Ktor API client, Room tables in the shared `AppDatabase`, repository with optimistic writes, WorkManager sync). Calendar layer gets a `TickTickCalendarProvider` that reads only Room. UI hooks into existing widget/search-action/settings extension points.

**Tech Stack:** Kotlin, Compose M3, Room3, Ktor 3, kotlinx.serialization, WorkManager, Koin.

## Global Constraints
- Open API base `https://api.ticktick.com/open/v1`, header `Authorization: Bearer <personal token>`.
- Dates: `yyyy-MM-dd'T'HH:mm:ssZ` (e.g. `2019-11-13T03:00:00+0000`); habit stamps `YYYYMMDD` ints.
- Priorities: 0 none, 1 low, 3 medium, 5 high. Task status: 0 open, 2 completed.
- Providers called with `allowNetwork=false` must never hit the network.
- Token stored in `EncryptedSharedPreferences` (same pattern as `libs/nextcloud`).
- Build: `nix develop -c ./gradlew :app:app:assembleDefaultDebug`; tests: `nix develop -c ./gradlew :data:ticktick:testDebugUnitTest`.
- No new third-party dependencies.

---

### Task 1: `data/ticktick` module — DTOs, API client, pure logic + tests
**Files:**
- Create: `data/ticktick/build.gradle.kts`, `data/ticktick/src/main/AndroidManifest.xml`
- Create: `.../ticktick/api/TickTickApi.kt` (Ktor client + `@Serializable` DTOs `TaskDto`, `ProjectDto`, `ProjectDataDto`, `HabitDto`, `HabitCheckinDto`, `CheckinEntryDto`)
- Create: `.../ticktick/TickTickDates.kt` — `parseTickTickDate(String?): Long?`, `formatTickTickDate(Long): String`, `stampOf(LocalDate): Int`
- Create: `.../ticktick/QuickAddParser.kt` — `parseQuickAdd(text, today: LocalDate): QuickAdd(title, date: LocalDate?, time: LocalTime?, tags, priority)`
- Create: `.../ticktick/Habits.kt` — `isDueOn(rrule: String?, date: LocalDate): Boolean`, `streak(doneStamps: Set<Int>, today: LocalDate, rrule): Int`
- Test: `data/ticktick/src/test/.../QuickAddParserTest.kt`, `TickTickDatesTest.kt`, `HabitsTest.kt`
- Modify: `settings.gradle.kts` (include `:data:ticktick`)

Tests (JUnit4, already used in repo):
- `"buy milk tomorrow 5pm"` → title `buy milk`, date today+1, time 17:00
- `"call mom friday #family !3"` → next Friday, tags `[family]`, priority 5 (`!1`→1, `!2`→3, `!3`→5)
- `"report in 3 days 17:30"` → today+3, 17:30; `"just text"` → no date
- `parseTickTickDate("2019-11-13T03:00:00+0000")` = 1573614000000; round trip format
- `isDueOn("RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR", Monday)` true, Tuesday false; DAILY always true; null true
- `streak` counts consecutive due days backwards; today not yet done doesn't break streak.

### Task 2: Room storage
**Files:**
- Create in `data/database`: `entities/TickTickEntities.kt` (`TickTickProjectEntity`, `TickTickTaskEntity`, `TickTickHabitEntity`, `TickTickCheckinEntity`), `daos/TickTickDao.kt`, `migrations/Migration_34_35.kt`
- Modify: `AppDatabase.kt` (entities, version 35, `tickTickDao()`, migration)

**Produces:** `TickTickDao` with `tasks(): Flow<List<TickTickTaskEntity>>`, `projects()`, `habits()`, `checkins(from: Int)`, `replaceTasks/Projects/Habits/Checkins(...)` (@Transaction), `upsertTask`, `deleteTask(id)`, `upsertCheckin`, `getTask(id)`.

### Task 3: Repository + sync worker + DI
**Files:** `.../ticktick/TickTickRepository.kt`, `TickTickSyncWorker.kt`, `TickTickTokenStore.kt`, `Module.kt`; modify `app/app/.../LauncherApplication.kt` (koin module + schedule periodic work).

**Produces (`TickTickRepository`):**
```kotlin
val isConfigured: Flow<Boolean>; val syncState: StateFlow<SyncState>
fun tasks(): Flow<List<TickTickTask>>; fun projects(): Flow<List<TickTickProject>>
fun habitsToday(): Flow<List<HabitToday>>
suspend fun sync(): Result<Unit>
suspend fun setCompleted(taskId: String, completed: Boolean): Result<Unit>
suspend fun createTask(title, projectId?, due: Long?, allDay, tags, priority): Result<Unit>
suspend fun updateTask(task: TickTickTask): Result<Unit>; suspend fun deleteTask(id): Result<Unit>
suspend fun checkIn(habitId: String, value: Double): Result<Unit>
fun setToken(token: String?); var defaultProjectId: String?
```

### Task 4: Calendar integration + clickable checkbox
- `CalendarEvent`: add `val canToggleCompleted: Boolean get() = false` and `suspend fun setCompleted(completed: Boolean): Boolean = false`.
- `data/calendar`: `TickTickCalendarProvider` (namespace `ticktick`), `TickTickCalendarEvent` (domain `ticktick`, launch opens `ticktick://` / web), deserializer, wire into `CalendarRepository.search/findMany/getCalendars`, re-emit on DB change.
- `CalendarItem.kt`: status icon clickable when `canToggleCompleted`.
- `CalendarWidgetVM`/favorites `includeTypes`: add `"ticktick"`.
- `CalendarSearchSettingsScreen`/`CalendarProviderSettingsScreen`: TickTick provider switch.

### Task 5: Edit sheet
- `ui/launcher/search/calendar/TickTickEditSheet.kt`; Edit toolbar action in `CalendarItem` for `TickTickCalendarEvent`.

### Task 6: Habits widget
- `data/widgets/HabitsWidget.kt` + registration in `Widget.fromDatabaseEntity`, `WidgetPickerSheet`, `WidgetItem`, `ConfigureWidgetSheet`, `AppWidget.kt` copy branch.
- `ui/launcher/widgets/habits/HabitsWidget.kt` + VM.

### Task 7: Quick-add search action
- `TickTickQuickAddAction` + builder (`key = "ticktick"`), registered in `SearchActionBuilder.from` and builtin list; icon `SearchActionIcon.Note`... new `Task` icon mapping.

### Task 8: Settings screen
- `ui/settings/ticktick/TickTickSettingsScreen.kt` + VM + route in `SettingsActivity`, entry in Integrations.

### Task 9: Build, install-ready APK, update spec status.

### Phase 2 (separate): subscribed calendars via v2 once endpoint captured from the web app.
