# Add project specific ProGuard rules here.

-dontobfuscate

-dontwarn java.nio.**
-dontwarn java.lang.invoke.**
-dontwarn sun.misc.Unsafe
-dontwarn com.actionbarsherlock.**
-dontwarn com.google.appengine.**
-dontwarn com.google.android.maps.**
-dontwarn roboguice.**Sherlock*
-dontwarn roboguice.activity.RoboMapActivity
-dontwarn org.joda.**
-dontwarn org.codehaus.**.**
-dontwarn org.slf4j.**
-dontwarn com.google.gson.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn net.i2p.**
-dontwarn org.apache.http.**
-dontwarn retrofit.client.**
-dontwarn com.squareup.okhttp.**
-dontwarn android.net.http.**
-dontwarn retrofit2.Platform$Java8
-dontwarn com.squareup.leakcanary.DisplayLeakService
-dontwarn java.lang.ClassValue

-keepattributes *

# keep database model
-keep class * extends com.orm.SugarRecord { *; }

# this is for dispatching menu clicks via reflection
-keepclassmembers class * {
    @com.pr0gramm.app.ui.OnOptionsItemSelected <methods>;
}

# keeps views and other stuff. Support library fails without this.
-keep class android.support.v7.widget.SearchView { *; }

# for gifs
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}
-keep class pl.droidsonroids.gif.GifInfoHandle{<init>(long,int,int,int);}

# this is for butterknife
-dontwarn butterknife.internal.**
-keep class **$$ViewBinder { *; }


# this is for dart injection library
-dontwarn com.f2prateek.dart.internal.**
-keep class **$$ExtraInjector { *; }

# for dart 2.0 only
-keep class **Henson { *; }
-keep class **$$IntentBuilder { *; }

# keep enums!
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# keep LeakCanary.
-keep class org.eclipse.mat.** { *; }
-keep class com.squareup.leakcanary.** { *; }

-keep class com.facebook.stetho.** {*;}
