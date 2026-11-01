package hhtznr.josm.plugins.elevation.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.gui.components.UIFactory;

/**
 * This class implements a JOSM extended dialog which shows information on
 * cached SRTM tiles and data structures building on them.
 *
 * @author Harald Hetzner
 */
public class SrtmElevationDataInfoDialog extends ExtendedDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new dialog providing structured information on on cached SRTM tiles
     * and data structures building on them
     *
     * @param parent                The parent component of the dialog.
     * @param elevationDataProvider The elevation data provider from which the
     *                              information on cached SRTM tiles and data
     *                              structures building on them will be retrieved.
     */
    public SrtmElevationDataInfoDialog(Component parent, ElevationDataProvider elevationDataProvider) {
        super(parent, "Information on cached SRTM elevation data", new String[] { "Close" }, false, true);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("SRTM Elevation Layer"), GBC.eol());
        List<String[]> content = new ArrayList<>();
        // Active tile grid
        List<String[]> tileGridInfo = elevationDataProvider.getTileGridInfo();
        for (String[] info : tileGridInfo) {
            content.add(info);
        }
        // Cache size
        content.add(new String[] { tr("SRTM tile cache"), "" + elevationDataProvider.getTileCacheInfo() });
        // Info on tiles -> type, status, size, source
        Map<String, String> cachedTilesInfo = elevationDataProvider.getCachedTilesInfo();
        for (Map.Entry<String, String> entry : cachedTilesInfo.entrySet()) {
            content.add(new String[] { entry.getKey(), entry.getValue() });
        }
        for (String[] entry : content) {
            panel.add(new JLabel(entry[0] + ':'), GBC.std());
            panel.add(GBC.glue(5, 0), GBC.std());
            panel.add(UIFactory.createInfoTextField(entry[1]), GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        setContent(scrollPane);
    }
}
