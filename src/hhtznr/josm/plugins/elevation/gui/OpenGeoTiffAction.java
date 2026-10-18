package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.GeoTiffImage;

/**
 * This class implements a JOSM menu action opening one or more GeoTIFF files.
 *
 * @author Harald Hetzner
 */
public class OpenGeoTiffAction extends DiskAccessAction {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@code OpenGeoTiffAction} instance. The action is registered
     * with the name "Open GeoTIFF" and is associated with the "open" icon. It
     * provides a tooltip description and is enabled by default.
     */
    public OpenGeoTiffAction() {
        super("Open GeoTIFF", "dialogs/open_geotiff.svg", "Open a GeoTIFF file.", null, true, "open-geotiff", false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String title = "Open a GeoTiff file";

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        fileChooser.setMultiSelectionEnabled(true);

        FileNameExtensionFilter geotiffFilter = new FileNameExtensionFilter(tr("GeoTIFF files (*.tif; *.tiff)"), "tif",
                "tiff");
        fileChooser.setFileFilter(geotiffFilter);

        // Load JOSM's last saved directory preference
        Path lastDirectory = ElevationPreferences.getGeoTiffLastDirectory();
        if (lastDirectory != null && Files.isDirectory(lastDirectory)) {
            fileChooser.setCurrentDirectory(lastDirectory.toFile());
        }

        // Open the modal dialog bound to JOSM's main window frame
        int result = fileChooser.showOpenDialog(MainApplication.getMainFrame());

        // Return if the user cancels the file chooser
        if (result != JFileChooser.APPROVE_OPTION) {
            Logging.info("Elevation: GeoTIFF file chooser canceled by user");
            return;
        }

        // Save the newly navigated directory path back into JOSM's system preferences
        File currentDirectory = fileChooser.getCurrentDirectory();
        if (currentDirectory != null) {
            ElevationPreferences.setGeoTiffLastDirectory(currentDirectory.toPath());
        }

        File[] files;
        if (fileChooser.isMultiSelectionEnabled()) {
            files = fileChooser.getSelectedFiles();
        } else {
            File singleFile = fileChooser.getSelectedFile();
            files = (singleFile != null) ? new File[] { singleFile } : new File[0];
        }

        Logging.info("Elevation: User selected " + files.length + " GeoTIFF files");
        for (File file : files) {
            Logging.info("Elevation: GeoTIFF file opened: " + file.getAbsolutePath());
            GeoTiffImage geoTiffImage = GeoTiffImage.readGeoTiffFile(file.toPath());
            if (geoTiffImage == null) {
                Logging.error("Elevation: Failed to read GeoTIFF file '" + file.getAbsolutePath() + "'");
                continue;
            }
            GeoTiffElevationLayer.addGeoTiffElevationLayer(geoTiffImage);
        }
    }
}
