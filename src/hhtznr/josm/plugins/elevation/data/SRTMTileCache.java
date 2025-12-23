package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;
import hhtznr.josm.plugins.elevation.util.CancelableExecutor;

/**
 * In-memory cache for SRTM tiles which can be limited in size. If the cache
 * size is limited, the last recently tiles will be removed if new tiles are
 * added exceeding the cache size.
 *
 * @author Harald Hetzner
 */
public class SRTMTileCache {

    private final ConcurrentHashMap<String, SRTMTileCacheEntry> cache = new ConcurrentHashMap<>();

    private final Object previousTileLock = new Object();
    private SRTMTile previousTile = null;

    private SRTMTile.Type srtmType;

    /**
     * Current size of the cache in bytes.
     */
    private int cacheSize = 0;

    /**
     * Maximum size of the cache in bytes.
     */
    private int cacheSizeLimit;

    private final LinkedList<SRTMTileCacheListener> listeners = new LinkedList<>();

    private final SRTMFileReader srtmFileReader;
    private static final CancelableExecutor<SRTMTile> fileReadExecutor = new CancelableExecutor<>(
            Executors.newSingleThreadExecutor());

    private boolean autoDownloadEnabled = false;
    private SRTMFileDownloader srtmFileDownloader = null;
    private static final CancelableExecutor<Optional<File>> fileDownloadExecutor = new CancelableExecutor<>(
            Executors.newFixedThreadPool(2));

    private final Object elevationDataSourcesLock = new Object();
    private List<ElevationDataSource> elevationDataSources;

    /**
     * Creates a new cache for SRTM tiles.
     *
     * @param srtmType       The type of SRTM tiles allowed to be cached.
     * @param cacheSizeLimit The maximum size of the cache in MiB.
     */
    /**
     * Creates a new cache for SRTM tiles.
     *
     * @param elevationDataSources The list of sources from which elevation data can
     *                             be obtained. The list must contain at least one
     *                             source. All sources must have the same type.
     * @param cacheSizeLimit       The maximum size of the cache in MiB.
     */
    public SRTMTileCache(List<ElevationDataSource> elevationDataSources, int cacheSizeLimit) {
        if (elevationDataSources == null)
            throw new NullPointerException("The list of elevation data sources cannot be null");

        if (elevationDataSources.isEmpty())
            throw new IllegalArgumentException("The list of elevation data sources cannot be empty.");

        srtmType = elevationDataSources.get(0).getSRTMTileType();
        if (elevationDataSources.size() > 1) {
            for (int i = 1; i < elevationDataSources.size(); i++) {
                SRTMTile.Type type = elevationDataSources.get(i).getSRTMTileType();
                elevationDataSources.get(i).getSRTMTileType();
                if (srtmType != type)
                    throw new IllegalArgumentException(
                            "The list of elevation data sources cannot contain diferent types: " + srtmType.toString()
                                    + " and " + type.toString());
            }
        }
        this.elevationDataSources = elevationDataSources;

        setCacheSizeLimit(cacheSizeLimit);
        srtmFileReader = new SRTMFileReader();
    }

    /**
     * Returns the list of elevation data sources.
     *
     * @return The list of elevation data sources.
     */
    public List<ElevationDataSource> getElevationDataSources() {
        return elevationDataSources;
    }

    /**
     * Returns the type of SRTM data of this SRTM file provider.
     *
     * @return The SRTM type.
     */
    public SRTMTile.Type getSRTMType() {
        return srtmType;
    }

    /**
     * Sets the SRTM type by setting the elevation data sources accordingly. If the
     * type not equal to the current type, flushes the tile grid cache and sets the
     * current SRTM tile grid to {@code null} to enforce that it will be regenerated
     * upon the next elevation query or elevation layer paint operation.
     *
     * @param type The SRTM type to set.
     * @return {@code true} if the type was changed, {@code false} the type was
     *         already set.
     */
    public boolean setSRTMType(SRTMTile.Type type) {
        if (type == srtmType)
            return false;

        // Set the elevation data sources according to the SRTM type
        synchronized (elevationDataSourcesLock) {
            if (type == SRTMTile.Type.SRTM1)
                elevationDataSources = ElevationPreferences.ELEVATION_DATA_SOURCES_SRTM1;
            else
                elevationDataSources = ElevationPreferences.ELEVATION_DATA_SOURCES_SRTM3;
            srtmType = type;
            flush();
            return true;
        }
    }

    /**
     * Returns an info string on current cache utilization.
     *
     * @return An info string on current cache utilization: Number of cached tiles,
     *         cache size and maximum cache size.
     */
    public synchronized String getInfo() {
        String info = "";
        int numberOfTiles = cache.size();
        info += numberOfTiles + (numberOfTiles == 1 ? " tile, " : " tiles, ");
        info += getSizeString(cacheSize) + " of max. ";
        info += getSizeString(cacheSizeLimit);
        return info;
    }

    /**
     * Returns a map that provides information on the currently cached SRTM tiles.
     * The tiles are ordered by their name.
     *
     * @return A map providing information on the currently cached SRTM tiles: Tile
     *         IDs, type, status, size and source.
     */
    public synchronized Map<String, String> getCachedTilesInfo() {
        Map<String, String> tileInfo = new TreeMap<>();
        for (Entry<String, SRTMTileCacheEntry> entry : cache.entrySet()) {
            SRTMTileCacheEntry cacheEntry = entry.getValue();
            CompletableFuture<SRTMTile> future = cacheEntry.getFuture();
            if (future.isDone()) {
                SRTMTile tile;
                try {
                    tile = future.get();
                } catch (InterruptedException e) {
                    break;
                } catch (ExecutionException e) {
                    // Should never happen because we do not complete exceptionally
                    continue;
                }
                String info = "Type: " + tile.getType().toString();
                info += ", status: " + cacheEntry.getStatus().toString();
                info += ", size: " + getSizeString(tile.getDataSize());
                info += ", source: " + (tile.getDataSource() == null ? "none" : tile.getDataSource().getName());
                tileInfo.put("SRTM tile " + tile.getID(), info);
            }
        }
        return tileInfo;
    }

    /**
     * Sets the maximum cache size to a new value and cleans the cache if required
     * to adopt to the new size limit.
     *
     * @param limit The maximum size of the cache in MiB.
     */
    public synchronized void setCacheSizeLimit(int limit) {
        if (limit == cacheSizeLimit)
            return;
        if (limit < ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT)
            limit = ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT;
        else if (limit > ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT)
            limit = ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT;
        cacheSizeLimit = limit * 1024 * 1024;
        Logging.info("Elevation: Maximum size of the SRTM tile cache set to " + getSizeString(cacheSizeLimit));
        if (cacheSize > cacheSizeLimit)
            clean();
    }

    /**
     * Returns the SRTM tile with the respective ID as soon as it has been cached.
     * Caching can involve downloading. Blocks the executing thread for as long as
     * it takes and as long as loading is not interrupted.
     *
     * @param srtmTileID The ID of the SRTM tile to retrieve.
     * @return The SRTM tile as a valid tile with data if the tile exists and the
     *         data is valid or otherwise as an invalid tile without data.
     * @throws CancellationException if the future used for asynchronous loading was
     *                               cancelled
     * @throws ExecutionException    should never be thrown; would be thrown if the
     *                               future used for asynchronous loading completed
     *                               exceptionally
     * @throws InterruptedException  if the current thread was interrupted while
     *                               waiting
     */
    public SRTMTile getTileOrWait(String srtmTileID)
            throws CancellationException, ExecutionException, InterruptedException {
        // Map editing is often done in the very same place for a while.
        // Therefore consider the previous tile first of all.
        synchronized (previousTileLock) {
            if (previousTile != null && previousTile.getID().equals(srtmTileID))
                return previousTile;
        }
        // Otherwise wait for the tile to be available with data
        SRTMTile tile = getTileFuture(srtmTileID).get();
        synchronized (previousTileLock) {
            previousTile = tile;
        }
        return tile;
    }

    /**
     * Returns the SRTM tile with the respective ID or being empty if the tile is
     * not cached (or cannot be cached, e.g. because it does not exist).
     *
     * @param srtmTileID The ID of the SRTM tile to retrieve.
     * @return An {@code Optional} holding the SRTM tile or being empty.
     */
    public Optional<SRTMTile> getTileIfPresent(String srtmTileID) {
        // Map editing is often done in the very same place for a while.
        // Therefore consider the previous tile first of all.
        synchronized (previousTileLock) {
            if (previousTile != null && previousTile.getID().equals(srtmTileID))
                return Optional.of(previousTile);
        }

        CompletableFuture<SRTMTile> future = getTileFuture(srtmTileID);
        if (future.isDone()) {
            try {
                SRTMTile tile = future.get();
                synchronized (previousTileLock) {
                    previousTile = tile;
                }
                return Optional.of(tile);
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Returns a {@code Future} from which an SRTM tile specified by the provided ID
     * can be obtained. Starts asynchronous loading of the SRTM tile of the tile is
     * not cached yet.
     *
     * @param srtmTileID The ID of the SRTM tile for which to retrieve the future.
     * @return A {@code Future} from which the SRTM tile can be obtained as soon as
     *         it is loaded.
     */
    public CompletableFuture<SRTMTile> getTileFuture(String srtmTileID) {
        SRTMTileCacheEntry entry = cache.computeIfAbsent(srtmTileID, id -> {
            SRTMTileCacheEntry newEntry = new SRTMTileCacheEntry(id);
            startLoadingAsync(newEntry);
            return newEntry;
        });

        CompletableFuture<SRTMTile> future = entry.getFuture();
        return future;
    }

    private void startLoadingAsync(SRTMTileCacheEntry entry) {
        CompletableFuture.runAsync(() -> {
            String srtmTileID = entry.getID();
            try {
                List<ElevationDataSource> dataSources;
                synchronized (elevationDataSourcesLock) {
                    dataSources = elevationDataSources;
                }

                SRTMTile srtmTile = null;
                SRTMTileCacheEntry.Status status = null;
                for (ElevationDataSource dataSource : dataSources) {
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Loading of SRTM tile " + entry.getID() + " was interrupted.");
                    if (dataSource.isSRTMTilePermanentlyMissing(srtmTileID)) {
                        Logging.info("Elevation: SRTM tile with ID " + srtmTileID
                                + " is permanently missing for source " + dataSource.toString());
                        continue;
                    }
                    File srtmFile = getLocalSRTMFile(srtmTileID, dataSource);
                    // If an SRTM file with the data exists on disk, read it in
                    if (srtmFile != null) {
                        Logging.info("Elevation: Caching data of SRTM tile " + srtmTileID + " from file "
                                + srtmFile.getAbsolutePath());
                        // Read the SRTM file as task in a separate thread
                        entry.setStatus(SRTMTileCacheEntry.Status.READING_SCHEDULED);
                        try {
                            srtmTile = readSRTMFile(entry, srtmFile, dataSource);
                            break;
                        } catch (ExecutionException e) {
                            if (e.getCause() instanceof IOException)
                                status = SRTMTileCacheEntry.Status.FILE_INVALID;
                        }
                    }
                    // If auto-downloading of SRTM files is enabled, try to download the missing
                    // file
                    else if (autoDownloadEnabled && dataSource.canAutoDownload()) {
                        entry.setStatus(SRTMTileCacheEntry.Status.DOWNLOAD_SCHEDULED);
                        Optional<File> optionalFile = downloadSRTMFile(entry, dataSource);
                        if (optionalFile.isPresent()) {
                            srtmFile = optionalFile.get();
                            srtmTile = readSRTMFile(entry, srtmFile, dataSource);
                        } else {
                            status = SRTMTileCacheEntry.Status.DOWNLOAD_FAILED;
                        }
                    }
                }

                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Loading of SRTM tile " + entry.getID() + " was interrupted.");

                if (status == null)
                    status = SRTMTileCacheEntry.Status.FILE_MISSING;

                if (srtmTile == null) {
                    srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                } else if (!srtmTile.isValid()) {
                    status = SRTMTileCacheEntry.Status.DATA_INVALID;
                } else {
                    status = SRTMTileCacheEntry.Status.VALID;
                    synchronized (previousTileLock) {
                        if (previousTile == null)
                            previousTile = srtmTile;
                    }
                }
                entry.setStatus(status);
                entry.getFuture().complete(srtmTile); // Wakes up all waiters
                synchronized (listeners) {
                    for (SRTMTileCacheListener listener : listeners)
                        listener.srtmTileCached(srtmTile, status);
                }
            } catch (Exception e) {
                SRTMTileCacheEntry.Status status = SRTMTileCacheEntry.Status.DATA_INVALID;
                entry.setStatus(status);
                // We don't do this; therefore ExecutionException should be thrown in practice
                // entry.getFuture().completeExceptionally(e);
                SRTMTile srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                entry.getFuture().complete(srtmTile);
                Logging.error("Elevation: Exception retrieving SRTM tile " + srtmTileID + ": " + e.toString());
                synchronized (listeners) {
                    for (SRTMTileCacheListener listener : listeners)
                        listener.srtmTileCached(srtmTile, status);
                }
            }
        });
    }

    /**
     * Determines the SRTM file for the given tile ID from the data directory of the
     * data source.
     *
     * @param srtmTileID          The SRTM tile ID.
     * @param elevationDataSource The elevation data source where to try to read a
     *                            matching SRTM file from the local data directory.
     * @return The SRTM file or {@code null} if no file is available for the given
     *         tile ID.
     */
    private File getLocalSRTMFile(String srtmTileID, ElevationDataSource elevationDataSource) {
        Logging.info("Elevation: Looking for on-disk SRTM file for tile ID " + srtmTileID);
        File srtmFile = null;
        // List the SRTM directory and filter out files that start with the SRTM tile ID
        // https://www.baeldung.com/java-list-directory-files
        Set<File> files = Stream.of(elevationDataSource.getDataDirectory().listFiles())
                .filter(file -> !file.isDirectory() && file.getName().startsWith(srtmTileID))
                .collect(Collectors.toSet());

        if (files.size() > 0) {
            srtmFile = files.iterator().next();
        }
        return srtmFile;
    }

    /**
     * Flushes the cache by removing all tiles and sets the cache size to zero.
     */
    public synchronized void flush() {
        synchronized (cache) {
            // Cancel all currently running/queued file download tasks
            fileDownloadExecutor.cancelAllTasks();
            // Cancel all currently running/queued file read tasks
            fileReadExecutor.cancelAllTasks();
            cache.clear();
            cacheSize = 0;
            synchronized (previousTileLock) {
                previousTile = null;
            }
        }
        Logging.info("Elevation: SRTM tile cache flushed");
    }

    /**
     * Removes least recently used SRTM tiles actually holding elevation data from
     * the cache ensuring that the cache size limit is not exceeded.
     */
    private synchronized void clean() {
        ArrayList<SRTMTileCacheEntry> allEntries = new ArrayList<>(cache.values());
        allEntries.sort(Comparator.comparingLong(SRTMTileCacheEntry::getAccessTime));
        for (SRTMTileCacheEntry entry : allEntries) {
            if (cacheSize <= cacheSizeLimit)
                break;
            if (entry != null) {
                CompletableFuture<SRTMTile> future = entry.getFuture();
                if (!future.isDone())
                    future.cancel(true);
                String srtmTileID = entry.getID();
                cache.remove(srtmTileID);
                int dataSize = entry.getDataSize();
                cacheSize -= dataSize;
                Logging.info("Elevation: Removed SRTM tile " + srtmTileID + " with status '"
                        + entry.getStatus().toString() + "' and size " + getSizeString(dataSize)
                        + " from cache; cache size: " + getSizeString(cacheSize));
            }
        }
    }

    /**
     * Cleans all SRTM tile cache entries with the given
     * {@link SRTMTileCacheEntry.Status} from the cache.
     *
     * @param status The status of the SRTM tile cache entry to clean from the
     *               cache.
     */
    public synchronized void cleanAllTilesWithStatus(SRTMTileCacheEntry.Status status) {
        cache.entrySet().removeIf(entry -> entry.getValue().getStatus() == status);
    }

    private static String getSizeString(int size) {
        return "" + size + " bytes (" + size / 1024 / 1024 + " MiB)";
    }

    /**
     * Adds an SRTM tile cache listener to this SRTM tile cache. All listeners are
     * informed if elevation data for a particular SRTM tile is available.
     *
     * @param listener The listener to add.
     */
    public void addSRTMTileCacheListener(SRTMTileCacheListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes a listener from this SRTM tile cache.
     *
     * @param listener The listener to be removed.
     */
    public void removeSRTMTileCacheListener(SRTMTileCacheListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private SRTMTile readSRTMFile(SRTMTileCacheEntry entry, File srtmFile, ElevationDataSource dataSource)
            throws InterruptedException, ExecutionException {
        Callable<SRTMTile> fileReadTask = () -> {
            entry.setStatus(SRTMTileCacheEntry.Status.READING);
            return srtmFileReader.readSRTMFile(srtmFile, dataSource);
        };
        return fileReadExecutor.submit(fileReadTask).get();
    }

    private Optional<File> downloadSRTMFile(SRTMTileCacheEntry entry, ElevationDataSource elevationDataSource)
            throws InterruptedException, ExecutionException {
        Callable<Optional<File>> downloadTask = () -> {
            entry.setStatus(SRTMTileCacheEntry.Status.DOWNLOADING);
            String srtmTileID = entry.getID();
            return srtmFileDownloader.download(srtmTileID, elevationDataSource);
        };
        return fileDownloadExecutor.submit(downloadTask).get();
    }

    /**
     * Returns whether automatic downloading of missing SRTM files is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public boolean isAutoDownloadEnabled() {
        return autoDownloadEnabled;
    }

    /**
     * Enables or disables automatic downloading of missing SRTM tiles.
     *
     * @param enabled {@code true} enables auto-download, {@code false} disables it.
     */
    public void setAutoDownloadEnabled(boolean enabled) {
        if (enabled)
            // If auto-downloading was (re-)enabled, clean all SRTM tiles which previously
            // failed downloading
            cleanAllTilesWithStatus(SRTMTileCacheEntry.Status.DOWNLOAD_FAILED);
        // Nothing else to do if the enabled status is unchanged
        if (autoDownloadEnabled == enabled)
            return;
        if (enabled) {
            if (srtmFileDownloader == null) {
                // Create an SRTM file downloader instance
                srtmFileDownloader = new SRTMFileDownloader();
                // Clear any SRTM tiles marked as missing from the cache so they will be
                // downloaded now, if needed
                cleanAllTilesWithStatus(SRTMTileCacheEntry.Status.FILE_MISSING);
            }
            autoDownloadEnabled = true;
            Logging.info("Elevation: Enabled auto-downloading of SRTM files");
        }
    }

    /**
     * Returns the SRTM file downloader used for auto-download of missing SRTM
     * tiles.
     *
     * @return The SRTM file downloader or {@code null} if auto-download is not
     *         enabled.
     */
    public SRTMFileDownloader getSRTMFileDownloader() {
        return srtmFileDownloader;
    }

    /**
     * Removes a listener from this elevation data provider.
     *
     * @param listener The listener to be removed.
     */
    public void removeElevationDataProviderListener(SRTMTileCacheListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
}
