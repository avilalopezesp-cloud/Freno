# Paraqué (antes Freno)

App Android que pone una pausa antes de abrir redes sociales. Funciona como pantalla de inicio (launcher) negra y solo texto, pero no se presenta como "launcher": es una solución al uso compulsivo.

- Kotlin + Jetpack Compose, una sola Activity (`MainActivity`) con pantallas en `Screens.kt` y `Stats.kt`.
- `FrenoAccessibilityService`: detecta la app en primer plano, muestra la pausa, cuenta el tiempo, avisa y aplica el límite.
- `FrenoPrefs`: ajustes y estadísticas en SharedPreferences, solo en el móvil (sin internet ni cuentas).
- Tono neutro: sin rachas, medallas ni mensajes que regañen. Textos de la app en español.
- Objetivo: publicar en Google Play. Nada que necesite adb o permisos que Play no permita.
- El paquete sigue siendo `com.freno.launcher` (cambiarlo obliga a reinstalar y se pierden los datos).

## Compilar y probar
- Android Studio: abrir la carpeta, elegir un emulador o el móvil por USB y pulsar Run.
- Terminal: `./gradlew installDebug` (o `gradle installDebug`) con el emulador abierto.
- Cada push a `main` compila en GitHub Actions y publica `freno.apk` en Releases.
