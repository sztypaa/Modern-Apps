-dontwarn com.google.re2j.**
-dontwarn java.beans.**
-dontobfuscate

# Tesseract
-keep class com.googlecode.tesseract.android.** { *; }

# LiteRT LM / Gemma 4
-keep class com.google.ai.edge.litertlm.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}