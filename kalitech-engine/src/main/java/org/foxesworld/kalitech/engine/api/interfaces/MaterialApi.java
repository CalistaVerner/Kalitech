// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.api.impl.material.MaterialApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * JS-first material factory.
 *
 * <pre>
 * engine.material().create({
 *   def: "Common/MatDefs/Misc/Unshaded.j3md",
 *   params: { Color:[1,0,0,1] }
 * })
 * </pre>
 *
 * The returned handle is a stable host object that can be reused across surfaces.
 */
public interface MaterialApi {

    /**
     * Creates a new material handle from a config object.
     */
    @HostAccess.Export
    MaterialApiImpl.MaterialHandle create(Value cfg);

    /**
     * Destroys (releases) a previously created material handle.
     * Implementations may treat this as a no-op if materials are GC-managed or cached.
     */
    @HostAccess.Export
    void destroy(MaterialApiImpl.MaterialHandle handle);

    @HostAccess.Export
    void set(MaterialApiImpl.MaterialHandle handle, Value params);
}