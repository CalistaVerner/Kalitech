/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowFilter;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowPipelinePresetLibrary;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class ShadowPipelineRegistry {
    private final Map<String, RegisteredStep> steps = new ConcurrentHashMap<String, RegisteredStep>();
    private final Map<Class<?>, StepBinder> binderCache = new ConcurrentHashMap();
    private final Map<String, List<OptionSpec>> schemaCache = new ConcurrentHashMap<String, List<OptionSpec>>();
    private final List<PostLinkPass> postLinks = Collections.synchronizedList(new ArrayList());
    private final ShadowPipelinePresetLibrary presets;

    public ShadowPipelineRegistry(ShadowPipelinePresetLibrary presets) {
        this.presets = Objects.requireNonNull(presets, "presets");
    }

    public static PostLinkPass linkByMember(Class<?> consumerType, String memberName, Class<?> providerType) {
        Objects.requireNonNull(consumerType, "consumerType");
        Objects.requireNonNull(memberName, "memberName");
        Objects.requireNonNull(providerType, "providerType");
        String m = memberName.trim();
        String setter = m.startsWith("set") ? m : "set" + Character.toUpperCase(m.charAt(0)) + m.substring(1);
        return (log, steps) -> {
            Object provider = ShadowPipelineRegistry.firstInstanceOf(steps, providerType);
            if (provider == null) {
                return;
            }
            for (Object c : ShadowPipelineRegistry.allInstancesOf(steps, consumerType)) {
                if (ShadowPipelineRegistry.tryInvokeSetter(c, setter, providerType, provider) || m.startsWith("set") && ShadowPipelineRegistry.tryInvokeSetter(c, m, providerType, provider) || ShadowPipelineRegistry.trySetField(c, m, providerType, provider) || log == null) continue;
                log.warn("[shadow] postLink failed: {} cannot accept {} via '{}'/'{}'", (Object)c.getClass().getSimpleName(), (Object)providerType.getSimpleName(), (Object)setter, (Object)m);
            }
        };
    }

    private static Object firstInstanceOf(List<?> list, Class<?> type) {
        for (Object o : list) {
            if (o == null || !type.isInstance(o)) continue;
            return o;
        }
        return null;
    }

    private static List<Object> allInstancesOf(List<?> list, Class<?> type) {
        ArrayList<Object> out = new ArrayList<Object>();
        for (Object o : list) {
            if (o == null || !type.isInstance(o)) continue;
            out.add(o);
        }
        return out;
    }

    private static boolean tryInvokeSetter(Object target, String methodName, Class<?> paramType, Object param) {
        try {
            Method m = ShadowPipelineRegistry.findMethod(target.getClass(), methodName, paramType);
            if (m == null) {
                return false;
            }
            m.setAccessible(true);
            m.invoke(target, param);
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> c, String name, Class<?> paramType) {
        for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            for (Method m : cur.getDeclaredMethods()) {
                Class<?> p;
                if (!m.getName().equals(name) || m.getParameterCount() != 1 || !(p = m.getParameterTypes()[0]).isAssignableFrom(paramType) && !paramType.isAssignableFrom(p)) continue;
                return m;
            }
        }
        return null;
    }

    private static boolean trySetField(Object target, String fieldName, Class<?> fieldType, Object value) {
        try {
            Field f = ShadowPipelineRegistry.findField(target.getClass(), fieldName);
            if (f == null) {
                return false;
            }
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (!t.isAssignableFrom(fieldType) && !fieldType.isAssignableFrom(t)) {
                return false;
            }
            f.set(target, value);
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            try {
                return cur.getDeclaredField(name);
            }
            catch (NoSuchFieldException noSuchFieldException) {
                continue;
            }
        }
        return null;
    }

    private static ShadowFilter newInstance(Logger log, String type, Class<? extends ShadowFilter> clazz) {
        try {
            Constructor<? extends ShadowFilter> c = clazz.getDeclaredConstructor(new Class[0]);
            c.setAccessible(true);
            return c.newInstance(new Object[0]);
        }
        catch (Throwable t) {
            if (log != null) {
                log.warn("[shadow] cannot instantiate step type='{}' class='{}': {}", (Object)type, (Object)clazz.getName(), (Object)t.toString());
            }
            return null;
        }
    }

    private static boolean isSupportedType(Class<?> t) {
        if (t.isPrimitive()) {
            return true;
        }
        if (t == String.class) {
            return true;
        }
        if (Number.class.isAssignableFrom(t)) {
            return true;
        }
        if (t == Boolean.class || t == Character.class) {
            return true;
        }
        return t.isEnum();
    }

    private static String kindOf(Class<?> t) {
        if (t.isEnum()) {
            return "enum";
        }
        if (t == Boolean.TYPE || t == Boolean.class) {
            return "boolean";
        }
        if (t == Integer.TYPE || t == Integer.class) {
            return "int";
        }
        if (t == Long.TYPE || t == Long.class) {
            return "long";
        }
        if (t == Float.TYPE || t == Float.class) {
            return "float";
        }
        if (t == Double.TYPE || t == Double.class) {
            return "double";
        }
        if (t == String.class) {
            return "string";
        }
        if (t == Short.TYPE || t == Short.class) {
            return "short";
        }
        if (t == Byte.TYPE || t == Byte.class) {
            return "byte";
        }
        if (t == Character.TYPE || t == Character.class) {
            return "char";
        }
        return "unknown";
    }

    private static Object parseValue(LuaValueRef v, Class<?> t) {
        try {
            if (t == Boolean.TYPE || t == Boolean.class) {
                return v.asBoolean();
            }
            if (t == Integer.TYPE || t == Integer.class) {
                return v.asInt();
            }
            if (t == Long.TYPE || t == Long.class) {
                return v.asLong();
            }
            if (t == Float.TYPE || t == Float.class) {
                return Float.valueOf((float)v.asDouble());
            }
            if (t == Double.TYPE || t == Double.class) {
                return v.asDouble();
            }
            if (t == Short.TYPE || t == Short.class) {
                return (short)v.asInt();
            }
            if (t == Byte.TYPE || t == Byte.class) {
                return (byte)v.asInt();
            }
            if (t == Character.TYPE || t == Character.class) {
                String s = v.isString() ? v.asString() : v.toString();
                return s.isEmpty() ? null : Character.valueOf(s.charAt(0));
            }
            if (t == String.class) {
                return v.isString() ? v.asString() : String.valueOf(v);
            }
            if (t.isEnum()) {
                String s = v.isString() ? v.asString() : String.valueOf(v);
                Class<?> ec = t;
                for (Enum e : (Enum[])ec.getEnumConstants()) {
                    if (!e.name().equalsIgnoreCase(s)) continue;
                    return e;
                }
                return null;
            }
            return null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Field> allFields(Class<?> c) {
        ArrayList<Field> out = new ArrayList<Field>();
        for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            out.addAll(Arrays.asList(cur.getDeclaredFields()));
        }
        return out;
    }

    private static List<Method> allMethods(Class<?> c) {
        ArrayList<Method> out = new ArrayList<Method>();
        for (Class<?> cur = c; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            out.addAll(Arrays.asList(cur.getDeclaredMethods()));
        }
        return out;
    }

    private static String normalizeKey(String k) {
        if (k == null) {
            return "";
        }
        String s = k.trim().toLowerCase(Locale.ROOT);
        s = s.replace("_", "").replace("-", "");
        return s;
    }

    private static String decapitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() == 1) {
            return s.toLowerCase(Locale.ROOT);
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static List<StepDef> mergeOverrides(Logger log, List<StepDef> base, LuaValueRef overridesArr) {
        ArrayList<StepDef> out = new ArrayList<StepDef>(base);
        int n = (int)overridesArr.getArraySize();
        for (int i = 0; i < n; ++i) {
            LuaValueRef e = overridesArr.getArrayElement((long)i);
            if (e == null || e.isNull()) continue;
            int at = -1;
            if (e.hasMember("at")) {
                try {
                    at = e.getMember("at").asInt();
                }
                catch (Throwable ignored) {
                    at = -1;
                }
            }
            String type = null;
            if (e.hasMember("type")) {
                type = ShadowPipelineRegistry.safeStr(e.getMember("type"));
            }
            if ((type == null || type.isEmpty()) && e.hasMember("id")) {
                type = ShadowPipelineRegistry.safeStr(e.getMember("id"));
            }
            if (type == null || type.isEmpty()) {
                type = "noop";
            }
            LuaValueRef cfg = e.hasMember("cfg") ? e.getMember("cfg") : e;
            StepDef sd = new StepDef(type, cfg);
            if (at >= 0 && at < out.size()) {
                out.set(at, sd);
                continue;
            }
            out.add(sd);
        }
        if (log != null) {
            log.debug("[shadow] pipeline overrides applied: base={} overrides={} => out={}", (Object)base.size(), (Object)n, (Object)out.size());
        }
        return out;
    }

    private static String optionName(ShadowOption meta, String fallback) {
        if (meta != null && meta.name() != null && !meta.name().isEmpty()) {
            return meta.name();
        }
        return fallback;
    }

    private static List<String> optionKeys(ShadowOption meta, String primary) {
        return List.of(primary);
    }

    private static String safeStr(LuaValueRef v) {
        try {
            if (v == null || v.isNull()) {
                return null;
            }
            return v.isString() ? v.asString() : String.valueOf(v);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static List<StepDef> parseStepsArray(LuaValueRef arr) {
        int n = (int)arr.getArraySize();
        ArrayList<StepDef> out = new ArrayList<StepDef>(n);
        for (int i = 0; i < n; ++i) {
            LuaValueRef e = arr.getArrayElement((long)i);
            if (e == null || e.isNull()) continue;
            String type = null;
            if (e.hasMember("type")) {
                type = ShadowPipelineRegistry.safeStr(e.getMember("type"));
            }
            if ((type == null || type.isEmpty()) && e.hasMember("id")) {
                type = ShadowPipelineRegistry.safeStr(e.getMember("id"));
            }
            if (type == null || type.isEmpty()) {
                type = "noop";
            }
            LuaValueRef cfg = e.hasMember("cfg") ? e.getMember("cfg") : e;
            out.add(new StepDef(type, cfg));
        }
        return out;
    }

    private static String canonicalType(String type) {
        return ShadowPipelineRegistry.normalizeKey(type);
    }

    private static String buildStableKeyFromSteps(int splits, List<StepDef> steps) {
        StringBuilder key = new StringBuilder(256);
        key.append("splits=").append(splits).append("|");
        for (StepDef s : steps) {
            String mk;
            key.append(s.type);
            LuaValueRef cfg = s.cfg;
            if (cfg != null && !cfg.isNull() && cfg.hasMembers() && !(mk = ShadowPipelineRegistry.stablePrimitiveMembersKey(cfg)).isEmpty()) {
                key.append("(").append(mk).append(")");
            }
            key.append(";");
        }
        return key.toString();
    }

    private static String stablePrimitiveMembersKey(LuaValueRef cfg) {
        try {
            if (cfg == null || cfg.isNull()) {
                return "";
            }
            if (!cfg.hasMembers()) {
                return "";
            }
            Set<String> keys = cfg.getMemberKeys();
            if (keys == null || keys.isEmpty()) {
                return "";
            }
            Object[] kk = keys.toArray(new String[0]);
            Arrays.sort(kk);
            StringBuilder sb = new StringBuilder(96);
            for (Object k : kk) {
                LuaValueRef v = cfg.getMember((String)k);
                if (v == null || v.isNull() || !v.isNumber() && !v.isBoolean() && !v.isString()) continue;
                if (!sb.isEmpty()) {
                    sb.append(",");
                }
                sb.append((String)k).append("=").append(v.toString());
            }
            return sb.toString();
        }
        catch (Throwable t) {
            return "";
        }
    }

    public Set<String> knownTypes() {
        return new TreeSet<String>(this.steps.keySet());
    }

    public void addPostLink(PostLinkPass pass) {
        Objects.requireNonNull(pass, "pass");
        this.postLinks.add(pass);
    }

    public void clearPostLinks() {
        this.postLinks.clear();
    }

    public List<ShadowFilter> build(Logger log, Runtime rt, PipelineDef def) {
        if (def == null || def.steps == null || def.steps.isEmpty()) {
            return List.of();
        }
        ArrayList<ShadowFilter> out = new ArrayList<ShadowFilter>(def.steps.size());
        for (StepDef sd : def.steps) {
            ShadowFilter f = this.create(log, rt, sd.type, sd.cfg);
            if (f == null) continue;
            out.add(f);
        }
        this.runPostLinks(log, out);
        return out;
    }

    public void register(String type, Class<? extends ShadowFilter> clazz) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(clazz, "clazz");
        String k = ShadowPipelineRegistry.normalizeKey(type);
        this.steps.put(k, new RegisteredStep(k, clazz, null));
        this.invalidateCaches(clazz, k);
        this.schemaCache.remove(k);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void runPostLinks(Logger log, List<ShadowFilter> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        synchronized (this.postLinks) {
            PostLinkPass[] passes = this.postLinks.toArray(new PostLinkPass[0]);
            // ** MonitorExit[var4_3] (shouldn't be in output)
            for (PostLinkPass p : passes) {
                try {
                    p.link(log, steps);
                }
                catch (Throwable t) {
                    if (log == null) continue;
                    log.warn("[shadow] postLink pass failed: {}", (Object)t.toString());
                }
            }
            return;
        }
    }

    public PipelineDef parsePipeline(Logger log, LuaValueRef src, int splits) {
        LuaValueRef p = LuaCfg.member((LuaValueRef)src, (String)"pipeline");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isString()) {
            String preset = ShadowPipelineRegistry.safeStr(p);
            List<StepDef> expanded = this.presets.expandPresetToSteps(log, preset, splits);
            return new PipelineDef(expanded, "preset:" + preset + "|splits=" + splits);
        }
        if (p.hasMembers() && LuaCfg.has((LuaValueRef)p, (String)"preset")) {
            String preset = ShadowPipelineRegistry.safeStr(p.getMember("preset"));
            List<StepDef> base = this.presets.expandPresetToSteps(log, preset, splits);
            LuaValueRef overrides = LuaCfg.member((LuaValueRef)p, (String)"overrides");
            if (overrides != null && !overrides.isNull() && overrides.hasArrayElements()) {
                base = ShadowPipelineRegistry.mergeOverrides(log, base, overrides);
            }
            String key = ShadowPipelineRegistry.buildStableKeyFromSteps(splits, base);
            return new PipelineDef(base, "preset:" + preset + "|" + key);
        }
        if (p.hasArrayElements()) {
            List<StepDef> steps = ShadowPipelineRegistry.parseStepsArray(p);
            String key = ShadowPipelineRegistry.buildStableKeyFromSteps(splits, steps);
            return new PipelineDef(steps, key);
        }
        if (log != null) {
            log.warn("[shadow] pipeline has unsupported type => ignored");
        }
        return null;
    }

    public void register(String type, StepFactory factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        String k = ShadowPipelineRegistry.normalizeKey(type);
        this.steps.put(k, new RegisteredStep(k, null, factory));
        this.schemaCache.remove(k);
    }

    public void applyConfig(Logger log, ShadowFilter step, LuaValueRef cfg) {
        if (step == null) {
            return;
        }
        if (cfg == null || cfg.isNull()) {
            return;
        }
        StepBinder binder = this.binderCache.computeIfAbsent(step.getClass(), StepBinder::build);
        binder.apply(log, step, cfg);
    }

    private void invalidateCaches(Class<?> clazz, String type) {
        this.binderCache.remove(clazz);
        this.schemaCache.remove(type);
    }

    public ShadowFilter create(Logger log, Runtime rt, String type, LuaValueRef cfg) {
        String resolved = canonicalType(type);
        RegisteredStep reg = this.steps.get(resolved);
        if (reg == null) {
            if (log != null) {
                log.warn("[shadow] unknown pipeline step type='{}' => skipped", (Object)type);
            }
            return null;
        }
        ShadowFilter inst = null;
        if (reg.factory != null) {
            try {
                inst = reg.factory.create(rt, cfg);
            }
            catch (Throwable t) {
                if (log != null) {
                    log.warn("[shadow] step factory failed type='{}': {}", (Object)type, (Object)t.toString());
                }
                return null;
            }
        } else if (reg.clazz != null) {
            inst = ShadowPipelineRegistry.newInstance(log, type, reg.clazz);
        }
        if (inst == null) {
            return null;
        }
        this.applyConfig(log, inst, cfg);
        return inst;
    }

    public List<OptionSpec> schemaFor(String type) {
        String resolved = canonicalType(type);
        List<OptionSpec> cached = this.schemaCache.get(resolved);
        if (cached != null) {
            return cached;
        }
        RegisteredStep reg = this.steps.get(resolved);
        if (reg == null || reg.clazz == null) {
            List<OptionSpec> empty = List.of();
            this.schemaCache.put(resolved, empty);
            return empty;
        }
        StepBinder binder = this.binderCache.computeIfAbsent(reg.clazz, StepBinder::build);
        List<OptionSpec> schema = binder.schema;
        this.schemaCache.put(resolved, schema);
        return schema;
    }

    @FunctionalInterface
    public static interface PostLinkPass {
        public void link(Logger var1, List<ShadowFilter> var2);
    }

    public static final class StepDef {
        public final String type;
        public final LuaValueRef cfg;

        public StepDef(String type, LuaValueRef cfg) {
            this.type = type;
            this.cfg = cfg;
        }
    }

    @Retention(value=RetentionPolicy.RUNTIME)
    @Target(value={ElementType.FIELD, ElementType.METHOD})
    public static @interface ShadowOption {
        public String name() default "";

        public String doc() default "";

        public double min() default Double.NaN;

        public double max() default Double.NaN;
    }

    public static final class PipelineDef {
        public final List<StepDef> steps;
        public final String key;

        public PipelineDef(List<StepDef> steps, String key) {
            this.steps = steps;
            this.key = key;
        }
    }

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

    @FunctionalInterface
    public static interface StepFactory {
        public ShadowFilter create(Runtime var1, LuaValueRef var2);
    }

    private static final class StepBinder {
        final Map<String, SetterBinding> settersByKey;
        final Map<String, FieldBinding> fieldsByKey;
        final List<OptionSpec> schema;

        StepBinder(Map<String, SetterBinding> settersByKey, Map<String, FieldBinding> fieldsByKey, List<OptionSpec> schema) {
            this.settersByKey = settersByKey;
            this.fieldsByKey = fieldsByKey;
            this.schema = schema;
        }

        static StepBinder build(Class<?> clazz) {
            HashMap<String, SetterBinding> setters = new HashMap<String, SetterBinding>();
            HashMap<String, FieldBinding> fields = new HashMap<String, FieldBinding>();
            ArrayList<OptionSpec> schema = new ArrayList<OptionSpec>();
            for (Field f : ShadowPipelineRegistry.allFields(clazz)) {
                Class<?> t;
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || !ShadowPipelineRegistry.isSupportedType(t = f.getType())) continue;
                ShadowOption meta = f.getAnnotation(ShadowOption.class);
                String name = ShadowPipelineRegistry.optionName(meta, f.getName());
                f.setAccessible(true);
                FieldBinding fb = new FieldBinding(f, t, meta);
                for (String k : ShadowPipelineRegistry.optionKeys(meta, name)) {
                    fields.put(ShadowPipelineRegistry.normalizeKey(k), fb);
                }
                schema.add(new OptionSpec(name, ShadowPipelineRegistry.kindOf(t), meta != null ? meta.doc() : "", "", meta != null && !Double.isNaN(meta.min()) ? Double.valueOf(meta.min()) : null, meta != null && !Double.isNaN(meta.max()) ? Double.valueOf(meta.max()) : null));
            }
            for (Method m : ShadowPipelineRegistry.allMethods(clazz)) {
                String prop;
                Class<?> pt;
                if (Modifier.isStatic(m.getModifiers()) || !m.getName().startsWith("set") || m.getParameterCount() != 1 || !ShadowPipelineRegistry.isSupportedType(pt = m.getParameterTypes()[0]) || (prop = ShadowPipelineRegistry.decapitalize(m.getName().substring(3))).isEmpty()) continue;
                ShadowOption meta = m.getAnnotation(ShadowOption.class);
                String name = ShadowPipelineRegistry.optionName(meta, prop);
                m.setAccessible(true);
                SetterBinding sb = new SetterBinding(m, pt, meta);
                for (String k : ShadowPipelineRegistry.optionKeys(meta, name)) {
                    setters.put(ShadowPipelineRegistry.normalizeKey(k), sb);
                }
                schema.add(new OptionSpec(name, ShadowPipelineRegistry.kindOf(pt), meta != null ? meta.doc() : "", "", meta != null && !Double.isNaN(meta.min()) ? Double.valueOf(meta.min()) : null, meta != null && !Double.isNaN(meta.max()) ? Double.valueOf(meta.max()) : null));
            }
            schema.sort(Comparator.comparing(a -> a.name));
            return new StepBinder(setters, fields, schema);
        }

        void apply(Logger log, Object target, LuaValueRef cfg) {
            if (cfg == null || cfg.isNull()) {
                return;
            }
            if (!cfg.hasMembers()) {
                return;
            }
            Set<String> keys = cfg.getMemberKeys();
            if (keys == null || keys.isEmpty()) {
                return;
            }
            for (String rawKey : keys) {
                Object parsed;
                LuaValueRef v = cfg.getMember(rawKey);
                if (v == null || v.isNull()) continue;
                String k = ShadowPipelineRegistry.normalizeKey(rawKey);
                SetterBinding sb = this.settersByKey.get(k);
                if (sb != null) {
                    Object parsed2 = ShadowPipelineRegistry.parseValue(v, sb.paramType);
                    if (parsed2 == null) continue;
                    try {
                        sb.invoke(target, parsed2);
                    }
                    catch (Throwable t) {
                        if (log == null) continue;
                        log.warn("[shadow] cfg set failed {}.{}: {}", (Object)target.getClass().getSimpleName(), (Object)rawKey, (Object)t.toString());
                    }
                    continue;
                }
                FieldBinding fb = this.fieldsByKey.get(k);
                if (fb == null || (parsed = ShadowPipelineRegistry.parseValue(v, fb.type)) == null) continue;
                try {
                    fb.set(target, parsed);
                }
                catch (Throwable t) {
                    if (log == null) continue;
                    log.warn("[shadow] cfg field set failed {}.{}: {}", (Object)target.getClass().getSimpleName(), (Object)rawKey, (Object)t.toString());
                }
            }
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
            this.field.set(target, v);
        }
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
            this.method.invoke(target, v);
        }
    }

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
}

