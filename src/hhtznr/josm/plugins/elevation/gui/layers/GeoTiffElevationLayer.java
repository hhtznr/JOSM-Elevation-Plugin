package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridException;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonLine;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffElevationGrid;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffElevationGridView;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImageException;
import hhtznr.josm.plugins.elevation.gui.dialogs.GeoTiffElevationLayerAdjustmentDialog;
import hhtznr.josm.plugins.elevation.gui.dialogs.GeoTiffInfoDialog;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines.IsolineSegments;
import hhtznr.josm.plugins.elevation.gui.paintables.GeoTiffContourLines;
import hhtznr.josm.plugins.elevation.gui.paintables.LowestAndHighestPoints;
import hhtznr.josm.plugins.elevation.util.Coloring;
import hhtznr.josm.plugins.elevation.util.DrawUtils;
import hhtznr.josm.plugins.elevation.util.DrawUtils.NonOverlappingTextRenderer;

/**
 * This class implements a JOSM layer for displaying GeoTIFF images. It converts
 * the image to grayscale and handles the rendering of the image on the map
 * view.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationLayer extends ElevationLayer implements GeoTiffElevationDataProviderListener {

    private static final double MIN_CONTOUR_LINE_PIXELS = 100.0;

    private final GeoTiffImage geoTiffImage;
    private GeoTiffElevationGrid elevationGrid = null;
    private GeoTiffElevationGridView elevationGridView = null;
    private BufferedImage grayscaleImage = null;
    private volatile GeoTiffContourLines contourLines = null;
    private volatile Future<?> contourLinesFuture = null;
    private final Object contourLinesLock = new Object();
    private long contourLinesRequestId = 0L;

    // For storing of previous values
    private int contourLineIsostep;
    private short lowerCutoffElevation;
    private short upperCutoffElevation;

    private boolean grayscaleImageEnabled = true;
    private Future<?> elevationGridFuture = null;
    private Future<?> grayscaleImageFuture = null;
    private Bounds previousMapBounds;

    private static final CopyOnWriteArrayList<GeoTiffElevationLayer> geoTiffLayers = new CopyOnWriteArrayList<>();
    private static GeoTiffElevationLayerAdjustmentDialog layerAdjustmentDialog = null;
    private static final Object layerAdjustmentDialogLock = new Object();

    /**
     * Creates a new map layer for visualization of the elevation data provided by a
     * GeoTIFF image using default values from preferences.
     *
     * @param name         The name of the layer.
     * @param geoTiffImage The GeoTIFF image providing the elevation data for this
     *                     layer.
     */
    public GeoTiffElevationLayer(String name, GeoTiffImage geoTiffImage) {
        this(name, geoTiffImage, ElevationPreferences.getContourLineIsostep(),
                ElevationPreferences.getContourLineStrokeWidth(), ElevationPreferences.getContourLineColoringScheme(),
                ElevationPreferences.getContourLineColor(), ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
                ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION);
    }

    /**
     * Creates a new map layer for visualization of the elevation data provided by a
     * GeoTIFF image.
     *
     * @param name                      The name of the layer.
     * @param geoTiffImage              The GeoTIFF image providing the elevation
     *                                  data for this layer.
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
    public GeoTiffElevationLayer(String name, GeoTiffImage geoTiffImage, int contourLineIsostep,
            float contourLineStrokeWidth, Coloring.Scheme contourLineColoringScheme, Color contourLineConstantColor,
            short lowerCutoffElevation, short upperCutoffElevation) {
        super(name, ImageProvider.get("layers", "elevation_geotiff.svg"), contourLineIsostep, contourLineStrokeWidth,
                contourLineColoringScheme, contourLineConstantColor, lowerCutoffElevation, upperCutoffElevation);

        this.geoTiffImage = geoTiffImage;
        this.contourLineIsostep = contourLineIsostep;
        this.lowerCutoffElevation = lowerCutoffElevation;
        this.upperCutoffElevation = upperCutoffElevation;

        geoTiffLayers.add(this);

        elevationGridFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                elevationGridFuture = null;
                return;
            }
            try {
                elevationGrid = new GeoTiffElevationGrid(geoTiffImage);
            } catch (GeoTiffImageException e) {
                Logging.error("Elevation: Cannot create contour lines for GeoTIFF file '"
                        + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
            } finally {
                elevationGridFuture = null;
                triggerRepaint();
            }
        });
        acquireLayerAdjustmentDialog();
    }

    /**
     * Makes this GeoTIFF elevation layer acquire the
     * {@link GeoTiffElevationLayerAdjustmentDialog} if it is open, such that this
     * layer can be adjusted via the dialog.
     *
     * @return {@code true} if the dialog was acquired, {@code false} otherwise.
     */
    public boolean acquireLayerAdjustmentDialog() {
        synchronized (layerAdjustmentDialogLock) {
            if (layerAdjustmentDialog != null) {
                return layerAdjustmentDialog.setGeoTiffElevationLayer(this);
            }
            return false;
        }
    }

    /**
     * Returns the GeoTIFF elevation grid used by this layer.
     *
     * @return The GeoTiff elevation grid or {@code null} if the grid has not been
     *         initialized yet.
     */
    public GeoTiffElevationGrid getGeoTiffElevationGrid() {
        return elevationGrid;
    }

    @Override
    public void paint(Graphics2D g, MapView mapView, Bounds mapBounds) {
        if (elevationGrid == null) {
            return;
        }

        if (previousMapBounds == null || !previousMapBounds.equals(mapBounds)) {
            try {
                elevationGridView = elevationGrid.getView(mapBounds);
            } catch (ElevationGridException e) {
                Logging.warn("Elevation: Could not create map view for GeoTIFF with path'"
                        + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
                elevationGridView = null;
                return;
            }
            invalidateContourLines();
            previousMapBounds = mapBounds;
        }

        if (grayscaleImageEnabled) {
            drawGrayscaleImage(g, mapView);
            drawGridBounds(g, mapView);
        }

        if (elevationGridView == null) {
            return;
        }

        // Determine the size of the GeoTIFF in pixels
        Bounds gridBounds = elevationGrid.getBounds();
        Point p1 = mapView.getPoint(gridBounds.getMin());
        Point p2 = mapView.getPoint(gridBounds.getMax());
        int widthPixels = Math.abs(p2.x - p1.x);
        int heightPixels = Math.abs(p2.y - p1.y);
        // Do not draw expensive features if dimensions below threshold
        if (widthPixels < MIN_CONTOUR_LINE_PIXELS && heightPixels < MIN_CONTOUR_LINE_PIXELS) {
            invalidateContourLines();
            return;
        }

        if (isContourLinesEnabled()) {
            drawContourLines(g, mapView, elevationGridView);
        }

        if (isLowestAndHighestPointsEnabled()) {
            drawLowestAndHighestPoints(g, mapView, elevationGridView);
        }
    }

    private void drawGrayscaleImage(Graphics2D g, MapView mapView) {
        if (grayscaleImageEnabled && grayscaleImage == null && grayscaleImageFuture == null) {
            grayscaleImageFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    grayscaleImage = geoTiffImage.getGrayScaleImage();
                } finally {
                    grayscaleImageFuture = null;
                    triggerRepaint();
                }
            });
        }
        ReferencedEnvelope geoBounds = geoTiffImage.getReferencedEnvelope();
        LatLon northWest = new LatLon(geoBounds.getMaxY(), geoBounds.getMinX());
        LatLon southEast = new LatLon(geoBounds.getMinY(), geoBounds.getMaxX());

        Point topLeftScreen = mapView.getPoint(northWest);
        Point bottomRightScreen = mapView.getPoint(southEast);

        int width = bottomRightScreen.x - topLeftScreen.x;
        int height = bottomRightScreen.y - topLeftScreen.y;
        if (width > 0 && height > 0 && grayscaleImageEnabled && grayscaleImage != null) {
            g.drawImage(grayscaleImage, topLeftScreen.x, topLeftScreen.y, width, height, null);
        }
    }

    private void drawGridBounds(Graphics2D g, MapView mapView) {
        Bounds gridBounds = elevationGrid.getBounds();

        LatLon southWest = gridBounds.getMin();
        LatLon northEast = gridBounds.getMax();
        Point lowerLeft = mapView.getPoint(southWest);
        Point upperRight = mapView.getPoint(northEast);
        // Reference point of a Java AWT Rectangle is upper-left corner
        int x = lowerLeft.x;
        int y = upperRight.y;
        int width = upperRight.x - lowerLeft.x + 1;
        int height = lowerLeft.y - upperRight.y + 1;
        if (width < 0 || height < 0) {
            return;
        }
        // Rectangle rectangle = new Rectangle(x, y, width, height);
        g.setColor(Color.BLUE);
        g.drawRect(x, y, width, height);

    }

    private void drawContourLines(Graphics2D g, MapView mapView, GeoTiffElevationGridView gridView) {

        float layerContourLineStrokeWidth = getContourLineStrokeWidth();
        BasicStroke contourLineStroke = new BasicStroke(layerContourLineStrokeWidth, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND);

        Coloring.Scheme layerContourLineColoringScheme = getContourLineColoringScheme();
        Color layerContourLineConstantColor = getContourLineConstantColor();

        GeoTiffContourLines currentContourLines = contourLines;

        int newContourLineIsostep = getContourLineIsostep();
        short newLowerCutoffElevation = getLowerCutoffElevation();
        short newUpperCutoffElevation = getUpperCutoffElevation();
        boolean contourLineSettingsChanged = contourLineIsostep != newContourLineIsostep
                || lowerCutoffElevation != newLowerCutoffElevation || upperCutoffElevation != newUpperCutoffElevation;
        if (contourLineSettingsChanged) {
            invalidateContourLines();
            contourLineIsostep = newContourLineIsostep;
            lowerCutoffElevation = newLowerCutoffElevation;
            upperCutoffElevation = newUpperCutoffElevation;
        }

        if (currentContourLines == null || contourLineSettingsChanged) {
            Future<?> future = contourLinesFuture;
            if (future == null || future.isDone()) {
                triggerContourLineComputation(gridView, newContourLineIsostep, newLowerCutoffElevation,
                        newUpperCutoffElevation);
            }
        }

        GeoTiffContourLines contourLinesRef = contourLines;
        if (contourLinesRef == null) {
            return;
        }

        IsolineSegments[] isolineSegments = contourLinesRef.getIsolineSegments();
        if (isolineSegments.length < 1) {
            return;
        }

        Rectangle screenClip = g.getClipBounds();

        g.setColor(layerContourLineConstantColor);
        g.setStroke(contourLineStroke);

        double minEle = lowerCutoffElevation;
        double maxEle = upperCutoffElevation;
        boolean useFalseColor = false;
        if (layerContourLineColoringScheme == Coloring.Scheme.FALSE_COLOR) {
            LowestAndHighestPoints lowestAndHighestPoints = gridView.getLowestAndHighestPoints();

            double lowestElevation = lowestAndHighestPoints.getLowestElevation();
            double highestElevation = lowestAndHighestPoints.getHighestElevation();
            if (!gridView.isNoDataValue(lowestElevation) && !gridView.isNoDataValue(highestElevation)
                    && lowestElevation != highestElevation) {
                minEle = Math.max(minEle, lowestElevation);
                maxEle = Math.min(maxEle, highestElevation);
                useFalseColor = true;
            }
        }
        for (ContourLines.IsolineSegments isolineSegment : isolineSegments) {
            if (useFalseColor) {
                short ele = isolineSegment.getIsoValue();
                g.setColor(Coloring.getRainbowColor(ele, minEle, maxEle));
            }
            for (LatLonLine segment : isolineSegment.getLineSegments()) {
                Point p1 = mapView.getPoint(segment.getLatLon1());
                Point p2 = mapView.getPoint(segment.getLatLon2());
                // Only draw if the line's bounding box intersects the map's bounding box
                if (screenClip.intersectsLine(p1.x, p1.y, p2.x, p2.y)) {
                    g.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }
    }

    private void invalidateContourLines() {
        synchronized (contourLinesLock) {
            contourLinesRequestId++;
            contourLines = null;
            Future<?> future = contourLinesFuture;
            contourLinesFuture = null;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private void triggerContourLineComputation(GeoTiffElevationGridView gridView, int contourLineIsostep,
            short lowerCutoffElevation, short upperCutoffElevation) {
        if (gridView == null) {
            return;
        }
        synchronized (contourLinesLock) {
            long requestId = ++contourLinesRequestId;
            contourLinesFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
                GeoTiffContourLines computedContourLines = null;
                try {
                    if (!Thread.currentThread().isInterrupted()) {
                        computedContourLines = new GeoTiffContourLines(gridView, contourLineIsostep,
                                lowerCutoffElevation, upperCutoffElevation);
                    }
                } catch (AsyncOperationException e) {
                    synchronized (contourLinesLock) {
                        if (requestId == contourLinesRequestId) {
                            Logging.error("Elevation: Cannot create contour lines for GeoTIFF file with path '"
                                    + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
                        }
                    }
                } finally {
                    synchronized (contourLinesLock) {
                        if (requestId == contourLinesRequestId) {
                            contourLines = computedContourLines;
                            contourLinesFuture = null;
                        }
                    }
                    triggerRepaint();
                }
            });
        }
    }

    private void drawLowestAndHighestPoints(Graphics2D g, MapView mapView, GeoTiffElevationGridView gridView) {
        LowestAndHighestPoints lowestAndHighestPoints = gridView.getLowestAndHighestPoints();

        List<LatLonEle> lowestPoints = lowestAndHighestPoints.getLowestPoints();
        List<LatLonEle> highestPoints = lowestAndHighestPoints.getHighestPoints();
        int maxLowestPoints = Math.min(lowestPoints.size(), ElevationPreferences.MAX_RENDERED_LOWEST_OR_HIGHEST_POINTS);
        List<Point> lowestPixels = new ArrayList<>(maxLowestPoints);
        for (int i = 0; i < maxLowestPoints; i++) {
            LatLonEle latLonEle = lowestPoints.get(i);
            Point p = mapView.getPoint(latLonEle);
            lowestPixels.add(p);
        }
        String textLowestElevation = "";
        double lowestElevation = lowestAndHighestPoints.getLowestElevation();
        if (!gridView.isNoDataValue(lowestElevation)) {
            textLowestElevation = String.format("%.3f", lowestElevation);
        }
        int maxHighestPoints = Math.min(highestPoints.size(),
                ElevationPreferences.MAX_RENDERED_LOWEST_OR_HIGHEST_POINTS);
        List<Point> highestPixels = new ArrayList<>(maxHighestPoints);
        for (int i = 0; i < maxHighestPoints; i++) {
            LatLonEle latLonEle = highestPoints.get(i);
            Point p = mapView.getPoint(latLonEle);
            highestPixels.add(p);
        }
        String textHighestElevation = "";
        double highestElevation = lowestAndHighestPoints.getHighestElevation();
        if (!gridView.isNoDataValue(highestElevation)) {
            textHighestElevation = String.format("%.3f", highestElevation);
        }

        // The size of the marker of an elevation data point in display coordinates
        final int markerSize = 8;
        final int textOffsetX = markerSize / 2 + 3;

        // Draw the symbols for the lowest and highest points
        for (Point p : lowestPixels) {
            DrawUtils.drawEquilateralTriangle(g, p.x, p.y, markerSize, false, Color.GREEN, Color.BLACK);
        }
        for (Point p : highestPixels) {
            DrawUtils.drawEquilateralTriangle(g, p.x, p.y, markerSize, true, Color.RED, Color.BLACK);
        }

        // Set the font for elevation value strings
        Font font = g.getFont().deriveFont(Font.BOLD, 12);
        g.setFont(font);
        // Draw non-overlapping labels for the highest and lowest points
        // Draw non-overlapping labels for the highest and lowest points
        NonOverlappingTextRenderer textRenderer = new NonOverlappingTextRenderer();
        for (Point p : lowestPixels) {
            textRenderer.drawTextSafely(g, p.x + textOffsetX, p.y, Color.BLUE, Color.WHITE, textLowestElevation);
        }
        for (Point p : highestPixels) {
            textRenderer.drawTextSafely(g, p.x + textOffsetX, p.y, Color.BLUE, Color.WHITE, textHighestElevation);
        }
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        ReferencedEnvelope geoBounds = geoTiffImage.getReferencedEnvelope();
        v.visit(new Bounds(geoBounds.getMinY(), geoBounds.getMinX(), geoBounds.getMaxY(), geoBounds.getMaxX()));
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        for (Action action : super.getMenuEntries()) {
            actions.add(action);
        }
        actions.add(new ShowGrayscaleImageAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowLayerAdjustmentDialogAction());
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new ShowInfoDialogAction());
        return actions.toArray(new Action[0]);
    }

    @Override
    public synchronized void destroy() {
        geoTiffLayers.remove(this);
        // Cancel any running background tasks of this layer
        invalidateContourLines();
        for (Future<?> future : new Future<?>[] { elevationGridFuture, elevationGridFuture, grayscaleImageFuture }) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }

        synchronized (layerAdjustmentDialogLock) {
            if (layerAdjustmentDialog != null) {
                Iterator<GeoTiffElevationLayer> layerIterator = geoTiffLayers.iterator();
                // Change dialog over to the next other GeoTIFF elevation layer
                if (layerIterator.hasNext()) {
                    GeoTiffElevationLayer layer = layerIterator.next();
                    layerAdjustmentDialog.setGeoTiffElevationLayer(layer);
                }
                // If there is no other GeoTIFF layer, dispose the dialog
                else {
                    layerAdjustmentDialog.dispose();
                    layerAdjustmentDialog = null;
                }
            }
        }

        grayscaleImage = null;
        elevationGridView = null;
        elevationGrid = null;

        super.destroy();
    }

    /**
     * Derives the name to be used for a {@code GeoTIFFElevationLayer} from the file
     * name taken from the path of the GeoTIFF file.
     *
     * @param path The file path.
     * @return The name to be used for a {@code GeoTIFFElevationLayer}.
     */
    public static String deriveLayerName(Path path) {
        String fileName = path.getFileName().toString();
        return "GeoTIFF " + fileName;
    }

    /**
     * Adds a new {@code GeoTIFFElevationLayer} to JOSM, if no
     * {@code GeoTIFFElevationLayer} for the same file name exists yet. Otherwise
     * does nothing, but noting this via the logger.
     *
     * @param geoTiffImage The GeoTIFF image for which to add the new layer.
     */
    public static void addGeoTiffElevationLayer(GeoTiffImage geoTiffImage) {
        String layerName = deriveLayerName(geoTiffImage.getAbsolutePath());

        if (getGeoTiffLayer(layerName) != null) {
            Logging.info("Elevation: Not adding GeoTIFF layer for '" + geoTiffImage.getAbsolutePath()
                    + "' because a GeoTiFFLayer with the same name already exists");
            return;
        }

        GeoTiffElevationLayer geoTiffLayer = new GeoTiffElevationLayer(layerName, geoTiffImage);
        MainApplication.getLayerManager().addLayer(geoTiffLayer);
    }

    /**
     * Returns the {@code GeoTiffElevationLayer} with the specified name if it
     * exists. already on JOSM's layer list.
     *
     * @param layerName The name of the GeoTIFF layer.
     * @return The {@code GeoTiffElevationLayer} with the specified name or
     *         {@code null} if no such layer exists.
     */
    public static GeoTiffElevationLayer getGeoTiffLayer(String layerName) {
        Iterator<GeoTiffElevationLayer> layerIterator = geoTiffLayers.iterator();

        while (layerIterator.hasNext()) {
            GeoTiffElevationLayer geoTiffLayer = layerIterator.next();
            if (geoTiffLayer.getName().equals(layerName)) {
                return geoTiffLayer;
            }
        }
        return null;
    }

    private class ShowGrayscaleImageAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowGrayscaleImageAction() {
            super("Enable/disable grayscale image", "grayscale_image");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GeoTiffElevationLayer layer = GeoTiffElevationLayer.this;
            layer.grayscaleImageEnabled = !layer.grayscaleImageEnabled;
            if (layer.grayscaleImageEnabled) {
                Logging.info("Elevation: GeoTIFF grayscale image enabled for layer " + getName());
            } else {
                Logging.info("Elevation: GeoTIFF grayscale image disabled for layer " + getName());
            }
            layer.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.grayscaleImageEnabled);
            return item;
        }
    }

    private class ShowInfoDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowInfoDialogAction() {
            super("GeoTIFF information", new ImageProvider("info"),
                    "Information on the GeoTIFF file used for the layer and its metadata", null, true,
                    "geotiff-layer-info", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            GeoTiffInfoDialog dialog = new GeoTiffInfoDialog(MainApplication.getMainFrame(),
                    GeoTiffElevationLayer.this.geoTiffImage);
            dialog.showDialog();
        }
    }

    private class ShowLayerAdjustmentDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowLayerAdjustmentDialogAction() {
            super("Dynamic elevation layer adjustment", new ImageProvider("elevation.svg"),
                    "Dynamic, non-presistent adjustment of elevation rendering", null, true, "geotiff-layer-settings",
                    false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            synchronized (layerAdjustmentDialogLock) {
                if (layerAdjustmentDialog == null) {
                    layerAdjustmentDialog = new GeoTiffElevationLayerAdjustmentDialog(MainApplication.getMainFrame(),
                            GeoTiffElevationLayer.this);
                } else {
                    layerAdjustmentDialog.setGeoTiffElevationLayer(GeoTiffElevationLayer.this);
                }
                layerAdjustmentDialog.showDialog();
            }
        }
    }

    @Override
    public void geoTiffIndexUpdated() {
        triggerRepaint();
    }
}
