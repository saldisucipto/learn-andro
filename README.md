# NativeStarter

Android native starter project using Kotlin, Jetpack Compose, Gradle Kotlin DSL, and a physical Android device.

## Toolchain

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android Gradle Plugin 8.10.1
- Kotlin 2.0.21
- Gradle Wrapper 8.11.1
- Minimum Android API 26

## 1. Check environment

Open PowerShell in this folder:

```powershell
java -version
adb devices
```

Make sure your phone appears with status `device`.

Set the SDK path if it is not already configured:

```powershell
$env:ANDROID_HOME = "C:\Android"
$env:ANDROID_SDK_ROOT = "C:\Android"
```

For a permanent setup, define both variables in Windows Environment Variables.

## 2. Generate the Gradle Wrapper

The wrapper bootstrap script downloads Gradle 8.11.1 once and generates the wrapper files:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\bootstrap-wrapper.ps1
```

This creates `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`.
After that, project builds use the wrapper and do not require a global Gradle installation.

## 3. Accept SDK licenses if needed

```powershell
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

## 4. Build the debug APK

```powershell
.\gradlew.bat assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 5. Install and launch on the connected phone

```powershell
.\gradlew.bat installDebug
adb shell monkey -p com.example.nativestarter 1
```

## 6. View logs

```powershell
adb logcat
```

To filter Android runtime errors:

```powershell
adb logcat *:E
```

## Notes

- No Android Studio or Android emulator is required.
- The first Gradle build downloads Gradle plugins and dependencies, so an internet connection is required.
- The project uses JDK 17. Confirm `JAVA_HOME` points to JDK 17 if Gradle reports a Java version error.
- If PowerShell blocks the bootstrap script, use the process-scoped execution policy command shown above.
