package org.foxesworld.kalitech.engine.world.systems.registry;

import java.util.Objects;

/**
 * Base class for {@link SystemProvider} implementations that want to expose
 * consistent metadata for logging and registry diagnostics.
 */
public abstract class AbstractSystemProvider implements SystemProvider {

    private final SystemDescriptor descriptor;

    protected AbstractSystemProvider(SystemDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public final String id() {
        return descriptor.id();
    }

    @Override
    public SystemDescriptor descriptor() {
        return descriptor;
    }
}
