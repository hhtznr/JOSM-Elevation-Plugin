package hhtznr.josm.plugins.elevation;

import java.awt.Cursor;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.gui.ElevationLayer;

/**
 * Map mode associated with the elevation layer.
 *
 * @author Harald Hetzner
 */
public class ElevationMapMode extends MapMode {

    private static final long serialVersionUID = 1L;

    private final ElevationLayer layer;

    /**
     * Creates a new elevation map mode.
     *
     * @param layer The elevation layer of this map mode.
     */
    public ElevationMapMode(ElevationLayer layer) {
        super("Elevation", "elevation", "Elevation Contour Lines",
                Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        this.layer = layer;
    }

    @Override
    public void enterMode() {
        Logging.info("Elevation: Map mode entered");
        super.enterMode();
        layer.setVisible(true);
    }

    @Override
    public void exitMode() {
        Logging.info("Elevation: Map mode exited");
        super.exitMode();
        layer.setVisible(false);
    }
}
