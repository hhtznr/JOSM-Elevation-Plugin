package hhtznr.josm.plugins.elevation.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
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
     * The color in which the contour lines are painted on the map.
     */
    public static final Color CONTOUR_LINE_COLOR = new Color(210, 180, 115);

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
                hillshadeTile = layer.getElevationDataProvider().getHillshadeImageTile(scaledBounds,
                        hillshadeAltitude, hillshadeAzimuth, false);
            }
            if (hillshadeTile == null)
                return;
            Bounds actualHillshadeBounds = hillshadeTile.getActualBounds();
            hillshadeNorthWest = new LatLon(actualHillshadeBounds.getMaxLat(), actualHillshadeBounds.getMinLon());
            LatLon hillshadeSouthEast = new LatLon(actualHillshadeBounds.getMinLat(), actualHillshadeBounds.getMaxLon());
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
                || !contourLines.covers(bounds)) {
            contourLineIsostep = layer.getContourLineIsostep();
            Bounds scaledBounds = getScaledBounds(bounds, BOUNDS_SCALE_FACTOR);
            contourLines = layer.getElevationDataProvider().getContourLines(scaledBounds, contourLineIsostep);
        }
        if (contourLines == null)
            return;
        g.setColor(CONTOUR_LINE_COLOR);
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (LatLonLine segment : contourLines.getIsolineSegments()) {
            Point p1 = mv.getPoint(segment.getLatLon1());
            Point p2 = mv.getPoint(segment.getLatLon2());
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
        }
    }

    private void drawElevationRaster(Graphics2D g, MapView mv, Bounds bounds) {
        ElevationRaster elevationRaster = layer.getElevationDataProvider().getElevationRaster(bounds);
        if (elevationRaster == null)
            return;
        g.setColor(Color.RED);
        for (int latIndex = 0; latIndex < elevationRaster.getHeight(); latIndex++) {
            for (int lonIndex = 0; lonIndex < elevationRaster.getWidth(); lonIndex++) {
                LatLonEle latLonEle = elevationRaster.getLatLonEle(latIndex, lonIndex);
                Point p = mv.getPoint(latLonEle);
                String ele = Integer.toString((int) latLonEle.ele());
                g.fillOval(p.x - 1, p.y - 1, 3, 3);
                g.setFont(g.getFont().deriveFont(Font.PLAIN, 10));
                g.drawString(ele, p.x + 2, p.y + 5);
            }
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
