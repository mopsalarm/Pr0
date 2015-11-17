package com.pr0gramm.app.util.scripting;

import java.util.List;

import rx.functions.Func2;

import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.any;
import static java.util.Arrays.asList;

/**
 * Functions for the interpreter
 */
class Functions {
    private Functions() {
    }

    public static Object and(Object[] values_) {
        List<Object> values = asList(values_);
        if (!all(values, val -> val instanceof Boolean))
            throw new InterpreterException("All arguments must be boolean for 'and', got: " + values);

        return all(values, Boolean.TRUE::equals);
    }

    public static Object or(Object[] values_) {
        List<Object> values = asList(values_);
        if (!all(values, val -> val instanceof Boolean))
            throw new InterpreterException("All arguments must be boolean for 'or', got: " + values);

        return any(values, Boolean.TRUE::equals);
    }

    public static Number plus(Object[] values_) {
        return reduceNumbers(values_, (a, b) -> a + b, (a, b) -> a + b);
    }

    public static Number minus(Object[] values_) {
        return reduceNumbers(values_, (a, b) -> a - b, (a, b) -> a - b);
    }

    public static Number multiply(Object[] values_) {
        return reduceNumbers(values_, (a, b) -> a * b, (a, b) -> a * b);
    }

    public static Number divide(Object[] values_) {
        return reduceNumbers(values_, (a, b) -> a / b, (a, b) -> a / b);
    }

    public static Object not(Object[] arguments) {
        Object value = arguments.length == 1 ? arguments[0] : null;
        if (!(value instanceof Boolean))
            throw new InterpreterException("'not' expects one boolean as parameter.");

        return !((boolean) value);
    }

    private static Number reduceNumbers(Object[] values_, Func2<Long, Long, Long> funcLongs, Func2<Double, Double, Double> funcDouble) {
        if (values_.length == 0)
            return 0L;

        if (!all(asList(values_), val -> val instanceof Double || val instanceof Long)) {
            String msg = "All values must be numbers, got: " + asList(values_);
            throw new InterpreterException(msg);
        }

        //noinspection unchecked
        List<Number> values = (List<Number>) (List<?>) asList(values_);
        Number result = values.get(0);
        for (int idx = 1; idx < values.size(); idx++) {
            // add two numbers
            Number value = values.get(idx);
            if (result instanceof Double || value instanceof Double) {
                result = funcDouble.call(result.doubleValue(), value.doubleValue());
            } else if (result instanceof Long || value instanceof Long) {
                result = funcLongs.call(result.longValue(), value.longValue());
            }
        }

        return result;
    }
}
