package org.foxesworld.kalitech.engine.modules.rig;

import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.rig.jme.JmeSkeletonResolver;
import org.foxesworld.kalitech.engine.world.systems.rig.RigService;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Objects;

/**
 * RigApi
 *
 * JS-facing API installed by RigSystem into SystemContext state.
 * This API is domain-agnostic: it works with any rigged model.
 */
public final class RigApi {

    private final RigService service;

    public RigApi(RigService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @HostAccess.Export
    public String version() {
        return "0.1.0";
    }

    @HostAccess.Export
    public boolean hasProfile(String id) {
        return service.profiles().get(id) != null;
    }

    /**
     * Register or replace a profile.
     * Input is a JS object with strict schema compatible with RigProfileValueParser.
     */
    @HostAccess.Export
    public void registerProfile(Value jsProfile) {
        RigProfile p = RigProfileValueParser.parse(jsProfile);
        Map<String, RigProfile> merged = RigProfileMaps.copyAndPut(service.profiles().all(), p);
        service.profiles().replaceAll(merged);
    }

    /**
     * Register many profiles at once (JS object: { id1: {...}, id2: {...} } or array of profiles).
     */
    @HostAccess.Export
    public int registerMany(Value jsProfiles) {
        Map<String, RigProfile> loaded = RigProfileValueParser.parseMany(jsProfiles);
        if (loaded.isEmpty()) return 0;
        Map<String, RigProfile> merged = RigProfileMaps.copyAndPutAll(service.profiles().all(), loaded);
        service.profiles().replaceAll(merged);
        return loaded.size();
    }

    /**
     * Bind a profile to a JME Spatial (SkinningControl/SkeletonControl).
     * Returns a binding map usable from JS.
     */
    @HostAccess.Export
    public Value bindToSpatial(Value js, String profileId, Object spatialObj) {
        Objects.requireNonNull(profileId, "profileId");
        if (!(spatialObj instanceof Spatial sp)) {
            throw new IllegalArgumentException("bindToSpatial expects com.jme3.scene.Spatial as 3rd argument");
        }

        SkeletonView view = JmeSkeletonResolver.resolve(sp);
        if (view == null) {
            throw new IllegalStateException("No skeleton found on Spatial (SkinningControl/SkeletonControl missing)");
        }

        RigBinding b = service.bind(profileId, view);
        return RigBindingValueCodec.toJs(js, b);
    }

    @HostAccess.Export
    public String[] listBones(Object spatialObj) {
        if (!(spatialObj instanceof Spatial sp)) {
            throw new IllegalArgumentException("listBones expects com.jme3.scene.Spatial as argument");
        }

        SkeletonView view = JmeSkeletonResolver.resolve(sp);
        if (view == null) {
            return new String[0];
        }

        int count = view.boneCount();
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            out[i] = view.boneName(i);
        }
        return out;
    }
}
