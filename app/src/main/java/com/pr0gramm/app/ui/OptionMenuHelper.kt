package com.pr0gramm.app.ui

import android.view.MenuItem
import com.google.common.base.Throwables
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Helps dispatching option menu clicks to the correct function.
 */
object OptionMenuHelper {
    private val logger = LoggerFactory.getLogger("OptionMenuHelper")

    @JvmStatic
    fun dispatch(instance: Any?, item: MenuItem?): Boolean {
        if (instance == null || item == null) {
            return false
        }

        for (method in instance.javaClass.methods) {
            val annotation = method.getAnnotation(OnOptionsItemSelected::class.java)
            if (annotation != null && item.itemId == annotation.value) {
                logger.info("dispatching menu action to " + method.name)

                val params = method.parameterTypes
                if (params.isEmpty()) {
                    return invoke(instance, method)
                } else if (params.size == 1 && params[0].isAssignableFrom(MenuItem::class.java)) {
                    return invoke(instance, method, item)
                } else {
                    throw IllegalArgumentException("Can not call " + method)
                }
            }
        }

        return false
    }

    private fun invoke(instance: Any, method: Method, vararg args: Any): Boolean {
        try {
            method.isAccessible = true

            val result = method.invoke(instance, *args)
            return result as? Boolean ?: (result != null || method.returnType == Void.TYPE)

        } catch (error: IllegalAccessException) {
            // should not occur
            return false

        } catch (e: InvocationTargetException) {
            throw Throwables.propagate(e.cause)
        }

    }

}
