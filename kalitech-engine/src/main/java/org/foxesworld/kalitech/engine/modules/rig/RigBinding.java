package org.foxesworld.kalitech.engine.modules.rig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigBinding
 *
 * Resolved mapping of profile roles/sockets to concrete bone indices.
 * Produced by {@link RigBinder}.
 */
public final class RigBinding {

    public final String profileId;

    /**
     * Canonical role -> resolved bone index.
     */
    public final Map<String, Integer> roleToBoneIndex;

    /**
     * Socket id -> resolved bone index.
     */
    public final Map<String, Integer> socketToBoneIndex;

    public RigBinding(String profileId,
                      Map<String, Integer> roleToBoneIndex,
                      Map<String, Integer> socketToBoneIndex) {
        this.profileId = normalizeId(profileId);
        this.roleToBoneIndex = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(roleToBoneIndex, "roleToBoneIndex")
        ));
        this.socketToBoneIndex = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(socketToBoneIndex, "socketToBoneIndex")
        ));
    }

    public Integer boneIndexForRole(String role) {
        if (role == null) return null;
        return roleToBoneIndex.get(role);
    }

    public Integer boneIndexForSocket(String socketId) {
        if (socketId == null) return null;
        return socketToBoneIndex.get(socketId);
    }

    private static String normalizeId(String id) {
        String s = (id == null) ? "" : id.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("profileId is empty");
        return s;
    }
}