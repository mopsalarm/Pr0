package com.pr0gramm.app.util.scripting;

import rx.functions.Func2;
import rx.functions.FuncN;

import static com.google.common.collect.Iterables.all;
import static java.util.Arrays.asList;

/**
 * A simple wrapper for a function with two arguments.
 */
public class BiFunction<T> implements FuncN<Object> {
    private final String name;
    private final Class<?> clazz;
    private final Func2<T, T, Object> function;

    BiFunction(String name, Class<T> clazz, Func2<T, T, Object> function) {
        this.name = name;
        this.clazz = clazz;
        this.function = function;
    }

    @Override
    public Object call(Object... args) {
        if (args.length != 2) {
            throw new InterpreterException("Need two arguments for function " + name);
        }

        if (!all(asList(args), clazz::isInstance)) {
            String msg = "Expect only arguments of type " + clazz.getSimpleName() + " by " + name;
            throw new InterpreterException(msg);
        }

        //noinspection unchecked
        return function.call((T) args[0], (T) args[1]);
    }
}
