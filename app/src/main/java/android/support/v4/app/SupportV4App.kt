package android.support.v4.app

/**
 * We need this to access some internal properties of the support library.
 * Good thing, we ship out a copy of the library with the app. If we didnt do that,
 * this might totally fail.
 */
object SupportV4App {
    @JvmStatic
    fun fragmentChildFragmentManager(fragment: Fragment): FragmentManager {
        return fragment.mChildFragmentManager
    }
}