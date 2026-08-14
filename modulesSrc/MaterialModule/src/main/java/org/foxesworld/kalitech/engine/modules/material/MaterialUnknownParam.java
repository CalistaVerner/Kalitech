/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.Material
 *  com.jme3.material.MaterialDef
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.material.Material;
import com.jme3.material.MaterialDef;
import java.lang.reflect.Method;

final class MaterialUnknownParam {
    private static volatile Method DEF_GET_PARAM;
    private static volatile boolean LOOKUP_DONE;

    private MaterialUnknownParam() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     * Converted monitor instructions to comments
     * Lifted jumps to return sites
     */
    static boolean isProbablyUnknownParam(Material m, String name) {
        if (m == null) return false;
        if (name == null) return false;
        if (name.isBlank()) {
            return false;
        }
        if (name.equals("Time")) return false;
        if (name.equals("T")) return false;
        if (name.equals("Debug")) {
            return false;
        }
        try {
            MaterialDef def = m.getMaterialDef();
            if (def == null) {
                return false;
            }
            Method meth = DEF_GET_PARAM;
            if (!LOOKUP_DONE) {
                Class<MaterialUnknownParam> clazz = MaterialUnknownParam.class;
                // MONITORENTER : org.foxesworld.kalitech.engine.modules.material.MaterialUnknownParam.class
                if (!LOOKUP_DONE) {
                    try {
                        DEF_GET_PARAM = def.getClass().getMethod("getMaterialParam", String.class);
                    }
                    catch (Throwable ignored) {
                        DEF_GET_PARAM = null;
                    }
                    LOOKUP_DONE = true;
                    meth = DEF_GET_PARAM;
                }
                // MONITOREXIT : clazz
            }
            if (meth == null) {
                return false;
            }
            Object mp = meth.invoke(def, name);
            if (mp != null) return false;
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    static {
        LOOKUP_DONE = false;
    }
}

