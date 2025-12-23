package org.foxesworld.kalitech.engine.script;

public interface ScriptModule {
    void init(Object api);
    void update(Object api, float tpf);
    void destroy(Object api);
}