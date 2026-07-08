# SMS Gateway para Android

Aplicación Android que convierte un dispositivo dedicado con SIM en un **gateway SMS corporativo**: recibe mensajes SMS entrantes y los reenvía automáticamente a una API REST via HTTPS.

---

## Características principales

- **Sin pérdida de mensajes** — patrón Outbox: persiste el SMS en Room antes de intentar enviarlo
- **Reintentos automáticos** — WorkManager con backoff exponencial configurable
- **Idempotencia** — cada SMS tiene un UUID interno; el Worker verifica si ya fue enviado antes de cada intento para evitar duplicados
- **Autorrecuperación** — `HealthMonitorWorker` periódico reencola SMS atascados cada 15 minutos
- **Arranque automático** — `BootReceiver` reactiva los Workers tras reiniciar el dispositivo
- **Configuración en caliente** — plantilla de URL, timeouts y reintentos editables en la app sin recompilar
- **Registro de auditoría** — log completo de eventos con filtros y exportación a fichero
- **Solo HTTPS** — tráfico en claro deshabilitado por defecto en `network_security_config.xml`

---

## Arquitectura

```
Presentation  →  Domain  →  Data
   MVVM           UseCases     Room · OkHttp · DataStore · WorkManager
```

Clean Architecture con tres capas estrictas, inyección de dependencias con **Hilt**, y reactividad completa mediante **Kotlin Flow**.

El flujo de cada SMS:

```
SIM → SmsReceiver → SmsIngestionService → Room INSERT → WorkManager
                                                              ↓
                                                    SmsDispatchWorker
                                                    GET <url_plantilla> (HTTPS)
```

Consulta [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para la documentación técnica completa.

---

## Stack tecnológico

| Categoría | Tecnología |
|-----------|-----------|
| Lenguaje | Kotlin 2.0.21 |
| SDK mínimo | Android 8.0 (API 26) |
| SDK objetivo | Android 15 (API 35) |
| Inyección de dependencias | Hilt 2.52 |
| Base de datos local | Room 2.6.1 + WAL mode |
| Tareas en background | WorkManager 2.9.1 |
| Cliente HTTP | OkHttp 4.12.0 |
| Configuración | DataStore Preferences 1.1.1 |
| Concurrencia | Coroutines + Flow 1.9.0 |
| UI | Material Design 3 + Navigation Component |

---

## Requisitos

- Android Studio Ladybug (2024.2.1) o superior
- JDK 17
- Android SDK con API 35
- Dispositivo Android 8.0+ con SIM activa

---

## Compilar e instalar

```bash
# Build debug
./gradlew assembleDebug

# Instalar en dispositivo conectado
./gradlew installDebug

# Conceder permisos por ADB (dispositivo dedicado)
adb shell pm grant com.capicua.smsgateway android.permission.RECEIVE_SMS
adb shell pm grant com.capicua.smsgateway android.permission.READ_SMS
adb shell pm grant com.capicua.smsgateway android.permission.POST_NOTIFICATIONS

# Excluir de optimización de batería
adb shell dumpsys deviceidle whitelist +com.capicua.smsgateway
```

---

## Configuración

Al abrir la app por primera vez, ve a la pestaña **Config** e introduce:

| Campo | Descripción |
|-------|-------------|
| Plantilla de URL | URL completa con marcadores `{telefono}`, `{mensaje}` y `{fecha}` (ver ejemplos) |
| Timeout (s) | Segundos por petición HTTP (5–120, defecto 30) |
| Máx. reintentos | Intentos antes de marcar el SMS como error permanente (1–100) |
| Intervalo reintento (s) | Backoff inicial entre reintentos (5–3600) |
| Aceptar certs. inválidos | Permite certificados SSL autofirmados; solo para redes privadas/pruebas |

### Petición generada

La app construye una URL sustituyendo los marcadores de la plantilla con los valores del SMS (URL-encoded) y lanza un **GET**:

```
# Plantilla mínima (solo {mensaje} es obligatorio):
https://api.empresa.com/sms?msg={mensaje}

# Plantilla completa:
https://api.empresa.com/notify?phone={telefono}&msg={mensaje}&ts={fecha}
```

| Marcador | Valor | Obligatorio |
|----------|-------|-------------|
| `{mensaje}` | Texto del SMS (URL-encoded) | ✅ Sí |
| `{telefono}` | Número remitente (URL-encoded) | No |
| `{fecha}` | Timestamp ISO-8601 UTC (URL-encoded) | No |

---

## Permisos

| Permiso | Motivo |
|---------|--------|
| `RECEIVE_SMS` | Recibir el broadcast del sistema al llegar un SMS |
| `READ_SMS` | Declarado como complemento a `RECEIVE_SMS` |
| `INTERNET` | Enviar los SMS a la API corporativa |
| `FOREGROUND_SERVICE` | Mantener el proceso vivo durante la escritura en BD |
| `RECEIVE_BOOT_COMPLETED` | Reactivar Workers tras reinicio |
| `POST_NOTIFICATIONS` | Notificación visible del servicio en primer plano (API 33+) |

---

## Estructura del proyecto

```
app/src/main/java/com/capicua/smsgateway/
├── data/
│   ├── config/          # AppConfig, ConfigDataStore
│   ├── local/db/        # Room: SmsEntity, LogEntity, SmsDao, LogDao, SmsDatabase
│   ├── remote/          # (reservado para futuras integraciones HTTP tipadas)
│   └── repository/      # SmsRepositoryImpl, LogRepositoryImpl
├── di/                  # Módulos Hilt: Database, Network, Config, Repository, Worker
├── domain/
│   ├── model/           # SmsMessage, LogEntry, LogTipo
│   └── usecase/         # SaveSmsUseCase, GetSmsListUseCase
├── presentation/
│   ├── dashboard/       # DashboardFragment + ViewModel + SmsListAdapter
│   ├── logs/            # LogsFragment + ViewModel + LogsAdapter
│   └── settings/        # SettingsFragment + ViewModel
├── receiver/            # SmsReceiver, BootReceiver
├── service/             # SmsIngestionService
├── util/                # Constants, Extensions, NetworkMonitor
├── worker/              # SmsDispatchWorker, HealthMonitorWorker
└── GatewayApplication.kt
docs/
└── ARCHITECTURE.md      # Documentación técnica completa
```

---

## Base de datos

El gateway usa SQLite vía Room con dos tablas:

- **`sms`** — mensajes recibidos con estado de envío (`enviado`, `intentos`, `ultimoError`)
- **`log_entries`** — registro de auditoría de todos los eventos del sistema

Las migraciones están versionadas y nunca destruyen datos existentes.

---

## Contribuir

1. Haz un fork del repositorio
2. Crea una rama para tu feature: `git checkout -b feature/nombre-feature`
3. Realiza tus cambios y añade tests
4. Abre un Pull Request con una descripción clara del cambio

---

## Licencia

Este proyecto se distribuye bajo la **GNU Lesser General Public License v2.1**.  
Consulta el fichero [`LICENSE`](LICENSE) para más detalles.

---

## Aviso legal

Este software se proporciona **"tal cual"**, sin garantía de ningún tipo, expresa o implícita. El autor no se hace responsable de:

- Pérdida, alteración o interceptación de mensajes SMS durante el tránsito o el almacenamiento.
- Uso indebido de la aplicación para capturar o redirigir comunicaciones sin el consentimiento de los remitentes.
- Daños directos o indirectos derivados del uso, mal uso o imposibilidad de uso del software.
- Incumplimientos normativos (RGPD, LOPD u otras regulaciones de privacidad) derivados de una configuración o despliegue inadecuados.

**El operador es el único responsable** de garantizar que el uso de esta aplicación cumple con la legislación vigente en su jurisdicción, incluyendo la obtención del consentimiento necesario para el tratamiento de datos personales contenidos en los SMS.

---

## Desarrollo asistido por IA

El diseño, la arquitectura y el código de este proyecto se desarrollaron con asistencia de [Claude](https://claude.ai) (Anthropic). El código fue revisado, validado y adaptado por el autor antes de cada commit.
