package android.support.v4.app;

import java.util.ArrayList;

/**
 * We need this to access some internal properties of the support library.
 * Good thing, we ship out a copy of the library with the app. If we didnt do that,
 * this might totally fail.
 */
public class SupportV4App {
    public static void activityFragmentsNoteStateNotSaved(FragmentActivity activity) {
        activity.mFragments.noteStateNotSaved();
    }

    public static ArrayList<Fragment> activityFragmentsActive(FragmentActivity activity) {
        return activity.mFragments.mActive;
    }

    public static int fragmentIndex(Fragment fragment) {
        return fragment.mIndex;
    }

    public static ArrayList<Fragment> fragmentChildFragmentManagerActive(Fragment fragment) {
        return ((FragmentManagerImpl) fragment.getChildFragmentManager()).mActive;
    }
}