# Tag library builds ID3/MP4/FLAC frames by reflection - keep it intact.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Desktop-only classes referenced by the tag library (not present on Android).
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn javax.naming.**
-dontwarn javax.xml.**
-dontwarn org.w3c.**
-dontwarn sun.**

# Networking
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
