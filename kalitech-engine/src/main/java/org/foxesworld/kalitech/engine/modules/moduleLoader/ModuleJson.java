package org.foxesworld.kalitech.engine.modules.moduleLoader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON parsing utilities for module.json.
 */
final class ModuleJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ModuleJson() {
    }

    static ModuleDescriptor parse(String json) throws Exception {
        Dto dto = MAPPER.readValue(json, Dto.class);
        return new ModuleDescriptor(
                dto.id,
                dto.name,
                dto.version,
                dto.mainClass,
                dto.depends,
                dto.js,
                dto.types,
                dto.docs,
                dto.globals
        );
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static final class Dto {
        public String id;
        public String name;
        public String version;
        public String mainClass;
        public List<String> depends;
        public String js;
        public String types;
        public String docs;
        public String[] globals;
    }
}