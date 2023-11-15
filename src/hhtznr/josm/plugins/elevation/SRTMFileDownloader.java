package hhtznr.josm.plugins.elevation;

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

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;

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

    private String authHeader;

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
     * @param bearer        The authorization bearer token to use. See also
     *                      {@link ElevationPreferences#ELEVATION_SERVER_AUTH_BEARER
     *                      ElevationPreferences.ELEVATION_SERVER_AUTH_BEARER} and
     *                      {@link ElevationPreferences#SRTM_SERVER_REGISTRATION_URL
     *                      ElevationPreferences.SRTM_SERVER_REGISTRATION_URL}.
     * @throws MalformedURLException Thrown if the URL is not properly formatted.
     */
    public SRTMFileDownloader(File srtmDirectory, String srtm1BaseURL, String srtm3BaseURL, String bearer)
            throws MalformedURLException {
        // May throw MalformedURLException
        this.srtm1BaseURL = new URL(srtm1BaseURL);
        this.srtm3BaseURL = new URL(srtm3BaseURL);
        // https://stackoverflow.com/questions/38085964/authorization-bearer-token-in-httpclient
        if (!bearer.equals(""))
            authHeader = "Bearer " + bearer;
        else
            authHeader = null;
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
                Config.getPref().get(ElevationPreferences.ELEVATION_SERVER_AUTH_BEARER,
                        ElevationPreferences.DEFAULT_ELEVATION_SERVER_AUTH_BEARER));
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
     * @throws RejectedExecutionException Thrown if the download task cannot be accepted for execution.
     */
    public Future<File> downloadSRTMFile(String srtmTileID, SRTMTile.Type srtmType) throws RejectedExecutionException {
        DownloadSRTMFileTask downloadTask = new DownloadSRTMFileTask(srtmTileID, srtmType);
        return downloadExecutor.submit(downloadTask);
    }

    /**
     * Task class to be executed by a thread executor service for downloading an
     * SRTM file from NASA's servers.
     */
    private class DownloadSRTMFileTask implements Callable<File> {

        private final String srtmTileID;
        private final SRTMTile.Type srtmType;

        public DownloadSRTMFileTask(String srtmTileID, SRTMTile.Type srtmType) {
            this.srtmTileID = srtmTileID;
            this.srtmType = srtmType;
        }

        @Override
        public File call() {
            URL srtmBaseURL;
            if (srtmType == SRTMTile.Type.SRTM1)
                srtmBaseURL = srtm1BaseURL;
            else
                srtmBaseURL = srtm3BaseURL;
            String srtmFileName = SRTMFiles.getSRTMFileName(srtmTileID, srtmType);

            Logging.info("Elevation: Downloading STRM file " + srtmFileName);
            downloadStarted(srtmTileID);
            File srtmFile = null;

            URL url = null;
            try {
                url = new URL(srtmBaseURL + srtmFileName);
            } catch (MalformedURLException e) {
                downloadFailed();
                return null;
            }
            HttpClient httpClient = HttpClient.create(url);
            if (authHeader != null)
                httpClient.setHeader("Authorization", authHeader);
            HttpClient.Response response = null;
            try {
                response = httpClient.connect();
                // https://urs.earthdata.nasa.gov/documentation/for_users/data_access/java
                if (response.getResponseCode() != 200) {
                    downloadFailed();
                    return null;
                }
                InputStream in = response.getContent();
                Path downloadedZipFile = Paths.get(SRTMFileDownloader.this.srtmDirectory.toString(), srtmFileName);
                Files.copy(in, downloadedZipFile, StandardCopyOption.REPLACE_EXISTING);
                srtmFile = downloadedZipFile.toFile();
            } catch (IOException e) {
                if (response != null) {
                    int responseCode = response.getResponseCode();
                    String responseMessage = response.getResponseMessage();
                    Logging.error("Elevation: SRTM server responded: " + responseCode + " " + responseMessage);
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED)
                        Logging.info(
                                "Elevation: SRTM download server did not grant authorization. You may need to renew your authorization bearer token!");
                    else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
                        Logging.info("Elevation: Requested SRTM file " + srtmFileName
                                + " was not found on the download server.");
                }
                Logging.error("Elevation: Downloading SRTM file " + srtmFileName + " failed: " + e.toString());
                downloadFailed();
                return null;
            }

            // This would happen, if the downloaded file was uncompressed, but it does not
            // contain an appropriately named file (prefixed with SRTM tile ID)
            if (srtmFile == null) {
                Logging.error("Elevation: Downloaded compressed SRTM file " + srtmFileName
                        + " did not contain a file with the expected STRM tile ID!");
                downloadFailed();
                return null;
            }

            Logging.info("Elevation: Successfully downloaded SRTM file " + srtmFile.getName() + " to SRTM directory: "
                    + SRTMFileDownloader.this.srtmDirectory.toString());
            downloadSucceeded(srtmFile);
            return srtmFile;
        }

        private void downloadStarted(String strmTileID) {
            for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
                listener.srtmFileDownloadStarted(strmTileID);
        }

        private void downloadSucceeded(File srtmFile) {
            for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
                listener.srtmFileDownloadSucceeded(srtmFile);
        }

        private void downloadFailed() {
            for (SRTMFileDownloadListener listener : SRTMFileDownloader.this.downloadListeners)
                listener.srtmFileDownloadFailed(srtmTileID);
        }
    }
}
