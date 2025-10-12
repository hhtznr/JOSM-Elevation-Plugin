package hhtznr.josm.plugins.elevation.gui;

import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;

/**
 * Toggle dialog for the right JOSM side pane to dynamically adjust rendering
 * options of the elevation layer.
 *
 * @author Harald Hetzner
 */
public class ElevationToggleDialog extends ToggleDialog {

    private static final long serialVersionUID = 2722645568569947314L;

    private final ElevationPlugin elevationPlugin;

    private final JLabel lblRenderingLimit = new JLabel("Layer Rendering Map Size Limit:");
    private final JSpinner spRenderingLimit = new JSpinner(
            new SpinnerNumberModel(ElevationPreferences.getElevationLayerRenderingLimit(),
                    ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT));
    private final JLabel lblRenderingLimitUnit = new JLabel("°");

    private final JLabel lblIsostep = new JLabel("Contour Line Isostep:");
    private final JSpinner spIsostep = new JSpinner(new SpinnerNumberModel(ElevationPreferences.getContourLineIsostep(),
            ElevationPreferences.MIN_CONTOUR_LINE_ISOSTEP, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP,
            ElevationPreferences.INCR_CONTOUR_LINE_ISOSTEP));
    private final JLabel lblIsostepUnit = new JLabel("m");

    private final JLabel lblUpperCutoff = new JLabel("Upper cutoff elevation:");
    private final JSpinner spUpperCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));
    private final JLabel lblUpperCutoffUnit = new JLabel("m");

    private final JLabel lblLowerCutoff = new JLabel("Lower cutoff elevation:");
    private final JSpinner spLowerCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));
    private final JLabel lblLowerCutoffUnit = new JLabel("m");

    /**
     * Constructs <code>ElevationToggleDialog</code>.
     */
    public ElevationToggleDialog(ElevationPlugin plugin) {
        super("Elevation Layer", "elevation.svg",
                "Dynamic, non-presistent configuration of selected elevation rendering settings", null, 150);
        elevationPlugin = plugin;

        spRenderingLimit.setToolTipText(
                "Layer rendering will be switched off if the map size (latitude, longitude) exceeds this value");
        spUpperCutoffValue.setToolTipText(
                "Contour lines and elevation raster points with an elevation less or equal this cutoff value will not be drawn");
        spLowerCutoffValue.setToolTipText(
                "Contour lines and elevation raster points with an elevation greater or equal this cutoff value will not be drawn");

        JPanel pnl = new JPanel(new GridBagLayout());

        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.insets = new Insets(5, 5, 0, 0);

        // Row "Layer rendering limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblRenderingLimit, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(spRenderingLimit, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblRenderingLimitUnit, gc);

        // Row "Contour line isostep"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblIsostep, gc);

        gc.gridx++;
        pnl.add(spIsostep, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblIsostepUnit, gc);

        // Row "Upper cutoff limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblUpperCutoff, gc);

        gc.gridx++;
        pnl.add(spUpperCutoffValue, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblUpperCutoffUnit, gc);

        // Row "Lower cutoff limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblLowerCutoff, gc);

        gc.gridx++;
        pnl.add(spLowerCutoffValue, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblLowerCutoffUnit, gc);

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());

        SideButton refreshButton = new SideButton(new RefreshAction());

        createLayout(pnl, false, Arrays.asList(refreshButton));
    }

    private class RefreshAction extends AbstractAction {

        private static final long serialVersionUID = -5732594099460050278L;

        public RefreshAction() {
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ElevationLayer elevationLayer = elevationPlugin.getElevationLayer();
            if (elevationLayer != null) {
                double renderingLimit = (Double) spRenderingLimit.getValue();
                int isostep = (Integer) spIsostep.getValue();
                int upperCutoff = (Integer) spUpperCutoffValue.getValue();
                int lowerCutoff = (Integer) spLowerCutoffValue.getValue();
                elevationLayer.setRenderingLimit(renderingLimit);
                elevationLayer.setContourLineIsostep(isostep);
                elevationLayer.setLowerCutoffElevation(lowerCutoff);
                elevationLayer.setUpperCutoffElevation(upperCutoff);
                elevationLayer.repaint();
            }
        }
    }

}
