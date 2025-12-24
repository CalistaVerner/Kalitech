package org.foxesworld.kalitech.engine.script.hotreload;

// Author: Calista Verner

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;
import static org.foxesworld.kalitech.engine.util.ReadCsv.readCsvProperty;

public final class HotReloadWatcher implements Closeable {

    private static final Logger log = LogManager.getLogger(HotReloadWatcher.class);

    private final Path root;
    private final WatchService watchService;

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    // registered dirs (avoid double register)
    private final Set<Path> registered = ConcurrentHashMap.newKeySet();

    // changed module ids (relative to root)
    private final Set<String> changedIds = ConcurrentHashMap.newKeySet();

    // optional: filter extensions
    private final Set<String> exts;

    // ---- IGNORE LISTS (Chromium/JCEF profile junk) ----

    // Any directory segment matching these will be ignored (skipped + no events)
    private static final Set<String> IGNORED_DIR_NAMES = readCsvProperty("kalitech.hotreload.ignore.dirs", new HashSet<>());

    // Some noisy files created by chromium profile root
    private static final Set<String> IGNORED_FILE_NAMES = readCsvProperty("kalitech.hotreload.ignore.files", new HashSet<>());

    public HotReloadWatcher(Path rootDirectory) {
        this(rootDirectory, Set.of(".js", ".json", ".glsl", ".txt"));
    }

    public HotReloadWatcher(Path rootDirectory, Set<String> extensions) {
        try {
            this.root = rootDirectory.toAbsolutePath().normalize();
            this.exts = extensions;
            this.watchService = FileSystems.getDefault().newWatchService();
            registerAll(root);
            log.info("HotReloadWatcher watching (recursive): {}", root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HotReloadWatcher", e);
        }
    }

    public Path root() {
        return root;
    }

    private void registerDir(Path dir) throws IOException {
        Path norm = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(norm)) return;

        // skip ignored subtrees
        if (shouldIgnoreDir(norm)) return;

        if (!registered.add(norm)) return;

        norm.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        log.debug("HotReloadWatcher registered: {}", norm);
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldIgnoreDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                registerDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Returns true if directory should be excluded from watching/indexing.
     * Rule: if ANY path segment relative to root matches IGNORED_DIR_NAMES -> ignore.
     */
    private boolean shouldIgnoreDir(Path absDir) {
        try {
            Path abs = absDir.toAbsolutePath().normalize();
            if (!abs.startsWith(root)) return true; // outside root (defensive)

            Path rel = root.relativize(abs);
            if (rel.getNameCount() == 0) return false; // root itself

            for (Path seg : rel) {
                if (IGNORED_DIR_NAMES.contains(seg.toString())) return true;
            }

            // defensive: known nested caches
            String relStr = rel.toString().replace('\\', '/');
            if (relStr.contains("Default/Cache")) return true;
            if (relStr.contains("Default/Code Cache")) return true;
            if (relStr.contains("Default/Network")) return true;

            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean shouldIgnoreFile(Path absFile) {
        try {
            Path abs = absFile.toAbsolutePath().normalize();
            if (!abs.startsWith(root)) return true;

            Path rel = root.relativize(abs);
            if (rel.getNameCount() == 0) return false;

            // if file is inside ignored dir -> ignore
            Path parent = abs.getParent();
            if (parent != null && shouldIgnoreDir(parent)) return true;

            String name = abs.getFileName().toString();
            return IGNORED_FILE_NAMES.contains(name);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Checks whether the changed path (RELATIVE TO ROOT) is interesting.
     * Directory events are interesting too (to detect new dirs), but ignored dirs are filtered earlier.
     */
    private boolean isInteresting(Path changedRelativeToRoot) {
        if (changedRelativeToRoot == null) return true;

        String relStr = changedRelativeToRoot.toString().replace('\\', '/');

        // quick ignore by segments
        for (String badDir : IGNORED_DIR_NAMES) {
            if (relStr.equals(badDir) || relStr.startsWith(badDir + "/")) return false;
            if (relStr.contains("/" + badDir + "/")) return false;
        }
        for (String badFile : IGNORED_FILE_NAMES) {
            if (relStr.equals(badFile) || relStr.endsWith("/" + badFile)) return false;
        }

        String name = relStr.toLowerCase(Locale.ROOT);

        // directory events (no extension) are interesting (handled elsewhere)
        if (!name.contains(".")) return true;

        for (String e : exts) {
            if (name.endsWith(e)) return true;
        }
        return false;
    }

    private void recordChanged(Path absPath) {
        try {
            Path abs = absPath.toAbsolutePath().normalize();
            if (!abs.startsWith(root)) return;

            if (Files.isDirectory(abs)) {
                if (shouldIgnoreDir(abs)) return;
            } else {
                if (shouldIgnoreFile(abs)) return;
            }

            String rel = root.relativize(abs).toString().replace('\\', '/');
            if (!rel.isBlank()) changedIds.add(rel);
        } catch (Exception ignored) {}
    }

    /**
     * Call in update(): returns a snapshot of changed module ids (relative to root),
     * then clears internal buffer.
     *
     * Example returned ids:
     * - "Scripts/systems/scene.js"
     * - "Scripts/entities/player.js"
     */
    public Set<String> pollChanged() {
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            Path watchedDir = (Path) key.watchable();

            for (WatchEvent<?> ev : key.pollEvents()) {
                WatchEvent.Kind<?> kind = ev.kind();
                if (kind == OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pev = (WatchEvent<Path>) ev;
                Path relToWatchedDir = pev.context();
                Path abs = watchedDir.resolve(relToWatchedDir).normalize();

                // compute rel-to-root for proper filtering
                Path relToRoot;
                try {
                    relToRoot = root.relativize(abs.toAbsolutePath().normalize());
                } catch (Exception e) {
                    relToRoot = null;
                }

                // If a new directory was created — start watching it too (unless ignored)
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(abs)) {
                            if (!shouldIgnoreDir(abs)) registerAll(abs);
                        }
                    } catch (Exception ex) {
                        log.debug("HotReloadWatcher: failed to register new dir {}", abs, ex);
                    }
                }

                if (isInteresting(relToRoot)) {
                    dirty.set(true);
                    recordChanged(abs);
                    log.debug("HotReload change: {} {}", kind.name(), abs);
                }
            }

            boolean ok = key.reset();
            if (!ok) {
                registered.remove(watchedDir);
            }
        }

        if (!dirty.getAndSet(false)) {
            return Set.of();
        }

        if (changedIds.isEmpty()) {
            return Set.of();
        }

        HashSet<String> out = new HashSet<>(changedIds);
        changedIds.clear();
        return Collections.unmodifiableSet(out);
    }

    /**
     * Backward-compatible: if true — something changed.
     * Internally also fills pollChanged().
     */
    public boolean pollDirty() {
        return !pollChanged().isEmpty();
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (Exception ignored) {}
        registered.clear();
        changedIds.clear();
    }
}