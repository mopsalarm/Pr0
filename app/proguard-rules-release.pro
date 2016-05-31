# Add project specific ProGuard rules here.

# -dontobfuscate
-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses
-mergeinterfacesaggressively

-dontpreverify

-optimizationpasses 20
-optimizations !method/removal/parameter,field/marking/private,code/simplification/*,class/merging/vertical,!class/merging/horizontal

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
-dontwarn com.squareup.okhttp.**
-dontwarn android.net.http.**
-dontwarn retrofit2.Platform$Java8
-dontwarn com.squareup.leakcanary.DisplayLeakService
-dontwarn java.lang.ClassValue
-dontwarn com.facebook.stetho.okhttp3.**

-keepattributes *Annotation*,Signature,SourceFile,LineNumberTable

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

# this is for dart injection library
-dontwarn com.f2prateek.dart.internal.**
-keep class **$$ExtraInjector { *; }

# this is for butterknife
-dontwarn butterknife.**
-keep class **$$ViewBinder { *; }
