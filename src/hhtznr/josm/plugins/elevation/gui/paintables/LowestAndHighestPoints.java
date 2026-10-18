package hhtznr.josm.plugins.elevation.gui.paintables;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing an object to represent the lowest and highest points
 * within a view on an SRTM tile grid.
 *
 * @author Harald Hetzner
 */
public class LowestAndHighestPoints extends AbstractElevationGridPaintable {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Lowest and highest points");

    private final List<LatLonEle> lowestPoints;
    private final List<LatLonEle> highestPoints;
    private final double lowestElevation;
    private final double highestElevation;

    /**
     * Creates a new lowest and highest points object for an elevation grid view.
     *
     * @param elevationGridView The elevation grid view from which elevation data
     *                          should be obtained.
     */
    public LowestAndHighestPoints(ElevationGridView elevationGridView) {
        super(namer.nextName(), elevationGridView);

        int height = elevationGridView.getHeight();
        int width = elevationGridView.getWidth();

        double previousMinEle = Double.POSITIVE_INFINITY;
        double previousMaxEle = Double.NEGATIVE_INFINITY;

        List<Point> lowestPointIndices = new LinkedList<>();
        List<Point> highestPointIndices = new LinkedList<>();
        for (int latIndex = 0; latIndex < height; latIndex++) {

            for (int lonIndex = 0; lonIndex < width; lonIndex++) {

                double ele = elevationGridView.getElevation(latIndex, lonIndex);
                if (elevationGridView.isNoDataValue(ele)) {
                    continue;
                }

                if (ele < previousMinEle) {
                    lowestPointIndices.clear();
                    lowestPointIndices.add(new Point(lonIndex, latIndex));
                    previousMinEle = ele;
                } else if (ele == previousMinEle) {
                    lowestPointIndices.add(new Point(lonIndex, latIndex));
                }

                if (ele > previousMaxEle) {
                    highestPointIndices.clear();
                    highestPointIndices.add(new Point(lonIndex, latIndex));
                    previousMaxEle = ele;
                } else if (ele == previousMaxEle) {
                    highestPointIndices.add(new Point(lonIndex, latIndex));
                }
            }
        }
        if (previousMinEle == Double.POSITIVE_INFINITY) {
            lowestElevation = Double.NaN;
        } else {
            lowestElevation = previousMinEle;
        }
        if (previousMaxEle == Double.NEGATIVE_INFINITY) {
            highestElevation = Double.NaN;
        } else {
            highestElevation = previousMaxEle;
        }

        lowestPoints = new ArrayList<>(lowestPointIndices.size());
        for (Point point : lowestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = elevationGridView.toLatLon(latIndex, lonIndex);
            lowestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), lowestElevation));
        }

        highestPoints = new ArrayList<>(highestPointIndices.size());
        for (Point point : highestPointIndices) {
            int latIndex = point.y;
            int lonIndex = point.x;
            LatLon latLon = elevationGridView.toLatLon(latIndex, lonIndex);
            highestPoints.add(new LatLonEle(latLon.lat(), latLon.lon(), highestElevation));
        }
    }

    /**
     * Creates a new lowest and highest points object where the lowest and highest
     * points are not derived from the view, but provided explicitly as pre-computed
     * values.
     *
     * @param elevationGridView The elevation grid view to which these lowest and
     *                          highest points belong.
     * @param lowestPoints      The list of the lowest coordinate points along with
     *                          their elevation.
     * @param highestPoints     The list of the highest coordinate points along with
     *                          their elevation.
     */
    public LowestAndHighestPoints(ElevationGridView elevationGridView, List<LatLonEle> lowestPoints,
            List<LatLonEle> highestPoints) {
        super(namer.nextName(), elevationGridView);
        this.lowestPoints = lowestPoints;
        this.highestPoints = highestPoints;
        if (lowestPoints.size() > 0) {
            this.lowestElevation = lowestPoints.get(0).ele();
        } else {
            this.lowestElevation = Double.NaN;
        }
        if (highestPoints.size() > 0) {
            this.highestElevation = highestPoints.get(0).ele();
        } else {
            this.highestElevation = Double.NaN;
        }
    }

    /**
     * Returns the list of the lowest points in the tile grid view. The list will be
     * empty if the view does not contain valid elevation data.
     *
     * @return The list with the lowest points.
     */
    public List<LatLonEle> getLowestPoints() {
        return lowestPoints;
    }

    /**
     * Returns the list of the highest points in the tile grid view. The list will
     * be empty if the view does not contain valid elevation data.
     *
     * @return The list with the highest points.
     */
    public List<LatLonEle> getHighestPoints() {
        return highestPoints;
    }

    /**
     * Returns the lowest elevation value in the tile grid view.
     * {@link SrtmTile#SRTM_DATA_VOID} will be returned if the view does not contain
     * valid elevation data.
     *
     * @return The lowest elevation value.
     */
    public double getLowestElevation() {
        return lowestElevation;
    }

    /**
     * Returns the highest elevation value in the tile grid view.
     * {@link SrtmTile#SRTM_DATA_VOID} will be returned if the view does not contain
     * valid elevation data.
     *
     * @return The highest elevation value.
     */
    public double getHighestElevation() {
        return highestElevation;
    }
}
