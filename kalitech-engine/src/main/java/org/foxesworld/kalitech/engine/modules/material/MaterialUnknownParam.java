// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialUnknownParam.java
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.material.Material;

import java.lang.reflect.Method;

final class MaterialUnknownParam {

    private static volatile Method DEF_GET_PARAM; // getMaterialParam(String)
    private static volatile boolean LOOKUP_DONE = false;

    private MaterialUnknownParam() {
    }

    static boolean isProbablyUnknownParam(Material m, String name) {
        if (m == null || name == null || name.isBlank()) return false;
        if (name.equals("Time") || name.equals("T") || name.equals("Debug")) return false;

        try {
            Object def = m.getMaterialDef();
            if (def == null) return false;

            Method meth = DEF_GET_PARAM;
            if (!LOOKUP_DONE) {
                synchronized (MaterialUnknownParam.class) {
                    if (!LOOKUP_DONE) {
                        try {
                            DEF_GET_PARAM = def.getClass().getMethod("getMaterialParam", String.class);
                        } catch (Throwable ignored) {
                            DEF_GET_PARAM = null;
                        }
                        LOOKUP_DONE = true;
                        meth = DEF_GET_PARAM;
                    }
                }
            }

            if (meth == null) return false;
            Object mp = meth.invoke(def, name);
            return mp == null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}