package hhtznr.josm.plugins.elevation.data;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * This class implements a cache entry for the {@link SRTMTileCache}.
 *
 * @author Harald Hetzner
 */
public class SRTMTileCacheEntry {

    private final String srtmTileID;
    private volatile Status status;

    private final CompletableFuture<SRTMTile> future;

    private long accessTime;

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
        DOWNLOAD_FAILED("download failed");

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
        this.status = Status.NEW;
        this.future = new CompletableFuture<>();
        this.accessTime = System.currentTimeMillis();
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
     * Returns a {@code Future} that will provide access to the SRTM tile of this
     * cache entry as soon as the tile is cached.
     *
     * @return {@code Future} to access the SRTM tile or wait for its availability.
     */
    public CompletableFuture<SRTMTile> getFuture() {
        synchronized (this) {
            this.accessTime = System.currentTimeMillis();
        }
        return future;
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
        SRTMTile tile = getTile();
        if (tile == null)
            return 0;
        return tile.getDataSize();
    }

    private SRTMTile getTile() {
        if (future.isDone()) {
            try {
                return future.get();
            } catch (CancellationException | InterruptedException | ExecutionException e) {
                return null;
            }
        }
        return null;
    }
}
