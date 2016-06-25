# Add project specific ProGuard rules here.

# -dontobfuscate
-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses
-mergeinterfacesaggressively

-dontpreverify

-optimizationpasses 20
-optimizations !method/removal/parameter,field/marking/private,code/simplification/*,class/merging/vertical,!class/merging/horizontal

-dontwarn **
-ignorewarnings

-keepattributes *Annotation*,Signature,SourceFile,LineNumberTable

# keep exception names
-keepnames class * extends java.lang.Throwable

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
-keep class **$$ExtraInjector { *; }

# this is for butterknife
-keep class **$$ViewBinder { *; }

