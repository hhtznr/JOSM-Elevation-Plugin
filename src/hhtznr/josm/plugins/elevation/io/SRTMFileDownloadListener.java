package hhtznr.josm.plugins.elevation.io;

import java.io.File;

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
     */
    public void srtmFileDownloadStarted(String srtmTileID);

    /**
     * Informs the implementing class that the SRTM file was downloaded
     * successfully.
     *
     * To be called by the thread downloading as soon as the download finished.
     *
     * @param srtmFile The downloaded SRTM file.
     */
    public void srtmFileDownloadSucceeded(File srtmFile);

    /**
     * Informs the implementing class that downloading HGT data for the given
     * coordinates failed.
     *
     * To be called by the thread downloading as soon as downloading fails.
     *
     * @param srtmTileID The ID of the SRTM tile for which the SRTM file was
     *                   supposed to be downloaded.
     */
    public void srtmFileDownloadFailed(String srtmTileID);
}
