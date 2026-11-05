# Freno

Launcher para Android, negro y solo texto, que pone una pausa antes de abrir las apps que se usan por impulso.

## Qué hace (v0.1)

- **Inicio**: hora, fecha, tus apps favoritas en texto y cuántas veces te has frenado hoy.
- **Lista de apps**: desliza hacia arriba (o toca "apps ↑"). Buscador con el teclado ya abierto.
- **Freno**: al abrir una app con freno sale un círculo de respiración, una espera (8 s por defecto), la pregunta "¿Para qué la abres?" y luego *Abrir* o *Mejor no*.
- **Mantener pulsada una app**: poner/quitar freno, añadir a inicio, ocultar, info de la app, desinstalar. Quitar un freno también pasa por la respiración.
- **Ajustes**: mantén pulsado un hueco vacío del inicio. Estadísticas del día, duración de la espera, apps con freno, apps ocultas.

Con freno de serie: Instagram, TikTok y Facebook.

## Instalarlo en el móvil

1. En GitHub, pestaña **Releases** → descarga `freno.apk` desde el móvil.
2. Ábrelo y permite instalar apps de esa fuente si te lo pide.
3. Pulsa el botón de inicio y elige **Freno** como app de inicio "Siempre". (O en Ajustes de Freno → *Cambiar app de inicio*.)

Cada vez que se sube un cambio a `main`, GitHub compila un APK nuevo y lo publica en Releases. Se instala encima del anterior sin perder tus ajustes.

## Técnica

Kotlin + Jetpack Compose. Sin internet, sin cuentas; los datos se guardan solo en el móvil. Firma con una clave de pruebas fija (`app/freno-debug.keystore`); para publicar en Play Store hará falta una clave propia.
