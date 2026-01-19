package org.foxesworld.kalitech.engine.api.registry;

import java.util.Objects;

public final class TaskKey<T> {
    private final String id;
    private final Class<T> type;

    private TaskKey(String id, Class<T> type) {
        String trimmed = Objects.requireNonNull(id, "id").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("TaskKey.id is blank");
        }
        this.id = trimmed;
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> TaskKey<T> of(String id, Class<T> type) {
        return new TaskKey<>(id, type);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskKey<?> that)) return false;
        return id.equals(that.id) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "TaskKey{" + id + ", type=" + type.getSimpleName() + '}';
    }
}
