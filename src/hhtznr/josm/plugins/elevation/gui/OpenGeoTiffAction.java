package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.geotools.api.data.DataSourceException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.GeoTiffImageException;

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
        super("Open GeoTIFF", "open", "Open a GeoTIFF file.", null, true, "open-geotiff", false);
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
        String lastDirectory = Config.getPref().get(ElevationPreferences.ELEVATION_GEOTIFF_LAST_DIRECTORY, null);
        if (lastDirectory != null && !lastDirectory.isEmpty()) {
            File geoTiffDirectory = new File(lastDirectory);
            if (geoTiffDirectory.exists() && geoTiffDirectory.isDirectory()) {
                fileChooser.setCurrentDirectory(geoTiffDirectory);
            }
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
            Config.getPref().put(ElevationPreferences.ELEVATION_GEOTIFF_LAST_DIRECTORY,
                    currentDirectory.getAbsolutePath());
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
            GeoTiffImage geoTiffImage = readGeoTiffFile(file);
            if (geoTiffImage == null) {
                Logging.error("Elevation: Failed to read GeoTIFF file '" + file.getAbsolutePath() + "'");
                continue;
            }
            addGeoTiffLayer(geoTiffImage);
        }
    }

    private GeoTiffImage readGeoTiffFile(File file) {
        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(file);
        } catch (DataSourceException e) {
            Logging.error("Elevation: Cannot read GeoTIFF file '" + file.getAbsolutePath() + " due to " + e.toString());
            return null;
        }

        GridCoverage2D coverage;
        try {
            coverage = reader.read(null);
        } catch (IOException e) {
            Logging.error("Elevation: Cannot read GeoTIFF file '" + file.getAbsolutePath() + " due to " + e.toString());
            return null;
        }
        try {
            return new GeoTiffImage(file, coverage);
        } catch (GeoTiffImageException e) {
            Logging.error("Elevation: Cannot create GeoTiffImage for file '" + file.getAbsolutePath() + " due to "
                    + e.toString() + " with cause: " + e.getCause());
            return null;
        }
    }

    private void addGeoTiffLayer(GeoTiffImage geoTiffImage) {
        String layerName = "GeoTIFF " + geoTiffImage.getFile().getName();

        MainLayerManager layerManager = MainApplication.getLayerManager();
        List<GeoTiffElevationLayer> existingGeoTiffLayers = layerManager.getLayersOfType(GeoTiffElevationLayer.class);

        for (GeoTiffElevationLayer existingLayer : existingGeoTiffLayers) {
            if (existingLayer.getName().equals(layerName)) {
                Logging.info("Elevation: Not adding GeoTIFF layer for '" + geoTiffImage.getFile().getAbsolutePath()
                        + "' because a GeoTiFFLayer with the same name already exists");
                return;
            }
        }

        GeoTiffElevationLayer geoTiffLayer = new GeoTiffElevationLayer(layerName, geoTiffImage);
        layerManager.addLayer(geoTiffLayer);
    }
}
