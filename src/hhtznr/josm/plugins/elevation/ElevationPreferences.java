package hhtznr.josm.plugins.elevation;

import java.io.File;
import java.nio.file.Paths;

import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.oauth.IOAuthToken;
import org.openstreetmap.josm.data.oauth.OAuth20Exception;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import hhtznr.josm.plugins.elevation.data.SRTMTile;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

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
     * Default property value for enabling use of elevation data: {@code true}.
     */
    public static final boolean DEFAULT_ELEVATION_ENABLED = true;

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
     * Property key for preferred SRTM type.
     */
    public static final String PREFERRED_SRTM_TYPE = "elevation.srtm.type.preferred";

    /**
     * Default property value for the preferred SRTM type.
     */
    public static final SRTMTile.Type DEFAULT_PREFERRED_SRTM_TYPE = SRTMTile.Type.SRTM1;

    /**
     * Property key for defining the elevation interpolation method.
     */
    public static final String ELEVATION_INTERPOLATION = "elevation.interpolation";

    /**
     * Default property value of the elevation interpolation method.
     */
    public static final SRTMTile.Interpolation DEFAULT_ELEVATION_INTERPOLATION = SRTMTile.Interpolation.BILINEAR;

    /**
     * Property key for size limit of the in-memory SRTM tile cache.
     */
    public static final String RAM_CACHE_SIZE_LIMIT = "elevation.cache.ram.size.limit";

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
     * Property key for enabling or disabling elevation visualization layer.
     */
    public static final String ELEVATION_LAYER_ENABLED = "elevation.layer.enabled";

    /**
     * Default property value for enabling the elevation visualization layer:
     * {@code true}.
     */
    public static final boolean DEFAULT_ELEVATION_LAYER_ENABLED = true;

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
    public static final double DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT = 0.1;

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
    public static final boolean DEFAULT_ELEVATION_CONTOUR_LINES_ENABLED = true;

    /**
     * Property key for step between neighboring elevation contour lines.
     */
    public static final String CONTOUR_LINE_ISOSTEP = "elevation.contour-line.isostep";

    /**
     * Default property value of the isostep between two neighboring elevation
     * contour lines: {@code 10}.
     */
    public static final int DEFAULT_CONTOUR_LINE_ISOSTEP = 10;

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
     * Property key for enabling or disabling hillshade rendering.
     */
    public static final String ELEVATION_HILLSHADE_ENABLED = "elevation.layer.hillshade.enabled";

    /**
     * Default property value for enabling hillshade rendering: {@code true}.
     */
    public static final boolean DEFAULT_ELEVATION_HILLSHADE_ENABLED = true;

    /**
     * Property key for the altitude of the illumination source in hillshade
     * computation.
     */
    public static final String HILLSHADE_ALTITUDE = "elevation.hillshade.altitude";

    /**
     * Default property value of the altitude (degrees) of the illumination source
     * in hillshade computation: {@code 45}.
     */
    public static final int DEFAULT_HILLSHADE_ALTITUDE = 45;

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
    public static final int DEFAULT_HILLSHADE_AZIMUTH = 315;

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
     * Property key for enabling or disabling rendering of the elevation data point
     * raster.
     */
    public static final String ELEVATION_RASTER_ENABLED = "elevation.layer.raster.enabled";

    /**
     * Default property value for enabling rendering of the elevation data point
     * raster: {@code false}.
     */
    public static final boolean DEFAULT_ELEVATION_RASTER_ENABLED = false;

    /**
     * Property key for enabling or disabling automatic download of elevation data.
     */
    public static final String ELEVATION_AUTO_DOWNLOAD_ENABLED = "elevation.autodownload";

    /**
     * Default property value for enabling automatic download of elevation data:
     * {@code false}.
     */
    public static final boolean DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED = false;

    /**
     * Legacy property key for authentication bearer token for Earthdata SRTM
     * server.
     */
    public static final String ELEVATION_SERVER_AUTH_BEARER = "elevation.srtm.server.auth.bearer";

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
    public static final String SRTM1_SERVER_BASE_URL = "https://" + EARTHDATA_DOWNLOAD_HOST
            + "/MEASURES/SRTMGL1.003/2000.02.11/";

    /**
     * URL of
     * <a href="https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL3.003/2000.02.11/">NASA's
     * Land Processes Distributed Active Archive Center (LP DAAC)</a> where SRTM3
     * files can be downloaded.
     *
     * Requires registration at
     * {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL}.
     */
    public static final String SRTM3_SERVER_BASE_URL = "https://" + EARTHDATA_DOWNLOAD_HOST
            + "/MEASURES/SRTMGL3.003/2000.02.11/";

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
                if (bearer == null || Utils.isBlank(bearer))
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
        if (bearerToken == null || Utils.isBlank(bearerToken))
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

    private ElevationPreferences() {
    }
}
