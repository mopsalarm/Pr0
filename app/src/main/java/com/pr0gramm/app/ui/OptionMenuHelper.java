package com.pr0gramm.app.ui;

import android.view.MenuItem;

import com.google.common.base.Throwables;
import com.pr0gramm.app.util.AndroidUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 */
public class OptionMenuHelper {
    private static final Logger logger = LoggerFactory.getLogger("OptionMenuHelper");

    private OptionMenuHelper() {
    }

    public static boolean dispatch(Object instance, MenuItem item) {
        if (instance == null || item == null) {
            return false;
        }

        for (Method method : instance.getClass().getMethods()) {
            OnOptionsItemSelected annotation = method.getAnnotation(OnOptionsItemSelected.class);
            if (annotation != null && item.getItemId() == annotation.value()) {
                logger.info("dispatching menu action to " + method.getName());

                Class<?>[] params = method.getParameterTypes();
                if (params.length == 0) {
                    return invoke(instance, method);
                } else if (params.length == 1 && params[0].isAssignableFrom(MenuItem.class)) {
                    return invoke(instance, method, item);
                } else {
                    throw new IllegalArgumentException("Can not call " + method);
                }
            }
        }

        return false;
    }

    private static boolean invoke(Object instance, Method method, Object... args) {
        try {
            Object result = method.invoke(instance, args);
            return result instanceof Boolean
                    ? (boolean) result
                    : result != null || method.getReturnType() == void.class;

        } catch (IllegalAccessException error) {
            // should not occur
            AndroidUtility.logToCrashlytics(error);
            return false;

        } catch (InvocationTargetException e) {
            throw Throwables.propagate(e.getCause());
        }
    }

}
