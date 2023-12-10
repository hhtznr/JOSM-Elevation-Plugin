package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;

/**
 * Abstract superclass of paintable objects obtained from an
 * {@code SRTMTileGrid}. Such paintable objects have nominal bounds within which
 * they provide useful paintable output and larger actual bounds which are to be
 * used for proper alignment of the paintable.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractSRTMTileGridPaintable {

    private final Bounds nominalBounds;
    private Bounds actualBounds;

    /**
     * Abstract constructor of a paintable object obtained from an SRTM tile grid.
     *
     * @param nominalBounds The nominal bounds within which useful paint output can
     *                      be provided by this paintable.
     * @param actualBounds  The actual bounds which are larger than the nominal
     *                      bounds. The actual bounds have to be used for proper
     *                      alignment of this paintable. They should always be
     *                      outside the viewport area.
     */
    public AbstractSRTMTileGridPaintable(Bounds nominalBounds, Bounds actualBounds) {
        this.nominalBounds = nominalBounds;
        this.actualBounds = actualBounds;
    }

    /**
     * Returns the nominal bounds.
     *
     * @return The nominal bounds within which useful paint output can be provided
     *         by this paintable.
     */
    public Bounds getNominalBounds() {
        return nominalBounds;
    }

    /**
     * Returns the actual bounds.
     *
     * @return The actual bounds which are larger than the nominal bounds. The
     *         actual bounds have to be used for proper alignment of this paintable.
     *         They should always be outside the viewport area.
     */
    public Bounds getActualBounds() {
        return actualBounds;
    }

    /**
     * Sets the actual bounds of this paintable.
     *
     * @param bounds The actual bounds.
     */
    protected void setActualBounds(Bounds bounds) {
        actualBounds = bounds;
    }

    /**
     * Returns whether the given bounds are covered by the nominal bounds of this
     * tile grid paintable.
     *
     * @param bounds The bounds for which to check if they are covered by this tile
     *               grid paintable.
     * @return {@code true} if the given bounds are contained in the nominal bounds,
     *         {@code false} otherwise.
     */
    public boolean covers(Bounds bounds) {
        return nominalBounds.contains(bounds);
    }
}
