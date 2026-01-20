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

    private boolean isDisposed = false;

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
     */
    public void addElevationDataConsumer(ElevationDataConsumer consumer) {
        elevationDataConsumers.addIfAbsent(consumer);
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
        synchronized (elevationDataConsumers) {
            boolean removed = elevationDataConsumers.remove(consumer);
            if (removed)
                considerDispose();
            return removed;
        }
    }

    public int getElevationDataConsumerCount() {
        return elevationDataConsumers.size();
    }

    /**
     * Returns whether this SRTM tile consumer is disposed. A disposed SRTM tile
     * consumer has released its resources and therefore can no longer be used.
     *
     * @return {@code true} if this SRTM tile consumer has been disposed.
     */
    public synchronized boolean isDisposed() {
        return isDisposed;
    }

    /**
     * Disposes this SRTM tile consumer, if all instances of
     * {@link ElevationDataConsumer} have been removed from it.
     */
    protected void considerDispose() {
        synchronized (elevationDataConsumers) {
            if (elevationDataConsumers.isEmpty()) {
                dispose();
                Logging.info("Elevation: Disposed " + toString() + " which is no longer needed.");
            } else {
                String[] names = new String[elevationDataConsumers.size()];
                for (int i = 0; i < elevationDataConsumers.size(); i++)
                    names[i] = elevationDataConsumers.get(i).getName();
                String consumers = String.join(", ", names);
                Logging.info("Elevation: Not disposing " + toString() + ": " + elevationDataConsumers.size()
                        + " elevation data consumers left: " + consumers);
            }
        }
    }

    private void dispose() {
        if (isDisposed) {
            Logging.info(
                    "Elevation: Attempted to dispose already disposed elevation data consumer " + toString() + ".");
            return;
        }
        elevationDataProvider.removeSRTMTileConsumer(this);
        // Note: removeSRTMTileConsumer() still accesses the cacheEntries
        cacheEntries = null;
        isDisposed = true;
        Logging.info("Elevation: " + name + " disposed.");
    }
}
