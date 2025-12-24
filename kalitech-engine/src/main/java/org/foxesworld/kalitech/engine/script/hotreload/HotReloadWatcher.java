package org.foxesworld.kalitech.engine.script.hotreload;

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
        if (!registered.add(norm)) return;

        norm.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        log.debug("HotReloadWatcher registered: {}", norm);
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isInteresting(Path changedRelative) {
        if (changedRelative == null) return true;
        String name = changedRelative.toString().toLowerCase();
        // directory events are interesting too
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
                Path rel = pev.context();
                Path abs = watchedDir.resolve(rel).normalize();

                // If a new directory was created — start watching it too
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(abs)) registerAll(abs);
                    } catch (Exception ex) {
                        log.debug("HotReloadWatcher: failed to register new dir {}", abs, ex);
                    }
                }

                if (isInteresting(rel)) {
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