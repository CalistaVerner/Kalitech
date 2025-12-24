package org.foxesworld.kalitech.engine.util;

import java.util.HashSet;
import java.util.Set;

public class ReadCsv {

    public static Set<String> readCsvProperty(String key, Set<String> defaults) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) return defaults;

        HashSet<String> out = new HashSet<>();
        for (String s : raw.split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out.isEmpty() ? defaults : Set.copyOf(out);
    }

}
