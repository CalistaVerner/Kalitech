/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.util.LongHashMap
 */
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.audio.AudioNode;
import java.util.concurrent.atomic.AtomicLong;
import org.foxesworld.kalitech.engine.util.LongHashMap;

public final class SoundNodeRegistry {
    private static final String USERDATA_SOUND_ID = "kalitech.soundId";
    private final LongHashMap<AudioNode> nodes = new LongHashMap(256);
    private final AtomicLong nextId = new AtomicLong(1L);

    public long cache(AudioNode node) {
        long id = this.nextId.getAndIncrement();
        this.nodes.put(id, node);
        node.setUserData(USERDATA_SOUND_ID, id);
        return id;
    }

    public long getId(AudioNode node) {
        if (node == null) {
            return 0L;
        }
        Long id = (Long)node.getUserData(USERDATA_SOUND_ID);
        return id != null ? id : 0L;
    }

    public AudioNode getById(long id) {
        return (AudioNode)this.nodes.get(id);
    }

    public AudioNode remove(long id) {
        return (AudioNode)this.nodes.remove(id);
    }
}

