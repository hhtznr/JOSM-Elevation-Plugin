package hhtznr.josm.plugins.elevation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.io.Compression;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * Class {@code SRTMFileReader} reads elevation data from SRTM (Shuttle Radar
 * Topography Mission Height) files. It can identify and read ZIP-compressed
 * SRTM1 and SRTM3 files.
 *
 * SRTM1 files are available at
 * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/">SRTM1
 * download page of NASA's Land Processes Distributed Active Archive Center (LP
 * DAAC)</a>.
 *
 * SRTM3 files are available ats
 * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/">SRTM3
 * download page of NASA's Land Processes Distributed Active Archive Center (LP
 * DAAC)</a>.
 *
 * In order to access these files, registration at
 * <a href="https://urs.earthdata.nasa.gov/users/new/">NASA Earthdata Login User
 * Registration</a> and creating an authorization bearer token on this site are
 * required.
 *
 * @author Harald Hetzner
 */
public class SRTMFileReader implements SRTMFileDownloadListener {

    private static SRTMFileReader srtmFileReader = null;

    private File srtmDirectory = null;
    private final HashMap<String, SRTMTile> tileCache = new HashMap<>();
    private SRTMTile previousTile = null;
    private final ExecutorService fileReadExecutor = Executors.newSingleThreadExecutor();

    private SRTMFileDownloader srtmFileDownloader;
    private SRTMTile.Type preferredSRTMType;
    private boolean autoDownloadEnabled = false;

    /**
     * Returns a singleton instance of the SRTM file reader.
     *
     * @return Singleton instance.
     */
    public static SRTMFileReader getInstance() {
        if (srtmFileReader == null)
            srtmFileReader = new SRTMFileReader();
        return srtmFileReader;
    }

    /**
     * Destroys the singleton instance.
     */
    public static void destroyInstance() {
        srtmFileReader = null;
    }

    private SRTMFileReader() {
        this(ElevationPreferences.DEFAULT_SRTM_DIRECTORY);
    }

    private SRTMFileReader(File srtmDirectory) {
        setSrtmDirectory(srtmDirectory);
        preferredSRTMType = SRTMTile.Type.fromName(Config.getPref().get(ElevationPreferences.PREFERRED_SRTM_TYPE,
                ElevationPreferences.DEFAULT_PREFERRED_SRTM_TYPE.getName()));
        boolean autoDownloadEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED);
        setAutoDownloadEnabled(autoDownloadEnabled);
    }

    /**
     * Returns the directory where SRTM files are located.
     *
     * @return The SRTM directory.
     */
    public File getSrtmDirectory() {
        return srtmDirectory;
    }

    /**
     * Sets the directory where SRTM files are located. If the directory does not
     * exist, it is created recursively.
     *
     * @param srtmDirectory The directory to set as SRTM directory.
     */
    public void setSrtmDirectory(File srtmDirectory) {
        if (!srtmDirectory.exists() && srtmDirectory.mkdirs())
            Logging.info("Elevation: Created directory for SRTM files: " + srtmDirectory.toString());
        if (srtmDirectory.isDirectory()) {
            this.srtmDirectory = srtmDirectory;
            if (srtmFileDownloader != null)
                srtmFileDownloader.setSRTMDirectory(srtmDirectory);
            Logging.info("Elevation: Set directory for SRTM files to: " + srtmDirectory.toString());
        } else {
            Logging.error("Elevation: Could not set directory for SRTM files: " + srtmDirectory.toString());
            this.srtmDirectory = null;
        }
    }

    /**
     * Returns the SRTM type that is preferred by this SRTM file reader.
     *
     * @return The preferred SRTM type.
     */
    public SRTMTile.Type getPreferredSRTMType() {
        return preferredSRTMType;
    }

    /**
     * Sets the SRTM type which is preferred by this SRTM file reader.
     *
     * @param type The SRTM type to be preferred.
     */
    public void setPreferredSRTMType(SRTMTile.Type type) {
        preferredSRTMType = type;
    }

    /**
     * Returns the elevation at the provided location, if an appropriate SRTM file
     * is available in the SRTM directory.
     *
     * @param latLon The location at which the elevation is of interest.
     * @return The elevation at the provided location or
     *         {@link SRTMTile#SRTM_DATA_VOID SRTMTile.SRTM_DATA_VOID} if there is a
     *         data void or no SRTM data covering the location is available.
     */
    public short getElevation(ILatLon latLon) {
        String srtmTileID = getSRTMTileID(latLon);
        // Map editing is often done in the very same place for a while.
        // Therefore consider the previous tile first of all.
        SRTMTile srtmTile = previousTile;

        // If there is no previous tile or the previous tile is for another tile ID
        if (srtmTile == null || !srtmTile.getID().equals(srtmTileID)) {

            synchronized (tileCache) {
                srtmTile = tileCache.get(srtmTileID);
                // If the tile is not in the cache, we will load it.
                // Put an empty tile into the cache until it was loaded.
                if (srtmTile == null)
                    tileCache.put(srtmTileID, new SRTMTile(srtmTileID, null, null, SRTMTile.Status.LOADING));
            }

            // Data previously not in cache
            if (srtmTile == null) {
                File srtmFile = getSrtmFile(srtmTileID);
                // If an SRTM file with the data exists locally, read it in
                if (srtmFile != null) {
                    Logging.info("Elevation: Caching data of SRTM tile " + srtmTileID + " from file "
                            + srtmFile.getAbsolutePath());
                    // Read the SRTM file as task in a separate thread
                    fileReadExecutor.submit(new ReadSRTMFileTask(srtmFile));
                }
                // If auto-downloading of SRTM files is enabled, try to download the missing file
                else if (autoDownloadEnabled) {
                    synchronized (tileCache) {
                        tileCache.put(srtmTileID, new SRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOAD_SCHEDULED));
                    }
                    srtmFileDownloader.downloadSRTMFile(srtmTileID, preferredSRTMType);
                }
                // Otherwise, put an empty data set with status "missing" into the cache
                else {
                    synchronized (tileCache) {
                        tileCache.put(srtmTileID, new SRTMTile(srtmTileID, null, null, SRTMTile.Status.MISSING));
                    }
                }
            }
            // If we have a valid tile now, remember it as the previous tile
            if (srtmTile != null && srtmTile.getStatus() == SRTMTile.Status.VALID)
                previousTile = srtmTile;
            else
                return SRTMTile.SRTM_DATA_VOID;
        }
        // Retrieve and return elevation value if SRTM data is valid
        short elevation = srtmTile.getElevation(latLon);
        //Logging.info("Elevation: Elevation for lat = " + latLon.lat() + ", lon = " + latLon.lon() + " from tile " + srtmTileID +": " + elevation + " m");  
        return elevation;
    }

    /**
     * Called by {@code ReadSRTMFileTask} when the SRTM file was read successfully
     * (tile status: valid) or reading failed (tile status: missing).
     *
     * @param srtmTile The SRTM tile that was read.
     */
    private void readSRTMFileTaskCompleted(SRTMTile srtmTile) {
        synchronized (tileCache) {
            tileCache.put(srtmTile.getID(), srtmTile);
        }
    }

    /**
     * Determines the SRTM file for the given tile ID. If both, an SRTM1 and an
     * SRTM3 file are available for the given tile ID, the SRTM1 file is preferred.
     *
     * @param srtmTileID The SRTM tile ID.
     * @return The SRTM file or {@code null} if no file is available for the given
     *         tile ID.
     */
    public File getSrtmFile(String srtmTileID) {
        if (srtmDirectory == null) {
            Logging.error("Elevation: Cannot read SRTM file for tile " + srtmTileID + " as SRTM directory is not set");
            return null;
        }
        Logging.info("Elevation: Looking for local SRTM file for tile ID " + srtmTileID);
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
                Logging.info("Elevation: Found preferred local SRTM file " + file.getName());
                return file;
            }
            if (file.getName().contains(nonPreferredSRTMFileID))
                nonPreferredSRTMFile = file;
        }
        if (nonPreferredSRTMFile != null) {
            Logging.info("Elevation: Found non-preferred local SRTM file " + nonPreferredSRTMFile.getName());
            // Download the SRTM file as the preferred type if auto-download enabled
            if (autoDownloadEnabled)
                srtmFileDownloader.downloadSRTMFile(srtmTileID, preferredSRTMType);
        }
        else
            Logging.info("Elevation: Did not find a local SRTM file for tile ID " + srtmTileID);

        return nonPreferredSRTMFile;
    }

    /**
     * Returns the appropriate SRTM tile ID for the given coordinate. The format of
     * the ID is <code>[N|S]nn[W|E]mmm</code> where <code>nn</code> is the integral
     * latitude without decimal places and <code>mmm</code> is the integral
     * longitude of the south west (lower left) corner of the tile.
     *
     * @param latLon The coordinate to get the SRTM tile ID for.
     * @return The ID of the SRTM tile.
     */
    private static String getSRTMTileID(ILatLon latLon) {
        // Integer part of latitude and longitude
        // https://stackoverflow.com/questions/54629033/cast-to-int-vs-math-floor
        int integerLat = (int) Math.floor(latLon.lat());
        int integerLon = (int) Math.floor(latLon.lon());

        String latPrefix = "N";
        // Negative latitudes are south of the equator
        if (integerLat < 0) {
            latPrefix = "S";
            integerLat = -integerLat;
        }

        String lonPrefix = "E";
        // Negative longitudes are west of the Prime Meridian
        if (integerLon < 0) {
            lonPrefix = "W";
            integerLon = -integerLon;
        }

        return String.format("%s%02d%s%03d", latPrefix, integerLat, lonPrefix, integerLon);
    }

    /**
     * Task class to be executed by a thread executor service for reading an SRTM
     * tile from a ZIP-compressed SRTM file as obtained from NASA.
     *
     * The types SRTM1 and SRTM3 are automatically distinguished based on the file
     * name. If both, an SRTM1 and SRTM3 file, are available for the same tile,
     * SRTM1 takes precedence.
     *
     * <b>Format of an uncompressed SRTM file</b>
     *
     * Source: https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     *
     * <i>Height files have the extension .HGT, and the DEM is provided as two-byte
     * (16-bit) binary signed integer raster data. Two-byte signed integers can
     * range from -32,767 to 32,767 m and can encompass the range of the Earth’s
     * elevations. Header or trailer bytes are not embedded in the file. The data
     * are stored in row major order, meaning all the data for the northernmost row,
     * row 1, are followed by all the data for row 2, and so on.
     *
     * The two-byte data are in Motorola "big-endian" order with the most
     * significant byte first. Most personal computers, and Macintosh computers
     * built after 2006 use Intel ("little-endian") order so byte swapping may be
     * necessary. Some software programs perform the swapping during ingest.
     *
     * Voids in Versions 1.0 and 2.1 are flagged with the value -32,768. There are
     * no voids in Version 3.0.</i>
     */
    private class ReadSRTMFileTask implements Runnable {

        private File srtmFile;

        /**
         * Creates a new SRTM file read task.
         *
         * @param srtmFile The SRTM file to read.
         */
        public ReadSRTMFileTask(File srtmFile) {
            this.srtmFile = srtmFile;
        }

        @Override
        public void run() {
            String srtmTileID = SRTMFiles.getSRTMTileIDFromFileName(srtmFile.getName());
            SRTMTile.Type type = SRTMFiles.getSRTMTileTypeFromFileName(srtmFile.getName());
            Logging.info("Elevation: Reading SRTM file '" + srtmFile.getName() + "' for tile ID " + srtmTileID);
            if (type == null) {
                Logging.error(
                        "Elevation: Cannot identify if file '" + srtmFile.getName() + "' is an SRTM1 or SRTM3 file.");
                SRTMFileReader.this
                        .readSRTMFileTaskCompleted(new SRTMTile(srtmTileID, type, null, SRTMTile.Status.MISSING));
                return;
            }

            // Expected number of elevation data points
            int srtmTileSize;
            if (type == SRTMTile.Type.SRTM1)
                srtmTileSize = SRTMTile.SRTM1_TILE_LENGTH * SRTMTile.SRTM1_TILE_LENGTH;
            else
                srtmTileSize = SRTMTile.SRTM3_TILE_LENGTH * SRTMTile.SRTM3_TILE_LENGTH;

            // Expected number of bytes in the SRTM file (number of data points * 2 bytes
            // per short).
            int bytesExpected = srtmTileSize * 2;

            short[] elevationData = null;

            try (InputStream inputStream = Compression.getUncompressedFileInputStream(srtmFile.toPath())) {
                // https://www.baeldung.com/convert-input-stream-to-array-of-bytes
                ByteBuffer byteBuffer = ByteBuffer.allocate(bytesExpected);
                while (inputStream.available() > 0) {
                    int b = inputStream.read();
                    // EOF
                    if (b == -1)
                        break;
                    if (byteBuffer.position() >= bytesExpected) {
                        Logging.error("Elevation: SRTM file '" + srtmFile.getName()
                                + "' contains more bytes than expected. Expected: " + bytesExpected + " bytes");
                        return;
                    }
                    byteBuffer.put((byte) b);
                }
                int bytesRead = byteBuffer.position();

                if (bytesRead != bytesExpected) {
                    Logging.error("Elevation: Wrong number of bytes in SRTM file '" + srtmFile.getName()
                            + "'. Expected: " + bytesExpected + " bytes; read: " + bytesRead + " bytes");
                    SRTMFileReader.this
                            .readSRTMFileTaskCompleted(new SRTMTile(srtmTileID, type, null, SRTMTile.Status.MISSING));
                }

                // Convert byte order
                byteBuffer.order(ByteOrder.BIG_ENDIAN);

                elevationData = new short[srtmTileSize];
                for (int index = 0; index * 2 < bytesRead; index++)
                    elevationData[index] = byteBuffer.getShort(index * 2);
            } catch (IOException e) {
                Logging.error("Elevation: Exception reading SRTM file '" + srtmFile.getName() + "': " + e.toString());
                return;
            }
            SRTMFileReader.this
                    .readSRTMFileTaskCompleted(new SRTMTile(srtmTileID, type, elevationData, SRTMTile.Status.VALID));
        }
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
                        synchronized (tileCache) {
                            for (String srtmTileID : tileCache.keySet()) {
                                SRTMTile srtmTile = tileCache.get(srtmTileID);
                                if (srtmTile.getStatus() == SRTMTile.Status.MISSING)
                                    tileCache.remove(srtmTileID);
                            }
                        }
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

    @Override
    public void srtmFileDownloadStarted(String srtmTileID) {
        synchronized (tileCache) {
            tileCache.put(srtmTileID, new SRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOADING));
        }
    }

    @Override
    public void srtmFileDownloadSucceeded(File srtmFile) {
        // Read the SRTM file as task in a separate thread
        fileReadExecutor.submit(new ReadSRTMFileTask(srtmFile));
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID) {
        synchronized (tileCache) {
            tileCache.put(srtmTileID, new SRTMTile(srtmTileID, null, null, SRTMTile.Status.DOWNLOAD_FAILED));
        }
    }
}
