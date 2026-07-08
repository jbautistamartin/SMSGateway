# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run commands

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Release build (requires keystore — see .gitignore for expected file names)
./gradlew assembleRelease

# Unit tests
./gradlew test

# Single test class
./gradlew test --tests "com.capicua.smsgateway.data.repository.SmsRepositoryTest"

# Grant runtime permissions on a dedicated device (run after install)
adb shell pm grant com.capicua.smsgateway android.permission.RECEIVE_SMS
adb shell pm grant com.capicua.smsgateway android.permission.READ_SMS
adb shell pm grant com.capicua.smsgateway android.permission.POST_NOTIFICATIONS
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway

# Real-time log monitoring
adb logcat -s "SmsReceiver" "SmsIngestionService" "SmsDispatchWorker" "HealthMonitorWorker"
```

## Architecture

Clean Architecture with strict 3-layer separation and MVVM in the presentation layer. Dependency injection throughout via Hilt.

```
Presentation  (Fragments + ViewModels)
    ↓ uses
Domain        (UseCases + pure Kotlin models)
    ↓ uses interfaces, implemented by
Data          (Room · OkHttp · DataStore · WorkManager)
```

### SMS lifecycle

```
SIM → SmsReceiver.onReceive()           ← BroadcastReceiver (main thread, < 10 s)
        ↓ startForegroundService()
      SmsIngestionService               ← ForegroundService (must call startForeground() < 5 s)
        ↓ SaveSmsUseCase
      Room INSERT (SmsEntity)           ← outbox: persists BEFORE dispatching
        ↓ WorkManager.enqueueUniqueWork()
      SmsDispatchWorker.doWork()        ← runs when network is available, with exponential backoff
        ↓ OkHttp GET <url_from_template>
      HTTP response → update Room
```

`HealthMonitorWorker` runs every 15 minutes to re-enqueue orphaned SMS (no active WorkManager task) and purge old records (SMS > 7 days, logs > 30 days).

`BootReceiver` re-enables WorkManager tasks after device restart.

### HTTP mechanism

The app makes a plain **GET** request. There is no fixed endpoint or JSON body. The operator configures a full URL **template** in the Settings screen, with optional markers that get URL-encoded and substituted at send time:

- `{mensaje}` — SMS body (**required** in the template)
- `{telefono}` — sender number
- `{fecha}` — ISO-8601 UTC timestamp

`AppConfig.construirUrl()` performs the substitution. `AppConfig.esValida()` returns false if the template is blank or missing `{mensaje}`. Authentication (if needed) goes inside the URL itself (e.g. `?token=secret&msg={mensaje}`).

### Key files

| File | Role |
|------|------|
| `data/config/AppConfig.kt` | Config data class + `construirUrl()` + `esValida()` |
| `data/config/ConfigDataStore.kt` | DataStore wrapper — reactive reads, atomic writes |
| `worker/SmsDispatchWorker.kt` | Sends one SMS; handles retry/failure logic |
| `worker/HealthMonitorWorker.kt` | Periodic cleanup + re-enqueue of stuck SMS |
| `util/HttpClientFactory.kt` | Derives an OkHttpClient from the base (timeout + optional trust-all SSL) |
| `di/NetworkModule.kt` | Provides base OkHttpClient (HEADERS logging in debug, NONE in release) |

### Idempotency

Idempotency is **client-side only**: `SmsDispatchWorker` checks `sms.enviado == true` in Room at the start of each attempt and returns `Result.success()` without making an HTTP call if already confirmed. The UUID stored in Room as the SMS primary key can be included in the URL template if the server also needs to deduplicate.

### Room database

Two tables, schema versioned in `SmsDatabase.kt`:
- `sms` (v1) — one row per received SMS, tracks `enviado`, `intentos`, `ultimoError`
- `log_entries` (v2) — audit log; types: `SMS_RECIBIDO`, `SMS_ENVIADO`, `ERROR`, `SISTEMA`

WAL mode enabled. No `fallbackToDestructiveMigration` — migrations must be explicit.

## Important notes

- `libs.versions.toml` is the single source of truth for all dependency versions. Retrofit and kotlinx.serialization entries were removed (unused); do not re-add them unless actually wiring up Retrofit.
- The `data/remote/` package is currently empty (reserved for future typed HTTP clients).
- The existing unit test (`SmsRepositoryTest`) is a placeholder — its `SmsMessage` constructor calls use English field names (`sender`, `body`) that do **not** match the current domain model (which uses `telefono`, `mensaje`). The tests compile as standalone fixtures but do not exercise real repository code yet.
- Debug variant uses `applicationId = com.capicua.smsgateway.debug`, so it can coexist with the release build on the same device.
