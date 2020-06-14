-verbose
-printconfiguration proguard-final.pro

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# -dontnote **
-ignorewarnings

-dontwarn com.google.android.gms.gcm.GcmTaskService
-dontwarn android.support.transition.Transition
-dontwarn java.lang.ClassValue
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# keep exception names
-keepnames class * extends java.lang.Throwable

# Keep api names for metrics
-keepnames interface com.pr0gramm.app.api.pr0gramm.Api

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep until > okhttp 4.7.2
# https://github.com/square/okhttp/issues/6092
-keepnames class okhttp3.OkHttpClient

# keep enums!
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Preserve some attributes that may be required for reflection.
-keepattributes RuntimeVisible*Annotations,InnerClasses,EnclosingMethod,Signature

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Assume that we run on android 21 and up. This is an r8 config to improve
# dead code elimination on old devices. Should already be added automatically, but be on
# the sure side here.
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 21..2147483647;
}

# Ensure the custom, fast service loader implementation is removed and R8 can
# optimize the class loading & service discovery
-assumevalues class kotlinx.coroutines.internal.MainDispatcherLoader {
  boolean FAST_SERVICE_LOADER_ENABLED return false;
}

-checkdiscard class kotlinx.coroutines.internal.FastServiceLoader
-checkdiscard class kotlin.Metadata

## Firebase logging
-keep class * extends java.lang.Exception
