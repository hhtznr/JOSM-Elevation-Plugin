package hhtznr.josm.plugins.elevation.data;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openstreetmap.josm.tools.Logging;

/**
 * This class implements a cache entry for the {@link SRTMTileCache}.
 *
 * @author Harald Hetzner
 */
public class SRTMTileCacheEntry {

    // TODO: Remove again
    private static final Cleaner CLEANER = Cleaner.create();

    private final String srtmTileID;
    private volatile Status status;

    private final CompletableFuture<SRTMTile> future = new CompletableFuture<>();

    private volatile long accessTime;

    private final ArrayList<SRTMTileConsumer> tileConsumers = new ArrayList<>();

    /**
     * Status of SRTM tiles (loading, valid, missing, download scheduled,
     * downloading, download failed).
     */
    public static enum Status {
        /**
         * Status indicating that the cache entry is new.
         */
        NEW("new"),

        /**
         * Status indicating that reading of the SRTM tile from disk has been scheduled.
         */
        READING_SCHEDULED("reading scheduled"),

        /**
         * Status indicating that the SRTM tile is currently being read from disk.
         */
        READING("reading"),

        /**
         * Status indicating that the SRTM tile holds valid elevation data.
         */
        VALID("valid"),

        /**
         * Status indicating that SRTM type and data do not match.
         */
        DATA_INVALID("data invalid"),

        /**
         * Status indicating that the SRTM file that should contain the elevation data
         * of the SRTM tile was considered for reading, but was found to be invalid.
         */
        FILE_INVALID("file invalid"),

        /**
         * Status indicating that the SRTM file that should contain the elevation data
         * of the SRTM tile is missing on disk and cannot be obtained (auto-download
         * disabled).
         */
        FILE_MISSING("file missing"),

        /**
         * Status indicating that downloading of the SRTM tile has been scheduled.
         */
        DOWNLOAD_SCHEDULED("download scheduled"),

        /**
         * Status indicating that the SRTM tile is currently being downloaded from the
         * SRTM server.
         */
        DOWNLOADING("downloading"),

        /**
         * Status indicating that downloading the SRTM tile failed.
         */
        DOWNLOAD_FAILED("download failed"),

        /**
         * Status indicating that loading of the SRTM tile was canceled.
         */
        LOADING_CANCELED("loading canceled");

        private final String statusName;

        Status(String statusName) {
            this.statusName = statusName;
        }

        /**
         * Returns the name associated with this SRTM status.
         *
         * @return The name of the status.
         */
        @Override
        public String toString() {
            return statusName;
        }
    }

    /**
     * Creates a new cache entry for an SRTM tile with the specified ID.
     *
     * @param srtmTileID The ID of the associated SRTM tile.
     */
    public SRTMTileCacheEntry(String srtmTileID) {
        this.srtmTileID = srtmTileID;
        status = Status.NEW;
        accessTime = System.currentTimeMillis();

        CLEANER.register(this,
                () -> Logging.info("Elevation: DEBUG: GC reclaimed SRTM tile cache entry: " + srtmTileID));
    }

    /**
     * Returns the ID of the SRTM tile associated with this cache entry.
     *
     * @return The ID of the SRTM tile.
     */
    public String getID() {
        return srtmTileID;
    }

    /**
     * Returns the status of this cache entry.
     *
     * @return The status.
     */
    public synchronized Status getStatus() {
        return status;
    }

    /**
     * Sets the status of this cache entry.
     *
     * @param status The status to set.
     */
    public synchronized void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Waits until loading of the SRTM tile completed normally or exceptionally by
     * calling {@code get()} on the internal {@code CompletableFuture} of this cache
     * entry. Returns immediately if loading is already completed.
     *
     * @throws ExecutionException   if the {@code CompletableFuture} was completed
     *                              exceptionally.
     * @throws InterruptedException if the current thread was interrupted while
     *                              waiting.
     */
    public void waitUntilLoadingCompleted() throws ExecutionException, InterruptedException {
        future.get();
    }

    /**
     * Waits until loading of the SRTM tile completed normally or exceptionally by
     * calling {@code get()} on the internal {@code CompletableFuture} of this cache
     * entry and then returns the SRTM tile of this cache entry. Returns immediately
     * if loading is already completed.
     *
     * @return The SRTM tile of this cache entry.
     * @throws ExecutionException   if the {@code CompletableFuture} was completed
     *                              exceptionally.
     * @throws InterruptedException if the current thread was interrupted while
     *                              waiting.
     */
    public SRTMTile getTileOrWait() throws ExecutionException, InterruptedException {
        synchronized (this) {
            this.accessTime = System.currentTimeMillis();
        }
        return future.get();
    }

    /**
     * Returns whether the SRTM tile held by this SRTM tile cache entry is loaded
     * into memory or alternatively was found not to be available (e.g. permanently
     * missing). In this state, the SRTM tile is immediately accessible. Otherwise,
     * it might be necessary to read the SRTM tile from disk or even download it.
     *
     * @return {@code true} if the SRTM tile is loaded into memory
     */
    public boolean isLoadingCompleted() {
        return future.isDone();
    }

    /**
     * Completes the internal {@code CompletableFuture} if this cache entry with an
     * SRTM tile. To be executed after asynchronous loading of the tile to make this
     * cache entry effectively usable.
     *
     * @param tile The SRTM tile to complete this cache entry.
     * @return {@code true} if the internal {@code CompletableFuture} could be
     *         completed.
     */
    protected synchronized boolean complete(SRTMTile tile) {
        return future.complete(tile);
    }

    /**
     * Cancels loading of the SRTM tile by canceling its internal internal
     * {@code CompletableFuture}.
     *
     * @return {@code true} if the task of the internal {@code CompletableFuture} is
     *         now canceled.
     */
    protected synchronized boolean cancelLoading() {
        // Note: For CompletableFuture, the boolean mayInterruptIfRunning has no effect
        return future.cancel(true);
    }

    /**
     * Returns the time of last attempted access to the data of this SRTM tile.
     *
     * @return The access time stamp.
     */
    public long getAccessTime() {
        return accessTime;
    }

    /**
     * Returns the data size of the associated SRTM tile.
     *
     * @return The data size of the associated SRTM tiles in bytes or {@code 0} if
     *         the tile is not available.
     */
    public synchronized int getDataSize() {
        Optional<SRTMTile> optional = getTileIfLoaded();
        if (optional.isEmpty())
            return 0;
        return optional.get().getDataSize();
    }

    public Optional<SRTMTile> getTileIfLoaded() {
        synchronized (this) {
            this.accessTime = System.currentTimeMillis();
        }
        if (future.isDone()) {
            try {
                SRTMTile tile = future.get();
                return Optional.of(tile);
            } catch (CancellationException | InterruptedException | ExecutionException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Registers a tile consumer of this tile.
     *
     * @param consumer The tile consumer.
     * @return {@code true} if the tile consumer is not already registered.
     */
    public boolean addTileConsumer(SRTMTileConsumer consumer) {
        synchronized (tileConsumers) {
            if (tileConsumers.contains(consumer))
                return false;
            tileConsumers.add(consumer);
            return true;
        }
    }

    /**
     * Unregisters a tile consumer of this tile.
     *
     * @param consumer The tile consumer.
     * @return {@code true} if the tile consumer was actually registered.
     */
    public boolean removeTileConsumer(SRTMTileConsumer consumer) {
        synchronized (tileConsumers) {
            return tileConsumers.remove(consumer);
        }
    }

    /**
     * Returns the current number of registered tile consumers.
     *
     * @return The number of tile consumers.
     */
    public int getTileConsumerCount() {
        synchronized (tileConsumers) {
            return tileConsumers.size();
        }
    }

    /**
     * Disposes this SRTM tile cache entry by canceling the asynchronous tile
     * loading operation if still running and disposing the SRTM tile if already
     * loaded.
     * <br>
     * Disposing is intended to free memory.
     */
    protected synchronized void disposeTile() {
        if (!future.isDone())
            future.cancel(true);
        Optional<SRTMTile> optional = getTileIfLoaded();
        if (optional.isPresent())
            optional.get().dispose();
    }
}
