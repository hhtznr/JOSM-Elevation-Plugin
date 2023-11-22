package hhtznr.josm.plugins.elevation.gui;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.HyperlinkEvent;

import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

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
    private final JosmComboBox<SRTMTile.Interpolation> cbInterpolation = new JosmComboBox<>(SRTMTile.Interpolation.values());
    private final JLabel lblCacheSize = new JLabel("Max. Size of In-Memory Tile Cache:");
    private final JSpinner spCacheSize = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_RAM_CACHE_SIZE_LIMIT, ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT,
            ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT, ElevationPreferences.INCR_RAM_CACHE_SIZE_LIMIT));
    private final JLabel lblCacheSizeUnit = new JLabel("MiB");
    private final JCheckBox cbEnableElevationLayer = new JCheckBox("Enable Elevation Contour Line Layer");
    private final JLabel lblRenderingLimit = new JLabel("Layer Rendering Map Size Limit:");
    private final JSpinner spRenderingLimit = new JSpinner(new SpinnerNumberModel(
            ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT, ElevationPreferences.MIN_ELEVATION_LAYER_RENDERING_LIMIT,
            ElevationPreferences.MAX_ELEVATION_LAYER_RENDERING_LIMIT, ElevationPreferences.INCR_ELEVATION_LAYER_RENDERING_LIMIT));
    private final JLabel lblRenderingLimitUnit = new JLabel("°");
    private final JCheckBox cbEnableAutoDownload = new JCheckBox("Enable Automatic Downloading of Elevation Data");
    private final JLabel lblAuthBearer = new JLabel("Authorization Bearer Token:");
    private final JosmTextField tfAuthBearer = new JosmTextField();
    private final JMultilineLabel lblAuthBearerNotes = new JMultilineLabel(I18n.tr(
            "<html>You need to register at <a href=\"{0}\">{0}</a> to create the authorization bearer token.</html>",
            ElevationPreferences.SRTM_SERVER_REGISTRATION_URL));

    /**
     * Constructs a new {@code ElevationPreferencesPanel}.
     */
    public ElevationPreferencePanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(buildPreferencePanel(), GBC.eop().anchor(GridBagConstraints.NORTHWEST).fill(GridBagConstraints.BOTH));

        initFromPreferences();
        updateEnabledState();
    }

    /**
     * Builds the panel for the elevation preferences.
     *
     * @return Panel with elevation preferences
     */
    private final JPanel buildPreferencePanel() {
        cbEnableElevation.setToolTipText(I18n.tr("SRTM files need to be placed in {0}",
                ElevationPreferences.DEFAULT_SRTM_DIRECTORY.getAbsolutePath()));
        cbEnableElevation.addItemListener(event -> updateEnabledState());

        spRenderingLimit.setToolTipText("Layer rendering will be switched off if the map size (latitude, longitude) exceeds this value");

        lblSRTM1Server.setEditable(false);
        lblSRTM1Server.addHyperlinkListener(event -> browseHyperlink(event));

        lblSRTM3Server.setEditable(false);
        lblSRTM3Server.addHyperlinkListener(event -> browseHyperlink(event));

        cbEnableAutoDownload.setToolTipText(I18n.tr("SRTM files will be downloaded from {0} or {0}",
                ElevationPreferences.SRTM1_SERVER_BASE_URL, ElevationPreferences.SRTM3_SERVER_BASE_URL));
        cbEnableAutoDownload.addItemListener(event -> updateEnabledState());

        lblAuthBearerNotes.setEditable(false);
        lblAuthBearerNotes.addHyperlinkListener(event -> browseHyperlink(event));

        JPanel pnl = new AutoSizePanel();
        GridBagConstraints gc = new GridBagConstraints();

        // Row "Enable elevation"
        gc.gridy++;
        gc.gridx = 0;
        gc.anchor = GridBagConstraints.LINE_START;
        gc.insets = new Insets(5, 5, 0, 0);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 3;
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
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblSRTMType, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        pnl.add(cbSRTMType, gc);

        // Row "Interpolation"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblInterpolation, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        pnl.add(cbInterpolation, gc);

        // Row "Cache size"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblCacheSize, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.NONE;
        pnl.add(spCacheSize, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(lblCacheSizeUnit, gc);

        // Row "Enable elevation layer"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 3;
        pnl.add(cbEnableElevationLayer, gc);

        // Row "Layer rendering limit"
        gc.gridy++;
        gc.gridx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblRenderingLimit, gc);

        gc.gridx++;
        pnl.add(spRenderingLimit, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        pnl.add(lblRenderingLimitUnit, gc);

        // Row "Auto-download enabled"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 3;
        pnl.add(cbEnableAutoDownload, gc);

        // Row "Auth bearer token"
        gc.gridy++;
        gc.fill = GridBagConstraints.NONE;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        pnl.add(lblAuthBearer, gc);

        gc.gridx++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        pnl.add(tfAuthBearer, gc);

        // Row "Auth bearer notes"
        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 3;
        gc.weightx = 1.0;
        pnl.add(lblAuthBearerNotes, gc);

        // add an extra spacer, otherwise the layout is broken
        gc.gridy++;
        gc.gridwidth = 3;
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1.0;
        pnl.add(new JPanel(), gc);
        return pnl;
    }

    /**
     * Initializes the panel with the values from the preferences.
     */
    private final void initFromPreferences() {
        IPreferences pref = Config.getPref();

        cbEnableElevation.setSelected(pref.getBoolean(ElevationPreferences.ELEVATION_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_ENABLED));
        cbSRTMType.setSelectedItem(SRTMTile.Type.fromString(pref.get(ElevationPreferences.PREFERRED_SRTM_TYPE,
                ElevationPreferences.DEFAULT_PREFERRED_SRTM_TYPE.toString())));
        cbInterpolation.setSelectedItem(SRTMTile.Interpolation.fromString(pref.get(ElevationPreferences.ELEVATION_INTERPOLATION,
                ElevationPreferences.DEFAULT_ELEVATION_INTERPOLATION.toString())));
        spCacheSize.setValue(pref.getInt(ElevationPreferences.RAM_CACHE_SIZE_LIMIT,
                ElevationPreferences.DEFAULT_RAM_CACHE_SIZE_LIMIT));
        cbEnableElevationLayer.setSelected(pref.getBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_LAYER_ENABLED));
        spRenderingLimit.setValue(pref.getDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT,
                ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT));
        cbEnableAutoDownload.setSelected(pref.getBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED));
        tfAuthBearer.setText(pref.get(ElevationPreferences.ELEVATION_SERVER_AUTH_BEARER,
                ElevationPreferences.DEFAULT_ELEVATION_SERVER_AUTH_BEARER));
    }

    private final void updateEnabledState() {
        if (cbEnableElevation.isSelected()) {
            lblSRTMType.setEnabled(true);
            cbSRTMType.setEnabled(true);
            lblInterpolation.setEnabled(true);
            cbInterpolation.setEnabled(true);
            lblCacheSize.setEnabled(true);
            spCacheSize.setEnabled(true);
            cbEnableElevationLayer.setEnabled(true);
            lblRenderingLimit.setEnabled(true);
            spRenderingLimit.setEnabled(true);
            lblRenderingLimitUnit.setEnabled(true);
            lblSRTM1Server.setEnabled(true);
            lblSRTM3Server.setEnabled(true);
            cbEnableAutoDownload.setEnabled(true);
            lblAuthBearer.setEnabled(cbEnableAutoDownload.isSelected());
            tfAuthBearer.setEnabled(cbEnableAutoDownload.isSelected());
            lblAuthBearerNotes.setEnabled(cbEnableAutoDownload.isSelected());
        } else {
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
            lblSRTM1Server.setEnabled(false);
            lblSRTM3Server.setEnabled(false);
            cbEnableAutoDownload.setEnabled(false);
            lblAuthBearer.setEnabled(false);
            tfAuthBearer.setEnabled(false);
            lblAuthBearerNotes.setEnabled(false);
        }
    }

    private final void browseHyperlink(HyperlinkEvent event) {
        // https://stackoverflow.com/questions/14101000/hyperlink-to-open-in-browser-in-java
        // https://www.codejava.net/java-se/swing/how-to-create-hyperlink-with-jlabel-in-java-swing
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String url = event.getURL().toString();
            try {
                Desktop.getDesktop().browse(URI.create(url));
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
        pref.put(ElevationPreferences.ELEVATION_INTERPOLATION, ((SRTMTile.Interpolation) cbInterpolation.getSelectedItem()).toString());
        pref.putInt(ElevationPreferences.RAM_CACHE_SIZE_LIMIT, (Integer) spCacheSize.getValue());
        pref.putBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED, cbEnableElevationLayer.isSelected());
        pref.putDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT, (Double) spRenderingLimit.getValue());
        pref.putBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED, cbEnableAutoDownload.isSelected());
        pref.put(ElevationPreferences.ELEVATION_SERVER_AUTH_BEARER, tfAuthBearer.getText());
    }
}
