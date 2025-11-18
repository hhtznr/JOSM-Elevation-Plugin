package hhtznr.josm.plugins.elevation;

import java.awt.Color;
import java.io.File;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.Authenticator.RequestorType;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuth20Exception;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.io.auth.CredentialsAgent;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

/**
 * Property keys and default values for elevation data preferences.
 *
 * @author Harald Hetzner
 */
public class ElevationPreferences {

    /**
     * Former property key for enabling or disabling use of elevation data. It was
     * available until version 0.13.2.
     *
     * @since 0.14.0
     */
    public static final String LEGACY_ELEVATION_ENABLED = "elevation.enabled";

    /**
     * Default path, where elevation data is stored.
     */
    public static final File DEFAULT_ELEVATION_DIRECTORY = Paths
            .get(Preferences.main().getDirs().getCacheDirectory(true).toString(), "elevation").toFile();

    /**
     * Default path, where SRTM1 and SRTM3 files were located until version 0.10.2.
     *
     * @since 0.11.0
     */
    public static final File LEGACY_SRTM_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(), "SRTM").toFile();

    /**
     * Default path, where SRTM files from NASA Earthdata are stored in separate
     * directories for SRTM1 and SRTM3.
     */
    public static final File DEFAULT_EARTHDATA_DIRECTORY = Paths
            .get(DEFAULT_ELEVATION_DIRECTORY.toString(), "Earthdata").toFile();

    /**
     * Default path, where SRTM1 files from NASA Earthdata are stored locally.
     */
    public static final File DEFAULT_EARTHDATA_SRTM1_DIRECTORY = Paths
            .get(DEFAULT_EARTHDATA_DIRECTORY.toString(), "SRTM1").toFile();

    /**
     * Default path, where SRTM3 files from NASA Earthdata are stored locally.
     */
    public static final File DEFAULT_EARTHDATA_SRTM3_DIRECTORY = Paths
            .get(DEFAULT_EARTHDATA_DIRECTORY.toString(), "SRTM3").toFile();

    /**
     * Default path, where SRTM-compatible HGT files from Sonny's LiDAR Digital
     * Terrain Models of Europe are stored in separate directories for DTM1 and
     * DTM3.
     */
    public static final File DEFAULT_SONNY_LIDAR_DIRECTORY = Paths
            .get(DEFAULT_ELEVATION_DIRECTORY.toString(), "Sonny LiDAR").toFile();

    /**
     * Default path, where DTM1 files from Sonny's LiDAR Digital Terrain Models of
     * Europe are stored locally.
     */
    public static final File DEFAULT_SONNY_LIDAR_DTM1_DIRECTORY = Paths
            .get(DEFAULT_SONNY_LIDAR_DIRECTORY.toString(), "DTM1").toFile();

    /**
     * Default path, where DTM3 files from Sonny's LiDAR Digital Terrain Models of
     * Europe are stored locally.
     */
    public static final File DEFAULT_SONNY_LIDAR_DTM3_DIRECTORY = Paths
            .get(DEFAULT_SONNY_LIDAR_DIRECTORY.toString(), "DTM3").toFile();

    /**
     * Property key for SRTM type.
     */
    public static final String SRTM_TYPE = "elevation.srtm.type.preferred";

    /**
     * Default property value for the SRTM type.
     */
    private static final SRTMTile.Type DEFAULT_SRTM_TYPE = SRTMTile.Type.SRTM1;

    /**
     * Property key for defining the elevation interpolation method.
     */
    public static final String ELEVATION_INTERPOLATION = "elevation.interpolation";

    /**
     * Default property value of the elevation interpolation method.
     */
    private static final SRTMTile.Interpolation DEFAULT_ELEVATION_INTERPOLATION = SRTMTile.Interpolation.BILINEAR;

    /**
     * Property key for size limit of the in-memory SRTM tile cache.
     */
    public static final String RAM_CACHE_SIZE_LIMIT = "elevation.cache.ram.size.limit";

    /**
     * Default property value for the size limit of the in-memory SRTM tile cache in
     * MiB: 128.
     */
    private static final int DEFAULT_RAM_CACHE_SIZE_LIMIT = 128;

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
     * Property key for enabling or disabling elevation visualization layer.
     */
    public static final String ELEVATION_LAYER_ENABLED = "elevation.layer.enabled";

    /**
     * Default property value for enabling the elevation visualization layer:
     * {@code true}.
     */
    private static final boolean DEFAULT_ELEVATION_LAYER_ENABLED = true;

    /**
     * Property key for the limit value of arc degrees in latitude or longitude
     * covered by the map view, where, if exceeded, elevation data caching as well
     * as contour line and hillshade rendering will be temporarily switched off.
     */
    public static final String ELEVATION_LAYER_RENDERING_LIMIT = "elevation.layer.rendering.limit";

    /**
     * Default property value for the limit value of arc degrees in latitude or
     * longitude covered by the map view, where, if exceeded, elevation data caching
     * as well as contour line and hillshade rendering will be temporarily switched
     * off: {@code 0.1}.
     */
    private static final double DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT = 0.1;

    /**
     * Minimum value of the elevation layer rendering limit: {@code 0.01}.
     */
    public static final double MIN_ELEVATION_LAYER_RENDERING_LIMIT = 0.01;

    /**
     * Maximum value of the elevation layer rendering limit: {@code 2.0}.
     */
    public static final double MAX_ELEVATION_LAYER_RENDERING_LIMIT = 2.0;

    /**
     * Increment of the elevation layer rendering limit: {@code 0.01}.
     */
    public static final double INCR_ELEVATION_LAYER_RENDERING_LIMIT = 0.01;

    /**
     * Property key for enabling or disabling contour line rendering.
     */
    public static final String ELEVATION_CONTOUR_LINES_ENABLED = "elevation.layer.contour-lines.enabled";

    /**
     * Default property value for enabling contour line rendering: {@code true}.
     */
    private static final boolean DEFAULT_ELEVATION_CONTOUR_LINES_ENABLED = true;

    /**
     * Property key for step between adjacent elevation contour lines.
     */
    public static final String CONTOUR_LINE_ISOSTEP = "elevation.contour-line.isostep";

    /**
     * Default property value of the isostep between two adjacent elevation contour
     * lines: {@code 10}.
     */
    private static final int DEFAULT_CONTOUR_LINE_ISOSTEP = 10;

    /**
     * Minimum value of the contour line isostep: {@code 5}.
     */
    public static final int MIN_CONTOUR_LINE_ISOSTEP = 5;

    /**
     * Maximum value of the contour line isostep: {@code 200}.
     */
    public static final int MAX_CONTOUR_LINE_ISOSTEP = 200;

    /**
     * Increment of the contour line isostep: {@code 5}.
     */
    public static final int INCR_CONTOUR_LINE_ISOSTEP = 5;

    /**
     * Property key for width of the stroke for drawing of contour lines.
     */
    public static final String CONTOUR_LINE_STROKE_WIDTH = "elevation.contour-line.stroke.width";

    /*
     * Default property value of the contour line stroke width.
     */
    private static final float DEFAULT_CONTOUR_LINE_STROKE_WIDTH = 1.0f;

    /**
     * Minimum value of the contour line stroke width: {@code 0.1}.
     */
    public static final float MIN_CONTOUR_LINE_STROKE_WIDTH = 0.1f;

    /**
     * Maximum value of the contour line stroke width: {@code 10.0}.
     */
    public static final float MAX_CONTOUR_LINE_STROKE_WIDTH = 10.0f;

    /**
     * Increment of the contour line stroke width: {@code 0.05}.
     */
    public static final float INCR_CONTOUR_LINE_STROKE_WIDTH = 0.05f;

    /**
     * Property key for color of contour lines.
     */
    public static final String CONTOUR_LINE_COLOR = "elevation.contour-line.color";

    /*
     * Default property value of the contour line color as HTML color.
     */
    private static final String DEFAULT_CONTOUR_LINE_COLOR = "#D2B473";

    /**
     * Property key for enabling or disabling hillshade rendering.
     */
    public static final String ELEVATION_HILLSHADE_ENABLED = "elevation.layer.hillshade.enabled";

    /**
     * Default property value for enabling hillshade rendering: {@code true}.
     */
    private static final boolean DEFAULT_ELEVATION_HILLSHADE_ENABLED = true;

    /**
     * Property key for the altitude of the illumination source in hillshade
     * computation.
     */
    public static final String HILLSHADE_ALTITUDE = "elevation.hillshade.altitude";

    /**
     * Default property value of the altitude (degrees) of the illumination source
     * in hillshade computation: {@code 45}.
     */
    private static final int DEFAULT_HILLSHADE_ALTITUDE = 45;

    /**
     * Minimum value of the hillshade illumination source altitude: {@code 0}.
     */
    public static final int MIN_HILLSHADE_ALTITUDE = 0;

    /**
     * Maximum value of the hillshade illumination source altitude: {@code 90}.
     */
    public static final int MAX_HILLSHADE_ALTITUDE = 90;

    /**
     * Increment of the hillshade illumination source altitude: {@code 1}.
     */
    public static final int INCR_HILLSHADE_ALTITUDE = 1;

    /**
     * Property key for the azimuth of the illumination source in hillshade
     * computation.
     */
    public static final String HILLSHADE_AZIMUTH = "elevation.hillshade.azimuth";

    /**
     * Default property value of the azimuth (degrees) of the illumination source in
     * hillshade computation: {@code 315} (NW).
     */
    private static final int DEFAULT_HILLSHADE_AZIMUTH = 315;

    /**
     * Minimum value of the hillshade illumination source azimuth: {@code 0}.
     */
    public static final int MIN_HILLSHADE_AZIMUTH = 0;

    /**
     * Maximum value of the hillshade illumination source azimuth: {@code 360}.
     */
    public static final int MAX_HILLSHADE_AZIMUTH = 360;

    /**
     * Increment of the hillshade illumination source azimuth: {@code 1}.
     */
    public static final int INCR_HILLSHADE_AZIMUTH = 1;

    public static final int DEFAULT_LOWER_CUTOFF_ELEVATION = -999;

    public static final int DEFAULT_UPPER_CUTOFF_ELEVATION = 9999;

    /**
     * Property key for enabling or disabling rendering of the elevation data point
     * raster.
     */
    public static final String ELEVATION_RASTER_ENABLED = "elevation.layer.raster.enabled";

    /**
     * Default property value for enabling rendering of the elevation data point
     * raster: {@code false}.
     */
    private static final boolean DEFAULT_ELEVATION_RASTER_ENABLED = false;

    /**
     * Property key for enabling or disabling rendering of the lowest and highest
     * points within the map view
     */
    public static final String LOWEST_AND_HIGHEST_POINTS_ENABLED = "elevation.layer.lowest_highest_points.enabled";

    /**
     * Default property value for enabling rendering of the lowest and highest
     * points within the map view: {@code false}.
     */
    private static final boolean DEFAULT_LOWEST_AND_HIGHEST_POINTS_ENABLED = false;

    /**
     * Property key for enabling or disabling automatic download of elevation data.
     */
    public static final String ELEVATION_AUTO_DOWNLOAD_ENABLED = "elevation.autodownload";

    /**
     * Default property value for enabling automatic download of elevation data:
     * {@code false}.
     */
    private static final boolean DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED = false;

    /**
     * Legacy property key for authentication bearer token for Earthdata SRTM
     * server.
     */
    public static final String ELEVATION_SERVER_AUTH_BEARER = "elevation.srtm.server.auth.bearer";

    /**
     * Property key for the type of authentication at the elevation data download
     * server.
     */
    public static final String ELEVATION_SERVER_AUTH_TYPE = "elevation.srtm.server.auth.type";

    /**
     * Default property value of the type of authentication at the elevation data
     * download server.
     */
    public static final SRTMFileDownloader.AuthType DEFAULT_ELEVATION_SERVER_AUTH_TYPE = SRTMFileDownloader.AuthType.BEARER_TOKEN;

    /**
     * Host name of the NASA Earthdata single sign-on server.
     *
     * See <a href=
     * "https://urs.earthdata.nasa.gov/documentation/for_users/sso_process_overview">Earthdata
     * Login Documentation</a>.
     */
    public static final String EARTHDATA_SSO_HOST = "urs.earthdata.nasa.gov";

    /**
     * Host name of the NASA Earthdata download server.
     */
    public static final String EARTHDATA_DOWNLOAD_HOST = "e4ftl01.cr.usgs.gov";

    /**
     * URL of <a href="https://urs.earthdata.nasa.gov/users/new/">NASA Earthdata
     * Login User Registration</a> where users need to register and create an
     * authorization bearer token in order to download elevation data.
     */
    public static final String SRTM_SERVER_REGISTRATION_URL = "https://" + EARTHDATA_SSO_HOST + "/users/new/";

    /**
     * URL of
     * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/">NASA's
     * Land Processes Distributed Active Archive Center (LP DAAC)</a> where SRTM1
     * files can be downloaded.
     *
     * Requires registration at
     * {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL}.
     */
    public static final URL SRTM1_SERVER_BASE_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://" + EARTHDATA_DOWNLOAD_HOST + "/MEASURES/SRTMGL1.003/2000.02.11/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM1_SERVER_BASE_URL = url;
    }

    /**
     * URL of
     * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/">NASA's
     * Land Processes Distributed Active Archive Center (LP DAAC)</a> where SRTM3
     * files can be downloaded.
     *
     * Requires registration at
     * {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL}.
     */
    public static final URL SRTM3_SERVER_BASE_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://" + EARTHDATA_DOWNLOAD_HOST + "/MEASURES/SRTMGL3.003/2000.02.11/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM3_SERVER_BASE_URL = url;
    }

    public static final URL SONNY_LIDAR_DTM1_BASE_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://bit.ly/dtm-europe-1s-v2");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SONNY_LIDAR_DTM1_BASE_URL = url;
    }

    public static final URL SONNY_LIDAR_DTM3_BASE_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://bit.ly/dtm-europe-3s-v2");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SONNY_LIDAR_DTM3_BASE_URL = url;
    }

    /**
     * Data source object for NASA Earthdata SRTM1.
     */
    public static final ElevationDataSource ELEVATION_DATA_SOURCE_EARTHDATA_SRTM1 = new ElevationDataSource(
            "NASA Earthdata SRTM1", DEFAULT_EARTHDATA_SRTM1_DIRECTORY, SRTMTile.Type.SRTM1, SRTM1_SERVER_BASE_URL,
            EARTHDATA_SSO_HOST, true);

    /**
     * Data source object for NASA Earthdata SRTM3.
     */
    public static final ElevationDataSource ELEVATION_DATA_SOURCE_EARTHDATA_SRTM3 = new ElevationDataSource(
            "NASA Earthdata SRTM3", DEFAULT_EARTHDATA_SRTM3_DIRECTORY, SRTMTile.Type.SRTM3, SRTM3_SERVER_BASE_URL,
            EARTHDATA_SSO_HOST, true);

    /*
     * Data source object for Sonny's LiDAR DTM1.
     */
    public static final ElevationDataSource ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM1 = new ElevationDataSource(
            "Sonny's LiDAR DTM1", DEFAULT_SONNY_LIDAR_DTM1_DIRECTORY, SRTMTile.Type.SRTM1, SONNY_LIDAR_DTM1_BASE_URL,
            null, false);

    /*
     * Data source object for Sonny's LiDAR DTM3.
     */
    public static final ElevationDataSource ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM3 = new ElevationDataSource(
            "Sonny's LiDAR DTM3", DEFAULT_SONNY_LIDAR_DTM3_DIRECTORY, SRTMTile.Type.SRTM3, SONNY_LIDAR_DTM3_BASE_URL,
            null, false);

    /**
     * List of elevation data sources for SRM1 data.
     */
    public static final List<ElevationDataSource> ELEVATION_DATA_SOURCES_SRTM1 = new LinkedList<>();
    static {
        ELEVATION_DATA_SOURCES_SRTM1.add(ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM1);
        ELEVATION_DATA_SOURCES_SRTM1.add(ELEVATION_DATA_SOURCE_EARTHDATA_SRTM1);
    }

    /**
     * List of elevation data sources for SRTM3 data.
     */
    public static final List<ElevationDataSource> ELEVATION_DATA_SOURCES_SRTM3 = new LinkedList<>();
    static {
        ELEVATION_DATA_SOURCES_SRTM3.add(ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM3);
        ELEVATION_DATA_SOURCES_SRTM3.add(ELEVATION_DATA_SOURCE_EARTHDATA_SRTM3);
    }

    /**
     * Returns the SRTM type.
     *
     * @return The SRTM type, {@code SRTM1} or {@code SRTM3}.
     */
    public static SRTMTile.Type getSRTMType() {
        return SRTMTile.Type.fromString(Config.getPref().get(SRTM_TYPE, DEFAULT_SRTM_TYPE.toString()));
    }

    /**
     * Returns the elevation interpolation method.
     *
     * @return The method for interpolation of elevation values.
     */
    public static SRTMTile.Interpolation getElevationInterpolation() {
        return SRTMTile.Interpolation
                .fromString(Config.getPref().get(ELEVATION_INTERPOLATION, DEFAULT_ELEVATION_INTERPOLATION.toString()));
    }

    /**
     * Returns the size limit of the in-memory SRTM tile cache.
     *
     * @return Size limit of the in-memory SRTM tile cache.
     */
    public static int getRAMCacheSizeLimit() {
        return Config.getPref().getInt(RAM_CACHE_SIZE_LIMIT, DEFAULT_RAM_CACHE_SIZE_LIMIT);
    }

    /**
     * Returns whether the elevation visualization layer is enabled or disabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getElevationLayerEnabled() {
        return Config.getPref().getBoolean(ELEVATION_LAYER_ENABLED, DEFAULT_ELEVATION_LAYER_ENABLED);
    }

    /**
     * Returns the limit value for rendering of the elevation visualization layer.
     *
     * @return The limit value of arc degrees in latitude or longitude covered by
     *         the map view, where, if exceeded, elevation data caching as well as
     *         contour line and hillshade rendering will be temporarily switched
     *         off.
     */
    public static double getElevationLayerRenderingLimit() {
        return Config.getPref().getDouble(ELEVATION_LAYER_RENDERING_LIMIT, DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT);
    }

    /**
     * Returns whether rendering of elevation contour lines is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getContourLinesEnabled() {
        return Config.getPref().getBoolean(ELEVATION_CONTOUR_LINES_ENABLED, DEFAULT_ELEVATION_CONTOUR_LINES_ENABLED);
    }

    /**
     * Returns the step between adjacent elevation contour lines.
     *
     * @return The step between adjacent elevation contour lines.
     */
    public static int getContourLineIsostep() {
        return Config.getPref().getInt(CONTOUR_LINE_ISOSTEP, DEFAULT_CONTOUR_LINE_ISOSTEP);
    }

    /**
     * Returns the contour line stroke width.
     *
     * @return The width of the stroke for drawing of contour lines in px.
     */
    public static float getContourLineStrokeWidth() {
        return (float) Config.getPref().getDouble(CONTOUR_LINE_STROKE_WIDTH, DEFAULT_CONTOUR_LINE_STROKE_WIDTH);
    }

    /**
     * Returns the contour line color.
     *
     * @return The color of contour lines.
     */
    public static Color getContourLineColor() {
        String htmlColor = Config.getPref().get(CONTOUR_LINE_COLOR, DEFAULT_CONTOUR_LINE_COLOR);
        return ColorHelper.html2color(htmlColor);
    }

    /**
     * Sets the value of the contour line color in the settings.
     *
     * @param color The color to set as contour line color in the settings.
     * @return {@code true} if the color value has changed.
     */
    public static boolean putContourLineColor(Color color) {
        String htmlColor = ColorHelper.color2html(color, false);
        return Config.getPref().put(CONTOUR_LINE_COLOR, htmlColor);
    }

    /**
     * Returns whether rendering of elevation hillshade is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getHillshadeEnabled() {
        return Config.getPref().getBoolean(ELEVATION_HILLSHADE_ENABLED, DEFAULT_ELEVATION_HILLSHADE_ENABLED);
    }

    /**
     * Returns the altitude of the hillshade illumination source.
     *
     * @return The altitude of the illumination source in hillshade computation.
     */
    public static int getHillshadeAltitude() {
        return Config.getPref().getInt(HILLSHADE_ALTITUDE, DEFAULT_HILLSHADE_ALTITUDE);
    }

    /**
     * Returns the azimuth of the hillshade illumination source.
     *
     * @return The azimuth of the illumination source in hillshade computation.
     */
    public static int getHillshadeAzimuth() {
        return Config.getPref().getInt(HILLSHADE_AZIMUTH, DEFAULT_HILLSHADE_AZIMUTH);
    }

    /**
     * Returns whether rendering of the elevation raster is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getElevationRasterEnabled() {
        return Config.getPref().getBoolean(ELEVATION_RASTER_ENABLED, DEFAULT_ELEVATION_RASTER_ENABLED);
    }

    /**
     * Returns whether rendering of the lowest and highest points within the map
     * view is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getLowestAndHighestPointsEnabled() {
        return Config.getPref().getBoolean(LOWEST_AND_HIGHEST_POINTS_ENABLED,
                DEFAULT_LOWEST_AND_HIGHEST_POINTS_ENABLED);
    }

    /**
     * Returns whether auto-download of SRTM tiles is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getAutoDownloadEnabled() {
        return Config.getPref().getBoolean(ELEVATION_AUTO_DOWNLOAD_ENABLED, DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED);
    }

    /**
     * Returns the type of authentication at the elevation data download server.
     *
     * @return The type of authentication at the elevation data download server.
     */
    public static SRTMFileDownloader.AuthType getElevationServerAuthType() {
        return SRTMFileDownloader.AuthType.fromString(
                Config.getPref().get(ELEVATION_SERVER_AUTH_TYPE, DEFAULT_ELEVATION_SERVER_AUTH_TYPE.toString()));
    }

    /**
     * Lookup the Earthdata user name and password in the preferences.
     *
     * @return The Eearthdata user name and password or {@code null} if these could
     *         not be retrieved from the preferences.
     */
    public static PasswordAuthentication lookupEarthdataCredentials() {
        CredentialsAgent cm = CredentialsManager.getInstance();
        try {
            PasswordAuthentication pa = cm.lookup(RequestorType.SERVER, EARTHDATA_SSO_HOST);
            return pa;
        } catch (CredentialsAgentException e) {
            Logging.error(e);
            Logging.warn("Failed to retrieve Earthdata credentials from credential manager.");
            Logging.warn("Current credential manager is of type ''{0}''", cm.getClass().getName());
            return null;
        }
    }

    /**
     * Stores the provided Earthdata credentials in the JOSM preferences if the user
     * name is not blank and the password is not empty.
     *
     * @param userName The Earthdata user name.
     * @param password The Earthdata password.
     * @return {@code PasswordAuthentication} which was stored or {@code null} if
     *         the provided user name is blank, the password is empty or an
     *         exception occurred when trying to store the credentials.
     */
    public static PasswordAuthentication storeEarthdataCredentials(String userName, char[] password) {
        if (userName.trim().isBlank())
            return null;
        if (password.length < 1)
            return null;
        CredentialsAgent cm = CredentialsManager.getInstance();
        try {
            PasswordAuthentication pa = new PasswordAuthentication(userName.trim(), password);
            cm.store(RequestorType.SERVER, EARTHDATA_SSO_HOST, pa);
            return pa;
        } catch (CredentialsAgentException e) {
            Logging.error(e);
            Logging.warn("Elevation: Failed to save Earthdata credentials to credential manager.");
            Logging.warn("Elevation: Current credential manager is of type ''{0}''", cm.getClass().getName());
            return null;
        }
    }

    /**
     * Removes stored Earthdata credentials from JOSM by setting them to empty
     * strings.
     *
     * @return {@code true} if removing the credentials succeeded or no credentials
     *         were stored.
     */
    public static boolean removeEarthdataCredentials() {
        PasswordAuthentication existingCredentials = lookupEarthdataCredentials();
        if (existingCredentials == null)
            return true;
        CredentialsAgent cm = CredentialsManager.getInstance();
        try {
            PasswordAuthentication pa = new PasswordAuthentication("", new char[0]);
            cm.store(RequestorType.SERVER, EARTHDATA_SSO_HOST, pa);
            return true;
        } catch (CredentialsAgentException e) {
            Logging.error(e);
            Logging.warn("Elevation: Failed to save Earthdata credentials to credential manager.");
            Logging.warn("Elevation: Current credential manager is of type ''{0}''", cm.getClass().getName());
            return false;
        }
    }

    /**
     * Lookup the Earthdata OAuth token in the preferences.
     *
     * @return The OAuth 2.0 token holding the Earthdata authorization bearer token
     *         or {@code null} if it could not be retrieved from the preferences.
     */
    public static OAuth20Token lookupEarthdataOAuthToken() {
        try {
            IOAuthToken authToken = CredentialsManager.getInstance().lookupOAuthAccessToken(EARTHDATA_DOWNLOAD_HOST);

            // Legacy support (bearer stored as normal preferences parameter)
            if (authToken == null) {
                String bearer = Config.getPref().get(ELEVATION_SERVER_AUTH_BEARER, null);
                if (bearer == null || bearer.isBlank())
                    return null;
                Logging.info("Elevation: Migrating authorization bearer token to JOSM OAuth 2.0 format");
                bearer = bearer.trim();
                OAuth20Token oAuthToken = storeEarthdataOAuthToken(bearer);
                Config.getPref().put(ELEVATION_SERVER_AUTH_BEARER, null);
                return oAuthToken;
            }

            if (authToken instanceof OAuth20Token)
                return (OAuth20Token) authToken;

            return null;
        } catch (CredentialsAgentException e) {
            Logging.error("Elevation: " + e);
            return null;
        }
    }

    /**
     * Stores the provided Earthdata authorization bearer token in the JOSM
     * preferences if it is not {@code null} or blank.
     *
     * @param bearerToken The Earthdata authorization bearer token.
     * @return The {@code OAuth20Token} object which was stored or {@code null} if
     *         the provided bearer token is {@code null} or blank or an
     *         {@code OAuth20Exception} should have occurred when trying to create
     *         the object.
     */
    public static OAuth20Token storeEarthdataOAuthToken(String bearerToken) {
        OAuth20Token oAuthToken = createEarthdataOAuthToken(bearerToken);
        if (oAuthToken != null) {
            try {
                CredentialsManager.getInstance().storeOAuthAccessToken(EARTHDATA_DOWNLOAD_HOST, oAuthToken);
            } catch (CredentialsAgentException e) {
                Logging.error("Elevation: " + e);
            }
        }
        return oAuthToken;
    }

    /**
     * Removes the Earthdata authorization bearer token from JOSM.
     *
     * @return {@code true} if removing the token succeeded or no token was stored.
     */
    public static boolean removeEarthdataOAuthToken() {
        OAuth20Token existingToken = lookupEarthdataOAuthToken();
        if (existingToken != null) {
            try {
                CredentialsManager.getInstance().storeOAuthAccessToken(EARTHDATA_DOWNLOAD_HOST, null);
            } catch (CredentialsAgentException e) {
                Logging.error("Elevation: " + e);
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a JOSM {@code OAuth20Token} containing the authorization bearer token
     * obtained in the {@code Generate Token} tab at
     * <a href="https://urs.earthdata.nasa.gov/home">Earthdata Login</a>.
     *
     * @param bearerToken The Earthdata authorization bearer token.
     * @return The {@code OAuth20Token} object or {@code null} if the provided
     *         bearer token is {@code null} or blank or an {@code OAuth20Exception}
     *         should have occurred when trying to create the object.
     */
    private static OAuth20Token createEarthdataOAuthToken(String bearerToken) {
        // Either client ID or bearer token must be non-null/non-blank
        if (bearerToken == null || bearerToken.isBlank())
            return null;
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add("token_type", "bearer");
        jsonObjectBuilder.add("access_token", bearerToken);
        String json = jsonObjectBuilder.build().toString();

        String earthDataURS = "https://" + EARTHDATA_SSO_HOST + "/";
        String earthDataDL = "https://" + EARTHDATA_DOWNLOAD_HOST + "/";
        // Earthdata neither provides us a client ID nor a client secret
        String clientId = "";
        String clientSecret = null;
        // Following two URLs are set because the parameters may not be null, but are
        // never used
        String authorizeUrl = earthDataURS;
        String redirectUri = earthDataURS;
        // These two parameters need to be set to the URL of the SRTM download server
        // Otherwise, OAuth20Parameters will refuse to be initialized and
        // OAuthToken.sign(HttpClient) will refuse to do its job
        String tokenUrl = earthDataDL;
        String apiUrl = earthDataDL;

        OAuth20Parameters parameters = new OAuth20Parameters(clientId, clientSecret, tokenUrl, authorizeUrl, apiUrl,
                redirectUri);
        try {
            return new OAuth20Token(parameters, json);
        } catch (OAuth20Exception e) {
            Logging.error("Elevation: " + e);
            return null;
        }
    }

    /**
     * Returns the list of elevation data sources for the currently used SRTM type.
     *
     * @return The elevation data sources.
     */
    public static List<ElevationDataSource> getElevationDataSources() {
        if (getSRTMType() == SRTMTile.Type.SRTM1)
            return ELEVATION_DATA_SOURCES_SRTM1;
        return ELEVATION_DATA_SOURCES_SRTM3;
    }

    private ElevationPreferences() {
    }
}
