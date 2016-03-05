package android.support.v4.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.MoreObjects.firstNonNull;

/**
 * We need this to access some internal properties of the support library.
 * Good thing, we ship out a copy of the library with the app. If we didnt do that,
 * this might totally fail.
 */
public class SupportV4App {
    public static void activityFragmentsNoteStateNotSaved(FragmentActivity activity) {
        activity.mFragments.noteStateNotSaved();
    }

    public static List<Fragment> activityFragmentsActive(FragmentActivity activity) {
        return firstNonNull(activity.mFragments.getActiveFragments(null),
                Collections.<Fragment>emptyList());
    }

    public static int fragmentIndex(Fragment fragment) {
        return fragment.mIndex;
    }

    public static ArrayList<Fragment> fragmentChildFragmentManagerActive(Fragment fragment) {
        return ((FragmentManagerImpl) fragment.getChildFragmentManager()).mActive;
    }
}