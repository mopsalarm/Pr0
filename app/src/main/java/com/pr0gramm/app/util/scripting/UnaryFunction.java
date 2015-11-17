package com.pr0gramm.app.util.scripting;

import rx.functions.Func1;
import rx.functions.FuncN;

import static com.google.common.collect.Iterables.all;
import static java.util.Arrays.asList;

/**
 * A simple unary function
 */
public class UnaryFunction<T> implements FuncN<Object> {
    private final String name;
    private final Class<T> clazz;
    private final Func1<T, Object> function;

    public UnaryFunction(String name, Class<T> clazz, Func1<T, Object> function) {
        this.name = name;
        this.clazz = clazz;
        this.function = function;
    }

    @Override
    public Object call(Object... args) {
        if (args.length != 1) {
            throw new InterpreterException("Need one arguments for function " + name);
        }

        if (!all(asList(args), clazz::isInstance)) {
            String msg = "Expect only one argument of type " + clazz.getSimpleName() + " by " + name;
            throw new InterpreterException(msg);
        }

        //noinspection unchecked
        return function.call((T) args[0]);
    }
}
