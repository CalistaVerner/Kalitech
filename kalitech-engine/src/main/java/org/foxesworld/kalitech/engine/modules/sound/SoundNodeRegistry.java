// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundNodeRegistry.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.audio.AudioNode;
import org.foxesworld.kalitech.engine.util.LongHashMap;

import java.util.concurrent.atomic.AtomicLong;

public final class SoundNodeRegistry {

    private static final String USERDATA_SOUND_ID = "kalitech.soundId";

    private final LongHashMap<AudioNode> nodes = new LongHashMap<>(256);
    private final AtomicLong nextId = new AtomicLong(1L);

    public long cache(AudioNode node) {
        long id = nextId.getAndIncrement();
        nodes.put(id, node);
        node.setUserData(USERDATA_SOUND_ID, id);
        return id;
    }

    public long getId(AudioNode node) {
        if (node == null) return 0L;
        Long id = node.getUserData(USERDATA_SOUND_ID);
        return id != null ? id : 0L;
    }

    public AudioNode getById(long id) {
        return nodes.get(id);
    }

    public AudioNode remove(long id) {
        return nodes.remove(id);
    }
}