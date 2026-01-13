package org.foxesworld.kalitech.engine.api.contract;

public enum ApiFlag {
    EDITOR_VISIBLE,
    SANDBOX_ALLOWED,
    DETERMINISTIC,
    PURE,               // no side effects (best-effort contract)
    EXPERIMENTAL,
    INTERNAL
}