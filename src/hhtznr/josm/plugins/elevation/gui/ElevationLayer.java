package hhtznr.josm.plugins.elevation.gui;

import java.awt.Graphics2D;
import java.awt.event.ActionEvent;

import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.ElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

/**
 * Class implementing a map layer for displaying elevation contour lines and
 * hillshade.
 */
public class ElevationLayer extends Layer implements ElevationDataProviderListener {

    private final ElevationDataProvider elevationDataProvider;
    private double renderingLimitArcDegrees;
    private int contourLineIsostep;

    SRTMTileGrid contourLineTileGrid = null;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;
    SRTMTileGrid hillshadeTileGrid = null;

    private boolean contourLinesEnabled;
    private boolean hillshadeEnabled;
    private boolean elevationRasterEnabled;

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
     * @param hillshadeAltitude        The altitude (degrees) of the illumination
     *                                 source in hillshade computation.
     * @param hillshadeAzimuth         The azimuth (degrees) of the illumination
     *                                 source in hillshade computation.
     */
    public ElevationLayer(ElevationDataProvider elevationDataProvider, double renderingLimitArcDegrees,
            int contourLineIsostep, int hillshadeAltitude, int hillshadeAzimuth) {
        super("Elevation Layer");
        this.renderingLimitArcDegrees = renderingLimitArcDegrees;
        this.contourLineIsostep = contourLineIsostep;
        this.hillshadeAltitude = hillshadeAltitude;
        this.hillshadeAzimuth = hillshadeAzimuth;
        this.elevationDataProvider = elevationDataProvider;
        elevationDataProvider.addElevationDataProviderListener(this);
        contourLinesEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_CONTOUR_LINES_ENABLED, ElevationPreferences.DEFAULT_ELEVATION_CONTOUR_LINES_ENABLED);
        hillshadeEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_HILLSHADE_ENABLED, ElevationPreferences.DEFAULT_ELEVATION_HILLSHADE_ENABLED);
        elevationRasterEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_RASTER_ENABLED, ElevationPreferences.DEFAULT_ELEVATION_RASTER_ENABLED);
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
        if (this.renderingLimitArcDegrees != renderingLimitArcDegrees) {
            this.renderingLimitArcDegrees = renderingLimitArcDegrees;
            repaint();
        }
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
        if (contourLineIsostep != isostep) {
            contourLineIsostep = isostep;
            repaint();
        }
    }

    /**
     * Returns an SRTM tile grid used for contour lines computation provided by this
     * layer's elevation data provider.
     *
     * @param clipBounds The bounds for which to create the SRTM tile grid.
     * @return The SRTM tile grid.
     */
    public SRTMTileGrid getContourLineTileGrid(Bounds clipBounds) {
        if (clipBounds != null)
            contourLineTileGrid = elevationDataProvider.getSRTMTileGrid(clipBounds);
        return contourLineTileGrid;
    }

    /**
     * Returns the hillshade alitude.
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
        if (hillshadeAltitude != altitude || hillshadeAzimuth != azimuth) {
            hillshadeAltitude = altitude;
            hillshadeAzimuth = azimuth;
            repaint();
        }
    }

    /**
     * Returns an SRTM tile grid used for hillshade computation provided by this
     * layer's elevation data provider.
     *
     * @param clipBounds The bounds for which to create the SRTM tile grid.
     * @return The SRTM tile grid.
     */
    public SRTMTileGrid getHillshadeTileGrid(Bounds clipBounds) {
        if (clipBounds != null)
            hillshadeTileGrid = elevationDataProvider.getSRTMTileGrid(clipBounds);
        return hillshadeTileGrid;
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // LayerPainter created by createMapViewPainter() is used instead
    }

    @Override
    protected LayerPainter createMapViewPainter(MapViewEvent event) {
        return new ElevationDrawHelper(this);
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.get("mapmode", "elevation");
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
        return null;
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        actions.add(LayerListDialog.getInstance().createShowHideLayerAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowContourLinesAction(this));
        actions.add(new ShowHillshadeAction(this));
        actions.add(new ShowElevationRasterAction(this));
        return actions.toArray(new Action[0]);
    }

    @Override
    public synchronized void destroy() {
        elevationDataProvider.removeElevationDataProviderListener(this);
    }

    @Override
    public void elevationDataAvailable(SRTMTile tile) {
        if (isVisible())
            repaint();
    }

    private void repaint() {
        MapFrame map = MainApplication.getMap();
        if (map != null)
            map.mapView.repaint();
    }

    private static class ShowContourLinesAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final ElevationLayer layer;

        public ShowContourLinesAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable contour lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.contourLinesEnabled = !layer.contourLinesEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_CONTOUR_LINES_ENABLED, layer.contourLinesEnabled);
            if (layer.contourLinesEnabled)
                Logging.info("Elevation: Contour lines enabled");
            else
                Logging.info("Elevation: Contour lines disabled");
            layer.repaint();
        }
    }

    private static class ShowHillshadeAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final ElevationLayer layer;

        public ShowHillshadeAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable hillshade");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.hillshadeEnabled = !layer.hillshadeEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_HILLSHADE_ENABLED, layer.hillshadeEnabled);
            if (layer.hillshadeEnabled)
                Logging.info("Elevation: Hillshade enabled");
            else
                Logging.info("Elevation: Hillshade disabled");
            layer.repaint();
        }
    }

    private static class ShowElevationRasterAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final ElevationLayer layer;

        public ShowElevationRasterAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable elevation raster");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.elevationRasterEnabled = !layer.elevationRasterEnabled;
            Config.getPref().putBoolean(ElevationPreferences.ELEVATION_RASTER_ENABLED, layer.elevationRasterEnabled);
            if (layer.elevationRasterEnabled)
                Logging.info("Elevation: Elevation raster points enabled");
            else
                Logging.info("Elevation: Elevation raster points disabled");
            layer.repaint();
        }
    }
}
