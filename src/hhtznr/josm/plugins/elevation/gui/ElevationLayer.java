package hhtznr.josm.plugins.elevation.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;
import hhtznr.josm.plugins.elevation.io.SRTMFileReadListener;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;
import hhtznr.josm.plugins.elevation.math.Hillshade;

public class ElevationLayer extends Layer implements SRTMFileReadListener {

    /**
     * The maximum value of arc degrees in latitude or longitude of the map view
     * where the elevation layer will display contour lines.
     */
    public static final double ELEVATION_LAYER_MAX_ARC_DEGREES = 0.1;

    /**
     * The color in which the contour lines are painted on the map.
     */
    public static final Color CONTOUR_LINE_COLOR = new Color(210, 180, 115);

    private Bounds bounds = null;
    SRTMTileGrid srtmTileGrid = null;
    SRTMTileGrid hillshadeTileGrid = null;
    private boolean drawContourLines = true;
    private boolean drawHillshade = true;
    private boolean drawElevationRaster = false;
    BufferedImage hillshadeImage = null;
    private LatLon hillshadeNorthWest = null;
    List<LatLonLine> contourLineSegments = null;

    public ElevationLayer() {
        super("Elevation Layer");
        SRTMFileReader.getInstance().addFileReadListener(this);
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        if (mv == null) {
            bounds = null;
            srtmTileGrid = null;
            contourLineSegments = null;
            hillshadeTileGrid = null;
            hillshadeImage = null;
            return;
        }

        // Disable drawing of contour lines if the map is zoomed out too much
        // This prevents high CPU and I/O usage
        boolean zoomLevelDisabled = bbox.getMaxLat() - bbox.getMinLat() > ELEVATION_LAYER_MAX_ARC_DEGREES
                || bbox.getMaxLon() - bbox.getMinLon() > ELEVATION_LAYER_MAX_ARC_DEGREES;

        Color previousColor = g.getColor();
        Font previousFont = g.getFont();
        Stroke previousStroke = g.getStroke();
        try {
            if (zoomLevelDisabled) {
                g.setColor(Color.RED);
                g.setFont(g.getFont().deriveFont(Font.BOLD, 16));
                g.drawString("Contour lines disabled for this zoom level", 10, mv.getHeight() - 10);
            } else {
                // If the bounds changed since the last paint operation
                if (!bbox.equals(bounds)) {
                    // Create a new SRTM tile grid for the map bounds if no grid was created or an
                    // existing grid does not cover the current bounds
                    if ((drawContourLines || drawElevationRaster)
                            && (srtmTileGrid == null || !srtmTileGrid.covers(bbox))) {
                        srtmTileGrid = new SRTMTileGrid(bbox);
                        contourLineSegments = null;
                    }
                    // Create a new SRTM tile grid for hillshade use
                    if (drawHillshade) {
                        hillshadeTileGrid = new SRTMTileGrid(bbox);
                        hillshadeImage = null;
                    }
                }
                if (drawHillshade && hillshadeTileGrid != null)
                    drawHillshade(g, mv);

                if (drawContourLines && srtmTileGrid != null)
                    drawSRTMIsolines(g, mv);

                if (drawElevationRaster && srtmTileGrid != null)
                    drawSRTMRaster(g, mv);

                bounds = bbox;
            }
        } finally {
            // Restore previous graphics configuration
            g.setColor(previousColor);
            g.setFont(previousFont);
            g.setStroke(previousStroke);
        }
    }

    private void drawHillshade(Graphics2D g, MapView mv) {
        Point upperLeft = null;
        if (hillshadeImage == null) {
            //System.out.println("Hillshade image is null");
            Hillshade.ImageTile hillshadeTile = hillshadeTileGrid.getHillshadeImage(false, Hillshade.DEFAULT_ALTITUDE_DEG,
                    Hillshade.DEFAULT_AZIMUTH_DEG);
            if (hillshadeTile == null)
                return;
            // The dimensions of the unscaled hillshade image
            int imageWidth = hillshadeTile.getImage().getWidth();
            int imageHeight = hillshadeTile.getImage().getHeight();
            //System.out.println("Hillshade image size: w = " + imageWidth + ", h = " + imageHeight);
            if (imageWidth <= 0 || imageHeight <= 0) {
                Logging.error("Elevation: Hillshade size too small: width = " + imageWidth + ", height = " + imageHeight);
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
            //System.out.println("Hillshade image scale factors: sx = " + sx + ", sy = " + sy);
            AffineTransform at = new AffineTransform();
            at.scale(sx, sy);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            hillshadeImage = scaleOp.filter(hillshadeTile.getImage(), null);
            //System.out.println("Hillshade image scaled size: width = " + hillshadeImage.getWidth() + ", height = " + hillshadeImage.getHeight());
            //System.out.println("Map size: width = " + mv.getWidth() + ", height = " + mv.getHeight());
        }
        if (upperLeft == null)
            upperLeft = mv.getPoint(hillshadeNorthWest);
        //System.out.println("Hillshade upper right corner (px): x = " + upperLeft.x + ", x = " + upperLeft.y);
        //System.out.println("Hillshade image scaled cropped size: width = " + (hillshadeImage.getWidth() + upperLeft.x) + ", height = " + (hillshadeImage.getHeight() + upperLeft.y));
        g.drawImage(hillshadeImage, upperLeft.x, upperLeft.y, hillshadeImage.getWidth(), hillshadeImage.getHeight(), null);
    }

    private void drawSRTMIsolines(Graphics2D g, MapView mv) {
        g.setColor(CONTOUR_LINE_COLOR);
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (contourLineSegments == null)
            contourLineSegments = srtmTileGrid.getIsolineSegments();
        if (contourLineSegments == null)
            return;
        for (LatLonLine segment : contourLineSegments) {
            Point p1 = mv.getPoint(segment.getLatLon1());
            Point p2 = mv.getPoint(segment.getLatLon2());
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
        }
    }

    private void drawSRTMRaster(Graphics2D g, MapView mv) {
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
        SRTMFileReader.getInstance().removeFileReadListener(this);
        super.destroy();
    }

    @Override
    public void srtmTileRead(SRTMTile tile) {
        if (isVisible())
            repaint();
    }

    private void repaint() {
        MapFrame map = MainApplication.getMap();
        if (map != null)
            map.repaint();
    }

    private static class ShowContourLinesAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private ElevationLayer layer;

        public ShowContourLinesAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable contour lines");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.drawContourLines = !layer.drawContourLines;
            if (layer.drawContourLines)
                Logging.info("Elevation: Contour lines enabled");
            else
                Logging.info("Elevation: Contour lines disabled");
            layer.repaint();
        }
    }

    private static class ShowHillshadeAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private ElevationLayer layer;

        public ShowHillshadeAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable hillshade");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.drawHillshade = !layer.drawHillshade;
            if (layer.drawHillshade)
                Logging.info("Elevation: Hillshade enabled");
            else
                Logging.info("Elevation: Hillshade disabled");
            layer.repaint();
        }
    }

    private static class ShowElevationRasterAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private ElevationLayer layer;

        public ShowElevationRasterAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable elevation raster");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.drawElevationRaster = !layer.drawElevationRaster;
            if (layer.drawElevationRaster)
                Logging.info("Elevation: Elevation raster points enabled");
            else
                Logging.info("Elevation: Elevation raster points disabled");
            layer.repaint();
        }
    }
}
