package hhtznr.josm.plugins.elevation.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

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
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.SRTMFileReadListener;
import hhtznr.josm.plugins.elevation.SRTMFileReader;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

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
    private boolean showElevationRaster = false;
    List<LatLonLine> contourLineSegments = null;

    public ElevationLayer() {
        super("Elevation Contour Line Layer");
        SRTMFileReader.getInstance().addFileReadListener(this);
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        if (mv == null) {
            bounds = null;
            srtmTileGrid = null;
            contourLineSegments = null;
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
                if (!bbox.equals(bounds)) {
                    srtmTileGrid = new SRTMTileGrid(bbox);
                    contourLineSegments = null;
                    bounds = bbox;
                }
                if (srtmTileGrid != null) {
                    drawSRTMIsolines(g, mv);
                    if (showElevationRaster)
                        drawSRTMRaster(g, mv);
                }
            }
        } finally {
            // Restore previous graphics configuration
            g.setColor(previousColor);
            g.setFont(previousFont);
            g.setStroke(previousStroke);
        }
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

    private static class ShowElevationRasterAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private ElevationLayer layer;

        public ShowElevationRasterAction(ElevationLayer layer) {
            this.layer = layer;
            putValue(NAME, "Enable/disable elevation raster");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            layer.showElevationRaster = !layer.showElevationRaster;
            if (layer.showElevationRaster)
                Logging.info("Elevation: Elevation raster points enabled");
            else
                Logging.info("Elevation: Elevation raster points disabled");
            layer.repaint();
        }
    }
}
