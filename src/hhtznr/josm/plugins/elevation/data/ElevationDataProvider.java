package hhtznr.josm.plugins.elevation.data;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.gui.LowestAndHighestPoints;
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

    private final Object tileGridLock = new Object();
    private SRTMTileGrid srtmTileGrid = null;

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
     * Returns the dimensions of the SRTM tile grid used by this elevation data
     * provider.
     *
     * @return An array of length {@code 2}, which provides the dimensions of the
     *         SRTM tile grid: grid width (index {@code 0}) and grid height (index
     *         {@code 1}).
     */
    public int[] getSRTMTileGridDimensions() {
        int[] dimensions = new int[2];
        dimensions[0] = 0;
        dimensions[1] = 0;
        synchronized (tileGridLock) {
            if (srtmTileGrid != null) {
                dimensions[0] = srtmTileGrid.getGridWidth();
                dimensions[1] = srtmTileGrid.getGridHeight();
            }
        }
        return dimensions;
    }

    /**
     * Returns the dimensions of the elevation raster of the SRTM tile grid used by
     * this elevation data provider.
     *
     * @return An array of length {@code 2}, which provides the dimensions of the
     *         elevation raster of the SRTM tile grid: raster width (index
     *         {@code 0}) and raster height (index {@code 1}).
     */
    public int[] getSRTMTileGridRasterDimensions() {
        int[] dimensions = new int[2];
        dimensions[0] = 0;
        dimensions[1] = 0;
        synchronized (tileGridLock) {
            if (srtmTileGrid != null) {
                dimensions[0] = srtmTileGrid.getRasterWidth();
                dimensions[1] = srtmTileGrid.getRasterHeight();
            }
        }
        return dimensions;
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
        synchronized (tileGridLock) {
            SRTMTile.Type oldType = getSRTMType();
            // Only sets the type of the new type is not equal to the old type
            if (tileCache.setSRTMType(type)) {
                srtmTileGrid = null;
                synchronized (listeners) {
                    for (ElevationDataProviderListener listener : listeners) {
                        listener.srtmTileTypeChanged(oldType, type);
                    }
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
     * Returns the elevation at the raster location that is closest to the provided
     * location, if an appropriate SRTM file is available in the SRTM directory and
     * the data is loaded into memory.
     *
     * @param latLon The coordinate where the elevation is of interest.
     * @return The closest raster location to the given location and its elevation
     *         or the given location and {@link SRTMTile#SRTM_DATA_VOID
     *         SRTMTile.SRTM_DATA_VOID} if there is a data void or no SRTM data
     *         covering the location is available or the data is not loaded yet.
     */
    public LatLonEle getLatLonEleNoWait(ILatLon latLon) {
        String tileID = SRTMTile.getTileID(latLon);
        Optional<SRTMTile> optionalTile = tileCache.getTileIfPresent(tileID);
        return optionalTile.map(srtmTile -> srtmTile.getLatLonEle(latLon, eleInterpolation))
                .orElse(new LatLonEle(latLon));
    }

    public LatLonEle getLatLonEleOrWait(ILatLon latLon)
            throws CancellationException, ExecutionException, InterruptedException {
        String tileID = SRTMTile.getTileID(latLon);
        // May throw an exception
        SRTMTile srtmTile = tileCache.getTileOrWait(tileID);
        return srtmTile.getLatLonEle(latLon, eleInterpolation);
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
        synchronized (tileGridLock) {
            if (srtmTileGrid == null || !srtmTileGrid.covers(bounds))
                srtmTileGrid = new SRTMTileGrid(this, bounds);
            try {
                return srtmTileGrid.getView(bounds).getLowestAndHighestPoints();
            } catch (SRTMTileGridException e) {
                Logging.error("Elevation: Cannot create lowest and highest points: " + e.toString());
                return null;
            }
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
        synchronized (tileGridLock) {
            if (srtmTileGrid == null || !srtmTileGrid.covers(bounds))
                srtmTileGrid = new SRTMTileGrid(this, bounds);

            try {
                return srtmTileGrid.getView(bounds).getElevationRaster();
            } catch (SRTMTileGridException e) {
                Logging.error("Elevation: Cannot create elevation raster: " + e.toString());
                return null;
            }
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
        synchronized (tileGridLock) {
            if (srtmTileGrid == null)
                srtmTileGrid = new SRTMTileGrid(this, bounds);
            Bounds renderingBounds = srtmTileGrid.getRenderingBoundsScaledByRasterStep(bounds,
                    ContourLines.BOUNDS_SCALE_RASTER_STEP);
            if (!srtmTileGrid.covers(renderingBounds))
                srtmTileGrid = new SRTMTileGrid(this, renderingBounds);
            try {
                return srtmTileGrid.getView(renderingBounds).getContourLines(isostep, lowerCutoffElevation,
                        upperCutoffElevation);
            } catch (SRTMTileGridException e) {
                Logging.error("Elevation: Cannot create contour lines: " + e.toString());
                return null;
            }
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
        synchronized (tileGridLock) {
            if (srtmTileGrid == null)
                srtmTileGrid = new SRTMTileGrid(this, bounds);
            Bounds renderingBounds = srtmTileGrid.getRenderingBoundsScaledByRasterStep(bounds,
                    HillshadeImageTile.BOUNDS_SCALE_RASTER_STEP);
            if (!srtmTileGrid.covers(renderingBounds))
                srtmTileGrid = new SRTMTileGrid(this, renderingBounds);

            try {
                return srtmTileGrid.getView(renderingBounds).getHillshadeImageTile(altitudeDeg, azimuthDeg,
                        withPerimeter);
            } catch (SRTMTileGridException e) {
                Logging.error("Elevation: Cannot create hillshade: " + e.toString());
                return null;
            }
        }
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
     * Triggers caching of all SRTM tiles needed to cover the defined area.
     *
     * @param southWest The south west (lower left) coordinate of the area.
     * @param northEast The north east (upper right) coordinate of the area.
     */
    public void cacheSRTMTiles(ILatLon southWest, ILatLon northEast) {
        int intLatSouth = (int) Math.floor(southWest.lat());
        int intLatNorth = (int) Math.floor(northEast.lat());
        int intLonWest = (int) Math.floor(southWest.lon());
        int intLonEast = (int) Math.floor(northEast.lon());
        cacheSRTMTiles(intLatSouth, intLonWest, intLatNorth, intLonEast);
    }

    /**
     * Trigger caching of SRTM files within the given bounds if not cached yet.
     * Latitude and longitude values are full arc degrees and refer to the south
     * west (lower left) corner of the SRTM tiles to be cached.
     *
     * @param intLatSouth The southern most latitude.
     * @param intLonWest  The western most longitude.
     * @param intLatNorth The northern most latitude.
     * @param intLonEast  The eastern most latitude
     */
    public void cacheSRTMTiles(int intLatSouth, int intLonWest, int intLatNorth, int intLonEast) {
        synchronized (tileCache) {
            // Not across 180th meridian
            if (intLonWest <= intLonEast) {
                for (int lon = intLonWest; lon <= intLonEast; lon++) {
                    for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                        // Calling the getter method will ensure that tiles are being read or downloaded
                        tileCache.getTileIfPresent(SRTMTile.getTileID(lat, lon));
                }
            }
            // Across 180th meridian
            else {
                for (int lon = intLonWest; lon <= 179; lon++) {
                    for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                        tileCache.getTileIfPresent(SRTMTile.getTileID(lat, lon));
                }
                for (int lon = -180; lon <= intLonEast; lon++) {
                    for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                        tileCache.getTileIfPresent(SRTMTile.getTileID(lat, lon));
                }
            }
        }
    }

    /**
     * Caches all SRTM tiles required to cover the specified bounds.
     *
     * @param bounds The map bounds.
     */
    public void cacheSRTMTiles(Bounds bounds) {
        int intLatSouth = (int) Math.floor(bounds.getMinLat());
        int intLatNorth = (int) Math.floor(bounds.getMaxLat());
        int intLonWest = (int) Math.floor(bounds.getMinLon());
        int intLonEast = (int) Math.floor(bounds.getMaxLon());
        cacheSRTMTiles(intLatSouth, intLonWest, intLatNorth, intLonEast);
    }

    /**
     * Returns the SRTM tile with the respective ID as soon as it has been cached.
     * Caching can involve downloading. Blocks the executing thread for as long as
     * it takes and as long as loading is not interrupted.
     *
     * @param srtmTileID The ID of the SRTM tile to retrieve.
     * @return The SRTM tile as a valid tile with data if the tile exists and the
     *         data is valid or otherwise as an invalid tile without data.
     * @throws ExecutionException   should never be thrown; would be thrown if the
     *                              future used for asynchronous loading completed
     *                              exceptionally
     * @throws InterruptedException if the current thread was interrupted while
     *                              waiting
     */
    public SRTMTile getSRTMTileOrWait(String srtmTileID) throws ExecutionException, InterruptedException {
        return tileCache.getTileOrWait(srtmTileID);
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

    /**
     * Returns a {@code Future} from which an SRTM tile specified by the provided ID
     * can be obtained. Starts asynchronous loading of the SRTM tile of the tile is
     * not cached yet.
     *
     * @param srtmTileID The ID of the SRTM tile for which to retrieve the future.
     * @return A {@code Future} from which the SRTM tile can be obtained as soon as
     *         it is loaded.
     */
    public CompletableFuture<SRTMTile> getSRTMTileFuture(String srtmTileID) {
        return tileCache.getTileFuture(srtmTileID);
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
        synchronized (tileGridLock) {
            if (srtmTileGrid != null && srtmTileGrid.contains(tile) && srtmTileGrid.areAllTilesCached()) {
                synchronized (listeners) {
                    for (ElevationDataProviderListener listener : listeners)
                        listener.elevationDataAvailable(srtmTileGrid);
                }
            }
        }
    }
}
