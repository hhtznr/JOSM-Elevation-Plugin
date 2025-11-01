package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
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

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

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
    private JTextField textFieldPeakName;
    private JTextField textFieldPeakCoord;
    private JTextField textFieldPeakEle;
    private JSpinner spinnerDistanceTolerance;
    private JSpinner spinnerSearchDistance;
    private JButton buttonFind;
    private JButton buttonStop;
    private JButton buttonAddToDataLayer;
    private JTextArea textAreaFeedback;

    private TopographicIsolationFinder isolationFinder;
    private Node peakNode = null;
    private Future<List<LatLonEle>> closestPoints = null;
    private List<Node> nodes = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();;

    /**
     * Creates a new topographic isolation finder dialog.
     *
     * @param parent                The parent component of the dialog.
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     */
    public TopographicIsolationFinderDialog(Component parent, ElevationDataProvider elevationDataProvider) {
        super(parent, "Topograhic Isolation Finder", new String[] { tr("Clear"), tr("Close") }, false, false);
        isolationFinder = new TopographicIsolationFinder(elevationDataProvider);
        isolationFinder.addElevationToolListener(this);
        build();
    }

    private void build() {

        buttonSetPeak = new JButton("< Set peak");
        buttonSetPeak.addActionListener(this::addPeak);

        textFieldPeakName = new JTextField(30);
        textFieldPeakCoord = new JTextField(30);
        textFieldPeakCoord.setEditable(false);
        textFieldPeakEle = new JTextField(30);
        textAreaFeedback = new JTextArea();
        textAreaFeedback.setColumns(40);
        textAreaFeedback.setRows(20);
        textAreaFeedback.setLineWrap(true);
        textAreaFeedback.setWrapStyleWord(true);
        textAreaFeedback.setEditable(false);

        spinnerDistanceTolerance = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));
        spinnerSearchDistance = new JSpinner(new SpinnerNumberModel(0.1, 0.1, 2.5, 0.1));

        buttonFind = new JButton("Determine topographic isolation");
        buttonFind.addActionListener(this::find);
        buttonFind.setEnabled(false);

        buttonStop = new JButton("Stop find task");
        buttonStop.addActionListener(this::stopFind);

        buttonAddToDataLayer = new JButton("Add to new data layer");
        buttonAddToDataLayer.addActionListener(this::addToDataLayer);
        buttonAddToDataLayer.setEnabled(false);

        JScrollPane scrollPaneFeedback = new JScrollPane(textAreaFeedback);
        // Optional: Ensure the vertical scrollbar only appears when needed
        scrollPaneFeedback.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JPanel pnl = new JPanel(new GridBagLayout());

        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.insets = new Insets(5, 5, 0, 0);

        // Row "select peak"
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("Select a peak node from the data layer"), gc);

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
        pnl.add(buttonSetPeak, gc);

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

        // Row "Search"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("Define the search distance tolerance and execute search"), gc);

        // Row "Search distance"
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

        // Row "Find"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonFind, gc);

        // TODO: Integrate with button "find"
        // Row "Stop"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonStop, gc);

        // Row "Status"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("Status"), gc);

        // Row "Feedback"
        gc.gridy++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        pnl.add(scrollPaneFeedback, gc);

        // Row "Add to layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(buttonAddToDataLayer, gc);

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());

        setContent(pnl);
    }

    private void addPeak(ActionEvent event) {
        System.out.println("ADD PEAK");
        textFieldPeakName.setText("");
        textFieldPeakCoord.setText("");
        textFieldPeakEle.setText("");
        textAreaFeedback.setText("");

        // 1. Get the active data layer
        OsmDataLayer editLayer = MainApplication.getLayerManager().getActiveDataLayer();

        if (editLayer == null) {
            // No active data layer is available
            buttonFind.setEnabled(false);
            return;
        }

        // 2. Get the DataSet
        DataSet dataSet = editLayer.getDataSet();

        if (dataSet == null) {
            textAreaFeedback.setText("Layer contains no data");
            buttonFind.setEnabled(false);
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
            textAreaFeedback.setText("No nodes selected");
            buttonFind.setEnabled(false);
            return;
        }

        if (selectedNodes.size() > 1) {
            textAreaFeedback.setText("More than one node selected");
            buttonFind.setEnabled(false);
            return;
        }

        peakNode = selectedNodes.get(0);
        LatLon latLon = peakNode.getCoor();
        textFieldPeakCoord.setText("" + latLon.lat() + ", " + latLon.lon());
        String name = peakNode.get("name");
        if (name != null)
            textFieldPeakName.setText(name);
        String ele = peakNode.get("ele");
        if (isDouble(ele)) {
            textFieldPeakEle.setText(ele);
            buttonFind.setEnabled(true);
        } else {
            buttonFind.setEnabled(false);
        }
    }

    private void find(ActionEvent event) {
        buttonFind.setEnabled(false);
        buttonStop.setEnabled(true);
        buttonAddToDataLayer.setEnabled(false);
        textAreaFeedback.setText("");
        double ele;
        try {
            ele = Integer.parseInt(textFieldPeakEle.getText());
        } catch (NumberFormatException e) {
            textAreaFeedback.append("Determination of closest points interrupted" + System.lineSeparator());
            buttonFind.setEnabled(true);
            return;
        }
        LatLonEle peak = new LatLonEle(peakNode.getCoor(), ele);
        double searchDistance = (Double) spinnerSearchDistance.getValue();
        double minLat = peak.lat() - searchDistance;
        double minLon = peak.lon() - searchDistance;
        double maxLat = peak.lat() + searchDistance;
        double maxLon = peak.lon() + searchDistance;
        Bounds searchBounds = new Bounds(minLat, minLon, maxLat, maxLon);
        int distanceTolerance = (Integer) spinnerDistanceTolerance.getValue();
        closestPoints = determineReferencePoints(peak, searchBounds, distanceTolerance);
        buttonAddToDataLayer.setEnabled(true);
        buttonStop.setEnabled(false);
    }

    private void stopFind(ActionEvent event) {
        if (closestPoints != null && !closestPoints.isCancelled()) {
            boolean canceled = closestPoints.cancel(true);
            if (canceled) {
                buttonStop.setEnabled(false);
                buttonAddToDataLayer.setEnabled(false);
                buttonFind.setEnabled(true);
            }

        }
    }

    private void addToDataLayer(ActionEvent event) {
        buttonAddToDataLayer.setEnabled(false);
        /*
         * OsmDataLayer dataLayer =
         * MainApplication.getLayerManager().getActiveDataLayer(); if (dataLayer ==
         * null) { System.out.println("No active data layer.");
         * buttonAddToDataLayer.setEnabled(true); return; }
         */
        if (nodes == null) {
            buttonAddToDataLayer.setEnabled(true);
            return;
        }
        nodes.add(new Node(peakNode));
        // DataSet ds = dataLayer.getDataSet();
        DataSet ds = new DataSet();
        for (Node node : nodes) {
            ds.addPrimitive(node);
        }
        String layerName = "Isolation reference points";
        String peakName = textFieldPeakName.getText();
        if (peakName != null && !peakName.isBlank())
            layerName += " of " + peakName;
        OsmDataLayer dataLayer = new OsmDataLayer(ds, layerName, null);
        MainApplication.getLayerManager().addLayer(dataLayer);
        MainApplication.getLayerManager().setActiveLayer(dataLayer);

        /*
         * for (Node node : nodes) { Command addCommand = new AddCommand(ds, node);
         * UndoRedoHandler.getInstance().add(addCommand); }
         */

        // layer.getHistory().add(addCommand);

        ds.setSelected(nodes);

        MapFrame mapFrame = MainApplication.getMap();
        if (mapFrame != null) {
            MapView mapView = mapFrame.mapView;
            double minLat = Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE;
            double maxLat = Double.MIN_VALUE;
            double maxLon = Double.MIN_VALUE;
            for (Node node : nodes) {
                double lat = node.getCoor().lat();
                double lon = node.getCoor().lon();
                minLat = Math.min(minLat, lat);
                minLon = Math.min(minLon, lon);
                maxLat = Math.max(maxLat, lat);
                maxLon = Math.max(maxLon, lon);
            }
            if (minLat == Double.MAX_VALUE) {
                buttonAddToDataLayer.setEnabled(true);
                textAreaFeedback.append("Could not determine the bounds containing the nodes. This should not happen."
                        + System.lineSeparator());
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

        buttonAddToDataLayer.setEnabled(true);
    }

    private Future<List<LatLonEle>> determineReferencePoints(LatLonEle peak, Bounds searchBounds,
            double distanceTolerance) throws RejectedExecutionException {
        try {
            Callable<List<LatLonEle>> task = () -> {
                List<LatLonEle> closestPoints = isolationFinder.determineReferencePoints(peak, searchBounds,
                        distanceTolerance);
                if (closestPoints.size() > 0)
                    nodes = new ArrayList<>(closestPoints.size());

                SwingUtilities.invokeLater(() -> {
                    int rank = 1;
                    for (LatLonEle point : closestPoints) {
                        double lat = point.lat();
                        double lon = point.lon();
                        double ele = point.ele();
                        double distance = peakNode.getCoor().greatCircleDistance((ILatLon) point);
                        textAreaFeedback.append("" + lat + ", " + lon + " (" + ele + " m) -> distance = "
                                + String.format("%.2f km", distance / 1000.0) + System.lineSeparator());
                        Node node = new Node(point);
                        node.put("ele", Double.toString(ele));
                        node.put("distance", Double.toString(distance));
                        node.put("distance:rank", Integer.toString(rank));
                        nodes.add(node);
                        rank++;
                    }
                });
                return closestPoints;
            };
            return executor.submit(task);
        } catch (RejectedExecutionException e) {
            buttonFind.setEnabled(true);
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
}
