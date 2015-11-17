package com.pr0gramm.app.util.scripting;

import android.support.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import rx.functions.FuncN;

/**
 * A very simple lisp-like interpreter.
 */
public class Interpreter {
    private final ImmutableMap<String, FuncN<Object>> functions;

    public Interpreter(Map<String, FuncN<Object>> functions) {
        this.functions = ImmutableMap.copyOf(functions);
    }

    public Object evaluate(Scope scope, List<Object> program) {
        if (program.size() < 1 || !(program.get(0) instanceof String)) {
            throw new InterpreterException("Invalid program: " + program);
        }

        String funcName = (String) program.get(0);
        List<Object> arguments = resolve(scope, program.subList(1, program.size()));
        return invoke(funcName, arguments);
    }


    private List<Object> resolve(Scope scope, List<Object> symbols) {
        ArrayList<Object> result = new ArrayList<>(symbols.size());
        for (Object symbol : symbols) {
            result.add(evaluateSymbol(scope, symbol));
        }

        return result;
    }

    private Object evaluateSymbol(Scope scope, Object value) {
        if (value == null)
            return null;

        if (value instanceof String) {
            String string = (String) value;
            if (string.startsWith(".")) {
                // resolve the value of the variable
                String variableName = string.substring(1);
                Object variableValue = scope.get(variableName);
                if (variableValue == null)
                    throw new InterpreterException("Variable does not exist: " + variableName);

                return evaluateSymbol(scope, variableValue);
            } else {
                return string;
            }
        }

        if (value instanceof List) {
            //noinspection unchecked
            return evaluate(scope, (List<Object>) value);
        }

        // we only have long and doubles.
        if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float)
                return ((Number) value).doubleValue();

            return ((Number) value).longValue();
        }

        return value;
    }

    /**
     * Invokes the function of the given name with the given arguments.
     */
    private Object invoke(String name, List<Object> args) {
        FuncN<Object> function = functions.get(name);
        if (function == null) {
            throw new InterpreterException("Function not found: " + name);
        }

        return function.call(args.toArray());
    }

    public static final Map<String, FuncN<Object>> DEFAULT_FUNCTIONS = ImmutableMap.<String, FuncN<Object>>builder()
            .put("not", new UnaryFunction<>("not", Boolean.class, var -> !var))
            .put("=", new BiFunction<>("=", Object.class, Objects::equal))
            .put("<", new BiFunction<>("<", Number.class, (a, b) -> a.doubleValue() < b.doubleValue()))
            .put("<=", new BiFunction<>("<=", Number.class, (a, b) -> a.doubleValue() <= b.doubleValue()))
            .put(">", new BiFunction<>(">", Number.class, (a, b) -> a.doubleValue() > b.doubleValue()))
            .put(">=", new BiFunction<>(">=", Number.class, (a, b) -> a.doubleValue() >= b.doubleValue()))
            .put("or", Functions::or)
            .put("and", Functions::and)
            .put("+", Functions::plus)
            .put("-", Functions::minus)
            .put("*", Functions::multiply)
            .put("/", Functions::divide)
            .build();

    public static class Scope {
        private final Map<String, Object> variables;

        private Scope(Map<String, Object> variables) {
            this.variables = ImmutableMap.copyOf(variables);
        }

        @Nullable
        public Object get(String name) {
            return this.variables.get(name);
        }

        public static Scope of(Map<String, Object> variables) {
            return new Scope(variables);
        }

        public static Scope empty() {
            return of(Collections.emptyMap());
        }
    }

}
