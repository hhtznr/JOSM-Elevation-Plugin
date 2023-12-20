package hhtznr.josm.plugins.elevation.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Class {@code SRTMFileDownloader} downloads files with elevation data from
 * SRTM (Shuttle Radar Topography Mission Height) from NASA's servers. It can
 * download SRTM1 and SRTM3 files.
 *
 * @author Harald Hetzner
 */
public class SRTMFileDownloader {

    private final URL srtm1BaseURL;
    private final URL srtm3BaseURL;
    private File srtmDirectory;

    private OAuth20Token oAuthToken;

    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);

    private final LinkedList<SRTMFileDownloadListener> downloadListeners = new LinkedList<>();

    /**
     * Creates a new SRTM file downloader.
     *
     * @param srtmDirectory The local directory into which to download SRTM files.
     * @param srtm1BaseURL  The URL from which to download SRTM1 files. See also
     *                      {@link ElevationPreferences#SRTM1_SERVER_BASE_URL
     *                      ElevationPreferences.SRTM1_SERVER_BASE_URL}.
     * @param srtm3BaseURL  The URL from which to download SRTM3 files. See also
     *                      {@link ElevationPreferences#SRTM3_SERVER_BASE_URL
     *                      ElevationPreferences.SRTM3_SERVER_BASE_URL}.
     * @param oAuthToken    JOSM {@code OAuth20Token} holding the Earthdata
     *                      authorization bearer token to use for authentication.
     *                      The bearer token can be obtained in the
     *                      {@code Generate Token} tab at
     *                      <a href="https://urs.earthdata.nasa.gov/home">Earthdata
     *                      Login</a>.
     * @throws MalformedURLException Thrown if the URL is not properly formatted.
     */
    public SRTMFileDownloader(File srtmDirectory, String srtm1BaseURL, String srtm3BaseURL, OAuth20Token oAuthToken)
            throws MalformedURLException {
        // May throw MalformedURLException
        this.srtm1BaseURL = new URL(srtm1BaseURL);
        this.srtm3BaseURL = new URL(srtm3BaseURL);
        this.oAuthToken = oAuthToken;
        setSRTMDirectory(srtmDirectory);
    }

    /**
     * Creates a new SRTM file downloader using defaults from the JOSM preferences
     * file.
     *
     * @param srtmDirectory The local directory into which to download SRTM files.
     * @throws MalformedURLException Thrown if the URL is not properly formatted.
     */
    public SRTMFileDownloader(File srtmDirectory) throws MalformedURLException {
        this(srtmDirectory, ElevationPreferences.SRTM1_SERVER_BASE_URL, ElevationPreferences.SRTM3_SERVER_BASE_URL,
                ElevationPreferences.lookupEarthdataOAuthToken());
    }

    /**
     * Sets the local download directory as specified.
     *
     * @param srtmDirectory The local directory to set as download directory.
     */
    public void setSRTMDirectory(File srtmDirectory) {
        this.srtmDirectory = srtmDirectory;
    }

    /**
     * Sets the JOSM {@code OAuth20Token} holding the Earthdata authorization bearer
     * token to a new token.
     *
     * @param oAuthToken The new OAuth token to use for authentication.
     */
    public void setOAuthToken(OAuth20Token oAuthToken) {
        this.oAuthToken = oAuthToken;
    }

    /**
     * Adds a download listener to this SRTM file downloader. All listeners are
     * informed if a download starts, succeeds or fails.
     *
     * @param listener The download listener to add.
     */
    public void addDownloadListener(SRTMFileDownloadListener listener) {
        if (!downloadListeners.contains(listener))
            downloadListeners.add(listener);
    }

    /**
     * Instructs this SRTM file downloader to download an SRTM file for the
     * specified tile ID and type. Downloading is performed in a separate thread.
     *
     * @param srtmTileID The SRTM tile ID for which to download the SRTM file.
     * @param srtmType   The SRTM type, SRTM1 or SRTM3.
     * @return A future to synchronize on availability of the downloaded file.
     * @throws RejectedExecutionException Thrown if the download task cannot be
     *                                    accepted for execution.
     */
    public Future<File> downloadSRTMFile(String srtmTileID, SRTMTile.Type srtmType) throws RejectedExecutionException {
        Callable<File> downloadTask = () -> {
            return download(srtmTileID, srtmType);
        };
        return downloadExecutor.submit(downloadTask);
    }

    private File download(String srtmTileID, SRTMTile.Type srtmType) {
        URL srtmBaseURL;
        if (srtmType == SRTMTile.Type.SRTM1)
            srtmBaseURL = srtm1BaseURL;
        else
            srtmBaseURL = srtm3BaseURL;
        String srtmFileName = SRTMFiles.getSRTMFileName(srtmTileID, srtmType);

        Logging.info("Elevation: Downloading SRTM file " + srtmFileName);
        downloadStarted(srtmTileID);
        File srtmFile = null;

        URL url = null;
        try {
            url = new URL(srtmBaseURL + srtmFileName);
        } catch (MalformedURLException e) {
            downloadFailed(srtmTileID);
            return null;
        }
        HttpClient httpClient = HttpClient.create(url);
        // Add the authorization bearer token to the HTTP header
        if (oAuthToken != null) {
            try {
                oAuthToken.sign(httpClient);
            } catch (OAuthException e) {
                Logging.error("Elevation: " + e.toString());
            }
        }
        try {
            httpClient.connect();
            int responseCode = httpClient.getResponse().getResponseCode();
            // https://urs.earthdata.nasa.gov/documentation/for_users/data_access/java
            if (responseCode != HttpURLConnection.HTTP_OK) {
                downloadFailed(srtmTileID);
                switch (responseCode) {
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    Logging.warn(
                            "Elevation: SRTM download server did not grant authorization. You may need to renew your Earthdata authorization bearer token!");
                    break;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    Logging.warn(
                            "Elevation: You need to authorize the application 'LP DAAC Data Pool' at Earthdata Login -> Applications!");
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    Logging.warn("Elevation: Requested SRTM file " + srtmFileName
                            + " was not found on the download server.");
                    break;
                default:
                    Logging.warn("Elevation: SRTM server responded: " + responseCode + " "
                            + httpClient.getResponse().getResponseMessage());
                    break;
                }
                return null;
            }
            InputStream in = httpClient.getResponse().getContent();
            Path downloadedZipFile = Paths.get(SRTMFileDownloader.this.srtmDirectory.toString(), srtmFileName);
            Files.copy(in, downloadedZipFile, StandardCopyOption.REPLACE_EXISTING);
            srtmFile = downloadedZipFile.toFile();
        } catch (IOException e) {
            Logging.error("Elevation: Downloading SRTM file " + srtmFileName + " failed due to I/O Exception: " + e.toString());
            downloadFailed(srtmTileID);
            return null;
        }

        // This would happen, if the downloaded file was uncompressed, but it does not
        // contain an appropriately named file (prefixed with SRTM tile ID)
        if (srtmFile == null) {
            Logging.error("Elevation: Downloaded compressed SRTM file " + srtmFileName
                    + " did not contain a file with the expected SRTM tile ID!");
            downloadFailed(srtmTileID);
            return null;
        }

        Logging.info("Elevation: Successfully downloaded SRTM file " + srtmFile.getName() + " to SRTM directory: "
                + SRTMFileDownloader.this.srtmDirectory.toString());
        downloadSucceeded(srtmFile);
        return srtmFile;
    }

    private void downloadStarted(String srtmTileID) {
        for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
            listener.srtmFileDownloadStarted(srtmTileID);
    }

    private void downloadFailed(String srtmTileID) {
        for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
            listener.srtmFileDownloadFailed(srtmTileID);
    }

    private void downloadSucceeded(File srtmFile) {
        for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
            listener.srtmFileDownloadSucceeded(srtmFile);
    }
}
