package org.foxesworld.kalitech.engine.script.resolve;

// Author: Calista Verner

import java.util.Optional;

@FunctionalInterface
public interface ResolverStrategy {

    /**
     * @param parentModuleId  текущий модуль (кто вызывает require), может быть "" для root
     * @param request         строка require("...")
     * @return нормализованный moduleId (например "Scripts/core/math.js") или empty если этот резолвер не применим
     */
    Optional<String> resolve(String parentModuleId, String request);
}