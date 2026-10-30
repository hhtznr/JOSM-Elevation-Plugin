package hhtznr.josm.plugins.elevation.gui.paintables;

import java.util.List;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonLine;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGridView;
import hhtznr.josm.plugins.elevation.math.MarchingSquares;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing contour lines composed of individual segments which cover
 * specified bounds.
 *
 * @author Harald Hetzner
 */
public class ContourLines extends AbstractElevationGridPaintable {

    /**
     * The minimum step when iteration over the grid in order to compute contour
     * lines: {@code 1}.
     */
    public static final int MINIMUM_GRID_STEP = 1;

    public static final int BOUNDS_SCALE_RASTER_STEP = 2;

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator("Contour lines");

    private static final int UNDEFINED_ISOSTEP = -1;

    private final int isostep;
    private final IsolineSegments[] isolineSegments;

    /**
     * Creates a new set of contour lines covering the bounds of the specified view.
     *
     * @param elevationGridView    The view on an elevation grid for which the
     *                             contour lines should be computed.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     * @param gridStepLat          The step in latitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. If the step is {@code 1}, each
     *                             grid point will be considered. If the step is
     *                             {@code 2}, only each 2nd grid point will be
     *                             conidered, etc. Value must be {@code >= 1}.
     * @param gridStepLon          The step in longitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. Value must be {@code >= 1}.
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected or interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, int isostep, int gridStepLat, int gridStepLon,
            int lowerCutoffElevation, int upperCutoffElevation) throws AsyncOperationException {
        this(elevationGridView, elevationGridView.getIsovalues(isostep, lowerCutoffElevation, upperCutoffElevation),
                isostep, gridStepLat, gridStepLon);
    }

    /**
     * Creates a new set of contour lines covering the bounds of the specified view
     * using the {@link MINIMUM_GRID_STEP}.
     *
     * @param elevationGridView    The view on an elevation grid for which the
     *                             contour lines should be computed.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     * @throws AsyncOperationException Thrown if asynchronous computation of the
     *                                 contour lines is rejected or interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) throws AsyncOperationException {
        this(elevationGridView, elevationGridView.getIsovalues(isostep, lowerCutoffElevation, upperCutoffElevation),
                isostep, MINIMUM_GRID_STEP, MINIMUM_GRID_STEP);
    }

    /**
     * Creates a new set of contour lines covering the bounds of the specified view
     * using the {@link MINIMUM_GRID_STEP}.
     *
     * @param elevationGridView The view on an elevation grid for which the contour
     *                          lines should be computed.
     * @param isostep           The step in meters between adjacent contour lines.
     * @throws AsyncOperationException Thrown if asynchronous computation of the
     *                                 contour lines is rejected or interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, short[] isovalues) throws AsyncOperationException {
        this(elevationGridView, isovalues, UNDEFINED_ISOSTEP, MINIMUM_GRID_STEP, MINIMUM_GRID_STEP);
    }

    private ContourLines(ElevationGridView elevationGridView, short[] isovalues, int isostep, int gridStepLat,
            int gridStepLon) throws AsyncOperationException {
        super(namer.nextName(), elevationGridView);
        if (isostep == UNDEFINED_ISOSTEP) {
            if (isovalues.length < 2) {
                this.isostep = 1;
            } else {
                this.isostep = isovalues[1] - isovalues[0];
            }
        } else {
            this.isostep = isostep;
        }
        MarchingSquares marchingSquares = new MarchingSquares(this.elevationGridView, isovalues, gridStepLat,
                gridStepLon);
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
     * Returns the step between two adjacent raster points in latitude or longitude
     * direction.
     *
     * @return The step in arc-seconds between two adjacent raster points in
     *         latitude or longitude direction.
     */
    public double getSrtmLatLonStep() {
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        if (elevationGridView instanceof SrtmTileGridView) {
            return ((SrtmTileGridView) elevationGridView).getLatLonStep();
        }
        throw new IllegalStateException(
                "This compatibility method can only be used with an instance of " + SrtmTileGridView.class.toString());
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
