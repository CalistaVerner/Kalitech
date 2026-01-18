// FILE: org/foxesworld/kalitech/engine/api/impl/ParticlesApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.ParticlesApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.particles.ParticleEmitterOps;
import org.foxesworld.kalitech.engine.modules.particles.ParticlesHostAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParticlesApiImpl extends AbstractApiModule implements ParticlesApi {

    private static final Logger log = LogManager.getLogger(ParticlesApiImpl.class);

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ParticleEmitter> byId = new ConcurrentHashMap<>();

    // Hot-path temporaries (JME thread only)
    private final Vector3f tmpPos = new Vector3f();
    private final Quaternion tmpRot = new Quaternion();

    private SimpleApplication app;
    private AssetManager assets;
    private Node root;

    public ParticlesApiImpl() {
        super("particles", "Particles", "3.2.0");
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.root = ctx.app.getRootNode();
        if (log.isDebugEnabled()) {
            log.debug("[particles] attach ok alive={}", byId.size());
        }
    }

    @Override
    public void detach() {
        try {
            onJmeSyncVoid("particles.detach", () -> {
                byId.forEach((id, em) -> {
                    if (em != null) em.removeFromParent();
                });
                byId.clear();
            });
        } finally {
            this.root = null;
            this.assets = null;
            this.app = null;
            super.detach();
            if (log.isDebugEnabled()) {
                log.debug("[particles] detach ok");
            }
        }
    }

    // ------------------------------------------------------------
    // Exported API
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public ParticleHandle create(Value cfg) {
        return profiled(() -> onJmeSync("particles.create", () -> {
            requireAttached();

            final int id = ids.getAndIncrement();
            final ParticleEmitter em = ParticleEmitterOps.createEmitter(assets, cfg, id);

            root.attachChild(em);
            byId.put(id, em);

            ParticlesHostAccess.applyEnabledIfPresent(em, cfg);

            if (log.isDebugEnabled()) {
                log.debug("[particles] create ok id={} name={} alive={}", id, em.getName(), byId.size());
            }

            return new ParticleHandle(id);
        }, new ParticleHandle(0)));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void destroy(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("particles.destroy", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;

            byId.remove(h.id);
            em.removeFromParent();

            if (log.isDebugEnabled()) {
                log.debug("[particles] destroy ok id={} name={} alive={}", h.id, em.getName(), byId.size());
            }
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setEnabled(ParticleHandle h, boolean enabled) {
        profiledVoid(() -> onJmeSyncVoid("particles.setEnabled", () -> {
            final ParticleEmitter em = get(h);
            if (em != null) em.setEnabled(enabled);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void play(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("particles.play", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;
            em.setEnabled(true);
            em.setParticlesPerSec(Math.max(0f, em.getParticlesPerSec()));
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void stop(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("particles.stop", () -> {
            final ParticleEmitter em = get(h);
            if (em != null) em.setParticlesPerSec(0f);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void configure(ParticleHandle h, Value cfg) {
        profiledVoid(() -> onJmeSyncVoid("particles.configure", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;
            ParticleEmitterOps.configureEmitter(assets, em, cfg);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPosition(ParticleHandle h, Value v3) {
        profiledVoid(() -> onJmeSyncVoid("particles.setPosition", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;
            ParticlesHostAccess.readVec3Into(v3, tmpPos, em.getLocalTranslation());
            em.setLocalTranslation(tmpPos.x, tmpPos.y, tmpPos.z);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setRotation(ParticleHandle h, Value q) {
        profiledVoid(() -> onJmeSyncVoid("particles.setRotation", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;
            ParticlesHostAccess.readQuatInto(q, tmpRot, em.getLocalRotation());
            em.setLocalRotation(tmpRot);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setScale(ParticleHandle h, double s) {
        profiledVoid(() -> onJmeSyncVoid("particles.setScale", () -> {
            final ParticleEmitter em = get(h);
            if (em != null) em.setLocalScale(ParticlesHostAccess.scale(s));
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void emitAll(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("particles.emitAll", () -> {
            final ParticleEmitter em = get(h);
            if (em != null) em.emitAllParticles();
        }));
    }

    /**
     * Immediately removes all currently alive particles from the emitter.
     * Useful for pooling to avoid "ghost" particles between reuses.
     */
    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void clear(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("particles.clear", () -> {
            final ParticleEmitter em = get(h);
            if (em != null) em.killAllParticles();
        }));
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void emit(ParticleHandle h, int count) {
        profiledVoid(() -> onJmeSyncVoid("particles.emit", () -> {
            final ParticleEmitter em = get(h);
            if (em == null) return;
            if (count <= 0) em.emitAllParticles();
            else em.emitParticles(count);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int alive() {
        return profiled(byId::size);
    }

    private ParticleEmitter get(ParticleHandle h) {
        if (h == null || h.id <= 0) return null;
        return byId.get(h.id);
    }
}