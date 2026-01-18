package org.foxesworld.kalitech.engine.script.runtime;

public interface BuiltinsRegistry {

    void init();

    Object require(String id);

    void invalidate(String id);
}