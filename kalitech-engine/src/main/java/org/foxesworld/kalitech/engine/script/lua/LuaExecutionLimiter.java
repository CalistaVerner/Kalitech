package org.foxesworld.kalitech.engine.script.lua;

import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

/**
 * Lightweight instruction and wall-clock budget for every Java-to-Lua entry.
 *
 * <p>The debug table is removed from script globals after this library is installed,
 * so scripts cannot replace or disable the limiter.</p>
 */
public final class LuaExecutionLimiter extends DebugLib {

    private static final String CALLBACK_INSTRUCTIONS = "kalitech.lua.maxCallbackInstructions";
    private static final String CALLBACK_MILLIS = "kalitech.lua.maxCallbackMillis";
    private static final String LIFECYCLE_INSTRUCTIONS = "kalitech.lua.maxLifecycleInstructions";
    private static final String LIFECYCLE_MILLIS = "kalitech.lua.maxLifecycleMillis";
    private static final String MODULE_INSTRUCTIONS = "kalitech.lua.maxModuleInstructions";
    private static final String MODULE_MILLIS = "kalitech.lua.maxModuleMillis";

    private static final long DEFAULT_CALLBACK_INSTRUCTIONS = 2_000_000L;
    private static final long DEFAULT_CALLBACK_MILLIS = 250L;
    private static final long DEFAULT_LIFECYCLE_INSTRUCTIONS = 100_000_000L;
    private static final long DEFAULT_LIFECYCLE_MILLIS = 30_000L;
    private static final long DEFAULT_MODULE_INSTRUCTIONS = 50_000_000L;
    private static final long DEFAULT_MODULE_MILLIS = 5_000L;
    private static final long SAMPLE_MASK = 0x3ffL;

    private static final ThreadLocal<Budget> ACTIVE = new ThreadLocal<>();

    public static Scope enterCallback(String label) {
        return enter(
                label,
                property(CALLBACK_INSTRUCTIONS, DEFAULT_CALLBACK_INSTRUCTIONS),
                property(CALLBACK_MILLIS, DEFAULT_CALLBACK_MILLIS)
        );
    }

    public static Scope enterLifecycle(String label) {
        return enter(
                label,
                property(LIFECYCLE_INSTRUCTIONS, DEFAULT_LIFECYCLE_INSTRUCTIONS),
                property(LIFECYCLE_MILLIS, DEFAULT_LIFECYCLE_MILLIS)
        );
    }

    public static Scope enterModule(String label) {
        return enter(
                label,
                property(MODULE_INSTRUCTIONS, DEFAULT_MODULE_INSTRUCTIONS),
                property(MODULE_MILLIS, DEFAULT_MODULE_MILLIS)
        );
    }

    private static Scope enter(String label, long instructionLimit, long millisLimit) {
        Budget current = ACTIVE.get();
        if (current != null) {
            current.depth++;
            return new Scope(current, false);
        }

        long now = System.nanoTime();
        long durationNanos = millisLimit <= 0L
                ? Long.MAX_VALUE
                : saturatingMultiply(millisLimit, 1_000_000L);
        long deadline = durationNanos == Long.MAX_VALUE || Long.MAX_VALUE - now < durationNanos
                ? Long.MAX_VALUE
                : now + durationNanos;

        Budget budget = new Budget(
                label == null || label.isBlank() ? "lua" : label,
                Math.max(0L, instructionLimit),
                now,
                deadline
        );
        ACTIVE.set(budget);
        return new Scope(budget, true);
    }

    @Override
    public void onCall(LuaFunction function) {
        super.onCall(function);
    }

    @Override
    public void onCall(LuaClosure closure, Varargs arguments, LuaValue[] stack) {
        super.onCall(closure, arguments, stack);
    }

    @Override
    public void onInstruction(int pc, Varargs values, int top) {
        Budget budget = ACTIVE.get();
        if (budget == null) return;

        long instructions = ++budget.instructions;
        if ((instructions & SAMPLE_MASK) == 0L) {
            super.onInstruction(pc, values, top);
            if (budget.deadlineNanos != Long.MAX_VALUE
                    && System.nanoTime() > budget.deadlineNanos) {
                throw exceeded(budget, "wall-clock");
            }
        }

        if (budget.instructionLimit > 0L && instructions > budget.instructionLimit) {
            throw exceeded(budget, "instruction");
        }
    }

    @Override
    public void onReturn() {
        super.onReturn();
    }

    private static LuaError exceeded(Budget budget, String kind) {
        long elapsedMillis = Math.max(0L, System.nanoTime() - budget.startedNanos) / 1_000_000L;
        return new LuaError("Lua " + kind + " budget exceeded: label=" + budget.label
                + ", instructions=" + budget.instructions
                + ", elapsedMs=" + elapsedMillis);
    }

    private static long property(String name, long fallback) {
        try {
            String raw = System.getProperty(name);
            if (raw == null || raw.isBlank()) return fallback;
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static final class Budget {
        final String label;
        final long instructionLimit;
        final long startedNanos;
        final long deadlineNanos;
        int depth = 1;
        long instructions;

        Budget(String label, long instructionLimit, long startedNanos, long deadlineNanos) {
            this.label = label;
            this.instructionLimit = instructionLimit;
            this.startedNanos = startedNanos;
            this.deadlineNanos = deadlineNanos;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Budget budget;
        private final boolean owner;
        private boolean closed;

        private Scope(Budget budget, boolean owner) {
            this.budget = budget;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            budget.depth = Math.max(0, budget.depth - 1);
            if (owner || budget.depth == 0) ACTIVE.remove();
        }
    }
}
