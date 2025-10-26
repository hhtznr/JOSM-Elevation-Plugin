package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

/**
 * Abstract superclass of paintable objects obtained from an
 * {@code SRTMTileGrid}. Such paintable objects have nominal bounds within which
 * they provide useful paintable output and larger actual bounds which are to be
 * used for proper alignment of the paintable.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractSRTMTileGridPaintable {

    protected final SRTMTileGrid tileGrid;
    protected final SRTMTileGrid.RasterIndexBounds renderingRasterIndexBounds;
    protected Bounds viewBounds;
    protected Bounds renderingBounds;

    /**
     * Abstract constructor of a paintable object obtained from an SRTM tile grid.
     *
     * @param tileGrid        The grid of SRTM tiles, where this paintable should
     *                        obtain elevation values from its raster.
     * @param viewBounds      The bounds of the view on this paintable object, where
     *                        flawless paint output shall be provided.
     * @param renderingBounds The rendering bounds which are larger than the view
     *                        bounds. The rendering bounds have to be used for
     *                        proper alignment of this paintable. They should always
     *                        be outside the viewport area. The area of the
     *                        rendering bounds, which is outside the view bounds may
     *                        contain artifacts present to technical reasons of the
     *                        implementation.
     */
    public AbstractSRTMTileGridPaintable(SRTMTileGrid tileGrid, Bounds viewBounds, Bounds renderingBounds) {
        this.tileGrid = tileGrid;
        this.renderingRasterIndexBounds = tileGrid.getRasterIndexBounds(renderingBounds);
        this.viewBounds = viewBounds;
        this.renderingBounds = renderingBounds;
    }

    /**
     * Returns the view bounds.
     *
     * @return The nominal bounds within which useful paint output can be provided
     *         by this paintable.
     */
    public Bounds getViewBounds() {
        return viewBounds;
    }

    /**
     * Returns the rendering bounds.
     *
     * @return The actual bounds which are larger than the nominal bounds. The
     *         actual bounds have to be used for proper alignment of this paintable.
     *         They should always be outside the viewport area.
     */
    public Bounds getRenderingBounds() {
        return renderingBounds;
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
        return viewBounds.contains(bounds);
    }
}
