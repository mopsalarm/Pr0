-allowaccessmodification
-repackageclasses

# -optimizations !field/propagation/value
# -optimizations !method/removal/parameter
# -optimizations !class/merging/horizontal
# -optimizations !field/removal/writeonly
-optimizations **
-optimizationpasses 7

# remove all not so important logging
-assumenosideeffects class * implements org.slf4j.Logger {
      public *** trace(...);
      public *** debug(...);
}

-assumenosideeffects class android.util.Log {
      public *** d(...);
      public *** v(...);
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*NotNull(java.lang.Object, java.lang.String);
}
