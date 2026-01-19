package org.foxesworld.kalitech.engine.modules.rig;

import org.graalvm.polyglot.Value;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigProfileValueParser
 *
 * Strict parser for JS objects to RigProfile.
 */
public final class RigProfileValueParser {

    private RigProfileValueParser() {
    }

    public static RigProfile parse(Value v) {
        if (v == null || v.isNull() || !v.hasMembers()) {
            throw new IllegalArgumentException("RigProfile must be a JS object");
        }

        String id = reqString(v, "id");
        Value skeletonV = reqObj(v, "skeleton");

        String root = optString(skeletonV, "root", null);

        Map<String, String> roles = readStringMap(reqObj(skeletonV, "roles"));
        Map<String, String[]> aliases = readAliasesMap(optObj(skeletonV, "aliases"));

        RigProfile.SkeletonSpec sk = new RigProfile.SkeletonSpec(root, roles, aliases);

        Map<String, RigProfile.SocketSpec> sockets = new LinkedHashMap<>();
        Value socketsV = optObj(v, "sockets");
        if (socketsV != null) {
            for (String key : socketsV.getMemberKeys()) {
                Value s = socketsV.getMember(key);
                if (s == null || s.isNull() || !s.hasMembers()) continue;

                String boneRole = optString(s, "boneRole", null);
                String boneName = optString(s, "boneName", null);

                float[] off = readVec3(optMember(s, "offset"), 0f, 0f, 0f);
                float[] rot = readVec3(optMember(s, "rotDeg"), 0f, 0f, 0f);

                RigProfile.SocketSpec spec = new RigProfile.SocketSpec(
                        boneRole, boneName,
                        off[0], off[1], off[2],
                        rot[0], rot[1], rot[2]
                );
                sockets.put(key, spec);
            }
        }

        return new RigProfile(id, sk, sockets);
    }

    /**
     * Accepts:
     * - array of profiles: [ {id:...}, {id:...} ]
     * - object map: { "id1": {id:"id1", ...}, "id2": {...} }
     */
    public static Map<String, RigProfile> parseMany(Value v) {
        if (v == null || v.isNull()) return Map.of();

        Map<String, RigProfile> out = new LinkedHashMap<>();

        if (v.hasArrayElements()) {
            long n = v.getArraySize();
            int len = (n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) n;
            for (int i = 0; i < len; i++) {
                Value el = v.getArrayElement(i);
                if (el == null || el.isNull()) continue;
                RigProfile p = parse(el);
                out.put(p.id, p);
            }
            return out;
        }

        if (v.hasMembers()) {
            for (String k : v.getMemberKeys()) {
                Value el = v.getMember(k);
                if (el == null || el.isNull()) continue;
                RigProfile p = parse(el);
                out.put(p.id, p);
            }
            return out;
        }

        return Map.of();
    }

    // ---------------------------------------------------------------------

    private static Value optMember(Value obj, String key) {
        try {
            if (obj != null && obj.hasMembers() && obj.hasMember(key)) {
                Value v = obj.getMember(key);
                return (v == null || v.isNull()) ? null : v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Value reqObj(Value obj, String key) {
        Value v = optMember(obj, key);
        if (v == null || !v.hasMembers()) throw new IllegalArgumentException("Missing object: " + key);
        return v;
    }

    private static Value optObj(Value obj, String key) {
        Value v = optMember(obj, key);
        if (v == null) return null;
        return v.hasMembers() ? v : null;
    }

    private static String reqString(Value obj, String key) {
        String s = optString(obj, key, null);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing string: " + key);
        return s.trim();
    }

    private static String optString(Value obj, String key, String def) {
        Value v = optMember(obj, key);
        if (v == null) return def;
        try {
            if (v.isString()) return v.asString();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static Map<String, String> readStringMap(Value obj) {
        Objects.requireNonNull(obj, "obj");
        if (!obj.hasMembers()) throw new IllegalArgumentException("Expected object map");

        Map<String, String> map = new LinkedHashMap<>();
        for (String k : obj.getMemberKeys()) {
            Value v = obj.getMember(k);
            if (v == null || v.isNull() || !v.isString()) continue;
            String s = v.asString();
            if (s == null || s.isBlank()) continue;
            map.put(k, s.trim());
        }

        if (map.isEmpty()) throw new IllegalArgumentException("roles map is empty");
        return map;
    }

    private static Map<String, String[]> readAliasesMap(Value obj) {
        if (obj == null) return Map.of();
        if (!obj.hasMembers()) return Map.of();

        Map<String, String[]> map = new LinkedHashMap<>();
        for (String k : obj.getMemberKeys()) {
            Value v = obj.getMember(k);
            if (v == null || v.isNull()) continue;

            if (v.hasArrayElements()) {
                long n = v.getArraySize();
                int len = (n > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) n;
                String[] arr = new String[len];
                for (int i = 0; i < len; i++) {
                    Value el = v.getArrayElement(i);
                    arr[i] = (el != null && !el.isNull() && el.isString()) ? el.asString() : null;
                }
                map.put(k, arr);
                continue;
            }

            if (v.isString()) {
                map.put(k, new String[]{v.asString()});
            }
        }
        return map;
    }

    private static float[] readVec3(Value v, float dx, float dy, float dz) {
        if (v == null || v.isNull()) return new float[]{dx, dy, dz};

        try {
            if (v.hasArrayElements()) {
                float x = (v.getArraySize() > 0) ? (float) v.getArrayElement(0).asDouble() : dx;
                float y = (v.getArraySize() > 1) ? (float) v.getArrayElement(1).asDouble() : dy;
                float z = (v.getArraySize() > 2) ? (float) v.getArrayElement(2).asDouble() : dz;
                return new float[]{x, y, z};
            }
            if (v.hasMembers()) {
                float x = (float) optNum(v, "x", dx);
                float y = (float) optNum(v, "y", dy);
                float z = (float) optNum(v, "z", dz);
                return new float[]{x, y, z};
            }
        } catch (Throwable ignored) {
        }

        return new float[]{dx, dy, dz};
    }

    private static double optNum(Value obj, String key, double def) {
        Value v = optMember(obj, key);
        if (v == null) return def;
        try {
            if (v.isNumber()) return v.asDouble();
        } catch (Throwable ignored) {
        }
        return def;
    }
}