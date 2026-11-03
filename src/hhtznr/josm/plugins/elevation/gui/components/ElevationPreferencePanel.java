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

    private final JosmComboBox<SrtmTile.Type> cbSrtmType = new JosmComboBox<>(
            new SrtmTile.Type[] { SrtmTile.Type.SRTM1, SrtmTile.Type.SRTM3 });

    private final JosmComboBox<SrtmTile.Interpolation> cbSrtmInterpolation = new JosmComboBox<>(
            SrtmTile.Interpolation.values());

    private final JLabel lblSrtmCacheSize = new JLabel("Target Size of In-Memory SRTM Tile Cache:");
    private final JSpinner spSrtmCacheSize = UIFactory.createSpinner(ElevationPreferences.getSrtmCacheSizeLimit(),
            ElevationPreferences.MIN_SRTM_CACHE_SIZE_LIMIT, ElevationPreferences.MAX_SRTM_CACHE_SIZE_LIMIT,
            ElevationPreferences.INCR_SRTM_CACHE_SIZE_LIMIT);

    private final JCheckBox cbEnableSrtmElevationLayer = new JCheckBox("Enable SRTM Elevation Visualization Layer");

    private final JLabel lblSrtmRenderingLimit = new JLabel("SRTM Layer Rendering Map Size Limit:");
    private final JSpinner spSrtmRenderingLimit = UIFactory.createSpinner(
            ElevationPreferences.getSrtmElevationLayerRenderingLimit(),
            ElevationPreferences.MIN_SRTM_LAYER_RENDERING_LIMIT, ElevationPreferences.MAX_SRTM_LAYER_RENDERING_LIMIT,
            ElevationPreferences.INCR_SRTM_LAYER_RENDERING_LIMIT);
    private final JLabel lblSrtmRenderingLimitUnit = new JLabel("°");
    private final JLabel lblSrtmIsostep = new JLabel("Contour Line Isostep:");
    private final JSpinner spSrtmIsostep = UIFactory.createSpinner(ElevationPreferences.getSrtmContourLineIsostep(),
            ElevationPreferences.MIN_CONTOUR_LINE_ISOSTEP, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP,
            ElevationPreferences.INCR_CONTOUR_LINE_ISOSTEP);
    private final JLabel lblSrtmIsostepUnit = new JLabel("m");

    private final JLabel lblSrtmStrokeWidth = new JLabel("Contour Line Stroke Width:");

    private final JSpinner spSrtmStrokeWidth = UIFactory.createSpinner(
            ElevationPreferences.getSrtmContourLineStrokeWidth(), ElevationPreferences.MIN_CONTOUR_LINE_STROKE_WIDTH,
            ElevationPreferences.MAX_CONTOUR_LINE_STROKE_WIDTH, ElevationPreferences.INCR_CONTOUR_LINE_STROKE_WIDTH);

    private final JLabel lblSrtmStrokeWidthUnit = new JLabel("px");

    private final JLabel lblSrtmContourLineColoring = new JLabel("Contour Line Coloring Scheme:");

    private final JosmComboBox<Coloring.Scheme> cbSrtmContourLineColoring = new JosmComboBox<>(
            new Coloring.Scheme[] { Coloring.Scheme.CONSTANT_COLOR, Coloring.Scheme.FALSE_COLOR });

    private final JLabel lblSrtmStrokeColor = new JLabel("Contour Line Constant Color:");

    private final ColorChooserButton btnSrtmStrokeColor;

    private final JLabel lblSrtmHillshadeAltitude = new JLabel("Hillshade Illumination Source Altitude:");
    private final JSpinner spSrtmHillshadeAltitude = UIFactory.createSpinner(
            ElevationPreferences.getSrtmHillshadeAltitude(), ElevationPreferences.MIN_HILLSHADE_ALTITUDE,
            ElevationPreferences.MAX_HILLSHADE_ALTITUDE, ElevationPreferences.INCR_HILLSHADE_ALTITUDE);
    private final JLabel lblSrtmHillshadeAltitudeUnit = new JLabel("°");

    private final JLabel lblSrtmHillshadeAzimuth = new JLabel("Hillshade Illumination Source Azimuth:");
    private final JSpinner spSrtmHillshadeAzimuth = UIFactory.createSpinner(
            ElevationPreferences.getSrtmHillshadeAzimuth(), ElevationPreferences.MIN_HILLSHADE_AZIMUTH,
            ElevationPreferences.MAX_HILLSHADE_AZIMUTH, ElevationPreferences.INCR_HILLSHADE_AZIMUTH);
    private final JLabel lblSrtmHillshadeAzimuthUnit = new JLabel("°");

    private final JCheckBox cbSrtmEnableAutoDownload = new JCheckBox(
            "Enable Automatic Downloading of SRTM Elevation Data from NASA Earthdata");

    private final JRadioButton rbSrtmPasswordAuth = new JRadioButton("Use Password Authentication");
    private final JRadioButton rbSrtmAuthBearer = new JRadioButton("Use Authorization Bearer Token");

    private final JMultilineLabel lblSrtmEarthdataNotes = new JMultilineLabel(I18n.tr(
            "You need to register as Earthdata user at <a href=\"{0}\">{0}</a> and optionally create an authorization bearer token",
            ElevationPreferences.SRTM_SERVER_REGISTRATION_URL));

    private final JPanel pnlSrtmAuthData = new JPanel(new BorderLayout());

    private final JPanel pnlSrtmPasswordAuth = new AutoSizePanel();
    private final JLabel lblSrtmUserName = new JLabel("Earthdata User Name:");
    private final JosmTextField tfSrtmUserName = new JosmTextField();
    private final JLabel lblSrtmPassword = new JLabel("Earthdata Password:");
    private final JosmPasswordField tfSrtmPassword = new JosmPasswordField();
    private final JButton btnSrtmRemoveCredentials = new JButton(new RemoveCredentialsAction());

    private final JPanel pnlSrtmAuthBearer = new AutoSizePanel();
    private final JLabel lblSrtmAuthBearer = new JLabel("Earthdata Authorization Bearer Token:");
    private final JosmTextField tfSrtmAuthBearer = new JosmTextField(70);
    private final JButton btnSrtmRemoveAuthBearer = new JButton(new RemoveAuthBearerAction());

    private final JCheckBox cbEnableGeoTiffEnvelopeLayer = new JCheckBox("Enable GeoTIFF Overview Layer");
    private final JLabel lblGeoTiffDirectory = new JLabel("GeoTIFF Directory:");
    private final JTextField tfGeoTiffDirectory = new JosmTextField(70);;
    private final JButton btSelectGeoTiffDirectory = new JButton("Select");
    private final JCheckBox cbGeoTiffRecursive = new JCheckBox("Recursive");

    /**
     * Constructs a new {@code ElevationPreferencePanel}.
     */
    public ElevationPreferencePanel() {
        btnSrtmStrokeColor = new ColorChooserButton(this, "Choose Color of Contour Lines",
                ElevationPreferences.getSrtmContourLineColor());

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

        spSrtmRenderingLimit.setToolTipText(ElevationPluginTexts.TOOL_TIP_LAYER_RENDERING_LIMIT);

        spSrtmHillshadeAltitude.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_ALTITUDE);
        spSrtmHillshadeAzimuth.setToolTipText(ElevationPluginTexts.TOOL_TIP_HILLSHADE_AZIMUTH);

        cbSrtmEnableAutoDownload.setToolTipText(I18n.tr("SRTM files will be downloaded from {0} or {1}",
                ElevationPreferences.SRTM1_SERVER_BASE_URL, ElevationPreferences.SRTM3_SERVER_BASE_URL));
        cbSrtmEnableAutoDownload.addItemListener(event -> updateSrtmLayerEnabledState());

        JPanel pnlAuthButtons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        pnlAuthButtons.add(rbSrtmPasswordAuth);
        pnlAuthButtons.add(rbSrtmAuthBearer);

        ItemListener authChangeListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                pnlSrtmAuthData.removeAll();
                if (rbSrtmPasswordAuth.isSelected()) {
                    pnlSrtmAuthData.add(pnlSrtmPasswordAuth, BorderLayout.CENTER);
                    pnlSrtmAuthData.revalidate();
                } else if (rbSrtmAuthBearer.isSelected()) {
                    pnlSrtmAuthData.add(pnlSrtmAuthBearer, BorderLayout.CENTER);
                    pnlSrtmAuthBearer.revalidate();
                }
                repaint();
            }
        };

        rbSrtmPasswordAuth.setToolTipText("Select to use your Earthdata user name and password for authentication");
        rbSrtmPasswordAuth.addItemListener(authChangeListener);
        rbSrtmAuthBearer.setToolTipText("Select to use an Earthdata authorization bearer token for authentication");
        rbSrtmAuthBearer.addItemListener(authChangeListener);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbSrtmPasswordAuth);
        bg.add(rbSrtmAuthBearer);

        lblSrtmEarthdataNotes.setEditable(false);
        lblSrtmEarthdataNotes.addHyperlinkListener(event -> {
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
        pnl.add(cbSrtmType, gc);

        // Row "Interpolation"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(new JLabel("Elevation Value Interpolation:"), gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(cbSrtmInterpolation, gc);

        // Row "Cache size"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSrtmCacheSize, gc);

        gc.gridx++;
        pnl.add(spSrtmCacheSize, gc);

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
        pnl.add(lblSrtmRenderingLimit, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(spSrtmRenderingLimit, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblSrtmRenderingLimitUnit, gc);

        // Row "Contour line isostep"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSrtmIsostep, gc);

        gc.gridx++;
        pnl.add(spSrtmIsostep, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblSrtmIsostepUnit, gc);

        // Row "Contour line stroke width"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSrtmStrokeWidth, gc);

        gc.gridx++;
        pnl.add(spSrtmStrokeWidth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblSrtmStrokeWidthUnit, gc);

        // Row "Contour line coloring scheme"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSrtmContourLineColoring, gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(cbSrtmContourLineColoring, gc);

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
        pnl.add(lblSrtmStrokeColor, gc);

        gc.gridx++;
        gc.fill = GBC.BOTH;
        pnl.add(btnSrtmStrokeColor, gc);

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
        pnl.add(lblSrtmHillshadeAltitude, gc);

        gc.gridx++;
        pnl.add(spSrtmHillshadeAltitude, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblSrtmHillshadeAltitudeUnit, gc);

        // Row "Hillshade azimuth"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSrtmHillshadeAzimuth, gc);

        gc.gridx++;
        pnl.add(spSrtmHillshadeAzimuth, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(lblSrtmHillshadeAzimuthUnit, gc);

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
        pnl.add(cbSrtmEnableAutoDownload, gc);

        // Row "Auth type radio buttons"
        gc.gridy++;
        pnl.add(pnlAuthButtons, gc);

        // Row "Earthdata notes"
        gc.gridy++;
        gc.fill = GBC.HORIZONTAL;
        pnl.add(lblSrtmEarthdataNotes, gc);

        // Row "Auth data panel"
        gc.gridy++;
        gc.fill = GBC.BOTH;
        pnl.add(pnlSrtmAuthData, gc);

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
        pnlSrtmPasswordAuth.add(lblSrtmUserName, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlSrtmPasswordAuth.add(tfSrtmUserName, gc);

        // Row "Password"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlSrtmPasswordAuth.add(lblSrtmPassword, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlSrtmPasswordAuth.add(tfSrtmPassword, gc);

        // Button "Remove credentials"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlSrtmPasswordAuth.add(new JPanel(), gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlSrtmPasswordAuth.add(btnSrtmRemoveCredentials, gc);
    }

    public void buildAuthBearerPanel() {
        JScrollPane authBearerScrollPane = new JScrollPane(tfSrtmAuthBearer, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
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
        pnlSrtmAuthBearer.add(lblSrtmAuthBearer, gc);

        gc.gridx++;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlSrtmAuthBearer.add(authBearerScrollPane, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlSrtmAuthBearer.add(new JPanel(), gc);

        // Button "Remove auth bearer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlSrtmAuthBearer.add(new JPanel(), gc);

        gc.gridx++;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlSrtmAuthBearer.add(btnSrtmRemoveAuthBearer, gc);

        gc.gridx++;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlSrtmAuthBearer.add(new JPanel(), gc);
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
        cbSrtmType.setSelectedItem(ElevationPreferences.getSrtmType());
        cbSrtmInterpolation.setSelectedItem(ElevationPreferences.getSrtmElevationInterpolation());
        cbEnableSrtmElevationLayer.setSelected(ElevationPreferences.getSrtmElevationLayerEnabled());
        cbSrtmContourLineColoring.setSelectedItem(ElevationPreferences.getSrtmContourLineColoringScheme());
        cbSrtmEnableAutoDownload.setSelected(ElevationPreferences.getSrtmAutoDownloadEnabled());

        if (ElevationPreferences.getSrtmServerAuthType() == SrtmFileDownloader.AuthType.BEARER_TOKEN) {
            rbSrtmAuthBearer.setSelected(true);
        } else {
            rbSrtmPasswordAuth.setSelected(true);
        }

        PasswordAuthentication passwordAuth = ElevationPreferences.lookupEarthdataCredentials();
        if (passwordAuth != null) {
            String userName = passwordAuth.getUserName();
            char[] password = passwordAuth.getPassword();
            if (userName != null) {
                tfSrtmUserName.setText(userName);
            }
            if (password != null) {
                tfSrtmPassword.setText(String.valueOf(password));
            }
        }

        OAuth20Token oAuthToken = ElevationPreferences.lookupEarthdataOAuthToken();
        if (oAuthToken != null && oAuthToken.getBearerToken() != null) {
            tfSrtmAuthBearer.setText(oAuthToken.getBearerToken());
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
        boolean autoDownloadEnabled = elevationLayerEnabled && cbSrtmEnableAutoDownload.isSelected();
        lblSrtmRenderingLimit.setEnabled(elevationLayerEnabled);
        spSrtmRenderingLimit.setEnabled(elevationLayerEnabled);
        lblSrtmRenderingLimitUnit.setEnabled(elevationLayerEnabled);
        lblSrtmIsostep.setEnabled(elevationLayerEnabled);
        spSrtmIsostep.setEnabled(elevationLayerEnabled);
        lblSrtmIsostepUnit.setEnabled(elevationLayerEnabled);
        lblSrtmStrokeWidth.setEnabled(elevationLayerEnabled);
        spSrtmStrokeWidth.setEnabled(elevationLayerEnabled);
        lblSrtmStrokeWidthUnit.setEnabled(elevationLayerEnabled);
        lblSrtmContourLineColoring.setEnabled(elevationLayerEnabled);
        cbSrtmContourLineColoring.setEnabled(elevationLayerEnabled);
        lblSrtmStrokeColor.setEnabled(elevationLayerEnabled);
        btnSrtmStrokeColor.setEnabled(elevationLayerEnabled);
        lblSrtmHillshadeAltitude.setEnabled(elevationLayerEnabled);
        spSrtmHillshadeAltitude.setEnabled(elevationLayerEnabled);
        lblSrtmHillshadeAltitudeUnit.setEnabled(elevationLayerEnabled);
        lblSrtmHillshadeAzimuth.setEnabled(elevationLayerEnabled);
        spSrtmHillshadeAzimuth.setEnabled(elevationLayerEnabled);
        lblSrtmHillshadeAzimuthUnit.setEnabled(elevationLayerEnabled);
        rbSrtmPasswordAuth.setEnabled(autoDownloadEnabled);
        rbSrtmAuthBearer.setEnabled(autoDownloadEnabled);
        lblSrtmEarthdataNotes.setEnabled(autoDownloadEnabled);
        for (Component component : pnlSrtmPasswordAuth.getComponents()) {
            component.setEnabled(autoDownloadEnabled);
        }
        for (Component component : pnlSrtmAuthBearer.getComponents()) {
            component.setEnabled(autoDownloadEnabled);
        }
        lblSrtmEarthdataNotes.setEnabled(autoDownloadEnabled);
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
        ElevationPreferences.setSrtmType((SrtmTile.Type) cbSrtmType.getSelectedItem());
        ElevationPreferences
                .setSrtmElevationInterpolation((SrtmTile.Interpolation) cbSrtmInterpolation.getSelectedItem());
        ElevationPreferences.setSrtmCacheSizeLimit((Integer) spSrtmCacheSize.getValue());
        ElevationPreferences.setSrtmElevationLayerEnabled(cbEnableSrtmElevationLayer.isSelected());
        ElevationPreferences.setSrtmElevationLayerRenderingLimit((Double) spSrtmRenderingLimit.getValue());
        ElevationPreferences.setSrtmContourLineIsostep((Integer) spSrtmIsostep.getValue());
        ElevationPreferences.setSrtmContourLineStrokeWidth((Double) spSrtmStrokeWidth.getValue());
        ElevationPreferences
                .setSrtmContourLineColoringScheme((Coloring.Scheme) cbSrtmContourLineColoring.getSelectedItem());
        ElevationPreferences.setSrtmContourLineColor(btnSrtmStrokeColor.getSelectedColor());
        ElevationPreferences.setSrtmHillshadeAltitude((Integer) spSrtmHillshadeAltitude.getValue());
        ElevationPreferences.setSrtmHillshadeAzimuth((Integer) spSrtmHillshadeAzimuth.getValue());
        ElevationPreferences.setSrtmAutoDownloadEnabled(cbSrtmEnableAutoDownload.isSelected());

        if (rbSrtmPasswordAuth.isSelected()) {
            String userName = tfSrtmUserName.getText();
            char[] password = tfSrtmPassword.getPassword();
            ElevationPreferences.storeEarthdataCredentials(userName, password);
            ElevationPreferences.setSrtmServerAuthType(SrtmFileDownloader.AuthType.BASIC);
        } else if (rbSrtmAuthBearer.isSelected()) {
            ElevationPreferences.storeEarthdataOAuthToken(tfSrtmAuthBearer.getText());
            ElevationPreferences.setSrtmServerAuthType(SrtmFileDownloader.AuthType.BEARER_TOKEN);
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
                tfSrtmUserName.setText("");
                tfSrtmPassword.setText("");
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
                tfSrtmAuthBearer.setText("");
            }
        }
    }
}
