package org.foxesworld.kalitech.engine.modules.rig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DefaultRigBinder
 *
 * Deterministic binder:
 * - Resolves roles using exact bone name
 * - Falls back to aliases (if present)
 * - Resolves sockets using boneRole (preferred) or boneName (fallback)
 */
public final class DefaultRigBinder implements RigBinder {

    @Override
    public RigBinding bind(RigProfile profile, SkeletonView skeleton) throws RigBindingException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(skeleton, "skeleton");

        final Map<String, Integer> roleToIndex = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : profile.skeleton.roles.entrySet()) {
            final String role = normalizeKey(e.getKey(), "role");
            final String boneName = normalizeKey(e.getValue(), "boneName");
            final int idx = resolveBoneIndex(profile, skeleton, boneName);
            if (idx < 0) {
                throw new RigBindingException("Failed to bind role='" + role + "' bone='" + boneName + "' for profile=" + profile.id);
            }
            roleToIndex.put(role, idx);
        }

        final Map<String, Integer> socketToIndex = new LinkedHashMap<>();
        for (Map.Entry<String, RigProfile.SocketSpec> e : profile.sockets.entrySet()) {
            final String socketId = normalizeKey(e.getKey(), "socketId");
            final RigProfile.SocketSpec s = Objects.requireNonNull(e.getValue(), "socketSpec");

            final int idx;
            if (s.boneRole != null) {
                Integer roleIdx = roleToIndex.get(s.boneRole);
                if (roleIdx == null) {
                    throw new RigBindingException("Socket '" + socketId + "' references unknown boneRole='" + s.boneRole + "' profile=" + profile.id);
                }
                idx = roleIdx;
            } else {
                idx = resolveBoneIndex(profile, skeleton, s.boneName);
                if (idx < 0) {
                    throw new RigBindingException("Failed to bind socket='" + socketId + "' boneName='" + s.boneName + "' profile=" + profile.id);
                }
            }

            socketToIndex.put(socketId, idx);
        }

        return new RigBinding(profile.id, roleToIndex, socketToIndex);
    }

    private static int resolveBoneIndex(RigProfile profile, SkeletonView skeleton, String authoredBoneName) {
        int idx = skeleton.findBoneIndex(authoredBoneName);
        if (idx >= 0) return idx;

        idx = findBoneIndexLoose(skeleton, authoredBoneName);
        if (idx >= 0) return idx;

        String[] aliases = profile.skeleton.aliasesForBone(authoredBoneName);
        if (aliases == null) return -1;

        for (String a : aliases) {
            if (a == null) continue;
            String t = a.trim();
            if (t.isEmpty()) continue;
            idx = skeleton.findBoneIndex(t);
            if (idx >= 0) return idx;

            idx = findBoneIndexLoose(skeleton, t);
            if (idx >= 0) return idx;
        }
        return -1;
    }

    private static String normalizeKey(String s, String what) {
        String t = (s == null) ? "" : s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(what + " is empty");
        return t;
    }

    private static int findBoneIndexLoose(SkeletonView skeleton, String boneName) {
        if (boneName == null) return -1;
        String target = boneName.trim();
        if (target.isEmpty()) return -1;

        String normalizedTarget = normalizeLoose(target);
        int count = skeleton.boneCount();
        for (int i = 0; i < count; i++) {
            String name = skeleton.boneName(i);
            if (name == null) continue;
            if (name.equals(target)) return i;
            if (name.equalsIgnoreCase(target)) return i;
            if (normalizedTarget.equals(normalizeLoose(name))) return i;
        }

        return -1;
    }

    private static String normalizeLoose(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
