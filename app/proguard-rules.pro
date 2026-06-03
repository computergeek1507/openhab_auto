# Default optimized ProGuard/R8 rules apply via proguard-android-optimize.txt.
# Most AndroidX, OkHttp, and kotlinx libraries ship their own consumer rules.

# org.json is bundled as a dependency here; keep it intact.
-keep class org.json.** { *; }

# Keep the Car App service and screen entry points (referenced from the manifest /
# reflectively by the host). Manifest-referenced components are kept automatically,
# but keep their members to be safe against host reflection.
-keep class com.openhab.auto.car.** { *; }
