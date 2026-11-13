package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridException;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.coordinate.CoordinateUtil;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffElevationGrid;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImageException;
import hhtznr.josm.plugins.elevation.gui.dialogs.GeoTiffElevationLayerAdjustmentDialog;
import hhtznr.josm.plugins.elevation.gui.dialogs.GeoTiffInfoDialog;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines;
import hhtznr.josm.plugins.elevation.gui.paintables.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.paintables.LowestAndHighestPoints;
import hhtznr.josm.plugins.elevation.util.Coloring;
import hhtznr.josm.plugins.elevation.util.DrawUtils;
import hhtznr.josm.plugins.elevation.util.DrawUtils.NonOverlappingTextRenderer;
import hhtznr.josm.plugins.elevation.util.FileUtils;

/**
 * This class implements a JOSM layer for displaying GeoTIFF images. It converts
 * the image to grayscale and handles the rendering of the image on the map
 * view.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationLayer extends ElevationLayer implements GeoTiffElevationDataProviderListener {

    private static final double MIN_CONTOUR_LINE_PIXELS = 100.0;

    private static final double BOUNDS_SCALE_FACTOR = 1.5;

    private final GeoTiffImage geoTiffImage;
    private GeoTiffElevationGrid elevationGrid = null;
    private ElevationGridView previousElevationGridView = null;
    private BufferedImage grayscaleImage = null;
    private float grayscaleImageOpacity;
    private ElevationRaster elevationRaster = null;
    private final Object elevationRasterLock = new Object();
    private volatile ContourLines contourLines = null;
    private volatile Future<?> contourLinesFuture = null;
    private final Object contourLinesLock = new Object();
    private long contourLinesRequestId = 0L;
    private LowestAndHighestPoints lowestAndHighestPoints = null;
    private final Object lowestAndHighestPointsLock = new Object();

    // For storing of previous values
    private int contourLineIsostep;
    private short lowerCutoffElevation;
    private short upperCutoffElevation;

    private boolean grayscaleImageEnabled;
    private boolean elevationRasterEnabled;
    private Future<?> elevationGridFuture = null;
    private Future<?> grayscaleImageFuture = null;
    private Bounds previousMapBounds = null;
    private Bounds previousIntersectionBounds = null;

    private static final CopyOnWriteArrayList<GeoTiffElevationLayer> geoTiffLayers = new CopyOnWriteArrayList<>();
    private static GeoTiffElevationLayerAdjustmentDialog layerAdjustmentDialog = null;
    private static final Object layerAdjustmentDialogLock = new Object();

    private static final Object addGeoTiffLayerLock = new Object();

    private static boolean ignoreOpenGeoTiffErrors = false;

    /**
     * Creates a new map layer for visualization of the elevation data provided by a
     * GeoTIFF image using default values from preferences.
     *
     * @param name         The name of the layer.
     * @param geoTiffImage The GeoTIFF image providing the elevation data for this
     *                     layer.
     */
    private GeoTiffElevationLayer(String name, GeoTiffImage geoTiffImage) {
        super(name, ImageProvider.get("layers", "elevation_geotiff.svg"),
                ElevationPreferences.getGeoTiffContourLineIsostep(),
                ElevationPreferences.getGeoTiffContourLineStrokeWidth(),
                ElevationPreferences.getGeoTiffContourLineColoringScheme(),
                ElevationPreferences.getGeoTiffContourLineColor(), ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION,
                ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION,
                ElevationPreferences.getGeoTiffContourLinesEnabled(),
                ElevationPreferences.getGeoTiffLowestAndHighestPointsEnabled());

        this.geoTiffImage = geoTiffImage;
        this.contourLineIsostep = getContourLineIsostep();
        this.lowerCutoffElevation = getLowerCutoffElevation();
        this.upperCutoffElevation = getUpperCutoffElevation();

        grayscaleImageEnabled = ElevationPreferences.getGeoTiffGrayscaleImageEnabled();
        grayscaleImageOpacity = ElevationPreferences.getGeoTiffGrayscaleImageOpacity();
        elevationRasterEnabled = ElevationPreferences.getGeoTiffElevationRasterEnabled();

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
     * Toggles the grayscale image enabled state.
     *
     * @return The new grayscale image enabled state.
     */
    public boolean toggleGrayscaleImageEnabled() {
        grayscaleImageEnabled = !grayscaleImageEnabled;
        return grayscaleImageEnabled;
    }

    /**
     * Returns the opacity of the grayscale image.
     *
     * @return The opacity of the grayscale image (range: 0.0 to 1.0).
     */
    public float getGrayscaleImageOpacity() {
        return grayscaleImageOpacity;
    }

    /**
     * Sets the opacity of the grayscale image.
     *
     * @param opacity The opacity to set (range: 0.0 to 1.0).
     */
    public void setGrayscaleImageOpacity(float opacity) {
        if (opacity < ElevationPreferences.MIN_IMAGE_OPACITY) {
            opacity = ElevationPreferences.MIN_IMAGE_OPACITY;
        } else if (opacity > ElevationPreferences.MAX_IMAGE_OPACITY) {
            opacity = ElevationPreferences.MAX_IMAGE_OPACITY;
        }
        if (opacity == grayscaleImageOpacity) {
            return;
        }
        grayscaleImageOpacity = opacity;
    }

    /**
     * Toggles the elevation raster enabled state.
     *
     * @return The new elevation raster enabled state.
     */
    public boolean toggleElevationRasterEnabled() {
        elevationRasterEnabled = !elevationRasterEnabled;
        return elevationRasterEnabled;
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

        ElevationGridView elevationGridView = previousElevationGridView;

        boolean invalidateUnscaledPaintables = false;
        boolean invalidateScaledPaintables = false;
        boolean elevationGridViewRecreated = false;

        if (previousMapBounds == null || !previousMapBounds.equals(mapBounds)) {
            try {
                elevationGridView = elevationGrid.getView(mapBounds);
                elevationGridViewRecreated = true;
                invalidateUnscaledPaintables = true;
            } catch (ElevationGridException e) {
                Logging.warn("Elevation: Could not create map view for GeoTIFF with path'"
                        + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
                return;
            }

            // Grid view will be null if there is no intersection
            if (elevationGridView != null) {
                // Bounds of the grid view are intersection of grid with map view
                Bounds intersectionBounds = elevationGridView.getBounds();

                // If there is no intersection, returned bounds will be null
                if (intersectionBounds == null) {
                    invalidateUnscaledPaintables = true;
                    invalidateScaledPaintables = true;
                    previousIntersectionBounds = null;
                }
                // Otherwise check whether the intersection has changed
                // It will still be the same if GeoTIFF is completely visible in map view
                else if (!intersectionBounds.equals(previousIntersectionBounds)) {
                    // Recreate the elevation grid view if it was not already (re-)created
                    if (!elevationGridViewRecreated) {
                        try {
                            elevationGridView = elevationGrid.getView(mapBounds);
                            elevationGridViewRecreated = true;
                            invalidateUnscaledPaintables = true;
                        } catch (ElevationGridException e) {
                            Logging.warn("Elevation: Could not create map view for GeoTIFF with path'"
                                    + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
                            return;
                        }
                    }

                    invalidateUnscaledPaintables = true;
                    previousIntersectionBounds = intersectionBounds;
                }
            } else {
                previousIntersectionBounds = null;
            }

            previousMapBounds = mapBounds;
        }
        previousElevationGridView = elevationGridView;

        if (invalidateScaledPaintables) {
            invalidateContourLines();
        }

        if (invalidateUnscaledPaintables) {
            synchronized (elevationRasterLock) {
                elevationRaster = null;
            }
            synchronized (lowestAndHighestPointsLock) {
                lowestAndHighestPoints = null;
            }
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

        if (elevationRasterEnabled) {
            drawElevationRaster(g, mapView, elevationGridView);
        }

        if (isContourLinesEnabled()) {
            drawContourLines(g, mapView, mapBounds, elevationGridView);
        }

        drawOutline(g, mapView);

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
            Graphics2D imageGraphics = (Graphics2D) g.create();
            try {
                imageGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, grayscaleImageOpacity));
                imageGraphics.drawImage(grayscaleImage, topLeftScreen.x, topLeftScreen.y, width, height, null);
            } finally {
                imageGraphics.dispose();
            }
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

    private void drawElevationRaster(Graphics2D g, MapView mapView, ElevationGridView gridView) {
        ElevationRaster currentElevationRaster;
        synchronized (elevationRasterLock) {
            if (elevationRaster == null) {
                elevationRaster = new ElevationRaster(gridView);
            }
            currentElevationRaster = elevationRaster;
        }

        // Set the font for elevation value strings
        Font font = g.getFont().deriveFont(Font.PLAIN, 10);
        g.setFont(font);

        // Screen pixel width and height of the elevation raster
        Point p1 = mapView.getPoint(currentElevationRaster.getBounds().getMin());
        Point p2 = mapView.getPoint(currentElevationRaster.getBounds().getMax());
        int rasterPixelWidth = Math.abs(p2.x - p1.x);
        int rasterPixelHeight = Math.abs(p2.y - p1.y);

        // Compute grid steps to skip grid points
        // Skip more grid points if further zoomed out from the map
        int gridStepLon = (int) Math
                .round(currentElevationRaster.getWidth() / (double) rasterPixelWidth * PIXELS_PER_GRID_POINT);
        int gridStepLat = (int) Math
                .round(currentElevationRaster.getHeight() / (double) rasterPixelHeight * PIXELS_PER_GRID_POINT);
        // Ensure that the steps are always at least 1
        gridStepLon = Math.max(gridStepLon, 1);
        gridStepLat = Math.max(gridStepLat, 1);

        // The size of the marker of an elevation data point in display coordinates
        final int markerSize = 3;

        DrawUtils.NonOverlappingTextRenderer textRenderer = new DrawUtils.NonOverlappingTextRenderer();
        for (int latIndex = 0; latIndex < currentElevationRaster.getHeight(); latIndex += gridStepLat) {
            for (int lonIndex = 0; lonIndex < currentElevationRaster.getWidth(); lonIndex += gridStepLon) {
                LatLonEle latLonEle = currentElevationRaster.getLatLonEle(latIndex, lonIndex);

                Point p = mapView.getPoint(latLonEle);
                String eleText = String.format("%.2f", latLonEle.ele());

                g.setColor(Color.RED);
                g.fillOval(p.x - markerSize / 2, p.y - markerSize / 2, markerSize, markerSize);

                textRenderer.drawTextSafely(g, p.x + markerSize, p.y, Color.BLUE, Color.WHITE, eleText);
            }
        }
    }

    private void drawContourLines(Graphics2D g, MapView mapView, Bounds mapBounds, ElevationGridView mapGridView) {
        float layerContourLineStrokeWidth = getContourLineStrokeWidth();
        BasicStroke contourLineStroke = new BasicStroke(layerContourLineStrokeWidth, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND);

        Coloring.Scheme layerContourLineColoringScheme = getContourLineColoringScheme();
        Color layerContourLineConstantColor = getContourLineConstantColor();

        ContourLines currentContourLines = contourLines;

        int newContourLineIsostep = getContourLineIsostep();
        short newLowerCutoffElevation = getLowerCutoffElevation();
        short newUpperCutoffElevation = getUpperCutoffElevation();

        // If current contour lines exist and cutoff elevation values were reduced,
        // attempt to shrink the amount of contour lines instead of computing new ones
        if (currentContourLines != null && !hasPendingContourLines()) {
            // Attempt to shrink contour lines if cutoff elevations changed
            if ((lowerCutoffElevation < newLowerCutoffElevation || upperCutoffElevation > newUpperCutoffElevation)
                    && currentContourLines.shrinkIsolineSegments(newLowerCutoffElevation, newUpperCutoffElevation)) {
                lowerCutoffElevation = newLowerCutoffElevation;
                upperCutoffElevation = newUpperCutoffElevation;
            }
            // Attempt to prune contour lines if isostep changed
            if (contourLineIsostep != newContourLineIsostep
                    && currentContourLines.pruneIsolineSegments(newContourLineIsostep)) {
                contourLineIsostep = newContourLineIsostep;
            }
        }

        boolean contourLineSettingsChanged = contourLineIsostep != newContourLineIsostep
                || lowerCutoffElevation != newLowerCutoffElevation || upperCutoffElevation != newUpperCutoffElevation;
        if (contourLineSettingsChanged) {
            invalidateContourLines();
            contourLineIsostep = newContourLineIsostep;
            lowerCutoffElevation = newLowerCutoffElevation;
            upperCutoffElevation = newUpperCutoffElevation;
        }

        // Compute contour lines if no contour lines exist or
        // if contour lines exist, but do not cover the map grid view of the GeoTIFF
        if (currentContourLines == null || !currentContourLines.covers(mapGridView.getBounds())) {
            Future<?> future = contourLinesFuture;
            if (future == null || future.isDone()) {
                triggerContourLineComputation(mapView, mapBounds, newContourLineIsostep, newLowerCutoffElevation,
                        newUpperCutoffElevation);
            }
            currentContourLines = contourLines;
            if (currentContourLines == null) {
                return;
            }
        }

        ContourLines.IsolinePath[] isolinePaths = currentContourLines.getIsolinePaths(mapView);
        if (isolinePaths == null) {
            return;
        }

        double minEle = lowerCutoffElevation;
        double maxEle = upperCutoffElevation;
        boolean useFalseColor = false;
        if (layerContourLineColoringScheme == Coloring.Scheme.FALSE_COLOR) {
            // Obtain lowest and highest points based on the map grid view
            // This ensures that coloring is based on the visible points
            LowestAndHighestPoints mapViewLowestAndHighestPoints;
            synchronized (lowestAndHighestPointsLock) {
                if (lowestAndHighestPoints == null) {
                    lowestAndHighestPoints = mapGridView.getLowestAndHighestPoints();
                }
                mapViewLowestAndHighestPoints = lowestAndHighestPoints;
            }

            double lowestElevation = mapViewLowestAndHighestPoints.getLowestElevation();
            double highestElevation = mapViewLowestAndHighestPoints.getHighestElevation();
            if (mapViewLowestAndHighestPoints.hasValidElevations() && lowestElevation != highestElevation) {
                minEle = Math.max(minEle, lowestElevation);
                maxEle = Math.min(maxEle, highestElevation);
                useFalseColor = true;
            }
        }

        Graphics2D g2 = (Graphics2D) g.create();
        AffineTransform mapTransform = mapView.getAffineTransform();

        g2.setColor(layerContourLineConstantColor);
        g2.setStroke(contourLineStroke);

        for (ContourLines.IsolinePath isolinePath : isolinePaths) {
            if (useFalseColor) {
                short ele = isolinePath.getIsovalue();
                g2.setColor(Coloring.getRainbowColor(ele, minEle, maxEle));
            }
            g2.draw(mapTransform.createTransformedShape(isolinePath.getPath()));
        }
        g2.dispose();
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

    private void triggerContourLineComputation(MapView mapView, Bounds mapBounds, int contourLineIsostep,
            short lowerCutoffElevation, short upperCutoffElevation) {
        final ElevationGridView gridView;
        try {
            // Use scaled bounds to create contour lines larger than map view
            // This avoid recalculation if map is slightly moved
            gridView = elevationGrid.getView(CoordinateUtil.getScaledBounds(mapBounds, BOUNDS_SCALE_FACTOR));
        } catch (ElevationGridException e) {
            Logging.warn("Elevation: Could not create map view for GeoTIFF with path'"
                    + geoTiffImage.getAbsolutePath().toString() + "': " + e.toString());
            return;
        }

        synchronized (contourLinesLock) {
            long requestId = ++contourLinesRequestId;
            contourLinesFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
                ContourLines computedContourLines = null;
                try {
                    if (!Thread.currentThread().isInterrupted()) {
                        // Determine resolution of elevation grid relative to screen pixels
                        int gridViewWidth = gridView.getWidth();
                        int gridViewHeight = gridView.getHeight();
                        Bounds gridViewBounds = gridView.getBounds();
                        Point p1 = mapView.getPoint(gridViewBounds.getMin());
                        Point p2 = mapView.getPoint(gridViewBounds.getMax());
                        int gridViewPixelWidth = Math.abs(p2.x - p1.x);
                        int gridViewPixelHeight = Math.abs(p2.y - p1.y);
                        // Compute grid steps to skip grid points
                        // Skip more grid points if further zoomed out from the map
                        int gridStepLon = (int) Math
                                .round((double) gridViewWidth / (double) gridViewPixelWidth * PIXELS_PER_GRID_POINT);
                        int gridStepLat = (int) Math
                                .round((double) gridViewHeight / (double) gridViewPixelHeight * PIXELS_PER_GRID_POINT);
                        // Ensure that the steps are always at least 1
                        gridStepLon = Math.max(gridStepLon, 1);
                        gridStepLat = Math.max(gridStepLat, 1);

                        computedContourLines = new ContourLines(gridView, contourLineIsostep, gridStepLat, gridStepLon,
                                lowerCutoffElevation, upperCutoffElevation);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
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

    private boolean hasPendingContourLines() {
        synchronized (contourLinesLock) {
            return contourLinesFuture != null;
        }
    }

    private void drawOutline(Graphics2D g, MapView mapView) {
        Color outlineColor = new Color(0, 102, 0); // dark green
        Color outlineFillColor = Color.WHITE;
        float outlineWidth = 2.0f;

        Bounds bounds = geoTiffImage.getBounds();
        Point p1 = mapView.getPoint(bounds.getMin());
        Point p2 = mapView.getPoint(bounds.getMax());
        int x = p1.x;
        int y = p2.y;
        int width = Math.abs(p1.x - p2.x);
        int height = Math.abs(p1.y - p2.y);

        Rectangle outline = new Rectangle(x, y, width, height);

        String name = FileUtils.getFileNameWithoutExtension(geoTiffImage.getAbsolutePath(), ".tif", ",tiff");
        int fontSize = 12;
        Font font = g.getFont().deriveFont(Font.BOLD, fontSize);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw stroke
        g.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outlineFillColor);
        g.draw(outline);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outlineColor);
        g.draw(outline);

        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(name);
        int textHeight = metrics.getHeight();
        int ascent = metrics.getAscent();
        y = y - textHeight / 2 + ascent;

        if (textWidth + 8 < width) {
            // Draw text
            g.setFont(font);
            DrawUtils.drawOutlinedText(g, x + 4, y + fontSize / 2 + 2, font, outlineColor, outlineFillColor,
                    outlineWidth, name);
        }
    }

    private void drawLowestAndHighestPoints(Graphics2D g, MapView mapView, ElevationGridView gridView) {
        LowestAndHighestPoints currentLowestAndHighestPoints;
        synchronized (lowestAndHighestPointsLock) {
            if (lowestAndHighestPoints == null) {
                lowestAndHighestPoints = gridView.getLowestAndHighestPoints();
            }
            currentLowestAndHighestPoints = lowestAndHighestPoints;
        }

        if (!currentLowestAndHighestPoints.hasValidElevations()) {
            return;
        }

        List<LatLonEle> lowestPoints = currentLowestAndHighestPoints.getLowestPoints();
        List<LatLonEle> highestPoints = currentLowestAndHighestPoints.getHighestPoints();
        int maxLowestPoints = Math.min(lowestPoints.size(), ElevationPreferences.MAX_RENDERED_LOWEST_OR_HIGHEST_POINTS);
        List<Point> lowestPixels = new ArrayList<>(maxLowestPoints);
        for (int i = 0; i < maxLowestPoints; i++) {
            LatLonEle latLonEle = lowestPoints.get(i);
            Point p = mapView.getPoint(latLonEle);
            lowestPixels.add(p);
        }
        double lowestElevation = currentLowestAndHighestPoints.getLowestElevation();
        String textLowestElevation = String.format("%.3f", lowestElevation);

        int maxHighestPoints = Math.min(highestPoints.size(),
                ElevationPreferences.MAX_RENDERED_LOWEST_OR_HIGHEST_POINTS);
        List<Point> highestPixels = new ArrayList<>(maxHighestPoints);
        for (int i = 0; i < maxHighestPoints; i++) {
            LatLonEle latLonEle = highestPoints.get(i);
            Point p = mapView.getPoint(latLonEle);
            highestPixels.add(p);
        }
        double highestElevation = currentLowestAndHighestPoints.getHighestElevation();
        String textHighestElevation = String.format("%.3f", highestElevation);

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
        Action[] actions = new Action[14];
        actions[0] = LayerListDialog.getInstance().createActivateLayerAction(this);
        actions[1] = LayerListDialog.getInstance().createShowHideLayerAction();
        actions[2] = MainApplication.getMenu().autoScaleActions.get(AutoScaleAction.AutoScaleMode.LAYER);
        actions[3] = LayerListDialog.getInstance().createDeleteLayerAction();
        actions[4] = SeparatorLayerAction.INSTANCE;
        actions[5] = new ShowLowestAndHighestPointsAction();
        actions[6] = new ShowContourLinesAction();
        actions[7] = new ShowElevationRasterAction();
        actions[8] = new ShowGrayscaleImageAction();
        actions[9] = SeparatorLayerAction.INSTANCE;
        actions[10] = new ShowLayerAdjustmentDialogAction();
        actions[11] = SeparatorLayerAction.INSTANCE;
        actions[12] = new CopyGeoTiffFileNameToClipBoardAction();
        actions[13] = new ShowInfoDialogAction();
        return actions;
    }

    @Override
    public synchronized void destroy() {
        synchronized (addGeoTiffLayerLock) {
            if (!geoTiffLayers.remove(this)) {
                Logging.warn("Elevation: GeoTIFF elevation layer '" + getName()
                        + "', which was requested to be removed from the global GeoTIFF layer list upon destruction, was not registered on the list!");
            }
        }
        // Cancel any running background tasks of this layer
        invalidateContourLines();
        for (Future<?> future : new Future<?>[] { elevationGridFuture, elevationGridFuture, grayscaleImageFuture }) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
        elevationGridFuture = null;
        elevationGridFuture = null;
        grayscaleImageFuture = null;

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
        synchronized (contourLinesLock) {
            if (contourLines != null) {
                contourLines.dispose(); // De facto not needed with GeoTIFF
            }
            contourLines = null;
        }
        synchronized (lowestAndHighestPointsLock) {
            lowestAndHighestPoints = null;
        }
        synchronized (elevationRasterLock) {
            elevationRaster = null;
        }
        previousElevationGridView = null;
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
     * Adds a new {@code GeoTIFFElevationLayer} to JOSM, if the specified GeoTIFF
     * file can be read and parsed and if no {@code GeoTIFFElevationLayer} for the
     * same file name exists yet. Otherwise does nothing, but noting this via the
     * logger.
     *
     * @param path        The path from which the GeoTIFF data is to be read.
     * @param initialZoom if {@code true} the map view is zoomed at the added
     *                    layer's projection bounds; if {@code false} the map view
     *                    is not zoomed.
     * @return {@code true} if the layer was added, {@code false} otherwise.
     */
    public static boolean addGeoTiffElevationLayer(Path path, boolean initialZoom) {
        synchronized (addGeoTiffLayerLock) {
            String layerName = deriveLayerName(path.toAbsolutePath());

            if (getGeoTiffLayer(layerName) != null) {
                Logging.info("Elevation: Not adding GeoTIFF layer for '" + path.toAbsolutePath().toString()
                        + "' because a GeoTiFFLayer with the same name already exists");
                return false;
            }

            try {
                // May throw GeoTiffImageException, IOException
                GeoTiffImage geoTiffImage = GeoTiffImage.readGeoTiffFile(path);
                Logging.info("Elevation: GeoTIFF file read: " + path.toAbsolutePath().toString());
                GeoTiffElevationLayer geoTiffLayer = new GeoTiffElevationLayer(layerName, geoTiffImage);
                geoTiffLayers.add(geoTiffLayer);
                MainApplication.getLayerManager().addLayer(geoTiffLayer, initialZoom);
                geoTiffLayer.acquireLayerAdjustmentDialog();
                return true;
            } catch (IOException e) {
                Logging.error("Elevation: Cannot read GeoTIFF file '" + path.toAbsolutePath().toString() + " due to "
                        + e.toString());
                showOpenGeoTiffErrorDialog(path, e);
                return false;
            } catch (GeoTiffImageException e) {
                Logging.error("Elevation: Cannot create GeoTiffImage for file '" + path.toAbsolutePath().toString()
                        + " due to " + e.toString() + " with cause: " + e.getCause());
                showOpenGeoTiffErrorDialog(path, e);
                return false;
            }
        }
    }

    private static void showOpenGeoTiffErrorDialog(Path path, Exception e) {
        if (ignoreOpenGeoTiffErrors) {
            return;
        }

        String fileName = path.getFileName().toString();
        String title = "Failed to open " + fileName;

        String message = "<html>";
        message += Utils.escapeReservedCharactersHTML("Failed to open " + path.toAbsolutePath().toString());
        message += "<br><br>";
        message += "Exception: " + Utils.escapeReservedCharactersHTML(e.toString());
        if (e instanceof GeoTiffImageException && e.getCause() != null) {
            message += "<br>";
            message += "Caused by: " + Utils.escapeReservedCharactersHTML(e.getCause().toString());
        }
        message += "<html>";

        final String optionOK = "OK";
        final String optionIgnore = "Ignore further errors";

        String[] options = { optionOK, optionIgnore };
        int choice = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

        if (choice == JOptionPane.CLOSED_OPTION) {
            return;
        }

        String selectedOption = options[choice];

        switch (selectedOption) {
        case optionIgnore:
            // Ignore errors, i.e. do not show a dialog again
            ignoreOpenGeoTiffErrors = true;
            break;

        case optionOK:
        default:
            // Just close the dialog
            break;
        }
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

    /**
     * Returns a list of all currently existing GeoTIFF elevation layers.
     *
     * @return All currently existing GeoTIFF elevation layers.
     */
    public static List<GeoTiffElevationLayer> getGeoTiffLayers() {
        return geoTiffLayers;
    }

    private class ShowContourLinesAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowContourLinesAction() {
            super("Enable/disable contour lines", "contour_lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean enabled = GeoTiffElevationLayer.this.toggleContourLinesEnabled();
            ElevationPreferences.setGeoTiffContourLinesEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: GeoTIFF contour lines enabled");
            } else {
                Logging.info("Elevation: GeoTIFF contour lines disabled");
            }
            GeoTiffElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.isContourLinesEnabled());
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
            boolean enabled = GeoTiffElevationLayer.this.toggleLowestAndHighestPointsEnabled();
            ElevationPreferences.setGeoTiffLowestAndHighestPointsEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: GeoTIFF lowest and highest points enabled");
            } else {
                Logging.info("Elevation: GeoTIFF Lowest and highest points disabled");
            }
            GeoTiffElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.isLowestAndHighestPointsEnabled());
            return item;
        }
    }

    private class ShowGrayscaleImageAction extends AbstractElevationLayerAction {

        private static final long serialVersionUID = 1L;

        public ShowGrayscaleImageAction() {
            super("Enable/disable grayscale image", "grayscale_image");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean enabled = GeoTiffElevationLayer.this.toggleGrayscaleImageEnabled();
            ElevationPreferences.setGeoTiffGrayscaleImagesEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: GeoTIFF grayscale image enabled for layer " + getName());
            } else {
                Logging.info("Elevation: GeoTIFF grayscale image disabled for layer " + getName());
            }
            GeoTiffElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.grayscaleImageEnabled);
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
            boolean enabled = GeoTiffElevationLayer.this.toggleElevationRasterEnabled();
            ElevationPreferences.setGeoTiffElevationRasterEnabled(enabled);
            if (enabled) {
                Logging.info("Elevation: GeoTIFF elevation raster points enabled");
            } else {
                Logging.info("Elevation: GeoTIFF elevation raster points disabled");
            }
            GeoTiffElevationLayer.this.triggerRepaint();
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.elevationRasterEnabled);
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

    private class CopyGeoTiffFileNameToClipBoardAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public CopyGeoTiffFileNameToClipBoardAction() {
            super("Copy file name to clipboard", new ImageProvider("copy.svg"), "Copy GeoTIFF file name to clipboard",
                    null, false, "geotiff-copy-to-clipboard", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String fileName = geoTiffImage.getAbsolutePath().getFileName().toString();
            StringSelection selection = new StringSelection(fileName);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            ;
        }
    }

    @Override
    public void geoTiffIndexUpdated() {
        triggerRepaint();
    }
}
