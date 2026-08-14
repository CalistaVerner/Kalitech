/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.material.MatParam
 *  com.jme3.material.Material
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector2f
 *  com.jme3.math.Vector3f
 *  com.jme3.math.Vector4f
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.material.MaterialParsers;
import org.foxesworld.kalitech.engine.modules.material.MaterialTextureLoader;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;
import org.foxesworld.kalitech.engine.modules.material.MaterialUtils;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

final class MaterialParamApplier {
    private static final Logger log = LogManager.getLogger(MaterialParamApplier.class);

    private MaterialParamApplier() {
    }

    static boolean applyParamAsync(Material m, String name, LuaValueRef v) {
        MaterialTypes.ParsedTex pt;
        if (m == null || name == null || name.isBlank() || MaterialParsers.isNull(v)) {
            return false;
        }
        MatParam declared = m.getParam(name);
        if (declared != null && MaterialParamApplier.applyByDeclared(m, name, declared, v)) {
            return true;
        }
        MaterialTypes.TextureDesc td = MaterialParsers.parseTextureDesc(v);
        if (td != null) {
            MaterialTextureLoader.setTextureSafe(m, name, td);
            return true;
        }
        ColorRGBA c = MaterialParsers.parseColor(v);
        if (c != null) {
            m.setColor(name, c);
            return true;
        }
        Vector2f v2 = MaterialParsers.parseVec2(v);
        if (v2 != null) {
            m.setVector2(name, v2);
            return true;
        }
        Vector3f v3 = MaterialParsers.parseVec3(v);
        if (v3 != null) {
            m.setVector3(name, v3);
            return true;
        }
        Vector4f v4 = MaterialParsers.parseVec4(v);
        if (v4 != null) {
            m.setVector4(name, v4);
            return true;
        }
        if (v.isBoolean()) {
            m.setBoolean(name, v.asBoolean());
            return true;
        }
        if (v.isNumber()) {
            m.setFloat(name, (float)v.asDouble());
            return true;
        }
        if (v.isString() && (pt = MaterialParsers.parseTextureShorthand(v.asString())).path() != null && !pt.path().isBlank()) {
            MaterialTextureLoader.setTextureSafe(m, name, new MaterialTypes.TextureDesc(pt.path(), pt.wrap(), null, null, 0, null));
            return true;
        }
        if (MaterialUtils.debug()) {
            log.warn("[MAT] applyParam: could not apply param='{}' type={}", (Object)name, (Object)MaterialParsers.safeType(v));
        }
        return false;
    }

    private static boolean applyByDeclared(Material m, String name, MatParam declared, LuaValueRef v) {
        String type = declared.getVarType().name();
        try {
            switch (type) {
                case "Boolean": {
                    if (v.isBoolean()) {
                        m.setBoolean(name, v.asBoolean());
                        return true;
                    }
                    if (!v.isNumber()) break;
                    m.setBoolean(name, v.asDouble() != 0.0);
                    return true;
                }
                case "Int": {
                    if (!v.isNumber()) break;
                    m.setInt(name, (int)Math.round(v.asDouble()));
                    return true;
                }
                case "Float": {
                    if (!v.isNumber()) break;
                    m.setFloat(name, (float)v.asDouble());
                    return true;
                }
                case "Color": {
                    ColorRGBA c = MaterialParsers.parseColor(v);
                    if (c != null) {
                        m.setColor(name, c);
                        return true;
                    }
                    break;
                }
                case "Vector2": {
                    Vector2f vv = MaterialParsers.parseVec2(v);
                    if (vv != null) {
                        m.setVector2(name, vv);
                        return true;
                    }
                    break;
                }
                case "Vector3": {
                    Vector3f vv = MaterialParsers.parseVec3(v);
                    if (vv != null) {
                        m.setVector3(name, vv);
                        return true;
                    }
                    break;
                }
                case "Vector4": {
                    Vector4f vv = MaterialParsers.parseVec4(v);
                    if (vv != null) {
                        m.setVector4(name, vv);
                        return true;
                    }
                    break;
                }
                case "Texture2D": 
                case "Texture3D": 
                case "TextureCubeMap": {
                    MaterialTypes.TextureDesc td = MaterialParsers.parseTextureDesc(v);
                    if (td != null) {
                        MaterialTextureLoader.setTextureSafe(m, name, td);
                        return true;
                    }
                    break;
                }
            }
        }
        catch (Throwable e) {
            log.warn("[MAT] applyByDeclared failed param='{}' declaredType={} err={}", (Object)name, (Object)type, (Object)e.toString());
        }
        return false;
    }
}

