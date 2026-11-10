package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.MapViewEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationListener;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridException;
import hhtznr.josm.plugins.elevation.data.coordinate.CoordinateUtil;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines;
import hhtznr.josm.plugins.elevation.gui.paintables.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.paintables.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.gui.paintables.LowestAndHighestPoints;
import hhtznr.josm.plugins.elevation.gui.paintables.PendingSrtmContourLines;
import hhtznr.josm.plugins.elevation.util.Coloring;
import hhtznr.josm.plugins.elevation.util.DrawUtils;
import hhtznr.josm.plugins.elevation.util.DrawUtils.NonOverlappingTextRenderer;

/**
 * The class that helps drawing elevation contour lines, elevation data raster
 * and hillshade on the elevation layer.
 *
 * @author Harald Hetzner
 */
public class SrtmElevationDrawHelper implements MapViewPaintable.LayerPainter, PaintableInvalidationListener {

    /**
     * Scale factor for bounds of new contour line and hillshade tiles so they do
     * not immediately need to be regenerated when the map is moved.
     */
    private static final double BOUNDS_SCALE_FACTOR = 1.2;

    /**
     * Upon zooming, the previous contour line and hillshade tile are discarded, if
     * at least one of its dimensions is by this factor larger than the currently
     * needed size.
     */
    private static final double SCALE_DISCARD_FACTOR = 1.5;

    private final SrtmElevationLayer layer;

    private int contourLineIsostep;
    private BasicStroke contourLineStroke;
    private short lowerCutoffElevation;
    private short upperCutoffElevation;
    private ContourLines contourLines = null;
    private PendingSrtmContourLines pendingContourLines = null;
    private final Object pendingContourLinesLock = new Object();
    private int hillshadeAltitude;
    private int hillshadeAzimuth;
    private HillshadeImageTile hillshadeImageTile = null;
    private BufferedImage scaledHillshadeImage = null;
    private LatLon hillshadeNorthWest = null;

    private ElevationRaster elevationRaster = null;

    private Bounds previousClipBounds = null;

    private LowestAndHighestPoints lowestAndHighestPoints = null;

    private boolean isDisposed = false;

    /**
     * Creates a new elevation draw helper.
     *
     * @param layer The elevation layer for which this draw helper performs
     *              rendering.
     */
    public SrtmElevationDrawHelper(SrtmElevationLayer layer) {
        this.layer = layer;
        this.layer.addInvalidationListener(this);
        contourLineIsostep = layer.getContourLineIsostep();
        setContourLineStroke(layer.getContourLineStrokeWidth());
    }

    @Override
    public void paint(MapViewGraphics graphics) {
        boolean isHillshadeEnabled = layer.isHillshadeEnabled();
        boolean isContourLinesEnabled = layer.isContourLinesEnabled();
        boolean isElevationRasterEnabled = layer.isElevationRasterEnabled();
        boolean isLowestAndHighestPointsEnabled = layer.isLowestAndHighestPointsEnabled();

        synchronized (this) {
            if (!isHillshadeEnabled && hillshadeImageTile != null) {
                hillshadeImageTile.dispose();
                hillshadeImageTile = null;
                scaledHillshadeImage = null;
            }
            if (!isContourLinesEnabled) {
                clearContourLines();
            }
            if (!isElevationRasterEnabled && elevationRaster != null) {
                elevationRaster.dispose();
                elevationRaster = null;
            }
            if (!isLowestAndHighestPointsEnabled && lowestAndHighestPoints != null) {
                lowestAndHighestPoints.dispose();
                lowestAndHighestPoints = null;
            }
        }

        if (!isHillshadeEnabled && !isContourLinesEnabled && !isElevationRasterEnabled
                && !isLowestAndHighestPointsEnabled) {
            return;
        }

        Bounds clipBounds = graphics.getClipBounds().getLatLonBoundsBox();

        // Disable drawing of contour lines if the map is zoomed out too much
        // This prevents high CPU and I/O usage
        boolean zoomLevelDisabled = Math.abs(clipBounds.getMaxLat() - clipBounds.getMinLat()) > layer
                .getRenderingLimit()
                || Math.abs(clipBounds.getMaxLon() - clipBounds.getMinLon()) > layer.getRenderingLimit();

        if (zoomLevelDisabled) {
            drawZoomLevelDisabled(graphics);
        } else {
            if (previousClipBounds != null
                    && (previousClipBounds.getWidth() > SCALE_DISCARD_FACTOR * clipBounds.getWidth()
                            || previousClipBounds.getHeight() > SCALE_DISCARD_FACTOR * clipBounds.getHeight())) {
                invalidate();
            }

            if (isHillshadeEnabled) {
                drawHillshade(graphics, clipBounds);
            }

            if (isContourLinesEnabled) {
                drawContourLines(graphics, clipBounds);
            }

            if (isElevationRasterEnabled) {
                drawElevationRaster(graphics, clipBounds);
            }

            if (isLowestAndHighestPointsEnabled) {
                drawLowestAndHighestPoints(graphics, clipBounds);
            }

            previousClipBounds = clipBounds;
        }
    }

    /**
     * Enforces repainting of this painter's paintable objects.
     */
    public synchronized void invalidate() {
        if (hillshadeImageTile != null) {
            hillshadeImageTile.dispose();
            hillshadeImageTile = null;
        }
        scaledHillshadeImage = null;

        synchronized (pendingContourLinesLock) {
            if (pendingContourLines != null) {
                pendingContourLines.dispose();
                pendingContourLines = null;
            }
        }

        if (contourLines != null) {
            contourLines.dispose();
            contourLines = null;
        }

        if (elevationRaster != null) {
            elevationRaster.dispose();
            elevationRaster = null;
        }

        if (lowestAndHighestPoints != null) {
            lowestAndHighestPoints.dispose();
            lowestAndHighestPoints = null;
        }
    }

    private void drawZoomLevelDisabled(MapViewGraphics graphics) {
        MapView mapView = graphics.getMapView();
        Graphics2D g = graphics.getDefaultGraphics();
        int x = 10;
        int y = mapView.getHeight() - 10;
        String text = "SRTM Elevation Layer rendering disabled for this zoom level";
        Font font = g.getFont().deriveFont(Font.BOLD, 16);
        DrawUtils.drawOutlinedText(g, x, y, font, Color.RED, Color.WHITE, 1.0f, text);
    }

    private synchronized void drawHillshade(MapViewGraphics graphics, Bounds clipBounds) {
        int layerHillshadeAltitude = layer.getHillshadeAltitude();
        int layerHillshadeAzimuth = layer.getHillshadeAzimuth();

        boolean hillshadeParametersChanged = hillshadeAltitude != layerHillshadeAltitude
                || hillshadeAzimuth != layerHillshadeAzimuth;
        boolean clipBoundsNotCoveredByHillshade = hillshadeImageTile == null || hillshadeParametersChanged
                || !CoordinateUtil.doBoundsContainWithExtraSpace(hillshadeImageTile.getBounds(), clipBounds,
                        2 * hillshadeImageTile.getLatLonStep());

        if (clipBoundsNotCoveredByHillshade || hillshadeParametersChanged) {
            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);
            refreshHillshadeImageTile(scaledBounds, layerHillshadeAltitude, layerHillshadeAzimuth);
            if (hillshadeImageTile == null) {
                return;
            }
        }

        if (hillshadeImageTile.getUnscaledImage() == null) {
            return;
        }

        MapView mapView = graphics.getMapView();
        Graphics2D g = graphics.getDefaultGraphics();

        Point upperLeft = null;
        if (scaledHillshadeImage == null || clipBoundsNotCoveredByHillshade || hillshadeParametersChanged) {

            Bounds hillshadeRenderingBounds = hillshadeImageTile.getBounds();
            hillshadeNorthWest = new LatLon(hillshadeRenderingBounds.getMaxLat(), hillshadeRenderingBounds.getMinLon());
            LatLon hillshadeSouthEast = new LatLon(hillshadeRenderingBounds.getMinLat(),
                    hillshadeRenderingBounds.getMaxLon());
            // Bounds of the hillshade tile in screen coordinates (x, y)
            upperLeft = mapView.getPoint(hillshadeNorthWest);
            Point lowerRight = mapView.getPoint(hillshadeSouthEast);
            // The dimensions of the hillshade tile in screen coordinates
            int onScreenWidth = lowerRight.x - upperLeft.x;
            int onScreenHeight = lowerRight.y - upperLeft.y;
            // Do not try to scale the image if the reported screen size is so large that
            // scaling could result in OutOfMemoryError or IllegalArgumentException of
            // java.awt.image.SampleModel.<init>
            // (For an unknown reason such large screen sizes can be obtained from map view
            // if trying to zoom in as strong as possible)
            if (onScreenWidth > 13000 || onScreenHeight > 13000) {
                Logging.info("ELEVATION: Omitting drawing of hillshade due to large on-screen dimensions "
                        + onScreenWidth + " x " + onScreenHeight + ", which may result in OutOfMemoryError");
                return;
            }
            // Scale the hillshade image to screen dimensions
            scaledHillshadeImage = hillshadeImageTile.getScaledImage(onScreenWidth, onScreenHeight);
            if (scaledHillshadeImage == null) {
                return;
            }
        } else {
            upperLeft = mapView.getPoint(hillshadeNorthWest);
        }
        g.setComposite(AlphaComposite.SrcOver); // default blending
        g.drawImage(scaledHillshadeImage, upperLeft.x, upperLeft.y, scaledHillshadeImage.getWidth(),
                scaledHillshadeImage.getHeight(), null);

        hillshadeAltitude = layerHillshadeAltitude;
        hillshadeAzimuth = layerHillshadeAzimuth;
    }

    private synchronized void drawContourLines(MapViewGraphics graphics, Bounds clipBounds) {
        MapView mapView = graphics.getMapView();

        float layerContourLineStrokeWidth = layer.getContourLineStrokeWidth();
        if (contourLineStroke.getLineWidth() != layerContourLineStrokeWidth) {
            setContourLineStroke(layerContourLineStrokeWidth);
        }
        Coloring.Scheme layerContourLineColoringScheme = layer.getContourLineColoringScheme();
        Color layerContourLineConstantColor = layer.getContourLineConstantColor();

        int layerContourLineIsostep = layer.getContourLineIsostep();
        short layerLowerCutoffElevation = layer.getLowerCutoffElevation();
        short layerUpperCutoffElevation = layer.getUpperCutoffElevation();
        // Reference this draw helper's contour lines
        // This prevents a change of the contour lines while working on them
        // The pending contour lines task may update them asynchronously
        ContourLines currentContourLines = contourLines;

        // If current contour lines exist and cutoff elevation values were reduced,
        // attempt to shrink the amount of contour lines instead of computing new ones
        if (currentContourLines != null && !hasPendingContourLines()) {
            // Attempt to shrink contour lines if cutoff elevations changed
            if ((lowerCutoffElevation < layerLowerCutoffElevation || upperCutoffElevation > layerUpperCutoffElevation)
                    && currentContourLines.shrinkIsolineSegments(layerLowerCutoffElevation,
                            layerUpperCutoffElevation)) {
                lowerCutoffElevation = layerLowerCutoffElevation;
                upperCutoffElevation = layerUpperCutoffElevation;
            }
            // Attempt to prune contour lines if isostep changed
            if (contourLineIsostep != layerContourLineIsostep
                    && currentContourLines.pruneIsolineSegments(layerContourLineIsostep)) {
                contourLineIsostep = layerContourLineIsostep;
            }
        }

        boolean contourLineSettingsChanged = contourLineIsostep != layerContourLineIsostep
                || lowerCutoffElevation != layerLowerCutoffElevation
                || upperCutoffElevation != layerUpperCutoffElevation;
        if (currentContourLines == null || hasPendingContourLines() || contourLineSettingsChanged
                || !CoordinateUtil.doBoundsContainWithExtraSpace(currentContourLines.getBounds(), clipBounds,
                        currentContourLines.getSrtmLatLonStep())) {

            contourLineIsostep = layerContourLineIsostep;
            lowerCutoffElevation = layerLowerCutoffElevation;
            upperCutoffElevation = layerUpperCutoffElevation;

            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);

            ContourLines refreshedContourLines = refreshContourLines(mapView, scaledBounds);
            if (refreshedContourLines != null) {
                currentContourLines = refreshedContourLines;
            }
        }
        if (currentContourLines == null) {
            return;
        }


        ContourLines.IsolinePath[] isolinePaths = currentContourLines.getIsolinePaths(mapView);
        if (isolinePaths.length < 1) {
            return;
        }

        short minEle = lowerCutoffElevation;
        short maxEle = upperCutoffElevation;
        boolean useFalseColor = false;
        if (layerContourLineColoringScheme == Coloring.Scheme.FALSE_COLOR) {
            if (lowestAndHighestPoints == null || !lowestAndHighestPoints.getBounds().equals(clipBounds)) {
                refreshLowestAndHighestPoints(clipBounds);
            }
            if (lowestAndHighestPoints != null) {
                short lowestElevation = (short) lowestAndHighestPoints.getLowestElevation();
                short highestElevation = (short) lowestAndHighestPoints.getHighestElevation();
                if (lowestElevation != SrtmTile.SRTM_DATA_VOID && highestElevation != SrtmTile.SRTM_DATA_VOID
                        && lowestElevation != highestElevation) {
                    minEle = (short) Math.max(minEle, lowestElevation);
                    maxEle = (short) Math.min(maxEle, highestElevation);
                    useFalseColor = true;
                }
            }
        }

        Graphics2D g2 = (Graphics2D) graphics.getDefaultGraphics().create();
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

    private synchronized void setContourLineStroke(float width) {
        contourLineStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private synchronized void drawElevationRaster(MapViewGraphics graphics, Bounds clipBounds) {
        if (elevationRaster == null || !CoordinateUtil.doBoundsContainWithExtraSpace(elevationRaster.getBounds(),
                clipBounds, elevationRaster.getLatLonStep())) {
            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);
            refreshElevationRaster(scaledBounds);
            if (elevationRaster == null) {
                return;
            }
        }

        MapView mapView = graphics.getMapView();
        Graphics2D g = graphics.getDefaultGraphics();

        // Set the font for elevation value strings
        Font font = g.getFont().deriveFont(Font.PLAIN, 10);
        g.setFont(font);

        // Dimensions of an up to 4 digit (+ 2 digits/points space) elevation value
        // string in screen coordinates
        FontMetrics metrics = g.getFontMetrics(font);
        int eleStringDisplayWidth = metrics.stringWidth("000000");
        int eleStringDisplayHeight = metrics.getHeight() + 2;

        // Screen pixel width and height of the elevation raster
        Point p1 = mapView.getPoint(elevationRaster.getBounds().getMin());
        Point p2 = mapView.getPoint(elevationRaster.getBounds().getMax());
        int rasterPixelWidth = Math.abs(p2.x - p1.x);
        int rasterPixelHeight = Math.abs(p2.y - p1.y);

        // Compute grid steps to skip grid points
        // Skip more grid points if further zoomed out from the map
        int gridStepLon = (int) Math
                .round(elevationRaster.getWidth() / (double) rasterPixelWidth * ElevationLayer.PIXELS_PER_GRID_POINT);
        int gridStepLat = (int) Math
                .round(elevationRaster.getHeight() / (double) rasterPixelHeight * ElevationLayer.PIXELS_PER_GRID_POINT);
        // Ensure that the steps are always at least 1
        gridStepLon = Math.max(gridStepLon, 1);
        gridStepLat = Math.max(gridStepLat, 1);

        // The size of the marker of an elevation data point in display coordinates
        final int markerSize = 3;

        // Last y position where label text was drawn
        // Offset initial value outside screen
        int lastLabeledY = rasterPixelHeight - 1 + (eleStringDisplayHeight - eleStringDisplayHeight / 2);

        for (int latIndex = 0; latIndex < elevationRaster.getHeight(); latIndex += gridStepLat) {
            // Last x position where label text was drawn
            // Offset initial value outside screen
            int lastLabeledX = -(eleStringDisplayWidth + markerSize) - 1;
            int textY = lastLabeledY;

            for (int lonIndex = 0; lonIndex < elevationRaster.getWidth(); lonIndex += gridStepLon) {
                LatLonEle latLonEle = elevationRaster.getLatLonEle(latIndex, lonIndex);

                Point p = mapView.getPoint(latLonEle);
                int x = p.x;
                int y = p.y;

                g.setColor(Color.RED);
                g.fillOval(x - markerSize / 2, y - markerSize / 2, markerSize, markerSize);

                int textX = x + markerSize;
                textY = y + eleStringDisplayHeight / 2;
                if (textX - lastLabeledX >= eleStringDisplayWidth && lastLabeledY - textY >= eleStringDisplayHeight) {
                    String eleText = Integer.toString((int) latLonEle.ele());
                    g.setColor(Color.BLUE);
                    g.drawString(eleText, textX, textY);
                    lastLabeledX = textX;
                }
            }

            if (lastLabeledY - textY >= eleStringDisplayHeight) {
                lastLabeledY = textY;
            }
        }
    }

    private synchronized void drawLowestAndHighestPoints(MapViewGraphics graphics, Bounds clipBounds) {
        if (lowestAndHighestPoints == null || !lowestAndHighestPoints.getBounds().equals(clipBounds)) {
            refreshLowestAndHighestPoints(clipBounds);
        }
        if (lowestAndHighestPoints == null) {
            return;
        }

        MapView mapView = graphics.getMapView();
        Graphics2D g = graphics.getDefaultGraphics();

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
        short lowestElevation = (short) lowestAndHighestPoints.getLowestElevation();
        if (lowestElevation != SrtmTile.SRTM_DATA_VOID) {
            textLowestElevation = Short.toString(lowestElevation);
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
        short highestElevation = (short) lowestAndHighestPoints.getHighestElevation();
        if (highestElevation != SrtmTile.SRTM_DATA_VOID) {
            textHighestElevation = Short.toString(highestElevation);
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
        NonOverlappingTextRenderer textRenderer = new NonOverlappingTextRenderer();
        for (Point p : lowestPixels) {
            textRenderer.drawTextSafely(g, p.x + textOffsetX, p.y, Color.BLUE, Color.WHITE, textLowestElevation);
        }
        for (Point p : highestPixels) {
            textRenderer.drawTextSafely(g, p.x + textOffsetX, p.y, Color.BLUE, Color.WHITE, textHighestElevation);
        }
    }

    private void refreshHillshadeImageTile(Bounds bounds, double altitude, double azimuth) {
        // Create and dispose crosswise so that cached tiles are taken over if possible
        HillshadeImageTile oldHillshadeImageTile = hillshadeImageTile;
        try {
            hillshadeImageTile = layer.getElevationDataProvider().getHillshadeImageTile(bounds, altitude, azimuth,
                    false);
        } catch (AsyncOperationException | InterruptedException e) {
            Logging.error("Elevation: Exception during hillshade computation: " + e.toString());
            hillshadeImageTile = null;
        }
        if (oldHillshadeImageTile != null) {
            oldHillshadeImageTile.dispose();
            oldHillshadeImageTile = null;
        }
    }

    private boolean hasPendingContourLines() {
        synchronized (pendingContourLinesLock) {
            return pendingContourLines != null;
        }
    }

    private ContourLines refreshContourLines(MapView mapView, Bounds bounds) {
        synchronized (pendingContourLinesLock) {
            if (isDisposed) {
                return null;
            }

            Point p1 = mapView.getPoint(bounds.getMin());
            Point p2 = mapView.getPoint(bounds.getMax());
            int requestPixelWidth = Math.abs(p2.x - p1.x);
            int requestPixelHeight = Math.abs(p2.y - p1.y);

            // Dispose pending contour lines that are no longer appropriate for current view
            if (pendingContourLines != null && (!pendingContourLines.getRequestBounds().equals(bounds)
                    || pendingContourLines.getIsostep() != contourLineIsostep
                    || pendingContourLines.getRequestPixelWidth() != requestPixelWidth
                    || pendingContourLines.getRequestPixelHeight() != requestPixelHeight
                    || pendingContourLines.getLowerCutoffElevation() != lowerCutoffElevation
                    || pendingContourLines.getUpperCutoffElevation() != upperCutoffElevation)) {
                pendingContourLines.dispose();
                pendingContourLines = null;
            }

            Bounds mapViewBounds = mapView.getLatLonBounds(mapView.getBounds());

            if (pendingContourLines == null) {
                try {
                    clearContourLinesIfNotIntersects(mapViewBounds);

                    Runnable repaintTask = () -> layer.triggerRepaint();

                    pendingContourLines = layer.getElevationDataProvider().getPendingSrtmContourLines(mapView, bounds,
                            contourLineIsostep, lowerCutoffElevation, upperCutoffElevation, repaintTask);
                } catch (AsyncOperationException | ElevationGridException e) {
                    Logging.warn("Elevation: Exception while waiting for contour lines to be computed for drawing: "
                            + e.toString());
                    clearContourLinesIfNotIntersects(mapViewBounds);
                    return null;
                }
            }

            Future<ContourLines> future = pendingContourLines.getContourLines();
            if (future.isCancelled()) {
                pendingContourLines.dispose();
                pendingContourLines = null;
                clearContourLinesIfNotIntersects(mapViewBounds);
                return null;
            }

            if (future.isDone()) {
                try {
                    ContourLines computedContourLines = future.get();
                    ContourLines newContourLines = pendingContourLines.claimContourLines(computedContourLines);
                    pendingContourLines.dispose();
                    pendingContourLines = null;

                    if (newContourLines == null) {
                        return null; // It was discarded; do not install it.
                    }

                    // Create and dispose crosswise so that cached tiles are taken over if possible
                    ContourLines oldContourLines = contourLines;
                    if (oldContourLines != null) {
                        oldContourLines.dispose();
                    }
                    contourLines = newContourLines;
                    return newContourLines;

                } catch (InterruptedException e) {
                    pendingContourLines.dispose();
                    pendingContourLines = null;
                    clearContourLinesIfNotIntersects(mapViewBounds);
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException | CancellationException e) {
                    Logging.warn("Elevation: Exception while waiting for contour lines to be computed for drawing: "
                            + e.toString());
                    pendingContourLines.dispose();
                    pendingContourLines = null;
                    clearContourLinesIfNotIntersects(mapViewBounds);
                    return null;
                }
            }
            return null;
        }
    }

    private void clearContourLinesIfNotIntersects(Bounds mapViewBounds) {
        if (contourLines != null && !mapViewBounds.intersects(contourLines.getBounds())) {
            clearContourLines();
        }
    }

    private void clearContourLines() {
        if (contourLines != null) {
            contourLines.dispose();
            contourLines = null;
        }
    }

    private void refreshElevationRaster(Bounds bounds) {
        // Create and dispose crosswise so that cached tiles are taken over if possible
        ElevationRaster oldElevationRaster = elevationRaster;
        elevationRaster = layer.getElevationDataProvider().getElevationRaster(bounds);
        if (oldElevationRaster != null) {
            oldElevationRaster.dispose();
            oldElevationRaster = null;
        }
    }

    private void refreshLowestAndHighestPoints(Bounds bounds) {
        // Create and dispose crosswise so that cached tiles are taken over if possible
        LowestAndHighestPoints oldLowestAndHighestPoints = lowestAndHighestPoints;
        lowestAndHighestPoints = layer.getElevationDataProvider().getLowestAndHighestPoints(bounds);
        if (oldLowestAndHighestPoints != null) {
            oldLowestAndHighestPoints.dispose();
            oldLowestAndHighestPoints = null;
        }
    }

    public void dispose() {
        synchronized (pendingContourLinesLock) {
            if (pendingContourLines != null) {
                pendingContourLines.dispose();
                pendingContourLines = null;
            }
            isDisposed = true;
        }
    }

    @Override
    public void detachFromMapView(MapViewEvent event) {
        layer.removeInvalidationListener(this);
    }

    @Override
    public void paintableInvalidated(PaintableInvalidationEvent event) {
    }
}
