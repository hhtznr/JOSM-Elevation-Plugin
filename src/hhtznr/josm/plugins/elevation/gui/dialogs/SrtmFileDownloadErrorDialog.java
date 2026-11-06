package hhtznr.josm.plugins.elevation.gui.dialogs;

import java.net.HttpURLConnection;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Utils;

import hhtznr.josm.plugins.elevation.data.srtm.SrtmDataSource;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.io.SrtmFileDownloadListener;
import hhtznr.josm.plugins.elevation.io.SrtmFileDownloader;

/**
 * This class implements an option dialog that pops up if an errors occurs when
 * automatically downloading an SRTM file (e.g. authorization error). Displaying
 * of further dialogs can be suppressed by selecting the "Ignore errors" option.
 *
 * @author Harald Hetzner
 */
public class SrtmFileDownloadErrorDialog implements SrtmFileDownloadListener {

    private final SrtmFileDownloader srtmFileDownloader;

    private boolean ignoreErrors = false;

    /**
     * Creates a new SRTM file download error dialog.
     *
     * @param srtmFileDownloader The SRTM file auto-downloader to listen for errors.
     */
    public SrtmFileDownloadErrorDialog(SrtmFileDownloader srtmFileDownloader) {
        this.srtmFileDownloader = srtmFileDownloader;
        srtmFileDownloader.addDownloadListener(this);
    }

    @Override
    public void srtmFileDownloadStarted(String srtmTileID, SrtmTile.Type type, SrtmDataSource dataSource) {
    }

    @Override
    public void srtmFileDownloadSucceeded(Path srtmFile, SrtmTile.Type type, SrtmDataSource dataSource) {
    }

    @Override
    public void srtmFileDownloadFailed(String srtmTileID, SrtmTile.Type type, SrtmDataSource dataSource,
            Exception exception) {
        if (ignoreErrors) {
            return;
        }

        final String optionOK = "OK";
        final String optionIgnore = "Ignore errors";
        final String optionRememberMissing = "Remember tile as permanently missing";
        String[] options = null;

        String title = "Error downloading SRTM tile " + srtmTileID;
        String message = exception.getMessage();
        if (exception instanceof SrtmFileDownloader.HTTPException) {
            SrtmFileDownloader.HTTPException httpException = (SrtmFileDownloader.HTTPException) exception;
            String advice = httpException.getAdvice();
            if (advice != null) {
                message = "<html>" + Utils.escapeReservedCharactersHTML(message) + "<br><br>"
                        + Utils.escapeReservedCharactersHTML(advice) + "</html>";
            }
            if (httpException.getResponse().getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                options = new String[3];
                options[2] = optionRememberMissing;
            }
        }

        if (options == null) {
            options = new String[2];
        }
        options[0] = optionOK;
        options[1] = optionIgnore;

        int choice = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);

        if (choice == JOptionPane.CLOSED_OPTION) {
            return;
        }

        String selectedOption = options[choice];

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
