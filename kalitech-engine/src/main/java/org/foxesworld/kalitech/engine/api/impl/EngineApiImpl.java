package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.KalitechApplication;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.*;
import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

import java.util.Objects;

public final class EngineApiImpl implements EngineApi {

    private static final Logger log = LogManager.getLogger(EngineApiImpl.class);

    private final SimpleApplication app;
    private final AssetManager assets;
    private final ScriptEventBus bus;
    private final EcsWorld ecs;

    private final LogApi logApi;
    private final AssetsApi assetsApi;
    private final EventsApi eventsApi;
    private final EntityApi entityApi;

    public EngineApiImpl(SimpleApplication app, AssetManager assets, ScriptEventBus bus, EcsWorld ecs) {
        this.app = Objects.requireNonNull(app, "app");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.ecs = Objects.requireNonNull(ecs, "ecs");

        this.logApi = new LogApiImpl(log);
        this.assetsApi = new AssetsApiImpl(assets);
        this.eventsApi = new EventsApiImpl(bus);
        this.entityApi = new EntityApiImpl(ecs);
    }

    @HostAccess.Export @Override public LogApi log() { return logApi; }
    @HostAccess.Export @Override public AssetsApi assets() { return assetsApi; }
    @HostAccess.Export @Override public EventsApi events() { return eventsApi; }
    @HostAccess.Export @Override public EntityApi entity() { return entityApi; }
    @HostAccess.Export @Override public String engineVersion() { return ((KalitechApplication) app).getVersion(); }

}