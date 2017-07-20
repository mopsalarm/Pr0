# Add project specific ProGuard rules here.

-dontobfuscate
-dontwarn **
-ignorewarnings
-dontoptimize

-keepattributes *

-keep class com.pr0gramm.app.** { *; }

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
-keep class **$$ViewBinder { *; }


# this is for dart injection library
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

# keep androidplot
-keep class com.androidplot.** { *; }