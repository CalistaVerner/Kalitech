package org.foxesworld.kalitech.script;

import java.util.Map;

public interface ScriptService {
    Object eval(String languageId, String code, Map<String, Object> bindings);
}