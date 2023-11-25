package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.LinkedList;
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
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloadListener;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;
import hhtznr.josm.plugins.elevation.io.SRTMFiles;

/**
 * Class {@code SRTMTileProvider} provides SRTM tiles and elevation data
 * obtained from these tiles. The data is read from on-disk SRTM files and
 * cached in RAM. Optionally, missing SRTM files can be downloaded.
 *
 * @author Harald Hetzner
 */
public class SRTMTileProvider implements SRTMFileDownloadListener {

    private File srtmDirectory = null;

    /**
     * This SRTM tile provider's in-memory cache where SRTM tiles read from file are
     * stored.
     */
    private final SRTMTileCache tileCache;

    private SRTMTile previousTile = null;

    private SRTMFileReader srtmFileReader;
    private SRTMFileDownloader srtmFileDownloader;
    private SRTMTile.Type preferredSRTMType;
    private SRTMTile.Interpolation eleInterpolation;
    private boolean autoDownloadEnabled = false;

    private final LinkedList<SRTMTileProviderListener> listeners = new LinkedList<>();

    private final ExecutorService fileReadExecutor = Executors.newSingleThreadExecutor();

    /**
     * Creates a new SRTM tile provider based on preferences or defaults.
     */
    public SRTMTileProvider() {
        this(ElevationPreferences.DEFAULT_SRTM_DIRECTORY,
                Config.getPref().getInt(ElevationPreferences.RAM_CACHE_SIZE_LIMIT,
                        ElevationPreferences.DEFAULT_RAM_CACHE_SIZE_LIMIT),
                SRTMTile.Type.fromString(Config.getPref().get(ElevationPreferences.PREFERRED_SRTM_TYPE,
                        ElevationPreferences.DEFAULT_PREFERRED_SRTM_TYPE.toString())),
                SRTMTile.Interpolation.fromString(Config.getPref().get(ElevationPreferences.ELEVATION_INTERPOLATION,
                        ElevationPreferences.DEFAULT_ELEVATION_INTERPOLATION.toString())),
                Config.getPref().getBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                        ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED));
    }

    /**
     * Creates a new SRTM tile provider.
     *
     * @param srtmDirectory       The directory from which to read and to which to
     *                            download SRTM files.
     * @param ramCacheMaxSize     The maximum size of the in-memory SRTM tile cache
     *                            in MiB.
     * @param preferredSRTMType   The preferred SRTM type (SRTM1 or SRTM3).
     * @param eleInterpolation    The type of elevation interpolation.
     * @param autoDownloadEnabled If {@code true} automatic downloading of missing
     *                            SRTM tiles will be attempted.
     */
    private SRTMTileProvider(File srtmDirectory, int ramCacheMaxSize, SRTMTile.Type preferredSRTMType,
            SRTMTile.Interpolation eleInterpolation, boolean autoDownloadEnabled) {
        srtmFileReader = new SRTMFileReader(srtmDirectory);
        tileCache = new SRTMTileCache(ramCacheMaxSize);
        setSRTMDirectory(srtmDirectory);
        this.preferredSRTMType = preferredSRTMType;
        this.eleInterpolation = eleInterpolation;
        setAutoDownloadEnabled(autoDownloadEnabled);
    }

    /**
     * Returns the directory where SRTM files are located.
     *
     * @return The SRTM directory.
     */
    public File getSRTMDirectory() {
        return srtmDirectory;
    }

    /**
     * Sets the directory where SRTM files are located. If the directory does not
     * exist, it is created recursively.
     *
     * @param srtmDirectory The directory to set as SRTM directory.
     */
    public void setSRTMDirectory(File srtmDirectory) {
        if (!srtmDirectory.exists() && srtmDirectory.mkdirs())
            Logging.info("Elevation: Created directory for SRTM files: " + srtmDirectory.toString());
        if (srtmDirectory.isDirectory()) {
            this.srtmDirectory = srtmDirectory;
            srtmFileReader.setSRTMDirectory(srtmDirectory);
            if (srtmFileDownloader != null)
                srtmFileDownloader.setSRTMDirectory(srtmDirectory);
            Logging.info("Elevation: Set directory for SRTM files to: " + srtmDirectory.toString());
        } else {
            Logging.error("Elevation: Could not set directory for SRTM files: " + srtmDirectory.toString());
            this.srtmDirectory = null;
        }
    }

    /**
     * Returns the SRTM type that is preferred by this SRTM file provider.
     *
     * @return The preferred SRTM type.
     */
    public SRTMTile.Type getPreferredSRTMType() {
        return preferredSRTMType;
    }

    /**
     * Sets the SRTM type which is preferred by this SRTM tile provider.
     *
     * @param type The SRTM type to be preferred.
     */
    public void setPreferredSRTMType(SRTMTile.Type type) {
        preferredSRTMType = type;
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

        // Not across the Prime Meridian
        if (intLonWest <= intLonEast) {
            for (int lon = intLonWest; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
        // Across the Prime Meridian (+/-180 °C)
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

        // Not across the Prime Meridian
        if (intLonWest <= intLonEast) {
            for (int lon = intLonWest; lon <= intLonEast; lon++) {
                for (int lat = intLatSouth; lat <= intLatNorth; lat++)
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    getSRTMTile(SRTMTile.getTileID(lat, lon));
            }
        }
        // Across the Prime Meridian (+/-180 °C)
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
                File srtmFile = getSRTMFile(srtmTileID, preferredSRTMType);
                // If an SRTM file with the data exists on disk, read it in
                if (srtmFile != null) {
                    Logging.info("Elevation: Caching data of SRTM tile " + srtmTileID + " from file "
                            + srtmFile.getAbsolutePath());
                    // Read the SRTM file as task in a separate thread
                    srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.READING_SCHEDULED);
                    try {
                        getSRTMTile(srtmFile);
                    } catch (RejectedExecutionException e) {
                        Logging.info("Elevation: Execution of file read task for SRTM tile " + srtmTileID
                                + " from file " + srtmFile.getAbsolutePath() + " rejected: " + e.toString());
                        srtmTile = tileCache.remove(srtmTileID);
                    }
                }
                // If auto-downloading of SRTM files is enabled, try to download the missing
                // file
                else if (autoDownloadEnabled) {
                    srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null,
                            SRTMTile.Status.DOWNLOAD_SCHEDULED);
                    try {
                        srtmFileDownloader.downloadSRTMFile(srtmTileID, preferredSRTMType);
                    } catch (RejectedExecutionException e) {
                        Logging.info("Elevation: Execution of download task for SRTM tile " + srtmTileID + " rejected: "
                                + e.toString());
                        srtmFileDownloadFailed(srtmTileID);
                    }
                }
                // Otherwise, put an empty data set with status "file missing" into the cache
                else {
                    srtmTile = tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.FILE_MISSING);
                }
            }
            // If we have a valid tile now, remember it as the previous tile
            if (srtmTile != null && srtmTile.getStatus() == SRTMTile.Status.VALID)
                previousTile = srtmTile;
        }
        return srtmTile;
    }

    /**
     * Determines the SRTM file for the given tile ID. If both, an SRTM1 and an
     * SRTM3 file are available for the given tile ID, the SRTM1 file is preferred.
     *
     * @param srtmTileID The SRTM tile ID.
     * @param preferredSRTMType   The preferred SRTM type (SRTM1 or SRTM3).
     * @return The SRTM file or {@code null} if no file is available for the given
     *         tile ID.
     */
    private File getSRTMFile(String srtmTileID, SRTMTile.Type preferredSRTMType) {
        if (srtmDirectory == null) {
            Logging.error("Elevation: Cannot read SRTM file for tile " + srtmTileID + " as SRTM directory is not set");
            return null;
        }
        Logging.info("Elevation: Looking for on-disk SRTM file for tile ID " + srtmTileID);
        // List the SRTM directory and filter out files that start with the SRTM tile ID
        // https://www.baeldung.com/java-list-directory-files
        Set<File> files = Stream.of(srtmDirectory.listFiles())
                .filter(file -> !file.isDirectory() && file.getName().startsWith(srtmTileID))
                .collect(Collectors.toSet());

        String preferredSRTMFileID = SRTMFiles.SRTM1_ZIP_FILE_ID;
        String nonPreferredSRTMFileID = SRTMFiles.SRTM3_ZIP_FILE_ID;
        if (preferredSRTMType == SRTMTile.Type.SRTM3) {
            preferredSRTMFileID = SRTMFiles.SRTM3_ZIP_FILE_ID;
            nonPreferredSRTMFileID = SRTMFiles.SRTM1_ZIP_FILE_ID;
        }
        File nonPreferredSRTMFile = null;
        for (File file : files) {
            if (file.getName().contains(preferredSRTMFileID)) {
                Logging.info("Elevation: Found preferred on-disk SRTM file " + file.getName());
                return file;
            }
            if (file.getName().contains(nonPreferredSRTMFileID))
                nonPreferredSRTMFile = file;
        }
        if (nonPreferredSRTMFile != null) {
            Logging.info("Elevation: Found non-preferred on-disk SRTM file " + nonPreferredSRTMFile.getName());
            // Download the SRTM file as the preferred type if auto-download enabled
            if (autoDownloadEnabled)
                srtmFileDownloader.downloadSRTMFile(srtmTileID, preferredSRTMType);
        } else
            Logging.info("Elevation: Did not find an on-disk SRTM file for tile ID " + srtmTileID);

        return nonPreferredSRTMFile;
    }

    /**
     * Reads an SRTM tile from a ZIP-compressed SRTM file as obtained from NASA in a
     * separate thread.
     *
     * @param srtmFile The SRTM file to read.
     * @return A {@code Future} from which the read SRTM tile can be obtained.
     * @throws RejectedExecutionException Thrown if the file read task cannot be
     *                                    accepted by the thread executor.
     */
    private Future<SRTMTile> getSRTMTile(File srtmFile) throws RejectedExecutionException {
        Callable<SRTMTile> fileReadTask = () -> {
            String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());
            tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.READING);
            SRTMTile.Type type = SRTMFiles.getSRTMTileTypeFromFileName(srtmFile.getName());
            if (type == null) {
                Logging.error("Elevation: Cannot identify whether file '" + srtmFile.getName()
                        + "' is an SRTM1 or SRTM3 file.");
                return tileCache.putOrUpdateSRTMTile(srtmTileID, type, null, SRTMTile.Status.FILE_INVALID);
            }

            SRTMTile srtmTile = null;
            try {
                srtmTile = tileCache.putOrUpdateSRTMTile(srtmFileReader.readSRTMFile(srtmFile));
                synchronized (listeners) {
                    for (SRTMTileProviderListener listener : listeners)
                        listener.elevationDataAvailable(srtmTile);
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
     * Returns a new SRTM tile grid that is able to provide elevation raster data
     * for the specified bounds. The elevation data is provided regardless of the
     * bounds intersecting one ore multiple SRTM tiles. The only precondition is
     * that the needed SRTM tiles are available or can be loaded.
     *
     * @param bounds The bounds to be covered by the SRTM tile grid.
     * @return The SRTM tile grid.
     */
    public SRTMTileGrid getSRTMTileGrid(Bounds bounds) {
        return new SRTMTileGrid(this, bounds);
    }

    public void setAutoDownloadEnabled(boolean enabled) {
        if (autoDownloadEnabled == enabled)
            return;
        if (enabled) {
            if (srtmDirectory != null) {
                if (srtmFileDownloader == null)
                    try {
                        // Create an SRTM file downloader instance
                        srtmFileDownloader = new SRTMFileDownloader(srtmDirectory);
                        srtmFileDownloader.addDownloadListener(this);
                        // Clear any SRTM tiles marked as missing from the cache so they will be
                        // downloaded, if needed
                        tileCache.cleanAllMissingTiles();
                    } catch (MalformedURLException e) {
                        autoDownloadEnabled = false;
                        Logging.error("Elevation: Cannot enable auto-downloading: " + e.toString());
                        return;
                    }
                else
                    srtmFileDownloader.setSRTMDirectory(srtmDirectory);
                autoDownloadEnabled = true;
                Logging.info("Elevation: Enabled auto-downloading of SRTM files to " + srtmDirectory.toString());
            } else {
                srtmFileDownloader = null;
                autoDownloadEnabled = false;
                Logging.error("Elevation: Cannot enable auto-downloading because directory SRTM directory was not set");
            }
        } else {
            srtmFileDownloader = null;
            autoDownloadEnabled = false;
            Logging.info("Elevation: Disabled auto-downloading of SRTM files due to missing SRTM directory");
        }
    }

    /**
     * Adds a tile provider listener to this SRTM tile provider. All listeners are
     * informed if elevation data for a particular SRTM tile is available.
     *
     * @param listener The listener to add.
     */
    public void addSRTMTileProviderListener(SRTMTileProviderListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes a listener from this SRTM tile provider.
     *
     * @param listener The listener to be removed.
     */
    public void removeSRTMTileProviderListener(SRTMTileProviderListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    @Override
    public void srtmFileDownloadStarted(String srtmTileID) {
        tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOADING);
    }

    @Override
    public void srtmFileDownloadSucceeded(File srtmFile) {
        // Read the SRTM file as task in a separate thread
        try {
            getSRTMTile(srtmFile);
        } catch (RejectedExecutionException e) {
            String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());
            Logging.info("Elevation: Execution of file read task for SRTM tile " + srtmTileID + " from file "
                    + srtmFile.getAbsolutePath() + " rejected: " + e.toString());
            tileCache.remove(srtmTileID);
        }
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID) {
        tileCache.putOrUpdateSRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOAD_FAILED);
    }
}
