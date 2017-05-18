package com.deadmandungeons.audioconnect;

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * A simple wrapper to a collection containing all available audio IDs for the configured account.<br>
 * The operations in this class will only be useful if the client is connected.
 * @author Jon
 */
public class AudioList {

    private static final int WARNING_DELAY_MILLIS = 1000 * 60 * 5;

    private final ConcurrentHashMap<String, Long> invalidIds = new ConcurrentHashMap<>();
    private final Set<String> audioIds = Sets.newConcurrentHashSet();

    private final Logger logger;

    AudioList(Logger logger) {
        this.logger = logger;
    }

    /**
     * This indicates whether or not an audio source exists with the given ID.<br>
     * <b>Note:</b> If the given audioId does not exist in the underlying collection,
     * a warning message is logged if not already.
     * @param audioId a unique audio ID to check against
     * @return <code>true</code> if this AudioList contains the given audioId, and <code>false</code> otherwise
     */
    public boolean contains(String audioId) {
        if (!audioIds.contains(audioId)) {
            long now = System.currentTimeMillis();
            Long lastWarnTime = invalidIds.get(audioId);
            if (lastWarnTime == null || lastWarnTime < now - WARNING_DELAY_MILLIS) {
                invalidIds.put(audioId, now);
                logger.warning("Invalid Identifier: ID '" + audioId + "' was referenced for an audio source that does not exist");
            }
            return false;
        }
        return true;
    }

    /**
     * @return an unmodifiable Set containing all of the known audio IDs for the configured account
     */
    public Set<String> getAudioIds() {
        return Collections.unmodifiableSet(audioIds);
    }

    /**
     * @return <code>true</code> if there are no known audio IDs, and <code>false</code> otherwise
     */
    public boolean isEmpty() {
        return audioIds.isEmpty();
    }

    boolean addAll(Collection<String> audioIds) {
        return this.audioIds.addAll(audioIds);
    }

    boolean removeAll(Collection<String> audioIds) {
        return this.audioIds.removeAll(audioIds);
    }

}
