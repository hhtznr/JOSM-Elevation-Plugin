package hhtznr.josm.plugins.elevation.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.MapViewEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationListener;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.CoordinateUtil;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;

/**
 * The class that helps drawing elevation contour lines, elevation data raster
 * and hillshade on the elevation layer.
 *
 * @author Harald Hetzner
 */
public class ElevationDrawHelper implements MapViewPaintable.LayerPainter, PaintableInvalidationListener {

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

    private final ElevationLayer layer;

    private int contourLineIsostep;
    private BasicStroke contourLineStroke;
    private Color contourLineColor;
    private int lowerCutoffElevation;
    private int upperCutoffElevation;
    private ContourLines contourLines = null;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;
    private HillshadeImageTile hillshadeImageTile = null;
    private BufferedImage scaledHillshadeImage = null;
    private LatLon hillshadeNorthWest = null;

    private ElevationRaster elevationRaster = null;

    private Bounds previousClipBounds = null;

    private List<List<LatLonEle>> lowestAndHighestPoints = null;

    /**
     * Creates a new elevation draw helper.
     *
     * @param layer The elevation layer for which this draw helper performs
     *              rendering.
     */
    public ElevationDrawHelper(ElevationLayer layer) {
        this.layer = layer;
        this.layer.addInvalidationListener(this);
        contourLineIsostep = layer.getContourLineIsostep();
        setContourLineStroke(layer.getContourLineStrokeWidth());
        contourLineColor = layer.getContourLineColor();
    }

    @Override
    public void paint(MapViewGraphics graphics) {
        synchronized (this) {
            if (!layer.isElevationRasterEnabled())
                elevationRaster = null;
            if (!layer.isContourLinesEnabled())
                contourLines = null;
            if (!layer.isHillshadeEnabled()) {
                hillshadeImageTile = null;
                scaledHillshadeImage = null;
            }
            if (!layer.isLowestAndHighestPointsEnabled())
                lowestAndHighestPoints = null;
        }

        if (!layer.isContourLinesEnabled() && !layer.isElevationRasterEnabled() && !layer.isHillshadeEnabled()
                && !layer.isLowestAndHighestPointsEnabled())
            return;

        Bounds clipBounds = graphics.getClipBounds().getLatLonBoundsBox();

        // Disable drawing of contour lines if the map is zoomed out too much
        // This prevents high CPU and I/O usage
        boolean zoomLevelDisabled = clipBounds.getMaxLat() - clipBounds.getMinLat() > layer.getRenderingLimit()
                || clipBounds.getMaxLon() - clipBounds.getMinLon() > layer.getRenderingLimit();

        if (zoomLevelDisabled) {
            drawZoomLevelDisabled(graphics.getDefaultGraphics(), graphics.getMapView());
        } else {
            if (previousClipBounds != null
                    && (previousClipBounds.getWidth() > SCALE_DISCARD_FACTOR * clipBounds.getWidth()
                            || previousClipBounds.getHeight() > SCALE_DISCARD_FACTOR * clipBounds.getHeight()))
                invalidate();

            if (layer.isHillshadeEnabled())
                drawHillshade(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            if (layer.isContourLinesEnabled())
                drawContourLines(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            if (layer.isElevationRasterEnabled())
                drawElevationRaster(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            if (layer.isLowestAndHighestPointsEnabled())
                drawLowestAndHighestPoints(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            previousClipBounds = clipBounds;
        }
    }

    /**
     * Enforces repainting of this painter's paintable objects.
     */
    public synchronized void invalidate() {
        contourLines = null;
        hillshadeImageTile = null;
        scaledHillshadeImage = null;
        elevationRaster = null;
        lowestAndHighestPoints = null;
    }

    private void drawZoomLevelDisabled(Graphics2D g, MapView mv) {
        g.setColor(Color.RED);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16));
        g.drawString("Elevation layer disabled for this zoom level", 10, mv.getHeight() - 10);
    }

    private synchronized void drawHillshade(Graphics2D g, MapView mv, Bounds clipBounds) {
        int layerHillshadeAltitude = layer.getHillshadeAltitude();
        int layerHillshadeAzimuth = layer.getHillshadeAzimuth();

        boolean hillshadeParametersChanged = hillshadeAltitude != layerHillshadeAltitude
                || hillshadeAzimuth != layerHillshadeAzimuth;
        boolean clipBoundsNotCoveredByHillshade = hillshadeImageTile == null || hillshadeParametersChanged
                || !hillshadeImageTile.covers(clipBounds);

        if (clipBoundsNotCoveredByHillshade || hillshadeParametersChanged) {
            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);
            hillshadeImageTile = layer.getElevationDataProvider().getHillshadeImageTile(scaledBounds,
                    layerHillshadeAltitude, layerHillshadeAzimuth, false);
            if (hillshadeImageTile == null)
                return;
        }

        if (hillshadeImageTile.getUnscaledImage() == null)
            return;

        Point upperLeft = null;
        if (scaledHillshadeImage == null || clipBoundsNotCoveredByHillshade || hillshadeParametersChanged) {

            Bounds hillshadeRenderingBounds = hillshadeImageTile.renderingBounds;
            hillshadeNorthWest = new LatLon(hillshadeRenderingBounds.getMaxLat(), hillshadeRenderingBounds.getMinLon());
            LatLon hillshadeSouthEast = new LatLon(hillshadeRenderingBounds.getMinLat(),
                    hillshadeRenderingBounds.getMaxLon());
            // Bounds of the hillshade tile in screen coordinates (x, y)
            upperLeft = mv.getPoint(hillshadeNorthWest);
            Point lowerRight = mv.getPoint(hillshadeSouthEast);
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
            if (scaledHillshadeImage == null)
                return;
        } else {
            upperLeft = mv.getPoint(hillshadeNorthWest);
        }
        g.setComposite(AlphaComposite.SrcOver); // default blending
        g.drawImage(scaledHillshadeImage, upperLeft.x, upperLeft.y, scaledHillshadeImage.getWidth(),
                scaledHillshadeImage.getHeight(), null);

        hillshadeAltitude = layerHillshadeAltitude;
        hillshadeAzimuth = layerHillshadeAzimuth;
    }

    private synchronized void drawContourLines(Graphics2D g, MapView mv, Bounds clipBounds) {
        float layerContourLineStrokeWidth = layer.getContourLineStrokeWidth();
        Color layerContourLineColor = layer.getContourLineColor();
        if (contourLineStroke.getLineWidth() != layerContourLineStrokeWidth
                || contourLineColor != layerContourLineColor) {
            setContourLineStroke(layerContourLineStrokeWidth);
            contourLineColor = layerContourLineColor;
        }

        int layerContourLineIsostep = layer.getContourLineIsostep();
        int layerLowerCutoffElevation = layer.getLowerCutoffElevation();
        int layerUpperCutoffElevation = layer.getUpperCutoffElevation();
        if (contourLines == null || contourLineIsostep != layerContourLineIsostep
                || lowerCutoffElevation != layerLowerCutoffElevation
                || upperCutoffElevation != layerUpperCutoffElevation || !contourLines.covers(clipBounds)) {
            contourLineIsostep = layerContourLineIsostep;
            lowerCutoffElevation = layerLowerCutoffElevation;
            upperCutoffElevation = layerUpperCutoffElevation;

            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);
            contourLines = layer.getElevationDataProvider().getContourLines(scaledBounds, contourLineIsostep,
                    lowerCutoffElevation, upperCutoffElevation);
            if (contourLines == null)
                return;
        }

        Rectangle screenClip = g.getClipBounds();

        g.setColor(contourLineColor);
        g.setStroke(contourLineStroke);
        for (ContourLines.IsolineSegments isolineSegment : contourLines.getIsolineSegments()) {
            for (LatLonLine segment : isolineSegment.getLineSegments()) {
                Point p1 = mv.getPoint(segment.getLatLon1());
                Point p2 = mv.getPoint(segment.getLatLon2());
                // Only draw if the line's bounding box intersects the map's bounding box
                if (screenClip.intersectsLine(p1.x, p1.y, p2.x, p2.y))
                    g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
    }

    private synchronized void setContourLineStroke(float width) {
        contourLineStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private synchronized void drawElevationRaster(Graphics2D g, MapView mv, Bounds clipBounds) {
        if (elevationRaster == null || !elevationRaster.covers(clipBounds)) {
            Bounds scaledBounds = CoordinateUtil.getScaledBounds(clipBounds, BOUNDS_SCALE_FACTOR);
            elevationRaster = layer.getElevationDataProvider().getElevationRaster(scaledBounds);
            if (elevationRaster == null)
                return;
        }

        // Set the font for elevation value strings
        Font font = g.getFont().deriveFont(Font.PLAIN, 10);
        g.setFont(font);

        // Dimensions of an up to 4 digit (+ 2 digits/points space) elevation value
        // string in screen coordinates
        FontMetrics metrics = g.getFontMetrics(font);
        int eleStringDisplayWidth = metrics.stringWidth("000000");
        int eleStringDisplayHeight = metrics.getHeight() + 2;
        // Dimensions of the map view in screen coordinates
        int mapViewDisplayWidth = mv.getWidth();
        int mapViewDisplayHeight = mv.getHeight();

        // Dimensions of the map view in world coordinates
        Bounds mapViewBounds = mv.getLatLonBounds(mv.getBounds());
        double mapViewWidth = mapViewBounds.getWidth();
        double mapViewHeight = mapViewBounds.getHeight();

        // Dimensions of the elevation value string in world coordinates
        double eleStringWidth = (double) eleStringDisplayWidth / (double) mapViewDisplayWidth * mapViewWidth;
        double eleStringHeight = (double) eleStringDisplayHeight / (double) mapViewDisplayHeight * mapViewHeight;

        // Distance between two adjacent raster points in world coordinates
        double latStep = elevationRaster.getLatStep();
        double lonStep = elevationRaster.getLonStep();

        // The size of the marker of an elevation data point in display coordinates
        final int markerSize = 3;

        double latDist = 0.0;
        for (int latIndex = 0; latIndex < elevationRaster.getHeight(); latIndex++) {
            double lonDist = 0.0;
            for (int lonIndex = 0; lonIndex < elevationRaster.getWidth(); lonIndex++) {
                LatLonEle latLonEle = elevationRaster.getLatLonEle(latIndex, lonIndex);

                Point p = mv.getPoint(latLonEle);
                String eleText = Integer.toString((int) latLonEle.ele());

                g.setColor(Color.RED);
                g.fillOval(p.x - markerSize / 2, p.y - markerSize / 2, markerSize, markerSize);
                if (latDist == 0.0 && lonDist == 0.0) {
                    g.setColor(Color.BLUE);
                    g.drawString(eleText, p.x + markerSize, p.y + eleStringDisplayHeight / 2);
                }
                lonDist += lonStep;
                if (lonDist > eleStringWidth)
                    lonDist = 0.0;
            }
            latDist += latStep;
            if (latDist > eleStringHeight)
                latDist = 0.0;
        }
    }

    private synchronized void drawLowestAndHighestPoints(Graphics2D g, MapView mv, Bounds clipBounds) {
        if (lowestAndHighestPoints == null || !clipBounds.equals(previousClipBounds))
            lowestAndHighestPoints = layer.getElevationDataProvider().getLowestAndHighestPoints(clipBounds);
        if (lowestAndHighestPoints == null || lowestAndHighestPoints.size() < 2)
            return;

        List<LatLonEle> lowestPoints = lowestAndHighestPoints.get(0);
        List<LatLonEle> highestPoints = lowestAndHighestPoints.get(1);
        List<Point> lowestPixels = new ArrayList<>(lowestPoints.size());
        for (LatLonEle latLonEle : lowestPoints) {
            Point p = mv.getPoint(latLonEle);
            lowestPixels.add(p);
        }
        String textLowestElevation = "";
        if (lowestPoints.size() > 0)
            textLowestElevation = Integer.toString((int) lowestPoints.get(0).ele());
        List<Point> highestPixels = new ArrayList<>(highestPoints.size());
        for (LatLonEle latLonEle : highestPoints) {
            Point p = mv.getPoint(latLonEle);
            highestPixels.add(p);
        }
        String textHighestElevation = "";
        if (highestPoints.size() > 0)
            textHighestElevation = Integer.toString((int) highestPoints.get(0).ele());

        // The size of the marker of an elevation data point in display coordinates
        final int markerSize = 8;
        final int textOffsetX = markerSize / 2 + 3;

        // Draw the symbols for the lowest and highest points
        for (Point p : lowestPixels)
            drawEquilateralTriangle(g, p.x, p.y, markerSize, false, Color.GREEN, Color.BLACK);
        for (Point p : highestPixels)
            drawEquilateralTriangle(g, p.x, p.y, markerSize, true, Color.RED, Color.BLACK);

        // Set the font for elevation value strings
        Font font = g.getFont().deriveFont(Font.BOLD, 12);
        g.setFont(font);
        // Draw non-overlapping labels for the highest and lowest points
        NonOverlappingTextRenderer textRenderer = new NonOverlappingTextRenderer();
        for (Point p : lowestPixels)
            textRenderer.drawTextSafely(g, p, textLowestElevation, textOffsetX, Color.BLUE);
        for (Point p : highestPixels)
            textRenderer.drawTextSafely(g, p, textHighestElevation, textOffsetX, Color.BLUE);
    }

    private void drawEquilateralTriangle(Graphics2D g, int centerX, int centerY, int sideLength, boolean upright,
            Color colorFill, Color colorOutline) {
        // Calculate height of the triangle
        double height = sideLength * (Math.sqrt(3.0) / 2.0);

        int[] xPoints = new int[3];
        int[] yPoints = new int[3];

        if (upright) {
            // Calculate the vertices
            // Top vertex: (Cx, Cy - 2/3*H)
            int topX = centerX;
            int topY = (int) (centerY - (2.0 / 3.0) * height);

            // Bottom-left vertex: (Cx - S/2, Cy + 1/3*H)
            int bottomLeftX = (int) (centerX - sideLength / 2.0);
            int bottomLeftY = (int) (centerY + (1.0 / 3.0) * height);

            // Bottom-right vertex: (Cx + S/2, Cy + 1/3*H)
            int bottomRightX = (int) (centerX + sideLength / 2.0);
            int bottomRightY = (int) (centerY + (1.0 / 3.0) * height);

            // Define the polygon coordinates
            xPoints[0] = topX;
            xPoints[1] = bottomLeftX;
            xPoints[2] = bottomRightX;
            yPoints[0] = topY;
            yPoints[1] = bottomLeftY;
            yPoints[2] = bottomRightY;
        } else {
            // Calculate the vertices
            // Bottom vertex: (Cx, Cy + 2/3*H)
            int bottomX = centerX;
            int bottomY = (int) (centerY + (2.0 / 3.0) * height);

            // Top-left vertex: (Cx - S/2, Cy - 1/3*H)
            int topLeftX = (int) (centerX - sideLength / 2.0);
            int topLeftY = (int) (centerY - (1.0 / 3.0) * height);

            // Top-right vertex: (Cx + S/2, Cy - 1/3*H)
            int topRightX = (int) (centerX + sideLength / 2.0);
            int topRightY = (int) (centerY - (1.0 / 3.0) * height);

            // Define the polygon coordinates
            xPoints[0] = bottomX;
            xPoints[1] = topRightX;
            xPoints[2] = topLeftX;
            yPoints[0] = bottomY;
            yPoints[1] = topRightY;
            yPoints[2] = topLeftY;
        }
        int nPoints = 3;

        // Draw and fill the triangle
        g.setColor(colorFill);
        g.fillPolygon(xPoints, yPoints, nPoints);
        if (colorOutline != null) {
            g.setColor(colorOutline);
            g.drawPolygon(xPoints, yPoints, nPoints);
        }
    }

    @Override
    public void detachFromMapView(MapViewEvent event) {
        layer.removeInvalidationListener(this);
    }

    @Override
    public void paintableInvalidated(PaintableInvalidationEvent event) {
    }

    private static class NonOverlappingTextRenderer {

        // A list to keep track of the screen areas where text has been drawn
        private final List<Rectangle> occupiedTextAreas = new ArrayList<>();

        private void drawTextSafely(Graphics2D g, Point p, String text, int offSetX, Color color) {
            // Get font metrics to measure the text size
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();
            int ascent = metrics.getAscent();

            int x = p.x + offSetX;
            int y = p.y - textHeight / 2 + ascent;
            /*
             * Calculate the bounding box for the potential text label We use p.y +
             * metrics.getAscent() because drawString draws from the baseline up;
             * metrics.getAscent() = Y start position (top edge of text)
             */
            Rectangle textBounds = new Rectangle(x, y - ascent, textWidth, textHeight);

            // Check for overlap with any previously drawn text areas
            boolean overlaps = false;
            for (Rectangle existingArea : occupiedTextAreas) {
                if (textBounds.intersects(existingArea)) {
                    overlaps = true;
                    break; // Overlap found, stop checking
                }
            }

            // 3. Draw and record only if no overlap
            if (!overlaps) {
                g.setColor(color);
                g.drawString(text, x, y); // drawString uses baseline Y

                // 4. Add this area to our list of occupied spaces
                occupiedTextAreas.add(textBounds);
            }
        }
    }
}
