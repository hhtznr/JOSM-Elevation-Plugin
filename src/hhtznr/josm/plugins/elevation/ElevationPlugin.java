package hhtznr.josm.plugins.elevation;

import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;

public class ElevationPlugin extends Plugin {

    private LocalElevationLabel localElevationLabel = null;

    /**
     * Initializes the plugin.
     *
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        Logging.info("Elevation plugin initialized");
    }

    /**
     * Called after Main.mapFrame is initialized. (After the first data is loaded).
     * You can use this callback to tweak the newFrame to your needs, as example
     * install an alternative Painter.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);
        if (localElevationLabel == null)
            localElevationLabel = new LocalElevationLabel(newFrame);
        else
            localElevationLabel.addToMapFrame(newFrame);
        if (newFrame != null)
            localElevationLabel.setVisible(true);
    }
}
