package hhtznr.josm.plugins.elevation.gui;

import java.io.File;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.MainApplication;

import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloadListener;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

/**
 * This class implements an option dialog that pops up if an errors occurs when
 * automatically downloading an SRTM file (e.g. authorization error). Displaying
 * of further dialogs can be suppressed by selecting the "Ignore errors" option.
 *
 * @author Harald Hetzner
 */
public class SRTMFileDownloadErrorDialog implements SRTMFileDownloadListener {

    private final SRTMFileDownloader srtmFileDownloader;

    private boolean ignoreErrors = false;

    /**
     * Creates a new SRTM file download error dialog.
     *
     * @param srtmFileDownloader The SRTM file auto-downloader to listen for errors.
     */
    public SRTMFileDownloadErrorDialog(SRTMFileDownloader srtmFileDownloader) {
        this.srtmFileDownloader = srtmFileDownloader;
        srtmFileDownloader.addDownloadListener(this);
    }

    @Override
    public void srtmFileDownloadStarted(String srtmTileID) {
    }

    @Override
    public void srtmFileDownloadSucceeded(File srtmFile, SRTMTile.Type type) {
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID, Exception exception) {
        if (ignoreErrors)
            return;

        String title = "Error downloading SRTM tile " + srtmTileID;
        String message = exception.getMessage();;
        if (exception instanceof SRTMFileDownloader.HTTPException) {
            String advice = ((SRTMFileDownloader.HTTPException) exception).getAdvice();
            if (advice != null)
                message = "<html>" + message + "<br><br>" + advice + "</html>";
        }
        String[] options = { "OK", "Ignore errors" };
        int selectedOption = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (selectedOption == JOptionPane.NO_OPTION)
            ignoreErrors = true;
    }

    /**
     * Disables this SRTM file download dialog by removing the download listener.
     */
    public void disable() {
        srtmFileDownloader.removeDownloadListener(this);
    }
}
