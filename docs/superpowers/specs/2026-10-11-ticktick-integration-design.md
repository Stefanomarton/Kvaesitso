# TickTick integration (fork) — design

Date: 2026-10-11 · Branch: `ticktick-integration`

## Goal
Tight TickTick integration built natively into a Kvaesitso fork: see, complete, add and edit
tasks; check in habits; show TickTick-subscribed calendars in the calendar widget.

## Decisions
- **Native integration in the fork** (like the Tasks.org provider), no plugin APK, no IPC.
- **Official Open API** (`https://api.ticktick.com/open/v1`) for tasks, projects **and habits**
  (the API gained habit + check-in endpoints in 2026, which removes the need for v2 for habits).
- **Auth: personal API token** (TickTick web → Settings → Account → API Token), sent as
  `Authorization: Bearer <token>`. Replaces OAuth: no developer app registration, no redirect.
  OAuth can be added later if the fork is ever shared with other users.
- **Unofficial v2 API only for subscribed calendars.** No public client documents this
  endpoint; it is implemented last, after capturing the real request from the TickTick web app.

## Architecture
New module `data/ticktick`:
- `TickTickApi` — Ktor client, Open API endpoints used:
  - `GET /project`, `GET /project/{id}/data` (+ `inbox`) — open tasks
  - `POST /task`, `POST /task/{id}`, `POST /project/{p}/task/{t}/complete`,
    `DELETE /project/{p}/task/{t}`
  - `GET /habit`, `GET /habit/checkins?habitIds&from&to`, `POST /habit/{id}/checkin`
- Room entities in `AppDatabase` (new migration): `ticktick_projects`, `ticktick_tasks`,
  `ticktick_habits`, `ticktick_checkins`.
- `TickTickRepository` — Flows from Room for UI; writes go API → optimistic Room update →
  rollback on failure → resync.
- `TickTickSyncWorker` — periodic (15 min) + on launcher resume + after each write.
  Sources sync independently; each source replaced in one transaction (server is truth).

Changes to existing code:
- `CalendarEvent` gains `suspend fun setCompleted(context, completed): Boolean` (default: unsupported).
  `CalendarItem` makes the status icon clickable when supported.
- `data/calendar`: `TickTickCalendarProvider` + `TickTickCalendarEvent`, reads Room only
  (widget calls with `allowNetwork=false`). Provider id `"ticktick"`.
- `data/widgets`: `HabitsWidget` (+ config: hidden habit ids, hide completed).
- `data/search-actions`: `TickTickQuickAddAction`.
- Settings → Integrations → TickTick: token, last sync/errors, sync now, default list, sign out.

## UI
1. **Checkbox** on task rows in search + calendar widget completes/reopens.
2. **Edit sheet** from task details toolbar: title, notes, due date/time, all-day, priority
   (0/1/3/5), list, delete (confirm), open in TickTick.
3. **Habits widget**: today's due habits, tap to check in (Boolean toggles, count habits +step),
   streak, 7-day strip.
4. **Quick add**: search action "Add to TickTick" with a small date parser
   (today/tomorrow/weekday/in N days, `5pm`/`17:00`), `#tag`, `!1-3` priority. Unparsed → no date.
5. **Subscribed calendars**: read-only events, one calendar list each (phase 2).

## Errors
- 401 → source marked "token invalid", surfaced in settings; widget keeps cached data.
- Failed write → rollback + toast; no offline queue (ponytail: add one if offline use matters).
- Reopening a completed task: `POST /task/{id}` with `status: 0` (no dedicated endpoint;
  verify against live account).

## Testing
JVM unit tests: quick-add parser, DTO→entity mapping (dates, all-day), streak calculation.
Build: `nix develop -c ./gradlew :app:app:assembleDefaultDebug`.

## Out of scope (v1)
Offline write queue, subtasks/checklist editing, reminders, OAuth, focus/pomodoro.
