package hhtznr.josm.plugins.elevation.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.MapViewEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationEvent;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.PaintableInvalidationListener;

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
    ContourLines contourLines = null;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;
    HillshadeImageTile hillshadeTile = null;
    BufferedImage hillshadeImage = null;
    private LatLon hillshadeNorthWest = null;

    private Bounds previousClipBounds = null;

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
        if (!layer.isContourLinesEnabled())
            contourLines = null;
        if (!layer.isHillshadeEnabled()) {
            hillshadeTile = null;
            hillshadeImage = null;
        }
        if (!layer.isContourLinesEnabled() && !layer.isElevationRasterEnabled() && !layer.isHillshadeEnabled())
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
                            || previousClipBounds.getHeight() > SCALE_DISCARD_FACTOR * clipBounds.getHeight())) {
                contourLines = null;
                hillshadeTile = null;
                hillshadeImage = null;
            }

            if (layer.isHillshadeEnabled())
                drawHillshade(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            if (layer.isContourLinesEnabled())
                drawContourLines(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            if (layer.isElevationRasterEnabled())
                drawElevationRaster(graphics.getDefaultGraphics(), graphics.getMapView(), clipBounds);

            previousClipBounds = clipBounds;
        }
    }

    private void drawZoomLevelDisabled(Graphics2D g, MapView mv) {
        g.setColor(Color.RED);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16));
        g.drawString("Contour lines disabled for this zoom level", 10, mv.getHeight() - 10);
    }

    private void drawHillshade(Graphics2D g, MapView mv, Bounds bounds) {
        Point upperLeft = null;
        if (hillshadeImage == null || hillshadeAltitude != layer.getHillshadeAltitude()
                || hillshadeAzimuth != layer.getHillshadeAzimuth() || !bounds.equals(previousClipBounds)) {
            hillshadeAltitude = layer.getHillshadeAltitude();
            hillshadeAzimuth = layer.getHillshadeAzimuth();
            if (hillshadeTile == null || !hillshadeTile.covers(bounds)) {
                Bounds scaledBounds = getScaledBounds(bounds, BOUNDS_SCALE_FACTOR);
                hillshadeTile = layer.getElevationDataProvider().getHillshadeImageTile(scaledBounds, hillshadeAltitude,
                        hillshadeAzimuth, false);
            }
            if (hillshadeTile == null)
                return;
            Bounds actualHillshadeBounds = hillshadeTile.getActualBounds();
            hillshadeNorthWest = new LatLon(actualHillshadeBounds.getMaxLat(), actualHillshadeBounds.getMinLon());
            LatLon hillshadeSouthEast = new LatLon(actualHillshadeBounds.getMinLat(),
                    actualHillshadeBounds.getMaxLon());
            // Bounds of the hillshade tile in screen coordinates (x, y)
            upperLeft = mv.getPoint(hillshadeNorthWest);
            Point lowerRight = mv.getPoint(hillshadeSouthEast);
            // The dimensions of the hillshade tile in screen coordinates
            int screenWidth = lowerRight.x - upperLeft.x;
            int screenHeight = lowerRight.y - upperLeft.y;
            // Do not try to scale the image if the reported screen size is so large that
            // scaling could result in OutOfMemoryError or IllegalArgumentException of
            // java.awt.image.SampleModel.<init>
            // (For an unknown reason such large screen sizes can be obtained from map view
            // if trying to zoom in as strong as possible)
            if (screenWidth > 13000 || screenHeight > 13000)
                return;
            // Scale the hillshade image to screen dimensions
            hillshadeImage = hillshadeTile.getScaledImage(screenWidth, screenHeight);
        }
        if (upperLeft == null)
            upperLeft = mv.getPoint(hillshadeNorthWest);
        g.drawImage(hillshadeImage, upperLeft.x, upperLeft.y, hillshadeImage.getWidth(), hillshadeImage.getHeight(),
                null);
    }

    private void drawContourLines(Graphics2D g, MapView mv, Bounds bounds) {
        if (contourLines == null || contourLineIsostep != layer.getContourLineIsostep()
                || contourLineStroke.getLineWidth() != layer.getContourLineStrokeWidth()
                || contourLineColor != layer.getContourLineColor() || !contourLines.covers(bounds)) {
            contourLineIsostep = layer.getContourLineIsostep();
            setContourLineStroke(layer.getContourLineStrokeWidth());
            contourLineColor = layer.getContourLineColor();
            Bounds scaledBounds = getScaledBounds(bounds, BOUNDS_SCALE_FACTOR);
            contourLines = layer.getElevationDataProvider().getContourLines(scaledBounds, contourLineIsostep);
        }
        if (contourLines == null)
            return;
        g.setColor(contourLineColor);
        g.setStroke(contourLineStroke);
        for (LatLonLine segment : contourLines.getIsolineSegments()) {
            Point p1 = mv.getPoint(segment.getLatLon1());
            Point p2 = mv.getPoint(segment.getLatLon2());
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
        }
    }

    private void setContourLineStroke(float width) {
        contourLineStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private void drawElevationRaster(Graphics2D g, MapView mv, Bounds bounds) {
        ElevationRaster elevationRaster = layer.getElevationDataProvider().getElevationRaster(bounds);
        if (elevationRaster == null)
            return;

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
        double eleStringWidth = Double.valueOf(eleStringDisplayWidth) / Double.valueOf(mapViewDisplayWidth)
                * mapViewWidth;
        double eleStringHeight = Double.valueOf(eleStringDisplayHeight) / Double.valueOf(mapViewDisplayHeight)
                * mapViewHeight;

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
                String ele = Integer.toString((int) latLonEle.ele());
                g.setColor(Color.RED);
                g.fillOval(p.x - markerSize / 2, p.y - markerSize / 2, markerSize, markerSize);
                if (latDist == 0.0 && lonDist == 0.0) {
                    g.setColor(Color.BLUE);
                    g.drawString(ele, p.x + markerSize, p.y + eleStringDisplayHeight / 2);
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

    private static Bounds getScaledBounds(Bounds bounds, double factor) {
        if (factor <= 0.0)
            throw new IllegalArgumentException("Scale factor " + factor + " <= 0");

        double latRange = bounds.getHeight();
        double lonRange = bounds.getWidth();
        double halfDeltaLat = 0.5 * (latRange * factor - latRange);
        double halfDeltaLon = 0.5 * (lonRange * factor - lonRange);
        double minLat = Math.max(bounds.getMinLat() - halfDeltaLat, -90.0);
        double maxLat = Math.min(bounds.getMaxLat() + halfDeltaLat, 90.0);
        double minLon = bounds.getMinLon() - halfDeltaLon;
        // Across 180th meridian
        if (minLon < -180.0)
            minLon = minLon + 360.0;
        double maxLon = bounds.getMaxLon() + halfDeltaLon;
        // Across 180th meridian
        if (maxLon > 180.0)
            maxLon = maxLon - 360.0;
        return new Bounds(minLat, minLon, maxLat, maxLon);
    }

    @Override
    public void detachFromMapView(MapViewEvent event) {
        layer.removeInvalidationListener(this);
    }

    @Override
    public void paintableInvalidated(PaintableInvalidationEvent event) {
    }
}
