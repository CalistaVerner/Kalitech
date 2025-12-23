package org.foxesworld.kalitech.engine.script.hotreload;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

public final class HotReloadWatcher implements Closeable {

    private static final Logger log = LogManager.getLogger(HotReloadWatcher.class);

    private final WatchService watchService;
    private final Path root;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public HotReloadWatcher(Path rootDirectory) {
        try {
            this.root = rootDirectory.toAbsolutePath().normalize();
            this.watchService = FileSystems.getDefault().newWatchService();
            root.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            log.info("HotReloadWatcher watching: {}", root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start HotReloadWatcher", e);
        }
    }

    /** Вызывать в update(): если true — перезагружай скрипт. */
    public boolean pollDirty() {
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            for (WatchEvent<?> ev : key.pollEvents()) {
                WatchEvent.Kind<?> kind = ev.kind();
                if (kind == OVERFLOW) continue;

                dirty.set(true);
                log.debug("HotReload change detected: {} {}", kind.name(), ev.context());
            }
            key.reset();
        }
        return dirty.getAndSet(false);
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (Exception ignored) {}
    }
}