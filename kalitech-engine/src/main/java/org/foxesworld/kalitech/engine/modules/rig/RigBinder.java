package org.foxesworld.kalitech.engine.modules.rig;

/**
 * RigBinder
 *
 * Resolves {@link RigProfile} roles and sockets against a concrete {@link SkeletonView}.
 */
public interface RigBinder {

    /**
     * Binds profile to skeleton.
     *
     * @param profile profile
     * @param skeleton skeleton adapter
     * @return resolved binding
     * @throws RigBindingException if required roles/sockets cannot be resolved
     */
    RigBinding bind(RigProfile profile, SkeletonView skeleton) throws RigBindingException;
}