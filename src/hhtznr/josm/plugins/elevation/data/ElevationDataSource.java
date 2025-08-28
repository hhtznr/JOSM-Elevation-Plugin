package hhtznr.josm.plugins.elevation.data;

import java.io.File;
import java.net.URL;

/**
 * Class {@code ElevationDataSource}
 *
 * @author Harald Hetzner
 */
public class ElevationDataSource {

    private final String name;
    private final File dataDirectory;
    private final SRTMTile.Type srtmTileType;
    private final URL downloadBaseURL;
    private final String ssoHost;
    private final boolean canAutoDownload;

    /**
     * Creates a new elevation data source.
     *
     * @param name            The name of the elevation data source.
     * @param dataDirectory   The local directory where elevation data files are
     *                        stored.
     * @param srtmTileType    The type of SRTM data, SRTM1 or SRTM3.
     * @param downloadBaseURL A base URL, which can be extended by an SRTM file name
     *                        in order to download an SRTM file.
     * @param ssoHost         A single sign-on host where to authorize file
     *                        downloads.
     * @param canAutoDownload If {@code true}, it is assumed that automatic download
     *                        of SRTM files from the given base URL is possible.
     */
    public ElevationDataSource(String name, File dataDirectory, SRTMTile.Type srtmTileType, URL downloadBaseURL,
            String ssoHost, boolean canAutoDownload) {
        this.name = name;
        this.dataDirectory = dataDirectory;
        this.srtmTileType = srtmTileType;
        this.downloadBaseURL = downloadBaseURL;
        this.ssoHost = ssoHost;
        this.canAutoDownload = canAutoDownload;
    }

    /**
     * Returns the name of this elevation data source.
     *
     * @return The name of this elevation data source.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the data directory of this elevation data source.
     *
     * @return The data directory of this elevation data source.
     */
    public File getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Returns the SRTM type of this elevation data source.
     *
     * @return The SRTM type of this elevation data source, SRTM1 or SRTM3.
     */
    public SRTMTile.Type getSRTMTileType() {
        return srtmTileType;
    }

    /**
     * Returns the base URL where SRTM files of this elevation data source are
     * available for download.
     *
     * @return The base URL where SRTM files of this elevation data source are
     *         available for download.
     */
    public URL getDownloadBaseURL() {
        return downloadBaseURL;
    }

    /**
     * A single sign-on host where to authorize prior to downloading SRTM files.
     *
     * @return The hostname of a single sign-on host.
     */
    public String getSSOHost() {
        return ssoHost;
    }

    /**
     * Returns whether automatic file download of locally missing SRTM files from
     * this elevation data source is possible.
     *
     * @return If {@code true}, automatic download of SRTM files from the base URL
     *         of this elevation data source is possible.
     */
    public boolean canAutoDownload() {
        return canAutoDownload;
    }
}
