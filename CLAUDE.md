# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run commands

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Release build (requiere las siguientes claves en local.properties):
#   KEYSTORE_PATH=<ruta al almacén, absoluta o relativa al módulo app/>
#   KEYSTORE_PASSWORD=<contraseña del almacén>
#   KEY_ALIAS=<alias de la clave>
#   KEY_PASSWORD=<contraseña de la clave>
# El almacén está fuera del repositorio y se comparte con las demás aplicaciones de
# la casa, así que KEYSTORE_PATH va relativa (`../../Claves/capicua.jks`): el file()
# de app/build.gradle.kts la resuelve desde el módulo, no desde la raíz.
# El APK resultante se genera como smsgateway-<versionName>.apk
./gradlew assembleRelease

# Unit tests
./gradlew test

# Single test class
./gradlew test --tests "com.capicua.smsgateway.data.repository.SmsRepositoryTest"

# Grant runtime permissions on a dedicated device (run after install)
adb shell pm grant com.capicua.smsgateway android.permission.RECEIVE_SMS
adb shell pm grant com.capicua.smsgateway android.permission.READ_SMS
adb shell pm grant com.capicua.smsgateway android.permission.POST_NOTIFICATIONS

# Exempt the app from Doze mode / battery optimization (REQUIRED for reliable dispatch)
# Without this, WorkManager may be deferred indefinitely when the screen is off.
# See "Device setup" section below for the on-device UI alternative.
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway

# Verify the app is in the whitelist
adb shell dumpsys deviceidle whitelist
# Expected: a line containing "com.capicua.smsgateway"

# Remove from whitelist (if needed)
adb shell dumpsys deviceidle whitelist -com.capicua.smsgateway

# Real-time log monitoring
adb logcat -s "SmsReceiver" "SmsIngestionService" "SmsDispatchWorker" "HealthMonitorWorker"
```

## Releases

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. Android will not install an
   APK whose `versionCode` is not higher than the one already on the phone.
2. **Write the entry in `CHANGELOG.md`**: what is under `[No publicado]` becomes the new version
   with its date. The changelog is the short answer to «what changed»; the GitHub release notes
   carry the installation steps.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/smsgateway-<versionName>.apk`,
   signed with the keys in `local.properties`.
4. Tag on `main` and publish with `gh release create vX.Y.Z --notes-file <notes> <apk>`.

Same signing key every time: that is what allows installing over the previous version without
losing the configuration or the audit log.

## The version check

«Acerca de» carries an **Actualizaciones** card: the app asks GitHub once a day whether a newer
release is published, and `MainActivity` puts a **badge on that tab** when there is one. No dialog
and no notification — this runs on a dedicated phone that nobody is looking at, and anything that
puts itself in front at startup hides what you came to check.

- **It reuses the app's `OkHttpClient`**, the same one that calls the forwarding API: one request a
  day does not deserve a second client. What it changes is the `User-Agent`, without which
  `api.github.com` answers 403. The response is read with `JSONObject` — four fields out of
  dozens, and this app does not pull in `kotlinx.serialization` for anything else.
- **Versions are compared as numbers** (`VersionPublicada`), never as text: `"1.10.0" < "1.9.0"`
  alphabetically, and that would silently stop the notice at the tenth release. `VersionPublicadaTest`
  covers it, and it is the only part of the check that can be tested without a network.
- **One question a day, with `If-None-Match`** (60 requests/hour/IP unauthenticated; a 304 does not
  count). The timestamp and the ETag live in `EstadoActualizacion`, in the settings DataStore but
  under their own keys and **outside `AppConfig`**: they are not gateway configuration and must not
  travel in the configuration backup.
- **What was found is remembered**, and not only the moment it was asked: the tag, the page and
  the APK go into `EstadoActualizacion` too, and `comprobar()` answers from there while the stored
  version still beats the installed one. Without it the badge appeared on the first launch of the
  day and vanished on the second, because the daily gate answered `SinPreguntar` and nobody
  remembered the finding. It is also the correct answer to a **304** —«the latest release has not
  changed» is not «it is the one installed»— and it clears itself: once the update is in, the
  comparison fails and `olvidarNovedad()` wipes it.
- **A 404 means no releases yet**, not an error; no network means stay quiet.
- **`REQUEST_INSTALL_PACKAGES`** lets the app open Android's installer with the APK that
  `DownloadManager` downloaded. There is no silent install without being device owner, and since
  Android 8 the user also has to authorise *this* app, which is checked before downloading.
- **Same signing key or no upgrade.** That is what makes a forged APK impossible — and also what
  stops this from installing over a debug build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

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

> **HTTP y HTTPS:** el tráfico cleartext (HTTP) está **permitido** (`cleartextTrafficPermitted="true"`) para dar soporte a servidores locales o corporativos sin TLS. Para restringir a HTTPS, cambia `usesCleartextTraffic` en `AndroidManifest.xml` y `cleartextTrafficPermitted` en `network_security_config.xml`.

### Key files

| File | Role |
|------|------|
| `data/config/AppConfig.kt` | Config data class + `construirUrl()` + `esValida()` |
| `data/config/ConfigDataStore.kt` | DataStore wrapper — reactive reads, atomic writes |
| `worker/SmsDispatchWorker.kt` | Sends one SMS; handles retry/failure logic |
| `worker/HealthMonitorWorker.kt` | Periodic cleanup + re-enqueue of stuck SMS |
| `util/HttpClientFactory.kt` | Derives an OkHttpClient from the base (timeout + optional trust-all SSL) |
| `util/OptimizacionBateria.kt` | Doze exemption: status query + the Settings screen's button |
| `util/InicioAutomatico.kt` | OEM autostart screen (MIUI/EMUI/ColorOS/Funtouch) + the Settings button |
| `util/ControlRecepcion.kt` | Enables/disables the `SmsReceiver` component — the reception kill switch |
| `data/config/ConfigBackup.kt` | Export/import of the configuration as JSON over a SAF `Uri` |
| `di/NetworkModule.kt` | Provides base OkHttpClient (HEADERS logging in debug, NONE in release) |

### Idempotency

Idempotency is **client-side only**: `SmsDispatchWorker` checks `sms.enviado == true` in Room at the start of each attempt and returns `Result.success()` without making an HTTP call if already confirmed. The UUID stored in Room as the SMS primary key can be included in the URL template if the server also needs to deduplicate.

### Room database

Two tables, schema versioned in `SmsDatabase.kt`:
- `sms` (v1) — one row per received SMS, tracks `enviado`, `intentos`, `ultimoError`
- `log_entries` (v2) — audit log; types: `SMS_RECIBIDO`, `SMS_ENVIADO`, `ERROR`, `SISTEMA`

WAL mode enabled. No `fallbackToDestructiveMigration` — migrations must be explicit.

## Device setup (production gateway phone)

These steps must be completed once on the dedicated Android device that acts as the SMS gateway.
Skip any step already done.

### Why battery optimization must be disabled

Android's **Doze mode** suspends background processes when the screen is off for more than a few
minutes. WorkManager respects Doze and may delay `SmsDispatchWorker` by hours. Exempting the app
ensures every SMS is forwarded within seconds of arrival, regardless of screen or battery state.

### Option A — ADB command (fastest, requires USB + developer tools)

```bash
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway
```

Run once after install. To verify the app was added to the whitelist:

```bash
adb shell dumpsys deviceidle whitelist
# Expected output includes a line with: com.capicua.smsgateway
```

### Option B — On-device UI (no USB required)

The exact path varies by manufacturer. Use the closest match:

**Stock Android / Pixel**
1. **Ajustes** → **Aplicaciones** → **SMS Gateway**
2. **Batería** → seleccionar **Sin restricciones**

**Samsung (One UI)**
1. **Ajustes** → **Aplicaciones** → **SMS Gateway**
2. **Batería** → desactivar **Permitir actividad en segundo plano** NO — en su lugar:
   - **Ajustes** → **Mantenimiento del dispositivo** → **Batería**
   - **Límites de uso en segundo plano** → **Aplicaciones sin suspender** → **Añadir** → SMS Gateway

**Xiaomi / MIUI / HyperOS**
1. **Ajustes** → **Aplicaciones** → **Administrar aplicaciones** → **SMS Gateway**
2. **Ahorro de batería** → **Sin restricciones**
3. Volver a la ficha de la app → activar **Inicio automático**

**Huawei / EMUI**
1. **Ajustes** → **Aplicaciones** → **SMS Gateway** → **Consumo de batería**
2. Desactivar **Gestión inteligente de energía** y seleccionar **Sin restricciones**

**OnePlus / OxygenOS / ColorOS**
1. **Ajustes** → **Aplicaciones** → **SMS Gateway** → **Batería**
2. **Optimización de batería** → **No optimizar**

> **Nota:** en cualquier fabricante también puedes buscar "Optimización de batería" directamente
> en el buscador de Ajustes, seleccionar **Todas las aplicaciones** y cambiar SMS Gateway
> a **No optimizar**.

### Other recommended settings (all manufacturers)

| Setting | Where | Value |
|---------|-------|-------|
| Inicio automático | Ajustes → Aplicaciones → SMS Gateway | **Activado** |
| Ejecutar en segundo plano | Ajustes → Aplicaciones → SMS Gateway → Batería | **Permitido** |
| Optimización de batería | Ajustes → Batería → Optimización → SMS Gateway | **No optimizar** |

---

## Important notes

- `libs.versions.toml` is the single source of truth for all dependency versions. Retrofit and kotlinx.serialization entries were removed (unused); do not re-add them unless actually wiring up Retrofit.
- The `data/remote/` package is currently empty (reserved for future typed HTTP clients).
- **`SmsRepositoryTest` used to break the whole test source set.** It was a placeholder written against the English domain model (`sender`, `body`, `status`), which stopped existing when the members moved to Spanish: it did not compile, and with it nothing in `src/test` compiled either — which is why the unit tests had never been run here. It now checks what can be checked without Room or Android: that a freshly received SMS is unsent, with no attempts and no error, which is the premise of the outbox pattern. Real repository tests need an in-memory Room database and therefore instrumentation; this does not pretend to replace them.
- Debug variant uses `applicationId = com.capicua.smsgateway.debug`, so it can coexist with the release build on the same device.
- **Anything scoped per-install must derive from `applicationId`, never be typed out.** The FileProvider authority was hardcoded as `com.capicua.smsgateway.provider`, so both variants declared the *same* authority — and an authority is unique device-wide, so Android refused to have both installed (`INSTALL_FAILED_CONFLICTING_PROVIDER`), defeating the very point of the `applicationIdSuffix`. It now reads `${applicationId}.provider` in the manifest and `BuildConfig.APPLICATION_ID + ".provider"` in `Constants`; the two have to stay in step. The symptom is misleading — it surfaces at install time, never at build time.
- **Reception has a master switch** at the top of Ajustes (`recepcionActiva`). Turning it off does more than store a flag: `ControlRecepcion` disables the `SmsReceiver` **component** via the `PackageManager`. An `if` at the top of `onReceive` would wake the app for every message that reaches the phone just to drop it, and would force an async DataStore read on the main thread inside the broadcast's ten-second budget. It stops the **inbound** side only — whatever is already queued still goes out, because discarding it would lose messages the app had already accepted. The component state is persisted by the system and survives reboots, so `GatewayApplication.sincronizarRecepcion()` only matters after a **reinstall**, where the component reverts to its manifest value and the saved setting has to win again.
- **Nothing starting after a reboot is usually not a bug in `BootReceiver`.** The SMS receiver is declared in the manifest, so the system starts it on its own. Two things outside the app's reach break that: the OEM **autostart** list (MIUI, HyperOS, EMUI, ColorOS, Funtouch ship it empty — `InicioAutomatico` opens that screen, and the permission can neither be granted nor queried from code), and Android's **stopped state** — a freshly installed app that has never been opened receives no broadcasts at all until someone launches it once.
- **Ajustes exports and imports the configuration** (`ConfigBackup`, JSON over a SAF `Uri`, so no storage permission). It is written with `org.json` rather than a serialization library because it is the only thing in the project that needs it. The file carries the URL template — domain and sometimes an API key — so the screen says out loud that it is sensitive.
- **«Acerca de» carries a liability disclaimer** alongside the privacy notice: the app ships as-is, and whoever installs and configures it is solely responsible for its use and for the law that applies to them.
- **The «Acerca de» tab carries the donation link**, and it is the only screen that mentions it: a card with an *Invitar a un café* button that opens `https://paypal.me/jbautistamartin` in the browser (`URL_DONATIVO`, a private const in `AboutFragment`). The button's heart (`drawable/ic_corazon.xml`) is the same four-Bézier path and the same gold SilentRunner draws in its tray menu, and it carries `app:iconTint="@null"` so the theme colour does not flatten it. The intent is fired and `ActivityNotFoundException` caught rather than asking the `PackageManager` first — since Android 11 package visibility can claim there is no browser when there is one — and a Snackbar explains it if nothing resolves, instead of leaving a dead button. It is optional end to end: the app never nags and no feature depends on it.
- **The link to the project closes the signature block** at the top of that tab — name, version,
  company, author and then *Ver el proyecto en GitHub* (`URL_PROYECTO`, beside `URL_DONATIVO` in
  `AboutFragment`): where the code lives is part of who answers for this. It is a bare
  `TextButton`, with no card and no icon, because it is a link and not a section to read. Both
  links are opened by the same `abrirEnlace(url)` and share the Snackbar for a device with no
  browser — the string was renamed from `about_donativo_sin_navegador` to `about_sin_navegador`
  when the second link arrived.
- **The release keystore is shared and lives outside the repository** (`../../Claves/capicua.jks`, relative to the `app/` module, which is where `file()` resolves it). The same key signs the other apps of the house, so every future update must be signed with it: if it is lost, the installed app can no longer be updated — only uninstalled and reinstalled, losing its data.
