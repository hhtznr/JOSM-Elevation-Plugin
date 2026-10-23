package hhtznr.josm.plugins.elevation.gui.layers;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Action;
import javax.swing.Icon;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.GeoTiffElevationDataProviderListener;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffImage;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffIndex;
import hhtznr.josm.plugins.elevation.data.geotiff.GeoTiffRecord;
import hhtznr.josm.plugins.elevation.gui.actions.GeoTiffSyncIndexFromDiskAction;
import hhtznr.josm.plugins.elevation.util.DrawUtils.NonOverlappingTextRenderer;
import hhtznr.josm.plugins.elevation.util.FileUtils;

/**
 * This class implements a JOSM map layer which indicates the position of all
 * GeoTIFF images within a specified directory by drawing the envelopes (bounds)
 * and names of the GeoTIFF files at the appropriate locations onto the map. If
 * activated, the layer recognizes and highlights a GeoTIFF envelope if the
 * mouse moves over it. By performing a double-click within the highlighted
 * envelope, the corresponding GeoTIFF data is immediately loaded to a new
 * {@link GeoTiffElevationLayer}. If the layer already exists, but is currently
 * hidden, it is set visible.
 *
 * @author Harald Hetzner
 */
public class GeoTiffEnvelopeLayer extends Layer implements GeoTiffElevationDataProviderListener {

    private static final Color ENVELOPE_COLOR = Color.BLUE;
    private static final Color HOVERED_ENVELOPE_COLOR = Color.RED;

    private final ElevationDataProvider elevationDataProvider;
    private final GeoTiffIndex index;
    private AtomicBoolean geoTiffIndexUpdated = new AtomicBoolean(false);

    private Bounds previousMapBounds = null;
    private List<GeoTiffRecord> geoTiffRecords = null;
    // The GeoTiff record, the mouse pointer is currently hovering over
    private GeoTiffRecord hoveredGeoTiffRecord = null;
    private final Object geoTiffRecordsLock = new Object();

    /**
     * Creates a new GeoTIFF envelope layer which indicates the position of
     * available GeoTIFF images on the map.
     *
     * @param elevationDataProvider The elevation data provider, which provides
     *                              access to the {@link GeoTiffIndex} for finding
     *                              GeoTIFF images on the map view.
     */
    public GeoTiffEnvelopeLayer(ElevationDataProvider elevationDataProvider) {
        super("GeoTIFF Overview Layer");
        this.elevationDataProvider = elevationDataProvider;
        this.elevationDataProvider.addGeoTiffElevationDataProviderListener(this);
        this.index = this.elevationDataProvider.getGeoTiffIndex();
    }

    @Override
    public void paint(Graphics2D g, MapView mapView, Bounds mapBounds) {
        if (previousMapBounds == null || !previousMapBounds.equals(mapBounds)
                || geoTiffIndexUpdated.compareAndSet(true, false)) {

            List<GeoTiffRecord> records = index.query(mapBounds);
            synchronized (geoTiffRecordsLock) {
                geoTiffRecords = records;
            }
            previousMapBounds = mapBounds;
        }
        synchronized (geoTiffRecordsLock) {
            if (geoTiffRecords != null) {
                NonOverlappingTextRenderer textRenderer = new NonOverlappingTextRenderer();
                for (GeoTiffRecord record : geoTiffRecords) {
                    Color envelopeColor = ENVELOPE_COLOR;
                    Layer activeLayer = MainApplication.getLayerManager().getActiveLayer();
                    if (this.equals(activeLayer) && hoveredGeoTiffRecord != null
                            && hoveredGeoTiffRecord.equals(record)) {
                        envelopeColor = HOVERED_ENVELOPE_COLOR;
                    }
                    drawEnvelope(g, mapView, record, envelopeColor, textRenderer);
                }
            }
        }
    }

    private void drawEnvelope(Graphics2D g, MapView mapView, GeoTiffRecord geoTiffRecord, Color envelopeColor,
            NonOverlappingTextRenderer textRenderer) {
        ReferencedEnvelope envelope = geoTiffRecord.getReferencedEnvelope();

        Point northWest = mapView.getPoint(new LatLon(envelope.getMaxY(), envelope.getMinX()));
        Point northEast = mapView.getPoint(new LatLon(envelope.getMaxY(), envelope.getMaxX()));
        Point southEast = mapView.getPoint(new LatLon(envelope.getMinY(), envelope.getMaxX()));
        Point southWest = mapView.getPoint(new LatLon(envelope.getMinY(), envelope.getMinX()));

        Path2D outline = new Path2D.Double();
        outline.moveTo(northWest.x, northWest.y);
        outline.lineTo(northEast.x, northEast.y);
        outline.lineTo(southEast.x, southEast.y);
        outline.lineTo(southWest.x, southWest.y);
        outline.closePath();

        Rectangle outlineBounds = outline.getBounds();
        int x = outlineBounds.x;
        int y = outlineBounds.y;

        String name = FileUtils.getFileNameWithoutExtension(geoTiffRecord.getAbsolutePath(), ".tif", ",tiff");
        int fontSize = 12;
        Font font = g.getFont().deriveFont(Font.BOLD, fontSize);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw stroke
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Color.WHITE);
        g.draw(outline);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(envelopeColor);
        g.draw(outline);

        // Draw text
        g.setFont(font);
        textRenderer.drawTextSafely(g, x + 4, y + fontSize / 2 + 2, envelopeColor, Color.WHITE, name);
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.get("layers", "elevation_geotiff.svg");
    }

    @Override
    public Object getInfoComponent() {
        return null;
    }

    @Override
    public Action[] getMenuEntries() {
        Action[] actions = new Action[4];
        actions[0] = LayerListDialog.getInstance().createActivateLayerAction(this);
        actions[1] = LayerListDialog.getInstance().createShowHideLayerAction();
        actions[2] = SeparatorLayerAction.INSTANCE;
        actions[3] = new GeoTiffSyncIndexFromDiskAction(elevationDataProvider.getGeoTiffIndex());
        return actions;
    }

    @Override
    public String getToolTipText() {
        return null;
    }

    @Override
    public boolean isMergable(Layer layer) {
        return false;
    }

    @Override
    public void mergeFrom(Layer layer) {
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public synchronized void destroy() {
        elevationDataProvider.removeGeoTiffElevationDataProviderListener(this);
        super.destroy();
    }

    /**
     * Initiates a repaint of the layer by invalidating it and requesting a repaint
     * of the map view if the layer is currently visible. If it is not visible, it
     * will be painted in its current configuration as soon as it becomes visible.
     */
    public void triggerRepaint() {
        invalidate();
        if (isVisible()) {
            MainApplication.getMap().mapView.repaint();
        }
    }

    @Override
    public LayerPainter attachToMapView(MapViewEvent event) {
        MapView mapView = event.getMapView();
        LayerPainter delegate = super.attachToMapView(event);

        MouseAdapter mouseListener = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                LatLon latLon = mapView.getLatLon(x, y);
                synchronized (geoTiffRecordsLock) {
                    if (geoTiffRecords == null) {
                        return;
                    }
                    for (GeoTiffRecord record : geoTiffRecords) {
                        ReferencedEnvelope envelope = record.getReferencedEnvelope();
                        // This picks the first record on the list
                        // TODO: Improve to handle cases where multiple envelopes overlap
                        if (envelope.contains(latLon.lon(), latLon.lat())) {
                            // Record is still the hovered record
                            if (record.equals(hoveredGeoTiffRecord)) {
                                return;
                            }
                            hoveredGeoTiffRecord = record;
                            triggerRepaint();
                            return;
                        }
                    }
                    clearHoveredGeoTiffRecord();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearHoveredGeoTiffRecord();
            }

            private void clearHoveredGeoTiffRecord() {
                synchronized (geoTiffRecordsLock) {
                    if (hoveredGeoTiffRecord != null) {
                        hoveredGeoTiffRecord = null;
                        triggerRepaint();
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    GeoTiffRecord record = hoveredGeoTiffRecord;
                    if (record != null) {
                        Path path = record.getAbsolutePath();
                        String layerName = GeoTiffElevationLayer.deriveLayerName(path);
                        if (GeoTiffElevationLayer.geoTiffLayerAlreadyExists(layerName)) {
                            GeoTiffElevationLayer.setGeoTiffLayerVisible(layerName);
                            Logging.info("Elevation: Not adding GeoTIFF layer for '" + path.toAbsolutePath().toString()
                                    + "' because a GeoTiFFLayer with the same name already exists");
                            return;
                        }

                        GeoTiffImage geoTiffImage = GeoTiffImage.readGeoTiffFile(path);
                        Logging.info("Elevation: GeoTIFF file read: " + path.toAbsolutePath().toString());
                        if (geoTiffImage == null) {
                            Logging.error("Elevation: Failed to read GeoTIFF file '" + path.toAbsolutePath().toString()
                                    + "'");
                        }
                        GeoTiffElevationLayer.addGeoTiffElevationLayer(geoTiffImage);
                    }
                }
            }
        };

        mapView.addMouseMotionListener(mouseListener);
        mapView.addMouseListener(mouseListener);

        return new LayerPainter() {
            @Override
            public void paint(MapViewGraphics graphics) {
                delegate.paint(graphics);
            }

            @Override
            public void detachFromMapView(MapViewEvent detachEvent) {
                mapView.removeMouseMotionListener(mouseListener);
                mapView.removeMouseListener(mouseListener);
                delegate.detachFromMapView(detachEvent);
            }
        };
    }

    @Override
    public void geoTiffIndexUpdated() {
        geoTiffIndexUpdated.getAndSet(true);
        triggerRepaint();
    }
}
