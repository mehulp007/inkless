# Release builds run R8 (see app/build.gradle.kts). Most libraries ship their own rules; these
# cover what they don't.

# Tesseract4Android's native code calls back into these classes by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
