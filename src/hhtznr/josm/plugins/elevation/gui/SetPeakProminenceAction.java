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
 * This class implements a toolbar action that sets prominence of a peak or hill
 * based on its elevation and the elevation of a saddle selected along with the
 * peak or hill. Adds ele tags based on the elevation data to peak/hill and
 * saddle if missing.
 *
 * @author Harald Hetzner
 */
public class SetPeakProminenceAction extends JosmAction {

    private static final long serialVersionUID = 1L;

    private final ElevationDataProvider elevationDataProvider;

    /**
     * Creates a new action to be used as toolbar icon for setting the prominence
     * tag of a peak or hill based on a saddle selected as key col.
     *
     * @param elevationDataProvider The elevation data provider that provides the
     *                              elevation values.
     */
    public SetPeakProminenceAction(ElevationDataProvider elevationDataProvider) {
        super("Set peak prominence", new ImageProvider("toolbar/set_prominence.svg"),
                "Set the promience tag of a selected peak or hill based on a selected saddle which is the key col and add ele tags if missing.",
                null, true, "set-prominence-tool", false);
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
        // This tool only works on a pair of nodes
        if (selectedNodes.size() != 2)
            return;

        Node peakNode = null;
        Node saddleNode = null;

        // 5. Set or update the elevation tag
        for (Node node : selectedNodes) {
            String natural = node.get("natural");
            if (natural == null)
                return;
            if (natural.equals("peak") || natural.equals("hill"))
                peakNode = node;
            else if (natural.equals("saddle"))
                saddleNode = node;
        }

        // This tool only works on a pair of peak and saddle
        if (peakNode == null || saddleNode == null)
            return;

        // Retrieve the elevation of peak and saddle
        // Set it based on the elevation data, if unset
        int peakEle;
        try {
            peakEle = getOrSetNodeEle(peakNode);
        } catch (NumberFormatException e) {
            // Do not consider or modify peaks with unparsable ele value
            return;
        }
        int saddleEle;
        try {
            saddleEle = getOrSetNodeEle(saddleNode);
        } catch (NumberFormatException e) {
            // Do not consider or modify saddles with unparsable ele value
            return;
        }

        // Compute the prominence
        int prominence = peakEle - saddleEle;
        // Do not set an invalid prominence
        if (prominence <= 0)
            return;

        // Create a command to change the property (add or replace)
        Command prominenceCmd = new ChangePropertyCommand(peakNode, "prominence", Integer.toString(prominence));
        // Add the command to the undo-redo handler (also executes it)
        UndoRedoHandler.getInstance().add(prominenceCmd);

        // Add a note to the saddle that it is the key col of the peak
        // if the peak is named and the saddle does not have a note tag
        String peakName = peakNode.get("name");
        String saddleNote = saddleNode.get("note");
        if (peakName != null && saddleNote == null) {
            String keyColNote = "key col of " + peakName;
            Command noteCmd = new ChangePropertyCommand(saddleNode, "note", keyColNote);
            UndoRedoHandler.getInstance().add(noteCmd);
        }
    }

    private int getOrSetNodeEle(Node node) throws NumberFormatException {
        String nodeEle = node.get("ele");
        if (nodeEle != null) {
            // This may cause a NumberFormatException, if the value of ele cannot be parsed
            // to an int
            return Integer.parseInt(nodeEle);
        }
        LatLon coordinate = node.getCoor();
        LatLonEle latLonEle = elevationDataProvider.getLatLonEle(coordinate);
        int ele = (int) Math.round(latLonEle.ele());
        // Create a command to change the property (add or replace)
        Command cmd = new ChangePropertyCommand(node, "ele", Integer.toString(ele));
        // Add the command to the undo-redo handler (also executes it)
        UndoRedoHandler.getInstance().add(cmd);
        return ele;
    }
}
