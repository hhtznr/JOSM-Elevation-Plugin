package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.SrtmElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGrid;
import hhtznr.josm.plugins.elevation.gui.dialogs.SrtmElevationDataInfoDialog;
import hhtznr.josm.plugins.elevation.gui.dialogs.SrtmElevationLayerAdjustmentDialog;
import hhtznr.josm.plugins.elevation.util.Coloring;

/**
 * Class implementing a map layer for displaying elevation contour lines,
 * hillshade, lowest and highest points in the map view, as well as an elevation
 * raster based on SRTM data. The user can select via the layer menu which of
 * this types of elevation visualization are rendered.
 *
 * @author Harald Hetzner
 */
public class SrtmElevationLayer extends ElevationLayer implements SrtmElevationDataProviderListener {

    private final SrtmElevationDrawHelper layerPainter;
    private final ElevationDataProvider elevationDataProvider;
    private double renderingLimitArcDegrees;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;

    private boolean hillshadeEnabled;
    private boolean elevationRasterEnabled;

    private SrtmElevationLayerAdjustmentDialog layerAdjustmentDialog = null;

    /**
     * Creates a new elevation layer using SRTM data.
     *
     * @param elevationDataProvider     The elevation data provider providing the
     *                                  data for this layer.
     * @param renderingLimitArcDegrees  The maximum size of the displayed map
     *                                  (latitude or longitude) where, if exceeded,
     *                                  rendering of the layer is switched off to
     *                                  avoid excessive CPU and memory usage.
     * @param contourLineIsostep        Step between neighboring elevation contour
     *                                  lines.
     * @param contourLineStrokeWidth    Width of the contour line stroke in px.
     * @param contourLineColoringScheme The coloring scheme of the contour lines.
     * @param contourLineConstantColor  Constant color of the contour lines.
     * @param hillshadeAltitude         The altitude (degrees) of the illumination
     *                                  source in hillshade computation.
     * @param hillshadeAzimuth          The azimuth (degrees) of the illumination
     *                                  source in hillshade computation.
     * @param lowerCutoffElevation      The elevation value below which elevation
     *                                  should not be visualized.
     * @param upperCutoffElevation      The elevation value above which elevation
     *                                  should not be visualized.
     */
    public SrtmElevationLayer(ElevationDataProvider elevationDataProvider, double renderingLimitArcDegrees,
            int contourLineIsostep, float contourLineStrokeWidth, Coloring.Scheme contourLineColoringScheme,
            Color contourLineConstantColor, int hillshadeAltitude, int hillshadeAzimuth, short lowerCutoffElevation,
            short upperCutoffElevation) {
        super("SRTM Elevation Layer", ImageProvider.get("layers", "elevation_srtm.svg"), contourLineIsostep,
                contourLineStrokeWidth, contourLineColoringScheme, contourLineConstantColor, lowerCutoffElevation,
                upperCutoffElevation, ElevationPreferences.getSrtmContourLinesEnabled(),
                ElevationPreferences.getSrtmLowestAndHighestPointsEnabled());
        this.layerPainter = new SrtmElevationDrawHelper(this);
        this.renderingLimitArcDegrees = renderingLimitArcDegrees;

        this.hillshadeAltitude = hillshadeAltitude;
        this.hillshadeAzimuth = hillshadeAzimuth;
        this.elevationDataProvider = elevationDataProvider;
        elevationDataProvider.addSrtmElevationDataProviderListener(this);
        hillshadeEnabled = ElevationPreferences.getSrtmHillshadeEnabled();
        elevationRasterEnabled = ElevationPreferences.getSrtmElevationRasterEnabled();
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

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // LayerPainter created by createMapViewPainter() is used instead
    }

    @Override
    protected LayerPainter createMapViewPainter(MapViewEvent event) {
        return layerPainter;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Action[] getMenuEntries() {
        Action[] actions = new Action[10];
        actions[0] = LayerListDialog.getInstance().createShowHideLayerAction();
        actions[1] = SeparatorLayerAction.INSTANCE;
        actions[2] = new ShowLowestAndHighestPointsAction();
        actions[3] = new ShowContourLinesAction();
        actions[4] = new ShowElevationRasterAction();
        actions[5] = new ShowHillshadeAction();
        actions[6] = SeparatorLayerAction.INSTANCE;
        actions[7] = new ShowLayerAdjustmentDialogAction();
        actions[8] = SeparatorLayerAction.INSTANCE;
        actions[9] = new ShowInfoDialogAction();
        return actions;
    }

    @Override
    public synchronized void destroy() {
        elevationDataProvider.removeSrtmElevationDataProviderListener(this);
        layerPainter.dispose();
        if (layerAdjustmentDialog != null) {
            layerAdjustmentDialog.setVisible(false);
            layerAdjustmentDialog.dispose();
            layerAdjustmentDialog = null;
        }
        super.destroy();
    }

    @Override
    public void elevationDataAvailable(SrtmTileGrid tileGrid) {
        // TODO: We should only repaint if the grid is of relevance to us
        // But consider that contour line bounds are slightly larger than the map view
        triggerRepaint();
    }

    @Override
    public void srtmTileTypeChanged(SrtmTile.Type oldType, SrtmTile.Type newType) {
        layerPainter.invalidate();
        invalidate();
    }

    private class ShowContourLinesAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowContourLinesAction() {
            super("Enable/disable contour lines", "contour_lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean enabled = SrtmElevationLayer.this.toggleContourLinesEnabled();
            ElevationPreferences.setSrtmContourLinesEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: SRTM contour lines enabled");
            } else {
                Logging.info("Elevation: SRTM contour lines disabled");
            }
            SrtmElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(SrtmElevationLayer.this.isContourLinesEnabled());
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
            boolean enabled = SrtmElevationLayer.this.toggleLowestAndHighestPointsEnabled();
            ElevationPreferences.setSrtmLowestAndHighestPointsEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: SRTM lowest and highest points enabled");
            } else {
                Logging.info("Elevation: SRTM Lowest and highest points disabled");
            }
            SrtmElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(SrtmElevationLayer.this.isLowestAndHighestPointsEnabled());
            return item;
        }
    }

    private class ShowHillshadeAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowHillshadeAction() {
            super("Enable/disable hillshade", "hillshade");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SrtmElevationLayer layer = SrtmElevationLayer.this;
            layer.hillshadeEnabled = !layer.hillshadeEnabled;
            ElevationPreferences.setSrtmHillshadeEnabled(layer.hillshadeEnabled);
            if (layer.hillshadeEnabled) {
                Logging.info("Elevation: Hillshade enabled");
            } else {
                Logging.info("Elevation: Hillshade disabled");
            }
            layer.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(SrtmElevationLayer.this.hillshadeEnabled);
            return item;
        }
    }

    private class ShowElevationRasterAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowElevationRasterAction() {
            super("Enable/disable elevation raster", "elevation_raster");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SrtmElevationLayer layer = SrtmElevationLayer.this;
            layer.elevationRasterEnabled = !layer.elevationRasterEnabled;
            ElevationPreferences.setSrtmElevationRasterEnabled(layer.elevationRasterEnabled);
            if (layer.elevationRasterEnabled) {
                Logging.info("Elevation: Elevation raster points enabled");
            } else {
                Logging.info("Elevation: Elevation raster points disabled");
            }
            layer.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(SrtmElevationLayer.this.elevationRasterEnabled);
            return item;
        }
    }

    private class ShowLayerAdjustmentDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowLayerAdjustmentDialogAction() {
            super("Dynamic elevation layer adjustment", new ImageProvider("elevation.svg"),
                    "Dynamic, non-presistent adjustment of elevation rendering", null, true, "elevation-layer-settings",
                    false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layerAdjustmentDialog == null) {
                layerAdjustmentDialog = new SrtmElevationLayerAdjustmentDialog(MainApplication.getMainFrame(),
                        SrtmElevationLayer.this);
            }
            layerAdjustmentDialog.showDialog();
        }
    }

    private class ShowInfoDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowInfoDialogAction() {
            super("SRTM information", new ImageProvider("info"),
                    "Information on cached SRTM tiles and data structures building on them", null, true,
                    "srtm-layer-info", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SrtmElevationDataInfoDialog dialog = new SrtmElevationDataInfoDialog(MainApplication.getMainFrame(),
                    SrtmElevationLayer.this.elevationDataProvider);
            dialog.showDialog();
        }
    }
}
