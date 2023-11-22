package hhtznr.josm.plugins.elevation.gui;

import javax.swing.Box;

import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.ElevationPreferences;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.io.SRTMFileReader;

/**
 * Elevation tab in preferences.
 *
 * @author Harald Hetzner
 */
public final class ElevationTabPreferenceSetting extends DefaultTabPreferenceSetting {

    private ElevationPreferencePanel pnlElevationPreferences;

    public ElevationTabPreferenceSetting() {
        super("elevation", I18n.tr("Elevation Data"),
                I18n.tr("Elevation preferences and connection settings for the SRTM server."));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        pnlElevationPreferences = new ElevationPreferencePanel();
        pnlElevationPreferences.add(Box.createVerticalGlue(), GBC.eol().fill());
        gui.createPreferenceTab(this).add(pnlElevationPreferences, GBC.eol().fill());
    }

    /**
     * Saves the values to the preferences and applies them.
     */
    @Override
    public boolean ok() {
        // Save to preferences file
        pnlElevationPreferences.saveToPreferences();

        // Apply preferences
        IPreferences pref = Config.getPref();
        boolean elevationEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_ENABLED,
                ElevationPreferences.DEFAULT_ELEVATION_ENABLED);
        ElevationPlugin.getInstance().setElevationEnabled(elevationEnabled);
        if (elevationEnabled) {
            SRTMFileReader.getInstance().setElevationInterpolation(
                    SRTMTile.Interpolation.fromString(pref.get(ElevationPreferences.ELEVATION_INTERPOLATION,
                            ElevationPreferences.DEFAULT_ELEVATION_INTERPOLATION.toString())));
            SRTMFileReader.getInstance().tileCache.setCacheSizeLimit(pref.getInt(
                    ElevationPreferences.RAM_CACHE_SIZE_LIMIT, ElevationPreferences.DEFAULT_RAM_CACHE_SIZE_LIMIT));

            boolean elevationLayerEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_LAYER_ENABLED);
            if (elevationLayerEnabled) {
                ElevationLayer elevationLayer = ElevationPlugin.getInstance().getElevationLayer();
                if (elevationLayer != null) {
                    elevationLayer
                            .setRenderingLimit(pref.getDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT,
                                    ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT));
                    elevationLayer.setContourLineIsostep(pref.getInt(ElevationPreferences.CONTOUR_LINE_ISOSTEP,
                            ElevationPreferences.DEFAULT_CONTOUR_LINE_ISOSTEP));
                }
            }
        }

        return false;
    }

    /**
     * No help available.
     */
    @Override
    public String getHelpContext() {
        return null;
    }
}
