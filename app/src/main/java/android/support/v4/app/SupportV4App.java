package android.support.v4.app;

/**
 * We need this to access some internal properties of the support library.
 * Good thing, we ship out a copy of the library with the app. If we didnt do that,
 * this might totally fail.
 */
public class SupportV4App {
    public static FragmentManager fragmentChildFragmentManager(Fragment fragment) {
        return fragment.mChildFragmentManager;
    }
}