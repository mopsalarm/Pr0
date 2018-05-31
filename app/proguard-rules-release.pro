-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses

-optimizationpasses 5

-optimizations !field/removal/writeonly
-optimizations !field/propagation/value
-optimizations !class/merging/horizontal
-optimizations !method/removal/parameter
-optimizations **

# remove all not so important logging
-assumenosideeffects class * implements org.slf4j.Logger {
      public *** trace(...);
      public *** debug(...);
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*NotNull(java.lang.Object, java.lang.String);
}

-keepclassmembers class **$WhenMappings {
    <fields>;
}
