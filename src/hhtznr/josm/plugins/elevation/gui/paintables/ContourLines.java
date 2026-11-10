package hhtznr.josm.plugins.elevation.gui.paintables;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MapView;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.LowestAndHighestElevation;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLon2EastNorthProjectionCache;
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
    private IsolinePath[] isolinePaths = null;;
    private MapView isolinesMapView = null;
    private static final Object isolinePathsLock = new Object();

    /**
     * Creates a new set of contour lines for a single isovalue covering the bounds
     * of the specified view.
     *
     * @param elevationGridView The view on an elevation grid for which the contour
     *                          lines should be computed.
     * @param isovalue          The isovalue in meters for which the contour lines
     *                          shall be computed.
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected.
     * @throws InterruptedException    if asynchronous computation of the contour
     *                                 lines is interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, short isovalue)
            throws AsyncOperationException, InterruptedException {
        this(elevationGridView, 1, MINIMUM_GRID_STEP, MINIMUM_GRID_STEP, isovalue, isovalue);
    }

    /**
     * Creates a new set of contour lines covering the bounds of the specified view.
     *
     * @param elevationGridView    The view on an elevation grid for which the
     *                             contour lines should be computed.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param gridStepLat          The step in latitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. If the step is {@code 1}, each
     *                             grid point will be considered. If the step is
     *                             {@code 2}, only each 2nd grid point will be
     *                             conidered, etc. Value must be {@code >= 1}.
     * @param gridStepLon          The step in longitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. Value must be {@code >= 1}.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected.
     * @throws InterruptedException    if asynchronous computation of the contour
     *                                 lines is interrupted.
     */
    public ContourLines(ElevationGridView elevationGridView, int isostep, int gridStepLat, int gridStepLon,
            int lowerCutoffElevation, int upperCutoffElevation) throws AsyncOperationException, InterruptedException {
        super(namer.nextName(), elevationGridView);
        try {
            LowestAndHighestElevation lowestAndHighestElevation = elevationGridView.getLowestAndHighestPoints()
                    .getLowestAndHighestElevation();

            if (!lowestAndHighestElevation.isValid()) {
                this.isolineSegments = new IsolineSegments[0];
            }

            double lowestElevation = lowestAndHighestElevation.getLowestElevation();
            double highestElevation = lowestAndHighestElevation.getHighestElevation();

            // Apply the lower cutoff elevation value, if it is greater than the minimum
            // elevation within the grid
            double minEle = Math.max(lowestElevation, lowerCutoffElevation);
            // Apply the upper cutoff elevation value, if it is smaller than the maximum
            // elevation within the grid
            double maxEle = Math.min(highestElevation, upperCutoffElevation);

            short[] isovalues;

            if (minEle > maxEle) {
                isovalues = new short[0]; // The cutoffs exclude the view's elevation range.
            } else {

                // Determine the list of isovalues, i.e. the elevation levels for which contour
                // lines should be computed
                // TODO: Use Math.ceil() and Math.floor() instead of cast to short
                short minIsovalue;
                short maxIsovalue;
                if ((short) minEle % isostep == 0) {
                    minIsovalue = (short) minEle;
                } else {
                    minIsovalue = (short) (((short) minEle / isostep) * isostep);
                }
                if ((short) maxEle % isostep == 0) {
                    maxIsovalue = (short) maxEle;
                } else {
                    maxIsovalue = (short) (((short) maxEle / isostep) * isostep);
                }
                if (maxIsovalue < minIsovalue) {
                    isovalues = new short[0];
                } else {
                    int nSteps = (maxIsovalue - minIsovalue) / isostep + 1;
                    isovalues = new short[nSteps];
                    for (int i = 0; i < nSteps; i++) {
                        isovalues[i] = (short) (minIsovalue + i * isostep);
                    }
                }
            }

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
     * Returns an array with the unprojected latitude-longitude isoline segments
     * forming the contour lines covering the bounds.
     *
     * @return An array with the isoline segments in latitude-longitude coordinate
     *         space.
     */
    public IsolineSegments[] getIsolineSegments() {
        return isolineSegments;
    }

    /**
     * Returns an array with the projected east-north isoline paths forming the
     * contour lines covering the bounds.<br>
     * The isoline paths (east-north coordinates) are computed from the always
     * present isoline segments (latitude-longitude coordinates), the first time
     * that this method is called on these contour lines and then retained.
     * Therefore, this method should not be called, if operations on
     * latitude-longitude coordinates only will be performed.
     *
     * @param mapView The map view for which to perform the conversion from
     *                latitude-longitude isoline segments to east-north isoline
     *                paths.
     * @return An array with the isoline paths in east-north coordinate space.
     */
    public IsolinePath[] getIsolinePaths(MapView mapView) {
        synchronized (isolinePathsLock) {
            if (mapView.equals(isolinesMapView) && isolinePaths != null) {
                return isolinePaths;
            }

            LatLon2EastNorthProjectionCache projectionCache = new LatLon2EastNorthProjectionCache(
                    mapView.getProjection());

            isolinePaths = new IsolinePath[isolineSegments.length];
            for (int i = 0; i < isolinePaths.length; i++) {
                IsolineSegments segments = isolineSegments[i];
                short isovalue = segments.getIsovalue();
                Path2D.Double path = segments.createIsolinePath(projectionCache);
                IsolinePath isolinesPath = new IsolinePath(isovalue, path);
                isolinePaths[i] = isolinesPath;
            }
            return isolinePaths;
        }
    }

    /**
     * Reduces the number of isoline segments and isoline paths of these contour
     * lines by removing segments with isovalues smaller and greater then the
     * specified target values.
     *
     * @param targetMinIsovalue The targeted new minimum isovalue. If the specified
     *                          value does not align with present isovalues, the
     *                          next greater present isovalue will be chosen as new
     *                          minimum isovalue.
     * @param targetMaxIsovalue The targeted new maximum isovalue. If the specified
     *                          value does not align with present isovalues, the
     *                          next smaller present isovalue will be chosen as new
     *                          maximum isovalue.
     * @return {@code true} if the number of isoline segments and paths was actually
     *         reduced by the requested operation, {@code false} otherwise.
     */
    public boolean shrinkIsolineSegments(int targetMinIsovalue, int targetMaxIsovalue) {
        synchronized (isolinePathsLock) {
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
            IsolinePath[] newIsolinePaths = null;
            if (isolinePaths != null) {
                newIsolinePaths = new IsolinePath[maxIndex - minIndex + 1];
            }
            for (int i = 0; i < newIsolineSegments.length; i++) {
                newIsolineSegments[i] = isolineSegments[minIndex + i];
                if (isolinePaths != null) {
                    newIsolinePaths[i] = isolinePaths[minIndex + i];
                }
            }
            isolineSegments = newIsolineSegments;
            isolinePaths = newIsolinePaths;
            return true;
        }
    }

    /**
     * Reduces the number of isoline segments and isoline paths of these contour
     * lines by removing segments with isovalues that do not correspond with the new
     * isostep.
     *
     * @param newIsostep The new isostep. It must be greater than the current
     *                   isostep and divisible by the current isostep. For instance,
     *                   with a current isostep of {@code 5}, a new isostep of
     *                   {@code 20} is valid for pruning isoline segments.
     * @return {@code true} if the number of isoline segments and paths was reduced
     *         or the new isostep was compatible and set as the contour line's
     *         isostep, whilst the number of isoline segments was effectively not
     *         reduced (e.g. in case of only a single isovalue); {@code false}
     *         otherwise.
     */
    public boolean pruneIsolineSegments(int newIsostep) {
        synchronized (isolinePathsLock) {
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
            ArrayList<IsolinePath> newIsolines = null;
            if (isolinePaths != null) {
                newIsolines = new ArrayList<>((isolineSegments.length - firstIndex) / indexStep);
            }
            for (int i = firstIndex; i < isolineSegments.length; i += indexStep) {
                newIsolineSegments.add(isolineSegments[i]);
                if (isolinePaths != null) {
                    newIsolines.add(isolinePaths[i]);
                }
            }
            isolineSegments = newIsolineSegments.toArray(new IsolineSegments[newIsolineSegments.size()]);
            if (newIsolines == null) {
                isolinePaths = null;
            } else {
                isolinePaths = newIsolines.toArray(new IsolinePath[newIsolines.size()]);
            }
            isostep = newIsostep;
            return true;
        }
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
     * Helper class for managing latitude-longitude isoline segments along with
     * their isovalue and for projecting them to east-north isoline paths.
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

        /**
         * Creates a new isoline path by projecting these isoline segments with
         * latitude-longitude endpoints to isoline path segments with east-north
         * endpoints.
         *
         * @param projectionCache The projection to be used for conversion of
         *                        latitude-longitude coordinates to east-north
         *                        coordinates.
         * @return The isoline path using east-north coordinates that corresponds to
         *         these isoline segments.
         */
        public Path2D.Double createIsolinePath(LatLon2EastNorthProjectionCache projectionCache) {
            Path2D.Double path = new Path2D.Double();
            for (LatLonLine line : lineSegments) {
                EastNorth p1 = projectionCache.convert(line.getLatLon1());
                EastNorth p2 = projectionCache.convert(line.getLatLon2());
                path.moveTo(p1.east(), p1.north());
                path.lineTo(p2.east(), p2.north());
            }
            return path;
        }
    }

    /**
     * Helper class for managing east-north isoline paths along with their isovalue.
     */
    public static class IsolinePath {
        private final short isovalue;
        private final Path2D.Double path;

        /**
         * Creates a new isoline path. A path can consist of arbitrary, disjoint line
         * segments together forming the isolines at the specified isovalue.
         *
         * @param isovalue The isolvalue of the isoline path.
         * @param path     The east-north coordinate path forming the isolines at the
         *                 isovalue.
         */
        protected IsolinePath(short isovalue, Path2D.Double path) {
            this.isovalue = isovalue;
            this.path = path;
        }

        /**
         * Returns the isovalue associated with the isoline path.
         *
         * @return The isovalue.
         */
        public short getIsovalue() {
            return isovalue;
        }

        /**
         * Returns the east-north coordinate path forming the isolines at the isovalue.
         *
         * @return The isoline path.
         */
        public Path2D.Double getPath() {
            return path;
        }
    }
}
