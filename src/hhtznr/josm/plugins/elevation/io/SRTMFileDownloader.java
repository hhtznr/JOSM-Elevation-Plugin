package hhtznr.josm.plugins.elevation.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.data.oauth.OAuthException;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Class {@code SRTMFileDownloader} downloads files with elevation data from
 * SRTM (Shuttle Radar Topography Mission Height) from NASA's servers. It can
 * download SRTM1 and SRTM3 files.
 *
 * @author Harald Hetzner
 */
public class SRTMFileDownloader {

    private String authRedirectLocation = null;

    private String basicAuthHeader = null;
    private OAuth20Token oAuthToken = null;

    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);

    private final LinkedList<SRTMFileDownloadListener> downloadListeners = new LinkedList<>();

    /**
     * Type of authentication at the elevation data download server.
     */
    public static enum AuthType {
        /**
         * Basic authentication.
         */
        BASIC("Basic authentication"),

        /**
         * Authorization bearer token.
         */
        BEARER_TOKEN("Authorization bearer token");

        private final String typeName;

        AuthType(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Returns the name associated with this interpolation type.
         *
         * @return The name of the type.
         */
        @Override
        public String toString() {
            return typeName;
        }

        /**
         * Returns the authentication type associated with a given name.
         *
         * @param name The name associated with the authentication type.
         * @return The authentication type associated with the name or {@code BASIC} if
         *         the name does not match a defined type.
         */
        public static AuthType fromString(String name) {
            for (AuthType type : AuthType.values()) {
                if (type.toString().equals(name))
                    return type;
            }
            return BASIC;
        }
    }

    /**
     * Creates a new SRTM file downloader.
     *
     */
    public SRTMFileDownloader() {
    }

    /**
     * Sets new Earthdata credentials for authorization at the download server. At
     * the same time disables authentication based on an authorization bearer token
     * if it was enabled.
     *
     * @param passwordAuth         The new Earthdata credentials.
     * @param authRedirectLocation The location where we will be redirected for
     *                             authentication.
     */
    public void setPasswordAuthentication(PasswordAuthentication passwordAuth, String authRedirectLocation) {
        String userName = passwordAuth.getUserName();
        String password = "";
        if (passwordAuth.getPassword() != null)
            password = String.valueOf(passwordAuth.getPassword());
        if (userName != null)
            basicAuthHeader = "Basic "
                    + Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        else
            basicAuthHeader = null;
        this.authRedirectLocation = authRedirectLocation;
        oAuthToken = null;
    }

    /**
     * Sets the JOSM {@code OAuth20Token} holding the Earthdata authorization bearer
     * token to a new token. At the same time disables password authentication if it
     * was enabled.
     *
     * @param oAuthToken JOSM {@code OAuth20Token} holding the Earthdata
     *                   authorization bearer token to use for authentication. The
     *                   bearer token can be obtained in the {@code Generate Token}
     *                   tab at
     *                   <a href="https://urs.earthdata.nasa.gov/home">Earthdata
     *                   Login</a>.
     */
    public void setOAuthToken(OAuth20Token oAuthToken) {
        this.oAuthToken = oAuthToken;
        basicAuthHeader = null;
        authRedirectLocation = null;
    }

    /**
     * Adds a download listener to this SRTM file downloader. All listeners are
     * informed if a download starts, succeeds or fails.
     *
     * @param listener The download listener to add.
     */
    public void addDownloadListener(SRTMFileDownloadListener listener) {
        synchronized (downloadListeners) {
            if (!downloadListeners.contains(listener))
                downloadListeners.add(listener);
        }
    }

    /**
     * Removes a download listener.
     *
     * @param listener The download listener to be removed;
     */
    public void removeDownloadListener(SRTMFileDownloadListener listener) {
        synchronized (downloadListeners) {
            downloadListeners.remove(listener);
        }
    }

    /**
     * Instructs this SRTM file downloader to download an SRTM file for the
     * specified tile ID and type. Downloading is performed in a separate thread.
     *
     * @param srtmTileID          The SRTM tile ID for which to download the SRTM
     *                            file.
     * @param elevationDataSource The elevation data source from which to download
     *                            the file with the provided tile ID.
     * @return A future to synchronize on availability of the downloaded file.
     * @throws RejectedExecutionException Thrown if the download task cannot be
     *                                    accepted for execution.
     */
    public Future<File> downloadSRTMFile(String srtmTileID, ElevationDataSource elevationDataSource)
            throws RejectedExecutionException {
        Callable<File> downloadTask = () -> {
            return download(srtmTileID, elevationDataSource);
        };
        return downloadExecutor.submit(downloadTask);
    }

    private File download(String srtmTileID, ElevationDataSource elevationDataSource) {
        SRTMTile.Type srtmType = elevationDataSource.getSRTMTileType();
        String srtmFileName = SRTMFiles.getEarthdataSRTMFileName(srtmTileID, srtmType);

        if (basicAuthHeader != null)
            Logging.info("Elevation: Trying to download SRTM file " + srtmFileName + " using password authentication.");
        else if (oAuthToken != null)
            Logging.info(
                    "Elevation: Tyring to download SRTM file " + srtmFileName + " using authorization bearer token.");
        else
            Logging.info("Elevation: Tyring to download SRTM file " + srtmFileName + " without authentication.");
        downloadStarted(srtmTileID, srtmType);
        File srtmFile = null;

        URL url = null;
        try {
            url = new URL(elevationDataSource.getDownloadBaseURL(), srtmFileName);
        } catch (MalformedURLException e) {
            downloadFailed(srtmTileID, srtmType, e);
            return null;
        }
        HttpClient httpClient = HttpClient.create(url);
        // Add the authorization bearer token to the HTTP header
        if (oAuthToken != null) {
            try {
                oAuthToken.sign(httpClient);
            } catch (OAuthException e) {
                Logging.error("Elevation: " + e.toString());
                downloadFailed(srtmTileID, srtmType, e);
                return null;
            }
        }
        try {
            if (basicAuthHeader != null)
                httpClient.connect(null, authRedirectLocation, basicAuthHeader);
            else
                httpClient.connect();
            int responseCode = httpClient.getResponse().getResponseCode();
            // https://urs.earthdata.nasa.gov/documentation/for_users/data_access/java
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String advice = null;
                switch (responseCode) {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    if (oAuthToken != null)
                        advice = "You may need to renew your Earthdata authorization bearer token";
                    else if (basicAuthHeader != null)
                        advice = "You may need to set up or correct your Earthdata credentials";
                    else
                        advice = "You may need to enter valid Earhtdata credentials or an authorization bearer token in the Elevation Data preferences";
                    break;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    advice = "You need to authorize the application 'LP DAAC Data Pool' at Earthdata Login -> Applications";
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    advice = "It is possible that no elevation data is available for " + srtmTileID;
                    break;
                default:
                    break;
                }
                Logging.warn("Elevation: SRTM server responded: " + responseCode + " "
                        + httpClient.getResponse().getResponseMessage());
                if (advice != null)
                    Logging.info("Elevation: " + advice);
                downloadFailed(srtmTileID, srtmType, new HTTPException(url, httpClient.getResponse(), advice));
                return null;
            }
            InputStream in = httpClient.getResponse().getContent();
            Path downloadedZipFile = Paths.get(elevationDataSource.getDataDirectory().toString(), srtmFileName);
            Files.copy(in, downloadedZipFile, StandardCopyOption.REPLACE_EXISTING);
            srtmFile = downloadedZipFile.toFile();
        } catch (IOException e) {
            Logging.error("Elevation: Downloading SRTM file " + srtmFileName + " failed due to I/O Exception: "
                    + e.toString());
            downloadFailed(srtmTileID, srtmType, e);
            return null;
        }

        // This would happen, if the downloaded file was uncompressed, but it does not
        // contain an appropriately named file (prefixed with SRTM tile ID)
        if (srtmFile == null) {
            String errorMessage = "Elevation: Downloaded compressed SRTM file " + srtmFileName
                    + " did not contain a file with the expected SRTM tile ID!";
            Logging.error(errorMessage);
            downloadFailed(srtmTileID, srtmType, new IOException(errorMessage));
            return null;
        }

        Logging.info("Elevation: Successfully downloaded SRTM file " + srtmFile.getName() + " to SRTM directory: "
                + elevationDataSource.getDataDirectory().toString());
        downloadSucceeded(srtmFile, elevationDataSource.getSRTMTileType());
        return srtmFile;
    }

    private void downloadStarted(String srtmTileID, SRTMTile.Type type) {
        synchronized (downloadListeners) {
            for (SRTMFileDownloadListener listener : downloadListeners)
                listener.srtmFileDownloadStarted(srtmTileID, type);
        }
    }

    private void downloadFailed(String srtmTileID, SRTMTile.Type type, Exception exception) {
        synchronized (downloadListeners) {
            for (SRTMFileDownloadListener listener : downloadListeners)
                listener.srtmFileDownloadFailed(srtmTileID, type, exception);
        }
    }

    private void downloadSucceeded(File srtmFile, SRTMTile.Type type) {
        synchronized (downloadListeners) {
            for (SRTMFileDownloadListener listener : downloadListeners)
                listener.srtmFileDownloadSucceeded(srtmFile, type);
        }
    }

    /**
     * Exception class for treating HTTP errors as exceptions.
     */
    public static class HTTPException extends Exception {

        private static final long serialVersionUID = 1L;
        private final URL url;
        private final HttpClient.Response response;
        private final String advice;

        /**
         * Creates a new HTTP exception.
         *
         * @param url      The URL for which the exception occurred.
         * @param response The HTTP error response of the host.
         * @param advice   Advice how to resolve the error.
         */
        public HTTPException(URL url, HttpClient.Response response, String advice) {
            super("Host " + url.getHost() + " responded: " + response.getResponseCode() + " - "
                    + response.getResponseMessage());
            this.url = url;
            this.response = response;
            this.advice = advice;
        }

        /**
         * Returns the URL for which the exception occurred.
         *
         * @return The URL.
         */
        public URL getURL() {
            return url;
        }

        /**
         * Returns the HTTP error response of the host.
         *
         * @return The HTTP error response.
         */
        public HttpClient.Response getResponse() {
            return response;
        }

        /**
         * Returns advice how to resolve the HTTP error.
         *
         * @return The advice or {@code null} if no advice is available.
         */
        public String getAdvice() {
            return advice;
        }
    }
}
