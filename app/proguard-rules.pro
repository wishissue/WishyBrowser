# GeckoView is reached through JNI and reflection and ships its own consumer
# rules; these are a safety net. The native engine dwarfs the Java code, so
# keeping it whole costs almost nothing in size.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.geckoview.**
-dontwarn org.mozilla.gecko.**

# GeckoView's passkey code references Google Play Services (proprietary).
# This app deliberately excludes it and turns WebAuthn off, so those classes
# are never loaded. Tell R8 not to fail on the missing references.
-dontwarn com.google.android.gms.**

# Readable stack traces if something ever crashes.
-keepattributes SourceFile,LineNumberTable
