# Add project specific ProGuard rules here.

# -dontobfuscate
-flattenpackagehierarchy

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
-dontwarn android.net.http.**
-dontwarn retrofit.Platform$Java8
-dontwarn com.squareup.leakcanary.DisplayLeakService
-dontwarn java.lang.ClassValue

# -keepattributes *Annotation*,Signature,SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature

# keep our application
-keep class com.pr0gramm.app.orm.** { *; }
-keep class com.pr0gramm.app.vpx.** { *; }
-keep class com.pr0gramm.app.api.** { *; }
-keep class com.pr0gramm.app.services.gif.** { *; }

# keep retrofit-annotations and methods
-keep class retrofit.** { *; }

# keep native  methods/names
-keepclasseswithmembernames class * {
    native <methods>;
}

# keeps views and other stuff. Support library fails without this.
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# for gifs
-keep public class pl.droidsonroids.gif.GifIOException{<init>(int);}
-keep class pl.droidsonroids.gif.GifInfoHandle{<init>(long,int,int,int);}


# this is for dart injection library
-dontwarn com.f2prateek.dart.internal.**
-keep class **$$ExtraInjector { *; }
-keepclasseswithmembernames class * {
    @com.f2prateek.dart.* <fields>;
}

#for dart 2.0 only
-keep class **Henson { *; }
-keep class **$$IntentBuilder { *; }
