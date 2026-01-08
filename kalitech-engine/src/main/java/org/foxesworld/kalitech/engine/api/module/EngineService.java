package org.foxesworld.kalitech.engine.api.module;

public interface EngineService {
    String id();

    default void attach(ApiContext ctx) {
    }

    default void detach() {
    }
}