package hhtznr.josm.plugins.elevation.gui.dialogs;

import java.awt.Component;

import javax.swing.JScrollPane;

import org.openstreetmap.josm.gui.ExtendedDialog;

import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImage;
import hhtznr.josm.plugins.elevation.gui.components.GeoTiffMetadataPanel;

/**
 * This class implements a JOSM extended dialog which shows metadata information
 * on a GeoTIFF file.
 *
 * @author Harald Hetzner
 */
public class GeoTiffInfoDialog extends ExtendedDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new dialog providing structured information on a GeoTIFF image
     * including relevant metadata.
     *
     * @param parent       The parent component of the dialog.
     * @param geoTiffImage The GeoTIFF image.
     */
    public GeoTiffInfoDialog(Component parent, GeoTiffImage geoTiffImage) {
        super(parent, "Information on GeoTIFF " + geoTiffImage.getAbsolutePath().getFileName().toString(),
                new String[] { "Close" }, false, true);

        GeoTiffMetadataPanel panel = new GeoTiffMetadataPanel(geoTiffImage);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        setContent(scrollPane);
    }
}
