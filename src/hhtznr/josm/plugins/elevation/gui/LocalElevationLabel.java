package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapStatus;
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
public class LocalElevationLabel extends ImageLabel implements MouseMotionListener {

    private final DecimalFormat ELEVATION_FORMAT = new DecimalFormat("0 m");

    private MapFrame mapFrame;

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
        setText("");
        mapFrame.mapView.addMouseMotionListener(this);
        // Add after the longitude ImageLabel at index = 2
        // or alternatively at index 0 or 1, if index = 2 should be out of range
        int index = Math.min(mapFrame.statusLine.getComponentCount(), 2);
        mapFrame.statusLine.add(this, GBC.std().insets(3, 0, 0, 0), index);
        this.mapFrame = mapFrame;
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
        short elevation = SRTMFileReader.getInstance().getElevation(latLon);
        if (elevation != SRTMTile.SRTM_DATA_VOID)
            setText(ELEVATION_FORMAT.format(elevation));
        else
            setText("No data");
    }
}
