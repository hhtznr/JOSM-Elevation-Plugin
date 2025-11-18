package hhtznr.josm.plugins.elevation.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.tools.ElevationToolListener;
import hhtznr.josm.plugins.elevation.tools.KeyColFinder;

/**
 * This class implements a dialog that enables determining the key col of a peak
 * based on an assumed line parent peak.
 *
 * @author Harald Hetzner
 */
public class KeyColFinderDialog extends ExtendedDialog implements ElevationToolListener {

    private static final long serialVersionUID = 1L;

    private JButton buttonSetPeakA;
    private JTextField textFieldPeakANodeID;
    private JTextField textFieldPeakAName;
    private JTextField textFieldPeakACoord;
    private JTextField textFieldPeakAEle;
    private JButton buttonSetPeakB;
    private JTextField textFieldPeakBNodeID;
    private JTextField textFieldPeakBName;
    private JTextField textFieldPeakBCoord;
    private JTextField textFieldPeakBEle;
    private JSpinner spinnerSearchAreaExpansionLat;
    private JSpinner spinnerSearchAreaExpansionLon;
    private JTextField textFieldSearchAreaLatLon;
    private JTextField textFieldSearchAreaSize;
    private JosmComboBox<KeyColFinder.UnionFindNeighbors> comboBoxUnionFindNeighbors;
    private JButton buttonFind;
    private JButton buttonStop;
    private JButton buttonAddToDataLayer;
    private JTextArea textAreaFeedback;

    private final ElevationDataProvider elevationDataProvider;
    private final KeyColFinder keyColFinder;
    private Node peakANode = null;
    private Node peakBNode = null;
    private Future<LatLonEle> keyColFuture = null;
    private Node keyColNode = null;
    private Bounds searchBounds = null;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();;

    private static enum DialogState {
        INITIAL, PEAK_NODES_SELECTED, PEAKS_DEFINED, SEARCH_RUNNING, KEY_COL_DETERMINED, ADDING_NODES_TO_DATA_LAYER
    }

    private static final String DISCLAIMER;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<body style='width:700px;'>");
        sb.append(
                "<p>This tool can be CPU- and memory-intensive, especially when applied to peaks that are far apart.</p>");
        sb.append("<p>In extreme cases, it may cause an out-of-memory error. ");
        sb.append("It is strongly recommended to save or upload your current map work before using this tool.</p>");
        sb.append("<p>The tool computes the lowest col on the highest path connecting the two selected peaks. ");
        sb.append("This col corresponds to the true <a href=\"https://en.wikipedia.org/wiki/Key_col\">key col</a> ");
        sb.append(
                "only if you have selected the true <a href=\"https://en.wikipedia.org/wiki/Line_parent\">line parent</a> as the reference point, or a node along the path to it beyond the key col.</p>");
        sb.append(
                "<p>If you are unsure about the true line parent, compute the key col for multiple candidate points and select the lowest col among them.</p>");
        sb.append("</body>");
        sb.append("</html>");
        DISCLAIMER = sb.toString();
    }

    /**
     * Creates a new key col finder dialog.
     *
     * @param parent                The parent component of the dialog.
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     */
    public KeyColFinderDialog(Component parent, ElevationDataProvider elevationDataProvider) {
        super(parent, "Key Col Finder", new String[] { tr("Close") }, false, false);
        this.elevationDataProvider = elevationDataProvider;
        // Set the icon of the close button via the superclass method
        setButtonIcons("misc/close.svg");
        keyColFinder = new KeyColFinder(elevationDataProvider);
        keyColFinder.addElevationToolListener(this);
        build();
    }

    private void build() {
        JEditorPane editorPaneDisclaimer = new JEditorPane("text/html", DISCLAIMER);
        editorPaneDisclaimer.setEditable(false);
        editorPaneDisclaimer.setOpaque(false);
        editorPaneDisclaimer.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (URISyntaxException | IOException ex) {
                    Logging.warn("Elevation: Cannot browse hyperlink: " + ex.toString());
                }
            }
        });
        editorPaneDisclaimer.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane scrollPaneDisclaimer = new JScrollPane(editorPaneDisclaimer);
        scrollPaneDisclaimer.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPaneDisclaimer.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        buttonSetPeakA = new JButton(new SelectPeakAction(SelectPeakAction.PEAK_A));
        textFieldPeakANodeID = new JTextField(30);
        textFieldPeakANodeID.setEditable(false);
        textFieldPeakAName = new JTextField(0);
        textFieldPeakACoord = new JTextField(30);
        textFieldPeakACoord.setEditable(false);
        textFieldPeakAEle = new JTextField(30);
        textFieldPeakAEle.setEditable(false);
        buttonSetPeakB = new JButton(new SelectPeakAction(SelectPeakAction.PEAK_B));
        textFieldPeakBNodeID = new JTextField(30);
        textFieldPeakBNodeID.setEditable(false);
        textFieldPeakBName = new JTextField(30);
        textFieldPeakBCoord = new JTextField(30);
        textFieldPeakBCoord.setEditable(false);
        textFieldPeakBEle = new JTextField(30);
        textFieldPeakBEle.setEditable(false);

        textAreaFeedback = new JTextArea();
        textAreaFeedback.setColumns(40);
        textAreaFeedback.setRows(20);
        textAreaFeedback.setLineWrap(true);
        textAreaFeedback.setWrapStyleWord(true);
        textAreaFeedback.setEditable(false);

        spinnerSearchAreaExpansionLat = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 5.0, 0.01));
        spinnerSearchAreaExpansionLat.addChangeListener(e -> {
            setSearchAreaLatLon();
        });
        spinnerSearchAreaExpansionLon = new JSpinner(new SpinnerNumberModel(0.1, 0.0, 5.0, 0.01));
        spinnerSearchAreaExpansionLon.addChangeListener(e -> {
            setSearchAreaLatLon();
        });
        textFieldSearchAreaLatLon = new JTextField(30);
        textFieldSearchAreaLatLon.setEditable(false);
        textFieldSearchAreaSize = new JTextField(30);
        textFieldSearchAreaSize.setEditable(false);

        comboBoxUnionFindNeighbors = new JosmComboBox<>(new KeyColFinder.UnionFindNeighbors[] {
                KeyColFinder.UnionFindNeighbors.FOUR, KeyColFinder.UnionFindNeighbors.EIGHT });
        comboBoxUnionFindNeighbors.setSelectedItem(KeyColFinder.UnionFindNeighbors.FOUR);
        buttonFind = new JButton(new FindAction());
        buttonStop = new JButton(new StopFindAction());

        buttonAddToDataLayer = new JButton(new AddToNewDataLayerAction());

        textFieldPeakAName.setToolTipText(
                "Peak name taken from the node's name tag, if present. It is optional and only used for naming the data layer.");
        textFieldPeakAEle.setToolTipText(
                "Peak elevation is obtained from the node’s ele tag, if present, and from the elevation raster at the peak’s coordinates. The elevation is shown for informational purposes only.");
        textFieldPeakBName.setToolTipText(
                "Peak name taken from the node's name tag, if present. It is optional and only used for naming the data layer.");
        textFieldPeakBEle.setToolTipText(
                "Peak elevation is obtained from the node’s ele tag, if present, and from the elevation raster at the peak’s coordinates. The elevation is shown for informational purposes only.");
        spinnerSearchAreaExpansionLat.setToolTipText(
                "Arc seconds by which to expand the search area north and south beyond the peaks’ latitude range. Without expansion, the peaks would lie at the top and bottom of the search area, which may prevent finding the key col.");
        spinnerSearchAreaExpansionLon.setToolTipText(
                "Arc seconds by which to expand the search area east and west beyond the peaks’ longitude range. Without expansion, the peaks would lie at the left and right of the search area, which may prevent finding the key col.");
        textFieldSearchAreaLatLon.setToolTipText("The coordinates of the corners of the search area.");
        textFieldSearchAreaSize.setToolTipText("The size of the search area in latitude and longitude.");
        comboBoxUnionFindNeighbors.setToolTipText(
                "Four neighbors is faster. Eight neighbors results in higher accuracy of the key col location.");

        setDialogState(DialogState.INITIAL);

        JScrollPane scrollPaneFeedback = new JScrollPane(textAreaFeedback);
        // Ensure the vertical scrollbar only appears when needed
        scrollPaneFeedback.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        JLabel labelDisclaimer = new JLabel("Disclaimer");
        labelDisclaimer.setFont(labelDisclaimer.getFont().deriveFont(Font.BOLD));
        JLabel labelSection1a = new JLabel("1a. Select a peak node from the data layer");
        labelSection1a.setFont(labelSection1a.getFont().deriveFont(Font.BOLD));
        JLabel labelSection1b = new JLabel("1b. Select a parent peak node from the data layer");
        labelSection1b.setFont(labelSection1b.getFont().deriveFont(Font.BOLD));
        JLabel labelSection2 = new JLabel("2. Define the search area");
        labelSection2.setFont(labelSection2.getFont().deriveFont(Font.BOLD));
        JLabel labelSection3 = new JLabel("3. Execute the search for the key col");
        labelSection3.setFont(labelSection3.getFont().deriveFont(Font.BOLD));
        JLabel labelSection4 = new JLabel("4. Search status and other messages");
        labelSection4.setFont(labelSection4.getFont().deriveFont(Font.BOLD));
        JLabel labelSection5 = new JLabel("5. Add determined key col to a new data layer");
        labelSection5.setFont(labelSection5.getFont().deriveFont(Font.BOLD));

        JPanel pnl = new JPanel(new GridBagLayout());
        GBC gc = GBC.std();

        gc.gridy = 0;
        gc.gridx = 0;
        gc.insets = new Insets(5, 5, 0, 0);

        // Section "Disclaimer"
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelDisclaimer, gc);

        // Row "Disclaimer"
        gc.gridy++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        gc.weighty = 0.0;
        pnl.add(editorPaneDisclaimer, gc);
        // pnl.add(new JLabel("Placeholder"), gc);

        // Separator before section 1a
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 1a: Select peak A"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection1a, gc);

        // Row "Peak A node ID"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Node ID:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakANodeID, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(buttonSetPeakA, gc);

        // Row "Peak A name"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Name:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakAName, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak A coordinates"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Coordinates:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakACoord, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak A elevation"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Elevation:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakAEle, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("m"), gc);

        // Separator before section 1b
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Section 1b: Select peak B"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSection1b, gc);

        // Row "Peak B node ID"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Node ID:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakBNodeID, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(buttonSetPeakB, gc);

        // Row "Peak B name"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Name:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakBName, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak B coordinates"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Coordinates:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakBCoord, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        // Row "Peak B elevation"
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Elevation:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldPeakBEle, gc);

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

        // Row "Search area expansion in latitude (°)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Search area expansion (lat):"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(spinnerSearchAreaExpansionLat, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("°"), gc);

        // Row "Search area expansion in longitude (°)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Search area expansion (lon):"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(spinnerSearchAreaExpansionLon, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("°"), gc);

        // Row "Search area bounds (lat-lon)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Search area bounds:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldSearchAreaLatLon, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("°"), gc);

        // Row "Search area size (lat-lon)"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Search area size:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(textFieldSearchAreaSize, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 0.0;
        pnl.add(new JLabel("°"), gc);

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

        // Row "Union-find neighbors"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Union-find:"), gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(comboBoxUnionFindNeighbors, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

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
            buttonSetPeakA.setEnabled(true);
            textFieldPeakANodeID.setText(null);
            textFieldPeakAName.setText(null);
            textFieldPeakAName.setEditable(false);
            textFieldPeakACoord.setText(null);
            textFieldPeakAEle.setText(null);
            buttonSetPeakB.setEnabled(true);
            textFieldPeakBNodeID.setText(null);
            textFieldPeakBName.setText(null);
            textFieldPeakBName.setEditable(false);
            textFieldPeakBCoord.setText(null);
            textFieldPeakBEle.setText(null);
            textFieldSearchAreaLatLon.setText(null);
            textFieldSearchAreaSize.setText(null);
            comboBoxUnionFindNeighbors.setEnabled(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            peakANode = null;
            peakBNode = null;
            keyColNode = null;
            keyColFuture = null;
            searchBounds = null;
            break;
        case PEAK_NODES_SELECTED:
            buttonSetPeakA.setEnabled(true);
            textFieldPeakAName.setEditable(true);
            buttonSetPeakB.setEnabled(true);
            textFieldPeakBName.setEditable(true);
            comboBoxUnionFindNeighbors.setEnabled(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            keyColNode = null;
            keyColFuture = null;
            break;
        case PEAKS_DEFINED:
            buttonSetPeakA.setEnabled(true);
            textFieldPeakAName.setEditable(true);
            buttonSetPeakB.setEnabled(true);
            textFieldPeakBName.setEditable(true);
            comboBoxUnionFindNeighbors.setEnabled(true);
            buttonFind.setEnabled(true);
            buttonStop.setEnabled(false);
            break;
        case SEARCH_RUNNING:
            buttonSetPeakA.setEnabled(false);
            textFieldPeakAName.setEditable(false);
            buttonSetPeakB.setEnabled(false);
            textFieldPeakBName.setEditable(false);
            comboBoxUnionFindNeighbors.setEnabled(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(true);
            textAreaFeedback.setText(null);
            buttonAddToDataLayer.setEnabled(false);
            keyColNode = null;
            keyColFuture = null;
            break;
        case KEY_COL_DETERMINED:
            buttonSetPeakA.setEnabled(true);
            textFieldPeakAName.setEditable(true);
            buttonSetPeakB.setEnabled(true);
            textFieldPeakBName.setEditable(true);
            comboBoxUnionFindNeighbors.setEnabled(true);
            buttonFind.setEnabled(true);
            buttonStop.setEnabled(false);
            buttonAddToDataLayer.setEnabled(true);
            break;
        case ADDING_NODES_TO_DATA_LAYER:
            buttonSetPeakA.setEnabled(false);
            textFieldPeakAName.setEditable(false);
            buttonSetPeakB.setEnabled(false);
            textFieldPeakBName.setEditable(false);
            comboBoxUnionFindNeighbors.setEnabled(false);
            buttonFind.setEnabled(false);
            buttonStop.setEnabled(false);
            buttonAddToDataLayer.setEnabled(false);
            break;
        }
    }

    private void setSearchAreaLatLon() {
        Node peakANode = this.peakANode;
        Node peakBNode = this.peakBNode;
        if (peakANode == null || peakBNode == null)
            return;

        double searchAreaExpansionLat = (Double) spinnerSearchAreaExpansionLat.getValue();
        double searchAreaExpansionLon = (Double) spinnerSearchAreaExpansionLon.getValue();
        LatLon peakA = peakANode.getCoor();
        LatLon peakB = peakBNode.getCoor();

        double minLat = Math.min(peakA.lat(), peakB.lat());
        double minLon = Math.min(peakA.lon(), peakB.lon());
        double maxLat = Math.max(peakA.lat(), peakB.lat());
        double maxLon = Math.max(peakA.lon(), peakB.lon());
        minLat -= searchAreaExpansionLat;
        minLon -= searchAreaExpansionLon;
        maxLat += searchAreaExpansionLat;
        maxLon += searchAreaExpansionLon;
        minLat = Math.max(minLat, -90.0);
        minLon = Math.max(minLon, -180.0);
        maxLat = Math.min(maxLat, 90.0);
        maxLon = Math.min(maxLon, 180.0);
        Bounds searchBounds = new Bounds(minLat, minLon, maxLat, maxLon);

        textFieldSearchAreaLatLon.setText(String.format("SW: %.4f, %.4f - NE: %.4f, %.4f", searchBounds.getMinLat(),
                searchBounds.getMinLon(), searchBounds.getMaxLat(), searchBounds.getMaxLon()));
        textFieldSearchAreaSize
                .setText(String.format("lat x lon: %.4f x %.4f", searchBounds.getHeight(), searchBounds.getWidth()));
        this.searchBounds = searchBounds;
    }

    private Future<LatLonEle> determineKeyCol(LatLonEle peakA, LatLonEle peakB, Bounds searchBounds,
            KeyColFinder.UnionFindNeighbors unionFindNeighbors) throws RejectedExecutionException {
        try {
            Callable<LatLonEle> task = () -> {
                LatLonEle keyCol = keyColFinder.findKeyCol(peakA, peakB, searchBounds, unionFindNeighbors);

                if (keyCol != null) {
                    keyColNode = new Node(keyCol);
                    keyColNode.put("natural", "saddle");
                    int ele = (int) keyCol.ele();
                    keyColNode.put("ele", Integer.toString(ele));
                    String note = "Key col of ";
                    String peakName = peakANode.get("name");
                    if (peakName != null)
                        note += peakName;
                    else
                        note += "P." + ele;
                    keyColNode.put("note", note);
                    textAreaFeedback.append("Key col determined:" + System.lineSeparator());
                    textAreaFeedback
                            .append("Coordinates: " + keyCol.lat() + ", " + keyCol.lon() + System.lineSeparator());
                    textAreaFeedback.append("Elevation: " + ele + " m" + System.lineSeparator());
                    setDialogState(DialogState.KEY_COL_DETERMINED);
                } else {
                    textAreaFeedback
                            .append("Could not determine a key col in the search area" + System.lineSeparator());
                    setDialogState(DialogState.PEAKS_DEFINED);
                }
                return keyCol;
            };
            return executor.submit(task);
        } catch (RejectedExecutionException e) {
            setDialogState(DialogState.PEAKS_DEFINED);
            textAreaFeedback.setText("Search task could not be executed as thread");
            throw e;
        }
    }

    @Override
    public void dispose() {
        keyColFinder.removeElevationToolListener(this);
        super.dispose();
    }

    @Override
    public void status(String message) {
        textAreaFeedback.append(message + System.lineSeparator());
    }

    @Override
    protected void buttonAction(int buttonIndex, ActionEvent evt) {
        // Close button
        if (buttonIndex == 0)
            dispose();
    }

    private class SelectPeakAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        private final String whichPeak;

        public static final String PEAK_A = "A";
        public static final String PEAK_B = "B";

        public SelectPeakAction(String whichPeak) {
            super("Set selected peak", "cursor/modifier/select_node.svg",
                    "Add a selected node that marks the location of the peak or hill", null, false);
            this.whichPeak = whichPeak;
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

            Node selectedNode = selectedNodes.get(0);
            long nodeID = selectedNode.getId();
            LatLon latLon = selectedNode.getCoor();
            String name = selectedNode.get("name");
            String ele = selectedNode.get("ele");
            int rasterEle = (int) Math.round(elevationDataProvider.getLatLonEle(latLon).ele());
            String eleText;
            if (ele != null)
                eleText = "Node: " + ele + ", elevation raster: " + rasterEle;
            else
                eleText = "Elevation raster: " + rasterEle;

            if (whichPeak.equals(PEAK_A)) {
                peakANode = selectedNode;
                textFieldPeakANodeID.setText(Long.toString(nodeID));
                textFieldPeakACoord.setText(Double.toString(latLon.lat()) + ", " + Double.toString(latLon.lon()));
                textFieldPeakAName.setText(name);
                textFieldPeakAEle.setText(eleText);
                setSearchAreaLatLon();
                if (peakBNode != null)
                    setDialogState(DialogState.PEAKS_DEFINED);
            } else if (whichPeak.equals(PEAK_B)) {
                peakBNode = selectedNode;
                textFieldPeakBNodeID.setText(Long.toString(nodeID));
                textFieldPeakBCoord.setText(Double.toString(latLon.lat()) + ", " + Double.toString(latLon.lon()));
                textFieldPeakBName.setText(name);
                textFieldPeakBEle.setText(eleText);
                setSearchAreaLatLon();
                if (peakANode != null)
                    setDialogState(DialogState.PEAKS_DEFINED);
            }
        }
    }

    private class FindAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public FindAction() {
            super("Determine key col", "dialogs/key_col.svg", null, null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            setDialogState(DialogState.SEARCH_RUNNING);

            if (peakANode.equals(peakBNode)) {
                setDialogState(DialogState.PEAKS_DEFINED);
                textAreaFeedback.append(
                        "Specified peaks are not allowed to be identical. Cannot proceed." + System.lineSeparator());
                return;
            }

            LatLonEle peakA = elevationDataProvider.getLatLonEle(peakANode.getCoor());
            if (!peakA.hasValidEle()) {
                setDialogState(DialogState.PEAKS_DEFINED);
                textAreaFeedback.append(
                        "Elevation raster does not provide a valid elevation value at the position of peak A. Cannot proceed."
                                + System.lineSeparator());
                return;
            }
            LatLonEle peakB = elevationDataProvider.getLatLonEle(peakBNode.getCoor());
            if (!peakB.hasValidEle()) {
                setDialogState(DialogState.PEAKS_DEFINED);
                textAreaFeedback.append(
                        "Elevation raster does not provide a valid elevation value at the position of peak B. Cannot proceed."
                                + System.lineSeparator());
                return;
            }

            KeyColFinder.UnionFindNeighbors unionFindNeighbors = (KeyColFinder.UnionFindNeighbors) comboBoxUnionFindNeighbors
                    .getSelectedItem();

            textAreaFeedback.append("Determination of key col for peaks:" + System.lineSeparator());
            textAreaFeedback.append("Peak A:" + System.lineSeparator());
            String peakANodeID = textFieldPeakANodeID.getText();
            textAreaFeedback.append("Node ID: " + peakANodeID + System.lineSeparator());
            String peakAName = textFieldPeakAName.getText();
            if (!peakAName.isBlank())
                textAreaFeedback.append("Name: " + peakAName + System.lineSeparator());
            String peakACoord = textFieldPeakACoord.getText();
            textAreaFeedback.append("Coordinates: " + peakACoord + System.lineSeparator());
            textAreaFeedback
                    .append("Raster elevation: " + (int) Math.round(peakA.ele()) + " m" + System.lineSeparator());

            textAreaFeedback.append("Peak B:" + System.lineSeparator());
            String peakBNodeID = textFieldPeakBNodeID.getText();
            textAreaFeedback.append("Node ID: " + peakBNodeID + System.lineSeparator());
            String peakBName = textFieldPeakBName.getText();
            if (!peakBName.isBlank())
                textAreaFeedback.append("Name: " + peakBName + System.lineSeparator());
            String peakBCoord = textFieldPeakBCoord.getText();
            textAreaFeedback.append("Coordinates: " + peakBCoord + System.lineSeparator());
            textAreaFeedback
                    .append("Raster elevation: " + (int) Math.round(peakB.ele()) + " m" + System.lineSeparator());

            try {
                keyColFuture = determineKeyCol(peakA, peakB, searchBounds, unionFindNeighbors);
            } catch (RejectedExecutionException e) {
                textAreaFeedback
                        .append("Determination of key col rejected by thread executor" + System.lineSeparator());
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
            if (keyColFuture != null && !keyColFuture.isCancelled()) {
                boolean canceled = keyColFuture.cancel(true);
                if (canceled)
                    setDialogState(DialogState.PEAKS_DEFINED);
                else
                    textAreaFeedback.append("Failed to cancel the background task that determines the key col"
                            + System.lineSeparator());
            }
        }
    }

    private class AddToNewDataLayerAction extends JosmAction {

        private static final long serialVersionUID = 1L;

        public AddToNewDataLayerAction() {
            super("Add to new data layer", "addnode.svg",
                    "Add the determined key col and a copy of the peak nodes to a new OSM data layer", null, false);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            setDialogState(DialogState.ADDING_NODES_TO_DATA_LAYER);

            if (keyColNode == null) {
                setDialogState(DialogState.PEAKS_DEFINED);
                textAreaFeedback.setText("No nodes available to add them to the data layer (should not happen)");
                return;
            }

            DataSet ds = new DataSet();
            ds.addPrimitive(new Node(peakANode));
            ds.addPrimitive(new Node(peakBNode));
            ds.addPrimitive(new Node(keyColNode));
            String layerName = "Key col";
            String peakAName = textFieldPeakAName.getText();
            if (peakAName != null && !peakAName.isBlank())
                layerName += " of " + peakAName;
            String peakBName = textFieldPeakBName.getText();
            if (peakBName != null && !peakBName.isBlank())
                layerName += " with " + peakBName;
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
                    setDialogState(DialogState.PEAKS_DEFINED);
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

            setDialogState(DialogState.KEY_COL_DETERMINED);
        }
    }
}
