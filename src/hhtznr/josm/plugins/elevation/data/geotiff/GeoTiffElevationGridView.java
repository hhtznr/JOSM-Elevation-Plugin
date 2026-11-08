package hhtznr.josm.plugins.elevation.data.geotiff;

import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.ElevationGrid;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.gui.paintables.LowestAndHighestPoints;

public class GeoTiffElevationGridView implements ElevationGridView {

    private final GeoTiffElevationGrid elevationGrid;
    private final int gridRasterLatIndexSouth;
    private final int gridRasterLonIndexWest;
    private final int height;
    private final int width;
    private final Bounds bounds;
    private final LowestAndHighestPoints lowestAndHighestPoints;

    protected GeoTiffElevationGridView(GeoTiffElevationGrid elevationGrid, int latIndexSouth, int latIndexNorth,
            int lonIndexWest, int lonIndexEast, Bounds bounds) {
        this.elevationGrid = elevationGrid;
        gridRasterLatIndexSouth = latIndexSouth;
        gridRasterLonIndexWest = lonIndexWest;
        height = latIndexNorth - latIndexSouth + 1;
        width = lonIndexEast - lonIndexWest + 1;
        this.bounds = bounds;

        lowestAndHighestPoints = new LowestAndHighestPoints(this);
    }

    @Override
    public ElevationGrid getElevationGrid() {
        return elevationGrid;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public double getElevation(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.getElevation(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public boolean isNoDataValue(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.isNoDataValue(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public boolean isNoDataValue(double ele) {
        return elevationGrid.isNoDataValue(ele);
    }

    @Override
    public LatLon toLatLon(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.toLatLon(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public LatLonEle toLatLonEle(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return elevationGrid.toLatLonEle(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public Bounds getBounds() {
        return bounds;
    }

    @Override
    public short[] getIsovalues(int isostep, int lowerCutoffElevation, int upperCutoffElevation) {
        List<LatLonEle> lowestPoints = lowestAndHighestPoints.getLowestPoints();
        double lowestElevation = Double.NaN;
        if (lowestPoints.size() > 0) {
            lowestElevation = lowestPoints.get(0).ele();
        }
        if (elevationGrid.isNoDataValue(lowestElevation)) {
            return null;
        }
        List<LatLonEle> highestPoints = lowestAndHighestPoints.getHighestPoints();
        double highestElevation = Double.NaN;
        if (highestPoints.size() > 0) {
            highestElevation = highestPoints.get(0).ele();
        }
        if (elevationGrid.isNoDataValue(highestElevation)) {
            return null;
        }

        // Apply the lower cutoff elevation value, if it is greater than the minimum
        // elevation within the grid
        double minEle = Math.max(lowestElevation, lowerCutoffElevation);
        // Apply the upper cutoff elevation value, if it is smaller than the maximum
        // elevation within the grid
        double maxEle = Math.min(highestElevation, upperCutoffElevation);

        if (minEle > maxEle) {
            return new short[0]; // The cutoffs exclude the view's elevation range.
        }

        // Determine the list of isovalues, i.e. the elevation levels for which contour
        // lines should be computed
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
            return new short[0];
        }
        int nSteps = (maxIsovalue - minIsovalue) / isostep + 1;
        short[] isovalues = new short[nSteps];
        for (int i = 0; i < nSteps; i++) {
            isovalues[i] = (short) (minIsovalue + i * isostep);
        }

        return isovalues;
    }

    @Override
    public LowestAndHighestPoints getLowestAndHighestPoints() {
        return lowestAndHighestPoints;
    }
}
