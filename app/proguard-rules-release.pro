-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 10
-optimizations field/marking/private,!method/removal/parameter,method/marking/static,method/inlining/*,!field/*,code/simplification/*,class/merging/vertical,!class/merging/horizontal


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