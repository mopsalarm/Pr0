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

# for moshi we need to keep the json adapters.
-keep,allowoptimization class **JsonAdapter {
    <init>(...);
    <fields>;
}

-keepnames @com.squareup.moshi.JsonClass class *

-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}

# Keep mBackgroundTintHelper etc in support library who are used in
# a bad access pattern. A null check is mistakenly optimized away.
# see https://sourceforge.net/p/proguard/bugs/531/
-keepclassmembers,allowshrinking,allowobfuscation class  android.support.** {
    !static final <fields>;
    ** m*Helper;
}

# Assume that we run on android 14 and up. This is an r8 config to improve
# dead code elimination on old devices.
-assumevalues class android.os.Build$VERSION {
    int SDK_INT return 17..2147483647;
}

# BEGIN moshi rules
#
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

-keep @com.squareup.moshi.JsonQualifier interface *

# Enum field names are used by the integrated EnumJsonAdapter.
# Annotate enums with @JsonClass(generateAdapter = false) to use them with Moshi.
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
}

# The name of @JsonClass types is used to look up the generated adapter.
-keepnames @com.squareup.moshi.JsonClass class *

# Retain generated JsonAdapters if annotated type is retained.
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>(...);
    <fields>;
}

# END moshi rules

