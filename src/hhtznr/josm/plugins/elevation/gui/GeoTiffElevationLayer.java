package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.Coloring;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationGrid;
import hhtznr.josm.plugins.elevation.data.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.GeoTiffImageException;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.gui.ContourLines.IsolineSegments;

/**
 * This class implements a JOSM layer for displaying GeoTIFF images. It converts
 * the image to grayscale and handles the rendering of the image on the map
 * view.
 *
 * @author Harald Hetzner
 */
public class GeoTiffElevationLayer extends ElevationLayer {

    private final GeoTiffImage geoTiffImage;
    private GeoTiffElevationGrid elevationGrid = null;
    private final BufferedImage grayscaleImage;
    private GeoTiffContourLines contourLines = null;
    private double[] lowestAndHighestElevation = null;

    private int contourLineIsostep = ElevationPreferences.getContourLineIsostep();
    private short lowerCutoffElevation = ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION;
    private short upperCutoffElevation = ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION;

    private boolean grayscaleImageEnabled = true;

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
        super(name, ImageProvider.get("", "elevation.svg"), contourLineIsostep, contourLineStrokeWidth,
                contourLineColoringScheme, contourLineConstantColor, lowerCutoffElevation, upperCutoffElevation);

        this.geoTiffImage = geoTiffImage;
        this.grayscaleImage = convertToGrayscale(geoTiffImage.getRawImage(), geoTiffImage.getNoDataValues());
    }

    private BufferedImage convertToGrayscale(RenderedImage img, double[] noDataValues) {
        if (img.getSampleModel().getNumBands() == 1) {
            return convertSingleBandToGrayscale(img, noDataValues);
        }

        BufferedImage bi;
        if (img instanceof BufferedImage) {
            bi = (BufferedImage) img;
        } else {
            bi = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawRenderedImage(img, new AffineTransform());
            g.dispose();
        }

        // Apply a grayscale filter matrix operation
        BufferedImage gray = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        op.filter(bi, gray);
        return gray;
    }

    private BufferedImage convertSingleBandToGrayscale(RenderedImage image, double[] noDataValues) {
        Raster raster = image.getData();
        int minX = image.getMinX();
        int minY = image.getMinY();
        int width = image.getWidth();
        int height = image.getHeight();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                if (isValidSample(value, noDataValues)) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
        }

        if (min == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("Elevation: GeoTIFF raster of file '"
                    + geoTiffImage.getFile().getAbsolutePath() + "' contains no valid single-band samples");
        }

        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = minY; y < minY + height; y++) {
            for (int x = minX; x < minX + width; x++) {
                double value = raster.getSampleDouble(x, y, 0);
                int intensity = 0;
                if (isValidSample(value, noDataValues)) {
                    intensity = (min == max) ? 127
                            : (int) Math.max(0, Math.min(255, Math.round((value - min) * 255 / (max - min))));
                }
                gray.getRaster().setSample(x - minX, y - minY, 0, intensity);
            }
        }
        return gray;
    }

    private boolean isValidSample(double value, double[] noDataValues) {
        if (!Double.isFinite(value)) {
            return false;
        }
        if (noDataValues != null) {
            for (double noDataValue : noDataValues) {
                if (Double.isNaN(noDataValue) ? Double.isNaN(value) : value == noDataValue) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void paint(Graphics2D g, MapView mapView, Bounds mapBounds) {
        ReferencedEnvelope geoBounds = geoTiffImage.getBounds();
        LatLon northWest = new LatLon(geoBounds.getMaxY(), geoBounds.getMinX());
        LatLon southEast = new LatLon(geoBounds.getMinY(), geoBounds.getMaxX());

        Point topLeftScreen = mapView.getPoint(northWest);
        Point bottomRightScreen = mapView.getPoint(southEast);

        int width = bottomRightScreen.x - topLeftScreen.x;
        int height = bottomRightScreen.y - topLeftScreen.y;
        if (width > 0 && height > 0) {
            if (grayscaleImageEnabled) {
                g.drawImage(grayscaleImage, topLeftScreen.x, topLeftScreen.y, width, height, null);
            }
            if (isContourLinesEnabled()) {
                drawContourLines(g, mapView, mapBounds);
            }
        }
    }

    private synchronized void drawContourLines(Graphics2D g, MapView mv, Bounds clipBounds) {
        float layerContourLineStrokeWidth = getContourLineStrokeWidth();
        BasicStroke contourLineStroke = new BasicStroke(layerContourLineStrokeWidth, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND);

        Coloring.Scheme layerContourLineColoringScheme = getContourLineColoringScheme();
        Color layerContourLineConstantColor = getContourLineConstantColor();

        int layerContourLineIsostep = getContourLineIsostep();
        short layerLowerCutoffElevation = getLowerCutoffElevation();
        short layerUpperCutoffElevation = getUpperCutoffElevation();
        if (contourLines == null || contourLineIsostep != layerContourLineIsostep
                || lowerCutoffElevation != layerLowerCutoffElevation
                || upperCutoffElevation != layerUpperCutoffElevation) {
            contourLineIsostep = layerContourLineIsostep;
            lowerCutoffElevation = layerLowerCutoffElevation;
            upperCutoffElevation = layerUpperCutoffElevation;

            if (elevationGrid == null) {
                try {
                    elevationGrid = new GeoTiffElevationGrid(geoTiffImage);
                } catch (GeoTiffImageException e) {
                    Logging.error("Elevation: Cannot create contour lines for GeoTIFF file '"
                            + geoTiffImage.getFile().getAbsolutePath() + "': " + e.toString());
                }
            }

            try {
                contourLines = new GeoTiffContourLines(elevationGrid, contourLineIsostep, lowerCutoffElevation,
                        upperCutoffElevation);
            } catch (AsyncOperationException e) {
                Logging.error("Elevation: Cannot create contour lines for GeoTIFF " + geoTiffImage.getFile().getName());
            }

            if (contourLines == null) {
                return;
            }
        }
        IsolineSegments[] isolineSegments = contourLines.getIsolineSegments();
        if (isolineSegments.length < 1) {
            return;
        }

        Rectangle screenClip = g.getClipBounds();

        g.setColor(layerContourLineConstantColor);
        g.setStroke(contourLineStroke);

        short minEle = lowerCutoffElevation;
        short maxEle = upperCutoffElevation;
        boolean useFalseColor = false;
        if (layerContourLineColoringScheme == Coloring.Scheme.FALSE_COLOR) {
            if (lowestAndHighestElevation == null) {
                lowestAndHighestElevation = elevationGrid.getLowestAndHighestElevation();
            }

            double lowestElevation = lowestAndHighestElevation[0];
            double highestElevation = lowestAndHighestElevation[1];
            if (!Double.isNaN(lowestElevation) && !Double.isNaN(highestElevation)) {
                if (lowestElevation != SRTMTile.SRTM_DATA_VOID && highestElevation != SRTMTile.SRTM_DATA_VOID
                        && lowestElevation != highestElevation) {
                    minEle = (short) Math.max(minEle, lowestElevation);
                    maxEle = (short) Math.min(maxEle, highestElevation);
                    useFalseColor = true;
                }
            }
        }
        for (ContourLines.IsolineSegments isolineSegment : isolineSegments) {
            if (useFalseColor) {
                short ele = isolineSegment.getIsoValue();
                g.setColor(Coloring.getRainbowColor(ele, minEle, maxEle));
            }
            for (LatLonLine segment : isolineSegment.getLineSegments()) {
                Point p1 = mv.getPoint(segment.getLatLon1());
                Point p2 = mv.getPoint(segment.getLatLon2());
                // Only draw if the line's bounding box intersects the map's bounding box
                if (screenClip.intersectsLine(p1.x, p1.y, p2.x, p2.y)) {
                    g.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }
    }

    @Override
    public Object getInfoComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(new JLabel(getName()), GBC.eol());
        List<String[]> content = new ArrayList<>();
        // Absolute path of the GeoTIFF file
        content.add(new String[] { tr("File path"), "" + geoTiffImage.getFile().getAbsolutePath() });
        for (String[] entry : content) {
            panel.add(new JLabel(entry[0] + ':'), GBC.std());
            panel.add(GBC.glue(5, 0), GBC.std());
            panel.add(UIFactory.createLayerInfoTextField(entry[1]), GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        }
        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scrollPane;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
        ReferencedEnvelope geoBounds = geoTiffImage.getBounds();
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
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[0]);
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
            // TODO: Handle via preferences?
            // ElevationPreferences.setContourLinesEnabled(layer.contourLinesEnabled);
            if (layer.grayscaleImageEnabled) {
                Logging.info("Elevation: GeoTIFF grayscale image enabled for layer " + getName());
            } else {
                Logging.info("Elevation: GeoTIFF grayscale image disabled for layer " + getName());
            }
            layer.invalidate();
            if (layer.isVisible()) {
                MainApplication.getMap().mapView.repaint();
            }
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(GeoTiffElevationLayer.this.grayscaleImageEnabled);
            return item;
        }
    }
}
