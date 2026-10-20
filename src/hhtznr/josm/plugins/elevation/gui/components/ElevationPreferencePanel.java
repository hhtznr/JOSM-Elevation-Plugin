package hhtznr.josm.plugins.elevation.gui.components;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.net.PasswordAuthentication;
import java.nio.file.Path;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.JosmPasswordField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.OpenBrowser;

import hhtznr.josm.plugins.elevation.ElevationPluginTexts;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.io.SrtmFileDownloader;
import hhtznr.josm.plugins.elevation.util.Coloring;

/**
 * Component allowing to enable or disable the plugin functionality, define the
 * directory where SRTM files are stored and settings for the server where these
 * can be downloaded.
 *
 * @author Harald Hetzner
 */
public class ElevationPreferencePanel extends VerticallyScrollablePanel {

    private static final long serialVersionUID = 1L;

    static final class AutoSizePanel extends JPanel {
        private static final long serialVersionUID = 1L;

        AutoSizePanel() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }

    private final JosmComboBox<SrtmTile.Type> cbSRTMType = new JosmComboBox<>(
            new SrtmTile.Type[] { SrtmTile.Type.SRTM1, SrtmTile.Type.SRTM3 });

    private final JosmComboBox<SrtmTile.Interpolation> cbInterpolation = new JosmComboBox<>(
            SrtmTile.Interpolation.values());

    private final JLabel lblCacheSize = new JLabel("Max. Size of In-Memory Tile Cache:");
    private final JSpinner spCacheSize = UIFactory.createSpinner(ElevationPreferences.getRAMCacheSizeLimit(),
            ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT, ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT,
            ElevationPreferences.INCR_RAM_CACHE_SIZE_LIMIT);

    private final JCheckBox cbEnableSrtmElevationLayer = new JCheckBox("Enable SRTM Elevation Visualization Layer");

    private final JLabel lblRenderingLimit = new JLabel("Layer Rendering Map Size Limit:");
    private final JSpinner spRenderingLimit = UIFactory.createSpinner(
            ElevationPreferences.getElevationLayerRenderingLimit(),
            ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
            ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT,
            ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT);
    private final JLabel lblRenderingLimitUnit = new JLabel("°");
    private final JLabel lblIsostep = new JLabel("Contour Line Isostep:");
    private final JSpinner spIsostep = UIFactory.createSpinner(ElevationPreferences.getContourLineIsostep(),
            ElevationPreferences.MIN_CONTOUR_LINE_ISOSTEP, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP,
            ElevationPreferences.INCR_CONTOUR_LINE_ISOSTEP);
    private final JLabel lblIsostepUnit = new JLabel("m");

    private final JLabel lblStrokeWidth = new JLabel("Contour Line Stroke Width:");

    private final JSpinner spStrokeWidth = UIFactory.createSpinner(ElevationPreferences.getContourLineStrokeWidth(),
            ElevationPreferences.MIN_CONTOUR_LINE_STROKE_WIDTH, ElevationPreferences.MAX_CONTOUR_LINE_STROKE_WIDTH,
            ElevationPreferences.INCR_CONTOUR_LINE_STROKE_WIDTH);

    private final JLabel lblStrokeWidthUnit = new JLabel("px");

    private final JLabel lblContourLineColoring = new JLabel("Contour Line Coloring Scheme:");

    private final JosmComboBox<Coloring.Scheme> cbContourLineColoring = new JosmComboBox<>(
            new Coloring.Scheme[] { Coloring.Scheme.CONSTANT_COLOR, Coloring.Scheme.FALSE_COLOR });

    private final JLabel lblStrokeColor = new JLabel("Contour Line Constant Color:");

    private final ColorChooserButton btnStrokeColor;

    private final JLabel lblHillshadeAltitude = new JLabel("Hillshade Illumination Source Altitude:");
    private final JSpinner spHillshadeAltitude = UIFactory.createSpinner(ElevationPreferences.getHillshadeAltitude(),
            ElevationPreferences.MIN_HILLSHADE_ALTITUDE, ElevationPreferences.MAX_HILLSHADE_ALTITUDE,
            ElevationPreferences.INCR_HILLSHADE_ALTITUDE);
    private final JLabel lblHillshadeAltitudeUnit = new JLabel("°");

    private final JLabel lblHillshadeAzimuth = new JLabel("Hillshade Illumination Source Azimuth:");
    private final JSpinner spHillshadeAzimuth = UIFactory.createSpinner(ElevationPreferences.getHillshadeAzimuth(),
            ElevationPreferences.MIN_HILLSHADE_AZIMUTH, ElevationPreferences.MAX_HILLSHADE_AZIMUTH,
            ElevationPreferences.INCR_HILLSHADE_AZIMUTH);
    private final JLabel lblHillshadeAzimuthUnit = new JLabel("°");

    private final JCheckBox cbEnableAutoDownload = new JCheckBox(
            "Enable Automatic Downloading of SRTM Elevation Data from NASA Earthdata");

    private final JRadioButton rbPasswordAuth = new JRadioButton("Use Password Authentication");
    private final JRadioButton rbAuthBearer = new JRadioButton("Use Authorization Bearer Token");

    private final JMultilineLabel lblEarthdataNotes = new JMultilineLabel(I18n.tr(
            "You need to register as Earthdata user at <a href=\"{0}\">{0}</a> and optionally create an authorization bearer token",
            ElevationPreferences.SRTM_SERVER_REGISTRATION_URL));

    private final JPanel pnlAuthData = new JPanel(new BorderLayout());

    private final JPanel pnlPasswordAuth = new AutoSizePanel();
    private final JLabel lblUserName = new JLabel("Earthdata User Name:");
    private final JosmTextField tfUserName = new JosmTextField();
    private final JLabel lblPassword = new JLabel("Earthdata Password:");
    private final JosmPasswordField tfPassword = new JosmPasswordField();
    private final JButton btnRemoveCredentials = new JButton(new RemoveCredentialsAction());

    private final JPanel pnlAuthBearer = new AutoSizePanel();
    private final JLabel lblAuthBearer = new JLabel("Earthdata Authorization Bearer Token:");
    private final JosmTextField tfAuthBearer = new JosmTextField(70);
    private final JButton btnRemoveAuthBearer = new JButton(new RemoveAuthBearerAction());

    private final JCheckBox cbEnableGeoTiffEnvelopeLayer = new JCheckBox("Enable GeoTIFF Overview Layer");
    private final JLabel lblGeoTiffDirectory = new JLabel("GeoTIFF Directory:");
    private final JTextField tfGeoTiffDirectory = new JosmTextField(70);;
    private final JButton btSelectGeoTiffDirectory = new JButton("Select");
    private final JCheckBox cbGeoTiffRecursive = new JCheckBox("Recursive");

    /**
     * Constructs a new {@code ElevationPreferencePanel}.
     */
    public ElevationPreferencePanel() {
        btnStrokeColor = new ColorChooserButton(this, "Choose Color of Contour Lines",
                ElevationPreferences.getContourLineColor());

        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(buildPreferencePanel(), GBC.eop().anchor(GBC.NORTHWEST).fill(GBC.BOTH));

        initFromPreferences();
        updateSrtmLayerEnabledState();
        updateGeoTiffLayerEnabledState();
    }

    /**
     * Builds the panel for the elevation preferences.
     *
     * @return Panel with elevation preferences.
     */
    private final JPanel buildPreferencePanel() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<body style='width:700px;'>");
        sb.append(I18n.tr(
                "SRTM1 files (elevation sampled at 1 arc seconds) of the whole Earth can be downloaded from <a href=\"{0}\">{0}</a> and need to be placed in {1}<br><br>",
                ElevationPreferences.SRTM1_MANUAL_DOWNLOAD_URL,
                ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY.toAbsolutePath().toString()));
        sb.append(I18n.tr(
                "SRTM3 files (elevation sampled at 3 arc seconds) of the whole Earth can be downloaded from <a href=\"{0}\">{0}</a> and need to be placed in {1}<br><br>",
                ElevationPreferences.SRTM3_MANUAL_DOWNLOAD_URL,
                ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY.toAbsolutePath().toString()));
        sb.append(I18n.tr(
                "High quality SRTM1 files (elevation sampled at 1 arc seconds) of Europe can be downloaded from <a href=\"{0}\">{0}</a> and need to be placed in {1}<br><br>",
                ElevationPreferences.SONNY_LIDAR_DTM1_BASE_URL,
                ElevationPreferences.DEFAULT_SONNY_LIDAR_DTM1_DIRECTORY.toAbsolutePath().toString()));
        sb.append(I18n.tr(
                "High quality SRTM3 files (elevation sampled at 3 arc seconds) of Europe can be downloaded from <a href=\"{0}\">{0}</a> and need to be placed in {1}<br>",
                ElevationPreferences.SONNY_LIDAR_DTM3_BASE_URL,
                ElevationPreferences.DEFAULT_SONNY_LIDAR_DTM3_DIRECTORY.toAbsolutePath().toString()));
        sb.append("</body>");
        sb.append("</html>");

        JLabel labelSrtmSourceInfo = new JLabel("Information on SRTM Data Sources");

        JMultilineLabel labelSRTMSources = new JMultilineLabel(sb.toString());
        labelSRTMSources.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                OpenBrowser.displayUrl(event.getURL().toString());
            }
        });

        cbEnableSrtmElevationLayer.addItemListener(event -> updateSrtmLayerEnabledState());

        spRenderingLimit.setToolTipText(ElevationPluginTexts.TOOL_TIP_LAYER_RENDERING_LIMIT);

        spHillshadeAltitude.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spHillshadeAzimuth.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_AZIMUTH);

        cbEnableAutoDownload.setToolTipText(I18n.tr("SRTM files will be downloaded from {0} or {1}",
                ElevationPreferences.SRTM1_SERVER_BASE_URL, ElevationPreferences.SRTM3_SERVER_BASE_URL));
        cbEnableAutoDownload.addItemListener(event -> updateSrtmLayerEnabledState());

        JPanel pnlAuthButtons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        pnlAuthButtons.add(rbPasswordAuth);
        pnlAuthButtons.add(rbAuthBearer);

        ItemListener authChangeListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                pnlAuthData.removeAll();
                if (rbPasswordAuth.isSelected()) {
                    pnlAuthData.add(pnlPasswordAuth, BorderLayout.CENTER);
                    pnlAuthData.revalidate();
                } else if (rbAuthBearer.isSelected()) {
                    pnlAuthData.add(pnlAuthBearer, BorderLayout.CENTER);
                    pnlAuthBearer.revalidate();
                }
                repaint();
            }
        };

        rbPasswordAuth.setToolTipText("Select to use your Earthdata user name and password for authentication");
        rbPasswordAuth.addItemListener(authChangeListener);
        rbAuthBearer.setToolTipText("Select to use an Earthdata authorization bearer token for authentication");
        rbAuthBearer.addItemListener(authChangeListener);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbPasswordAuth);
        bg.add(rbAuthBearer);

        lblEarthdataNotes.setEditable(false);
        lblEarthdataNotes.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                OpenBrowser.displayUrl(event.getURL().toString());
            }
        });

        cbEnableGeoTiffEnvelopeLayer.addItemListener(event -> updateGeoTiffLayerEnabledState());
        tfGeoTiffDirectory.setEditable(false);
        btSelectGeoTiffDirectory.addActionListener(event -> showGeoTiffDirectoryChooser());

        buildCredentialsPanel();
        buildAuthBearerPanel();

        GBC gc = GBC.std();

        // Elevation preferences panel
        JPanel pnl = new AutoSizePanel();

        // Row "SRTM source info"
        gc.gridy = 0;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSrtmSourceInfo, gc);

        // Row "SRTM sources"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(labelSRTMSources, gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "SRTM type"
        gc.gridy++;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("SRTM Type:"), gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(cbSRTMType, gc);

        // Row "Interpolation"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Elevation Value Interpolation:"), gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(cbInterpolation, gc);

        // Row "Cache size"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblCacheSize, gc);

        gc.gridx++;
        pnl.add(spCacheSize, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JLabel("MiB"), gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Enable elevation layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        pnl.add(cbEnableSrtmElevationLayer, gc);

        // Row "Layer rendering limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblRenderingLimit, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(spRenderingLimit, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblRenderingLimitUnit, gc);

        // Row "Contour line isostep"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblIsostep, gc);

        gc.gridx++;
        pnl.add(spIsostep, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblIsostepUnit, gc);

        // Row "Contour line stroke width"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblStrokeWidth, gc);

        gc.gridx++;
        pnl.add(spStrokeWidth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblStrokeWidthUnit, gc);

        // Row "Contour line coloring scheme"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblContourLineColoring, gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(cbContourLineColoring, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // Row "Contour line constant color"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblStrokeColor, gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(btnStrokeColor, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Hillshade altitude"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblHillshadeAltitude, gc);

        gc.gridx++;
        pnl.add(spHillshadeAltitude, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblHillshadeAltitudeUnit, gc);

        // Row "Hillshade azimuth"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblHillshadeAzimuth, gc);

        gc.gridx++;
        pnl.add(spHillshadeAzimuth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblHillshadeAzimuthUnit, gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Auto-download enabled"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = GBC.REMAINDER;
        pnl.add(cbEnableAutoDownload, gc);

        // Row "Auth type radio buttons"
        gc.gridy++;
        pnl.add(pnlAuthButtons, gc);

        // Row "Earthdata notes"
        gc.gridy++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(lblEarthdataNotes, gc);

        // Row "Auth data panel"
        gc.gridy++;
        gc.fill = GBC.BOTH;
        pnl.add(pnlAuthData, gc);

        // Separator before next section
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JSeparator(SwingConstants.HORIZONTAL), gc);

        // Row "Enable GeoTIFF layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        pnl.add(cbEnableGeoTiffEnvelopeLayer, gc);

        // Row "GeoTiff layer panel"
        gc.gridy++;
        gc.fill = GBC.BOTH;
        pnl.add(buildGeoTiffLayerPanel(), gc);

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());
        return pnl;
    }

    public void buildCredentialsPanel() {
        GBC gc = GBC.std();

        // Credentials panel
        // Row "User name"
        gc.gridy = 0;
        gc.gridx = 0;
        gc.insets = new Insets(5, 5, 0, 0);
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlPasswordAuth.add(lblUserName, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlPasswordAuth.add(tfUserName, gc);

        // Row "Password"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlPasswordAuth.add(lblPassword, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlPasswordAuth.add(tfPassword, gc);

        // Button "Remove credentials"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlPasswordAuth.add(new JPanel(), gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlPasswordAuth.add(btnRemoveCredentials, gc);
    }

    public void buildAuthBearerPanel() {
        JScrollPane authBearerScrollPane = new JScrollPane(tfAuthBearer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        GBC gc = GBC.std();

        // Authorization bearer token panel
        // Row "Bearer token"
        gc.gridy = 0;
        gc.gridx = 0;
        gc.insets = new Insets(5, 5, 0, 0);
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlAuthBearer.add(lblAuthBearer, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlAuthBearer.add(authBearerScrollPane, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlAuthBearer.add(new JPanel(), gc);

        // Button "Remove auth bearer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlAuthBearer.add(new JPanel(), gc);

        gc.gridx++;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlAuthBearer.add(btnRemoveAuthBearer, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlAuthBearer.add(new JPanel(), gc);
    }

    public JPanel buildGeoTiffLayerPanel() {
        JScrollPane geoTiffDirectoryScrollPane = new JScrollPane(tfGeoTiffDirectory,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JPanel pnl = new AutoSizePanel();
        GBC gc = GBC.std();

        // Row 1
        gc.gridy = 0;
        gc.gridx = 0;
        gc.anchor = GBC.LINE_START;
        gc.insets = new Insets(5, 5, 0, 0);
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblGeoTiffDirectory, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.weightx = 0.0;
        pnl.add(geoTiffDirectoryScrollPane, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(btSelectGeoTiffDirectory, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // Row 2
        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JPanel(), gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(cbGeoTiffRecursive, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(new JPanel(), gc);

        // add an extra spacer, otherwise the layout is broken
        gc.gridy++;
        gc.gridwidth = 4;
        gc.fill = GBC.BOTH;
        gc.weighty = 1.0;
        pnl.add(new JPanel(), gc);
        return pnl;
    }

    /**
     * Initializes the panel with the values from the preferences.
     */
    private final void initFromPreferences() {
        cbSRTMType.setSelectedItem(ElevationPreferences.getSRTMType());
        cbInterpolation.setSelectedItem(ElevationPreferences.getElevationInterpolation());
        cbEnableSrtmElevationLayer.setSelected(ElevationPreferences.getElevationLayerEnabled());
        cbContourLineColoring.setSelectedItem(ElevationPreferences.getContourLineColoringScheme());
        cbEnableAutoDownload.setSelected(ElevationPreferences.getAutoDownloadEnabled());

        if (ElevationPreferences.getElevationServerAuthType() == SrtmFileDownloader.AuthType.BEARER_TOKEN) {
            rbAuthBearer.setSelected(true);
        } else {
            rbPasswordAuth.setSelected(true);
        }

        PasswordAuthentication passwordAuth = ElevationPreferences.lookupEarthdataCredentials();
        if (passwordAuth != null) {
            String userName = passwordAuth.getUserName();
            char[] password = passwordAuth.getPassword();
            if (userName != null) {
                tfUserName.setText(userName);
            }
            if (password != null) {
                tfPassword.setText(String.valueOf(password));
            }
        }

        OAuth20Token oAuthToken = ElevationPreferences.lookupEarthdataOAuthToken();
        if (oAuthToken != null && oAuthToken.getBearerToken() != null) {
            tfAuthBearer.setText(oAuthToken.getBearerToken());
        }

        cbEnableGeoTiffEnvelopeLayer.setSelected(ElevationPreferences.getGeoTiffEnvelopeLayerEnabled());
        Path path = ElevationPreferences.getGeoTiffIndexDirectory();
        if (path != null) {
            tfGeoTiffDirectory.setText(path.toAbsolutePath().toString());
        }
        cbGeoTiffRecursive.setSelected(ElevationPreferences.getGeoTiffIndexDirectoryRecursive());
    }

    private final void updateSrtmLayerEnabledState() {
        boolean elevationLayerEnabled = cbEnableSrtmElevationLayer.isSelected();
        boolean autoDownloadEnabled = elevationLayerEnabled && cbEnableAutoDownload.isSelected();
        lblRenderingLimit.setEnabled(elevationLayerEnabled);
        spRenderingLimit.setEnabled(elevationLayerEnabled);
        lblRenderingLimitUnit.setEnabled(elevationLayerEnabled);
        lblIsostep.setEnabled(elevationLayerEnabled);
        spIsostep.setEnabled(elevationLayerEnabled);
        lblIsostepUnit.setEnabled(elevationLayerEnabled);
        lblStrokeWidth.setEnabled(elevationLayerEnabled);
        spStrokeWidth.setEnabled(elevationLayerEnabled);
        lblStrokeWidthUnit.setEnabled(elevationLayerEnabled);
        lblContourLineColoring.setEnabled(elevationLayerEnabled);
        cbContourLineColoring.setEnabled(elevationLayerEnabled);
        lblStrokeColor.setEnabled(elevationLayerEnabled);
        btnStrokeColor.setEnabled(elevationLayerEnabled);
        lblHillshadeAltitude.setEnabled(elevationLayerEnabled);
        spHillshadeAltitude.setEnabled(elevationLayerEnabled);
        lblHillshadeAltitudeUnit.setEnabled(elevationLayerEnabled);
        lblHillshadeAzimuth.setEnabled(elevationLayerEnabled);
        spHillshadeAzimuth.setEnabled(elevationLayerEnabled);
        lblHillshadeAzimuthUnit.setEnabled(elevationLayerEnabled);
        rbPasswordAuth.setEnabled(autoDownloadEnabled);
        rbAuthBearer.setEnabled(autoDownloadEnabled);
        lblEarthdataNotes.setEnabled(autoDownloadEnabled);
        for (Component component : pnlPasswordAuth.getComponents()) {
            component.setEnabled(autoDownloadEnabled);
        }
        for (Component component : pnlAuthBearer.getComponents()) {
            component.setEnabled(autoDownloadEnabled);
        }
        lblEarthdataNotes.setEnabled(autoDownloadEnabled);
    }

    private final void updateGeoTiffLayerEnabledState() {
        boolean layerEnabled = cbEnableGeoTiffEnvelopeLayer.isSelected();
        lblGeoTiffDirectory.setEnabled(layerEnabled);
        tfGeoTiffDirectory.setEnabled(layerEnabled);
        btSelectGeoTiffDirectory.setEnabled(layerEnabled);
        cbGeoTiffRecursive.setEnabled(layerEnabled);
    }

    /**
     * Shows a file chooser for selection of the directory containing GeoTIFF files.
     */
    private void showGeoTiffDirectoryChooser() {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("Select a directory with GeoTIFF files");

            if (!tfGeoTiffDirectory.getText().equals("")) {
                File currentDirectory = new File(tfGeoTiffDirectory.getText());
                fileChooser.setCurrentDirectory(currentDirectory);
            }

            if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                File selectedDirectory = fileChooser.getSelectedFile();
                // The FileChooser is weird. You can type in a directory, which does not exist
                if (selectedDirectory.isDirectory()) {
                    tfGeoTiffDirectory.setText(selectedDirectory.getAbsolutePath());
                }
            }
        });
    }

    /**
     * Saves the current values to the preferences.
     */
    public void saveToPreferences() {
        ElevationPreferences.setSRTMType((SrtmTile.Type) cbSRTMType.getSelectedItem());
        ElevationPreferences.setElevationInterpolation((SrtmTile.Interpolation) cbInterpolation.getSelectedItem());
        ElevationPreferences.setRAMCacheSizeLimit((Integer) spCacheSize.getValue());
        ElevationPreferences.setElevationLayerEnabled(cbEnableSrtmElevationLayer.isSelected());
        ElevationPreferences.setElevationLayerRenderingLimit((Double) spRenderingLimit.getValue());
        ElevationPreferences.setContourLineIsostep((Integer) spIsostep.getValue());
        ElevationPreferences.setContourLineStrokeWidth((Double) spStrokeWidth.getValue());
        ElevationPreferences.setContourLineColoringScheme((Coloring.Scheme) cbContourLineColoring.getSelectedItem());
        ElevationPreferences.setContourLineColor(btnStrokeColor.getSelectedColor());
        ElevationPreferences.setHillshadeAltitude((Integer) spHillshadeAltitude.getValue());
        ElevationPreferences.setHillshadeAzimuth((Integer) spHillshadeAzimuth.getValue());
        ElevationPreferences.setAutoDownloadEnabled(cbEnableAutoDownload.isSelected());

        if (rbPasswordAuth.isSelected()) {
            String userName = tfUserName.getText();
            char[] password = tfPassword.getPassword();
            ElevationPreferences.storeEarthdataCredentials(userName, password);
            ElevationPreferences.setElevationServerAuthType(SrtmFileDownloader.AuthType.BASIC);
        } else if (rbAuthBearer.isSelected()) {
            ElevationPreferences.storeEarthdataOAuthToken(tfAuthBearer.getText());
            ElevationPreferences.setElevationServerAuthType(SrtmFileDownloader.AuthType.BEARER_TOKEN);
        }

        ElevationPreferences.setGeoTiffEnvelopeLayerEnabled(cbEnableGeoTiffEnvelopeLayer.isSelected());
        String pathString = tfGeoTiffDirectory.getText();
        Path path = null;
        if (!pathString.isBlank()) {
            path = Path.of(pathString);
        }
        ElevationPreferences.setGeoTiffIndexDirectory(path);
        ElevationPreferences.setGeoTiffIndexDirectoryRecursive(cbGeoTiffRecursive.isSelected());
    }

    private class RemoveCredentialsAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public RemoveCredentialsAction() {
            putValue(NAME, "Remove credentials");
            putValue(SHORT_DESCRIPTION,
                    "Remove user name and password from JOSM. This does not affect the associated Earthdata account.");
            new ImageProvider("cancel").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (ElevationPreferences.removeEarthdataCredentials()) {
                tfUserName.setText("");
                tfPassword.setText("");
            }
        }
    }

    private class RemoveAuthBearerAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public RemoveAuthBearerAction() {
            putValue(NAME, "Remove bearer token");
            putValue(SHORT_DESCRIPTION, "Remove bearer token from JOSM. This does not revoke the bearer token.");
            new ImageProvider("cancel").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (ElevationPreferences.removeEarthdataOAuthToken()) {
                tfAuthBearer.setText("");
            }
        }
    }
}
