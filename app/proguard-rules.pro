-dontwarn io.github.libxposed.annotation.**
# io.github.libxposed:service references these annotations but does not depend on them; they are
# CLASS-retention only and are never loaded at runtime. Without this, R8 fails the release build
# with "Missing class androidx.annotation.NonNull".
-dontwarn androidx.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
# Keep the entry class name readable: R8 may otherwise rename it and rewrite java_init.list to a
# single letter, which works but makes logcat and crash reports unreadable.
-keep,allowoptimization class io.github.nku100.carrierims.hook.CarrierConfigModule {
    public <init>();
}
