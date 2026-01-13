package org.foxesworld.kalitech.engine.api.contract;

public enum ContractMode {
    OFF,        // no validation, no clamping
    STRICT,     // throw on violation
    CLAMP       // clamp where possible (range), else throw
}