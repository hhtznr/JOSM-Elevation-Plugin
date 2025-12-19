package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;

/**
 * Abstract superclass of paintable objects obtained from an
 * {@code SRTMTileGrid}. Such paintable objects have nominal bounds within which
 * they provide useful paintable output and larger actual bounds which are to be
 * used for proper alignment of the paintable.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractSRTMTileGridPaintable {

    protected final SRTMTileGridView tileGridView;

    /**
     * Abstract constructor of a paintable object obtained from an SRTM tile grid
     * view.
     *
     * @param tileGridView The SRTM tile grid view, where this paintable should
     *                     obtain elevation values from its raster.
     */
    public AbstractSRTMTileGridPaintable(SRTMTileGridView tileGridView) {
        this.tileGridView = tileGridView;
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
        return tileGridView.getBounds().contains(bounds);
    }

    /**
     * Returns the coordinate bounds of this paintable object.
     *
     * @return The coordinate bounds.
     */
    public Bounds getBounds() {
        return tileGridView.getBounds();
    }
}
