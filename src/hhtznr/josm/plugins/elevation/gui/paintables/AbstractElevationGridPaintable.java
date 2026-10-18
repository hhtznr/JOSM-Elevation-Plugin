package hhtznr.josm.plugins.elevation.gui.paintables;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.ElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;

/**
 * Abstract superclass of paintable objects obtained from an
 * {@code SRTMTileGrid}. Such paintable objects have nominal bounds within which
 * they provide useful paintable output and larger actual bounds which are to be
 * used for proper alignment of the paintable.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractElevationGridPaintable extends ElevationDataConsumer {

    protected final ElevationGridView elevationGridView;

    private boolean invalidated = false;

    /**
     * Abstract constructor of a paintable object obtained from an SRTM tile grid
     * view.
     *
     * @param elevationGridView The elevation grid view, where this paintable should
     *                          obtain elevation values from its raster.
     */
    public AbstractElevationGridPaintable(String name, ElevationGridView elevationGridView) {
        super(name, elevationGridView.getElevationGrid());
        this.elevationGridView = elevationGridView;
    }

    /**
     * Returns whether the given bounds are covered by the bounds of this tile grid
     * paintable.
     *
     * @param bounds The bounds for which to check if they are covered by this tile
     *               grid paintable.
     * @return {@code true} if the given bounds are contained in the bounds,
     *         {@code false} otherwise.
     */
    public boolean covers(Bounds bounds) {
        return elevationGridView.getBounds().contains(bounds);
    }

    /**
     * Returns the coordinate bounds of this paintable object.
     *
     * @return The coordinate bounds.
     */
    public Bounds getBounds() {
        return elevationGridView.getBounds();
    }

    /**
     * Invalidates this abstract tile grid paintable.
     */
    public void invalidate() {
        invalidated = true;
    }

    /**
     * Returns whether this abstract tile grid paintable has been invalidated.
     *
     * @return {@code true} if this abstract tile grid paintable has been
     *         invalidated, {@code false} otherwise.
     */
    public boolean isInvalidated() {
        return invalidated;
    }
}
