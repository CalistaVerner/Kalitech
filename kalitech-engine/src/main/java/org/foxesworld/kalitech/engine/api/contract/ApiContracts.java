package org.foxesworld.kalitech.engine.api.contract;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiContracts {

    private final ConcurrentHashMap<Method, CompiledMethod> cache = new ConcurrentHashMap<>(256);

    private static void enforceThread(String apiId, Method m, ApiThreadRule rule, ThreadChecker checker) {
        if (rule == null || rule == ApiThreadRule.ANY) return;
        if (checker == null) return;

        final boolean jme = safeIsJme(checker);

        if (rule == ApiThreadRule.JME && !jme) {
            throw fail(apiId, m, -1, "ApiThread(JME)", "must be called/executed on JME thread");
        }
        if (rule == ApiThreadRule.NOT_JME && jme) {
            throw fail(apiId, m, -1, "ApiThread(NOT_JME)", "must NOT be called/executed on JME thread");
        }
    }

    private static boolean safeIsJme(ThreadChecker checker) {
        try {
            return checker.isJmeThread();
        } catch (Throwable t) {
            return false;
        }
    }

    private static ContractMode resolveMode(ContractMode moduleMode, ApiMethod.Mode methodMode) {
        if (methodMode == null || methodMode == ApiMethod.Mode.DEFAULT) return moduleMode;
        return switch (methodMode) {
            case OFF -> ContractMode.OFF;
            case STRICT -> ContractMode.STRICT;
            case CLAMP -> ContractMode.CLAMP;
            case DEFAULT -> moduleMode;
        };
    }

    // ---------------- Internals ----------------

    private static CompiledMethod build(Method m) {
        final ApiMethod am = m.getAnnotation(ApiMethod.class);

        final ApiThreadRule thread = (am != null) ? am.thread() : ApiThreadRule.ANY;
        final boolean sync = (am != null) && am.sync();
        final ApiMethod.Mode mode = (am != null) ? am.mode() : ApiMethod.Mode.DEFAULT;
        final ApiFlag[] flags = (am != null) ? am.flags() : new ApiFlag[0];
        final ApiCostHint cost = (am != null) ? am.cost() : ApiCostHint.NORMAL;
        final ApiParam[] headerParams = (am != null) ? am.params() : new ApiParam[0];

        final Parameter[] ps = m.getParameters();
        final ParamRules[] rules = new ParamRules[ps.length];

        boolean any = false;

        // 1) parameter-level annotations
        for (int i = 0; i < ps.length; i++) {
            final Parameter p = ps[i];
            final Annotation[] anns = p.getAnnotations();
            if (anns.length == 0) continue;

            final ParamRules r = new ParamRules();

            for (Annotation a : anns) {
                final Class<? extends Annotation> t = a.annotationType();
                if (t == NotNull.class) r.notNull = true;
                else if (t == Finite.class) r.finite = true;
                else if (t == Range.class) {
                    Range rr = (Range) a;
                    r.range = new RangeRule(rr.min(), rr.max());
                }
            }

            if (r.notNull || r.finite || r.range != null) {
                rules[i] = r;
                any = true;
            }
        }

        // 2) method-header params (merge/override)
        if (headerParams != null && headerParams.length > 0) {
            for (ApiParam hp : headerParams) {
                final int idx = hp.index();
                if (idx < 0 || idx >= ps.length) continue;

                ParamRules r = rules[idx];
                if (r == null) r = new ParamRules();

                if (hp.notNull()) r.notNull = true;
                if (hp.finite()) r.finite = true;

                final double min = hp.min();
                final double max = hp.max();
                final boolean hasRange = !(Double.isInfinite(min) && min < 0) || !(Double.isInfinite(max) && max > 0);
                // Simpler: if user set something other than default infinities, treat as range.
                final boolean defaultMin = Double.NEGATIVE_INFINITY == min;
                final boolean defaultMax = Double.POSITIVE_INFINITY == max;
                if (!(defaultMin && defaultMax)) {
                    r.range = new RangeRule(min, max);
                }

                rules[idx] = r;
                any = true;
            }
        }

        return new CompiledMethod(any ? rules : new ParamRules[0], thread, sync, mode, flags, cost);
    }

    private static ApiContractException fail(String apiId, Method m, int idx, String rule, String tail) {
        final String sig = m.getDeclaringClass().getSimpleName() + "." + m.getName();
        final String msg = (idx >= 0)
                ? "[api][contract] " + apiId + " " + sig + " param[" + idx + "] violates " + rule + ": " + tail
                : "[api][contract] " + apiId + " " + sig + " violates " + rule + ": " + tail;
        return new ApiContractException(apiId, sig, idx, rule, msg);
    }

    private static Object castNumberLike(Number like, double v) {
        if (like instanceof Integer) return (int) v;
        if (like instanceof Long) return (long) v;
        if (like instanceof Short) return (short) v;
        if (like instanceof Byte) return (byte) v;
        if (like instanceof Float) return (float) v;
        return v; // Double default
    }

    public CompiledMethod compile(Method m) {
        return cache.computeIfAbsent(Objects.requireNonNull(m, "m"), ApiContracts::build);
    }

    public Object[] validateAndMaybeFix(
            ContractMode moduleMode,
            String apiId,
            Method m,
            Object[] args,
            ThreadChecker threadChecker
    ) {
        final CompiledMethod cm = compile(m);

        final ContractMode effectiveMode = resolveMode(moduleMode, cm.methodMode);
        if (effectiveMode == ContractMode.OFF) {
            // Still enforce thread rule if requested? In AAA engines: thread rule is not "validation", it's correctness.
            // We'll enforce it always when declared.
            enforceThread(apiId, m, cm.threadRule, threadChecker);
            return args;
        }

        enforceThread(apiId, m, cm.threadRule, threadChecker);

        if (cm.paramRules.length == 0) return args;

        final Object[] a = (args == null) ? new Object[0] : args;

        for (int i = 0; i < cm.paramRules.length; i++) {
            final ParamRules r = cm.paramRules[i];
            if (r == null) continue;

            final Object v = (i < a.length) ? a[i] : null;

            if (r.notNull && v == null) {
                throw fail(apiId, m, i, "NotNull", "parameter is null");
            }

            if (r.finite && v != null) {
                if (v instanceof Float f) {
                    if (!Float.isFinite(f)) throw fail(apiId, m, i, "Finite", "float is not finite: " + f);
                } else if (v instanceof Double d) {
                    if (!Double.isFinite(d)) throw fail(apiId, m, i, "Finite", "double is not finite: " + d);
                }
            }

            if (r.range != null && v != null) {
                if (!(v instanceof Number n)) {
                    throw fail(apiId, m, i, "Range", "parameter is not a Number: " + v.getClass().getName());
                }
                final double x = n.doubleValue();
                final double min = r.range.min;
                final double max = r.range.max;

                if (x < min || x > max) {
                    if (effectiveMode == ContractMode.CLAMP) {
                        final double clamped = (x < min) ? min : max;
                        a[i] = castNumberLike(n, clamped);
                    } else {
                        throw fail(apiId, m, i, "Range(" + min + ".." + max + ")", "value=" + x);
                    }
                }
            }
        }

        return a;
    }

    public interface ThreadChecker {
        boolean isJmeThread();
    }

    // ---------------- Compiled model ----------------

    public static final class CompiledMethod {
        public final ParamRules[] paramRules;
        public final ApiThreadRule threadRule;
        public final boolean sync;
        public final ApiMethod.Mode methodMode;
        public final ApiFlag[] flags;
        public final ApiCostHint cost;

        private CompiledMethod(
                ParamRules[] paramRules,
                ApiThreadRule threadRule,
                boolean sync,
                ApiMethod.Mode methodMode,
                ApiFlag[] flags,
                ApiCostHint cost
        ) {
            this.paramRules = Objects.requireNonNull(paramRules, "paramRules");
            this.threadRule = (threadRule == null) ? ApiThreadRule.ANY : threadRule;
            this.sync = sync;
            this.methodMode = (methodMode == null) ? ApiMethod.Mode.DEFAULT : methodMode;
            this.flags = (flags == null) ? new ApiFlag[0] : flags;
            this.cost = (cost == null) ? ApiCostHint.NORMAL : cost;
        }

        @Override
        public String toString() {
            return "CompiledMethod{thread=" + threadRule +
                    ", sync=" + sync +
                    ", mode=" + methodMode +
                    ", cost=" + cost +
                    ", flags=" + Arrays.toString(flags) +
                    ", rules=" + Arrays.toString(paramRules) + "}";
        }
    }

    public static final class ParamRules {
        boolean notNull;
        boolean finite;
        RangeRule range;

        @Override
        public String toString() {
            return "ParamRules{notNull=" + notNull + ", finite=" + finite + ", range=" + range + "}";
        }
    }

    private static final class RangeRule {
        final double min;
        final double max;

        RangeRule(double min, double max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public String toString() {
            return "Range(" + min + ".." + max + ")";
        }
    }
}