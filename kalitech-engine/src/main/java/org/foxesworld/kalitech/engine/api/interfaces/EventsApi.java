package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@Deprecated
public interface EventsApi {
    @HostAccess.Export void emit(String topic, Object payload);

    /** Subscribe: returns a subscription id (token) that can be used to off(). */
    @HostAccess.Export int on(String topic, Value handler);

    /**
     * Subscribe one-shot: handler is removed after the first event.
     */
    @HostAccess.Export
    int once(String topic, Value handler);

    /** Unsubscribe by token. */
    @HostAccess.Export boolean off(String topic, int token);

    /** Remove all listeners for a topic. */
    @HostAccess.Export void clear(String topic);
}