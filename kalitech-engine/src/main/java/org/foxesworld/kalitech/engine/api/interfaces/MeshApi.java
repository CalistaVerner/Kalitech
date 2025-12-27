// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

/**
 * JS-first mesh constructor.
 *
 * <pre>
 * engine.mesh().create({
 *   type: "box"|"sphere"|"cylinder"|"capsule",
 *   size|radius|height|hx|hy|hz,
 *   name,
 *   pos|rot|scale,
 *   material: {def, params} | MaterialHandle,
 *   physics: {enabled?, mass, lockRotation?, kinematic?, friction?, restitution?, damping?, collider?},
 *   attach: true|false
 * })
 * </pre>
 */
public interface MeshApi {
    @HostAccess.Export
    SurfaceApi.SurfaceHandle create(Value cfg);
}
