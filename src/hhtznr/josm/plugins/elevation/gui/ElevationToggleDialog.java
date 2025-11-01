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

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPluginTexts;
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

    // General
    private final JLabel lblRenderingLimit = new JLabel("Layer Rendering Map Size Limit:");
    private final JSpinner spRenderingLimit = new JSpinner(
            new SpinnerNumberModel(ElevationPreferences.getElevationLayerRenderingLimit(),
                    ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT));
    private final JLabel lblRenderingLimitUnit = new JLabel("°");

    // Contour lines
    private final JLabel lblIsostep = new JLabel("Contour Line Isostep:");
    private final JSpinner spIsostep = new JSpinner(new SpinnerNumberModel(ElevationPreferences.getContourLineIsostep(),
            1, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP, 1));
    private final JLabel lblIsostepUnit = new JLabel("m");

    private final JLabel lblUpperCutoff = new JLabel("Upper Cutoff Elevation:");
    private final JSpinner spUpperCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));
    private final JLabel lblUpperCutoffUnit = new JLabel("m");

    private final JLabel lblLowerCutoff = new JLabel("Lower Cutoff Elevation:");
    private final JSpinner spLowerCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));
    private final JLabel lblLowerCutoffUnit = new JLabel("m");

    // Hillshade
    private final JLabel lblHillshadeAltitude = new JLabel("Hillshade Illumination Source Altitude:");
    private final JSpinner spHillshadeAltitude = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAltitude(), ElevationPreferences.MIN_HILLSHADE_ALTITUDE,
            ElevationPreferences.MAX_HILLSHADE_ALTITUDE, ElevationPreferences.INCR_HILLSHADE_ALTITUDE));
    private final JLabel lblHillshadeAltitudeUnit = new JLabel("°");

    private final JLabel lblHillshadeAzimuth = new JLabel("Hillshade Illumination Source Azimuth:");
    private final JSpinner spHillshadeAzimuth = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAzimuth(), ElevationPreferences.MIN_HILLSHADE_AZIMUTH,
            ElevationPreferences.MAX_HILLSHADE_AZIMUTH, ElevationPreferences.INCR_HILLSHADE_AZIMUTH));
    private final JLabel lblHillshadeAzimuthUnit = new JLabel("°");

    /**
     * Constructs <code>ElevationToggleDialog</code>.
     */
    public ElevationToggleDialog(ElevationPlugin plugin) {
        super("Elevation Layer", "elevation.svg",
                "Dynamic, non-presistent configuration of selected elevation rendering settings", null, 150);
        elevationPlugin = plugin;

        spRenderingLimit.setToolTipText(ElevationPluginTexts.TOOL_TIP_LAYER_RENDERING_LIMIT);
        spUpperCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spLowerCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_LOWER_CUTOFF_ELEVATION);
        spHillshadeAltitude.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spHillshadeAzimuth.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_AZIMUTH);

        JPanel pnl = new JPanel(new GridBagLayout());

        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.insets = new Insets(5, 5, 0, 0);

        // Row "Layer rendering limit"
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

        // Row "Hillshade altitude"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblHillshadeAltitude, gc);

        gc.gridx++;
        pnl.add(spHillshadeAltitude, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblHillshadeAltitudeUnit, gc);

        // Row "Hillshade azimuth"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblHillshadeAzimuth, gc);

        gc.gridx++;
        pnl.add(spHillshadeAzimuth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblHillshadeAzimuthUnit, gc);

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
                // Interchange values
                if (upperCutoff < lowerCutoff) {
                    int temp = upperCutoff;
                    upperCutoff = lowerCutoff;
                    lowerCutoff = temp;
                    spUpperCutoffValue.setValue(Integer.valueOf(upperCutoff));
                    spLowerCutoffValue.setValue(Integer.valueOf(lowerCutoff));
                }
                int hillshadeAltitude = (Integer) spHillshadeAltitude.getValue();
                int hillshadeAzimuth = (Integer) spHillshadeAzimuth.getValue();
                elevationLayer.setRenderingLimit(renderingLimit);
                elevationLayer.setContourLineIsostep(isostep);
                elevationLayer.setLowerCutoffElevation(lowerCutoff);
                elevationLayer.setUpperCutoffElevation(upperCutoff);
                elevationLayer.setHillshadeIllumination(hillshadeAltitude, hillshadeAzimuth);
                elevationLayer.invalidate();
                MainApplication.getMap().mapView.repaint();
            }
        }
    }

}
