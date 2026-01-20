package hhtznr.josm.plugins.elevation.tools;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.ElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;

/**
 * This is the abstract super class of elevation tools that use an elevation
 * data provider and can have one or more tool listeners to which they send
 * status messages.
 *
 * @author Harald Hetzner
 */
public abstract class AbstractElevationTool extends ElevationDataConsumer {

    protected final ElevationDataProvider elevationDataProvider;
    private final Bounds bounds;
    private final List<ElevationToolListener> listeners = new ArrayList<>();

    /**
     * Creates a new abstract elevation tool.
     *
     * @param name                  The name of the tool instance (used for
     *                              logging).
     * @param elevationDataProvider The elevation data provider from which elevation
     *                              data can be obtained.
     * @param bounds                The coordinate bound, the tool works in.
     */
    public AbstractElevationTool(String name, ElevationDataProvider elevationDataProvider, Bounds bounds) {
        super(name, elevationDataProvider.getGridMatching(bounds));
        this.elevationDataProvider = elevationDataProvider;
        this.bounds = bounds;
    }

    /**
     * Returns the coordinate bounds of operation of this elevation tool.
     *
     * @return The coordinate bound, the tool works in.
     */
    public Bounds getBounds() {
        return bounds;
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
