package androidx.fragment.app

/**
 * We need this to access some internal properties of the support library.
 * Good thing, we ship out a copy of the library with the app. If we didnt do that,
 * this might totally fail.
 */
object SupportV4App {
    fun fragmentChildFragmentManager(fragment: androidx.fragment.app.Fragment): androidx.fragment.app.FragmentManager? {
        return fragment.mChildFragmentManager
    }
}