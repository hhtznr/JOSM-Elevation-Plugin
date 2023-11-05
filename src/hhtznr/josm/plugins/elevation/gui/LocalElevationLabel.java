package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.widgets.ImageLabel;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.SRTMFileReader;
import hhtznr.josm.plugins.elevation.SRTMTile;

/**
 * An image label for the status line of a map frame, which is intended to
 * display the elevation at the mouse pointer location on the map.
 *
 * @author Harald Hetzner
 */
public class LocalElevationLabel extends ImageLabel implements MouseMotionListener, ZoomChangeListener {

    private final DecimalFormat ELEVATION_FORMAT = new DecimalFormat("0 m");

    private MapFrame mapFrame = null;

    private boolean elevationZoomLevelEnabled = false;

    private Timer timer = new Timer();
    private TimerTask pendingTimerTask = null;

    public LocalElevationLabel(MapFrame mapFrame) {
        super("ele", "The terrain elevation at the mouse pointer.", 10, MapStatus.PROP_BACKGROUND_COLOR.get());

        setForeground(MapStatus.PROP_FOREGROUND_COLOR.get());
        addToMapFrame(mapFrame);
    }

    /**
     * Adds this local elevation label to the status line of a map frame.
     *
     * @param mapFrame The JOSM map frame where this local elevation label should be
     *                 added to the status line.
     */
    public void addToMapFrame(MapFrame mapFrame) {
        if (mapFrame == null)
            return;
        if (this.mapFrame != null)
            remove();
        mapFrame.mapView.addMouseMotionListener(this);
        MapView.addZoomChangeListener(this);
        // Add after the longitude ImageLabel at index = 2
        // or alternatively at index 0 or 1, if index = 2 should be out of range
        int index = Math.min(mapFrame.statusLine.getComponentCount(), 2);
        mapFrame.statusLine.add(this, GBC.std().insets(3, 0, 0, 0), index);
        this.mapFrame = mapFrame;
        zoomChanged();
    }

    /**
     * Removes this local elevation label from the map frame.
     */
    public void remove() {
        mapFrame.mapView.removeMouseMotionListener(this);
        mapFrame.statusLine.remove(this);
        mapFrame = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (mapFrame.mapView.getCenter() == null)
            return;
        // Do not update the view if ctrl or right button is pressed.
        if ((e.getModifiersEx() & (MouseEvent.CTRL_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK)) == 0)
            updateEleText(mapFrame.mapView.getLatLon(e.getX(), e.getY()));
    }

    private void updateEleText(ILatLon latLon) {
        if (!elevationZoomLevelEnabled || latLon == null) {
            setText("");
            return;
        }
        short elevation = SRTMFileReader.getInstance().getElevation(latLon);
        if (elevation != SRTMTile.SRTM_DATA_VOID)
            setText(ELEVATION_FORMAT.format(elevation));
        else
            setText("No data");
    }

    @Override
    public void zoomChanged() {
        // Determine the projection bounds of the map view
        ProjectionBounds projectionBounds = mapFrame.mapView.getProjectionBounds();
        LatLon minLatLon = mapFrame.mapView.getProjection().eastNorth2latlon(projectionBounds.getMin());
        LatLon maxLatLon = mapFrame.mapView.getProjection().eastNorth2latlon(projectionBounds.getMax());
        double latSouth = minLatLon.lat();
        double latNorth = maxLatLon.lat();
        double lonWest = minLatLon.lon();
        double lonEast = maxLatLon.lon();
        double latRange = latNorth - latSouth;
        double lonRange;
        // Normal case: Bound do not cross the Prime Meridian
        if (lonWest < lonEast)
            lonRange = lonEast - lonWest;
        // Special case: Bounds cross the Prime Meridian
        else
            lonRange = 180 - lonWest + 180 + lonEast;

        LatLon southWest = new LatLon(latSouth, lonWest);
        LatLon northEast = new LatLon(latNorth, lonEast);
        elevationZoomLevelEnabled = latRange <= SRTMTile.SRTM_TILE_ARC_DEGREES
                && lonRange <= SRTMTile.SRTM_TILE_ARC_DEGREES;
        updateEleText(null);

        if (elevationZoomLevelEnabled) {
            if (pendingTimerTask != null)
                pendingTimerTask.cancel();
            timer.purge();
            pendingTimerTask = new CacheSRTMTilesInMapBounds(southWest, northEast);
            // Ensure caching of SRTM tiles for current map bounds after 5 s
            timer.schedule(pendingTimerTask, 5000L);
        }
    }

    private class CacheSRTMTilesInMapBounds extends TimerTask {

        private ILatLon southWest;
        private ILatLon northEast;

        CacheSRTMTilesInMapBounds(ILatLon southWest, ILatLon northEast) {
            this.southWest = southWest;
            this.northEast = northEast;
        }

        @Override
        public void run() {
            SRTMFileReader.getInstance().cacheSRTMTiles(southWest, northEast);
        }
    }
}
