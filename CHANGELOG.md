# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/);
versionado según [SemVer](https://semver.org/lang/es/).

## [No publicado]

---

## [1.3.0] - 2026-10-25

**Última versión.** SMSGateway queda descontinuada: sigue en
[SMSRelay](https://github.com/jbautistamartin/SMSRelay), que junta las cuatro aplicaciones de la
familia y reenvía por WhatsApp, Telegram, SMS o una API REST, eligiendo el canal en cada regla.
SMSRelay importa la copia de seguridad de esta aplicación tal cual.

### Añadido

- **Aviso de aplicación descontinuada** en «Acerca de», el primero de la pantalla, con un botón
  que abre la descarga de SMSRelay y los pasos para pasarse sin perder las reglas.

### Cambiado

- **Toolchain al día.** AGP 8.5.2 → 9.4.1, Kotlin 2.0.21 → 2.4.21, KSP 2.3.12, Gradle 9.3.0 →
  9.8.2, Hilt 2.60.1, Room 2.8.5, WorkManager 2.12.0 y el resto de dependencias a su última
  versión estable. OkHttp pasa de la 4 a la 5.5.1. `compileSdk` pasa a 37; `targetSdk` sigue en 35, así que el
  comportamiento en el teléfono no cambia. Para compilar hace falta **Temurin 25** y la
  **plataforma 37** del SDK. Se hizo a la vez en las cuatro aplicaciones de la familia.

### Documentación

- **`MASTERPLAN-GATEWAY.md`**, escrito a posteriori: el origen de SMSGateway y de la tubería que
  heredaron las otras tres. Lo llevan las cuatro, para que todas tengan la misma estructura de
  planes.

## [1.2.0] - 2026-10-17

### Añadido

- **Copiar una entrada del registro con una pulsación larga.** Copia la misma línea que escribe
  el fichero exportado —fecha, tipo y detalle—, para pegarla en un correo o en un parte de
  incidencia sin tener que exportar el registro entero. El aviso se enseña hasta Android 12;
  desde Android 13 lo saca el propio sistema, con vista previa de lo copiado, y poner otro encima
  serían dos avisos de lo mismo.

- **Aviso de privacidad en «Acerca de»**, que es la tarjeta que llevaban las otras tres y aquí
  faltaba: el contenido de los SMS sale del teléfono hacia la API que se configure, y el remitente
  original no lo sabe ni lo ha consentido.

- **Subtítulo en «Configuración»**, como en las otras tres, diciendo de qué manda esta pantalla y
  que aquí no hay reglas: todos los SMS salen por la misma URL.

### Cambiado

- **Icono de la familia.** El icono era el que más se salía: fondo plano de un azul que ya no
  estaba en la paleta de la aplicación, un dibujo que no decía lo que hace y sin capa monocroma
  —el único de los cuatro que quedaba fuera de los iconos temáticos de Android 13+—. Ahora lleva
  el bocadillo de mensaje común a las cuatro, con una flecha de subida calada porque el destino es
  una API, el fondo en degradado derivado del primario del tema y capa monocroma.

- **Paleta violeta.** El primario era el verde azulado `#006874`, exactamente el mismo que
  SMStoSMS. En esta familia el color distingue la aplicación de un vistazo en el lanzador y dice a
  dónde entrega; SMSGateway entrega a una API, no a una aplicación de mensajería, así que estrena
  color propio en vez de compartir el de otra.

- **Orden común en «Configuración».** Las cuatro aplicaciones de la familia presentan ahora las
  secciones en el mismo orden: **Recepción de SMS**, **Fiabilidad en segundo plano**, la sección
  propia del destino, **Límites**, **Envío y reintentos** y **Copia de seguridad**. Quien salta de
  una a otra encuentra lo mismo en el mismo sitio. Aquí el orden ya era ese; lo que cambia es el
  nombre de una sección y el subtítulo que faltaba.

- **Orden común en «Acerca de»**: marcadores, aviso de privacidad, responsabilidad,
  actualizaciones, código abierto y donativo, en ese orden en las cuatro.

- **«Código abierto» pasa a ser una tarjeta** antes del donativo, como en las otras tres, en lugar
  de un enlace suelto bajo la firma.

- **Filtros del registro en una sola fila**, con desplazamiento horizontal como en las otras tres,
  en lugar de repartidos en dos líneas. El `NestedScrollableHost` portado de SMStoSMS es lo que
  evita que arrastrar sobre los filtros cambie de pestaña.

- **«Reintentos» se llama ahora «Envío y reintentos»**, como en las otras tres.

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
