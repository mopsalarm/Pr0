-allowaccessmodification
-repackageclasses
-optimizations **

-keepattributes !*

# remove all those extra checking in release builds.
#
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
}