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
-dontwarn java.lang.ClassValue

# keep annotations
-keepattributes *Annotation*

# keep our application
-keep class com.pr0gramm.** {
    *;
}

# keep retrofit-annotations and methods
-keep class retrofit.** { *; }

# roboguice - keep classes with inject-constructor
-keep class roboguice.inject.** { *; }
-keep class javax.annotation.** { *; }
-keepclasseswithmembers class * {
    @com.google.inject.Inject public <init>(...);
}

# we need this for roboguice
-keep class * extends com.google.inject.AnnotationDatabase {
    *;
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
