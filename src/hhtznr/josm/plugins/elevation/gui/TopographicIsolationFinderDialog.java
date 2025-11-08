package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.GBC;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.CoordinateUtil;
import hhtznr.josm.plugins.elevation.tools.ElevationToolListener;
import hhtznr.josm.plugins.elevation.tools.TopographicIsolationFinder;

/**
 * This class implements a dialog that enables determining the topographic
 * isolation of a peak and its isolation reference points. The reference points
 * can be added to a new data layer along with the peak to allow further
 * evaluation by the user.
 *
 * @author Harald Hetzner
 */
public class TopographicIsolationFinderDialog extends ExtendedDialog implements ElevationToolListener {

    private static final long serialVersionUID = 1L;

    private JButton buttonSetPeak;
    private JTextField textFieldPeakNodeID;
    private JTextField textFieldPeakName;
    private JTextField textFieldPeakCoord;
    private JTextField textFieldPeakEle;
    private JSpinner spinnerDistanceTolerance;
    private JSpinner spinnerSearchDistance;
    private JTextField textFieldSearchDistanceLatLon;
    private JButton buttonFind;
    private JButton buttonStop;
    private JButton buttonAddToDataLayer;
    private JTextArea textAreaFeedback;

    private final TopographicIsolationFinder isolationFinder;
    private Node peakNode = null;
    private Future<List<LatLonEle>> closestPointsFuture = null;
    private List<Node> nodes = null;
    private Bounds searchBounds = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();;

    private static enum DialogState {
        INITIAL, PEAK_NODE_SELECTED, PEAK_DEFINED, SEARCH_RUNNING, REFERENCE_POINTS_DETERMINED,
        ADDING_POINTS_TO_DATA_LAYER
    }

    /**
     * Creates a new topographic isolation finder dialog.
     *
     * @param parent                The parent component of the dialog.
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     */
    public TopographicIsolationFinderDialog(Component parent, ElevationDataProvider elevationDataProvider) {
        super(parent, "Topograhic Isolation Finder", new String[] { tr("Close") }, false, false);
        // Set the icon of the close button via the superclass method
        setButtonIcons("misc/close.svg");
        isolationFinder = new TopographicIsolationFinder(elevationDataProvider);
        isolationFinder.addElevationToolListener(this);
        build();
    }

    private void build() {
        buttonSetPeak = new JButton(new SelectPeakAction());
        textFieldPeakNodeID = new JTextField(30);
        textFieldPeakNodeID.setEditable(false);
        textFieldPeakName = new JTextField(30);
        textFieldPeakCoord = new JTextField(30);
        textFieldPeakCoord.setEditable(false);
        textFieldPeakEle = new JTextField(30);
        textFieldPeakEle.getDocument().addDocumentListener(new TextFieldElevationDocumentListener());
        textAreaFeedback = new JTextArea();
        textAreaFeedback.setColumns(40);
        textAreaFeedback.setRows(20);
        textAreaFeedback.setLineWrap(true);
        textAreaFeedback.setWrapStyleWord(true);
        textAreaFeedback.setEditable(false);

        spinnerSearchDistance = new JSpinner(new SpinnerNumberModel(10, 1, 10000, 1));
        spinnerSearchDistance.addChangeListener(e -> {
            setSearchDistanceLatLon();
        });
        textFieldSearchDistanceLatLon = new JTextField(30);
        textFieldSearchDistanceLatLon.setEditable(false);
        spinnerDistanceTolerance = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));

        buttonFind = new JButton(new FindAction());

        buttonStop = new JButton(new StopFindAction());

        buttonAddToDataLayer = new JButton(new AddToNewDataLayerAction());

        textFieldPeakName.setToolTipText(
                "Peak name taken from the node's name tag, if it exists. It is optional and only used for naming the data layer.");
        textFieldPeakEle.setToolTipText(
                "Peak elevation taken from the node's ele tag, if it exists. You can manually set it. Gratually increase it if a higher raster elevation at the peak location prevents finding the actual isolation.");
        spinnerSearchDistance.setToolTipText(
                "Search distance along Earth's surface in kilometers into each geographic direction. Search is performed in a square with the peak in its center and side length twice the distance.");
        textFieldSearchDistanceLatLon.setToolTipText(
                "The corresponding search distances and search area size in geographic latitude and longitude.");
        spinnerDistanceTolerance.setToolTipText(
                "Search distance tolerance in meters. If 0, the result will be the very closest, by 1 m higher raster point. If > 0, the result may contain further points within [closest distance, closest distance + tolerance].");

        setDialogState(DialogState.INITIAL);

        JScrollPane scrollPaneFeedback = new JScrollPane(textAreaFeedback);
        // Ensure the vertical scrollbar only appears when needed
        scrollPaneFeedback.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JLabel labelSection1 = new JLabel("1. Select a peak node from the data layer");
        labelSection1.setFont(labelSection1.getFont().deriveFont(Font.BOLD));
        JLabel labelSection2 = new JLabel("2. Define the maximum search distance and the distance tolerance");
        labelSection2.setFont(labelSection2.getFont().deriveFont(Font.BOLD));
        JLabel labelSection3 = new JLabel("3. Execute the search for isolation reference points");
        labelSection3.setFont(labelSection3.getFont().deriveFont(Font.BOLD));
        JLabel labelSection4 = new JLabel("4. Search status and other messages");
        labelSection4.setFont(labelSection4.getFont().deriveFont(Font.BOLD));
        JLabel labelSection5 = new JLabel("5. Add determined isolation reference points to a new data layer");
        labelSection5.setFont(labelSection5.getFont().deriveFont(Font.BOLD));

        JPanel pnl = new JPanel(new GridBagLayout());
        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.insets = new Insets(5, 5, 0, 0);

        // Row "Section 1: Select peak"
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection1, gc);

        // Row "Peak node ID"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Node ID:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakNodeID, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(buttonSetPeak, gc);

        // Row "Peak name"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Name:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakName, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak coordinates"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Coordinates:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakCoord, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak elevation"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Elevation:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakEle, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("m"), gc);

        // Separator before section 2
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 2: Search parameters"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection2, gc);

        // Row "Search distance (km)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Search distance:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(spinnerSearchDistance, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("km"), gc);

        // Row "Search distance (lat-lon)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldSearchDistanceLatLon, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("°"), gc);

        // Row "Distance tolerance"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Distance tolerance:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(spinnerDistanceTolerance, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("m"), gc);

        // Separator before section 3
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 3: Execute"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection3, gc);

        // Row "Find"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonFind, gc);

        // Row "Stop"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonStop, gc);

        // Separator before section 4
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 4: Status"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection4, gc);

        // Row "Feedback"
        gc.gridy++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        pnl.add(scrollPaneFeedback, gc);

        // Separator before section 5
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        gc.weighty = 0.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 5: Add to layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection5, gc);

        // Row "Add to layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonAddToDataLayer, gc);

        setContent(pnl);
    }

    private void setDialogState(DialogState state) {
        switch (state) {
        case INITIAL:
            buttonSetPeak.setEnabled(true);
            textFieldPeakNodeID.setText(null);
            textFieldPeakName.setText(null);
            textFieldPeakName.setEditable(false);
            textFieldPeakCoord.setText(null);
            textFieldPeakEle.setText(null);
            textFieldPeakEle.setEditable(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            peakNode = null;
            nodes = null;
            closestPointsFuture = null;
            searchBounds = null;
            break;
        case PEAK_NODE_SELECTED:
            buttonSetPeak.setEnabled(true);
            textFieldPeakName.setEditable(true);
            textFieldPeakEle.setEditable(true);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            nodes = null;
            closestPointsFuture = null;
            break;
        case PEAK_DEFINED:
            buttonSetPeak.setEnabled(true);
            textFieldPeakName.setEditable(true);
            textFieldPeakEle.setEditable(true);
            buttonFind.setEnabled(true);
            buttonStop.setEnabled(false);
            break;
        case SEARCH_RUNNING:
            buttonSetPeak.setEnabled(false);
            textFieldPeakName.setEditable(false);
            textFieldPeakEle.setEditable(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(true);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            nodes = null;
            closestPointsFuture = null;
            break;
        case REFERENCE_POINTS_DETERMINED:
            buttonSetPeak.setEnabled(true);
            textFieldPeakName.setEditable(true);
            textFieldPeakEle.setEditable(true);
            buttonFind.setEnabled(true);
            buttonStop.setEnabled(false);
            buttonAddToDataLayer.setEnabled(true);
            break;
        case ADDING_POINTS_TO_DATA_LAYER:
            buttonSetPeak.setEnabled(false);
            textFieldPeakName.setEditable(false);
            textFieldPeakEle.setEditable(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            buttonAddToDataLayer.setEnabled(false);
            break;
        }
    }

    private void setSearchDistanceLatLon() {
        Node peakNode = this.peakNode;
        if (peakNode == null)
            return;
        int distance = (Integer) spinnerSearchDistance.getValue();
        distance *= 1000; // convert from km to m
        LatLon peak = peakNode.getCoor();

        LatLon north = CoordinateUtil.destination(peak, distance, 0);
        LatLon south = CoordinateUtil.destination(peak, distance, 180);
        LatLon east = CoordinateUtil.destination(peak, distance, 90);
        LatLon west = CoordinateUtil.destination(peak, distance, 270);
        double minLat = Math.max(south.lat(), -90.0);
        double minLon = Math.max(-180.0, west.lon());
        double maxLat = Math.min(north.lat(), 90.0);
        double maxLon = Math.min(180.0, east.lon());
        double latSearchDistance = (maxLat - minLat) / 2;
        double lonSearchDistance = (maxLon - minLon) / 2;
        Bounds searchBounds = new Bounds(minLat, minLon, maxLat, maxLon);
        textFieldSearchDistanceLatLon.setText(String.format("lat: %.4f, lon: %.4f (area: %.4f x %.4f)",
                latSearchDistance, lonSearchDistance, latSearchDistance * 2, lonSearchDistance * 2));
        this.searchBounds = searchBounds;
    }

    private Future<List<LatLonEle>> determineReferencePoints(LatLonEle peak, Bounds searchBounds,
            double distanceTolerance) throws RejectedExecutionException {
        try {
            Callable<List<LatLonEle>> task = () -> {
                List<LatLonEle> closestPoints = isolationFinder.determineReferencePoints(peak, searchBounds,
                        distanceTolerance);
                if (closestPoints.size() > 0)
                    nodes = new ArrayList<>(closestPoints.size());

                int candidateRank = 1;
                int nCandidates = closestPoints.size();
                for (LatLonEle point : closestPoints) {
                    double lat = point.lat();
                    double lon = point.lon();
                    int ele = (int) point.ele();
                    double distance = peakNode.getCoor().greatCircleDistance((ILatLon) point);
                    textAreaFeedback.append("" + lat + ", " + lon + " (" + ele + " m) -> distance = "
                            + String.format("%.2f km", distance / 1000.0) + System.lineSeparator());
                    Node node = new Node(point);
                    node.put("name", Integer.toString(candidateRank));
                    node.put("description", "Isolation reference point candidate " + Integer.toString(candidateRank)
                            + " of " + nCandidates);
                    if (candidateRank == 1) {
                        String note = "Isolation reference point, which is closest to the peak";
                        String peakName = peakNode.get("name");
                        if (peakName != null)
                            note += " " + peakName;
                        node.put("note", note);
                    }
                    node.put("ele", Integer.toString(ele));
                    node.put("distance", Double.toString(distance));
                    nodes.add(node);
                    candidateRank++;
                }
                if (nodes.size() > 0)
                    setDialogState(DialogState.REFERENCE_POINTS_DETERMINED);
                else
                    setDialogState(DialogState.PEAK_DEFINED);
                return closestPoints;
            };
            return executor.submit(task);
        } catch (RejectedExecutionException e) {
            setDialogState(DialogState.PEAK_DEFINED);
            textAreaFeedback.setText("Search task could not be executed as thread");
            throw e;
        }
    }

    private static boolean isDouble(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            // The string does not contain a parsable integer
            return false;
        }
    }

    @Override
    public void dispose() {
        isolationFinder.removeElevationToolListener(this);
        super.dispose();
    }

    @Override
    public void status(String message) {
        textAreaFeedback.append(message + System.lineSeparator());
    }

    private class SelectPeakAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public SelectPeakAction() {
            super("Set selected peak", "cursor/modifier/select_node.svg",
                    "Add a selected node that marks the location of the peak or hill", null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            // 1. Get the active data layer
            OsmDataLayer editLayer = MainApplication.getLayerManager().getActiveDataLayer();

            if (editLayer == null) {
                // No active data layer is available
                setDialogState(DialogState.INITIAL);
                textAreaFeedback.setText("No data layer active (should not happen)");
                return;
            }

            // 2. Get the DataSet
            DataSet dataSet = editLayer.getDataSet();

            if (dataSet == null) {
                setDialogState(DialogState.INITIAL);
                textAreaFeedback.setText("Layer contains no data");
                return;
            }

            // 3. Get the overall selection
            Collection<OsmPrimitive> selection = dataSet.getSelected();

            // 4. Filter for Nodes
            List<Node> selectedNodes = new ArrayList<>();
            for (OsmPrimitive primitive : selection) {
                if (primitive instanceof Node)
                    selectedNodes.add((Node) primitive);
            }
            if (selectedNodes.size() == 0) {
                setDialogState(DialogState.INITIAL);
                textAreaFeedback.setText("No nodes selected");
                return;
            }

            if (selectedNodes.size() > 1) {
                setDialogState(DialogState.INITIAL);
                textAreaFeedback.setText("More than one node selected");
                return;
            }

            peakNode = selectedNodes.get(0);
            long nodeID = peakNode.getId();
            textFieldPeakNodeID.setText(Long.toString(nodeID));
            LatLon latLon = peakNode.getCoor();
            textFieldPeakCoord.setText(Double.toString(latLon.lat()) + ", " + Double.toString(latLon.lon()));
            String name = peakNode.get("name");
            textFieldPeakName.setText(name);
            String ele = peakNode.get("ele");
            textFieldPeakEle.setText(ele);
            setSearchDistanceLatLon();
        }
    }

    private class TextFieldElevationDocumentListener implements DocumentListener {

        // Called when text is inserted
        @Override
        public void insertUpdate(DocumentEvent e) {
            validateInput();
        }

        // Called when text is removed
        @Override
        public void removeUpdate(DocumentEvent e) {
            validateInput();
        }

        // Called when attributes change (ignored for this purpose)
        @Override
        public void changedUpdate(DocumentEvent e) {
            // Not used for plain text fields
        }

        private void validateInput() {
            String text = textFieldPeakEle.getText();
            // Enable the isolation finder if the elevation value is a double and a
            // node providing the coordinates of the peak is selected
            if (isDouble(text) && peakNode != null)
                setDialogState(DialogState.PEAK_DEFINED);
            else
                setDialogState(DialogState.PEAK_NODE_SELECTED);
        }
    }

    private class FindAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public FindAction() {
            super("Determine topographic isolation", "dialogs/topographic_isolation.svg", null, null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            setDialogState(DialogState.SEARCH_RUNNING);
            String eleText = textFieldPeakEle.getText();
            short ele;
            try {
                ele = (short) Double.parseDouble(eleText);
            } catch (NumberFormatException e) {
                setDialogState(DialogState.PEAK_DEFINED);
                textAreaFeedback.append(
                        "Cannot convert elevation to number, provided value: " + eleText + System.lineSeparator());
                return;
            }
            textAreaFeedback.append("Determination of elevation reference points for peak:" + System.lineSeparator());
            String peakNodeID = textFieldPeakNodeID.getText();
            textAreaFeedback.append("Node ID: " + peakNodeID + System.lineSeparator());
            String peakName = textFieldPeakName.getText();
            if (!peakName.isBlank())
                textAreaFeedback.append("Name: " + peakName + System.lineSeparator());
            String peakCoord = textFieldPeakCoord.getText();
            textAreaFeedback.append("Coordinates: " + peakCoord + System.lineSeparator());
            textAreaFeedback.append("Elevation: " + ele + " m" + System.lineSeparator());

            LatLonEle peak = new LatLonEle(peakNode.getCoor(), ele);
            int distanceTolerance = (Integer) spinnerDistanceTolerance.getValue();
            try {
                closestPointsFuture = determineReferencePoints(peak, searchBounds, distanceTolerance);
            } catch (RejectedExecutionException e) {
                textAreaFeedback
                        .append("Determination of closest points rejected by thread executor" + System.lineSeparator());
            }
        }
    }

    private class StopFindAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public StopFindAction() {
            super("Stop find task", "cancel", null, null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (closestPointsFuture != null && !closestPointsFuture.isCancelled()) {
                boolean canceled = closestPointsFuture.cancel(true);
                if (canceled)
                    setDialogState(DialogState.PEAK_DEFINED);
                else
                    textAreaFeedback.append(
                            "Failed to cancel the background task that determines the isolation reference points"
                                    + System.lineSeparator());
            }
        }
    }

    private class AddToNewDataLayerAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public AddToNewDataLayerAction() {
            super("Add to new data layer", "addnode.svg",
                    "Add the determined isolation reference points and a copy of the peak node to a new OSM data layer",
                    null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            setDialogState(DialogState.ADDING_POINTS_TO_DATA_LAYER);

            if (nodes == null) {
                setDialogState(DialogState.PEAK_DEFINED);
                textAreaFeedback.setText("No nodes available to add them to the data layer (should not happen)");
                return;
            }

            DataSet ds = new DataSet();
            ds.addPrimitive(new Node(peakNode));
            for (Node node : nodes)
                ds.addPrimitive(new Node(node));
            String layerName = "Isolation reference points";
            String peakName = textFieldPeakName.getText();
            if (peakName != null && !peakName.isBlank())
                layerName += " of " + peakName;
            OsmDataLayer dataLayer = new OsmDataLayer(ds, layerName, null);
            MainApplication.getLayerManager().addLayer(dataLayer);
            MainApplication.getLayerManager().setActiveLayer(dataLayer);

            ds.setSelected(ds.getNodes());

            MapFrame mapFrame = MainApplication.getMap();
            if (mapFrame != null) {
                MapView mapView = mapFrame.mapView;
                double minLat = Double.MAX_VALUE;
                double minLon = Double.MAX_VALUE;
                double maxLat = Double.MIN_VALUE;
                double maxLon = Double.MIN_VALUE;
                for (Node node : ds.getNodes()) {
                    double lat = node.getCoor().lat();
                    double lon = node.getCoor().lon();
                    minLat = Math.min(minLat, lat);
                    minLon = Math.min(minLon, lon);
                    maxLat = Math.max(maxLat, lat);
                    maxLon = Math.max(maxLon, lon);
                }
                if (minLat == Double.MAX_VALUE) {
                    setDialogState(DialogState.PEAK_DEFINED);
                    textAreaFeedback
                            .setText("Could not determine the bounds containing the nodes. This should not happen.");
                    return;
                }
                // Increase the size of the bounds by 10 %
                // Prevents points being located at the edges of the map
                double extraSpaceLat = (maxLat - minLat) * 0.05;
                double extraSpaceLon = (maxLon - minLon) * 0.05;
                minLat = Math.max(minLat - extraSpaceLat, -90);
                minLon = Math.max(minLon - extraSpaceLon, 0);
                maxLat = Math.min(maxLat + extraSpaceLat, 90);
                maxLon = Math.min(maxLon + extraSpaceLon, 180);
                Bounds bounds = new Bounds(minLat, minLon, maxLat, maxLon);
                mapView.zoomTo(bounds);
            }

            setDialogState(DialogState.REFERENCE_POINTS_DETERMINED);
        }
    }
}
