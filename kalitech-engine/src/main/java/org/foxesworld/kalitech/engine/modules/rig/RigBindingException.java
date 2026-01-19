package org.foxesworld.kalitech.engine.modules.rig;

/**
 * RigBindingException
 *
 * Thrown when a rig profile cannot be bound to a concrete skeleton.
 */
public final class RigBindingException extends RuntimeException {

    public RigBindingException(String message) {
        super(message);
    }

    public RigBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}