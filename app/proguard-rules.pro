# WebView and Compose ship their own consumer rules. Keep stack traces readable.
-keepattributes SourceFile,LineNumberTable

# OkHttp optional TLS providers (not present on Android): silence R8 missing-class warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
