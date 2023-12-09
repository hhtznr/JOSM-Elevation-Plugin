package hhtznr.josm.plugins.elevation.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
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

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;
import hhtznr.josm.plugins.elevation.math.Hillshade;

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

    private final ElevationLayer layer;

    private int contourLineIsostep;
    List<LatLonLine> contourLineSegments = null;

    private int hillshadeAltitude;
    private int hillshadeAzimuth;
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
        Bounds clipBounds = graphics.getClipBounds().getLatLonBoundsBox();

        // Disable drawing of contour lines if the map is zoomed out too much
        // This prevents high CPU and I/O usage
        boolean zoomLevelDisabled = clipBounds.getMaxLat() - clipBounds.getMinLat() > layer.getRenderingLimit()
                || clipBounds.getMaxLon() - clipBounds.getMinLon() > layer.getRenderingLimit();

        if (zoomLevelDisabled) {
            drawZoomLevelDisabled(graphics.getDefaultGraphics(), graphics.getMapView());
        } else {
            SRTMTileGrid contourLineTileGrid = layer.getContourLineTileGrid(null);
            SRTMTileGrid hillshadeTileGrid = layer.getHillshadeTileGrid(null);
            // If the bounds changed since the last paint operation
            if (!clipBounds.equals(previousClipBounds)) {
                // Create a new SRTM tile grid for the map bounds if no grid was created or an
                // existing grid does not cover the current bounds
                if ((layer.isContourLinesEnabled() || layer.isElevationRasterEnabled())
                        && (contourLineTileGrid == null || !contourLineTileGrid.covers(clipBounds))) {
                    contourLineTileGrid = layer.getContourLineTileGrid(clipBounds);
                    contourLineSegments = null;
                }
                // Create a new SRTM tile grid for hillshade use
                if (layer.isHillshadeEnabled()) {
                    hillshadeTileGrid = layer.getHillshadeTileGrid(clipBounds);
                    hillshadeImage = null;
                }
            }
            if (layer.isHillshadeEnabled() && hillshadeTileGrid != null)
                drawHillshade(graphics.getDefaultGraphics(), graphics.getMapView(), hillshadeTileGrid);

            if (layer.isContourLinesEnabled() && contourLineTileGrid != null)
                drawSRTMIsolines(graphics.getDefaultGraphics(), graphics.getMapView(), contourLineTileGrid);

            if (layer.isElevationRasterEnabled() && contourLineTileGrid != null)
                drawSRTMRaster(graphics.getDefaultGraphics(), graphics.getMapView(), contourLineTileGrid);

            previousClipBounds = clipBounds;
        }
    }

    private void drawZoomLevelDisabled(Graphics2D g, MapView mv) {
        g.setColor(Color.RED);
        g.setFont(g.getFont().deriveFont(Font.BOLD, 16));
        g.drawString("Contour lines disabled for this zoom level", 10, mv.getHeight() - 10);
    }

    private void drawHillshade(Graphics2D g, MapView mv, SRTMTileGrid srtmTileGrid) {
        Point upperLeft = null;
        if (hillshadeImage == null || hillshadeAltitude != layer.getHillshadeAltitude()
                || hillshadeAzimuth != layer.getHillshadeAzimuth()) {
            hillshadeAltitude = layer.getHillshadeAltitude();
            hillshadeAzimuth = layer.getHillshadeAzimuth();
            Hillshade.ImageTile hillshadeTile = srtmTileGrid.getHillshadeImage(hillshadeAltitude, hillshadeAzimuth,
                    false);
            if (hillshadeTile == null)
                return;
            // The dimensions of the unscaled hillshade image
            int imageWidth = hillshadeTile.getImage().getWidth();
            int imageHeight = hillshadeTile.getImage().getHeight();
            if (imageWidth <= 0 || imageHeight <= 0) {
                Logging.error(
                        "Elevation: Hillshade size too small: width = " + imageWidth + ", height = " + imageHeight);
                return;
            }

            hillshadeNorthWest = hillshadeTile.getNorthWest();
            // Bounds of the hillshade tile in screen coordinates (x, y)
            upperLeft = mv.getPoint(hillshadeNorthWest);
            Point lowerRight = mv.getPoint(hillshadeTile.getSouthEast());
            // The dimensions of the hillshade tile in screen coordinates
            int screenWidth = lowerRight.x - upperLeft.x;
            int screenHeight = lowerRight.y - upperLeft.y;
            // Scale the hillshade image to screen dimensions
            // https://stackoverflow.com/questions/4216123/how-to-scale-a-bufferedimage
            double sx = Double.valueOf(screenWidth) / Double.valueOf(imageWidth);
            double sy = Double.valueOf(screenHeight) / Double.valueOf(imageHeight);
            AffineTransform at = new AffineTransform();
            at.scale(sx, sy);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            hillshadeImage = scaleOp.filter(hillshadeTile.getImage(), null);
        }
        if (upperLeft == null)
            upperLeft = mv.getPoint(hillshadeNorthWest);
        g.drawImage(hillshadeImage, upperLeft.x, upperLeft.y, hillshadeImage.getWidth(), hillshadeImage.getHeight(),
                null);
    }

    private void drawSRTMIsolines(Graphics2D g, MapView mv, SRTMTileGrid srtmTileGrid) {
        if (contourLineSegments == null || contourLineIsostep != layer.getContourLineIsostep()) {
            contourLineIsostep = layer.getContourLineIsostep();
            contourLineSegments = srtmTileGrid.getIsolineSegments(contourLineIsostep);
        }
        if (contourLineSegments == null)
            return;
        g.setColor(CONTOUR_LINE_COLOR);
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (LatLonLine segment : contourLineSegments) {
            Point p1 = mv.getPoint(segment.getLatLon1());
            Point p2 = mv.getPoint(segment.getLatLon2());
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
        }
    }

    private void drawSRTMRaster(Graphics2D g, MapView mv, SRTMTileGrid srtmTileGrid) {
        g.setColor(Color.RED);
        for (LatLonEle latLonEle : srtmTileGrid.getLatLonEleList()) {
            Point p = mv.getPoint(latLonEle);
            String ele = Integer.toString((int) latLonEle.ele());
            g.fillOval(p.x - 1, p.y - 1, 3, 3);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 10));
            g.drawString(ele, p.x + 2, p.y + 5);
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
