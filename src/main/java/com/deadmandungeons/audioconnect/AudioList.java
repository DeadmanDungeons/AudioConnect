package com.deadmandungeons.audioconnect;

import com.google.common.collect.Sets;

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
    private final UpdateHandler updateHandler;

    AudioList(Logger logger, UpdateHandler updateHandler) {
        this.logger = logger;
        this.updateHandler = updateHandler;
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
            long currentTime = System.currentTimeMillis();
            Long lastWarnTime = invalidIds.putIfAbsent(audioId, currentTime);
            if (lastWarnTime == null || lastWarnTime < currentTime - WARNING_DELAY_MILLIS) {
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

    boolean addAll(Set<String> audioIds) {
        boolean updated = this.audioIds.addAll(audioIds);
        invalidIds.keySet().removeAll(audioIds);
        return updated;
    }

    boolean removeAll(Set<String> audioIds) {
        return this.audioIds.removeAll(audioIds);
    }

    boolean deleteAll(Set<String> audioIds) {
        boolean updated = removeAll(audioIds);
        updateHandler.deleteAll(audioIds);
        return updated;
    }

    void replace(String audioId, String newAudioId) {
        audioIds.remove(audioId);
        audioIds.add(newAudioId);
        invalidIds.remove(newAudioId);
        updateHandler.replace(audioId, newAudioId);
    }

    public interface UpdateHandler {

        void deleteAll(Set<String> audioIds);

        void replace(String audioId, String newAudioId);

    }

}
