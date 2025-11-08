package hhtznr.josm.plugins.elevation.gui;

import java.util.List;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;
import hhtznr.josm.plugins.elevation.math.MarchingSquares;

/**
 * Class implementing contour lines composed of individual segments which cover
 * specified bounds.
 *
 * @author Harald Hetzner
 */
public class ContourLines extends AbstractSRTMTileGridPaintable {

    public static final int BOUNDS_SCALE_RASTER_STEP = 2;

    private final int isostep;
    private final IsolineSegments[] isolineSegments;

    /**
     * Creates a new set of contour lines covering the specified bounds.
     *
     * @param tileGrid             The SRTM tile grid from which elevation data
     *                             should be obtained.
     * @param renderingBounds      The bounds in latitude-longitude coordinate
     *                             space, within which these contour lines can be
     *                             rendered. The very edges might show artifacts.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     */
    public ContourLines(SRTMTileGrid tileGrid, Bounds renderingBounds, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) {
        super(tileGrid, tileGrid.getViewBoundsScaledByRasterStep(renderingBounds, BOUNDS_SCALE_RASTER_STEP),
                renderingBounds);
        this.isostep = isostep;
        short[] isovalues = tileGrid.getIsovalues(renderingRasterIndexBounds, isostep, lowerCutoffElevation,
                upperCutoffElevation);
        MarchingSquares marchingSquares = new MarchingSquares(this.tileGrid, this.renderingBounds,
                renderingRasterIndexBounds, isovalues);
        this.isolineSegments = marchingSquares.getIsolineSegments();
    }

    /**
     * Creates a new set of contour lines for the specified isovalues only covering
     * the specified bounds.
     *
     * @param tileGrid        The SRTM tile grid from which elevation data should be
     *                        obtained.
     * @param renderingBounds The bounds in latitude-longitude coordinate space,
     *                        within which these contour lines can be rendered. The
     *                        very edges might show artifacts.
     * @param isovalues       The isovalues for which to create contour lines.
     */
    public ContourLines(SRTMTileGrid tileGrid, Bounds renderingBounds, short[] isovalues) {
        super(tileGrid, tileGrid.getViewBoundsScaledByRasterStep(renderingBounds, BOUNDS_SCALE_RASTER_STEP),
                renderingBounds);
        if (isovalues.length < 2)
            this.isostep = 1;
        else
            this.isostep = isovalues[1] - isovalues[0];
        MarchingSquares marchingSquares = new MarchingSquares(this.tileGrid, this.renderingBounds,
                renderingRasterIndexBounds, isovalues);
        this.isolineSegments = marchingSquares.getIsolineSegments();
    }

    /**
     * Returns the step between adjacent contour lines.
     *
     * @return The step in meters between adjacent contour lines.
     */
    public int getIsostep() {
        return isostep;
    }

    /**
     * Returns the array of isoline segments forming the contour linescovering the
     * bounds.
     *
     * @return The array of isoline segments.
     */
    public IsolineSegments[] getIsolineSegments() {
        return isolineSegments;
    }

    /**
     * Helper class for managing isolinesegments along with their isovalue.
     */
    public static class IsolineSegments {

        private final short isovalue;
        private final List<LatLonLine> lineSegments;

        /**
         * Creates a new isoline segments object holding isoline segments along with
         * their isovalue (elevation value).
         *
         * @param isovalue     The isovalue of the isoline segments.
         * @param lineSegments The straight line segments in latitude-longitude
         *                     coordinate space forming the isolines for the given
         *                     isovalue.
         */
        public IsolineSegments(short isovalue, List<LatLonLine> lineSegments) {
            this.isovalue = isovalue;
            this.lineSegments = lineSegments;
        }

        /**
         * Returns the isovalue.
         *
         * @return The isovalue of the isoline segments.
         */
        public short getIsoValue() {
            return isovalue;
        }

        /**
         * Returns the isoline segments.
         *
         * @return The isoline segments.
         */
        public List<LatLonLine> getLineSegments() {
            return lineSegments;
        }
    }
}
