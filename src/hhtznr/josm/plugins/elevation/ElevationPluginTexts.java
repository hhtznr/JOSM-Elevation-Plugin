package hhtznr.josm.plugins.elevation;

/**
 * Texts intended to be used more than once within the scope of the plugin.
 *
 * @author Harald Hetzner
 */
public class ElevationPluginTexts {

    private ElevationPluginTexts() {
    }

    public static final String TOOL_TIP_LAYER_RENDERING_LIMIT = "Layer rendering will be switched off if the map size (latitude, longitude) exceeds this value";

    public static final String TOOL_TIP_LOWER_CUTOFF_ELEVATION = "Contour lines and elevation raster points with an elevation greater or equal this cutoff value will not be drawn";

    public static final String TOOL_TIP_UPPER_CUTOFF_ELEVATION = "Contour lines and elevation raster points with an elevation less or equal this cutoff value will not be drawn";

    public static final String TOOL_TIP_HILLSHADE_ALTITUDE = "The altitude is the angle of the illumination source above the horizon in the range from 0 (horizon) to 90° (overhead)";

    public static final String TOOL_TIP_HILLSHADE_AZIMUTH = "The azimuth is the anglular direction of the illumination source (N: 0, E: 90°, S: 180°, W: 270°)";
}
