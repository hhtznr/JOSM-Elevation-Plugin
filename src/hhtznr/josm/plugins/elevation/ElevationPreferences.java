package hhtznr.josm.plugins.elevation;

import java.io.File;
import java.nio.file.Paths;

import org.openstreetmap.josm.data.Preferences;

import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Property keys and default values for elevation data preferences.
 *
 * @author Harald Hetzner
 */
public class ElevationPreferences {

    /**
     * Property key for enabling or disabling use of elevation data.
     */
    public static final String ELEVATION_ENABLED = "elevation.enabled";

    /**
     * Property key for preferred SRTM type.
     */
    public static final String PREFERRED_SRTM_TYPE = "elevation.srtm.type.preferred";

    /**
     * Property key for defining the elevation interpolation method.
     */
    public static final String ELEVATION_INTERPOLATION = "elevation.interpolation";

    /**
     * Property key for size limit of the in-memory SRTM tile cache.
     */
    public static final String RAM_CACHE_SIZE_LIMIT = "elevation.cache.ram.size.limit";

    /**
     * Property key for enabling or disabling automatic download of elevation data.
     */
    public static final String ELEVATION_AUTO_DOWNLOAD_ENABLED = "elevation.autodownload";

    /**
     * Property key for authentication bearer token for SRTM server.
     */
    public static final String ELEVATION_SERVER_AUTH_BEARER = "elevation.srtm.server.auth.bearer";

    /**
     * Default property value for enabling use of elevation data: {@code true}.
     */
    public static final boolean DEFAULT_ELEVATION_ENABLED = true;

    /**
     * Default property value for the preferred SRTM type.
     */
    public static final SRTMTile.Type DEFAULT_PREFERRED_SRTM_TYPE = SRTMTile.Type.SRTM1;

    /**
     * Default property value of the elevation interpolation method.
     */
    public static final SRTMTile.Interpolation DEFAULT_ELEVATION_INTERPOLATION = SRTMTile.Interpolation.BILINEAR;

    /**
     * Default property value for the size limit of the in-memory SRTM tile cache in
     * MiB: 128.
     */
    public static final int DEFAULT_RAM_CACHE_SIZE_LIMIT = 128;

    /**
     * Minimum size limit of the in-memory SRTM tile cache in MiB: 32.
     */
    public static final int MIN_RAM_CACHE_SIZE_LIMIT = 32;

    /**
     * Maximum size limit of the in-memory SRTM tile cache in MiB: 8192.
     */
    public static final int MAX_RAM_CACHE_SIZE_LIMIT = 8192;

    /**
     * Increment of the size limit of the in-memory SRTM tile cache used by the
     * spinner in the elevation preferences in MiB: 1.
     */
    public static final int INCR_RAM_CACHE_SIZE_LIMIT = 1;

    /**
     * Default property value for authentication bearer token for SRTM HGT server:
     * Empty {@code String}.
     */
    public static final String DEFAULT_ELEVATION_SERVER_AUTH_BEARER = "";

    /**
     * Default property value for enabling automatic download of elevation data:
     * {@code false}.
     */
    public static final boolean DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED = false;

    /**
     * URL of <a href="https://urs.earthdata.nasa.gov/users/new/">NASA Earthdata
     * Login User Registration</a> where users need to register and create an
     * authorization bearer token in order to download elevation data.
     */
    public static final String SRTM_SERVER_REGISTRATION_URL = "https://urs.earthdata.nasa.gov/users/new/";

    /**
     * Default path, where elevation data is stored.
     */
    public static final File DEFAULT_ELEVATION_DIRECTORY = Paths
            .get(Preferences.main().getDirs().getCacheDirectory(true).toString(), "elevation").toFile();

    /**
     * Default path, where SRTM1 and SRTM3 files need to be located.
     */
    public static final File DEFAULT_SRTM_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(), "SRTM")
            .toFile();

    /**
     * URL of
     * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/">NASA's
     * Land Processes Distributed Active Archive Center (LP DAAC)</a> where SRTM1
     * files can be downloaded.
     *
     * Requires registration at
     * {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL}.
     */
    public static final String SRTM1_SERVER_BASE_URL = "https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/";

    /**
     * URL of
     * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/">NASA's
     * Land Processes Distributed Active Archive Center (LP DAAC)</a> where SRTM3
     * files can be downloaded.
     *
     * Requires registration at
     * {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL}.
     */
    public static final String SRTM3_SERVER_BASE_URL = "https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/";

    private ElevationPreferences() {
    }
}
