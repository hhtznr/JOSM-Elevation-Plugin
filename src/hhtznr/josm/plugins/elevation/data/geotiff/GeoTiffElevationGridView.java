package hhtznr.josm.plugins.elevation.data.geotiff;

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
    public LowestAndHighestPoints getLowestAndHighestPoints() {
        return lowestAndHighestPoints;
    }
}
