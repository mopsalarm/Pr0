-verbose

-dontwarn **
-dontnote **
-ignorewarnings

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
-keep class pl.droidsonroids.gif.GifInfoHandle{<init>(long,int,int,int);}

# evernote android job library
-keep public class com.evernote.android.job.v21.PlatformJobService
-keep public class com.evernote.android.job.v14.PlatformAlarmService
-keep public class com.evernote.android.job.v14.PlatformAlarmReceiver
-keep public class com.evernote.android.job.JobBootReceiver
-keep public class com.evernote.android.job.JobRescheduleService

# keep for google cast.
-keep public class com.pr0gramm.app.services.cast.CastOptionsProvider { *; }
-keep public class android.support.v7.app.MediaRouteActionProvider { public <init>(...); }

# we need this for the publicsuffixes.gz
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# keep enums!
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Preserve some attributes that may be required for reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

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

-keepclassmembers class **.R$* {
    public static <fields>;
}

# Understand the @Keep support annotation.
-keep class android.support.annotation.Keep

-keep @android.support.annotation.Keep class * {*;}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <methods>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <fields>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <init>(...);
}