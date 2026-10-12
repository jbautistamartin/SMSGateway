# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/);
versionado según [SemVer](https://semver.org/lang/es/).

## [1.1.0] - 2026-10-11

### Añadido

- **Aviso de versión nueva.** Comprueba una vez al día si hay publicado un release más reciente y
  lo marca con un punto en la pestaña **Acerca de**. Desde su tarjeta se descarga e instala el APK
  —con el gestor de descargas del sistema y la confirmación de Android—, se puede buscar a mano y
  se puede apagar la comprobación automática. La configuración y el registro se conservan al
  actualizar, porque el APK va firmado con la misma clave.

- **Enlace al proyecto en «Acerca de».** Cierra la firma de esa pestaña —nombre, versión, empresa
  y autor— con un botón que abre la página del repositorio en el navegador.

## [1.0.1] - 2026-07-09

### Añadido

- **Soporte de HTTP en claro** para servidores locales o corporativos sin TLS.

### Cambiado

- **La firma del release se lee de `local.properties`**, sin credenciales escritas en el código ni
  en el repositorio.
- El APK de publicación sale con la versión en el nombre: `smsgateway-<version>.apk`.

## [1.0.0] - 2026-07-08

Primera versión estable: un teléfono dedicado con SIM que recibe SMS y los reenvía a una API REST.

### Añadido

- **Recepción de SMS entrantes** con `SmsReceiver` más un servicio en primer plano, que es lo que
  permite terminar el trabajo fuera del presupuesto de diez segundos del *broadcast*.
- **Reenvío automático a una API REST** por GET, con plantilla de URL configurable
  (`{mensaje}`, `{telefono}`, `{fecha}`).
- **Sin pérdida de mensajes.** Patrón *outbox* sobre Room (SQLite con WAL): el SMS se persiste
  antes de intentar nada.
- **Reintentos automáticos** con WorkManager (*backoff* lineal, sin restricción de red).
- **Idempotencia.** Cada SMS lleva un UUID interno y el *worker* comprueba si ya se envió antes
  de cada intento, para no duplicar.
- **Rescate de SMS huérfanos** cada 30 segundos y **limpieza periódica** de lo enviado y de los
  registros con más de 30 días.
- **Arranque automático** tras reiniciar el dispositivo.
- **Configuración en caliente**: URL, tiempo límite, reintentos y certificados autofirmados, sin
  recompilar.
- **Registro de auditoría** con filtros y exportación a fichero.
