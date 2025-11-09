package hhtznr.josm.plugins.elevation.tools;

import java.util.ArrayList;
import java.util.List;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;

/**
 * This is the abstract super class of elevation tools that use an elevation
 * data provider and can have one or more tool listeners to which they send
 * status messages.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractElevationTool {

    protected final ElevationDataProvider elevationDataProvider;
    private final List<ElevationToolListener> listeners = new ArrayList<>();

    /**
     * Creates a new abstract elevation tool.
     *
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     */
    public AbstractElevationTool(ElevationDataProvider elevationDataProvider) {
        this.elevationDataProvider = elevationDataProvider;
    }

    /**
     * Adds a listener to this elevation tool
     *
     * @param listener The listener to add.
     */
    public void addElevationToolListener(ElevationToolListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Removes a listener from this elevation tool.
     *
     * @param listener The listener to remove.
     */
    public void removeElevationToolListener(ElevationToolListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Sends a status message to all listeners.
     *
     * @param message The message to send.
     */
    protected void informListenersAboutStatus(String message) {
        synchronized (listeners) {
            for (ElevationToolListener listener : listeners)
                listener.status(message);
        }
    }
}
