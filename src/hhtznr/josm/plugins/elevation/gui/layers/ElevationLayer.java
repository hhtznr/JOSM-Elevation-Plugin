package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.util.Coloring;

/**
 * This is the abstract super class of JOSM layers for visualizing elevation
 * data.
 *
 * @author Harald Hetzner
 */
public abstract class ElevationLayer extends Layer {

    private final Icon icon;

    private int contourLineIsostep;
    private float contourLineStrokeWidth;
    private Coloring.Scheme contourLineColoringScheme;
    private Color contourLineConstantColor;

    private short lowerCutoffElevation;
    private short upperCutoffElevation;

    private boolean contourLinesEnabled;
    private boolean lowestAndHighestPointsEnabled;

    /**
     * Creates a new abstract elevation layer for visualizing elevation data.
     *
     * @param name                      The name of the layer. This name will be
     *                                  displayed to the JOSM user in the layer
     *                                  list.
     * @param icon                      The image icon of the layer. This icon will
     *                                  be displayed to the JOSM user in the layer
     *                                  list.
     * @param contourLineIsostep        Step between neighboring elevation contour
     *                                  lines.
     * @param contourLineStrokeWidth    Width of the contour line stroke in px.
     * @param contourLineColoringScheme The coloring scheme of the contour lines.
     * @param contourLineConstantColor  Constant color of the contour lines.
     * @param lowerCutoffElevation      The elevation value below which elevation
     *                                  should not be visualized.
     * @param upperCutoffElevation      The elevation value above which elevation
     *                                  should not be visualized.
     */
    public ElevationLayer(String name, Icon icon, int contourLineIsostep, float contourLineStrokeWidth,
            Coloring.Scheme contourLineColoringScheme, Color contourLineConstantColor, short lowerCutoffElevation,
            short upperCutoffElevation) {
        super(name);
        this.icon = icon;
        this.contourLineIsostep = contourLineIsostep;
        this.contourLineStrokeWidth = contourLineStrokeWidth;
        this.contourLineColoringScheme = contourLineColoringScheme;
        this.contourLineConstantColor = contourLineConstantColor;
        this.lowerCutoffElevation = lowerCutoffElevation;
        this.upperCutoffElevation = upperCutoffElevation;

        contourLinesEnabled = ElevationPreferences.getContourLinesEnabled();
        lowestAndHighestPointsEnabled = ElevationPreferences.getLowestAndHighestPointsEnabled();
    }

    /**
     * Returns whether elevation contour lines are enabled.
     *
     * @return {@code true} if displaying of elevation contour lines is enabled.
     */
    public boolean isContourLinesEnabled() {
        return contourLinesEnabled;
    }

    /**
     * Returns whether displaying of the lowest and highest points within the map
     * view is enabled.
     *
     * @return {@code true} if displaying of the lowest and highest points within
     *         the map view is enabled.
     */
    public boolean isLowestAndHighestPointsEnabled() {
        return lowestAndHighestPointsEnabled;
    }

    /**
     * Returns the step between adjacent elevation contour lines.
     *
     * @return The step between adjacent elevation contour lines.
     */
    public int getContourLineIsostep() {
        return contourLineIsostep;
    }

    /**
     * Sets a new isostep.
     *
     * @param isostep Step between neighboring elevation contour lines.
     */
    public void setContourLineIsostep(int isostep) {
        contourLineIsostep = isostep;
    }

    /**
     * Returns the contour line stroke width.
     *
     * @return The width of the contour line stroke in px.
     */
    public float getContourLineStrokeWidth() {
        return contourLineStrokeWidth;
    }

    /**
     * Sets the width of the contour line stroke to a new value.
     *
     * @param width The new width of the contour line stroke in px.
     */
    public void setContourLineStrokeWidth(float width) {
        contourLineStrokeWidth = width;
    }

    /**
     * Returns the coloring scheme of the contour lines.
     *
     * @return The coloring scheme.
     */
    public Coloring.Scheme getContourLineColoringScheme() {
        return contourLineColoringScheme;
    }

    /**
     * Sets the coloring scheme of the contour lines to a new value.
     *
     * @param scheme The new coloring scheme of the contour lines.
     */
    public void setContourLineColoringScheme(Coloring.Scheme scheme) {
        contourLineColoringScheme = scheme;
    }

    /**
     * Returns the constant color of the contour lines.
     *
     * @return The constant color of the contour lines.
     */
    public Color getContourLineConstantColor() {
        return contourLineConstantColor;
    }

    /**
     * Sets the constant color of the contour lines to a new value.
     *
     * @param color The new constant color of the contour lines.
     */
    public void setContourLineConstantColor(Color color) {
        contourLineConstantColor = color;
    }

    /**
     * Returns the elevation below which elevation data should not be visualized.
     *
     * @return The elevation value below which elevation data should not be
     *         visualized.
     */
    public short getLowerCutoffElevation() {
        return lowerCutoffElevation;
    }

    /**
     * Sets the elevation below which elevation data should not be visualized.
     *
     * @param value The elevation value below which elevation data should not be
     *              visualized.
     */
    public void setLowerCutoffElevation(short value) {
        lowerCutoffElevation = value;
    }

    /**
     * Returns the elevation above which elevation data should not be visualized.
     *
     * @return The elevation value above which elevation data should not be
     *         visualized.
     */
    public short getUpperCutoffElevation() {
        return upperCutoffElevation;
    }

    /**
     * Sets the elevation above which elevation data should not be visualized.
     *
     * @param value The elevation value above which elevation data should not be
     *              visualized.
     */
    public void setUpperCutoffElevation(short value) {
        upperCutoffElevation = value;
    }

    /**
     * Initiates a repaint of the layer by invalidating it and requesting a repaint
     * of the map view if the layer is currently visible. If it is not visible, it
     * will be painted in its current configuration as soon as it becomes visible.
     */
    public void triggerRepaint() {
        invalidate();
        if (isVisible()) {
            MainApplication.getMap().mapView.repaint();
        }
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(LayerListDialog.getInstance().createShowHideLayerAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowContourLinesAction());
        actions.add(new ShowLowestAndHighestPointsAction());
        return actions.toArray(new Action[0]);
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public String getToolTipText() {
        return null;
    }

    @Override
    public void mergeFrom(Layer from) {
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    protected static abstract class AbstractElevationLayerAction extends AbstractAction implements LayerAction {

        private static final long serialVersionUID = 1L;

        public AbstractElevationLayerAction(String actionName, String iconName) {
            putValue(NAME, actionName);
            new ImageProvider("dialogs", iconName).getResource().attachImageIcon(this, true);
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            if (layers.size() == 0) {
                return false;
            }
            for (Layer layer : layers) {
                if (!(layer instanceof SrtmElevationLayer)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class ShowContourLinesAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowContourLinesAction() {
            super("Enable/disable contour lines", "contour_lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ElevationLayer layer = ElevationLayer.this;
            layer.contourLinesEnabled = !layer.contourLinesEnabled;
            ElevationPreferences.setContourLinesEnabled(layer.contourLinesEnabled);
            if (layer.contourLinesEnabled) {
                Logging.info("Elevation: Contour lines enabled");
            } else {
                Logging.info("Elevation: Contour lines disabled");
            }
            layer.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(ElevationLayer.this.contourLinesEnabled);
            return item;
        }
    }

    private class ShowLowestAndHighestPointsAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowLowestAndHighestPointsAction() {
            super("Enable/disable lowest and highest points", "lowest_highest_points");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ElevationLayer layer = ElevationLayer.this;
            layer.lowestAndHighestPointsEnabled = !layer.lowestAndHighestPointsEnabled;
            ElevationPreferences.setLowestAndHighestPointsEnabled(layer.lowestAndHighestPointsEnabled);
            if (layer.lowestAndHighestPointsEnabled) {
                Logging.info("Elevation: Lowest and highest points enabled");
            } else {
                Logging.info("Elevation: Lowest and highest points disabled");
            }
            layer.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(ElevationLayer.this.lowestAndHighestPointsEnabled);
            return item;
        }
    }
}
