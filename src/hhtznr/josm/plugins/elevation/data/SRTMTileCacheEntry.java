package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.io.InvalidSRTMDataException;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;

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

    private final CompletableFuture<SRTMTile> tileFuture = new CompletableFuture<>();
    private CompletableFuture<Void> asyncLoadFuture = null;
    private final AtomicReference<Future<?>> fileFetchFuture = new AtomicReference<>();

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
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public void waitUntilLoadingCompleted() throws AsyncOperationException {
        try {
            // May throw CancellationException, ExecutionException or InterruptedException
            tileFuture.get();
        } catch (CancellationException | ExecutionException | InterruptedException e) {
            Logging.info("Loading of SRTM tile " + srtmTileID + " canceled while waiting for it.");
            throw new AsyncOperationException(e);
        }
    }

    /**
     * Waits until loading of the SRTM tile completed normally or exceptionally by
     * calling {@code get()} on the internal {@code CompletableFuture} of this cache
     * entry and then returns the SRTM tile of this cache entry. Returns immediately
     * if loading is already completed.
     *
     * @return The SRTM tile of this cache entry.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public SRTMTile getTileOrWait() throws AsyncOperationException {
        synchronized (this) {
            this.accessTime = System.currentTimeMillis();
        }
        try {
            // May throw CancellationException, ExecutionException or InterruptedException
            return tileFuture.get();
        } catch (CancellationException | ExecutionException | InterruptedException e) {
            Logging.info("Loading of SRTM tile " + srtmTileID + " canceled while waiting for it in order to get it.");
            throw new AsyncOperationException(e);
        }
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
        return tileFuture.isDone();
    }

    /**
     * Returns {@code true} if loading of the SRTM tile was completed with an
     * exception.
     *
     * @return {@code true} if loading of the SRTM tile was completed exceptionally.
     */
    public boolean isCompletedExceptionally() {
        return tileFuture.isCompletedExceptionally();
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
    private synchronized boolean complete(SRTMTile tile) {
        return tileFuture.complete(tile);
    }

    private synchronized boolean completeExceptionally(Throwable ex) {
        return tileFuture.completeExceptionally(ex);
    }

    /**
     * Cancels loading of the SRTM tile by canceling its internal internal
     * {@code CompletableFuture}.
     *
     * @return {@code true} if the task of the internal {@code CompletableFuture} is
     *         now canceled.
     */
    protected synchronized boolean cancelLoading() {
        boolean canceled = tileFuture.cancel(true);

        CompletableFuture<?> loadFuture = asyncLoadFuture;
        if (loadFuture != null) {
            loadFuture.cancel(true);
            asyncLoadFuture = null;
        }

        Future<?> ioFuture = fileFetchFuture.get();
        if (ioFuture != null) {
            ioFuture.cancel(true);
            fileFetchFuture.set(null);
        }

        // Note: For CompletableFuture, the boolean mayInterruptIfRunning has no effect
        return canceled;
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
        if (tileFuture.isDone()) {
            try {
                SRTMTile tile = tileFuture.get();
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

    protected void loadAsync(SRTMTileCache cache) {
        asyncLoadFuture = CompletableFuture.runAsync(() -> {
            List<ElevationDataSource> dataSources = cache.getElevationDataSources();
            if (dataSources.isEmpty())
                return;
            SRTMTile.Type srtmType = dataSources.get(0).getSRTMTileType();
            try {
                SRTMTile srtmTile = null;
                for (ElevationDataSource dataSource : dataSources) {
                    if (tileFuture.isCancelled() || tileFuture.isCompletedExceptionally())
                        throw new CancellationException("Loading of SRTM tile " + srtmTileID + " was canceled.");
                    if (dataSource.isSRTMTilePermanentlyMissing(srtmTileID)) {
                        Logging.info("Elevation: SRTM tile with ID " + srtmTileID
                                + " is permanently missing for source " + dataSource.toString());
                        continue;
                    }
                    File srtmFile = null;
                    try {
                        srtmFile = dataSource.getLocalSRTMFile(srtmTileID);
                    } catch (FileNotFoundException e) {
                        status = SRTMTileCacheEntry.Status.FILE_MISSING;
                    }
                    // If an SRTM file with the data exists on disk, read it in
                    if (srtmFile != null) {
                        Logging.info("Elevation: Caching data of SRTM tile " + srtmTileID + " from file "
                                + srtmFile.getAbsolutePath());
                        // Read the SRTM file as task in a separate thread
                        status = SRTMTileCacheEntry.Status.READING_SCHEDULED;
                        try {
                            Future<SRTMTile> readFuture = readSRTMFile(srtmFile, dataSource, cache.getSRTMFileReader());
                            fileFetchFuture.set(readFuture);
                            srtmTile = readFuture.get();
                            fileFetchFuture.set(null);
                            if (tileFuture.isCancelled() || tileFuture.isCompletedExceptionally())
                                throw new CancellationException(
                                        "Loading of SRTM tile " + srtmTileID + " was canceled.");
                            break;
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof IOException)
                                status = SRTMTileCacheEntry.Status.FILE_INVALID;
                            if (e.getCause() instanceof InvalidSRTMDataException)
                                status = SRTMTileCacheEntry.Status.DATA_INVALID;
                        }
                    }
                    // If auto-downloading of SRTM files is enabled, try to download the missing
                    // file
                    else if (cache.isAutoDownloadEnabled() && dataSource.canAutoDownload()) {
                        SRTMFileDownloader downloader = cache.getSRTMFileDownloader();
                        if (downloader == null)
                            continue;
                        status = SRTMTileCacheEntry.Status.DOWNLOAD_SCHEDULED;
                        Future<Optional<File>> downloadFuture = downloadSRTMFile(dataSource, downloader);
                        fileFetchFuture.set(downloadFuture);
                        Optional<File> optionalFile = downloadFuture.get();
                        fileFetchFuture.set(null);
                        if (tileFuture.isCancelled() || tileFuture.isCompletedExceptionally())
                            throw new CancellationException("Loading of SRTM tile " + srtmTileID + " was canceled.");
                        if (optionalFile.isPresent()) {
                            srtmFile = optionalFile.get();
                            try {
                                Future<SRTMTile> readFuture = readSRTMFile(srtmFile, dataSource,
                                        cache.getSRTMFileReader());
                                fileFetchFuture.set(readFuture);
                                srtmTile = readFuture.get();
                                fileFetchFuture.set(null);
                                if (tileFuture.isCancelled() || tileFuture.isCompletedExceptionally())
                                    throw new CancellationException(
                                            "Loading of SRTM tile " + srtmTileID + " was canceled.");
                                break;
                            } catch (ExecutionException e) {
                                if (e.getCause() instanceof IOException)
                                    status = SRTMTileCacheEntry.Status.FILE_INVALID;
                                if (e.getCause() instanceof InvalidSRTMDataException)
                                    status = SRTMTileCacheEntry.Status.DATA_INVALID;
                            }
                        } else {
                            status = SRTMTileCacheEntry.Status.DOWNLOAD_FAILED;
                        }
                    }
                }

                if (tileFuture.isCancelled() || tileFuture.isCompletedExceptionally())
                    throw new CancellationException("Loading of SRTM tile " + srtmTileID + " was canceled.");

                if (status == null)
                    status = SRTMTileCacheEntry.Status.FILE_MISSING;

                if (srtmTile == null) {
                    srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                } else {
                    status = SRTMTileCacheEntry.Status.VALID;
                }
                complete(srtmTile); // Wakes up all waiters
                cache.srtmTileCached(srtmTile, status);
                Logging.info("Elevation: SRTM tile " + srtmTileID + " loaded with status " + status.toString());
            } catch (Exception e) {
                if (e instanceof CancellationException)
                    status = SRTMTileCacheEntry.Status.LOADING_CANCELED;
                else
                    status = SRTMTileCacheEntry.Status.DATA_INVALID;
                SRTMTile srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                completeExceptionally(e);
                if (!(e instanceof CancellationException)) {
                    Logging.error("Elevation: Exception retrieving SRTM tile " + srtmTileID + ": " + e.toString());
                    e.printStackTrace();
                }
                cache.srtmTileCached(srtmTile, status);
            }
            asyncLoadFuture = null;
        });
    }

    private Future<SRTMTile> readSRTMFile(File srtmFile, ElevationDataSource dataSource, SRTMFileReader srtmFileReader)
            throws InterruptedException, ExecutionException {
        Callable<SRTMTile> fileReadTask = () -> {
            status = SRTMTileCacheEntry.Status.READING;
            return srtmFileReader.readSRTMFile(srtmFile, dataSource);
        };
        return SRTMTileCache.fileReadExecutor.submit(fileReadTask);
    }

    private Future<Optional<File>> downloadSRTMFile(ElevationDataSource elevationDataSource,
            SRTMFileDownloader srtmFileDownloader) throws InterruptedException, ExecutionException {
        Callable<Optional<File>> downloadTask = () -> {
            status = SRTMTileCacheEntry.Status.DOWNLOADING;
            return srtmFileDownloader.download(srtmTileID, elevationDataSource);
        };
        return SRTMTileCache.fileDownloadExecutor.submit(downloadTask);
    }

    /**
     * Disposes this SRTM tile cache entry by canceling the asynchronous tile
     * loading operation if still running and disposing the SRTM tile if already
     * loaded. <br>
     * Disposing is intended to free memory.
     */
    protected synchronized void disposeTile() {
        if (!tileFuture.isDone())
            cancelLoading();
        Optional<SRTMTile> optional = getTileIfLoaded();
        if (optional.isPresent())
            optional.get().dispose();
    }
}
