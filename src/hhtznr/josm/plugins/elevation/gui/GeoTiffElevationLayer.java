package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
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
import java.util.concurrent.Future;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.Coloring;
import hhtznr.josm.plugins.elevation.data.ElevationGridException;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationGrid;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationGridView;
import hhtznr.josm.plugins.elevation.data.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.GeoTiffImageException;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.gui.ContourLines.IsolineSegments;
import hhtznr.josm.plugins.elevation.util.DrawUtils;
import hhtznr.josm.plugins.elevation.util.DrawUtils.NonOverlappingTextRenderer;

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
    private GeoTiffElevationGridView elevationGridView = null;
    private BufferedImage grayscaleImage = null;
    private GeoTiffContourLines contourLines = null;

    // For storing of previous values
    private int contourLineIsostep;
    private short lowerCutoffElevation;
    private short upperCutoffElevation;

    private boolean grayscaleImageEnabled = true;
    private Future<?> elevationGridFuture = null;
    private Future<?> grayscaleImageFuture = null;
    private Future<?> contourLinesFuture = null;

    private Bounds previousMapBounds;

    private GeoTiffElevationLayerAdjustmentDialog layerAdjustmentDialog = null;

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
        super(name, ImageProvider.get("", "elevation.svg"), contourLineIsostep, contourLineStrokeWidth,
                contourLineColoringScheme, contourLineConstantColor, lowerCutoffElevation, upperCutoffElevation);

        this.geoTiffImage = geoTiffImage;
        this.contourLineIsostep = contourLineIsostep;
        this.lowerCutoffElevation = lowerCutoffElevation;
        this.upperCutoffElevation = upperCutoffElevation;

        elevationGridFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
            if (Thread.currentThread().isInterrupted()) {
                elevationGridFuture = null;
                return;
            }
            try {
                elevationGrid = new GeoTiffElevationGrid(geoTiffImage);
            } catch (GeoTiffImageException e) {
                Logging.error("Elevation: Cannot create contour lines for GeoTIFF file '"
                        + geoTiffImage.getFile().getAbsolutePath() + "': " + e.toString());
            } finally {
                elevationGridFuture = null;
                triggerRepaint();
            }
        });
    }

    private void convertGeoTiffToGrayscale() {
        RenderedImage img = geoTiffImage.getRawImage();
        double[] noDataValues = geoTiffImage.getNoDataValues();
        if (img.getSampleModel().getNumBands() == 1) {
            grayscaleImage = convertSingleBandToGrayscale(img, noDataValues);
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
        grayscaleImage = gray;
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
                Logging.warn("Elevation: Could not create map view for GeoTIFF " + geoTiffImage.getFile().getName()
                        + ": " + e.toString());
                elevationGridView = null;
                return;
            }
            Future<?> future = contourLinesFuture;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            contourLines = null;
            previousMapBounds = mapBounds;
        }

        if (grayscaleImageEnabled) {
            drawGrayscaleImage(g, mapView);
            drawGridBounds(g, mapView);
        }

        if (elevationGridView == null) {
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
                    convertGeoTiffToGrayscale();
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
        if (currentContourLines == null || contourLineIsostep != newContourLineIsostep
                || lowerCutoffElevation != newLowerCutoffElevation || upperCutoffElevation != newUpperCutoffElevation) {
            Future<?> future = contourLinesFuture;
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            contourLineIsostep = newContourLineIsostep;
            lowerCutoffElevation = newLowerCutoffElevation;
            upperCutoffElevation = newUpperCutoffElevation;

            triggerContourLineComputation(newContourLineIsostep, newLowerCutoffElevation, newUpperCutoffElevation);
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

    private void triggerContourLineComputation(int contourLineIsostep, short lowerCutoffElevation,
            short upperCutoffElevation) {
        if (elevationGridView == null) {
            return;
        }
        contourLinesFuture = ElevationPlugin.getParallelExecutor().submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                contourLines = new GeoTiffContourLines(elevationGridView, contourLineIsostep, lowerCutoffElevation,
                        upperCutoffElevation);
            } catch (AsyncOperationException e) {
                Logging.error("Elevation: Cannot create contour lines for GeoTIFF " + geoTiffImage.getFile().getName());
            } finally {
                contourLinesFuture = null;
                triggerRepaint();
            }
        });
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
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[0]);
    }

    @Override
    public synchronized void destroy() {
        // Cancel any running background tasks of this layer
        for (Future<?> future : new Future<?>[] { elevationGridFuture, elevationGridFuture, grayscaleImageFuture,
                contourLinesFuture }) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
        contourLines = null;
        grayscaleImage = null;
        elevationGridView = null;
        elevationGrid = null;

        super.destroy();
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

    private class ShowLayerAdjustmentDialogAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public ShowLayerAdjustmentDialogAction() {
            super("Dynamic elevation layer adjustment", new ImageProvider("elevation.svg"),
                    "Dynamic, non-presistent adjustment of elevation rendering", null, true, "elevation-layer-settings",
                    false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (layerAdjustmentDialog == null) {
                layerAdjustmentDialog = new GeoTiffElevationLayerAdjustmentDialog(MainApplication.getMainFrame(),
                        GeoTiffElevationLayer.this);
            }
            layerAdjustmentDialog.showDialog();
        }
    }
}
