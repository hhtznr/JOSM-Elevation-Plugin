package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapStatus;
import org.openstreetmap.josm.gui.widgets.ImageLabel;
import org.openstreetmap.josm.tools.GBC;

/**
 * An image label for the status line of a map frame, which is intended to
 * display the elevation at the mouse pointer location on the map.
 */
public class LocalElevationLabel extends ImageLabel {

    public LocalElevationLabel(MapFrame mapFrame) {
        super("ele", "The terrain elevation at the mouse pointer.", 10, MapStatus.PROP_BACKGROUND_COLOR.get());

        setForeground(MapStatus.PROP_FOREGROUND_COLOR.get());
        setText("No data");
        addToMapFrame(mapFrame);
    }

	/**
	 * Adds this local elevation label to the status line of a map frame.
	 *
	 * @param mapFrame The JOSM map frame where this local elevation label should
	 *                 be added to the status line.
	 */
    public void addToMapFrame(MapFrame mapFrame) {
        if (mapFrame == null)
            return;
        // Add after the longitude ImageLabel at index = 2
        // or alternatively at index 0 or 1, if index = 2 should be out of range
        int index = Math.min(mapFrame.statusLine.getComponentCount(), 2);
        mapFrame.statusLine.add(this, GBC.std().insets(3, 0, 0, 0), index);
    }
}
