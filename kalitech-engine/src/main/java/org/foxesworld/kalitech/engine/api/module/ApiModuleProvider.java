package org.foxesworld.kalitech.engine.api.module;

public interface ApiModuleProvider {
    String id();

    default int order() {
        return 0;
    }

    void register(ApiRegistry registry);
}
