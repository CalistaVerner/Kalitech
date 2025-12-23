package org.foxesworld.kalitech.engine.script.hotreload;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public final class HotReloadWatcher implements Closeable {

    private static final Logger log = LogManager.getLogger(HotReloadWatcher.class);

    private final WatchService watchService;
    private final Path root;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    // keep track of registered dirs (avoid double register)
    private final Set<Path> registered = ConcurrentHashMap.newKeySet();

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

    /** Call in update(): if true — reload. */
    public boolean pollDirty() {
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
                        if (Files.isDirectory(abs)) {
                            registerAll(abs);
                        }
                    } catch (Exception ex) {
                        log.debug("HotReloadWatcher: failed to register new dir {}", abs, ex);
                    }
                }

                if (isInteresting(rel)) {
                    dirty.set(true);
                    log.debug("HotReload change: {} {}", kind.name(), abs);
                }
            }

            boolean ok = key.reset();
            if (!ok) {
                // directory no longer accessible
                registered.remove(watchedDir);
            }
        }
        return dirty.getAndSet(false);
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (Exception ignored) {}
        registered.clear();
    }
}