-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 10
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,method/inlining/*,code/simplification/*,class/merging/vertical,!class/merging/horizontal,!method/removal/parameter


# remove all not so important logging
-assumenosideeffects class * implements org.slf4j.Logger {
      public *** trace(...);
      public *** debug(...);
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

-keepclassmembers class **$WhenMappings {
    <fields>;
}
