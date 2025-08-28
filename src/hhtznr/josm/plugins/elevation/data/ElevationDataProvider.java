package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloadListener;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;
import hhtznr.josm.plugins.elevation.io.SRTMFiles;

/**
 * Class {@code ElevationDataProvider} provides SRTM tiles and elevation data
 * obtained from these tiles. The data is read from on-disk SRTM files and
 * cached in RAM. Optionally, missing SRTM files can be downloaded.
 *
 * @author Harald Hetzner
 */
public class ElevationDataProvider implements SRTMFileDownloadListener {

    /**
     * This elevation data provider's in-memory cache where SRTM tiles read from
     * file are stored.
     */
    private final SRTMTileCache tileCache;

    private SRTMTile previousTile = null;

    private final Object tileGridLock = new Object();
    private SRTMTileGrid previousTileGrid = null;

    private SRTMFileReader srtmFileReader;
    private SRTMFileDownloader srtmFileDownloader;
    private SRTMTile.Interpolation eleInterpolation;
    private boolean autoDownloadEnabled = false;

    private final List<ElevationDataSource> elevationDataSources;

    private final LinkedList<ElevationDataProviderListener> listeners = new LinkedList<>();

    private final ExecutorService fileReadExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new elevation data provider based on preferences or defaults.
     */
    public ElevationDataProvider() {
        this(ElevationPreferences.ELEVATION_DATA_SOURCES, ElevationPreferences.getRAMCacheSizeLimit(),
                ElevationPreferences.getPreferredSRTMType(), ElevationPreferences.getElevationInterpolation(),
                ElevationPreferences.getAutoDownloadEnabled());
    }

    /**
     * Creates a new elevation data provider.
     *
     * @param elevationDataSources A list of data sources from which to obtain
     *                             elevation data.
     * @param ramCacheMaxSize      The maximum size of the in-memory SRTM tile cache
     *                             in MiB.
     * @param preferredSRTMType    The preferred SRTM type (SRTM1 or SRTM3).
     * @param eleInterpolation     The type of elevation interpolation.
     * @param autoDownloadEnabled  If {@code true} automatic downloading of missing
     *                             SRTM tiles will be attempted.
     */
    private ElevationDataProvider(List<ElevationDataSource> elevationDataSources, int ramCacheMaxSize,
            SRTMTile.Type preferredSRTMType, SRTMTile.Interpolation eleInterpolation, boolean autoDownloadEnabled) {
        this.elevationDataSources = elevationDataSources;
        srtmFileReader = new SRTMFileReader();
        tileCache = new SRTMTileCache(ramCacheMaxSize);
        setPreferredSRTMType(preferredSRTMType);
        this.eleInterpolation = eleInterpolation;
        setAutoDownloadEnabled(autoDownloadEnabled);
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
     * Returns the SRTM type that is preferred by this SRTM file provider.
     *
     * @return The preferred SRTM type.
     */
    public SRTMTile.Type getPreferredSRTMType() {
        return elevationDataSources.get(0).getSRTMTileType();
    }

    /**
     * Sets the SRTM type which is preferred by this elevation data provider by
     * sorting the list of elevation data sources accordingly.
     *
     * @param type The SRTM type to be preferred.
     */
    public void setPreferredSRTMType(SRTMTile.Type type) {
        if (type == SRTMTile.Type.SRTM1)
            elevationDataSources.sort(Comparator.comparing((ElevationDataSource s) -> s.getSRTMTileType()));
        else
            elevationDataSources.sort(Comparator.comparing((ElevationDataSource s) -> s.getSRTMTileType()).reversed());
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
     * location, if an appropriate SRTM file is available in the SRTM directory.
     *
     * @param latLon The coordinate where the elevation is of interest.
     * @return The closest raster location to the given location and its elevation
     *         or the given location and {@link SRTMTile#SRTM_DATA_VOID
     *         SRTMTile.SRTM_DATA_VOID} if there is a data void or no SRTM data
     *         covering the location is available.
     */
    public LatLonEle getLatLonEle(ILatLon latLon) {
        SRTMTile srtmTile = getSRTMTile(SRTMTile.getTileID(latLon));
        // Retrieves and returns elevation and its actual coordinate if SRTM data is
        // valid
        return srtmTile.getLatLonEle(latLon, eleInterpolation);
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
            if (previousTileGrid == null || !previousTileGrid.covers(bounds)
                    || previousTileGrid.getNominalBounds().getWidth() > 1.5 * bounds.getWidth()
                    || previousTileGrid.getNominalBounds().getHeight() > 1.5 * bounds.getHeight())
                previousTileGrid = new SRTMTileGrid(this, bounds);
            return previousTileGrid.getElevationRaster();
        }
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds. The segments do not have a useful order. If this method does not
     * return {@code null}, the contour lines will always cover the bounds. However,
     * the method may return contour lines for a larger area as needed.
     *
     * @param bounds  The bounds in latitude-longitude coordinate space.
     * @param isostep Step between adjacent elevation contour lines.
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds or {@code null} if no suitable elevation data is cached to
     *         compute the contour lines.
     */
    public ContourLines getContourLines(Bounds bounds, int isostep) {
        synchronized (tileGridLock) {
            if (previousTileGrid == null || !previousTileGrid.covers(bounds)
                    || previousTileGrid.getNominalBounds().getWidth() > 1.5 * bounds.getWidth()
                    || previousTileGrid.getNominalBounds().getHeight() > 1.5 * bounds.getHeight())
                previousTileGrid = new SRTMTileGrid(this, bounds);
            return previousTileGrid.getContourLines(isostep);
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
            if (previousTileGrid == null || !previousTileGrid.covers(bounds)
                    || previousTileGrid.getNominalBounds().getWidth() > 1.5 * bounds.getWidth()
                    || previousTileGrid.getNominalBounds().getHeight() > 1.5 * bounds.getHeight())
                previousTileGrid = new SRTMTileGrid(this, bounds);
            return previousTileGrid.getHillshadeImage(altitudeDeg, azimuthDeg, withPerimeter);
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
        // Logging.info("Elevation: Trigger caching of SRTM tiles covering current map
        // bounds from S = " + intLatSouth
        // + "°, E = " + intLonEast + "° to N = " + intLatNorth + "°, W = " + intLonWest
        // + "°");

        // Not across 180th meridian
        if (intLonWest <= intLonEast) {
            for (int lon = intLonWest; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
        // Across 180th meridian
        else {
            for (int lon = intLonWest; lon <= 179; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
            for (int lon = -180; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
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
        // Logging.info("Elevation: Trigger caching of SRTM tiles covering current map
        // bounds from S = " + intLatSouth
        // + "°, E = " + intLonEast + "° to N = " + intLatNorth + "°, W = " + intLonWest
        // + "°");

        // Not across 180th meridian
        if (intLonWest <= intLonEast) {
            for (int lon = intLonWest; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
        // Across 180th meridian
        else {
            for (int lon = intLonWest; lon <= 179; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
            for (int lon = -180; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
    }

    /**
     * Returns an SRTM tile for the given SRTM tile ID.
     *
     * @param srtmTileID The SRTM tile ID.
     * @return The SRTM tile for the given SRTM tile ID. The returned tile may hold
     *         no data, if it is not available (yet), but it will never be
     *         {@code null}.
     */
    public synchronized SRTMTile getSRTMTile(String srtmTileID) {
        // Map editing is often done in the very same place for a while.
        // Therefore consider the previous tile first of all.
        SRTMTile srtmTile = previousTile;

        // If there is no previous tile or the previous tile is for another tile ID
        if (srtmTile == null || !srtmTile.getID().equals(srtmTileID)) {
            srtmTile = tileCache.get(srtmTileID);

            // Data previously not in cache
            if (srtmTile == null) {
                for (ElevationDataSource elevationDataSource : elevationDataSources) {
                    File srtmFile = getLocalSRTMFile(srtmTileID, elevationDataSource);
                    // If an SRTM file with the data exists on disk, read it in
                    if (srtmFile != null) {
                        Logging.info("Elevation: Caching data of SRTM tile " + srtmTileID + " from file "
                                + srtmFile.getAbsolutePath());
                        // Read the SRTM file as task in a separate thread
                        srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null,
                                SRTMTile.Status.READING_SCHEDULED);
                        try {
                            getSRTMTile(srtmFile, elevationDataSource.getSRTMTileType());
                        } catch (RejectedExecutionException e) {
                            Logging.info("Elevation: Execution of file read task for SRTM tile " + srtmTileID
                                    + " from file " + srtmFile.getAbsolutePath() + " rejected: " + e.toString());
                            srtmTile = tileCache.remove(srtmTileID);
                        }
                        break;
                    }
                    // If auto-downloading of SRTM files is enabled, try to download the missing
                    // file
                    else if (autoDownloadEnabled && elevationDataSource.canAutoDownload()) {
                        if (elevationDataSource.canAutoDownload()) {
                            srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null,
                                    SRTMTile.Status.DOWNLOAD_SCHEDULED);
                            try {
                                srtmFileDownloader.downloadSRTMFile(srtmTileID, elevationDataSource);
                                break;
                            } catch (RejectedExecutionException e) {
                                Logging.info("Elevation: Execution of download task for SRTM tile " + srtmTileID
                                        + " rejected: " + e.toString());
                                srtmFileDownloadFailed(srtmTileID, e);
                            }
                        }

                    }
                }

            }
            // If we could not establish an SRTM tile to be, put an empty data set with
            // status "file missing" into the cache
            if (srtmTile == null)
                srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.FILE_MISSING);
            // If we have a valid tile now, remember it as the previous tile
            else if (srtmTile.getStatus() == SRTMTile.Status.VALID)
                previousTile = srtmTile;
        }
        return srtmTile;
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
     * Reads an SRTM tile from an optionally ZIP-compressed SRTM file as obtained
     * from NASA in a separate thread.
     *
     * @param srtmFile The SRTM file to read.
     * @return A {@code Future} from which the read SRTM tile can be obtained.
     * @throws RejectedExecutionException Thrown if the file read task cannot be
     *                                    accepted by the thread executor.
     */
    private Future<SRTMTile> getSRTMTile(File srtmFile, SRTMTile.Type type) throws RejectedExecutionException {
        Callable<SRTMTile> fileReadTask = () -> {
            String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());
            tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.READING);

            SRTMTile srtmTile = null;
            try {
                srtmTile = tileCache.putOrUpdateSRTMTile(srtmFileReader.readSRTMFile(srtmFile, type));
                // Check if all SRTM tiles of most recently requested tile grid are cached now
                // If so, inform listeners
                SRTMTileGrid tileGrid = previousTileGrid;
                if (tileGrid != null && tileGrid.checkAllRequiredSRTMTilesCached()) {
                    synchronized (listeners) {
                        for (ElevationDataProviderListener listener : listeners)
                            listener.elevationDataAvailable(tileGrid);
                    }
                }
                return srtmTile;
            } catch (IOException e) {
                Logging.error("Elevation: " + e.toString());
                return tileCache.putOrUpdateSRTMTile(srtmTileID, type, null, SRTMTile.Status.FILE_INVALID);
            }
        };

        return fileReadExecutor.submit(fileReadTask);
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
            tileCache.cleanAllTilesWithStatus(SRTMTile.Status.DOWNLOAD_FAILED);
        // Nothing else to do if the enabled status is unchanged
        if (autoDownloadEnabled == enabled)
            return;
        if (enabled) {
            if (srtmFileDownloader == null) {
                // Create an SRTM file downloader instance
                srtmFileDownloader = new SRTMFileDownloader();
                srtmFileDownloader.addDownloadListener(this);
                // Clear any SRTM tiles marked as missing from the cache so they will be
                // downloaded now, if needed
                tileCache.cleanAllTilesWithStatus(SRTMTile.Status.FILE_MISSING);
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
    public void srtmFileDownloadStarted(String srtmTileID) {
        tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOADING);
    }

    @Override
    public void srtmFileDownloadSucceeded(File srtmFile, SRTMTile.Type type) {
        // Read the SRTM file as task in a separate thread
        try {
            getSRTMTile(srtmFile, type);
        } catch (RejectedExecutionException e) {
            String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());
            Logging.info("Elevation: Execution of file read task for SRTM tile " + srtmTileID + " from file "
                    + srtmFile.getAbsolutePath() + " rejected: " + e.toString());
            tileCache.remove(srtmTileID);
        }
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID, Exception exception) {
        tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOAD_FAILED);
    }
}
