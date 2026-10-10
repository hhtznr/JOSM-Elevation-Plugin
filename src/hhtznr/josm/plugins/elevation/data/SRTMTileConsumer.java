package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.tools.Logging;

/**
 * This is the abstract superclass of classes using SRTM tiles to directly
 * obtain elevation data from them. The class implements methods to add and
 * remove instances of {@link ElevationDataConsumer}, which obtain elevation
 * data via a subclass instance of this class. This way, it can be decided, when
 * an subclass instance of this class can safely be disposed. This is the case
 * if not a single elevation data consumer is any longer registered.
 *
 * @author Harald Hetzner
 */
public abstract class SRTMTileConsumer {

    private final String name;
    protected final ElevationDataProvider elevationDataProvider;
    private List<SRTMTileCacheEntry> cacheEntries = null;
    private final CopyOnWriteArrayList<ElevationDataConsumer> elevationDataConsumers = new CopyOnWriteArrayList<>();

    private enum State {
        ACTIVE, DISPOSING, DISPOSED
    }

    private final Object lifecycleLock = new Object();
    private State state = State.ACTIVE;
    private int activeUsers;

    /**
     * Creates a new SRMT tile consumer.
     *
     * @param name                  The name of the consumer (useful for logging and
     *                              debugging).
     * @param elevationDataProvider The elevation data provider used to obtain SRTM
     *                              tile cache entries to access SRTM tiles.
     */
    public SRTMTileConsumer(String name, ElevationDataProvider elevationDataProvider) {
        this.name = name;
        this.elevationDataProvider = elevationDataProvider;
        cacheEntries = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Returns the name of this SRTM tile consumer (useful for logging and
     * debugging).
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Adds an SRTM tile cache entry to the list of SRTM tile cache entries used by
     * this SRTM tile consumer. The corresponding SRTM tile can be obtained from the
     * cache entry as soon as it has been read into memory or immediately if this
     * has already taken place.
     *
     * @param entry The SRTM tile cache entry to add.
     */
    protected synchronized void addCacheEntry(SRTMTileCacheEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (!cacheEntries.contains(entry)) {
                cacheEntries.add(entry);
            }
        }
    }

    /**
     * Returns the list of SRTM tile cache entries of this SRTM tile consumer.
     *
     * @return The list of SRTM tile cache entries, from which the corresponding
     *         SRTM tiles can be obtained.
     */
    public synchronized List<SRTMTileCacheEntry> getCacheEntryList() {
        return cacheEntries;
    }

    /**
     * Adds an elevation data consumer to the list of consumers using this SRTM tile
     * consumer.
     *
     * @param consumer The elevation data consumer to add.
     * @return {@code true} if the consumer was added, {@code false} if
     */
    public boolean addElevationDataConsumer(ElevationDataConsumer consumer) {
        synchronized (lifecycleLock) {
            if (state != State.ACTIVE) {
                return false;
            }
            return elevationDataConsumers.addIfAbsent(consumer);
        }
    }

    /**
     * Removes an elevation data consumer from the list of consumers using this SRTM
     * tile consumer. Disposes this SRTM tile consumer if no other elevation data
     * consumers are left.
     *
     * @param consumer The elevation data consumer to remove.
     * @return {@code true} if the list of consumers contained the specified
     *         consumer.
     */
    public boolean removeElevationDataConsumer(ElevationDataConsumer consumer) {
        boolean removed;
        boolean shouldDispose;

        synchronized (lifecycleLock) {
            removed = elevationDataConsumers.remove(consumer);
            shouldDispose = removed && beginDisposalIfUnused();
        }

        if (shouldDispose) {
            disposeResources();
        }

        return removed;
    }

    // Only call within synchronized block on lifecycleLock
    private boolean beginDisposalIfUnused() {
        if (state == State.ACTIVE && elevationDataConsumers.isEmpty() && activeUsers == 0) {
            state = State.DISPOSING;
            return true;
        }
        return false;
    }

    /**
     * Returns the number of elevation data consumers registered with this SRTM tile
     * consumer.
     *
     * @return The number of registered elevation data consumers.
     */
    public int getElevationDataConsumerCount() {
        return elevationDataConsumers.size();
    }

    /**
     * Blocks disposal of this {@code SRTMTileConsumer} until {@link #release()} is
     * called. Can be called multiple times, but {@link #release()} needs to be
     * called the same number of times.
     *
     * @return {@code true} if disposal could be blocked; {@code false} if this
     *         {@code SRTMTileConsumer} is already disposed.
     */
    public boolean acquire() {
        synchronized (lifecycleLock) {
            if (state != State.ACTIVE) {
                return false;
            }
            activeUsers++;
            return true;
        }
    }

    /**
     * Unblocks disposal of this {@code SRTMTileConsumer}. Needs to be called the
     * number of times that {@link #acquire()} has been called.
     */
    public void release() {
        boolean shouldDispose;
        synchronized (lifecycleLock) {
            if (activeUsers == 0) {
                throw new IllegalStateException("Release called more than acquire");
            }

            activeUsers--;
            shouldDispose = beginDisposalIfUnused();
        }

        if (shouldDispose) {
            disposeResources();
        }
    }

    /**
     * Returns whether this SRTM tile consumer is disposed. A disposed SRTM tile
     * consumer has released its resources and therefore can no longer be used.
     *
     * @return {@code true} if this SRTM tile consumer is currently dispoing or
     *         already disposed, {@code false} if it is still active and can be
     *         used.
     */
    public boolean isDisposed() {
        synchronized (lifecycleLock) {
            return state != State.ACTIVE;
        }
    }

    private void disposeResources() {
        try {
            elevationDataProvider.removeSRTMTileConsumer(this);
            onDispose();
            Logging.info("Elevation: Disposed " + name + " which is no longer needed.");
        } finally {
            synchronized (this) {
                cacheEntries = null;
            }
            synchronized (lifecycleLock) {
                state = State.DISPOSED;
            }
        }
    }

    /**
     * Executed inside {@link #disposeResources()}. To be overwritten by subclasses
     * for subclass-specific cleanup. Default behavior is to perform no
     * subclass-specific cleanup.
     */
    protected void onDispose() {
    }

    /**
     * Disposes this SRTM tile consumer, if all instances of
     *
     * If considerDispose() must remain because it is called from elsewhere, make it
     * use this same transition helper; it should not set a flag and then call a
     * second method that tries to set the flag again. ElevationDataProvider still
     * calls this method, but it should not be called directly by any other code. It
     * is called when all instances of {@link ElevationDataConsumer} have been
     * removed from it.
     */
    protected void considerDispose() {
        boolean shouldDispose;
        synchronized (lifecycleLock) {
            shouldDispose = beginDisposalIfUnused();
        }

        if (shouldDispose) {
            disposeResources();
        }
    }
}
