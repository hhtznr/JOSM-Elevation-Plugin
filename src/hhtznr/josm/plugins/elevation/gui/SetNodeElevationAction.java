package hhtznr.josm.plugins.elevation.gui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.ImageProvider;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;

/**
 * This class implements a toolbar action that sets the elevation of one or more
 * selected nodes base on the raster elevation.
 *
 * @author Harald Hetzner
 */
public class SetNodeElevationAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationDataProvider elevationDataProvider;

    /**
     * Creates a new action to be used as toolbar icon for setting the elevation of
     * selected nodes.
     *
     * @param elevationDataProvider The elevation data provider that provides the
     *                              elevation values.
     */
    public SetNodeElevationAction(ElevationDataProvider elevationDataProvider) {
        super("Set node elevation", new ImageProvider("toolbar/set_elevation.svg"),
                "Set the ele tag of one or more selected nodes based on the raster elevation.", null, true,
                "set-elevation-tool", false);
        this.elevationDataProvider = elevationDataProvider;
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        // 1. Get the active data layer
        OsmDataLayer editLayer = MainApplication.getLayerManager().getActiveDataLayer();
        if (editLayer == null)
            return;
        // 2. Get the DataSet
        DataSet dataSet = editLayer.getDataSet();
        // 3. Get the overall selection
        Collection<OsmPrimitive> selection = dataSet.getSelected();

        // 4. Filter for Nodes
        List<Node> selectedNodes = new ArrayList<>();
        for (OsmPrimitive primitive : selection) {
            if (primitive instanceof Node)
                selectedNodes.add((Node) primitive);
        }
        // 5. Set or update the elevation tag
        for (Node node : selectedNodes) {
            LatLon coordinate = node.getCoor();
            LatLonEle latLonEle = elevationDataProvider.getLatLonEle(coordinate);
            if (latLonEle.hasValidEle()) {
                int ele = (int) Math.round(latLonEle.ele());
                // Create a command to change the property (add or replace)
                Command cmd = new ChangePropertyCommand(node, "ele", Integer.toString(ele));
                // Add the command to the undo-redo handler (also executes it)
                UndoRedoHandler.getInstance().add(cmd);
            }
        }
    }
}
