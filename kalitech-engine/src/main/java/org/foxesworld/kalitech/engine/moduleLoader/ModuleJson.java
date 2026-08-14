package org.foxesworld.kalitech.engine.moduleLoader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Strict parser for module.json.
 */
final class ModuleJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ModuleJson() {
    }

    private static String[] coerceMainClass(Object value) {
        if (value == null) return null;

        if (value instanceof String text) {
            String normalized = text.trim();
            return normalized.isEmpty() ? null : new String[]{normalized};
        }

        if (value instanceof List<?> list) {
            if (list.isEmpty()) return null;
            String[] temporary = new String[list.size()];
            int count = 0;
            for (Object item : list) {
                if (item == null) continue;
                String normalized = String.valueOf(item).trim();
                if (!normalized.isEmpty()) temporary[count++] = normalized;
            }
            if (count == 0) return null;
            if (count == temporary.length) return temporary;
            String[] result = new String[count];
            System.arraycopy(temporary, 0, result, 0, count);
            return result;
        }

        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : new String[]{normalized};
    }

    static ModuleDescriptor parse(String json) throws Exception {
        Dto dto = MAPPER.readValue(json, Dto.class);
        return new ModuleDescriptor(
                dto.id,
                dto.name,
                dto.version,
                coerceMainClass(dto.mainClass),
                dto.depends,
                dto.lua,
                dto.docs
        );
    }

    @SuppressWarnings("FieldMayBeFinal")
    private static final class Dto {
        public String id;
        public String name;
        public String version;
        public Object mainClass;
        public List<String> depends;
        public String lua;
        public String docs;
    }
}
