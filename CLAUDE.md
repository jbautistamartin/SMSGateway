# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Toda la documentación de este proyecto está en español, igual que los comentarios y el KDoc
> del código. La línea de arriba se conserva en inglés porque es la cabecera estándar que
> identifica el fichero.

## Comandos de compilación y ejecución

```bash
# Compilación debug
./gradlew assembleDebug

# Instalar en el dispositivo conectado
./gradlew installDebug

# Compilación release (requiere las siguientes claves en local.properties):
#   KEYSTORE_PATH=<ruta al almacén, absoluta o relativa al módulo app/>
#   KEYSTORE_PASSWORD=<contraseña del almacén>
#   KEY_ALIAS=<alias de la clave>
#   KEY_PASSWORD=<contraseña de la clave>
# El almacén está fuera del repositorio y se comparte con las demás aplicaciones de
# la casa, así que KEYSTORE_PATH va relativa (`../../Claves/capicua.jks`): el file()
# de app/build.gradle.kts la resuelve desde el módulo, no desde la raíz.
# El APK resultante se genera como smsgateway-<versionName>.apk
./gradlew assembleRelease

# Tests unitarios
./gradlew test

# Una sola clase de test
./gradlew test --tests "com.capicua.smsgateway.data.repository.SmsRepositoryTest"

# Conceder permisos en tiempo de ejecución en un dispositivo dedicado (tras instalar)
adb shell pm grant com.capicua.smsgateway android.permission.RECEIVE_SMS
adb shell pm grant com.capicua.smsgateway android.permission.READ_SMS
adb shell pm grant com.capicua.smsgateway android.permission.POST_NOTIFICATIONS

# Eximir la app del modo Doze / optimización de batería (OBLIGATORIO para un despacho fiable)
# Sin esto, WorkManager puede posponerse indefinidamente con la pantalla apagada.
# La alternativa por interfaz está en la sección «Preparación del dispositivo», más abajo.
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway

# Verificar que la app está en la lista de exentas
adb shell dumpsys deviceidle whitelist
# Se espera: una línea que contenga "com.capicua.smsgateway"

# Quitarla de la lista (si hiciera falta)
adb shell dumpsys deviceidle whitelist -com.capicua.smsgateway

# Seguimiento del log en tiempo real
adb logcat -s "SmsReceiver" "SmsIngestionService" "SmsDispatchWorker" "HealthMonitorWorker"
```

## Publicar una versión

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`. Android no instala un APK cuyo
   `versionCode` no sea mayor que el que ya está en el teléfono.
2. **Escribe la entrada en `CHANGELOG.md`**: lo que está bajo `[No publicado]` pasa a ser la
   versión nueva con su fecha. El changelog es la respuesta corta a «qué ha cambiado»; las notas
   del release de GitHub llevan los pasos de instalación.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/smsgateway-<versionName>.apk`,
   firmado con las claves de `local.properties`.
4. Etiqueta sobre `main` y publica con `gh release create vX.Y.Z --notes-file <notas> <apk>`.

Siempre la misma clave de firma: es lo que permite instalar encima de la versión anterior sin
perder la configuración ni el registro de auditoría.

## El aviso de versión nueva

«Acerca de» lleva una tarjeta de **Actualizaciones**: la app pregunta a GitHub una vez al día si
hay un release más reciente, y `MainActivity` pone una **marca en esa pestaña** cuando lo hay. Ni
diálogo ni notificación: esto corre en un teléfono dedicado que nadie está mirando, y cualquier
cosa que se ponga delante al arrancar tapa justo lo que venías a consultar.

- **Reutiliza el `OkHttpClient` de la aplicación**, el mismo que llama a la API de reenvío: una
  petición al día no merece un segundo cliente. Lo que sí cambia es el `User-Agent`, sin el cual
  `api.github.com` responde 403. La respuesta se lee con `JSONObject` —cuatro campos de entre
  docenas— y esta aplicación no arrastra `kotlinx.serialization` para nada más.
- **Las versiones se comparan como números** (`VersionPublicada`), nunca como texto: `"1.10.0" <
  "1.9.0"` alfabéticamente, y eso dejaría el aviso mudo a partir del décimo release.
  `VersionPublicadaTest` lo cubre, y es la única parte de la comprobación que puede probarse sin
  red.
- **Una pregunta al día, con `If-None-Match`** (60 peticiones/hora/IP sin autenticar; un 304 no
  cuenta). La marca de tiempo y el ETag viven en `EstadoActualizacion`, en el DataStore de ajustes
  pero bajo sus propias claves y **fuera de `AppConfig`**: no son configuración del gateway y no
  deben viajar en la copia de seguridad.
- **Lo que se encontró se recuerda**, y no solo el momento en que se preguntó: la etiqueta, la
  página y el APK van también a `EstadoActualizacion`, y `comprobar()` responde desde ahí mientras
  la versión guardada siga ganando a la instalada. Sin eso, la marca aparecía en el primer arranque
  del día y desaparecía en el segundo, porque la barrera diaria respondía `SinPreguntar` y nadie
  recordaba el hallazgo. Es además la respuesta correcta a un **304** —«el último release no ha
  cambiado» no es «es el instalado»— y se limpia sola: una vez instalada la actualización, la
  comparación falla y `olvidarNovedad()` lo borra.
- **Un 404 significa que todavía no hay releases**, no un error; sin red, callar.
- **`REQUEST_INSTALL_PACKAGES`** permite a la app abrir el instalador de Android con el APK que
  descargó `DownloadManager`. No hay instalación silenciosa sin ser device owner, y desde Android 8
  el usuario tiene además que autorizar a *esta* app, cosa que se comprueba antes de descargar.
- **La misma clave de firma o no hay actualización.** Eso es lo que hace imposible un APK
  falsificado, y también lo que impide instalar encima de una compilación debug
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

## Arquitectura

Clean Architecture con separación estricta en tres capas y MVVM en la capa de presentación.
Inyección de dependencias con Hilt en todo el proyecto.

```
Presentación  (Fragments + ViewModels)
    ↓ usa
Dominio       (UseCases + modelos de Kotlin puro)
    ↓ usa interfaces, implementadas por
Datos         (Room · OkHttp · DataStore · WorkManager)
```

### Ciclo de vida de un SMS

```
SIM → SmsReceiver.onReceive()           ← BroadcastReceiver (hilo principal, < 10 s)
        ↓ startForegroundService()
      SmsIngestionService               ← ForegroundService (debe llamar a startForeground() < 5 s)
        ↓ SaveSmsUseCase
      INSERT en Room (SmsEntity)        ← outbox: persiste ANTES de despachar
        ↓ WorkManager.enqueueUniqueWork()
      SmsDispatchWorker.doWork()        ← se ejecuta cuando hay red, con backoff exponencial
        ↓ OkHttp GET <url_de_la_plantilla>
      respuesta HTTP → actualizar Room
```

`HealthMonitorWorker` se ejecuta cada 15 minutos para reencolar SMS huérfanos (sin tarea activa de
WorkManager) y purgar registros antiguos (SMS de más de 7 días, logs de más de 30).

`BootReceiver` reactiva las tareas de WorkManager tras reiniciar el dispositivo.

### Mecanismo HTTP

La aplicación hace una petición **GET** sencilla. No hay endpoint fijo ni cuerpo JSON. Quien la
opera configura una **plantilla** de URL completa en la pantalla de Ajustes, con marcadores
opcionales que se codifican para URL y se sustituyen en el momento del envío:

- `{mensaje}` — cuerpo del SMS (**obligatorio** en la plantilla)
- `{telefono}` — número del remitente
- `{fecha}` — marca de tiempo ISO-8601 en UTC

`AppConfig.construirUrl()` hace la sustitución. `AppConfig.esValida()` devuelve false si la
plantilla está vacía o le falta `{mensaje}`. La autenticación, si hace falta, va dentro de la
propia URL (por ejemplo `?token=secreto&msg={mensaje}`).

> **HTTP y HTTPS:** el tráfico en claro (HTTP) está **permitido**
> (`cleartextTrafficPermitted="true"`) para dar soporte a servidores locales o corporativos sin
> TLS. Para restringir a HTTPS, cambia `usesCleartextTraffic` en `AndroidManifest.xml` y
> `cleartextTrafficPermitted` en `network_security_config.xml`.

### Ficheros clave

| Fichero | Cometido |
|------|------|
| `data/config/AppConfig.kt` | Data class de configuración + `construirUrl()` + `esValida()` |
| `data/config/ConfigDataStore.kt` | Envoltorio de DataStore — lecturas reactivas, escrituras atómicas |
| `worker/SmsDispatchWorker.kt` | Envía un SMS; gestiona la lógica de reintento y de fallo |
| `worker/HealthMonitorWorker.kt` | Limpieza periódica + reencolado de SMS atascados |
| `util/HttpClientFactory.kt` | Deriva un OkHttpClient del base (timeout + SSL permisivo opcional) |
| `util/OptimizacionBateria.kt` | Exención de Doze: consulta del estado + el botón de Ajustes |
| `util/InicioAutomatico.kt` | Pantalla de inicio automático del fabricante (MIUI/EMUI/ColorOS/Funtouch) + el botón de Ajustes |
| `util/ControlRecepcion.kt` | Activa y desactiva el componente `SmsReceiver` — el interruptor de recepción |
| `data/config/ConfigBackup.kt` | Exportar e importar la configuración como JSON sobre un `Uri` de SAF |
| `di/NetworkModule.kt` | Provee el OkHttpClient base (logging HEADERS en debug, NONE en release) |

### Idempotencia

La idempotencia es **solo de cliente**: `SmsDispatchWorker` comprueba `sms.enviado == true` en Room
al principio de cada intento y devuelve `Result.success()` sin hacer la llamada HTTP si ya está
confirmado. El UUID que Room guarda como clave primaria del SMS puede incluirse en la plantilla de
URL si el servidor necesita deduplicar también por su parte.

### Base de datos Room

Dos tablas, esquema versionado en `SmsDatabase.kt`:
- `sms` (v1) — una fila por SMS recibido; registra `enviado`, `intentos` y `ultimoError`
- `log_entries` (v2) — registro de auditoría; tipos: `SMS_RECIBIDO`, `SMS_ENVIADO`, `ERROR`,
  `SISTEMA`

Modo WAL activado. Sin `fallbackToDestructiveMigration`: las migraciones tienen que ser explícitas.

## Preparación del dispositivo (teléfono pasarela de producción)

Estos pasos se hacen una sola vez en el dispositivo Android dedicado que actúa como pasarela de
SMS. Sáltate los que ya estén hechos.

### Por qué hay que desactivar la optimización de batería

El **modo Doze** de Android suspende los procesos en segundo plano cuando la pantalla lleva unos
minutos apagada. WorkManager respeta Doze y puede retrasar `SmsDispatchWorker` durante horas.
Eximir a la app garantiza que cada SMS se reenvíe a los pocos segundos de llegar, sin importar el
estado de la pantalla ni de la batería.

### Opción A — comando ADB (lo más rápido; requiere USB y herramientas de desarrollo)

```bash
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway
```

Se ejecuta una vez tras instalar. Para verificar que la app quedó en la lista:

```bash
adb shell dumpsys deviceidle whitelist
# La salida debe incluir una línea con: com.capicua.smsgateway
```

### Opción B — interfaz del dispositivo (sin USB)

La ruta exacta varía según el fabricante. Usa la que más se parezca:

**Android puro / Pixel**
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

### Otros ajustes recomendados (todos los fabricantes)

| Ajuste | Dónde | Valor |
|---------|-------|-------|
| Inicio automático | Ajustes → Aplicaciones → SMS Gateway | **Activado** |
| Ejecutar en segundo plano | Ajustes → Aplicaciones → SMS Gateway → Batería | **Permitido** |
| Optimización de batería | Ajustes → Batería → Optimización → SMS Gateway | **No optimizar** |

---

## Notas importantes

- `libs.versions.toml` es la única fuente de verdad para las versiones de todas las dependencias.
  Las entradas de Retrofit y kotlinx.serialization se eliminaron por no usarse; no las vuelvas a
  añadir salvo que de verdad vayas a montar Retrofit.
- El paquete `data/remote/` está vacío por ahora (reservado para futuros clientes HTTP tipados).
- **`SmsRepositoryTest` rompía todo el conjunto de tests.** Era un placeholder escrito contra el
  modelo de dominio en inglés (`sender`, `body`, `status`), que dejó de existir cuando los miembros
  pasaron al español: no compilaba, y con él no compilaba nada de `src/test` — que es la razón de
  que los tests unitarios no se hubieran llegado a ejecutar aquí. Ahora comprueba lo que se puede
  comprobar sin Room ni Android: que un SMS recién recibido está sin enviar, sin intentos y sin
  error, que es la premisa del patrón outbox. Unos tests de repositorio de verdad necesitan una
  base de datos Room en memoria y por tanto instrumentación; esto no pretende sustituirlos.
- La variante debug usa `applicationId = com.capicua.smsgateway.debug`, así que puede convivir con
  la de release en el mismo dispositivo.
- **Todo lo que tenga alcance por instalación debe derivarse de `applicationId`, nunca escribirse a
  mano.** La autoridad del FileProvider estaba escrita como `com.capicua.smsgateway.provider`, de
  modo que ambas variantes declaraban la *misma* autoridad — y una autoridad es única en todo el
  dispositivo, así que Android se negaba a tener las dos instaladas
  (`INSTALL_FAILED_CONFLICTING_PROVIDER`), echando por tierra el sentido mismo del
  `applicationIdSuffix`. Ahora se lee `${applicationId}.provider` en el manifiesto y
  `BuildConfig.APPLICATION_ID + ".provider"` en `Constants`; los dos tienen que ir a la par. El
  síntoma despista: aparece al instalar, nunca al compilar.
- **La recepción tiene un interruptor general** arriba del todo en Ajustes (`recepcionActiva`).
  Apagarlo hace algo más que guardar un flag: `ControlRecepcion` desactiva el **componente**
  `SmsReceiver` a través del `PackageManager`. Un `if` al principio de `onReceive` despertaría a la
  app con cada mensaje que llegase al teléfono solo para descartarlo, y obligaría a una lectura
  asíncrona de DataStore en el hilo principal dentro del presupuesto de diez segundos del
  broadcast. Detiene solo el lado **entrante**: lo que ya esté en cola termina de salir, porque
  descartarlo perdería mensajes que la app ya había aceptado. El sistema persiste el estado del
  componente y sobrevive a los reinicios, así que `GatewayApplication.sincronizarRecepcion()` solo
  importa después de una **reinstalación**, donde el componente vuelve al valor del manifiesto y el
  ajuste guardado tiene que volver a imponerse.
- **Que no arranque nada tras un reinicio no suele ser un fallo de `BootReceiver`.** El receptor de
  SMS está declarado en el manifiesto, así que el sistema lo arranca por su cuenta. Dos cosas fuera
  del alcance de la app lo rompen: la lista de **inicio automático** del fabricante (MIUI, HyperOS,
  EMUI, ColorOS y Funtouch la traen vacía — `InicioAutomatico` abre esa pantalla, y el permiso no
  se puede conceder ni consultar desde código) y el **estado detenido** de Android: una app recién
  instalada que nadie ha abierto nunca no recibe ningún broadcast hasta que alguien la lanza una
  vez.
- **Ajustes exporta e importa la configuración** (`ConfigBackup`, JSON sobre un `Uri` de SAF, así
  que sin permiso de almacenamiento). Está escrito con `org.json` en lugar de con una biblioteca de
  serialización porque es lo único del proyecto que lo necesita. El fichero lleva la plantilla de
  URL —dominio y a veces una clave de API—, así que la pantalla avisa en voz alta de que es
  sensible.
- **«Acerca de» lleva un descargo de responsabilidad** junto al aviso de privacidad: la aplicación
  se entrega tal cual, y quien la instala y la configura es el único responsable de su uso y de la
  ley que le aplique.
- **La pestaña «Acerca de» lleva el enlace de donación**, y es la única pantalla que lo menciona:
  una tarjeta con un botón *Invitar a un café* que abre `https://paypal.me/jbautistamartin` en el
  navegador (`URL_DONATIVO`, una const privada de `AboutFragment`). El corazón del botón
  (`drawable/ic_corazon.xml`) es el mismo trazado de cuatro béziers y el mismo dorado que dibuja
  SilentRunner en su menú de bandeja, y lleva `app:iconTint="@null"` para que el color del tema no
  lo aplane. El intent se lanza capturando `ActivityNotFoundException` en lugar de preguntar antes
  al `PackageManager` —desde Android 11 la visibilidad de paquetes puede afirmar que no hay
  navegador cuando sí lo hay— y un Snackbar lo explica si no resuelve nada, en vez de dejar un
  botón muerto. Es opcional de principio a fin: la app no insiste nunca y ninguna funcionalidad
  depende de ello.
- **El enlace al proyecto es una tarjeta de *Código abierto*** justo encima de la de donación
  (`about_proyecto_*`, `URL_PROYECTO` al lado de `URL_DONATIVO` en `AboutFragment`): dónde vive el
  código es parte de quién responde por esto. Era un `TextButton` pelado que cerraba el bloque de
  firma, con el argumento de que un enlace no es una sección que leer; pasó a ser una tarjeta para
  que la **pestaña «Acerca de» lleve las mismas tarjetas y en el mismo orden que SMStoSMS,
  SMStoTelegram y SMStoWhats** — marcadores, privacidad, responsabilidad, actualizaciones, código
  abierto y donación. Los dos enlaces los abre el mismo `abrirEnlace(url)` y comparten el Snackbar
  para un dispositivo sin navegador; la cadena pasó de llamarse `about_donativo_sin_navegador` a
  `about_sin_navegador` cuando llegó el segundo enlace.
- **Las cuatro aplicaciones de la casa comparten un sistema de iconos de lanzador, y esta es el
  miembro más nuevo.** Fondo = un vector con degradado lineal derivado de `md_theme_primary` (más
  claro ×1,25, más oscuro ×0,64); frente = el bocadillo de la familia con el glifo propio de la app
  recortado bajo `fillType="evenOdd"` (aquí una flecha hacia arriba: el destino es una API, no una
  aplicación de mensajería); más una capa **monocroma** idéntica, sin la cual el lanzador deja el
  icono fuera de los iconos temáticos de Android 13+. El color es lo que distingue las apps a 48 dp
  —turquesa SMStoSMS, azul SMStoTelegram, verde SMStoWhats, violeta esta—, así que **el fondo del
  icono y el primary del tema se cambian juntos**; el icono de esta app era un `<shape>` plano en
  un azul que la paleta ya había abandonado, que es exactamente el fallo que esa regla evita.
- **El almacén de claves de release es compartido y vive fuera del repositorio**
  (`../../Claves/capicua.jks`, relativo al módulo `app/`, que es desde donde lo resuelve `file()`).
  La misma clave firma las demás aplicaciones de la casa, así que toda actualización futura tiene
  que ir firmada con ella: si se pierde, la aplicación instalada ya no se puede actualizar — solo
  desinstalar y reinstalar, perdiendo sus datos.
