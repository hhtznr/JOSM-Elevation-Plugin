package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.widgets.ImageLabel;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * An image label for the status line of a map frame, which is intended to
 * display the elevation at the mouse pointer location on the map.
 *
 * @author Harald Hetzner
 */
public class LocalElevationLabel extends ImageLabel implements MouseMotionListener, ZoomChangeListener {

    private static final long serialVersionUID = 1L;

    private final DecimalFormat ELEVATION_FORMAT_NO_INTERPOLATION = new DecimalFormat("0 m");

    private final DecimalFormat ELEVATION_FORMAT_INTERPOLATION = new DecimalFormat("0.0 m");

    private final ElevationDataProvider elevationDataProvider;

    private MapFrame mapFrame = null;

    private boolean elevationZoomLevelEnabled = false;

    private final Timer timer = new Timer();
    private TimerTask pendingTimerTask = null;

    /**
     * Creates a new map status label which displays the terrain elevation at the
     * location of the mouse pointer.
     *
     * @param mapFrame              The map frame, where the label is to be located
     *                              in the status bar.
     * @param elevationDataProvider The elevation data provider providing the data
     *                              to update this label.
     */
    public LocalElevationLabel(MapFrame mapFrame, ElevationDataProvider elevationDataProvider) {
        super("ele", "Terrain elevation at the mouse pointer.", 10, MapStatus.PROP_BACKGROUND_COLOR.get());
        this.elevationDataProvider = elevationDataProvider;
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
        LatLonEle latLonEle = elevationDataProvider.getLatLonEle(latLon);
        DecimalFormat eleFormat;
        if (elevationDataProvider.getElevationInterpolation() == SRTMTile.Interpolation.NONE)
            eleFormat = ELEVATION_FORMAT_NO_INTERPOLATION;
        else
            eleFormat = ELEVATION_FORMAT_INTERPOLATION;
        if (latLonEle.hasValidEle())
            setText(eleFormat.format(latLonEle.ele()));
        else
            setText("No data");
    }

    @Override
    public void zoomChanged() {
        updateEleText(null);

        final Bounds bounds = mapFrame.mapView.getLatLonBounds(mapFrame.mapView.getBounds());
        elevationZoomLevelEnabled = bounds.getHeight() <= SRTMTile.SRTM_TILE_ARC_DEGREES
                && bounds.getWidth() <= SRTMTile.SRTM_TILE_ARC_DEGREES;
        if (elevationZoomLevelEnabled) {
            synchronized (timer) {
                if (pendingTimerTask != null)
                    pendingTimerTask.cancel();
                timer.purge();
                pendingTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        double latSouth = bounds.getMinLat();
                        double latNorth = bounds.getMaxLat();
                        // minLon does always seem to be west, see Bounds.crosses180thMeridian()
                        double lonWest = bounds.getMinLon();
                        // maxLon does always seem to be east, see Bounds.crosses180thMeridian()
                        double lonEast = bounds.getMaxLon();
                        LatLon southWest = new LatLon(latSouth, lonWest);
                        LatLon northEast = new LatLon(latNorth, lonEast);
                        elevationDataProvider.cacheSRTMTiles(southWest, northEast);
                        synchronized (timer) {
                            pendingTimerTask = null;
                        }
                    }
                };
                // Ensure caching of SRTM tiles for current map bounds after 5 s
                timer.schedule(pendingTimerTask, 5000L);
            }
        }
    }
}
