package org.foxesworld.kalitech.engine.api.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stable registry for task handlers (API adapters, loaders, request handlers, etc.).
 * Core systems store only {@link TaskKey} references while implementations can be
 * registered, versioned, deprecated, and replaced without touching the core.
 */
public final class TaskRegistry {
    private static final Logger log = LogManager.getLogger(TaskRegistry.class);

    private final ConcurrentHashMap<TaskKey<?>, TaskBucket<?>> buckets = new ConcurrentHashMap<>();
    private final AtomicLong orderSeq = new AtomicLong(0L);

    public <T> Registration<T> register(TaskKey<T> key, T handler, ModuleInfo info) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(info, "info");

        if (!key.type().isInstance(handler)) {
            throw new IllegalArgumentException("Handler type mismatch for " + key + ": got " + handler.getClass());
        }

        TaskBucket<T> bucket = bucketFor(key, true);
        TaskHandler<T> entry = new TaskHandler<>(handler, info, orderSeq.incrementAndGet());
        bucket.register(entry);
        if (log.isDebugEnabled()) {
            log.debug("[tasks] registered key={} id={} ver={} deprecated={} impl={}",
                    key.id(),
                    info.id(),
                    info.version(),
                    info.deprecated(),
                    handler.getClass().getName());
        }
        if (info.deprecated()) {
            log.warn("[tasks] registered deprecated handler key={} id={} ver={} replacedBy={} notes={}",
                    key.id(),
                    info.id(),
                    info.version(),
                    info.replacedBy(),
                    info.notes());
        }
        return () -> bucket.unregister(info.id());
    }

    public <T> Optional<TaskHandler<T>> resolveLatest(TaskKey<T> key) {
        return resolveLatest(key, false);
    }

    public <T> Optional<TaskHandler<T>> resolveLatest(TaskKey<T> key, boolean includeDeprecated) {
        TaskBucket<T> bucket = bucketFor(key, false);
        if (bucket == null) return Optional.empty();
        return bucket.resolveLatest(includeDeprecated);
    }

    public <T> Optional<TaskHandler<T>> resolveById(TaskKey<T> key, String moduleId) {
        return resolveById(key, moduleId, false);
    }

    public <T> Optional<TaskHandler<T>> resolveById(TaskKey<T> key, String moduleId, boolean includeDeprecated) {
        TaskBucket<T> bucket = bucketFor(key, false);
        if (bucket == null) return Optional.empty();
        return bucket.resolveById(moduleId, includeDeprecated);
    }

    public <T> List<TaskHandler<T>> list(TaskKey<T> key) {
        TaskBucket<T> bucket = bucketFor(key, false);
        if (bucket == null) return List.of();
        return bucket.list();
    }

    @SuppressWarnings("unchecked")
    private <T> TaskBucket<T> bucketFor(TaskKey<T> key, boolean create) {
        if (!create) {
            return (TaskBucket<T>) buckets.get(key);
        }
        return (TaskBucket<T>) buckets.computeIfAbsent(key, k -> new TaskBucket<>());
    }

    public interface Registration<T> {
        void unregister();
    }

    public static final class TaskHandler<T> {
        private final T handler;
        private final ModuleInfo info;
        private final long order;

        private TaskHandler(T handler, ModuleInfo info, long order) {
            this.handler = handler;
            this.info = info;
            this.order = order;
        }

        public T handler() {
            return handler;
        }

        public ModuleInfo info() {
            return info;
        }

        long order() {
            return order;
        }
    }

    private static final class TaskBucket<T> {
        private final List<TaskHandler<T>> handlers = new ArrayList<>();

        synchronized void register(TaskHandler<T> handler) {
            handlers.add(handler);
        }

        synchronized Optional<TaskHandler<T>> resolveLatest(boolean includeDeprecated) {
            return handlers.stream()
                    .filter(h -> includeDeprecated || !h.info.deprecated())
                    .max(handlerComparator());
        }

        synchronized Optional<TaskHandler<T>> resolveById(String moduleId, boolean includeDeprecated) {
            if (moduleId == null || moduleId.trim().isEmpty()) return Optional.empty();
            String wanted = moduleId.trim();
            return handlers.stream()
                    .filter(h -> h.info.id().equals(wanted))
                    .filter(h -> includeDeprecated || !h.info.deprecated())
                    .max(handlerComparator());
        }

        synchronized List<TaskHandler<T>> list() {
            ArrayList<TaskHandler<T>> out = new ArrayList<>(handlers);
            out.sort(handlerComparator().reversed());
            return Collections.unmodifiableList(out);
        }

        synchronized void unregister(String moduleId) {
            if (moduleId == null || moduleId.trim().isEmpty()) return;
            String wanted = moduleId.trim();
            handlers.removeIf(h -> h.info.id().equals(wanted));
        }

        private Comparator<TaskHandler<T>> handlerComparator() {
            return Comparator
                    .comparing((TaskHandler<T> h) -> h.info.parsedVersion())
                    .thenComparingLong(TaskHandler::order);
        }
    }
}
