# Add project specific ProGuard rules here.

-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-dontpreverify
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 20
-optimizations !method/removal/parameter,field/marking/private,code/simplification/*,class/merging/vertical,!class/merging/horizontal

-dontwarn **
-ignorewarnings

-keepattributes *Annotation*,Signature,SourceFile,LineNumberTable

# keep exception names
-keepnames class * extends java.lang.Throwable

# this is for dispatching menu clicks via reflection
-keepclassmembers class * {
    @com.pr0gramm.app.ui.OnOptionsItemSelected <methods>;
}

# for gifs
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}

# this is for dart injection library
-keep class **$$ExtraInjector { *; }

# this is for butterknife
-keep class **$$ViewBinder { *; }
