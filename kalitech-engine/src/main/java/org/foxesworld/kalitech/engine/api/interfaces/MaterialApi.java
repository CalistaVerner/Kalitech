// FILE: org/foxesworld/kalitech/engine/api/interfaces/MaterialApi.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.material.Material;
import org.foxesworld.kalitech.engine.api.types.MaterialHandle;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface MaterialApi {

    // JS-visible

    @HostAccess.Export
    MaterialHandle create(Value cfg);

    @HostAccess.Export
    int createId(Value cfg);

    @HostAccess.Export
    MaterialHandle getById(int id);

    @HostAccess.Export
    void destroy(MaterialHandle handle);

    @HostAccess.Export
    void destroyById(int id);

    @HostAccess.Export
    void set(MaterialHandle handle, Value params);

    @HostAccess.Export
    void setById(int id, Value params);

    // Java-only (engine-internal)
    Material material(MaterialHandle handle);

    Material materialById(int id);
}