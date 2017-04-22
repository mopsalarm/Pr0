# Add project specific ProGuard rules here.

-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-dontpreverify
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 20
-optimizations !method/removal/parameter,method/marking/static,method/inlining/*,field/marking/private,code/simplification/*,class/merging/vertical,!class/merging/horizontal

-dontwarn **
-ignorewarnings

-keepattributes *Annotation*,SourceFile,LineNumberTable

# keep exception names
-keepnames class * extends java.lang.Throwable

# this is for dispatching menu clicks via reflection
-keepclassmembers class * {
    @com.pr0gramm.app.ui.OnOptionsItemSelected <methods>;
}

# We keep all the names of the api interface.
-keepclasseswithmembernames class com.pr0gramm.app.api.pr0gramm.Api

# for gifs
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}

# this is for dart injection library
-keep class **$$ExtraInjector { *; }

# this is for butterknife
-keep class **$$ViewBinder { *; }

-keep public class com.evernote.android.job.v21.PlatformJobService
-keep public class com.evernote.android.job.v14.PlatformAlarmService
-keep public class com.evernote.android.job.v14.PlatformAlarmReceiver
-keep public class com.evernote.android.job.JobBootReceiver
-keep public class com.evernote.android.job.JobRescheduleService

# keep enums!
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
