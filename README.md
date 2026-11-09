# GORILLA — Standoff 2 companion

GORILLA is a separate Android companion app for the installed original Standoff 2 application.

## Features
- Launches the installed original Standoff 2 app (package: `com.axlebolt.standoff2`).
- User-enabled floating panel over other apps.
- Local weapon profile selector and saved sensitivity, crosshair-size and panel-opacity notes.
- Does not inject into the game, read game memory, automate input, aim, fire, track opponents or provide wallhack functionality.

## Build
Open this repository in Android Studio with JDK 17, sync Gradle, then choose **Build > Build APK(s)**.

The project uses compile SDK 35, target SDK 35 and minimum SDK 23. On Android 16, grant “Display over other apps” before starting the overlay. Device firmware may impose additional foreground-service restrictions.

Settings are stored locally by GORILLA. They do not change Standoff 2 game settings.
