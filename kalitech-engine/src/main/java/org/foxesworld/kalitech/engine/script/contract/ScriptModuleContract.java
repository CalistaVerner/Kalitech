// FILE: ScriptModuleContract.java
package org.foxesworld.kalitech.engine.script.contract;

import org.graalvm.polyglot.Value;

public final class ScriptModuleContract {

    public record Meta(String id, String version) {}

    public record Descriptor(
            String moduleId,
            Meta meta,
            Value exports,
            Value system,
            Value prefabs,
            Value dispose
    ) {}
}