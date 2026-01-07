package org.foxesworld.kalitech.engine.api.module;

public interface ApiModule {
    String id();

    default String name() {
        return id();
    }

    default String version() {
        return "0.0.0";
    }

    default void attach(ApiContext ctx) {
    }

    default void detach() {
    }

    ApiStats stats();
}
