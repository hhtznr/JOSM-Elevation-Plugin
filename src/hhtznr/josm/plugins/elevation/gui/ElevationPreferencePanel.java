package hhtznr.josm.plugins.elevation.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.net.URI;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.HyperlinkEvent;

import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.JosmPasswordField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

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

    private final JCheckBox cbEnableElevation = new JCheckBox(I18n.tr("Enable Use of Elevation Data"));
    private final JMultilineLabel lblSRTM1Server = new JMultilineLabel(I18n.tr(
            "<html>SRTM1 files (elevation sampled at 1 arc-seconds) can be downloaded from <a href=\"{0}\">{0}</a>.</html>",
            ElevationPreferences.SRTM1_SERVER_BASE_URL));
    private final JMultilineLabel lblSRTM3Server = new JMultilineLabel(I18n.tr(
            "<html>SRTM3 files (elevation sampled at 3 arc-seconds) can be downloaded from <a href=\"{0}\">{0}</a>.</html>",
            ElevationPreferences.SRTM3_SERVER_BASE_URL));

    private final JLabel lblSRTMType = new JLabel("Preferred SRTM Type:");
    private final JosmComboBox<SRTMTile.Type> cbSRTMType = new JosmComboBox<>(SRTMTile.Type.values());

    private final JLabel lblInterpolation = new JLabel("Elevation Value Interpolation:");
    private final JosmComboBox<SRTMTile.Interpolation> cbInterpolation = new JosmComboBox<>(
            SRTMTile.Interpolation.values());

    private final JLabel lblCacheSize = new JLabel("Max. Size of In-Memory Tile Cache:");
    private final JSpinner spCacheSize = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getRAMCacheSizeLimit(), ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT,
            ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT, ElevationPreferences.INCR_RAM_CACHE_SIZE_LIMIT));
    private final JLabel lblCacheSizeUnit = new JLabel("MiB");

    private final JCheckBox cbEnableElevationLayer = new JCheckBox("Enable Elevation Visualization Layer");

    private final JLabel lblRenderingLimit = new JLabel("Layer Rendering Map Size Limit:");
    private final JSpinner spRenderingLimit = new JSpinner(
            new SpinnerNumberModel(ElevationPreferences.getElevationLayerRenderingLimit(),
                    ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT));
    private final JLabel lblRenderingLimitUnit = new JLabel("°");
    private final JLabel lblIsostep = new JLabel("Contour Line Isostep:");
    private final JSpinner spIsostep = new JSpinner(new SpinnerNumberModel(ElevationPreferences.getContourLineIsostep(),
            ElevationPreferences.MIN_CONTOUR_LINE_ISOSTEP, ElevationPreferences.MAX_CONTOUR_LINE_ISOSTEP,
            ElevationPreferences.INCR_CONTOUR_LINE_ISOSTEP));
    private final JLabel lblIsostepUnit = new JLabel("m");

    private final JLabel lblStrokeWidth = new JLabel("Contour Line Stroke Width:");

    private final JSpinner spStrokeWidth = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getContourLineStrokeWidth(), ElevationPreferences.MIN_CONTOUR_LINE_STROKE_WIDTH,
            ElevationPreferences.MAX_CONTOUR_LINE_STROKE_WIDTH, ElevationPreferences.INCR_CONTOUR_LINE_STROKE_WIDTH));

    private final JLabel lblStrokeWidthUnit = new JLabel("px");

    private final JLabel lblStrokeColor = new JLabel("Contour Line Color:");

    private final JButton btnStrokeColor = new JButton();

    private final JLabel lblHillshadeAltitude = new JLabel("Hillshade Illumination Source Altitude:");
    private final JSpinner spHillshadeAltitude = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAltitude(), ElevationPreferences.MIN_HILLSHADE_ALTITUDE,
            ElevationPreferences.MAX_HILLSHADE_ALTITUDE, ElevationPreferences.INCR_HILLSHADE_ALTITUDE));
    private final JLabel lblHillshadeAltitudeUnit = new JLabel("°");

    private final JLabel lblHillshadeAzimuth = new JLabel("Hillshade Illumination Source Azimuth:");
    private final JSpinner spHillshadeAzimuth = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.getHillshadeAzimuth(), ElevationPreferences.MIN_HILLSHADE_AZIMUTH,
            ElevationPreferences.MAX_HILLSHADE_AZIMUTH, ElevationPreferences.INCR_HILLSHADE_AZIMUTH));
    private final JLabel lblHillshadeAzimuthUnit = new JLabel("°");

    private final JCheckBox cbEnableAutoDownload = new JCheckBox("Enable Automatic Downloading of Elevation Data");

    private final JRadioButton rbPasswordAuth = new JRadioButton("Use Password Authentication");
    private final JRadioButton rbAuthBearer = new JRadioButton("Use Authorization Bearer Token");

    private final JMultilineLabel lblEarthdataNotes = new JMultilineLabel(I18n.tr(
            "<html>You need to register as Earthdata user at <a href=\"{0}\">{0}</a> and optionally create an authorization bearer token.</html>",
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
    private final JosmTextField tfAuthBearer = new JosmTextField();
    private final JButton btnRemoveAuthBearer = new JButton(new RemoveAuthBearerAction());

    /**
     * Constructs a new {@code ElevationPreferencePanel}.
     */
    public ElevationPreferencePanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(buildPreferencePanel(), GBC.eop().anchor(GBC.NORTHWEST).fill(GBC.BOTH));

        initFromPreferences();
        updateEnabledState();
    }

    /**
     * Builds the panel for the elevation preferences.
     *
     * @return Panel with elevation preferences.
     */
    private final JPanel buildPreferencePanel() {
        cbEnableElevation
                .setToolTipText(I18n.tr("SRTM files need to be placed in subdirectories SRTM1 and SRTM3 in {0}",
                        ElevationPreferences.DEFAULT_EARTHDATA_DIRECTORY.getAbsolutePath()));
        cbEnableElevation.addItemListener(event -> updateEnabledState());

        lblSRTM1Server.setEditable(false);
        lblSRTM1Server.addHyperlinkListener(event -> browseHyperlink(event));

        lblSRTM3Server.setEditable(false);
        lblSRTM3Server.addHyperlinkListener(event -> browseHyperlink(event));

        cbEnableElevationLayer.addItemListener(event -> updateEnabledState());

        spRenderingLimit.setToolTipText(
                "Layer rendering will be switched off if the map size (latitude, longitude) exceeds this value");

        Color contourLineColor = ElevationPreferences.getContourLineColor();
        btnStrokeColor.setBackground(contourLineColor);
        btnStrokeColor.setForeground(ColorHelper.getForegroundColor(contourLineColor));
        btnStrokeColor.setText(ColorHelper.color2html(contourLineColor));
        btnStrokeColor.setOpaque(true);
        btnStrokeColor.setBorderPainted(false);
        btnStrokeColor.setRolloverEnabled(false);
        btnStrokeColor.addActionListener(event -> {
            Color selectedColor = JColorChooser.showDialog(ElevationPreferencePanel.this,
                    "Choose Color of Contour Lines", btnStrokeColor.getBackground());
            if (selectedColor != null) {
                btnStrokeColor.setBackground(selectedColor);
                btnStrokeColor.setForeground(ColorHelper.getForegroundColor(selectedColor));
                btnStrokeColor.setText(ColorHelper.color2html(selectedColor));
            }
        });

        spHillshadeAltitude.setToolTipText(
                "The altitude is the angle of the illumination source above the horizon in the range from 0 (horizon) to 90° (overhead)");

        spHillshadeAzimuth.setToolTipText(
                "The azimuth is the anglular direction of the illumination source (N: 0, E: 90°, S: 180°, W: 270°)");

        cbEnableAutoDownload.setToolTipText(I18n.tr("SRTM files will be downloaded from {0} or {0}",
                ElevationPreferences.SRTM1_SERVER_BASE_URL, ElevationPreferences.SRTM3_SERVER_BASE_URL));
        cbEnableAutoDownload.addItemListener(event -> updateEnabledState());

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
        lblEarthdataNotes.addHyperlinkListener(event -> browseHyperlink(event));

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
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlAuthBearer.add(tfAuthBearer, gc);

        // Button "Remove auth bearer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnlAuthBearer.add(new JPanel(), gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnlAuthBearer.add(btnRemoveAuthBearer, gc);

        // Elevation preferences panel
        JPanel pnl = new AutoSizePanel();

        // Row "Enable elevation"
        gc.gridy = 0;
        gc.gridx = 0;
        gc.fill = GBC.HORIZONTAL;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(cbEnableElevation, gc);

        // Row "SRTM1 server"
        gc.gridy++;
        pnl.add(lblSRTM1Server, gc);

        // Row "SRTM3 server"
        gc.gridy++;
        pnl.add(lblSRTM3Server, gc);

        // Row "SRTM type"
        gc.gridy++;
        gc.fill = GBC.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSRTMType, gc);

        gc.gridx++;
        gc.gridwidth = GBC.REMAINDER;
        gc.weightx = 1.0;
        pnl.add(cbSRTMType, gc);

        // Row "Interpolation"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblInterpolation, gc);

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
        pnl.add(lblCacheSizeUnit, gc);

        // Row "Enable elevation layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GBC.NONE;
        pnl.add(cbEnableElevationLayer, gc);

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
        gc.fill = GBC.NONE;
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

        // Row "Contour line color"
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
        gc.fill = GBC.NONE;
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

        // add an extra spacer, otherwise the layout is broken
        pnl.add(Box.createVerticalGlue(), GBC.eol().fill());
        return pnl;
    }

    /**
     * Initializes the panel with the values from the preferences.
     */
    private final void initFromPreferences() {
        cbEnableElevation.setSelected(ElevationPreferences.getElevationEnabled());
        cbSRTMType.setSelectedItem(ElevationPreferences.getPreferredSRTMType());
        cbInterpolation.setSelectedItem(ElevationPreferences.getElevationInterpolation());
        cbEnableElevationLayer.setSelected(ElevationPreferences.getElevationLayerEnabled());
        btnStrokeColor.setBackground(ElevationPreferences.getContourLineColor());
        cbEnableAutoDownload.setSelected(ElevationPreferences.getAutoDownloadEnabled());

        if (ElevationPreferences.getElevationServerAuthType() == SRTMFileDownloader.AuthType.BEARER_TOKEN)
            rbAuthBearer.setSelected(true);
        else
            rbPasswordAuth.setSelected(true);

        PasswordAuthentication passwordAuth = ElevationPreferences.lookupEarthdataCredentials();
        if (passwordAuth != null) {
            String userName = passwordAuth.getUserName();
            char[] password = passwordAuth.getPassword();
            if (userName != null)
                tfUserName.setText(userName);
            if (password != null)
                tfPassword.setText(String.valueOf(password));
        }

        OAuth20Token oAuthToken = ElevationPreferences.lookupEarthdataOAuthToken();
        if (oAuthToken != null && oAuthToken.getBearerToken() != null)
            tfAuthBearer.setText(oAuthToken.getBearerToken());
    }

    private final void updateEnabledState() {
        if (cbEnableElevation.isSelected()) {
            lblSRTM1Server.setEnabled(true);
            lblSRTM3Server.setEnabled(true);
            lblSRTMType.setEnabled(true);
            cbSRTMType.setEnabled(true);
            lblInterpolation.setEnabled(true);
            cbInterpolation.setEnabled(true);
            lblCacheSize.setEnabled(true);
            spCacheSize.setEnabled(true);
            cbEnableElevationLayer.setEnabled(true);
            lblRenderingLimit.setEnabled(cbEnableElevationLayer.isSelected());
            spRenderingLimit.setEnabled(cbEnableElevationLayer.isSelected());
            lblRenderingLimitUnit.setEnabled(cbEnableElevationLayer.isSelected());
            lblIsostep.setEnabled(cbEnableElevationLayer.isSelected());
            spIsostep.setEnabled(cbEnableElevationLayer.isSelected());
            lblIsostepUnit.setEnabled(cbEnableElevationLayer.isSelected());
            lblStrokeWidth.setEnabled(cbEnableElevationLayer.isSelected());
            spStrokeWidth.setEnabled(cbEnableElevationLayer.isSelected());
            lblStrokeWidthUnit.setEnabled(cbEnableElevationLayer.isSelected());
            lblStrokeColor.setEnabled(cbEnableElevationLayer.isSelected());
            btnStrokeColor.setEnabled(cbEnableElevationLayer.isSelected());
            lblHillshadeAltitude.setEnabled(cbEnableElevationLayer.isSelected());
            spHillshadeAltitude.setEnabled(cbEnableElevationLayer.isSelected());
            lblHillshadeAltitudeUnit.setEnabled(cbEnableElevationLayer.isSelected());
            lblHillshadeAzimuth.setEnabled(cbEnableElevationLayer.isSelected());
            spHillshadeAzimuth.setEnabled(cbEnableElevationLayer.isSelected());
            lblHillshadeAzimuthUnit.setEnabled(cbEnableElevationLayer.isSelected());
            cbEnableAutoDownload.setEnabled(true);
            rbPasswordAuth.setEnabled(cbEnableAutoDownload.isSelected());
            rbAuthBearer.setEnabled(cbEnableAutoDownload.isSelected());
            lblEarthdataNotes.setEnabled(cbEnableAutoDownload.isSelected());
            for (Component component : pnlPasswordAuth.getComponents())
                component.setEnabled(cbEnableAutoDownload.isSelected());
            for (Component component : pnlAuthBearer.getComponents())
                component.setEnabled(cbEnableAutoDownload.isSelected());
            lblEarthdataNotes.setEnabled(cbEnableAutoDownload.isSelected());
        } else {
            lblSRTM1Server.setEnabled(false);
            lblSRTM3Server.setEnabled(false);
            lblSRTMType.setEnabled(false);
            cbSRTMType.setEnabled(false);
            lblInterpolation.setEnabled(false);
            cbInterpolation.setEnabled(false);
            lblCacheSize.setEnabled(false);
            spCacheSize.setEnabled(false);
            cbEnableElevationLayer.setEnabled(false);
            lblRenderingLimit.setEnabled(false);
            spRenderingLimit.setEnabled(false);
            lblRenderingLimitUnit.setEnabled(false);
            lblIsostep.setEnabled(false);
            spIsostep.setEnabled(false);
            lblIsostepUnit.setEnabled(false);
            lblStrokeWidth.setEnabled(false);
            spStrokeWidth.setEnabled(false);
            lblStrokeWidthUnit.setEnabled(false);
            lblStrokeColor.setEnabled(false);
            btnStrokeColor.setEnabled(false);
            lblHillshadeAltitude.setEnabled(false);
            spHillshadeAltitude.setEnabled(false);
            lblHillshadeAltitudeUnit.setEnabled(false);
            lblHillshadeAzimuth.setEnabled(false);
            spHillshadeAzimuth.setEnabled(false);
            lblHillshadeAzimuthUnit.setEnabled(false);
            cbEnableAutoDownload.setEnabled(false);
            rbPasswordAuth.setEnabled(false);
            rbAuthBearer.setEnabled(false);
            lblEarthdataNotes.setEnabled(false);
            for (Component component : pnlPasswordAuth.getComponents())
                component.setEnabled(false);
            for (Component component : pnlAuthBearer.getComponents())
                component.setEnabled(false);
            lblEarthdataNotes.setEnabled(false);
        }
    }

    private final void browseHyperlink(HyperlinkEvent event) {
        // https://stackoverflow.com/questions/14101000/hyperlink-to-open-in-browser-in-java
        // https://www.codejava.net/java-se/swing/how-to-create-hyperlink-with-jlabel-in-java-swing
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String url = event.getURL().toString();
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(URI.create(url));
                    else
                        Logging.info("BROWSE action is not supported");
                }
            } catch (IOException e) {
                Logging.error(e.toString());
            }
        }
    }

    /**
     * Saves the current values to the preferences.
     */
    public void saveToPreferences() {
        IPreferences pref = Config.getPref();
        pref.putBoolean(ElevationPreferences.ELEVATION_ENABLED, cbEnableElevation.isSelected());
        pref.put(ElevationPreferences.PREFERRED_SRTM_TYPE, ((SRTMTile.Type) cbSRTMType.getSelectedItem()).toString());
        pref.put(ElevationPreferences.ELEVATION_INTERPOLATION,
                ((SRTMTile.Interpolation) cbInterpolation.getSelectedItem()).toString());
        pref.putInt(ElevationPreferences.RAM_CACHE_SIZE_LIMIT, (Integer) spCacheSize.getValue());
        pref.putBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED, cbEnableElevationLayer.isSelected());
        pref.putDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT, (Double) spRenderingLimit.getValue());
        pref.putInt(ElevationPreferences.CONTOUR_LINE_ISOSTEP, (Integer) spIsostep.getValue());
        pref.putDouble(ElevationPreferences.CONTOUR_LINE_STROKE_WIDTH, (Double) spStrokeWidth.getValue());
        ElevationPreferences.putContourLineColor(btnStrokeColor.getBackground());
        pref.putInt(ElevationPreferences.HILLSHADE_ALTITUDE, (Integer) spHillshadeAltitude.getValue());
        pref.putInt(ElevationPreferences.HILLSHADE_AZIMUTH, (Integer) spHillshadeAzimuth.getValue());
        pref.putBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED, cbEnableAutoDownload.isSelected());

        if (rbPasswordAuth.isSelected()) {
            String userName = tfUserName.getText();
            char[] password = tfPassword.getPassword();
            ElevationPreferences.storeEarthdataCredentials(userName, password);
            pref.put(ElevationPreferences.ELEVATION_SERVER_AUTH_TYPE, SRTMFileDownloader.AuthType.BASIC.toString());
        } else if (rbAuthBearer.isSelected()) {
            ElevationPreferences.storeEarthdataOAuthToken(tfAuthBearer.getText());
            pref.put(ElevationPreferences.ELEVATION_SERVER_AUTH_TYPE,
                    SRTMFileDownloader.AuthType.BEARER_TOKEN.toString());
        }
    }

    private class RemoveCredentialsAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        RemoveCredentialsAction() {
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

        RemoveAuthBearerAction() {
            putValue(NAME, "Remove bearer token");
            putValue(SHORT_DESCRIPTION, "Remove bearer token from JOSM. This does not revoke the bearer token.");
            new ImageProvider("cancel").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (ElevationPreferences.removeEarthdataOAuthToken())
                tfAuthBearer.setText("");
        }
    }
}
