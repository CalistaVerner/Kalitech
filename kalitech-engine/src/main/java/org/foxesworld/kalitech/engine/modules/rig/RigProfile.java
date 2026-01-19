package org.foxesworld.kalitech.engine.modules.rig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigProfile
 *
 * Immutable, data-driven rig description.
 * The profile is intentionally domain-agnostic: it describes "roles" and "sockets"
 * without assuming arms/weapons/characters.
 *
 * <p>Concepts:</p>
 * <ul>
 *   <li><b>Role</b> - canonical name (e.g. "root", "mount.primary", "tip") mapped to a bone name.</li>
 *   <li><b>Aliases</b> - optional fallback names for resolving bones across different rigs.</li>
 *   <li><b>Socket</b> - a named attachment point derived from a role and local offset.</li>
 * </ul>
 */
public final class RigProfile {

    public final String id;
    public final SkeletonSpec skeleton;
    public final Map<String, SocketSpec> sockets;

    public RigProfile(String id, SkeletonSpec skeleton, Map<String, SocketSpec> sockets) {
        this.id = normalizeId(id);
        this.skeleton = Objects.requireNonNull(skeleton, "skeleton");
        this.sockets = Collections.unmodifiableMap(new LinkedHashMap<>(
                (sockets != null) ? sockets : Map.of()
        ));
    }

    private static String normalizeId(String id) {
        String s = (id == null) ? "" : id.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("RigProfile.id is empty");
        return s;
    }

    public SocketSpec socket(String socketId) {
        if (socketId == null) return null;
        return sockets.get(socketId);
    }

    // ---------------------------------------------------------------------
    // Nested types
    // ---------------------------------------------------------------------

    public static final class SkeletonSpec {

        /**
         * Root bone name (as authored in the asset). May be used for validation only.
         */
        public final String root;

        /**
         * Canonical role -> authored bone name mapping.
         */
        public final Map<String, String> roles;

        /**
         * Authored bone name -> alternative bone names.
         * Helps resolve rigs where bones are named differently.
         */
        public final Map<String, String[]> aliases;

        public SkeletonSpec(String root, Map<String, String> roles, Map<String, String[]> aliases) {
            this.root = normalizeOptional(root);
            this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(roles, "roles")
            ));
            this.aliases = Collections.unmodifiableMap(new LinkedHashMap<>(
                    (aliases != null) ? aliases : Map.of()
            ));

            if (this.roles.isEmpty()) {
                throw new IllegalArgumentException("RigProfile.skeleton.roles is empty");
            }
        }

        public String roleBoneName(String role) {
            if (role == null) return null;
            return roles.get(role);
        }

        public String[] aliasesForBone(String boneName) {
            if (boneName == null) return null;
            return aliases.get(boneName);
        }

        private static String normalizeOptional(String s) {
            String t = (s == null) ? "" : s.trim();
            return t.isEmpty() ? null : t;
        }
    }

    public static final class SocketSpec {

        /**
         * Socket resolved from a role (preferred) or direct bone name (fallback).
         * Exactly one of boneRole / boneName should be non-null.
         */
        public final String boneRole;

        public final String boneName;

        /**
         * Local translation offset relative to the resolved bone transform.
         */
        public final float ox, oy, oz;

        /**
         * Local rotation offset in degrees (XYZ order).
         */
        public final float rxDeg, ryDeg, rzDeg;

        public SocketSpec(String boneRole, String boneName,
                          float ox, float oy, float oz,
                          float rxDeg, float ryDeg, float rzDeg) {
            this.boneRole = normalizeOptional(boneRole);
            this.boneName = normalizeOptional(boneName);

            if (this.boneRole == null && this.boneName == null) {
                throw new IllegalArgumentException("SocketSpec requires boneRole or boneName");
            }
            if (this.boneRole != null && this.boneName != null) {
                throw new IllegalArgumentException("SocketSpec must use either boneRole or boneName, not both");
            }

            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.rxDeg = rxDeg;
            this.ryDeg = ryDeg;
            this.rzDeg = rzDeg;
        }

        private static String normalizeOptional(String s) {
            String t = (s == null) ? "" : s.trim();
            return t.isEmpty() ? null : t;
        }
    }
}