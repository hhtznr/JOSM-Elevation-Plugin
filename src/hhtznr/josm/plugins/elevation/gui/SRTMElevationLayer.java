package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.Coloring;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.ElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

/**
 * Class implementing a map layer for displaying elevation contour lines,
 * hillshade, lowest and highest points in the map view, as well as an elevation
 * raster based on SRTM data. The user can select via the layer menu which of
 * this types of elevation visualization are rendered.
 *
 * @author Harald Hetzner
 */
public class SRTMElevationLayer extends ElevationLayer implements ElevationDataProviderListener {

    private final ElevationDrawHelper layerPainter;
    private final ElevationDataProvider elevationDataProvider;
    private double renderingLimitArcDegrees;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;

    private boolean hillshadeEnabled;
    private boolean elevationRasterEnabled;

    private ElevationLayerAdjustmentDialog layerAdjustmentDialog = null;

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
    public SRTMElevationLayer(ElevationDataProvider elevationDataProvider, double renderingLimitArcDegrees,
            int contourLineIsostep, float contourLineStrokeWidth, Coloring.Scheme contourLineColoringScheme,
            Color contourLineConstantColor, int hillshadeAltitude, int hillshadeAzimuth, short lowerCutoffElevation,
            short upperCutoffElevation) {
        super("SRTM Elevation Layer", ImageProvider.get("", "elevation.svg"), contourLineIsostep,
                contourLineStrokeWidth, contourLineColoringScheme, contourLineConstantColor, lowerCutoffElevation,
                upperCutoffElevation);
        this.layerPainter = new ElevationDrawHelper(this);
        this.renderingLimitArcDegrees = renderingLimitArcDegrees;

        this.hillshadeAltitude = hillshadeAltitude;
        this.hillshadeAzimuth = hillshadeAzimuth;
        this.elevationDataProvider = elevationDataProvider;
        elevationDataProvider.addElevationDataProviderListener(this);
        hillshadeEnabled = ElevationPreferences.getHillshadeEnabled();
        elevationRasterEnabled = ElevationPreferences.getElevationRasterEnabled();
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
            panel.add(UIFactory.createLayerInfoTextField(entry[1]), GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        for (Action action : super.getMenuEntries()) {
            actions.add(action);
        }
        actions.add(new ShowHillshadeAction());
        actions.add(new ShowElevationRasterAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowElevationLayerDialogAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[0]);
    }

    @Override
    public synchronized void destroy() {
        elevationDataProvider.removeElevationDataProviderListener(this);
        if (layerAdjustmentDialog != null) {
            layerAdjustmentDialog.setVisible(false);
            layerAdjustmentDialog.dispose();
            layerAdjustmentDialog = null;
        }
        super.destroy();
    }

    @Override
    public void elevationDataAvailable(SRTMTileGrid tileGrid) {
        triggerRepaint();
    }

    @Override
    public void srtmTileTypeChanged(SRTMTile.Type oldType, SRTMTile.Type newType) {
        layerPainter.invalidate();
        invalidate();
    }

    private class ShowHillshadeAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowHillshadeAction() {
            super("Enable/disable hillshade", "hillshade");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            SRTMElevationLayer layer = SRTMElevationLayer.this;
            layer.hillshadeEnabled = !layer.hillshadeEnabled;
            ElevationPreferences.setHillshadeEnabled(layer.hillshadeEnabled);
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
            item.setSelected(SRTMElevationLayer.this.hillshadeEnabled);
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
            SRTMElevationLayer layer = SRTMElevationLayer.this;
            layer.elevationRasterEnabled = !layer.elevationRasterEnabled;
            ElevationPreferences.setElevationRasterEnabled(layer.elevationRasterEnabled);
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
            item.setSelected(SRTMElevationLayer.this.elevationRasterEnabled);
            return item;
        }
    }

    private class ShowElevationLayerDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowElevationLayerDialogAction() {
            super("Dynamic elevation layer adjustment", new ImageProvider("elevation.svg"),
                    "Dynamic, non-presistent adjustment of elevation rendering", null, true, "elevation-layer-settings",
                    false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layerAdjustmentDialog == null) {
                layerAdjustmentDialog = new ElevationLayerAdjustmentDialog(MainApplication.getMainFrame(),
                        SRTMElevationLayer.this);
            }
            layerAdjustmentDialog.showDialog();
        }
    }
}
