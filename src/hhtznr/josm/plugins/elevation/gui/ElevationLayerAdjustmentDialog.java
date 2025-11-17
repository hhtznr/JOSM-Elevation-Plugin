package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.ElevationPluginTexts;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * Dialog to dynamically adjust rendering options of the elevation layer.
 *
 * @author Harald Hetzner
 */
public class ElevationLayerAdjustmentDialog extends ExtendedDialog {

    private static final long serialVersionUID = 2722645568569947314L;

    private final ElevationLayer elevationLayer;

    // General
    private final JosmComboBox<SRTMTile.Type> comboBoxSRTMType = new JosmComboBox<>(
            new SRTMTile.Type[] { SRTMTile.Type.SRTM1, SRTMTile.Type.SRTM3 });
    private final JSpinner spRenderingLimit = new JSpinner(
            new SpinnerNumberModel(ElevationPreferences.getElevationLayerRenderingLimit(),
                    ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT));

    // Contour lines
    private final JSpinner spIsostep = new JSpinner(new SpinnerNumberModel(ElevationPreferences.getContourLineIsostep(),
            1, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP, 1));

    private final JSpinner spUpperCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));

    private final JSpinner spLowerCutoffValue = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1));

    private final JSpinner spStrokeWidth = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getContourLineStrokeWidth(), ElevationPreferences.MIN_CONTOUR_LINE_STROKE_WIDTH,
            ElevationPreferences.MAX_CONTOUR_LINE_STROKE_WIDTH, ElevationPreferences.INCR_CONTOUR_LINE_STROKE_WIDTH));

    private final ColorChooserButton btnStrokeColor;

    // Hillshade
    private final JSpinner spHillshadeAltitude = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAltitude(), ElevationPreferences.MIN_HILLSHADE_ALTITUDE,
            ElevationPreferences.MAX_HILLSHADE_ALTITUDE, ElevationPreferences.INCR_HILLSHADE_ALTITUDE));

    private final JSpinner spHillshadeAzimuth = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAzimuth(), ElevationPreferences.MIN_HILLSHADE_AZIMUTH,
            ElevationPreferences.MAX_HILLSHADE_AZIMUTH, ElevationPreferences.INCR_HILLSHADE_AZIMUTH));

    /**
     * Constructs a dialog that allows to dynamically adjust rendering parameters of
     * an elevation layer. Adjustment is non-peristent.
     *
     * @param parent The parent component of the dialog.
     * @param layer  The elevation layer.
     */
    public ElevationLayerAdjustmentDialog(Component parent, ElevationLayer layer) {
        super(parent, "Elevation Layer Adjustment", new String[] { tr("Refresh"), tr("Close") }, false, false);
        // Set the icon of the close button via the superclass method
        setButtonIcons("dialogs/refresh.svg", "misc/close.svg");

        elevationLayer = layer;

        comboBoxSRTMType.setSelectedItem(ElevationPreferences.getSRTMType());

        spRenderingLimit.setToolTipText(ElevationPluginTexts.TOOL_TIP_LAYER_RENDERING_LIMIT);
        spUpperCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spLowerCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_LOWER_CUTOFF_ELEVATION);

        btnStrokeColor = new ColorChooserButton(this, "Choose Color of Contour Lines",
                ElevationPreferences.getContourLineColor());

        spHillshadeAltitude.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spHillshadeAzimuth.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_AZIMUTH);

        JLabel labelSectionGeneral = new JLabel("General Layer Settings");
        labelSectionGeneral.setFont(labelSectionGeneral.getFont().deriveFont(Font.BOLD));
        JLabel labelSectionContourLines = new JLabel("Contour Lines");
        labelSectionContourLines.setFont(labelSectionContourLines.getFont().deriveFont(Font.BOLD));
        JLabel labelSectionHillshade = new JLabel("Hillshade");
        labelSectionHillshade.setFont(labelSectionHillshade.getFont().deriveFont(Font.BOLD));

        JPanel pnl = new JPanel(new GridBagLayout());

        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.insets = new Insets(5, 5, 0, 0);

        // Row "Section General"
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSectionGeneral, gc);

        // Row "SRTM Type"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("SRTM Type:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(comboBoxSRTMType, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // Row "Layer rendering limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Layer Rendering Map Size Limit:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(spRenderingLimit, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("°"), gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section Contour lines"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSectionContourLines, gc);

        // Row "Contour line isostep"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Isostep:"), gc);

        gc.gridx++;
        pnl.add(spIsostep, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("m"), gc);

        // Row "Upper cutoff limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Upper Cutoff Elevation:"), gc);

        gc.gridx++;
        pnl.add(spUpperCutoffValue, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("m"), gc);

        // Row "Lower cutoff limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Lower Cutoff Elevation:"), gc);

        gc.gridx++;
        pnl.add(spLowerCutoffValue, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("m"), gc);

        // Row "Contour line stroke width"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Contour Line Stroke Width:"), gc);

        gc.gridx++;
        pnl.add(spStrokeWidth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("px"), gc);

        // Row "Contour line color"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Contour Line Color:"), gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(btnStrokeColor, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section Hillshade"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSectionHillshade, gc);

        // Row "Hillshade altitude"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Illumination Source Altitude:"), gc);

        gc.gridx++;
        pnl.add(spHillshadeAltitude, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("°"), gc);

        // Row "Hillshade azimuth"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Illumination Source Azimuth:"), gc);

        gc.gridx++;
        pnl.add(spHillshadeAzimuth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("°"), gc);

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());

        setContent(pnl);
    }

    @Override
    protected void buttonAction(int buttonIndex, ActionEvent evt) {
        // Refresh button
        if (buttonIndex == 0) {
            refreshLayer();
        }
        // Close button
        else if (buttonIndex == 1) {
            setVisible(false);
        }
    }

    private void refreshLayer() {
        SRTMTile.Type srtmType = (SRTMTile.Type) comboBoxSRTMType.getSelectedItem();
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
        float strokeWidth = ((Double) spStrokeWidth.getValue()).floatValue();
        Color color = btnStrokeColor.getSelectedColor();
        int hillshadeAltitude = (Integer) spHillshadeAltitude.getValue();
        int hillshadeAzimuth = (Integer) spHillshadeAzimuth.getValue();
        elevationLayer.getElevationDataProvider().setSRTMType(srtmType);
        elevationLayer.setRenderingLimit(renderingLimit);
        elevationLayer.setContourLineIsostep(isostep);
        elevationLayer.setLowerCutoffElevation(lowerCutoff);
        elevationLayer.setUpperCutoffElevation(upperCutoff);
        elevationLayer.setContourLineStrokeWidth(strokeWidth);
        elevationLayer.setContourLineColor(color);
        elevationLayer.setHillshadeIllumination(hillshadeAltitude, hillshadeAzimuth);
        elevationLayer.invalidate();
        MainApplication.getMap().mapView.repaint();
    }
}
