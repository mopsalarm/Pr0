# Add project specific ProGuard rules here.

-verbose

-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-dontpreverify
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 10
-optimizations field/marking/private,!method/removal/parameter,method/marking/static,method/inlining/*,!field/*,code/simplification/*,class/merging/vertical,!class/merging/horizontal

-dontwarn **
-dontnote **
-ignorewarnings

-keepattributes SourceFile

# keep exception names
-keepnames class * extends java.lang.Throwable

# this is for dispatching menu clicks via reflection
-keepclassmembers class * {
    @com.pr0gramm.app.ui.OnOptionsItemSelected <methods>;
}

# We keep all the names of the api interface.
-keepnames class com.pr0gramm.app.api.pr0gramm.Api { *; }

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

# remove all not so important logging
-assumenosideeffects class * implements org.slf4j.Logger {
      public *** trace(...);
      public *** debug(...);
}

#-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
#    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
#}

-keepclassmembers class **$WhenMappings {
    <fields>;
}