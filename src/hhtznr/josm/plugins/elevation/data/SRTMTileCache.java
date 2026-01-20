package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.io.InvalidSRTMDataException;
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

    private final CopyOnWriteArrayList<SRTMTileCacheListener> listeners = new CopyOnWriteArrayList<>();

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
        synchronized (cache) {
            for (Entry<String, SRTMTileCacheEntry> entry : cache.entrySet()) {
                SRTMTileCacheEntry cacheEntry = entry.getValue();
                if (cacheEntry.isLoadingCompleted()) {
                    SRTMTile tile;
                    try {
                        tile = cacheEntry.getTileOrWait();
                    } catch (InterruptedException e) {
                        break;
                    } catch (ExecutionException e) {
                        // Should never happen because we do not complete exceptionally
                        continue;
                    }
                    synchronized (cacheEntry) {
                        String info = "Type: " + tile.getType().toString();
                        info += ", status: " + cacheEntry.getStatus().toString();
                        info += ", size: " + getSizeString(tile.getDataSize());
                        info += ", source: " + (tile.getDataSource() == null ? "none" : tile.getDataSource().getName());
                        tileInfo.put("SRTM tile " + tile.getID(), info);
                    }
                }
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
     * Initiates caching of the SRTM tiles needed to cover the specified bounds with
     * elevation data.
     *
     * @param intLatSouth The southernmost coordinate of the bounds, as full arc
     *                    degrees aligning with SRTM tile bounds.
     * @param intLonWest  The westernmost coordinate of the bounds, as full arc
     *                    degrees aligning with SRTM tile bounds.
     * @param intLatNorth The northernmost coordinate of the bounds, as full arc
     *                    degrees aligning with SRTM tile bounds.
     * @param intLonEast  The easternmost coordinate of the bounds, as full arc
     *                    degrees aligning with SRTM tile bounds.
     * @param consumer    The SRTM tile consumer for which to cache the bounds.
     *                    Registering the consumer with the tiles will ensure that
     *                    these are not cleaned up.
     */
    public synchronized void cacheSRTMTiles(int intLatSouth, int intLonWest, int intLatNorth, int intLonEast,
            SRTMTileConsumer consumer) {
        // Not across 180th meridian
        if (intLonWest <= intLonEast) {
            for (int lon = intLonWest; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    try {
                        getTileCacheEntryFor(SRTMTile.getTileID(lat, lon), consumer);
                    } catch (CancellationException e) {
                        continue;
                    }
            }
        }
        // Across 180th meridian
        else {
            for (int lon = intLonWest; lon <= 179; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    try {
                        getTileCacheEntryFor(SRTMTile.getTileID(lat, lon), consumer);
                    } catch (CancellationException e) {
                        continue;
                    }
            }
            for (int lon = -180; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    try {
                        getTileCacheEntryFor(SRTMTile.getTileID(lat, lon), consumer);
                    } catch (CancellationException e) {
                        continue;
                    }
            }
        }
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
     */
    public SRTMTile getTileOrWait(String srtmTileID, SRTMTileConsumer consumer)
            throws CancellationException, ExecutionException, InterruptedException {
        // Map editing is often done in the very same place for a while.
        // Therefore consider the previous tile first of all.
        synchronized (previousTileLock) {
            if (previousTile != null && previousTile.getID().equals(srtmTileID))
                return previousTile;
        }
        // Otherwise wait for the tile to be available with data
        // May throw CancellationException
        SRTMTileCacheEntry entry = getTileCacheEntryFor(srtmTileID, consumer);
        // May throw ExecutionException or InterruptedException
        SRTMTile tile = entry.getTileOrWait();
        if (entry.getStatus() == SRTMTileCacheEntry.Status.LOADING_CANCELED)
            throw new CancellationException("Loading of SRTM tile " + srtmTileID + " was canceled");
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

        SRTMTileCacheEntry entry = cache.get(srtmTileID);
        if (entry != null) {
            if (entry.isLoadingCompleted()) {
                try {
                    // May throw ExecutionException or InterruptedException
                    SRTMTile tile = entry.getTileOrWait();
                    synchronized (previousTileLock) {
                        previousTile = tile;
                    }
                    return Optional.of(tile);
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns an SRTM tile cache entry enabling access to the requested SRMT tile
     * as soon as it is loaded. Asynchronously loads the SRTM tile if it is not
     * cached yet. Registers the provided SRTM tile consumer with the cache entry.
     * This ensures that the cache entry is not cleaned up as long as the consumer
     * is registered.
     *
     * @param srtmTileID The ID of the requested SRTM tile.
     * @param consumer   The SRTM tile consumer to register with the cache entry of
     *                   the requested SRTM tile.
     * @return An SRTM tile cache entry that enables access to the SRTM tile as soon
     *         as it is loaded.
     * @throws CancellationException if loading of the SRTM tile was canceled.
     */
    public synchronized SRTMTileCacheEntry getTileCacheEntryFor(String srtmTileID, SRTMTileConsumer consumer)
            throws CancellationException {
        synchronized (cache) {
            SRTMTileCacheEntry entry = cache.get(srtmTileID);
            if (entry != null) {
                synchronized (entry) {
                    if (entry.getStatus() == SRTMTileCacheEntry.Status.LOADING_CANCELED) {
                        cache.remove(srtmTileID);
                        throw new CancellationException("Loading of SRTM tile " + srtmTileID + " was canceled.");
                    } else {
                        entry.addTileConsumer(consumer);
                        return entry;
                    }
                }
            }
            entry = cache.computeIfAbsent(srtmTileID, id -> {
                SRTMTileCacheEntry newEntry = new SRTMTileCacheEntry(id);
                newEntry.addTileConsumer(consumer);
                startLoadingAsync(newEntry);
                return newEntry;
            });
            entry.addTileConsumer(consumer);
            return entry;
        }
    }

    private CompletableFuture<Void> startLoadingAsync(SRTMTileCacheEntry entry) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
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
                        entry.setStatus(SRTMTileCacheEntry.Status.READING_SCHEDULED);
                        try {
                            srtmTile = readSRTMFile(entry, srtmFile, dataSource);
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
                    else if (autoDownloadEnabled && dataSource.canAutoDownload()) {
                        entry.setStatus(SRTMTileCacheEntry.Status.DOWNLOAD_SCHEDULED);
                        Optional<File> optionalFile = downloadSRTMFile(entry, dataSource);
                        if (optionalFile.isPresent()) {
                            srtmFile = optionalFile.get();
                            try {
                                srtmTile = readSRTMFile(entry, srtmFile, dataSource);
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

                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Loading of SRTM tile " + entry.getID() + " was interrupted.");

                if (status == null)
                    status = SRTMTileCacheEntry.Status.FILE_MISSING;

                if (srtmTile == null) {
                    srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                } else {
                    status = SRTMTileCacheEntry.Status.VALID;
                    synchronized (previousTileLock) {
                        if (previousTile == null)
                            previousTile = srtmTile;
                    }
                }
                entry.setStatus(status);
                entry.complete(srtmTile); // Wakes up all waiters
                updateCacheSize(entry.getDataSize());
                synchronized (listeners) {
                    for (SRTMTileCacheListener listener : listeners)
                        listener.srtmTileCached(srtmTile, status);
                }
                Logging.info("Elevation: SRTM tile " + entry.getID() + " loaded with status "
                        + entry.getStatus().toString());
            } catch (Exception e) {
                boolean loadingCanceled = e instanceof ExecutionException || e instanceof InterruptedException;
                SRTMTileCacheEntry.Status status;
                if (loadingCanceled)
                    status = SRTMTileCacheEntry.Status.LOADING_CANCELED;
                else
                    status = SRTMTileCacheEntry.Status.DATA_INVALID;
                entry.setStatus(status);
                // We don't do this; therefore ExecutionException should be thrown in practice
                // entry.getFuture().completeExceptionally(e);
                SRTMTile srtmTile = SRTMTile.createInvalidTile(srtmTileID, srtmType);
                entry.complete(srtmTile);
                if (!loadingCanceled) {
                    Logging.error("Elevation: Exception retrieving SRTM tile " + srtmTileID + ": " + e.toString());
                    e.printStackTrace();
                }
                synchronized (listeners) {
                    for (SRTMTileCacheListener listener : listeners)
                        listener.srtmTileCached(srtmTile, status);
                }
            }
        });
        return future;
    }

    /**
     * Flushes the cache by removing all tiles and sets the cache size to zero.
     */
    private synchronized void flush() {
        // Cancel all currently running/queued file download tasks
        fileDownloadExecutor.cancelAllTasks();
        // Cancel all currently running/queued file read tasks
        fileReadExecutor.cancelAllTasks();
        cache.clear();
        cacheSize = 0;
        synchronized (previousTileLock) {
            previousTile = null;
        }
        Logging.info("Elevation: SRTM tile cache flushed");
    }

    /**
     * Removes least recently used SRTM tiles actually holding elevation data from
     * the cache ensuring that the cache size limit is not exceeded.
     */
    public synchronized void clean() {
        if (cacheSize <= cacheSizeLimit)
            return;
        synchronized (cache) {
            ArrayList<SRTMTileCacheEntry> allEntries = new ArrayList<>(cache.values());
            allEntries.sort(Comparator.comparingLong(SRTMTileCacheEntry::getAccessTime));
            for (SRTMTileCacheEntry entry : allEntries) {
                synchronized (entry) {
                    // Skip tiles that are known but have no size
                    if (entry.getDataSize() == 0)
                        continue;
                    considerRemoveEntry(entry);
                }

                if (cacheSize <= cacheSizeLimit)
                    break;
            }
        }
    }

    private synchronized boolean considerRemoveEntry(SRTMTileCacheEntry entry) {
        if (entry == null)
            return false;

        String srtmTileID;
        int dataSize;
        synchronized (cache) {
            synchronized (entry) {
                if (entry.getTileConsumerCount() != 0)
                    return false;
                srtmTileID = entry.getID();
                if (!cache.remove(srtmTileID, entry))
                    return false;
                if (!entry.isLoadingCompleted())
                    entry.cancelLoading();
                dataSize = entry.getDataSize();
                entry.disposeTile();
                if (dataSize > 0)
                    updateCacheSize(-dataSize);
                Logging.info("Elevation: Removed SRTM tile " + srtmTileID + " with status '"
                        + entry.getStatus().toString() + "' and size " + getSizeString(dataSize)
                        + " from cache; cache size: " + getSizeString(cacheSize));
            }
        }

        return true;
    }

    private synchronized void updateCacheSize(int dataSizeToAdd) {
        cacheSize += dataSizeToAdd;
        if (cacheSize > cacheSizeLimit)
            clean();
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
        // Nothing else to do if the enabled status is unchanged
        if (autoDownloadEnabled == enabled)
            return;

        if (enabled) {
            if (srtmFileDownloader == null) {
                // Create an SRTM file downloader instance
                srtmFileDownloader = new SRTMFileDownloader();
                // TODO: If auto-downloading was (re-)enabled, try to download all SRTM tiles
                // that are missing or previously failed to download.
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
     * Removes the provided SRTM tile consumer from the provided tile cache entry
     * and removes the entry from the cache if no consumer is any longer registered
     * with the entry.
     *
     * @param entry The cache entry from which to remove the consumer.
     * @param consumer The consumer to remove.
     */
    public synchronized void removeSRTMTileConsumer(SRTMTileCacheEntry entry, SRTMTileConsumer consumer) {
        synchronized (entry) {
            if (entry.removeTileConsumer(consumer))
                considerRemoveEntry(entry);
        }
    }
}
