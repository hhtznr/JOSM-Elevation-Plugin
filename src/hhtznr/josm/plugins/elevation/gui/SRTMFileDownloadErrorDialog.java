package hhtznr.josm.plugins.elevation.gui;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.MainApplication;

import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
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
    public void srtmFileDownloadStarted(String srtmTileID, SRTMTile.Type type, ElevationDataSource dataSource) {
    }

    @Override
    public void srtmFileDownloadSucceeded(File srtmFile, SRTMTile.Type type, ElevationDataSource dataSource) {
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID, SRTMTile.Type type, ElevationDataSource dataSource,
            Exception exception) {
        if (ignoreErrors)
            return;

        final String optionOK = "OK";
        final String optionIgnore = "Ignore errors";
        final String optionRememberMissing = "Remember tile as permanently missing";

        List<String> optionsList = new ArrayList<>(3);
        optionsList.add(optionOK);
        optionsList.add(optionIgnore);

        String title = "Error downloading SRTM tile " + srtmTileID;
        String message = exception.getMessage();
        if (exception instanceof SRTMFileDownloader.HTTPException) {
            SRTMFileDownloader.HTTPException httpException = (SRTMFileDownloader.HTTPException) exception;
            String advice = httpException.getAdvice();
            if (advice != null)
                message = "<html>" + message + "<br><br>" + advice + "</html>";
            if (httpException.getResponse().getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
                optionsList.add(optionRememberMissing);
        }
        Object[] options = optionsList.toArray();
        int choice = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);

        if (choice == JOptionPane.CLOSED_OPTION)
            return;

        String selectedOption = (String) options[choice];

        switch (selectedOption) {
        case optionIgnore:
            // Ignore errors, i.e. do not show a dialog again
            ignoreErrors = true;
            break;

        case optionRememberMissing:
            // Add to the list of permanently missing tiles (saved to file)
            dataSource.addPermanentlyMissingSRTMTile(srtmTileID);
            break;

        case optionOK:
        default:
            // Just close the dialog
            break;
        }
    }

    /**
     * Disables this SRTM file download dialog by removing the download listener.
     */
    public void disable() {
        srtmFileDownloader.removeDownloadListener(this);
    }
}
