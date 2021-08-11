-allowaccessmodification
-repackageclasses
-optimizations *

# remove all those extra checking in release builds.
#
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
}


-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keep class kotlin.coroutines.Continuation
-keep class retrofit2.Response