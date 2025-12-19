package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

/**
 * This class implements helper methods for dealing with Osm primitives
 *
 * @author Harald Hetzner
 */
public class OsmPrimitiveUtil {

    private OsmPrimitiveUtil() {
    }

    /**
     * Adds the specified bounds to the specified data set as a closed rectangular
     * way.
     *
     * @param dataSet The data set.
     * @param bounds  The bounds.
     * @param name    The value of a name tag to add to the way. No tag is added if
     *                {@code null} or blank.
     */
    public static void addBoundsToDataSet(DataSet dataSet, Bounds bounds, String name) {
        double minLat = bounds.getMinLat();
        double minLon = bounds.getMinLon();
        double maxLat = bounds.getMaxLat();
        double maxLon = bounds.getMaxLon();
        Node nodeSW = new Node(new LatLon(minLat, minLon));
        Node nodeSE = new Node(new LatLon(minLat, maxLon));
        Node nodeNE = new Node(new LatLon(maxLat, maxLon));
        Node nodeNW = new Node(new LatLon(maxLat, minLon));
        Way way = new Way();
        way.addNode(nodeSW);
        way.addNode(nodeSE);
        way.addNode(nodeNE);
        way.addNode(nodeNW);
        // Add the last way once more to close the area
        way.addNode(nodeSW);
        if (name != null && !name.isBlank())
            way.put("name", name);
        dataSet.addPrimitive(nodeSW);
        dataSet.addPrimitive(nodeSE);
        dataSet.addPrimitive(nodeNE);
        dataSet.addPrimitive(nodeNW);
        dataSet.addPrimitive(way);
    }
}
