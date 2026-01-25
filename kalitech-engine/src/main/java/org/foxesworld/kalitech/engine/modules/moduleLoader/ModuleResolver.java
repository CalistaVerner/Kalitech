package org.foxesworld.kalitech.engine.modules.moduleLoader;

import java.util.*;

/**
 * Resolves module load order using topological sorting.
 */
public final class ModuleResolver {

    private static void visit(
            ModuleJar m,
            Map<String, ModuleJar> byId,
            Set<String> perm,
            Set<String> temp,
            List<ModuleJar> out
    ) {
        String id = m.desc.id;
        if (perm.contains(id)) return;
        if (!temp.add(id)) {
            throw new IllegalStateException("Cyclic dependency detected at: " + id);
        }

        for (String dep : m.desc.depends) {
            ModuleJar d = byId.get(dep);
            if (d == null) {
                throw new IllegalStateException("Missing dependency: " + id + " -> " + dep);
            }
            visit(d, byId, perm, temp, out);
        }

        temp.remove(id);
        perm.add(id);
        out.add(m);
    }

    public List<ModuleJar> resolveLoadOrder(List<ModuleJar> modules) {
        Objects.requireNonNull(modules, "modules");

        HashMap<String, ModuleJar> byId = new HashMap<>(modules.size() * 2);
        for (ModuleJar m : modules) {
            ModuleJar prev = byId.put(m.desc.id, m);
            if (prev != null) {
                throw new IllegalStateException("Duplicate module id: " + m.desc.id
                        + " (jars: " + prev.jarPath.getFileName() + ", " + m.jarPath.getFileName() + ")");
            }
        }

        ArrayList<ModuleJar> out = new ArrayList<>(modules.size());
        HashSet<String> perm = new HashSet<>();
        HashSet<String> temp = new HashSet<>();

        for (ModuleJar m : modules) {
            visit(m, byId, perm, temp, out);
        }

        return out;
    }
}