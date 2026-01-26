package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.gui.LowestAndHighestPoints;
import hhtznr.josm.plugins.elevation.gui.MapViewElevationDataConsumer;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

/**
 * Class {@code ElevationDataProvider} provides SRTM tiles and elevation data
 * obtained from these tiles. The data is read from on-disk SRTM files and
 * cached in RAM. Optionally, missing SRTM files can be downloaded.
 *
 * @author Harald Hetzner
 */
public class ElevationDataProvider implements SRTMTileCacheListener {

    /**
     * This elevation data provider's in-memory cache where SRTM tiles read from
     * file are stored.
     */
    private final SRTMTileCache tileCache;

    private final Set<SRTMTileGrid> activeTileGrids = ConcurrentHashMap.newKeySet();

    private SRTMTile.Interpolation eleInterpolation;

    private final LinkedList<ElevationDataProviderListener> listeners = new LinkedList<>();

    /**
     * Creates a new elevation data provider based on preferences or defaults.
     */
    public ElevationDataProvider() {
        this(ElevationPreferences.getElevationDataSources(), ElevationPreferences.getRAMCacheSizeLimit(),
                ElevationPreferences.getSRTMType(), ElevationPreferences.getElevationInterpolation(),
                ElevationPreferences.getAutoDownloadEnabled());
    }

    /**
     * Creates a new elevation data provider.
     *
     * @param elevationDataSources A list of data sources from which to obtain
     *                             elevation data.
     * @param ramCacheMaxSize      The maximum size of the in-memory SRTM tile cache
     *                             in MiB.
     * @param srtmType             The SRTM type (SRTM1 or SRTM3).
     * @param eleInterpolation     The type of elevation interpolation.
     * @param autoDownloadEnabled  If {@code true} automatic downloading of missing
     *                             SRTM tiles will be attempted.
     */
    public ElevationDataProvider(List<ElevationDataSource> elevationDataSources, int ramCacheMaxSize,
            SRTMTile.Type srtmType, SRTMTile.Interpolation eleInterpolation, boolean autoDownloadEnabled) {
        tileCache = new SRTMTileCache(elevationDataSources, ramCacheMaxSize);
        tileCache.addSRTMTileCacheListener(this);
        setSRTMType(srtmType);
        this.eleInterpolation = eleInterpolation;
        setAutoDownloadEnabled(autoDownloadEnabled);
    }

    /**
     * Returns a list with textual information on the currently active SRTM tile
     * grids.
     *
     * @return A list with string array of length {@code 2}. The list will contain a
     *         string array item for each active tile grid. Each string array will
     *         contain the name of the grid at index {@code 0} and information onf
     *         the grid at index {@code 1}.
     */
    public List<String[]> getTileGridInfo() {
        List<String[]> infoList = new ArrayList<>();
        synchronized (activeTileGrids) {
            for (SRTMTileGrid tileGrid : activeTileGrids) {
                String[] tileGridInfo = new String[2];
                tileGridInfo[0] = tileGrid.getName();
                tileGridInfo[1] = tileGrid.getInfoString();
                infoList.add(tileGridInfo);
            }
        }
        return infoList;
    }

    /**
     * Returns an info string on current cache utilization.
     *
     * @return An info string on current cache utilization: Number of cached tiles,
     *         cache size and maximum cache size.
     */
    public String getTileCacheInfo() {
        return tileCache.getInfo();
    }

    /**
     * Adds a listener to this elevation data provider's SRTM tile cache. The
     * listener will be informed whenever a valid SRTM tile is cached.
     *
     * @param listener The listener to add.
     */
    public void addTileCacheListener(SRTMTileCacheListener listener) {
        tileCache.addSRTMTileCacheListener(listener);
    }

    /**
     * Removes a listener to this elevation data provider's SRTM tile cache.
     *
     * @param listener The listener to remove.
     */
    public void removeTileCacheListener(SRTMTileCacheListener listener) {
        tileCache.removeSRTMTileCacheListener(listener);
    }

    /**
     * Returns a map that provides information on the currently cached SRTM tiles.
     * The tiles are ordered by their name.
     *
     * @return A map providing information on the currently cached SRTM tiles: Tile
     *         IDs, type, status, size and source.
     */
    public Map<String, String> getCachedTilesInfo() {
        return tileCache.getCachedTilesInfo();
    }

    /**
     * Returns the list of elevation data sources.
     *
     * @return The list of elevation data sources.
     */
    public List<ElevationDataSource> getElevationDataSources() {
        return tileCache.getElevationDataSources();
    }

    /**
     * Returns the type of SRTM data of this SRTM file provider.
     *
     * @return The SRTM type.
     */
    public SRTMTile.Type getSRTMType() {
        return tileCache.getSRTMType();
    }

    /**
     * Sets the SRTM type by setting the elevation data sources accordingly. If the
     * type is not equal to the current type, flushes the tile grid cache and sets
     * the current SRTM tile grid to {@code null} to enforce that it will be
     * regenerated upon the next elevation query or elevation layer paint operation.
     *
     * @param type The SRTM type to set.
     */
    public boolean setSRTMType(SRTMTile.Type type) {
        synchronized (activeTileGrids) {
            SRTMTile.Type oldType = getSRTMType();
            // Only sets the type of the new type is not equal to the old type
            if (tileCache.setSRTMType(type)) {
                activeTileGrids.clear();
                synchronized (listeners) {
                    for (ElevationDataProviderListener listener : listeners)
                        listener.srtmTileTypeChanged(oldType, type);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Returns the type of elevation interpolation.
     *
     * @return The type of elevation interpolation.
     */
    public SRTMTile.Interpolation getElevationInterpolation() {
        return eleInterpolation;
    }

    /**
     * Sets the type of elevation interpolation.
     *
     * @param eleInterpolation The type of elevation interpolation to set.
     */
    public void setElevationInterpolation(SRTMTile.Interpolation eleInterpolation) {
        this.eleInterpolation = eleInterpolation;
    }

    /**
     * Returns the elevation at the given location immediately, if an appropriate
     * SRTM file is available and the data is already loaded into memory. Otherwise,
     * a {@link LatLonEle} with elevation set to {@link SRTMTile#SRTM_DATA_VOID
     * SRTMTile.SRTM_DATA_VOID} is returned. If interpolation is disabled, the
     * raster location that is closest to the provided location and the
     * corresponding elevation is returned. Otherwise, the elevation is interpolated
     * for the provided location.
     *
     * @param latLon The coordinate where the elevation is of interest.
     * @return The coordinate (interpolation enabled) or the closest raster
     *         coordinate (interpolation disabled) and the corresponding elevation.
     */
    public LatLonEle getLatLonEleNoWait(ILatLon latLon) {
        String tileID = SRTMTile.getTileID(latLon);
        Optional<SRTMTile> optionalTile = tileCache.getTileIfPresent(tileID);
        return optionalTile.map(srtmTile -> srtmTile.getLatLonEle(latLon, eleInterpolation))
                .orElse(new LatLonEle(latLon));
    }

    /**
     * Returns the elevation at the given location immediately, if an appropriate
     * SRTM file is available and the data is already loaded into memory. Otherwise,
     * waits until data is available. If interpolation is disabled, the raster
     * location that is closest to the provided location and the corresponding
     * elevation is returned. Otherwise, the elevation is interpolated for the
     * provided location.
     *
     * @param latLon The coordinates, where elevation should be retrieved.
     * @return The coordinate (interpolation enabled) or the closest raster
     *         coordinate (interpolation disabled) and the corresponding elevation.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public LatLonEle getLatLonEleOrWait(ILatLon latLon) throws AsyncOperationException {
        // Calls ElevationDataProvider.getTileCacheEntryFor()
        // May throw CancellationException
        SingleSRTMTileConsumer tileConsumer = new SingleSRTMTileConsumer(this, latLon);

        SRTMTileCacheEntry entry = tileConsumer.getCacheEntry();
        try {
            // may throw CancellationException, ExecutionException and InterruptedException
            SRTMTile srtmTile = entry.getTileOrWait();
            return srtmTile.getLatLonEle(latLon, eleInterpolation);
        } finally {
            tileConsumer.considerDispose();
        }
    }

    /**
     * Returns an SRTM tile grid covering the specified bounds. If possible, a grid
     * matching the bounds, which is already used by other consumers, is returned.
     * <br>
     * Note: An {@link ElevationDataConsumer} needs to register itself to the
     * returned grid.
     *
     * @param bounds The bounds.
     * @return An SRTM tile grid covering the bounds, but not bigger than needed.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public synchronized SRTMTileGrid getGridMatching(Bounds bounds) throws AsyncOperationException {
        SRTMTileGrid tileGrid = null;
        SRTMTileGrid nextGrid;
        synchronized (activeTileGrids) {
            Iterator<SRTMTileGrid> iterator = activeTileGrids.iterator();
            while (iterator.hasNext()) {
                nextGrid = iterator.next();
                if (nextGrid.matchesTileGridBounds(bounds))
                    tileGrid = nextGrid;
            }
            if (tileGrid == null) {
                // May throw AsyncOperationException
                tileGrid = new SRTMTileGrid(this, bounds);
                activeTileGrids.add(tileGrid);
            }
            return tileGrid;
        }
    }

    /**
     * Returns an object with two lists providing the coordinate points from the
     * elevation raster, which have the lowest and highest elevation within the
     * given map bounds, respectively.
     *
     * @param bounds The bounds in latitude-longitude coordinate space.
     * @return An object with two lists providing the coordinate points with the
     *         lowest and highest elevation within the given bounds. {@code null} is
     *         returned if not all SRTM tiles are cached yet.
     */
    public LowestAndHighestPoints getLowestAndHighestPoints(Bounds bounds) {
        try {
            SRTMTileGrid tileGrid = getGridMatching(bounds);
            return tileGrid.getView(bounds).getLowestAndHighestPoints();
        } catch (AsyncOperationException | SRTMTileGridException e) {
            Logging.error("Elevation: Cannot create lowest and highest points: " + e.toString());
            return null;
        }
    }

    /**
     * Returns all raster coordinates and the associated elevation values within the
     * bounds. If this method does not return {@code null}, the raster coordinates
     * will always cover the bounds. However, the method may return a larger raster
     * as is needed.
     *
     * @param bounds The bounds in latitude-longitude coordinate space.
     * @return A list with raster coordinates and associated elevation values or
     *         {@code null} if insufficient cached elevation data is available.
     */
    public ElevationRaster getElevationRaster(Bounds bounds) {
        try {
            SRTMTileGrid tileGrid = getGridMatching(bounds);
            return tileGrid.getView(bounds).getElevationRaster();
        } catch (AsyncOperationException | SRTMTileGridException e) {
            Logging.error("Elevation: Cannot create elevation raster: " + e.toString());
            return null;
        }
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds. The segments do not have a useful order. If this method does not
     * return {@code null}, the contour lines will always cover the bounds. However,
     * the method may return contour lines for a larger area as needed.
     *
     * @param bounds               The bounds in latitude-longitude coordinate
     *                             space.
     * @param isostep              Step between adjacent elevation contour lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be drawn.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be drawn.
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds or {@code null} if no suitable elevation data is cached to
     *         compute the contour lines.
     */
    public ContourLines getContourLines(Bounds bounds, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) {

        try {
            SRTMTileGrid tileGrid = getGridMatching(bounds);
            Bounds renderingBounds = tileGrid.getRenderingBoundsScaledByRasterStep(bounds,
                    ContourLines.BOUNDS_SCALE_RASTER_STEP);
            tileGrid = getGridMatching(renderingBounds);
            return tileGrid.getView(renderingBounds).getContourLines(isostep, lowerCutoffElevation,
                    upperCutoffElevation);
        } catch (AsyncOperationException | SRTMTileGridException e) {
            Logging.error("Elevation: Cannot create contour lines: " + e.toString());
            return null;
        }
    }

    /**
     * Returns a buffered image with the computed hillshade ARGB values for the
     * elevation values within the given bounds. If this method does not return
     * {@code null}, the hillshade image will always cover the bounds. However, the
     * method may return an image for a larger area as needed.
     *
     * @param bounds        The bounds in latitude-longitude coordinate space.
     * @param altitudeDeg   The altitude is the angle of the illumination source
     *                      above the horizon. The units are in degrees, from 0 (on
     *                      the horizon) to 90 (overhead).
     * @param azimuthDeg    The azimuth is the angular direction of the sun,
     *                      measured from north in clockwise degrees from 0 to 360.
     * @param withPerimeter If {@code} true, the a first and last row as well as the
     *                      a first and last column without computed values will be
     *                      added such that the size of the 2D array corresponds to
     *                      that of the input data. If {@code false}, these rows and
     *                      columns will be omitted.
     * @return An image with the computed hillshade values or {@code null} if no
     *         suitable elevation data is cached to compute the hillshades.
     */
    public HillshadeImageTile getHillshadeImageTile(Bounds bounds, double altitudeDeg, double azimuthDeg,
            boolean withPerimeter) {
        try {
            SRTMTileGrid tileGrid = getGridMatching(bounds);
            Bounds renderingBounds = tileGrid.getRenderingBoundsScaledByRasterStep(bounds,
                    ContourLines.BOUNDS_SCALE_RASTER_STEP);
            tileGrid = getGridMatching(renderingBounds);
            return tileGrid.getView(renderingBounds).getHillshadeImageTile(altitudeDeg, azimuthDeg, withPerimeter);
        } catch (AsyncOperationException | SRTMTileGridException e) {
            Logging.error("Elevation: Cannot create hillshade: " + e.toString());
            return null;
        }
    }

    /**
     * Returns a new map view elevation data consumer. This special elevation data
     * consumer is used to ensure that SRTM tiles covering the map view are loaded
     * into memory. This ensures that elevation data is available for e.g. the local
     * elevation label to be operational.
     *
     * @param mapFrame              The map frame to which the map view elevation
     *                              data consumer is bound.
     * @param switchOffMapDimension The maximum size of the map in the greater of
     *                              both dimensions, for which, if exceeded, reading
     *                              of elevation data shall be switched off to avoid
     *                              high CPU and memory usage.
     * @return The map view elevation ata consumer.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public MapViewElevationDataConsumer getMapViewElevationDataConsumer(MapFrame mapFrame, double switchOffMapDimension)
            throws AsyncOperationException {
        return new MapViewElevationDataConsumer(mapFrame, this, switchOffMapDimension);
    }

    /**
     * Sets the maximum cache size to a new value and cleans the cache if required
     * to adopt to the new size limit.
     *
     * @param limit The maximum size of the cache in MiB.
     */
    public synchronized void setCacheSizeLimit(int limit) {
        tileCache.setCacheSizeLimit(limit);
    }

    /**
     * Cleans the SRTM tile cache if the maximum cache size is currently exceeded.
     */
    public synchronized void cleanCache() {
        tileCache.clean();
    }

    /**
     * Caches all SRTM tiles required to cover the bounds described by the specified
     * coordinates.
     *
     * @param latSouth The southern most latitude of the bounds.
     * @param lonWest  The western most longitude of the bounds.
     * @param latNorth The northern most latitude of the bounds.
     * @param lonEast  The eastern most latitude of the bounds.
     * @param consumer The SRTM tile consumer for which the tiles are to be cached.
     */
    private void cacheSRTMTiles(double latSouth, double lonWest, double latNorth, double lonEast,
            SRTMTileConsumer consumer) {
        int intLatSouth = (int) Math.floor(latSouth);
        int intLonWest = (int) Math.floor(lonWest);
        int intLatNorth = (int) Math.floor(latNorth);
        int intLonEast = (int) Math.floor(lonEast);
        tileCache.cacheSRTMTiles(intLatSouth, intLonWest, intLatNorth, intLonEast, consumer);
    }

    /**
     * Caches all SRTM tiles required to cover the specified bounds.
     *
     * @param bounds   The map bounds.
     * @param consumer The SRTM tile consumer for which the tiles are to be cached.
     */
    public void cacheSRTMTiles(Bounds bounds, SRTMTileConsumer consumer) {
        double latSouth = bounds.getMinLat();
        double lonWest = bounds.getMinLon();
        double latNorth = bounds.getMaxLat();
        double lonEast = bounds.getMaxLon();
        cacheSRTMTiles(latSouth, lonWest, latNorth, lonEast, consumer);
    }

    /**
     * Returns the SRTM tile with the respective ID as soon as it has been cached.
     * Caching can involve downloading. Blocks the executing thread for as long as
     * it takes and as long as loading is not interrupted.
     *
     * @param srtmTileID The ID of the SRTM tile to retrieve.
     * @return The SRTM tile as a valid tile with data if the tile exists and the
     *         data is valid or otherwise as an invalid tile without data.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public SRTMTile getSRTMTileOrWait(String srtmTileID, SRTMTileConsumer consumer) throws AsyncOperationException {
        return tileCache.getTileOrWait(srtmTileID, consumer);
    }

    /**
     * Returns the SRTM tile with the respective ID or being empty if the tile is
     * not cached (or cannot be cached, e.g. because it does not exist).
     *
     * @param srtmTileID The ID of the SRTM tile to retrieve.
     * @return An {@code Optional} holding the SRTM tile or being empty.
     */
    public Optional<SRTMTile> getSRTMTileIfPresent(String srtmTileID) {
        return tileCache.getTileIfPresent(srtmTileID);
    }

    public SRTMTileCacheEntry getSRTMTileCacheEntryFor(String srtmTileID, SRTMTileConsumer consumer)
            throws CancellationException {
        return tileCache.getTileCacheEntryFor(srtmTileID, consumer);
    }

    /**
     * Returns whether automatic downloading of missing SRTM files is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise.
     */
    public boolean isAutoDownloadEnabled() {
        return tileCache.isAutoDownloadEnabled();
    }

    /**
     * Enables or disables automatic downloading of missing SRTM tiles.
     *
     * @param enabled {@code true} enables auto-download, {@code false} disables it.
     */
    public void setAutoDownloadEnabled(boolean enabled) {
        tileCache.setAutoDownloadEnabled(enabled);
    }

    /**
     * Returns the SRTM file downloader used for auto-download of missing SRTM
     * tiles.
     *
     * @return The SRTM file downloader or {@code null} if auto-download is not
     *         enabled.
     */
    public SRTMFileDownloader getSRTMFileDownloader() {
        return tileCache.getSRTMFileDownloader();
    }

    /**
     * Adds an elevation data provider listener to this elevation data provider. All
     * listeners are informed if elevation data for a particular SRTM tile is
     * available.
     *
     * @param listener The listener to add.
     */
    public void addElevationDataProviderListener(ElevationDataProviderListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes a listener from this elevation data provider.
     *
     * @param listener The listener to be removed.
     */
    public void removeElevationDataProviderListener(ElevationDataProviderListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void srtmTileCached(SRTMTile tile, SRTMTileCacheEntry.Status status) {
        // Check if all SRTM tiles of most recently requested tile grid are cached now
        // If so, inform listeners
        synchronized (activeTileGrids) {
            for (SRTMTileGrid tileGrid : activeTileGrids) {
                try {
                    if (!tileGrid.isDisposed() && tileGrid.contains(tile) && tileGrid.areAllTilesCached()) {
                        synchronized (listeners) {
                            for (ElevationDataProviderListener listener : listeners)
                                listener.elevationDataAvailable(tileGrid);
                        }
                    }
                } catch (AsyncOperationException e) {
                    continue;
                }
            }
        }
    }

    /**
     * Removes the specified SRTM tile consumer from all SRTM tile cache entries
     * from which it is consuming tiles. Does not execute if the consumer is already
     * disposed.
     *
     * @param consumer The SRTM tile consumer to remove.
     */
    protected void removeSRTMTileConsumer(SRTMTileConsumer consumer) {
        synchronized (consumer) {
            if (consumer.isDisposed()) {
                Logging.warn("Elevation: Attempted to remove SRTM tile consumer " + consumer.getName()
                        + " which is already disposed.");
                return;
            }
            if (consumer instanceof SRTMTileGrid) {
                SRTMTileGrid tileGrid = (SRTMTileGrid) consumer;
                activeTileGrids.remove(tileGrid);
            }
            synchronized (tileCache) {
                List<SRTMTileCacheEntry> cacheEntries = consumer.getCacheEntryList();
                synchronized (cacheEntries) {
                    for (SRTMTileCacheEntry entry : cacheEntries)
                        tileCache.removeSRTMTileConsumer(entry, consumer);
                }
            }
        }
    }
}
