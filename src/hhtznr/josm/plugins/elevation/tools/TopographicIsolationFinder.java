package hhtznr.josm.plugins.elevation.tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.LatLonLine;
import hhtznr.josm.plugins.elevation.data.SRTMTileGridException;
import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;
import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * This class implements a tool that can compute candidates for reference
 * points, from which the topographic isolation of a peak can be determined. For
 * this, the tool computes the isolines within specified search bounds, which
 * are by 1 meter higher than the provided elevation of the peak. The tool then
 * checks, which points on the isolines have the lowest great circle distance to
 * the peaks position taking into account a specified distance tolerance. This
 * allows the user to decide on his discretion, which of the proposed points is
 * actually the closest since elevation data is not always perfectly accurate,
 * particularly in areas with steep terrain.
 *
 * @author Harald Hetzner
 */
public class TopographicIsolationFinder extends AbstractElevationTool {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Topographic isolation finder");

    /**
     * Creates a new topographic isolation finder.
     *
     * @param elevationDataProvider The elevation data provider providing the data
     *                              for this topographic isolation finder.
     * @param searchBounds          The bounds within which to search for isolation
     *                              reference points.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public TopographicIsolationFinder(ElevationDataProvider elevationDataProvider, Bounds searchBounds)
            throws AsyncOperationException {
        super(namer.nextName(), elevationDataProvider, searchBounds);
    }

    /**
     * Determines reference points for the topographic isolation of a specified
     * peak.
     *
     * @param peak              The coordinates and the elevation of the peak.
     *                          Regarding the elevation of the peak, its official
     *                          elevation should be preferred over the elevation
     *                          value obtained from raster elevation data like SRTM
     *                          data. Raster elevation data tends to underestimate
     *                          the elevation of peaks.
     * @param searchDistance    The maximum search distance in meters. It is used in
     *                          order not to search in corners of the rectangular
     *                          search bounds.
     * @param distanceTolerance A distance tolerance value in meters. Points that
     *                          are up to the distance tolerance further away from
     *                          the given peak as compared to the very closest
     *                          reference point, are included in the list of result.
     *                          To arrive at exactly one highest point (if one
     *                          exists in the specified bounds), a value of
     *                          {@code 0.0} has to be specified.
     * @param deadZoneRadius    Dead zone radius value in meters. Within the dead
     *                          zone around the peak any elevation raster points
     *                          that are higher than its provided elevation will be
     *                          ignored and not be contained in the returned list of
     *                          possible reference points.
     * @return A list of possible reference points for the topographic isolation of
     *         the specified peak, ordered by increasing great circle distance to
     *         the peak, i.e. the first point is the closest according to
     *         computation. An empty list is returned, if no (higher) reference
     *         point could be determined within the specified bounds.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public List<LatLonEle> determineReferencePoints(LatLonEle peak, double searchDistance, double distanceTolerance,
            double deadZoneRadius) throws AsyncOperationException {
        Bounds searchBounds = getBounds();
        SRTMTileGridView tileGridView;
        try {
            tileGridView = getTileGrid().getView(searchBounds);
        } catch (SRTMTileGridException e) {
            Logging.error("Elevation: Cannot establish topographic prominence search: " + e.toString());
            return null;
        }

        informListenersAboutStatus("Waiting for all needed SRTM tiles to be cached");
        // Wait for the tiles being cached
        // May throw CancellationException or InterruptedException
        tileGridView.waitForTilesCached();
        if (Thread.currentThread().isInterrupted()) {
            String message = "Interrupted while waiting for tiles to be cached";
            informListenersAboutStatus(message);
            Logging.info("Elevation: Topographic isolation finder: " + message);
            throw new AsyncOperationException(new InterruptedException("Topographic isolation finder: " + message));
        }
        informListenersAboutStatus("All needed SRTM tiles cached");

        short isolationReferenceElevation = (short) (peak.ele() + 1);
        short[] isovalues = new short[] { isolationReferenceElevation };

        informListenersAboutStatus("Creating contour lines");
        ContourLines contourLines = new ContourLines(tileGridView, isovalues);
        ContourLines.IsolineSegments[] allIsoLineSegments = contourLines.getIsolineSegments();
        contourLines.dispose();
        contourLines = null;
        if (allIsoLineSegments.length < 1) {
            String message = "Could not determine isolation for peak " + peak.toString()
                    + ": No higher isolines found within search bounds " + searchBounds.toString();
            Logging.info("Elevation: Topographic isolation finder: " + message);
            informListenersAboutStatus(message);
            return new ArrayList<>(0);
        }
        List<LatLonLine> isolineSegments = allIsoLineSegments[0].getLineSegments();
        // Immediately set to null to free memory as soon as possible
        allIsoLineSegments = null;
        informListenersAboutStatus("Contour lines created");

        informListenersAboutStatus("Determining closest points");
        Set<LatLonEleDist> closestPoints = new HashSet<>();
        double minClosestDistance = Double.MAX_VALUE;
        double maxClosestDistance = Double.MAX_VALUE;
        for (LatLonLine isolineSegment : isolineSegments) {
            // Stop immediately, if the executing thread got interrupted
            if (Thread.currentThread().isInterrupted()) {
                isolineSegments = null;
                String message = "Interrupted while determining closest points from isoline segments";
                Logging.info("Elevation: Topographic isolation finder: " + message);
                throw new AsyncOperationException(new InterruptedException("Topographic isolation finder: " + message));
            }
            LatLon closestPoint = isolineSegment.getClosestPointTo(peak);
            double distance = peak.greatCircleDistance((ILatLon) closestPoint);

            if (distance > deadZoneRadius && distance < maxClosestDistance && distance <= searchDistance) {
                if (distance < minClosestDistance) {
                    minClosestDistance = distance;
                    maxClosestDistance = minClosestDistance + distanceTolerance;

                    Iterator<LatLonEleDist> iterator = closestPoints.iterator();
                    while (iterator.hasNext()) {
                        LatLonEleDist pointDist = iterator.next();
                        if (pointDist.distance > maxClosestDistance)
                            iterator.remove();
                    }
                }
                LatLonEle point = new LatLonEle(closestPoint, isolationReferenceElevation);
                closestPoints.add(new LatLonEleDist(point, distance));
            }
        }
        // Set to null to free memory as soon as possible
        isolineSegments = null;

        // Should not happen, because otherwise we could not generate the isolines
        if (closestPoints.size() < 1) {
            String message = "Could not determine isolation for peak " + peak.toString()
                    + ": No higher point found within search bounds " + searchBounds.toString();
            Logging.info("Elevation: Topographic isolation finder: " + message);
            informListenersAboutStatus(message);
            return new ArrayList<>(0);
        }
        informListenersAboutStatus("" + closestPoints.size() + " closest points determined");

        ArrayList<LatLonEleDist> closestPointsSorted = new ArrayList<>(closestPoints);
        closestPointsSorted.sort((a, b) -> Double.compare(a.distance, b.distance));
        ArrayList<LatLonEle> resultList = new ArrayList<>(closestPoints.size());
        for (LatLonEleDist closestPoint : closestPointsSorted)
            resultList.add(closestPoint.point);
        return resultList;
    }

    private static class LatLonEleDist {

        public final LatLonEle point;
        public final double distance;

        public LatLonEleDist(LatLonEle point, double distance) {
            this.point = point;
            this.distance = distance;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || !(obj instanceof LatLonEleDist))
                return false;
            LatLonEleDist that = (LatLonEleDist) obj;
            return Double.compare(that.point.lat(), point.lat()) == 0
                    && Double.compare(that.point.lon(), point.lon()) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(point.lat(), point.lon());
        }
    }
}
