package hhtznr.josm.plugins.elevation.tools;

/**
 * This interface can be implemented by tool that execute tasks running in the
 * background to send status messages to the UI.
 *
 * @author Harald Hetzner
 */
public interface ElevationToolListener {

    /**
     * Reports a status message to the listeners.
     *
     * @param message The status message to be reported.
     */
    public void status(String message);

}
