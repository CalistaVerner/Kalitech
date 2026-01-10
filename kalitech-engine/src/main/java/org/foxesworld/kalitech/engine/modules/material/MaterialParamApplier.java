// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialParamApplier.java
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.modules.material.MaterialParsers.*;

final class MaterialParamApplier {

    private static final Logger log = LogManager.getLogger(MaterialParamApplier.class);

    private MaterialParamApplier() {
    }

    static boolean applyParamAsync(Material m, String name, Value v) {
        if (m == null || name == null || name.isBlank() || isNull(v)) return false;

        MatParam declared = m.getParam(name);
        if (declared != null && applyByDeclared(m, name, declared, v)) return true;

        // Inference
        MaterialTypes.TextureDesc td = parseTextureDesc(v);
        if (td != null) {
            MaterialTextureLoader.setTextureSafe(m, name, td);
            return true;
        }

        ColorRGBA c = parseColor(v);
        if (c != null) {
            m.setColor(name, c);
            return true;
        }

        Vector2f v2 = parseVec2(v);
        if (v2 != null) {
            m.setVector2(name, v2);
            return true;
        }

        Vector3f v3 = parseVec3(v);
        if (v3 != null) {
            m.setVector3(name, v3);
            return true;
        }

        Vector4f v4 = parseVec4(v);
        if (v4 != null) {
            m.setVector4(name, v4);
            return true;
        }

        if (v.isBoolean()) {
            m.setBoolean(name, v.asBoolean());
            return true;
        }
        if (v.isNumber()) {
            m.setFloat(name, (float) v.asDouble());
            return true;
        }

        if (v.isString()) {
            MaterialTypes.ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() != null && !pt.path().isBlank()) {
                MaterialTextureLoader.setTextureSafe(
                        m, name,
                        new MaterialTypes.TextureDesc(pt.path(), pt.wrap(), null, null, 0, null)
                );
                return true;
            }
        }

        if (MaterialUtils.debug()) log.warn("[MAT] applyParam: could not apply param='{}' type={}", name, safeType(v));
        return false;
    }

    private static boolean applyByDeclared(Material m, String name, MatParam declared, Value v) {
        String type = declared.getVarType().name();
        try {
            switch (type) {
                case "Boolean" -> {
                    if (v.isBoolean()) {
                        m.setBoolean(name, v.asBoolean());
                        return true;
                    }
                    if (v.isNumber()) {
                        m.setBoolean(name, v.asDouble() != 0.0);
                        return true;
                    }
                }
                case "Int" -> {
                    if (v.isNumber()) {
                        m.setInt(name, (int) Math.round(v.asDouble()));
                        return true;
                    }
                }
                case "Float" -> {
                    if (v.isNumber()) {
                        m.setFloat(name, (float) v.asDouble());
                        return true;
                    }
                }
                case "Color" -> {
                    ColorRGBA c = parseColor(v);
                    if (c != null) {
                        m.setColor(name, c);
                        return true;
                    }
                }
                case "Vector2" -> {
                    Vector2f vv = parseVec2(v);
                    if (vv != null) {
                        m.setVector2(name, vv);
                        return true;
                    }
                }
                case "Vector3" -> {
                    Vector3f vv = parseVec3(v);
                    if (vv != null) {
                        m.setVector3(name, vv);
                        return true;
                    }
                }
                case "Vector4" -> {
                    Vector4f vv = parseVec4(v);
                    if (vv != null) {
                        m.setVector4(name, vv);
                        return true;
                    }
                }
                case "Texture2D", "Texture3D", "TextureCubeMap" -> {
                    MaterialTypes.TextureDesc td = parseTextureDesc(v);
                    if (td != null) {
                        MaterialTextureLoader.setTextureSafe(m, name, td);
                        return true;
                    }
                }
                default -> {
                    // ignore
                }
            }
        } catch (Throwable e) {
            log.warn("[MAT] applyByDeclared failed param='{}' declaredType={} err={}", name, type, e.toString());
        }
        return false;
    }
}