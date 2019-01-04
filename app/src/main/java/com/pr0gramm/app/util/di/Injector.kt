package com.pr0gramm.app.util.di

import android.content.Context
import com.pr0gramm.app.ApplicationClass
import com.pr0gramm.app.ui.base.AsyncScope
import com.pr0gramm.app.ui.fragments.FeedFragment.Companion.logger
import com.pr0gramm.app.util.debug
import com.pr0gramm.app.util.doInBackground
import com.pr0gramm.app.util.time
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface Provider<T : Any> {
    fun get(injector: Injector): T
}

class InstanceProvider<T : Any>(private val value: T) : Provider<T> {
    override fun get(injector: Injector): T {
        return value
    }
}

private class RecursionChecker {
    private val flag = AtomicBoolean()

    fun check() {
        if (!flag.compareAndSet(false, true)) {
            throw IllegalStateException("Already under construction.")
        }
    }
}

class SingletonProvider<T : Any>(
        private val factory: Injector.() -> T) : Provider<T> {

    @Volatile
    private var value: Any = NoValue

    private val recursionChecker = RecursionChecker()

    override fun get(injector: Injector): T {
        if (value === NoValue) {
            synchronized(this) {
                if (value === NoValue) {
                    recursionChecker.check()
                    value = factory(injector)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}

class AsyncSingletonProvider<T : Any>(
        private val factory: suspend Injector.() -> T) : Provider<T> {

    private val recursionChecker = RecursionChecker()

    private var value: Any = NoValue
    val result = CompletableDeferred<T>()

    fun init(injector: Injector) {
        recursionChecker.check()

        AsyncScope.launch {
            try {
                val value = factory(injector)

                // set for quicker access
                this@AsyncSingletonProvider.value = value

                result.complete(value)
            } catch (err: Exception) {
                result.completeExceptionally(err)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(injector: Injector): T {
        if (value !== NoValue)
            return value as T

        return runBlocking { result.await() }
    }
}

private object NoValue

class Module private constructor() {
    val providers = mutableMapOf<Injector.Key, Provider<*>>()

    inline fun <reified T : Any> bind(tag: String? = null): Binder<T> {
        return Binder(T::class.java, tag)
    }

    fun register(key: Injector.Key, provider: Provider<*>) {
        providers[key] = provider
    }

    inner class Binder<T : Any>(val type: Class<T>, val tag: String? = null) {
        infix fun with(provider: Provider<T>) {
            register(Injector.Key(type, tag), provider)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T : Any> instance(value: T): Provider<T> = InstanceProvider(value)

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T : Any> singleton(noinline factory: Injector.() -> T): Provider<T> {
        return SingletonProvider(factory)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun <T : Any> eagerSingleton(noinline factory: suspend Injector.() -> T): Provider<T> {
        return AsyncSingletonProvider(factory)
    }

    inline fun <T : Any> provider(crossinline factory: Injector.() -> T): Provider<T> {
        return object : Provider<T> {
            override fun get(injector: Injector): T = factory(injector)
        }
    }

    companion object {
        fun build(config: Module.() -> Unit): Injector {
            val providers = Module().apply(config).providers.toMap()

            val injector = Injector(providers)

            // filter out all eager singletons
            providers.values.forEach { prov ->
                if (prov is AsyncSingletonProvider<*>) {
                    prov.init(injector)
                }
            }

            debug {
                eagerValidate(injector, providers.keys)
            }

            return injector
        }

        private fun eagerValidate(injector: Injector, keys: Set<Injector.Key>) {
            doInBackground {
                try {
                    logger.time("Validating ${keys.size} dependencies.") {
                        // initialize everything once in debug mode
                        keys.forEach { key ->
                            logger.debug { "Now getting instance for key $key" }
                            injector.instance(key)
                        }
                    }
                } catch (err: Throwable) {
                    err.printStackTrace()

                    // let the app crash
                    Thread.sleep(10000)
                    System.exit(1)
                }
            }
        }
    }
}

class Injector(private val providers: Map<Injector.Key, Provider<*>>) {
    inline fun <reified T : Any> instance(): T {
        return instance(Key(T::class.java, null))
    }

    inline fun <reified T : Any> instance(tag: String): T {
        return instance(Key(T::class.java, tag))
    }

    fun <T : Any> instance(key: Key): T {
        val provider = providers.get(key)
                ?: throw IllegalArgumentException("No dependency for key $key")

        @Suppress("UNCHECKED_CAST")
        return provider.get(this) as T
    }

    suspend fun waitForEagerSingletons() {
        providers.values.forEach { prov ->
            val p = prov as? AsyncSingletonProvider
            p?.result?.join()
        }
    }

    data class Key(val type: Class<*>, val tag: String?)
}

inline val Context.injector: Injector get() = (applicationContext as ApplicationClass).injector

class PropertyInjector {
    private var injector: Injector? = null
    private val properties = mutableListOf<InjectedProperty<Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> instance(key: Injector.Key): ReadOnlyProperty<Any?, T> {
        val injector: Injector? = injector

        return if (injector == null) {
            val prop = InjectedProperty<T>(key)
            properties += prop as InjectedProperty<Any>
            prop
        } else {
            StaticProperty(injector.instance<T>(key))
        }
    }

    fun inject(context: Context) {
        val injector = context.injector

        if (this.injector == null) {
            this.injector = injector
            properties.forEach { it.inject(injector) }
        }
    }
}

private class StaticProperty<T : Any>(private val value: T) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

private class InjectedProperty<T : Any>(private val key: Injector.Key) : ReadOnlyProperty<Any?, T> {
    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
            ?: throw IllegalStateException("injected property ${property.name} not initialized")

    fun inject(injector: Injector) {
        value = injector.instance(key) as T
    }
}

interface LazyInjectorAware {
    val injector: PropertyInjector
}

inline fun <reified T : Any> LazyInjectorAware.instance(tag: String? = null): ReadOnlyProperty<Any?, T> {
    return injector.instance(Injector.Key(T::class.java, tag))
}

interface InjectorAware {
    val injector: Injector
}

inline fun <reified T : Any> InjectorAware.instance(): T {
    return injector.instance()
}

inline fun <reified T : Any> InjectorAware.instance(tag: String): T {
    return injector.instance(tag)
}

inline fun <reified T : Any> lazyInject(tag: String? = null, crossinline injector: () -> Injector): Lazy<T> {
    return lazy(LazyThreadSafetyMode.PUBLICATION) {
        injector().instance<T>(Injector.Key(T::class.java, tag))
    }
}
