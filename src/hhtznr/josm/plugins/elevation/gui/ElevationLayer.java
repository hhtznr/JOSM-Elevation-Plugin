package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.widgets.UrlLabel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.ElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

/**
 * Class implementing a map layer for displaying elevation contour lines and
 * hillshade.
 */
public class ElevationLayer extends Layer implements ElevationDataProviderListener {

    private final ElevationDrawHelper layerPainter;
    private final ElevationDataProvider elevationDataProvider;
    private double renderingLimitArcDegrees;
    private int contourLineIsostep;
    private float contourLineStrokeWidth;
    private Color contourLineColor;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;

    private int lowerCutoffElevation;
    private int upperCutoffElevation;

    private boolean contourLinesEnabled;
    private boolean hillshadeEnabled;
    private boolean elevationRasterEnabled;
    private boolean lowestAndHighestPointsEnabled;

    /**
     * Creates a new elevation layer.
     *
     * @param elevationDataProvider    The elevation data provider providing the
     *                                 data for this layer.
     * @param renderingLimitArcDegrees The maximum size of the displayed map
     *                                 (latitude or longitude) where, if exceeded,
     *                                 rendering of the layer is switched off to
     *                                 avoid excessive CPU and memory usage.
     * @param contourLineIsostep       Step between neighboring elevation contour
     *                                 lines.
     * @param contourLineStrokeWidth   Width of the contour line stroke in px.
     * @param contourLineColor         Color of the contour lines.
     * @param hillshadeAltitude        The altitude (degrees) of the illumination
     *                                 source in hillshade computation.
     * @param hillshadeAzimuth         The azimuth (degrees) of the illumination
     *                                 source in hillshade computation.
     * @param lowerCutoffElevation     The elevation value below which elevation
     *                                 should not be visualized.
     * @param upperCutoffElevation     The elevation value above which elevation
     *                                 should not be visualized.
     */
    public ElevationLayer(ElevationDataProvider elevationDataProvider, double renderingLimitArcDegrees,
            int contourLineIsostep, float contourLineStrokeWidth, Color contourLineColor, int hillshadeAltitude,
            int hillshadeAzimuth, int lowerCutoffElevation, int upperCutoffElevation) {
        super("Elevation Layer");
        this.layerPainter = new ElevationDrawHelper(this);
        this.renderingLimitArcDegrees = renderingLimitArcDegrees;
        this.contourLineIsostep = contourLineIsostep;
        this.contourLineStrokeWidth = contourLineStrokeWidth;
        this.contourLineColor = contourLineColor;
        this.hillshadeAltitude = hillshadeAltitude;
        this.hillshadeAzimuth = hillshadeAzimuth;
        this.lowerCutoffElevation = lowerCutoffElevation;
        this.upperCutoffElevation = upperCutoffElevation;
        this.elevationDataProvider = elevationDataProvider;
        elevationDataProvider.addElevationDataProviderListener(this);
        contourLinesEnabled = ElevationPreferences.getContourLinesEnabled();
        hillshadeEnabled = ElevationPreferences.getHillshadeEnabled();
        elevationRasterEnabled = ElevationPreferences.getElevationRasterEnabled();
        lowestAndHighestPointsEnabled = ElevationPreferences.getLowestAndHighestPointsEnabled();
    }

    /**
     * Returns the elevation data provider of this elevation layer.
     *
     * @return The elevation data provider.
     */
    public ElevationDataProvider getElevationDataProvider() {
        return elevationDataProvider;
    }

    /**
     * Returns the rendering limit.
     *
     * @return The maximum size of the displayed map (latitude or longitude) where,
     *         if exceeded, rendering of the layer is switched off to avoid
     *         excessive CPU and memory usage.
     */
    public double getRenderingLimit() {
        return renderingLimitArcDegrees;
    }

    /**
     * Sets a new rendering limit.
     *
     * @param renderingLimitArcDegrees The maximum size of the displayed map
     *                                 (latitude or longitude) where, if exceeded,
     *                                 rendering of the layer is switched off to
     *                                 avoid excessive CPU and memory usage.
     */
    public void setRenderingLimit(double renderingLimitArcDegrees) {
        this.renderingLimitArcDegrees = renderingLimitArcDegrees;
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
     * Returns whether hillshade is enabled.
     *
     * @return {@code true} if displaying hillshade is enabled.
     */
    public boolean isHillshadeEnabled() {
        return hillshadeEnabled;
    }

    /**
     * Returns whether the elevation data raster is enabled.
     *
     * @return {@code true} if displaying of the elevation data raster is enabled.
     */
    public boolean isElevationRasterEnabled() {
        return elevationRasterEnabled;
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
     * Returns the contour line color.
     *
     * @return The color of the contour lines.
     */
    public Color getContourLineColor() {
        return contourLineColor;
    }

    /**
     * Sets the color of the contour lines to a new value.
     *
     * @param color The new color of the contour lines.
     */
    public void setContourLineColor(Color color) {
        contourLineColor = color;
    }

    /**
     * Returns the hillshade altitude.
     *
     * @return The altitude of the hillshade illumination source.
     */
    public int getHillshadeAltitude() {
        return hillshadeAltitude;
    }

    /**
     * Returns the hillshade azimuth.
     *
     * @return The azimuth of the hillshade illumination source.
     */
    public int getHillshadeAzimuth() {
        return hillshadeAzimuth;
    }

    /**
     * Set a new altitude and azimuth of the illumination source in hillshade
     * computation.
     *
     * @param altitude The altitude (degrees) of the illumination source in
     *                 hillshade computation.
     * @param azimuth  The azimuth (degrees) of the illumination source in hillshade
     *                 computation.
     */
    public void setHillshadeIllumination(int altitude, int azimuth) {
        hillshadeAltitude = altitude;
        hillshadeAzimuth = azimuth;
    }

    /**
     * Returns the elevation below which elevation data should not be visualized.
     *
     * @return The elevation value below which elevation data should not be
     *         visualized.
     */
    public int getLowerCutoffElevation() {
        return lowerCutoffElevation;
    }

    /**
     * Sets the elevation below which elevation data should not be visualized.
     *
     * @param value The elevation value below which elevation data should not be
     *              visualized.
     */
    public void setLowerCutoffElevation(int value) {
        lowerCutoffElevation = value;
    }

    /**
     * Returns the elevation above which elevation data should not be visualized.
     *
     * @return The elevation value above which elevation data should not be
     *         visualized.
     */
    public int getUpperCutoffElevation() {
        return upperCutoffElevation;
    }

    /**
     * Sets the elevation above which elevation data should not be visualized.
     *
     * @param value The elevation value above which elevation data should not be
     *              visualized.
     */
    public void setUpperCutoffElevation(int value) {
        upperCutoffElevation = value;
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // LayerPainter created by createMapViewPainter() is used instead
    }

    @Override
    protected LayerPainter createMapViewPainter(MapViewEvent event) {
        return layerPainter;
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.get("elevation");
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

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public Object getInfoComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel("Elevation Layer"), GBC.eol());
        List<List<String>> content = new ArrayList<>();
        // Tile grid
        int[] tileGridDimensions = elevationDataProvider.getSRTMTileGridDimensions();
        content.add(Arrays.asList(tr("SRTM tile grid"),
                "" + tileGridDimensions[0] + " x " + tileGridDimensions[1] + " SRTM tiles"));
        // Tile grid raster
        int[] tileGridRasterDimensions = elevationDataProvider.getSRTMTileGridRasterDimensions();
        content.add(Arrays.asList(tr("SRTM tile grid raster"),
                "" + tileGridRasterDimensions[0] + " x " + tileGridRasterDimensions[1] + " elevation data points"));
        // Cache size
        content.add(Arrays.asList(tr("SRTM tile cache"), "" + elevationDataProvider.getTileCacheInfo()));
        // Info on tiles -> type, status, size, source
        Map<String, String> cachedTilesInfo = elevationDataProvider.getCachedTilesInfo();
        for (Map.Entry<String, String> entry : cachedTilesInfo.entrySet()) {
            content.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        for (List<String> entry : content) {
            panel.add(new JLabel(entry.get(0) + ':'), GBC.std());
            panel.add(GBC.glue(5, 0), GBC.std());
            panel.add(createTextField(entry.get(1)), GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }
        return panel;
    }

    private static JComponent createTextField(String text) {
        if (text != null && text.matches("https?://.*")) {
            return new UrlLabel(text);
        }
        JTextField ret = new JTextField(text);
        ret.setEditable(false);
        ret.setBorder(BorderFactory.createEmptyBorder());
        return ret;
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(LayerListDialog.getInstance().createShowHideLayerAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowContourLinesAction(this));
        actions.add(new ShowHillshadeAction(this));
        actions.add(new ShowLowestAndHighestPointsAction(this));
        actions.add(new ShowElevationRasterAction(this));
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[0]);
    }

    @Override
    public synchronized void destroy() {
        elevationDataProvider.removeElevationDataProviderListener(this);
    }

    @Override
    public void elevationDataAvailable(SRTMTileGrid tileGrid) {
        invalidate();
        if (isVisible())
            MainApplication.getMap().mapView.repaint();
    }

    private static abstract class AbstractElevationLayerAction extends AbstractAction implements LayerAction {

        private static final long serialVersionUID = 1L;

        protected final ElevationLayer layer;

        public AbstractElevationLayerAction(ElevationLayer layer, String actionName, String iconName) {
            this.layer = layer;
            putValue(NAME, actionName);
            new ImageProvider("dialogs", iconName).getResource().attachImageIcon(this, true);
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            if (layers.size() == 0)
                return false;
            for (Layer layer : layers) {
                if (!(layer instanceof ElevationLayer))
                    return false;
            }
            return true;
        }
    }

    private static class ShowContourLinesAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowContourLinesAction(ElevationLayer layer) {
            super(layer, "Enable/disable contour lines", "contour_lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.contourLinesEnabled = !layer.contourLinesEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_CONTOUR_LINES_ENABLED,
                    layer.contourLinesEnabled);
            if (layer.contourLinesEnabled)
                Logging.info("Elevation: Contour lines enabled");
            else
                Logging.info("Elevation: Contour lines disabled");
            layer.invalidate();
            if (layer.isVisible())
                MainApplication.getMap().mapView.repaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(layer.contourLinesEnabled);
            return item;
        }
    }

    private static class ShowHillshadeAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowHillshadeAction(ElevationLayer layer) {
            super(layer, "Enable/disable hillshade", "hillshade");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.hillshadeEnabled = !layer.hillshadeEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_HILLSHADE_ENABLED, layer.hillshadeEnabled);
            if (layer.hillshadeEnabled)
                Logging.info("Elevation: Hillshade enabled");
            else
                Logging.info("Elevation: Hillshade disabled");
            layer.invalidate();
            if (layer.isVisible())
                MainApplication.getMap().mapView.repaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(layer.hillshadeEnabled);
            return item;
        }
    }

    private static class ShowElevationRasterAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowElevationRasterAction(ElevationLayer layer) {
            super(layer, "Enable/disable elevation raster", "elevation_raster");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.elevationRasterEnabled = !layer.elevationRasterEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_RASTER_ENABLED, layer.elevationRasterEnabled);
            if (layer.elevationRasterEnabled)
                Logging.info("Elevation: Elevation raster points enabled");
            else
                Logging.info("Elevation: Elevation raster points disabled");
            layer.invalidate();
            if (layer.isVisible())
                MainApplication.getMap().mapView.repaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(layer.elevationRasterEnabled);
            return item;
        }
    }

    private static class ShowLowestAndHighestPointsAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowLowestAndHighestPointsAction(ElevationLayer layer) {
            super(layer, "Enable/disable lowest and highest points", "lowest_highest_points");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.lowestAndHighestPointsEnabled = !layer.lowestAndHighestPointsEnabled;
            Config.getPref().putBoolean(ElevationPreferences.LOWEST_AND_HIGHEST_POINTS_ENABLED,
                    layer.lowestAndHighestPointsEnabled);
            if (layer.lowestAndHighestPointsEnabled)
                Logging.info("Elevation: Lowest and highest points enabled");
            else
                Logging.info("Elevation: Lowest and highest points disabled");
            layer.invalidate();
            if (layer.isVisible())
                MainApplication.getMap().mapView.repaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(layer.lowestAndHighestPointsEnabled);
            return item;
        }
    }
}
