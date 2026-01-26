package org.foxesworld.kalitech.engine.moduleLoader;

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

    private static String[] coerceMainClass(Object v) {
        if (v == null) return null;

        if (v instanceof String s) {
            String t = s.trim();
            return t.isEmpty() ? null : new String[]{t};
        }

        if (v instanceof List<?> list) {
            if (list.isEmpty()) return null;
            String[] tmp = new String[list.size()];
            int n = 0;
            for (Object o : list) {
                if (o == null) continue;
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) tmp[n++] = s;
            }
            if (n == 0) return null;
            if (n == tmp.length) return tmp;
            String[] out = new String[n];
            System.arraycopy(tmp, 0, out, 0, n);
            return out;
        }

        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : new String[]{s};
    }

    static ModuleDescriptor parse(String json) throws Exception {
        Dto dto = MAPPER.readValue(json, Dto.class);
        return new ModuleDescriptor(
                dto.id,
                dto.name,
                dto.version,
                coerceMainClass(dto.mainClass),
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

        // Accept both: "mainClass": "..." and "mainClass": ["...","..."]
        public Object mainClass;

        public List<String> depends;
        public String js;
        public String types;
        public String docs;
        public String[] globals;
    }
}