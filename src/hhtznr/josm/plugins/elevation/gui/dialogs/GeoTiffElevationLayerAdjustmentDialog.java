package hhtznr.josm.plugins.elevation.gui.dialogs;

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
import javax.swing.JScrollPane;
import javax.swing.JSpinner;

import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.ElevationPluginTexts;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.gui.components.ColorChooserButton;
import hhtznr.josm.plugins.elevation.gui.components.UIFactory;
import hhtznr.josm.plugins.elevation.gui.layers.GeoTiffElevationLayer;
import hhtznr.josm.plugins.elevation.util.Coloring;

/**
 * Dialog to dynamically adjust rendering options of a GeoTIFF elevation layer.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationLayerAdjustmentDialog extends ExtendedDialog {

    private static final long serialVersionUID = -1L;

    private GeoTiffElevationLayer elevationLayer = null;
    private final Object elevationLayerLock = new Object();

    // Contour lines
    private final JSpinner spIsostep = UIFactory.createSpinner(ElevationPreferences.getContourLineIsostep(), 1,
            ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP, 1);

    private final JSpinner spUpperCutoffValue = UIFactory.createSpinner(
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1);

    private final JSpinner spLowerCutoffValue = UIFactory.createSpinner(
            ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION, ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
            ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION, 1);

    private final JSpinner spStrokeWidth = UIFactory.createSpinner(ElevationPreferences.getContourLineStrokeWidth(),
            ElevationPreferences.MIN_CONTOUR_LINE_STROKE_WIDTH, ElevationPreferences.MAX_CONTOUR_LINE_STROKE_WIDTH,
            ElevationPreferences.INCR_CONTOUR_LINE_STROKE_WIDTH);

    private final JosmComboBox<Coloring.Scheme> comboBoxColoring = new JosmComboBox<>(
            new Coloring.Scheme[] { Coloring.Scheme.CONSTANT_COLOR, Coloring.Scheme.FALSE_COLOR });

    private final ColorChooserButton btnStrokeColor = new ColorChooserButton(this, "Choose Color of Contour Lines",
            ElevationPreferences.getContourLineColor());

    /**
     * Constructs a dialog that allows to dynamically adjust rendering parameters of
     * a GeoTIFF elevation layer. Adjustment is non-peristent.
     *
     * @param parent The parent component of the dialog.
     * @param layer  The elevation layer.
     */
    public GeoTiffElevationLayerAdjustmentDialog(Component parent, GeoTiffElevationLayer layer) {
        super(parent, getDialogTitle(layer), new String[] { tr("Refresh"), tr("Close") }, false, false);
        // Set the icon of the close button via the superclass method
        setButtonIcons("dialogs/refresh.svg", "misc/close.svg");

        // Sets the layer and sets the values of the components accordingly
        setGeoTiffElevationLayer(layer);

        spUpperCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spLowerCutoffValue.setToolTipText(ElevationPluginTexts.TOOL_TIP_LOWER_CUTOFF_ELEVATION);

        comboBoxColoring.setSelectedItem(ElevationPreferences.getContourLineColoringScheme());

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

        // Row "Section Contour lines"
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

        // Row "Contour line coloring scheme"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Contour Line Coloring Scheme:"), gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(comboBoxColoring, gc);

        // Row "Contour line constant color"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Contour Line Constant Color:"), gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(btnStrokeColor, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());

        JScrollPane scrollPane = new JScrollPane(pnl);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        setContent(scrollPane);
    }

    private static String getDialogTitle(GeoTiffElevationLayer layer) {
        return layer.getName() + " Adjustment";
    }

    /**
     * Returns the {@link GeoTiffElevationLayer} currently handled by this
     * adjustment dialog.
     *
     * @return The GeoTIFF elevation layer.a
     */
    public GeoTiffElevationLayer getGeoTiffElevationLayer() {
        synchronized (elevationLayerLock) {
            return elevationLayer;
        }
    }

    /**
     * Sets the GeoTIFF elevation layer handled by this adjustment dialog to a new
     * layer.
     *
     * @param newLayer The new layer to set as layer handled by this adjustment
     *                 dialog.
     * @return {@code true} if the layer is not identical to the layer currently
     *         handled by this adjustment dialog, {@code false} otherwise.
     */
    public boolean setGeoTiffElevationLayer(GeoTiffElevationLayer newLayer) {
        synchronized (elevationLayerLock) {
            if (elevationLayer == null && newLayer == null) {
                return false;
            }

            if (elevationLayer != null && elevationLayer.equals(newLayer)) {
                return false;
            }

            setTitle(getDialogTitle(newLayer));
            spIsostep.setValue(Integer.valueOf(newLayer.getContourLineIsostep()));
            spUpperCutoffValue.setValue(Integer.valueOf(newLayer.getUpperCutoffElevation()));
            spLowerCutoffValue.setValue(Integer.valueOf(newLayer.getLowerCutoffElevation()));
            spStrokeWidth.setValue(Double.valueOf(newLayer.getContourLineStrokeWidth()));
            comboBoxColoring.setSelectedItem(newLayer.getContourLineColoringScheme());
            btnStrokeColor.setSelectedColor(newLayer.getContourLineConstantColor());

            elevationLayer = newLayer;
        }
        return true;
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
        synchronized (elevationLayerLock) {
            int isostep = (Integer) spIsostep.getValue();
            short upperCutoff = ((Integer) spUpperCutoffValue.getValue()).shortValue();
            short lowerCutoff = ((Integer) spLowerCutoffValue.getValue()).shortValue();
            // Interchange values
            if (upperCutoff < lowerCutoff) {
                short temp = upperCutoff;
                upperCutoff = lowerCutoff;
                lowerCutoff = temp;
                spUpperCutoffValue.setValue(Integer.valueOf(upperCutoff));
                spLowerCutoffValue.setValue(Integer.valueOf(lowerCutoff));
            }
            float strokeWidth = ((Double) spStrokeWidth.getValue()).floatValue();
            Coloring.Scheme scheme = (Coloring.Scheme) comboBoxColoring.getSelectedItem();
            Color color = btnStrokeColor.getSelectedColor();
            elevationLayer.setContourLineIsostep(isostep);
            elevationLayer.setLowerCutoffElevation(lowerCutoff);
            elevationLayer.setUpperCutoffElevation(upperCutoff);
            elevationLayer.setContourLineStrokeWidth(strokeWidth);
            elevationLayer.setContourLineColoringScheme(scheme);
            elevationLayer.setContourLineConstantColor(color);
            elevationLayer.triggerRepaint();
        }
    }
}
