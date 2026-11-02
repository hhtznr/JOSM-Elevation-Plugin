package hhtznr.josm.plugins.elevation;

import java.awt.Color;
import java.net.Authenticator.RequestorType;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.Path;
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

import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffIndex;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmDataSource;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.gui.layers.GeoTiffEnvelopeLayer;
import hhtznr.josm.plugins.elevation.io.SrtmFileDownloader;
import hhtznr.josm.plugins.elevation.util.Coloring;
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
    public static final Path DEFAULT_ELEVATION_DIRECTORY = Paths
            .get(Preferences.main().getDirs().getCacheDirectory(true).toString(), "elevation");

    /**
     * Default path, where SRTM1 and SRTM3 files were located until version 0.10.2.
     *
     * @since 0.11.0
     */
    public static final Path LEGACY_SRTM_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(), "SRTM");

    /**
     * Default path, where SRTM files from NASA Earthdata are stored in separate
     * directories for SRTM1 and SRTM3.
     */
    public static final Path DEFAULT_EARTHDATA_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(),
            "Earthdata");

    /**
     * Default path, where SRTM1 files from NASA Earthdata are stored locally.
     */
    public static final Path DEFAULT_EARTHDATA_SRTM1_DIRECTORY = Paths.get(DEFAULT_EARTHDATA_DIRECTORY.toString(),
            "SRTM1");

    /**
     * Default path, where SRTM3 files from NASA Earthdata are stored locally.
     */
    public static final Path DEFAULT_EARTHDATA_SRTM3_DIRECTORY = Paths.get(DEFAULT_EARTHDATA_DIRECTORY.toString(),
            "SRTM3");

    /**
     * Default path, where SRTM-compatible HGT files from Sonny's LiDAR Digital
     * Terrain Models of Europe are stored in separate directories for DTM1 and
     * DTM3.
     */
    public static final Path DEFAULT_SONNY_LIDAR_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(),
            "Sonny LiDAR");

    /**
     * Default path, where DTM1 files from Sonny's LiDAR Digital Terrain Models of
     * Europe are stored locally.
     */
    public static final Path DEFAULT_SONNY_LIDAR_DTM1_DIRECTORY = Paths.get(DEFAULT_SONNY_LIDAR_DIRECTORY.toString(),
            "DTM1");

    /**
     * Default path, where DTM3 files from Sonny's LiDAR Digital Terrain Models of
     * Europe are stored locally.
     */
    public static final Path DEFAULT_SONNY_LIDAR_DTM3_DIRECTORY = Paths.get(DEFAULT_SONNY_LIDAR_DIRECTORY.toString(),
            "DTM3");

    /**
     * Property key for SRTM type.
     */
    public static final String SRTM_TYPE = "elevation.srtm.type.preferred";

    /**
     * Default property value for the SRTM type.
     */
    private static final SrtmTile.Type DEFAULT_SRTM_TYPE = SrtmTile.Type.SRTM1;

    /**
     * Property key for defining the elevation interpolation method.
     */
    public static final String SRTM_ELEVATION_INTERPOLATION = "elevation.interpolation";

    /**
     * Default property value of the elevation interpolation method.
     */
    private static final SrtmTile.Interpolation DEFAULT_SRTM_ELEVATION_INTERPOLATION = SrtmTile.Interpolation.BILINEAR;

    /**
     * Property key for size limit of the in-memory SRTM tile cache.
     */
    public static final String SRTM_CACHE_SIZE_LIMIT = "elevation.cache.ram.size.limit";

    /**
     * Default property value for the size limit of the in-memory SRTM tile cache in
     * MiB: 128.
     */
    private static final int DEFAULT_SRTM_CACHE_SIZE_LIMIT = 128;

    /**
     * Minimum size limit of the in-memory SRTM tile cache in MiB: 32.
     */
    public static final int MIN_SRTM_CACHE_SIZE_LIMIT = 32;

    /**
     * Maximum size limit of the in-memory SRTM tile cache in MiB: 8192.
     */
    public static final int MAX_SRTM_CACHE_SIZE_LIMIT = 8192;

    /**
     * Increment of the size limit of the in-memory SRTM tile cache used by the
     * spinner in the elevation preferences in MiB: 1.
     */
    public static final int INCR_SRTM_CACHE_SIZE_LIMIT = 1;

    /**
     * Property key for enabling or disabling SRTM elevation visualization layer.
     */
    public static final String SRTM_ELEVATION_LAYER_ENABLED = "elevation.layer.enabled";

    /**
     * Default property value for enabling the SRTM elevation visualization layer:
     * {@code true}.
     */
    private static final boolean DEFAULT_SRTM_ELEVATION_LAYER_ENABLED = true;

    /**
     * Property key for the limit value of arc degrees in latitude or longitude
     * covered by the map view, where, if exceeded, SRTM elevation data caching as
     * well as contour line and hillshade rendering will be temporarily switched
     * off.
     */
    public static final String SRTM_ELEVATION_LAYER_RENDERING_LIMIT = "elevation.layer.rendering.limit";

    /**
     * Default property value for the limit value of arc degrees in latitude or
     * longitude covered by the map view, where, if exceeded, SRTM elevation data
     * caching as well as contour line and hillshade rendering will be temporarily
     * switched off: {@code 0.1}.
     */
    private static final double DEFAULT_SRTM_ELEVATION_LAYER_RENDERING_LIMIT = 0.1;

    /**
     * Minimum value of the SRTM elevation layer rendering limit: {@code 0.01}.
     */
    public static final double MIN_SRTM_LAYER_RENDERING_LIMIT = 0.01;

    /**
     * Maximum value of the SRTM elevation layer rendering limit: {@code 5.0}.
     */
    public static final double MAX_SRTM_LAYER_RENDERING_LIMIT = 5.0;

    /**
     * Increment of the SRTM elevation layer rendering limit: {@code 0.01}.
     */
    public static final double INCR_SRTM_LAYER_RENDERING_LIMIT = 0.01;

    /**
     * Property key for enabling or disabling SRTM contour line rendering.
     */
    public static final String SRTM_CONTOUR_LINES_ENABLED = "elevation.layer.contour-lines.enabled";

    /**
     * Default property value for enabling SRTM contour line rendering:
     * {@code true}.
     */
    private static final boolean DEFAULT_SRTM_CONTOUR_LINES_ENABLED = true;

    /**
     * Property key for step between adjacent SRTM contour lines.
     */
    public static final String SRTM_CONTOUR_LINE_ISOSTEP = "elevation.contour-line.isostep";

    /**
     * Default property value of the isostep between two adjacent SRTM contour
     * lines: {@code 10}.
     */
    private static final int DEFAULT_SRTM_CONTOUR_LINE_ISOSTEP = 10;

    /**
     * Minimum value of the contour line isostep: {@code 1}.
     */
    public static final int MIN_CONTOUR_LINE_ISOSTEP = 1;

    /**
     * Maximum value of the contour line isostep: {@code 500}.
     */
    public static final int MAX_CONTOUR_LINE_ISOSTEP = 500;

    /**
     * Increment of the contour line isostep: {@code 5}.
     */
    public static final int INCR_CONTOUR_LINE_ISOSTEP = 1;

    /**
     * Property key for width of the stroke for drawing of SRTM contour lines.
     */
    public static final String SRTM_CONTOUR_LINE_STROKE_WIDTH = "elevation.contour-line.stroke.width";

    /*
     * Default property value of the SRTM contour line stroke width.
     */
    private static final float DEFAULT_SRTM_CONTOUR_LINE_STROKE_WIDTH = 1.0f;

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
     * Property key for color of SRTM contour lines.
     */
    public static final String SRTM_CONTOUR_LINE_COLOR = "elevation.contour-line.color";

    /*
     * Default property value of the SRTM contour line color as HTML color.
     */
    private static final String DEFAULT_SRTM_CONTOUR_LINE_COLOR = "#D2B473";

    /**
     * Property key for coloring scheme of SRTM contour lines.
     */
    public static final String SRTM_CONTOUR_LINE_COLORING = "elevation.contour-line.coloring";

    /**
     * Default property value of the SRTM contour line coloring scheme.
     */
    private static final Coloring.Scheme DEFAULT_SRTM_CONTOUR_LINE_COLORING = Coloring.Scheme.CONSTANT_COLOR;

    /**
     * Property key for enabling or disabling SRTM hillshade rendering.
     */
    public static final String SRTM_HILLSHADE_ENABLED = "elevation.layer.hillshade.enabled";

    /**
     * Default property value for enabling SRTM hillshade rendering: {@code true}.
     */
    private static final boolean DEFAULT_SRTM_HILLSHADE_ENABLED = true;

    /**
     * Property key for the altitude of the illumination source in SRTM hillshade
     * computation.
     */
    public static final String SRTM_HILLSHADE_ALTITUDE = "elevation.hillshade.altitude";

    /**
     * Default property value of the altitude (degrees) of the illumination source
     * in SRTM hillshade computation: {@code 45}.
     */
    private static final int DEFAULT_SRTM_HILLSHADE_ALTITUDE = 45;

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
     * Property key for the azimuth of the illumination source in SRTM hillshade
     * computation.
     */
    public static final String SRTM_HILLSHADE_AZIMUTH = "elevation.hillshade.azimuth";

    /**
     * Default property value of the azimuth (degrees) of the illumination source in
     * SRTM hillshade computation: {@code 315} (NW).
     */
    private static final int DEFAULT_SRTM_HILLSHADE_AZIMUTH = 315;

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

    /**
     * Default value of the lower cutoff elevation: {@code -999}.
     */
    public static final short DEFAULT_LOWER_CUTOFF_ELEVATION = -999;

    /**
     * Default of the upper cutoff elevation: {@code 9999}.
     */
    public static final short DEFAULT_UPPER_CUTOFF_ELEVATION = 9999;

    /**
     * Property key for enabling or disabling rendering of the SRTM elevation data
     * point raster.
     */
    public static final String SRTM_ELEVATION_RASTER_ENABLED = "elevation.layer.raster.enabled";

    /**
     * Default property value for enabling rendering of the SRTM elevation data
     * point raster: {@code false}.
     */
    private static final boolean DEFAULT_SRTM_ELEVATION_RASTER_ENABLED = false;

    /**
     * Property key for enabling or disabling rendering of the SRTM lowest and
     * highest points within the map view
     */
    public static final String SRTM_LOWEST_AND_HIGHEST_POINTS_ENABLED = "elevation.layer.lowest_highest_points.enabled";

    /**
     * Default property value for enabling rendering of the SRTM lowest and highest
     * points within the map view: {@code true}.
     */
    private static final boolean DEFAULT_SRTM_LOWEST_AND_HIGHEST_POINTS_ENABLED = true;

    /**
     * The maximum number of lowest points and highest points, respectively, that is
     * rendered for an elevation layer in a map view.
     */
    public static final int MAX_RENDERED_LOWEST_OR_HIGHEST_POINTS = 10;

    /**
     * Property key for enabling or disabling automatic download of SRTM elevation
     * data.
     */
    public static final String SRTM_AUTO_DOWNLOAD_ENABLED = "elevation.autodownload";

    /**
     * Default property value for enabling automatic download of SRTM elevation
     * data: {@code false}.
     */
    private static final boolean DEFAULT_SRTM_AUTO_DOWNLOAD_ENABLED = false;

    /**
     * Legacy property key for authentication bearer token for Earthdata SRTM
     * server.
     */
    public static final String ELEVATION_SERVER_AUTH_BEARER = "elevation.srtm.server.auth.bearer";

    /**
     * Property key for the type of authentication at the SRTM elevation data
     * download server.
     */
    public static final String SRTM_SERVER_AUTH_TYPE = "elevation.srtm.server.auth.type";

    /**
     * Default property value of the type of authentication at the SRTM elevation
     * data download server.
     */
    public static final SrtmFileDownloader.AuthType DEFAULT_SRTM_SERVER_AUTH_TYPE = SrtmFileDownloader.AuthType.BEARER_TOKEN;

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
    public static final String EARTHDATA_DOWNLOAD_HOST = "data.lpdaac.earthdatacloud.nasa.gov";

    /**
     * URL of <a href="https://urs.earthdata.nasa.gov/users/new/">NASA Earthdata
     * Login User Registration</a> where users need to register and create an
     * authorization bearer token in order to download elevation data.
     */
    public static final String SRTM_SERVER_REGISTRATION_URL = "https://" + EARTHDATA_SSO_HOST + "/users/new/";

    /**
     * URL of <a href=
     * "https://data.lpdaac.earthdatacloud.nasa.gov/lp-prod-protected/SRTMGL1.003/">NASA's
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
            url = new URL("https://" + EARTHDATA_DOWNLOAD_HOST + "/lp-prod-protected/SRTMGL1.003/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM1_SERVER_BASE_URL = url;
    }

    /**
     * URL of <a href=
     * "https://data.lpdaac.earthdatacloud.nasa.gov/lp-prod-protected/SRTMGL3.003/">NASA's
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
            url = new URL("https://" + EARTHDATA_DOWNLOAD_HOST + "/lp-prod-protected/SRTMGL3.003/");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM3_SERVER_BASE_URL = url;
    }

    /**
     * URL where SRTM1 files from NASA Earthdata can manually be downloaded.
     */
    public static final URL SRTM1_MANUAL_DOWNLOAD_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://www.earthdata.nasa.gov/data/catalog/lpcloud-srtmgl1-003");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM1_MANUAL_DOWNLOAD_URL = url;
    }

    /**
     * URL where SRTM3 files from NASA Earthdata can manually be downloaded.
     */
    public static final URL SRTM3_MANUAL_DOWNLOAD_URL;
    static {
        URL url = null;
        try {
            url = new URL("https://www.earthdata.nasa.gov/data/catalog/lpcloud-srtmgl3n-003");
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Internal error: hardcoded URL is malformed", e);
        }
        SRTM3_MANUAL_DOWNLOAD_URL = url;
    }

    /**
     * URL where DTM1 files from Sonny's LiDAR Digital Terrain Models of Europe can
     * manually be downloaded.
     */
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

    /**
     * URL where DTM3 files from Sonny's LiDAR Digital Terrain Models of Europe can
     * manually be downloaded.
     */
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
    public static final SrtmDataSource ELEVATION_DATA_SOURCE_EARTHDATA_SRTM1 = new SrtmDataSource(
            "NASA Earthdata SRTM1", DEFAULT_EARTHDATA_SRTM1_DIRECTORY, SrtmTile.Type.SRTM1, SRTM1_SERVER_BASE_URL,
            EARTHDATA_SSO_HOST, true);

    /**
     * Data source object for NASA Earthdata SRTM3.
     */
    public static final SrtmDataSource ELEVATION_DATA_SOURCE_EARTHDATA_SRTM3 = new SrtmDataSource(
            "NASA Earthdata SRTM3", DEFAULT_EARTHDATA_SRTM3_DIRECTORY, SrtmTile.Type.SRTM3, SRTM3_SERVER_BASE_URL,
            EARTHDATA_SSO_HOST, true);

    /*
     * Data source object for Sonny's LiDAR DTM1.
     */
    public static final SrtmDataSource ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM1 = new SrtmDataSource("Sonny's LiDAR DTM1",
            DEFAULT_SONNY_LIDAR_DTM1_DIRECTORY, SrtmTile.Type.SRTM1, SONNY_LIDAR_DTM1_BASE_URL, null, false);

    /*
     * Data source object for Sonny's LiDAR DTM3.
     */
    public static final SrtmDataSource ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM3 = new SrtmDataSource("Sonny's LiDAR DTM3",
            DEFAULT_SONNY_LIDAR_DTM3_DIRECTORY, SrtmTile.Type.SRTM3, SONNY_LIDAR_DTM3_BASE_URL, null, false);

    /**
     * List of elevation data sources for SRM1 data.
     */
    public static final List<SrtmDataSource> ELEVATION_DATA_SOURCES_SRTM1 = new LinkedList<>();
    static {
        ELEVATION_DATA_SOURCES_SRTM1.add(ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM1);
        ELEVATION_DATA_SOURCES_SRTM1.add(ELEVATION_DATA_SOURCE_EARTHDATA_SRTM1);
    }

    /**
     * List of elevation data sources for SRTM3 data.
     */
    public static final List<SrtmDataSource> ELEVATION_DATA_SOURCES_SRTM3 = new LinkedList<>();
    static {
        ELEVATION_DATA_SOURCES_SRTM3.add(ELEVATION_DATA_SOURCE_SONNY_LIDAR_DTM3);
        ELEVATION_DATA_SOURCES_SRTM3.add(ELEVATION_DATA_SOURCE_EARTHDATA_SRTM3);
    }

    /**
     * Property key for persisting the last directory used for opening GeoTIFF
     * files.
     */
    public static final String GEOTIFF_LAST_DIRECTORY = "elevation.geotiff.last-directory";

    /**
     * Property key for persisting the directory with GeoTIFF files to be indexed
     * for quick access via {@link GeoTiffEnvelopeLayer}.
     */
    public static final String GEOTIFF_INDEX_DIRECTORY = "elevation.geotiff.index-directory";

    /**
     * Property key for persisting whether the directory with GeoTIFF files to be
     * index shall be searched for GeoTIFF files recursively.
     */
    public static final String GEOTIFF_INDEX_DIRECTORY_RECURSIVE = "elevation.geotiff.index-directory.recursive";

    /**
     * Default property value for recursive GeoTIFF search: {@code true}.
     */
    public static final boolean DEFAULT_GEOTIFF_INDEX_DIRECTORY_RECURSIVE = true;

    /**
     * Path, where the JSON file for persisting the GeoTIFF envelope data of the
     * {@link GeoTiffIndex} is located.
     */
    public static final Path GEOTIFF_INDEX_CACHE_FILE = DEFAULT_ELEVATION_DIRECTORY
            .resolve("GeoTiffIndexCacheFile.json");

    /**
     * Property key for enabling or disabling the {@link GeoTiffEnvelopeLayer}.
     */
    public static final boolean DEFAULT_GEOTIFF_ENVELOPE_LAYER_ENABLED = true;

    /**
     * Property key for persisting whether the {@link GeoTiffEnvelopeLayer} is
     * enabled.
     */
    public static final String GEOTIFF_ENVELOPE_LAYER_ENABLED = "elevation.geotiff.envelope-layer.enabled";

    /**
     * Property key for enabling or disabling GeoTIFF contour line rendering.
     */
    public static final String GEOTIFF_CONTOUR_LINES_ENABLED = "elevation.geotiff.layer.contour-lines.enabled";

    /**
     * Default property value for enabling GeoTIFF contour line rendering:
     * {@code true}.
     */
    private static final boolean DEFAULT_GEOTIFF_CONTOUR_LINES_ENABLED = true;

    /**
     * Property key for step between adjacent GeoTIFF contour lines.
     */
    public static final String GEOTIFF_CONTOUR_LINE_ISOSTEP = "elevation.geotiff.contour-line.isostep";

    /**
     * Default property value of the isostep between two adjacent GeoTIFF contour
     * lines: {@code 5}.
     */
    private static final int DEFAULT_GEOTIFF_CONTOUR_LINE_ISOSTEP = 5;

    /**
     * Property key for width of the stroke for drawing of GeoTIFF contour lines.
     */
    public static final String GEOTIFF_CONTOUR_LINE_STROKE_WIDTH = "elevation.geotiff.contour-line.stroke.width";

    /*
     * Default property value of the GeoTIFF contour line stroke width.
     */
    private static final float DEFAULT_GEOTIFF_CONTOUR_LINE_STROKE_WIDTH = 1.0f;

    /**
     * Property key for color of GeoTIFF contour lines.
     */
    public static final String GEOTIFF_CONTOUR_LINE_COLOR = "elevation.geotiff.contour-line.color";

    /*
     * Default property value of the GeoTIFF contour line color as HTML color.
     */
    private static final String DEFAULT_GEOTIFF_CONTOUR_LINE_COLOR = "#D2B473";

    /**
     * Property key for coloring scheme of GeoTiff contour lines.
     */
    public static final String GEOTIFF_CONTOUR_LINE_COLORING = "elevation.geotiff.contour-line.coloring";

    /**
     * Default property value of the GeoTiff contour line coloring scheme.
     */
    private static final Coloring.Scheme DEFAULT_GEOTIFF_CONTOUR_LINE_COLORING = Coloring.Scheme.FALSE_COLOR;

    /**
     * Property key for enabling or disabling rendering of the GeoTIFF lowest and
     * highest points within the map view
     */
    public static final String GEOTIFF_LOWEST_AND_HIGHEST_POINTS_ENABLED = "elevation.geotiff.layer.lowest_highest_points.enabled";

    /**
     * Default property value for enabling rendering of the GeoTIFF lowest and
     * highest points within the map view: {@code true}.
     */
    private static final boolean DEFAULT_GEOTIFF_LOWEST_AND_HIGHEST_POINTS_ENABLED = true;

    /**
     * Property key for enabling or disabling GeoTIFF grayscale image rendering.
     */
    public static final String GEOTIFF_GRAYSCALE_IMAGE_ENABLED = "elevation.geotiff.layer.grayscale-image.enabled";

    /**
     * Default property value for enabling GeoTIFF grayscale image rendering:
     * {@code true}.
     */
    private static final boolean DEFAULT_GEOTIFF_GRAYSCALE_IMAGE_ENABLED = true;

    /**
     * Removes the legacy 'elevation enabled' preference from the settings.
     *
     * @return {@code true} if the value has changed.
     */
    public static boolean removeElevationEnabledPreference() {
        return Config.getPref().put(LEGACY_ELEVATION_ENABLED, null);
    }

    /**
     * Returns the SRTM type.
     *
     * @return The SRTM type, {@code SRTM1} or {@code SRTM3}.
     */
    public static SrtmTile.Type getSRTMType() {
        return SrtmTile.Type.fromString(Config.getPref().get(SRTM_TYPE, DEFAULT_SRTM_TYPE.toString()));
    }

    /**
     * Sets the specified SRTM type in the settings.
     *
     * @param type The SRTM type.
     * @return {@code true} if the SRTM type has changed.
     */
    public static boolean setSRTMType(SrtmTile.Type type) {
        return Config.getPref().put(SRTM_TYPE, type.toString());
    }

    /**
     * Returns the elevation interpolation method.
     *
     * @return The method for interpolation of elevation values.
     */
    public static SrtmTile.Interpolation getElevationInterpolation() {
        return SrtmTile.Interpolation.fromString(
                Config.getPref().get(SRTM_ELEVATION_INTERPOLATION, DEFAULT_SRTM_ELEVATION_INTERPOLATION.toString()));
    }

    /**
     * Sets the specified elevation interpolation in the settings.
     *
     * @param interpolation The interpolation type.
     * @return {@code true} if the interpolation type has changed.
     */
    public static boolean setElevationInterpolation(SrtmTile.Interpolation interpolation) {
        return Config.getPref().put(SRTM_ELEVATION_INTERPOLATION, interpolation.toString());
    }

    /**
     * Returns the size limit of the in-memory SRTM tile cache.
     *
     * @return Size limit of the in-memory SRTM tile cache in MiB.
     */
    public static int getRAMCacheSizeLimit() {
        return Config.getPref().getInt(SRTM_CACHE_SIZE_LIMIT, DEFAULT_SRTM_CACHE_SIZE_LIMIT);
    }

    /**
     * Sets the sitze limit of the in-memory SRTM tile cache in the settings.
     *
     * @param limit Size limit of the in-memory SRTM tile cache in MiB.
     * @return {@code true} if the limit value has changed.
     */
    public static boolean setRAMCacheSizeLimit(int limit) {
        return Config.getPref().putInt(SRTM_CACHE_SIZE_LIMIT, limit);
    }

    /**
     * Returns whether the SRTM elevation visualization layer is enabled or
     * disabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmElevationLayerEnabled() {
        return Config.getPref().getBoolean(SRTM_ELEVATION_LAYER_ENABLED, DEFAULT_SRTM_ELEVATION_LAYER_ENABLED);
    }

    /**
     * Sets the elevation visualization layer to the specified enabled state in the
     * settings.
     *
     * @param enabled If {@code true}, the elevation layer is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmElevationLayerEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_ELEVATION_LAYER_ENABLED, enabled);
    }

    /**
     * Returns the limit value for rendering of the SRTM elevation visualization
     * layer.
     *
     * @return The limit value of arc degrees in latitude or longitude covered by
     *         the map view, where, if exceeded, elevation data caching as well as
     *         contour line and hillshade rendering will be temporarily switched
     *         off.
     */
    public static double getSrtmElevationLayerRenderingLimit() {
        return Config.getPref().getDouble(SRTM_ELEVATION_LAYER_RENDERING_LIMIT,
                DEFAULT_SRTM_ELEVATION_LAYER_RENDERING_LIMIT);
    }

    /**
     * Sets the limit value for rendering of the SRTM elevation visualization layer
     * in the settings.
     *
     * @param arcDegrees The limit value of arc degrees in latitude or longitude
     *                   covered by the map view, where, if exceeded, elevation data
     *                   caching as well as contour line and hillshade rendering
     *                   will be temporarily switched off.
     * @return {@code true} if the limit value has changed.
     */
    public static boolean setSrtmElevationLayerRenderingLimit(double arcDegrees) {
        return Config.getPref().putDouble(SRTM_ELEVATION_LAYER_RENDERING_LIMIT, arcDegrees);
    }

    /**
     * Returns whether rendering of SRTM contour lines is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmContourLinesEnabled() {
        return Config.getPref().getBoolean(SRTM_CONTOUR_LINES_ENABLED, DEFAULT_SRTM_CONTOUR_LINES_ENABLED);
    }

    /**
     * Sets rendering of SRTM contour lines to the specified state.
     *
     * @param enabled If {@code true}, SRTM contour line rendering is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmContourLinesEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_CONTOUR_LINES_ENABLED, enabled);
    }

    /**
     * Returns the step between adjacent SRTM contour lines.
     *
     * @return The step between adjacent SRTM contour lines.
     */
    public static int getSrtmContourLineIsostep() {
        return Config.getPref().getInt(SRTM_CONTOUR_LINE_ISOSTEP, DEFAULT_SRTM_CONTOUR_LINE_ISOSTEP);
    }

    /**
     * Sets the step between adjacent SRTM contour lines to the specified value in
     * the settings.
     *
     * @param isostep The step between adjacent SRTM contour lines.
     * @return {@code true} if the isostep value has changed.
     */
    public static boolean setSrtmContourLineIsostep(int isostep) {
        return Config.getPref().putInt(SRTM_CONTOUR_LINE_ISOSTEP, isostep);
    }

    /**
     * Returns the SRTM contour line stroke width.
     *
     * @return The width of the stroke for drawing of SRTM contour lines in px.
     */
    public static float getSrtmContourLineStrokeWidth() {
        return (float) Config.getPref().getDouble(SRTM_CONTOUR_LINE_STROKE_WIDTH,
                DEFAULT_SRTM_CONTOUR_LINE_STROKE_WIDTH);
    }

    /**
     * Sets the SRTM contour line stroke width to the specified value in the
     * settings.
     *
     * @param width The width of the stroke for drawing of SRTM contour lines in px.
     * @return {@code true} if the stroke width value has changed.
     */
    public static boolean setSrtmContourLineStrokeWidth(double width) {
        return Config.getPref().putDouble(SRTM_CONTOUR_LINE_STROKE_WIDTH, width);
    }

    /**
     * Returns the SRTM contour line color.
     *
     * @return The color of SRTM contour lines.
     */
    public static Color getSrtmContourLineColor() {
        String htmlColor = Config.getPref().get(SRTM_CONTOUR_LINE_COLOR, DEFAULT_SRTM_CONTOUR_LINE_COLOR);
        return ColorHelper.html2color(htmlColor);
    }

    /**
     * Sets the value of the SRTM contour line color in the settings.
     *
     * @param color The color to set as SRTM contour line color in the settings.
     * @return {@code true} if the color value has changed.
     */
    public static boolean setSrtmContourLineColor(Color color) {
        String htmlColor = ColorHelper.color2html(color, false);
        return Config.getPref().put(SRTM_CONTOUR_LINE_COLOR, htmlColor);
    }

    /**
     * Returns the SRTM contour line coloring scheme.
     *
     * @return The coloring scheme for SRTM contour lines.
     */
    public static Coloring.Scheme getSrtmContourLineColoringScheme() {
        return Coloring.Scheme.fromString(
                Config.getPref().get(SRTM_CONTOUR_LINE_COLORING, DEFAULT_SRTM_CONTOUR_LINE_COLORING.toString()));
    }

    /**
     * Sets the value of the SRTM contour line coloring scheme in the settings.
     *
     * @param scheme The coloring scheme to set as SRTM contour line coloring scheme
     *               in the settings.
     * @return {@code true} if the value has changed.
     */
    public static boolean setSrtmContourLineColoringScheme(Coloring.Scheme scheme) {
        return Config.getPref().put(SRTM_CONTOUR_LINE_COLORING, scheme.toString());
    }

    /**
     * Returns whether rendering of SRTM hillshade is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmHillshadeEnabled() {
        return Config.getPref().getBoolean(SRTM_HILLSHADE_ENABLED, DEFAULT_SRTM_HILLSHADE_ENABLED);
    }

    /**
     * Sets rendering of SRTM hillshade to the specified state in the settings.
     *
     * @param enabled If {@code true}, SRTM hillshade rendering is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmHillshadeEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_HILLSHADE_ENABLED, enabled);
    }

    /**
     * Returns the altitude of the SRTM hillshade illumination source.
     *
     * @return The altitude of the illumination source in SRTM hillshade
     *         computation.
     */
    public static int getSrtmHillshadeAltitude() {
        return Config.getPref().getInt(SRTM_HILLSHADE_ALTITUDE, DEFAULT_SRTM_HILLSHADE_ALTITUDE);
    }

    /**
     * Sets the altitude of the illumination source in SRTM hillshade computation to
     * the specified value in the settings.
     *
     * @param altitude The altitude of the illumination source in SRTM hillshade
     *                 computation.
     * @return {@code true} if the altitude value has changed.
     */
    public static boolean setSrtmHillshadeAltitude(int altitude) {
        return Config.getPref().putInt(SRTM_HILLSHADE_ALTITUDE, altitude);
    }

    /**
     * Returns the azimuth of the SRTM hillshade illumination source.
     *
     * @return The azimuth of the illumination source in SRTM hillshade computation.
     */
    public static int getSrtmHillshadeAzimuth() {
        return Config.getPref().getInt(SRTM_HILLSHADE_AZIMUTH, DEFAULT_SRTM_HILLSHADE_AZIMUTH);
    }

    /**
     * Sets the azimuth of the illumination source in SRTM hillshade computation to
     * the specified value in the settings.
     *
     * @param azimuth The azimuth of the illumination source in SRTM hillshade
     *                computation.
     * @return {@code true} if the azimuth value has changed.
     */
    public static boolean setSrtmHillshadeAzimuth(int azimuth) {
        return Config.getPref().putInt(SRTM_HILLSHADE_AZIMUTH, azimuth);
    }

    /**
     * Returns whether rendering of the SRTM elevation raster is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmElevationRasterEnabled() {
        return Config.getPref().getBoolean(SRTM_ELEVATION_RASTER_ENABLED, DEFAULT_SRTM_ELEVATION_RASTER_ENABLED);
    }

    /**
     * Sets rendering of the SRTM elevation raster to the specified state in the
     * settings.
     *
     * @param enabled If {@code true}, rendering of the SRTM elevation raster is
     *                enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmElevationRasterEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_HILLSHADE_ENABLED, enabled);
    }

    /**
     * Returns whether rendering of the SRTM lowest and highest points within the
     * map view is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmLowestAndHighestPointsEnabled() {
        return Config.getPref().getBoolean(SRTM_LOWEST_AND_HIGHEST_POINTS_ENABLED,
                DEFAULT_SRTM_LOWEST_AND_HIGHEST_POINTS_ENABLED);
    }

    /**
     * Sets rendering of the SRTM lowest and highest points within the map view to
     * the specified state in the settings.
     *
     * @param enabled If {@code true}, rendering of the SRTM lowest and highest
     *                points within the map view is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmLowestAndHighestPointsEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_LOWEST_AND_HIGHEST_POINTS_ENABLED, enabled);
    }

    /**
     * Returns whether auto-download of SRTM tiles is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getSrtmAutoDownloadEnabled() {
        return Config.getPref().getBoolean(SRTM_AUTO_DOWNLOAD_ENABLED, DEFAULT_SRTM_AUTO_DOWNLOAD_ENABLED);
    }

    /**
     * Sets auto-download of SRTM tiles to the specified state in the settings.
     *
     * @param enabled If {@code true}, auto-download of SRTM tiles is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setSrtmAutoDownloadEnabled(boolean enabled) {
        return Config.getPref().putBoolean(SRTM_AUTO_DOWNLOAD_ENABLED, enabled);
    }

    /**
     * Returns the type of authentication at the SRTM data download server.
     *
     * @return The type of authentication at the SRTM data download server.
     */
    public static SrtmFileDownloader.AuthType getSrtmServerAuthType() {
        return SrtmFileDownloader.AuthType
                .fromString(Config.getPref().get(SRTM_SERVER_AUTH_TYPE, DEFAULT_SRTM_SERVER_AUTH_TYPE.toString()));
    }

    /**
     * Sets the type of authentication at the SRTM data download server to the
     * specified type in the settings.
     *
     * @param authType The type of authentication at the SRTM data download server.
     * @return {@code true} if the value has changed.
     */
    public static boolean setSrtmServerAuthType(SrtmFileDownloader.AuthType authType) {
        return Config.getPref().put(SRTM_SERVER_AUTH_TYPE, authType.toString());
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
        if (userName.trim().isBlank()) {
            return null;
        }
        if (password.length < 1) {
            return null;
        }
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
        if (existingCredentials == null) {
            return true;
        }
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
                if (bearer == null || bearer.isBlank()) {
                    return null;
                }
                Logging.info("Elevation: Migrating authorization bearer token to JOSM OAuth 2.0 format");
                bearer = bearer.trim();
                OAuth20Token oAuthToken = storeEarthdataOAuthToken(bearer);
                Config.getPref().put(ELEVATION_SERVER_AUTH_BEARER, null);
                return oAuthToken;
            }

            if (authToken instanceof OAuth20Token) {
                return (OAuth20Token) authToken;
            }

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
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }
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
    public static List<SrtmDataSource> getElevationDataSources() {
        if (getSRTMType() == SrtmTile.Type.SRTM1) {
            return ELEVATION_DATA_SOURCES_SRTM1;
        }
        return ELEVATION_DATA_SOURCES_SRTM3;
    }

    /**
     * Returns the path of the directory from which individual GeoTIFF files were
     * opened most recently.
     *
     * @return The absolute path of the directory or {@code null} if it is not
     *         specified.
     */
    public static Path getGeoTiffLastDirectory() {
        String path = Config.getPref().get(GEOTIFF_LAST_DIRECTORY, null);
        if (path == null) {
            return null;
        }
        return Path.of(path).toAbsolutePath();
    }

    /**
     * Sets the path of the directory from which individual GeoTIFF files were
     * opened most recently in the settings.
     *
     * @param path The directory path or {@code null} if it is not specified.
     * @return {@code true} if the path has changed.
     */
    public static boolean setGeoTiffLastDirectory(Path path) {
        if (path == null) {
            return Config.getPref().put(GEOTIFF_LAST_DIRECTORY, null);
        }
        return Config.getPref().put(GEOTIFF_LAST_DIRECTORY, path.toAbsolutePath().toString());
    }

    /**
     * Returns the path of the directory with the GeoTIFF files that should be
     * loaded into the {@link GeoTiffIndex}.
     *
     * @return The absolute path of the directory or {@code null} if it is not
     *         specified.
     */
    public static Path getGeoTiffIndexDirectory() {
        String path = Config.getPref().get(GEOTIFF_INDEX_DIRECTORY, null);
        if (path == null) {
            return null;
        }
        return Path.of(path).toAbsolutePath();
    }

    /**
     * Sets the path of the directory with the GeoTIFF files that should be loaded
     * into the {@link GeoTiffIndex} in the settings.
     *
     * @param path The directory path or {@code null} if it is not specified.
     * @return {@code true} if the path has changed.
     */
    public static boolean setGeoTiffIndexDirectory(Path path) {
        if (path == null) {
            return Config.getPref().put(GEOTIFF_INDEX_DIRECTORY, null);
        }
        return Config.getPref().put(GEOTIFF_INDEX_DIRECTORY, path.toAbsolutePath().toString());
    }

    /**
     * Sets whether to search for GeoTIFF files in the GeoTIFF directory
     * recursively.
     *
     * @return {@code true} if search shall be performed recursively.
     */
    public static boolean getGeoTiffIndexDirectoryRecursive() {
        return Config.getPref().getBoolean(GEOTIFF_INDEX_DIRECTORY_RECURSIVE,
                DEFAULT_GEOTIFF_INDEX_DIRECTORY_RECURSIVE);
    }

    /**
     * Returns whether to search for GeoTIFF files in the GeoTIFF directory
     * recursively.
     *
     * @param recursive If {@code true}, the GeoTIFF directory will be searched for
     *                  GeoTIFF files recursively.
     * @return {@code true} if the recursive flag has changed.
     */
    public static boolean setGeoTiffIndexDirectoryRecursive(boolean recursive) {
        return Config.getPref().putBoolean(GEOTIFF_INDEX_DIRECTORY_RECURSIVE, recursive);
    }

    /**
     * Returns whether the GeoTIFF envelope layer layer is enabled or disabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getGeoTiffEnvelopeLayerEnabled() {
        return Config.getPref().getBoolean(GEOTIFF_ENVELOPE_LAYER_ENABLED, DEFAULT_GEOTIFF_ENVELOPE_LAYER_ENABLED);
    }

    /**
     * Sets the GeoTIFF envelope layer to the specified enabled state in the
     * settings.
     *
     * @param enabled If {@code true}, the elevation layer is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setGeoTiffEnvelopeLayerEnabled(boolean enabled) {
        return Config.getPref().putBoolean(GEOTIFF_ENVELOPE_LAYER_ENABLED, enabled);
    }

    /**
     * Returns whether rendering of GeoTIFF contour lines is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getGeoTiffContourLinesEnabled() {
        return Config.getPref().getBoolean(GEOTIFF_CONTOUR_LINES_ENABLED, DEFAULT_GEOTIFF_CONTOUR_LINES_ENABLED);
    }

    /**
     * Sets rendering of GeoTIFF contour lines to the specified state.
     *
     * @param enabled If {@code true}, GeoTIFF contour line rendering is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setGeoTiffContourLinesEnabled(boolean enabled) {
        return Config.getPref().putBoolean(GEOTIFF_CONTOUR_LINES_ENABLED, enabled);
    }

    /**
     * Returns the step between adjacent GeoTIFF contour lines.
     *
     * @return The step between adjacent GeoTIFF contour lines.
     */
    public static int getGeoTiffContourLineIsostep() {
        return Config.getPref().getInt(GEOTIFF_CONTOUR_LINE_ISOSTEP, DEFAULT_GEOTIFF_CONTOUR_LINE_ISOSTEP);
    }

    /**
     * Sets the step between adjacent GeoTIFF contour lines to the specified value
     * in the settings.
     *
     * @param isostep The step between adjacent GeoTIFF contour lines.
     * @return {@code true} if the isostep value has changed.
     */
    public static boolean setGeoTiffContourLineIsostep(int isostep) {
        return Config.getPref().putInt(GEOTIFF_CONTOUR_LINE_ISOSTEP, isostep);
    }

    /**
     * Returns the GeoTIFF contour line stroke width.
     *
     * @return The width of the stroke for drawing of GeoTIFF contour lines in px.
     */
    public static float getGeoTiffContourLineStrokeWidth() {
        return (float) Config.getPref().getDouble(GEOTIFF_CONTOUR_LINE_STROKE_WIDTH,
                DEFAULT_GEOTIFF_CONTOUR_LINE_STROKE_WIDTH);
    }

    /**
     * Sets the GeoTIFF contour line stroke width to the specified value in the
     * settings.
     *
     * @param width The width of the stroke for drawing of GeoTIFF contour lines in
     *              px.
     * @return {@code true} if the stroke width value has changed.
     */
    public static boolean setGeoTiffContourLineStrokeWidth(double width) {
        return Config.getPref().putDouble(GEOTIFF_CONTOUR_LINE_STROKE_WIDTH, width);
    }

    /**
     * Returns the GeoTIFF contour line color.
     *
     * @return The color of GeoTIFF contour lines.
     */
    public static Color getGeoTiffContourLineColor() {
        String htmlColor = Config.getPref().get(GEOTIFF_CONTOUR_LINE_COLOR, DEFAULT_GEOTIFF_CONTOUR_LINE_COLOR);
        return ColorHelper.html2color(htmlColor);
    }

    /**
     * Sets the value of the GeoTIFF contour line color in the settings.
     *
     * @param color The color to set as GeoTIFF contour line color in the settings.
     * @return {@code true} if the color value has changed.
     */
    public static boolean setGeoTiffContourLineColor(Color color) {
        String htmlColor = ColorHelper.color2html(color, false);
        return Config.getPref().put(GEOTIFF_CONTOUR_LINE_COLOR, htmlColor);
    }

    /**
     * Returns the GeoTIFF contour line coloring scheme.
     *
     * @return The coloring scheme for GeoTIFF contour lines.
     */
    public static Coloring.Scheme getGeoTiffContourLineColoringScheme() {
        return Coloring.Scheme.fromString(
                Config.getPref().get(GEOTIFF_CONTOUR_LINE_COLORING, DEFAULT_GEOTIFF_CONTOUR_LINE_COLORING.toString()));
    }

    /**
     * Sets the value of the GeoTIFF contour line coloring scheme in the settings.
     *
     * @param scheme The coloring scheme to set as GeoTIFF contour line coloring
     *               scheme in the settings.
     * @return {@code true} if the value has changed.
     */
    public static boolean setGeoTiffContourLineColoringScheme(Coloring.Scheme scheme) {
        return Config.getPref().put(GEOTIFF_CONTOUR_LINE_COLORING, scheme.toString());
    }

    /**
     * Returns whether rendering of GeoTIFF lowest and highest points within the map
     * view is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getGeoTiffLowestAndHighestPointsEnabled() {
        return Config.getPref().getBoolean(GEOTIFF_LOWEST_AND_HIGHEST_POINTS_ENABLED,
                DEFAULT_GEOTIFF_LOWEST_AND_HIGHEST_POINTS_ENABLED);
    }

    /**
     * Sets rendering of GeoTIFF lowest and highest points within the map view to
     * the specified state in the settings.
     *
     * @param enabled If {@code true}, rendering of the GeoTIFF lowest and highest
     *                points within the map view is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setGeoTiffLowestAndHighestPointsEnabled(boolean enabled) {
        return Config.getPref().putBoolean(GEOTIFF_LOWEST_AND_HIGHEST_POINTS_ENABLED, enabled);
    }

    /**
     * Returns whether rendering of GeoTIFF grayscale images is enabled.
     *
     * @return {@code true} if enabled.
     */
    public static boolean getGeoTiffGrayscaleImageEnabled() {
        return Config.getPref().getBoolean(GEOTIFF_GRAYSCALE_IMAGE_ENABLED, DEFAULT_GEOTIFF_GRAYSCALE_IMAGE_ENABLED);
    }

    /**
     * Sets rendering of GeoTIFF grayscale images to the specified state.
     *
     * @param enabled If {@code true}, GeoTIFF grayscale image rendering is enabled.
     * @return {@code true} if the enabled state has changed.
     */
    public static boolean setGeoTiffGrayscaleImagesEnabled(boolean enabled) {
        return Config.getPref().putBoolean(GEOTIFF_GRAYSCALE_IMAGE_ENABLED, enabled);
    }

    private ElevationPreferences() {
    }
}
