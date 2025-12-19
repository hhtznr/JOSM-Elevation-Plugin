package hhtznr.josm.plugins.elevation.gui;

import java.util.List;

import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;
import hhtznr.josm.plugins.elevation.math.MarchingSquares;

/**
 * Class implementing contour lines composed of individual segments which cover
 * specified bounds.
 *
 * @author Harald Hetzner
 */
public class ContourLines extends AbstractSRTMTileGridPaintable {

    public static final int BOUNDS_SCALE_RASTER_STEP = 2;

    private static final int UNDEFINED_ISOSTEP = -1;

    private final int isostep;
    private final IsolineSegments[] isolineSegments;

    /**
     * Creates a new set of contour lines covering the bounds of the specified view.
     *
     * @param tileGridView         The SRTM tile grid view from which elevation data
     *                             should be obtained.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     */
    public ContourLines(SRTMTileGridView tileGridView, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) {
        this(tileGridView, tileGridView.getIsovalues(isostep, lowerCutoffElevation, upperCutoffElevation), isostep);
    }

    /**
     * Creates a new set of contour lines for the specified isovalues only covering
     * the bounds of the specified view.
     *
     * @param tileGridView    The SRTM tile grid view from which elevation data
     *                        should be obtained.
     * @param isovalues       The isovalues for which to create contour lines.
     */
    public ContourLines(SRTMTileGridView tileGridView, short[] isovalues) {
        this(tileGridView, isovalues, UNDEFINED_ISOSTEP);
    }

    private ContourLines(SRTMTileGridView tileGridView, short[] isovalues, int isostep) {
        super(tileGridView);
        if (isostep == UNDEFINED_ISOSTEP) {
            if (isovalues.length < 2)
                this.isostep = 1;
            else
                this.isostep = isovalues[1] - isovalues[0];
        } else {
            this.isostep = isostep;
        }
        MarchingSquares marchingSquares = new MarchingSquares(this.tileGridView, isovalues);
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
