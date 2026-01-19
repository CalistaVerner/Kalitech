package org.foxesworld.kalitech.engine.world.systems.rig;

import org.foxesworld.kalitech.engine.modules.rig.*;

import java.util.Objects;

/**
 * RigService
 *
 * Facade used by other systems / scripting to resolve profiles and create bindings.
 * Engine-specific skeleton adapters should implement {@link SkeletonView}.
 */
public final class RigService {

    private final RigProfileRegistry profiles;
    private final RigBinder binder;

    public RigService(RigProfileRegistry profiles, RigBinder binder) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.binder = Objects.requireNonNull(binder, "binder");
    }

    public RigProfileRegistry profiles() {
        return profiles;
    }

    public RigBinding bind(String profileId, SkeletonView skeleton) {
        RigProfile p = profiles.require(profileId);
        return binder.bind(p, skeleton);
    }
}