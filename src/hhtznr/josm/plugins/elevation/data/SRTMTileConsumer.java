package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final AtomicInteger activeUsers = new AtomicInteger(0);

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
        if (entry == null)
            return;
        synchronized (entry) {
            if (!cacheEntries.contains(entry))
                cacheEntries.add(entry);
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
        if (isDisposed.get())
            return false;

        elevationDataConsumers.addIfAbsent(consumer);

        // Double-check in case disposal happened concurrently
        if (isDisposed.get()) {
            elevationDataConsumers.remove(consumer);
            return false;
        }

        return true;
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
        boolean removed = elevationDataConsumers.remove(consumer);
        if (removed)
            considerDispose();
        return removed;
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
        if (isDisposed.get())
            return false;

        activeUsers.incrementAndGet();

        // Double-check to avoid race with dispose
        if (isDisposed.get()) {
            release();
            return false;
        }

        return true;
    }

    /**
     * Unblocks disposal of this {@code SRTMTileConsumer}. Needs to be called the
     * number of times that {@link #acquire()} has been called.
     */
    public void release() {
        int remaining = activeUsers.decrementAndGet();

        if (remaining < 0)
            throw new IllegalStateException("Release called more than acquire");

        considerDispose();
    }

    /**
     * Returns whether this SRTM tile consumer is disposed. A disposed SRTM tile
     * consumer has released its resources and therefore can no longer be used.
     *
     * @return {@code true} if this SRTM tile consumer has been disposed.
     */
    public boolean isDisposed() {
        return isDisposed.get();
    }

    /**
     * Disposes this SRTM tile consumer, if all instances of
     * {@link ElevationDataConsumer} have been removed from it.
     */
    protected void considerDispose() {
        if (elevationDataConsumers.isEmpty() && activeUsers.get() == 0) {
            if (isDisposed.compareAndSet(false, true)) {
                dispose();
                Logging.info("Elevation: Disposed " + toString() + " which is no longer needed.");
            }
        } else {
            String[] names = new String[elevationDataConsumers.size()];
            for (int i = 0; i < elevationDataConsumers.size(); i++)
                names[i] = elevationDataConsumers.get(i).getName();
            String consumers = String.join(", ", names);
            Logging.info("Elevation: Not disposing " + toString() + ": " + elevationDataConsumers.size()
                    + " elevation data consumers left: " + consumers);
        }
    }

    private void dispose() {
        // Ensure only one thread performs disposal
        if (!isDisposed.compareAndSet(false, true)) {
            Logging.info(
                    "Elevation: Attempted to dispose already disposed elevation data consumer " + toString() + ".");
            return;
        }
        elevationDataProvider.removeSRTMTileConsumer(this);
        // Note: removeSRTMTileConsumer() still accesses the cacheEntries
        cacheEntries = null;

        Logging.info("Elevation: " + name + " disposed.");
    }
}
