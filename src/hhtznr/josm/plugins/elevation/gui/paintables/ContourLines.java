package hhtznr.josm.plugins.elevation.gui.paintables;

import java.util.ArrayList;
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

    private int isostep;
    private IsolineSegments[] isolineSegments;

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
     *                                 lines is rejected
     * @throws InterruptedException    if asynchronous computation of the contour
     *                                 lines is interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, int isostep, int gridStepLat, int gridStepLon,
            int lowerCutoffElevation, int upperCutoffElevation) throws AsyncOperationException, InterruptedException {
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
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected
     * @throws InterruptedException    if asynchronous computation of the contour
     *                                 lines is interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) throws AsyncOperationException, InterruptedException {
        this(elevationGridView, elevationGridView.getIsovalues(isostep, lowerCutoffElevation, upperCutoffElevation),
                isostep, MINIMUM_GRID_STEP, MINIMUM_GRID_STEP);
    }

    /**
     * Creates a new set of contour lines covering the bounds of the specified view
     * using the {@link MINIMUM_GRID_STEP}.
     *
     * @param elevationGridView The view on an elevation grid for which the contour
     *                          lines should be computed.
     * @param isovalues         An array of elevation values defining the isolevels.
     *                          See
     *                          {@link MarchingSquares#MarchingSquares(ElevationGridView, short[])}.
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected
     * @throws InterruptedException    if asynchronous computation of the contour
     *                                 lines is interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, short[] isovalues)
            throws AsyncOperationException, InterruptedException {
        this(elevationGridView, isovalues, UNDEFINED_ISOSTEP, MINIMUM_GRID_STEP, MINIMUM_GRID_STEP);
    }

    private ContourLines(ElevationGridView elevationGridView, short[] isovalues, int isostep, int gridStepLat,
            int gridStepLon) throws AsyncOperationException, InterruptedException {
        super(namer.nextName(), elevationGridView);
        try {
            if (isostep == UNDEFINED_ISOSTEP) {
                if (isovalues.length < 2) {
                    this.isostep = 1;
                } else {
                    this.isostep = isovalues[1] - isovalues[0];
                }
            } else {
                this.isostep = isostep;
            }
            if (isovalues.length > 0) {
                MarchingSquares marchingSquares = new MarchingSquares(this.elevationGridView, isovalues, gridStepLat,
                        gridStepLon);
                this.isolineSegments = marchingSquares.getIsolineSegments();
            } else {
                this.isolineSegments = new IsolineSegments[0];
            }
        } catch (AsyncOperationException | InterruptedException | RuntimeException | Error e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            super.dispose();
            throw e;
        }
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
     * Reduces the number of isoline segments of these contour lines by removing
     * segments with isovalues smaller and greater then the specified target values.
     *
     * @param targetMinIsovalue The targeted new minimum isovalue. If the specified
     *                          value does not align with present isovalues, the
     *                          next greater present isovalue will be chosen as new
     *                          minimum isovalue.
     * @param targetMaxIsovalue The targeted new maximum isovalue. If the specified
     *                          value does not align with present isovalues, the
     *                          next smaller present isovalue will be chosen as new
     *                          maximum isovalue.
     * @return {@code true} if the number of isoline segments was actually reduced
     *         by the requested operation, {@code false} otherwise.
     */
    public boolean shrinkIsolineSegments(int targetMinIsovalue, int targetMaxIsovalue) {
        if (targetMinIsovalue > targetMaxIsovalue || isolineSegments.length < 1) {
            return false;
        }
        int minIsovalue = isolineSegments[0].getIsovalue();
        int maxIsovalue = isolineSegments[isolineSegments.length - 1].getIsovalue();
        // Requested isovalue range is larger than current range
        if (targetMinIsovalue <= minIsovalue && targetMaxIsovalue >= maxIsovalue) {
            return false;
        }
        // Requested isovalue range is outside current range
        if (targetMaxIsovalue < minIsovalue || targetMinIsovalue > maxIsovalue) {
            isolineSegments = new IsolineSegments[0];
            return true;
        }

        int minIndex = 0;
        int maxIndex = isolineSegments.length - 1;
        for (int i = 0; i < isolineSegments.length; i++) {
            int isovalue = isolineSegments[i].getIsovalue();
            if (isovalue >= targetMinIsovalue) {
                minIndex = i;
                break;
            }
        }
        for (int i = isolineSegments.length - 1; i >= 0; i--) {
            int isovalue = isolineSegments[i].getIsovalue();
            if (isovalue <= targetMaxIsovalue) {
                maxIndex = i;
                break;
            }
        }
        // Isoline segments unchanged
        if (minIndex == 0 && maxIndex == isolineSegments.length - 1) {
            return false;
        }
        if (minIndex > maxIndex) {
            isolineSegments = new IsolineSegments[0];
            return true;
        }

        IsolineSegments[] newIsolineSegments = new IsolineSegments[maxIndex - minIndex + 1];
        for (int i = 0; i < newIsolineSegments.length; i++) {
            newIsolineSegments[i] = isolineSegments[minIndex + i];
        }
        isolineSegments = newIsolineSegments;
        return true;
    }

    /**
     * Reduces the number of isoline segments of these contour lines by removing
     * segments with isovalues that do not correspond with the new isostep.
     *
     * @param newIsostep The new isostep. It must be greater than the current
     *                   isostep and divisible by the current isostep. For instance,
     *                   with a current isostep of {@code 5}, a new isostep of
     *                   {@code 20} is valid for pruning isoline segments.
     * @return {@code true} if the number of isoline segments was reduced or the new
     *         isostep was compatible and set as the contour line's isostep, whilst
     *         the number of isoline segments was effectively not reduced (e.g. in
     *         case of only a single isovalue); {@code false} otherwise.
     */
    public boolean pruneIsolineSegments(int newIsostep) {
        // Reject negative or zero isovalues
        if (newIsostep < 1) {
            throw new IllegalArgumentException("Isostep must be > 0");
        }

        // Nothing to do, if isostep is unchanged
        if (newIsostep == isostep) {
            return false;
        }

        // We cannot prune isolines if the new isostep is smaller
        if (newIsostep < isostep) {
            return false;
        }

        // We cannot prune if the new isostep is not a divisor
        if (newIsostep % isostep != 0) {
            return false;
        }

        // If no isoline segments exist, only update isostep
        if (isolineSegments.length == 0) {
            isostep = newIsostep;
            return true;
        }

        // Determine the index of the first isovalue aligned with the new isostep
        int firstIndex = -1;
        for (int i = 0; i < isolineSegments.length; i++) {
            int isovalue = isolineSegments[i].getIsovalue();
            if (isovalue % newIsostep == 0) {
                firstIndex = i;
                break;
            }
        }

        // Empty isoline segments if no existing isovalue is aligned with new isostep
        if (firstIndex < 0) {
            isolineSegments = new IsolineSegments[0];
            isostep = newIsostep;
            return true;
        }

        // Step between current indices to retrieve remaining isovalues
        int indexStep = newIsostep / isostep;
        // Retrieve remaining isovalues
        ArrayList<IsolineSegments> newIsolineSegments = new ArrayList<>(
                (isolineSegments.length - firstIndex) / indexStep);
        for (int i = firstIndex; i < isolineSegments.length; i += indexStep) {
            newIsolineSegments.add(isolineSegments[i]);
        }
        isolineSegments = newIsolineSegments.toArray(new IsolineSegments[newIsolineSegments.size()]);
        isostep = newIsostep;
        return true;
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
        public short getIsovalue() {
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
