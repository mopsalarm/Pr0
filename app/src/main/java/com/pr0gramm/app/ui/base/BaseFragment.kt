package com.pr0gramm.app.ui.base

import android.content.Context
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import com.pr0gramm.app.Logger
import com.pr0gramm.app.time
import com.pr0gramm.app.util.di.LazyInjectorAware
import com.pr0gramm.app.util.di.PropertyInjector
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A fragment that provides lifecycle events as an observable.
 */
abstract class BaseFragment(name: String, @LayoutRes layoutId: Int = 0) : Fragment(layoutId), HasViewCache, LazyInjectorAware {
    protected val logger: Logger = Logger(name)

    override val injector: PropertyInjector = PropertyInjector()

    override val viewCache: ViewCache = ViewCache { view?.findViewById(it) }

    override fun onAttach(context: Context) {
        logger.time("Injecting services") { injector.inject(context) }
        super.onAttach(context)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        this.viewCache.reset()
    }

    fun setTitle(title: String) {
        val activity = activity as? AppCompatActivity ?: return
        activity.title = title
    }
}


fun <T : ViewBinding> Fragment.bindViews(bind: (root: View) -> T): ReadOnlyProperty<Fragment, T> {
    return object : ReadOnlyProperty<Fragment, T>, LifecycleEventObserver {
        private var binding: T? = null

        override fun getValue(thisRef: Fragment, property: KProperty<*>): T {
            // return current binding
            binding?.let { return it }

            // add us as an observer to reset the view once it is destroyed.
            viewLifecycleOwner.lifecycle.addObserver(this)

            // create & set a new binding
            return bind(requireView()).also { binding = it }
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event === Lifecycle.Event.ON_DESTROY) {
                // reset the current binding if the fragments view gets destroyed.
                binding = null
            }
        }
    }
}
