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

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# keep exception names
-keepnames class * extends java.lang.Throwable

# Keep api names for metrics
-keepnames interface com.pr0gramm.app.api.pr0gramm.Api

# evernote android job library
-keep public class com.evernote.android.job.v21.PlatformJobService
-keep public class com.evernote.android.job.v14.PlatformAlarmService
-keep public class com.evernote.android.job.v14.PlatformAlarmReceiver
-keep public class com.evernote.android.job.JobBootReceiver
-keep public class com.evernote.android.job.JobRescheduleService


-keep class com.llamalab.safs.android.AndroidFileSystemProvider {
    <init>(...);
}

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# keep enums!
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Preserve some attributes that may be required for reflection.
-keepattributes RuntimeVisible*Annotations,InnerClasses,EnclosingMethod,SourceFile

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep setters in Views so that animations can still work.
-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Assume that we run on android 21 and up. This is an r8 config to improve
# dead code elimination on old devices. Should already be added automatically, but be on
# the sure side here.
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 21..2147483647;
}
