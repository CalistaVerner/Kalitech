/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetManager
 *  com.jme3.effect.ParticleEmitter
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Node
 *  com.jme3.scene.Spatial
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.foxesworld.kalitech.engine.modules.particles.ParticleLuaConfig;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class ParticlesApiImpl
extends AbstractApiModule
implements ParticlesApi {
    private static final Logger log = LogManager.getLogger(ParticlesApiImpl.class);
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ParticleEmitter> byId = new ConcurrentHashMap();
    private final Vector3f tmpPos = new Vector3f();
    private final Quaternion tmpRot = new Quaternion();
    private SimpleApplication app;
    private AssetManager assets;
    private Node root;

    public ParticlesApiImpl() {
        super("particles", "Particles", "3.2.0");
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.root = ctx.app.getRootNode();
        if (log.isDebugEnabled()) {
            log.debug("[particles] attach ok alive={}", (Object)this.byId.size());
        }
    }

    public void detach() {
        try {
            this.onJmeSyncVoid("particles.detach", () -> {
                this.byId.forEach((id, em) -> {
                    if (em != null) {
                        em.removeFromParent();
                    }
                });
                this.byId.clear();
            });
        }
        finally {
            this.root = null;
            this.assets = null;
            this.app = null;
            super.detach();
            if (log.isDebugEnabled()) {
                log.debug("[particles] detach ok");
            }
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public ParticlesApi.ParticleHandle create(LuaValueRef cfg) {
        return (ParticlesApi.ParticleHandle)this.profiled(() -> (ParticlesApi.ParticleHandle)this.onJmeSync("particles.create", () -> {
            this.requireAttached();
            int id = this.ids.getAndIncrement();
            ParticleEmitter em = ParticleEmitterOps.createEmitter(this.assets, cfg, id);
            this.root.attachChild((Spatial)em);
            this.byId.put(id, em);
            ParticleLuaConfig.applyEnabledIfPresent(em, cfg);
            if (log.isDebugEnabled()) {
                log.debug("[particles] create ok id={} name={} alive={}", (Object)id, (Object)em.getName(), (Object)this.byId.size());
            }
            return new ParticlesApi.ParticleHandle(id);
        }, new ParticlesApi.ParticleHandle(0)));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void destroy(ParticlesApi.ParticleHandle h) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.destroy", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            this.byId.remove(h.id);
            em.removeFromParent();
            if (log.isDebugEnabled()) {
                log.debug("[particles] destroy ok id={} name={} alive={}", (Object)h.id, (Object)em.getName(), (Object)this.byId.size());
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setEnabled(ParticlesApi.ParticleHandle h, boolean enabled) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.setEnabled", () -> {
            ParticleEmitter em = this.get(h);
            if (em != null) {
                em.setEnabled(enabled);
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void play(ParticlesApi.ParticleHandle h) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.play", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            em.setEnabled(true);
            em.setParticlesPerSec(Math.max(0.0f, em.getParticlesPerSec()));
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void stop(ParticlesApi.ParticleHandle h) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.stop", () -> {
            ParticleEmitter em = this.get(h);
            if (em != null) {
                em.setParticlesPerSec(0.0f);
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void configure(ParticlesApi.ParticleHandle h, LuaValueRef cfg) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.configure", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            ParticleEmitterOps.configureEmitter(this.assets, em, cfg);
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPosition(ParticlesApi.ParticleHandle h, LuaValueRef v3) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.setPosition", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            ParticleLuaConfig.readVec3Into(v3, this.tmpPos, em.getLocalTranslation());
            em.setLocalTranslation(this.tmpPos.x, this.tmpPos.y, this.tmpPos.z);
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setRotation(ParticlesApi.ParticleHandle h, LuaValueRef q) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.setRotation", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            ParticleLuaConfig.readQuatInto(q, this.tmpRot, em.getLocalRotation());
            em.setLocalRotation(this.tmpRot);
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setScale(ParticlesApi.ParticleHandle h, double s) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.setScale", () -> {
            ParticleEmitter em = this.get(h);
            if (em != null) {
                em.setLocalScale(ParticleLuaConfig.scale(s));
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void emitAll(ParticlesApi.ParticleHandle h) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.emitAll", () -> {
            ParticleEmitter em = this.get(h);
            if (em != null) {
                em.emitAllParticles();
            }
        }));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void clear(ParticlesApi.ParticleHandle h) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.clear", () -> {
            ParticleEmitter em = this.get(h);
            if (em != null) {
                em.killAllParticles();
            }
        }));
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void emit(ParticlesApi.ParticleHandle h, int count) {
        this.profiledVoid(() -> this.onJmeSyncVoid("particles.emit", () -> {
            ParticleEmitter em = this.get(h);
            if (em == null) {
                return;
            }
            if (count <= 0) {
                em.emitAllParticles();
            } else {
                em.emitParticles(count);
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public int alive() {
        return (Integer)this.profiled(this.byId::size);
    }

    private ParticleEmitter get(ParticlesApi.ParticleHandle h) {
        if (h == null || h.id <= 0) {
            return null;
        }
        return this.byId.get(h.id);
    }
}

