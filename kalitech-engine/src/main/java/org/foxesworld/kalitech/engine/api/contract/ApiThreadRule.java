package org.foxesworld.kalitech.engine.api.contract;

public enum ApiThreadRule {
    ANY,
    JME,        // must be executed on JME thread
    NOT_JME     // must NOT be executed on JME thread
}