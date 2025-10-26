package hhtznr.josm.plugins.elevation.io;

import java.io.File;

import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed about SRTM download events being performed in separate threads.
 *
 * @author Harald Hetzner
 */
public interface SRTMFileDownloadListener {

    /**
     * Informs the implementing class that downloading of the SRTM file has started.
     *
     * To be called by the thread downloading as soon as downloading actually
     * started.
     *
     * @param srtmTileID The ID of the SRTM tile for which the SRTM file is now
     *                   being downloaded.
     * @param type       The type of SRTM data to be downloaded, SRTM1 or SRTM3.
     * @param dataSource The original source of the SRTM tile.
     */
    public void srtmFileDownloadStarted(String srtmTileID, SRTMTile.Type type, ElevationDataSource dataSource);

    /**
     * Informs the implementing class that the SRTM file was downloaded
     * successfully.
     *
     * To be called by the thread downloading as soon as the download finished.
     *
     * @param srtmFile   The downloaded SRTM file.
     * @param type       The type of the downloaded SRTM data, SRTM1 or SRTM3.
     * @param dataSource The original source of the SRTM tile.
     */
    public void srtmFileDownloadSucceeded(File srtmFile, SRTMTile.Type type, ElevationDataSource dataSource);

    /**
     * Informs the implementing class that downloading SRTM data for the given
     * coordinates failed.
     *
     * To be called by the thread downloading as soon as downloading fails.
     *
     * @param srtmTileID The ID of the SRTM tile for which the SRTM file was
     *                   supposed to be downloaded.
     * @param type       The type of the SRTM data that should have been downloaded,
     *                   SRTM1 or SRTM3.
     * @param dataSource The original source of the SRTM tile.
     * @param exception  The exception associated with the failed download.
     */
    public void srtmFileDownloadFailed(String srtmTileID, SRTMTile.Type type, ElevationDataSource dataSource,
            Exception exception);
}
