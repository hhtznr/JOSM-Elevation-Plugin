package hhtznr.josm.plugins.elevation.data.srtm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.DummyElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;

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
public abstract class SrtmTileConsumer {

    private final String name;
    protected final ElevationDataProvider elevationDataProvider;
    private List<SrtmTileCacheEntry> cacheEntries = null;
    private final CopyOnWriteArrayList<ElevationDataConsumer> elevationDataConsumers = new CopyOnWriteArrayList<>();

    private enum State {
        ACTIVE, DISPOSING, DISPOSED
    }

    private final Object lifecycleLock = new Object();
    private State state = State.ACTIVE;

    private DummyElevationDataConsumer dummyConsumer = null;

    /**
     * Creates a new SRMT tile consumer.
     *
     * @param name                  The name of the consumer (useful for logging and
     *                              debugging).
     * @param elevationDataProvider The elevation data provider used to obtain SRTM
     *                              tile cache entries to access SRTM tiles.
     */
    public SrtmTileConsumer(String name, ElevationDataProvider elevationDataProvider) {
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
    protected synchronized void addCacheEntry(SrtmTileCacheEntry entry) {
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
    public synchronized List<SrtmTileCacheEntry> getCacheEntryList() {
        return cacheEntries;
    }

    /**
     * Adds an elevation data consumer to the list of consumers using this SRTM tile
     * consumer. <br>
     * If the provided consumer is an instance of
     * {@link DummyElevationDataConsumer}, it will be registered as such if no dummy
     * consumer was registered previously. A dummy elevation data consumer that will
     * prevent disposal of this SRTM tile consumer prior to the first real elevation
     * data consumer having been added. The dummy consumer will be removed when the
     * first real elevation data consumer is added.
     *
     * @param consumer The elevation data consumer to add.
     * @return {@code true} if the consumer was added, {@code false} otherwise. In
     *         the special case of a {@link DummyElevationDataConsumer} being added,
     *         {@code true} is also returned if the grid is already protected by
     *         another dummy consumer.
     */
    public boolean addElevationDataConsumer(ElevationDataConsumer consumer) {
        synchronized (lifecycleLock) {
            if (state != State.ACTIVE) {
                return false;
            }
            if (consumer instanceof DummyElevationDataConsumer) {
                // The tile consumer is already protected from disposal
                if (dummyConsumer != null) {
                    return true;
                }
                if (elevationDataConsumers.addIfAbsent(consumer)) {
                    dummyConsumer = (DummyElevationDataConsumer) consumer;
                    return true;
                }
                return false;
            }

            boolean added = elevationDataConsumers.addIfAbsent(consumer);
            if (added && dummyConsumer != null) {
                if (elevationDataConsumers.remove(dummyConsumer)) {
                    dummyConsumer = null;
                }
            }
            return added;
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
        if (state == State.ACTIVE && elevationDataConsumers.isEmpty()) {
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
     * Unblocks disposal of this {@code SRTMTileConsumer} by removing the
     * {@link DummyElevationDataConsumer}, which protects the grid from disposal if
     * not other consumer is registered.
     */
    public void release() {
        boolean shouldDispose;
        synchronized (lifecycleLock) {
            if (dummyConsumer != null) {
                elevationDataConsumers.remove(dummyConsumer);
                dummyConsumer = null;
            }

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
    public void considerDispose() {
        boolean shouldDispose;
        synchronized (lifecycleLock) {
            shouldDispose = beginDisposalIfUnused();
        }

        if (shouldDispose) {
            disposeResources();
        }
    }
}
