// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowPipelineRegistry.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.has;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.member;

/**
 * Central registry for shadow pipeline steps.
 * <p>
 * Features:
 * <ul>
 *   <li>Build pipelines from JS by step {@code type} without touching orchestrator.</li>
 *   <li>Apply configuration dynamically via reflection (fields + setters).</li>
 *   <li>Expose option schemas for each step for JS/UI tooling (introspection).</li>
 *   <li>Run post-link passes to wire step dependencies (e.g., gate -> snap) inside registry.</li>
 *   <li>Support type aliases for backward compatibility and UX (e.g., "pcf" -> "poissonPcf").</li>
 * </ul>
 */
public final class ShadowPipelineRegistry {

    private final Map<String, RegisteredStep> steps = new ConcurrentHashMap<>();
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    private final Map<Class<?>, StepBinder> binderCache = new ConcurrentHashMap<>();
    private final Map<String, List<OptionSpec>> schemaCache = new ConcurrentHashMap<>();
    private final List<PostLinkPass> postLinks = Collections.synchronizedList(new ArrayList<>());

    private final ShadowPipelinePresetLibrary presets;

    public ShadowPipelineRegistry(ShadowPipelinePresetLibrary presets) {
        this.presets = Objects.requireNonNull(presets, "presets");
    }

    /**
     * Creates a post-link pass that assigns the first provider instance to all consumers via
     * either a public/protected setter or a direct field write.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Try setter: {@code setXxx(providerType)} based on {@code memberName}.</li>
     *   <li>Try method: {@code memberName(providerType)} if memberName already looks like a method.</li>
     *   <li>Try field: {@code memberName}.</li>
     * </ol>
     * <p>
     * Example: linkByMember(TexelSnapFilter.class, "gate", TemporalSnapGateFilter.class)
     */
    public static PostLinkPass linkByMember(Class<?> consumerType, String memberName, Class<?> providerType) {
        Objects.requireNonNull(consumerType, "consumerType");
        Objects.requireNonNull(memberName, "memberName");
        Objects.requireNonNull(providerType, "providerType");

        final String m = memberName.trim();
        final String setter = m.startsWith("set") ? m : ("set" + Character.toUpperCase(m.charAt(0)) + m.substring(1));

        return (log, steps) -> {
            Object provider = firstInstanceOf(steps, providerType);
            if (provider == null) return;

            for (Object c : allInstancesOf(steps, consumerType)) {
                if (tryInvokeSetter(c, setter, providerType, provider)) continue;
                if (m.startsWith("set") && tryInvokeSetter(c, m, providerType, provider)) continue;
                if (trySetField(c, m, providerType, provider)) continue;

                if (log != null) {
                    log.warn("[shadow] postLink failed: {} cannot accept {} via '{}'/'{}'",
                            c.getClass().getSimpleName(), providerType.getSimpleName(), setter, m);
                }
            }
        };
    }

    private static Object firstInstanceOf(List<?> list, Class<?> type) {
        for (Object o : list) {
            if (o != null && type.isInstance(o)) return o;
        }
        return null;
    }

    private static List<Object> allInstancesOf(List<?> list, Class<?> type) {
        ArrayList<Object> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null && type.isInstance(o)) out.add(o);
        }
        return out;
    }

    private static boolean tryInvokeSetter(Object target, String methodName, Class<?> paramType, Object param) {
        try {
            Method m = findMethod(target.getClass(), methodName, paramType);
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(target, param);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> c, String name, Class<?> paramType) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (p.isAssignableFrom(paramType) || paramType.isAssignableFrom(p)) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static boolean trySetField(Object target, String fieldName, Class<?> fieldType, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return false;
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (!(t.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(t))) return false;
            f.set(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static ShadowFilter newInstance(Logger log, String type, Class<? extends ShadowFilter> clazz) {
        try {
            Constructor<? extends ShadowFilter> c = clazz.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Throwable t) {
            if (log != null) {
                log.warn("[shadow] cannot instantiate step type='{}' class='{}': {}", type, clazz.getName(), t.toString());
            }
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------

    private static boolean isSupportedType(Class<?> t) {
        if (t.isPrimitive()) return true;
        if (t == String.class) return true;
        if (Number.class.isAssignableFrom(t)) return true;
        if (t == Boolean.class || t == Character.class) return true;
        return t.isEnum();
    }

    private static String kindOf(Class<?> t) {
        if (t.isEnum()) return "enum";
        if (t == boolean.class || t == Boolean.class) return "boolean";
        if (t == int.class || t == Integer.class) return "int";
        if (t == long.class || t == Long.class) return "long";
        if (t == float.class || t == Float.class) return "float";
        if (t == double.class || t == Double.class) return "double";
        if (t == String.class) return "string";
        if (t == short.class || t == Short.class) return "short";
        if (t == byte.class || t == Byte.class) return "byte";
        if (t == char.class || t == Character.class) return "char";
        return "unknown";
    }

    private static Object parseValue(Value v, Class<?> t) {
        try {
            if (t == boolean.class || t == Boolean.class) return v.asBoolean();
            if (t == int.class || t == Integer.class) return v.asInt();
            if (t == long.class || t == Long.class) return v.asLong();
            if (t == float.class || t == Float.class) return (float) v.asDouble();
            if (t == double.class || t == Double.class) return v.asDouble();
            if (t == short.class || t == Short.class) return (short) v.asInt();
            if (t == byte.class || t == Byte.class) return (byte) v.asInt();
            if (t == char.class || t == Character.class) {
                String s = v.isString() ? v.asString() : v.toString();
                return s.isEmpty() ? null : s.charAt(0);
            }
            if (t == String.class) return v.isString() ? v.asString() : String.valueOf(v);
            if (t.isEnum()) {
                String s = v.isString() ? v.asString() : String.valueOf(v);
                @SuppressWarnings("unchecked")
                Class<? extends Enum> ec = (Class<? extends Enum>) t;
                for (Object e : ec.getEnumConstants()) {
                    if (((Enum<?>) e).name().equalsIgnoreCase(s)) return e;
                }
                return null;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Field> allFields(Class<?> c) {
        ArrayList<Field> out = new ArrayList<>();
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            out.addAll(Arrays.asList(cur.getDeclaredFields()));
            cur = cur.getSuperclass();
        }
        return out;
    }

    private static List<Method> allMethods(Class<?> c) {
        ArrayList<Method> out = new ArrayList<>();
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            out.addAll(Arrays.asList(cur.getDeclaredMethods()));
            cur = cur.getSuperclass();
        }
        return out;
    }

    private static String normalizeKey(String k) {
        if (k == null) return "";
        String s = k.trim().toLowerCase(Locale.ROOT);
        s = s.replace("_", "").replace("-", "");
        return s;
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() == 1) return s.toLowerCase(Locale.ROOT);
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------------
    // Aliases
    // ---------------------------------------------------------------------

    private static List<StepDef> mergeOverrides(Logger log, List<StepDef> base, Value overridesArr) {
        ArrayList<StepDef> out = new ArrayList<>(base);

        int n = (int) overridesArr.getArraySize();
        for (int i = 0; i < n; i++) {
            Value e = overridesArr.getArrayElement(i);
            if (e == null || e.isNull()) continue;

            int at = -1;
            if (e.hasMember("at")) {
                try {
                    at = e.getMember("at").asInt();
                } catch (Throwable ignored) {
                    at = -1;
                }
            }

            String type = null;
            if (e.hasMember("type")) type = safeStr(e.getMember("type"));
            if ((type == null || type.isEmpty()) && e.hasMember("id")) type = safeStr(e.getMember("id"));
            if (type == null || type.isEmpty()) type = "noop";

            Value cfg = e.hasMember("cfg") ? e.getMember("cfg") : e;
            StepDef sd = new StepDef(type, cfg);

            if (at >= 0 && at < out.size()) out.set(at, sd);
            else out.add(sd);
        }

        if (log != null) {
            log.debug("[shadow] pipeline overrides applied: base={} overrides={} => out={}", base.size(), n, out.size());
        }
        return out;
    }

    private static String optionName(ShadowOption meta, String fallback) {
        if (meta != null && meta.name() != null && !meta.name().isEmpty()) return meta.name();
        return fallback;
    }

    private static List<String> optionKeys(ShadowOption meta, String primary) {
        ArrayList<String> out = new ArrayList<>(4);
        out.add(primary);
        if (meta != null) {
            for (String a : meta.aliases()) {
                if (a != null && !a.isEmpty()) out.add(a);
            }
        }
        return out;
    }

    /**
     * Registers an alias for a step type.
     * <p>
     * Both alias and target are normalized (case-insensitive, '_' and '-' ignored).
     * Alias is resolved at creation and schema lookup time.
     */
    public void alias(String alias, String targetType) {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(targetType, "targetType");

        String a = normalizeKey(alias);
        String t = normalizeKey(targetType);

        if (a.isEmpty() || t.isEmpty()) return;
        if (a.equals(t)) return;

        aliases.put(a, t);
        schemaCache.remove(a);
    }

    // ---------------------------------------------------------------------
    // Parsing helpers
    // ---------------------------------------------------------------------

    private static String safeStr(Value v) {
        try {
            if (v == null || v.isNull()) return null;
            return v.isString() ? v.asString() : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<StepDef> parseStepsArray(Value arr) {
        int n = (int) arr.getArraySize();
        ArrayList<StepDef> out = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Value e = arr.getArrayElement(i);
            if (e == null || e.isNull()) continue;

            String type = null;
            if (e.hasMember("type")) type = safeStr(e.getMember("type"));
            if ((type == null || type.isEmpty()) && e.hasMember("id")) type = safeStr(e.getMember("id"));
            if (type == null || type.isEmpty()) type = "noop";

            Value cfg = e.hasMember("cfg") ? e.getMember("cfg") : e;
            out.add(new StepDef(type, cfg));
        }
        return out;
    }

    /**
     * Returns canonical type for a given input type (resolving aliases).
     * If the type is unknown, returns normalized input.
     */
    private String resolveType(Logger log, String type) {
        String cur = normalizeKey(type);
        if (cur.isEmpty()) return cur;

        int guard = 0;
        while (guard++ < 16) {
            String next = aliases.get(cur);
            if (next == null || next.isEmpty() || next.equals(cur)) break;
            cur = next;
        }

        if (guard >= 16 && log != null) {
            log.warn("[shadow] alias resolution exceeded guard for type='{}'", type);
        }
        return cur;
    }

    private static String buildStableKeyFromSteps(int splits, List<StepDef> steps) {
        StringBuilder key = new StringBuilder(256);
        key.append("splits=").append(splits).append("|");

        for (StepDef s : steps) {
            key.append(s.type);

            Value cfg = s.cfg;
            if (cfg != null && !cfg.isNull() && cfg.hasMembers()) {
                String mk = stablePrimitiveMembersKey(cfg);
                if (!mk.isEmpty()) key.append("(").append(mk).append(")");
            }
            key.append(";");
        }
        return key.toString();
    }

    private static String stablePrimitiveMembersKey(Value cfg) {
        try {
            if (cfg == null || cfg.isNull()) return "";
            if (!cfg.hasMembers()) return "";

            Set<String> keys = cfg.getMemberKeys();
            if (keys == null || keys.isEmpty()) return "";

            String[] kk = keys.toArray(new String[0]);
            Arrays.sort(kk);

            StringBuilder sb = new StringBuilder(96);
            for (String k : kk) {
                Value v = cfg.getMember(k);
                if (v == null || v.isNull()) continue;
                if (v.isNumber() || v.isBoolean() || v.isString()) {
                    if (!sb.isEmpty()) sb.append(",");
                    sb.append(k).append("=").append(v.toString());
                }
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ---------------------------------------------------------------------
    // Pipeline parsing + schema
    // ---------------------------------------------------------------------

    /**
     * Returns canonical registered types (excluding aliases).
     */
    public Set<String> knownTypes() {
        return new TreeSet<>(steps.keySet());
    }

    /**
     * Returns known aliases (alias -> targetType).
     */
    public Map<String, String> knownAliases() {
        TreeMap<String, String> out = new TreeMap<>();
        out.putAll(aliases);
        return out;
    }

    /**
     * Adds a post-link pass executed after step creation/config.
     * Passes run in registration order.
     */
    public void addPostLink(PostLinkPass pass) {
        Objects.requireNonNull(pass, "pass");
        postLinks.add(pass);
    }

    /**
     * Clears all post-link passes (useful for tests or reloads).
     */
    public void clearPostLinks() {
        postLinks.clear();
    }

    /**
     * Builds (creates + configures) all step instances for the pipeline def,
     * then runs post-link passes.
     */
    public List<ShadowFilter> build(Logger log, Runtime rt, PipelineDef def) {
        if (def == null || def.steps == null || def.steps.isEmpty()) {
            return List.of();
        }

        ArrayList<ShadowFilter> out = new ArrayList<>(def.steps.size());
        for (StepDef sd : def.steps) {
            ShadowFilter f = create(log, rt, sd.type, sd.cfg);
            if (f != null) out.add(f);
        }

        runPostLinks(log, out);
        return out;
    }

    /**
     * Registers a step by class. Instances are created via default constructor.
     */
    public void register(String type, Class<? extends ShadowFilter> clazz) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(clazz, "clazz");

        String k = normalizeKey(type);
        steps.put(k, new RegisteredStep(k, clazz, null));
        invalidateCaches(clazz, k);
        schemaCache.remove(k);
    }

    /**
     * Executes all post-link passes.
     */
    public void runPostLinks(Logger log, List<ShadowFilter> steps) {
        if (steps == null || steps.isEmpty()) return;

        PostLinkPass[] passes;
        synchronized (postLinks) {
            passes = postLinks.toArray(new PostLinkPass[0]);
        }

        for (PostLinkPass p : passes) {
            try {
                p.link(log, steps);
            } catch (Throwable t) {
                if (log != null) log.warn("[shadow] postLink pass failed: {}", t.toString());
            }
        }
    }

    /**
     * Parses pipeline from JS. Supported forms:
     * <ul>
     *   <li>{@code pipeline: [ {type:"..."}, ... ]}</li>
     *   <li>{@code pipeline: "presetName"}</li>
     *   <li>{@code pipeline: { preset:"name", overrides:[ ... ] }}</li>
     * </ul>
     * The returned key is stable (best-effort) to drive rebuild decisions.
     */
    public PipelineDef parsePipeline(Logger log, Value src, int splits) {
        Value p = member(src, "pipeline");
        if (p == null || p.isNull()) return null;

        if (p.isString()) {
            String preset = safeStr(p);
            List<StepDef> expanded = presets.expandPresetToSteps(log, preset, splits);
            return new PipelineDef(expanded, "preset:" + preset + "|splits=" + splits);
        }

        if (p.hasMembers() && has(p, "preset")) {
            String preset = safeStr(p.getMember("preset"));
            List<StepDef> base = presets.expandPresetToSteps(log, preset, splits);

            Value overrides = member(p, "overrides");
            if (overrides != null && !overrides.isNull() && overrides.hasArrayElements()) {
                base = mergeOverrides(log, base, overrides);
            }

            String key = buildStableKeyFromSteps(splits, base);
            return new PipelineDef(base, "preset:" + preset + "|" + key);
        }

        if (p.hasArrayElements()) {
            List<StepDef> steps = parseStepsArray(p);
            String key = buildStableKeyFromSteps(splits, steps);
            return new PipelineDef(steps, key);
        }

        if (log != null) log.warn("[shadow] pipeline has unsupported type => ignored");
        return null;
    }

    /**
     * Registers a step by factory. Use when the step needs constructor args or shared instances.
     */
    public void register(String type, StepFactory factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");

        String k = normalizeKey(type);
        steps.put(k, new RegisteredStep(k, null, factory));
        schemaCache.remove(k);
    }

    public void applyConfig(Logger log, ShadowFilter step, Value cfg) {
        if (step == null) return;
        if (cfg == null || cfg.isNull()) return;

        StepBinder binder = binderCache.computeIfAbsent(step.getClass(), StepBinder::build);
        binder.apply(log, step, cfg);
    }

    private void invalidateCaches(Class<?> clazz, String type) {
        binderCache.remove(clazz);
        schemaCache.remove(type);
    }

    /**
     * Optional metadata for configuration targets.
     * Apply to a non-static field or a one-arg setter.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.METHOD})
    public @interface ShadowOption {
        String name() default "";

        String[] aliases() default {};

        String doc() default "";

        double min() default Double.NaN;

        double max() default Double.NaN;
    }

    /**
     * Factory receives Runtime and step cfg; it may ignore both.
     */
    @FunctionalInterface
    public interface StepFactory {
        ShadowFilter create(Runtime rt, Value cfg);
    }

    /**
     * Post-link pass: invoked after all step instances are created and configured.
     * Use to wire dependencies between steps (gate->snap, etc.).
     */
    @FunctionalInterface
    public interface PostLinkPass {
        void link(Logger log, List<ShadowFilter> steps);
    }

    /**
     * Option schema for JS-side tooling.
     */
    public static final class OptionSpec {
        public final String name;
        public final String kind;
        public final String doc;
        public final String defaultValue;
        public final Double min;
        public final Double max;

        public OptionSpec(String name, String kind, String doc, String defaultValue, Double min, Double max) {
            this.name = name;
            this.kind = kind;
            this.doc = doc;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
        }
    }

    /**
     * Parsed pipeline definition.
     */
    public static final class PipelineDef {
        public final List<StepDef> steps;
        public final String key;

        public PipelineDef(List<StepDef> steps, String key) {
            this.steps = steps;
            this.key = key;
        }
    }

    /**
     * Single step in parsed pipeline definition.
     */
    public static final class StepDef {
        public final String type;
        public final Value cfg;

        public StepDef(String type, Value cfg) {
            this.type = type;
            this.cfg = cfg;
        }
    }

    /**
     * Runtime info that can be used by factories.
     */
    public static final class Runtime {
        public final int mapSize;
        public final int splits;

        public Runtime(int mapSize, int splits) {
            this.mapSize = mapSize;
            this.splits = splits;
        }
    }

    private static final class RegisteredStep {
        final String type;
        final Class<? extends ShadowFilter> clazz;
        final StepFactory factory;

        RegisteredStep(String type, Class<? extends ShadowFilter> clazz, StepFactory factory) {
            this.type = type;
            this.clazz = clazz;
            this.factory = factory;
        }
    }

    /**
     * Builds and configures a single step instance.
     */
    public ShadowFilter create(Logger log, Runtime rt, String type, Value cfg) {
        String resolved = resolveType(log, type);

        RegisteredStep reg = steps.get(resolved);
        if (reg == null) {
            if (log != null) log.warn("[shadow] unknown pipeline step type='{}' => skipped", type);
            return null;
        }

        ShadowFilter inst = null;

        if (reg.factory != null) {
            try {
                inst = reg.factory.create(rt, cfg);
            } catch (Throwable t) {
                if (log != null) log.warn("[shadow] step factory failed type='{}': {}", type, t.toString());
                return null;
            }
        } else if (reg.clazz != null) {
            inst = newInstance(log, type, reg.clazz);
        }

        if (inst == null) return null;

        applyConfig(log, inst, cfg);
        return inst;
    }

    /**
     * Returns dynamic option schema for a type, via reflection.
     * Aliases are resolved before schema lookup.
     */
    public List<OptionSpec> schemaFor(String type) {
        String resolved = resolveType(null, type);

        List<OptionSpec> cached = schemaCache.get(resolved);
        if (cached != null) return cached;

        RegisteredStep reg = steps.get(resolved);
        if (reg == null || reg.clazz == null) {
            List<OptionSpec> empty = List.of();
            schemaCache.put(resolved, empty);
            return empty;
        }

        StepBinder binder = binderCache.computeIfAbsent(reg.clazz, StepBinder::build);
        List<OptionSpec> schema = binder.schema;
        schemaCache.put(resolved, schema);
        return schema;
    }

    private static final class SetterBinding {
        final Method method;
        final Class<?> paramType;
        final ShadowOption meta;

        SetterBinding(Method method, Class<?> paramType, ShadowOption meta) {
            this.method = method;
            this.paramType = paramType;
            this.meta = meta;
        }

        void invoke(Object target, Object v) throws Throwable {
            method.invoke(target, v);
        }
    }

    private static final class FieldBinding {
        final Field field;
        final Class<?> type;
        final ShadowOption meta;

        FieldBinding(Field field, Class<?> type, ShadowOption meta) {
            this.field = field;
            this.type = type;
            this.meta = meta;
        }

        void set(Object target, Object v) throws Throwable {
            field.set(target, v);
        }
    }

    private static final class StepBinder {
        final Map<String, SetterBinding> settersByKey;
        final Map<String, FieldBinding> fieldsByKey;
        final List<OptionSpec> schema;

        StepBinder(Map<String, SetterBinding> settersByKey,
                   Map<String, FieldBinding> fieldsByKey,
                   List<OptionSpec> schema) {
            this.settersByKey = settersByKey;
            this.fieldsByKey = fieldsByKey;
            this.schema = schema;
        }

        static StepBinder build(Class<?> clazz) {
            Map<String, SetterBinding> setters = new HashMap<>();
            Map<String, FieldBinding> fields = new HashMap<>();
            List<OptionSpec> schema = new ArrayList<>();

            for (Field f : allFields(clazz)) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (Modifier.isFinal(f.getModifiers())) continue;

                Class<?> t = f.getType();
                if (!isSupportedType(t)) continue;

                ShadowOption meta = f.getAnnotation(ShadowOption.class);
                String name = optionName(meta, f.getName());

                f.setAccessible(true);

                FieldBinding fb = new FieldBinding(f, t, meta);
                for (String k : optionKeys(meta, name)) {
                    fields.put(normalizeKey(k), fb);
                }

                schema.add(new OptionSpec(
                        name,
                        kindOf(t),
                        meta != null ? meta.doc() : "",
                        "",
                        meta != null && !Double.isNaN(meta.min()) ? meta.min() : null,
                        meta != null && !Double.isNaN(meta.max()) ? meta.max() : null
                ));
            }

            for (Method m : allMethods(clazz)) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getName().startsWith("set")) continue;
                if (m.getParameterCount() != 1) continue;

                Class<?> pt = m.getParameterTypes()[0];
                if (!isSupportedType(pt)) continue;

                String prop = decapitalize(m.getName().substring(3));
                if (prop.isEmpty()) continue;

                ShadowOption meta = m.getAnnotation(ShadowOption.class);
                String name = optionName(meta, prop);

                m.setAccessible(true);

                SetterBinding sb = new SetterBinding(m, pt, meta);
                for (String k : optionKeys(meta, name)) {
                    setters.put(normalizeKey(k), sb);
                }

                schema.add(new OptionSpec(
                        name,
                        kindOf(pt),
                        meta != null ? meta.doc() : "",
                        "",
                        meta != null && !Double.isNaN(meta.min()) ? meta.min() : null,
                        meta != null && !Double.isNaN(meta.max()) ? meta.max() : null
                ));
            }

            schema.sort(Comparator.comparing(a -> a.name));
            return new StepBinder(setters, fields, schema);
        }

        void apply(Logger log, Object target, Value cfg) {
            if (cfg == null || cfg.isNull()) return;
            if (!cfg.hasMembers()) return;

            Set<String> keys = cfg.getMemberKeys();
            if (keys == null || keys.isEmpty()) return;

            for (String rawKey : keys) {
                Value v = cfg.getMember(rawKey);
                if (v == null || v.isNull()) continue;

                String k = normalizeKey(rawKey);

                SetterBinding sb = settersByKey.get(k);
                if (sb != null) {
                    Object parsed = parseValue(v, sb.paramType);
                    if (parsed != null) {
                        try {
                            sb.invoke(target, parsed);
                        } catch (Throwable t) {
                            if (log != null) {
                                log.warn("[shadow] cfg set failed {}.{}: {}", target.getClass().getSimpleName(), rawKey, t.toString());
                            }
                        }
                    }
                    continue;
                }

                FieldBinding fb = fieldsByKey.get(k);
                if (fb != null) {
                    Object parsed = parseValue(v, fb.type);
                    if (parsed != null) {
                        try {
                            fb.set(target, parsed);
                        } catch (Throwable t) {
                            if (log != null) {
                                log.warn("[shadow] cfg field set failed {}.{}: {}", target.getClass().getSimpleName(), rawKey, t.toString());
                            }
                        }
                    }
                }
            }
        }
    }
}