# ML Kit barcode scanning relies on reflection for internal model loading.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# CameraX uses reflection to discover Camera2 vendor extensions.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep the IME service entry point (referenced only from the manifest).
-keep class com.example.nativestarter.ime.ScannerImeService { *; }

# Keep Compose runtime metadata used by the tooling/preview pipeline.
-dontwarn androidx.compose.**

# Strip verbose logging in release builds.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
