package hhtznr.josm.plugins.elevation.data;

import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * This class is a dummy implementation of the {@link ElevationDataConsumer}
 * class. It is intended to be added to a new {@link ElevationDataConsumer}
 * instance to prevent it from being cleaned up while it has already been
 * created, but no real consumer has been added to it yet.
 *
 * @author Harald Hetzner
 */
public class DummyElevationDataConsumer extends ElevationDataConsumer {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Dummy elevation data consumer");

    /**
     * Creates a new dummy elevation data consumer, which is intended to protect a
     * new {@link ElevationDataConsumer} from early cleanup as long as no real
     * consumer is registered.
     *
     * @param elevationGrid The elevation grid protected by this consumer.
     */
    public DummyElevationDataConsumer(ElevationGrid elevationGrid) {
        super(namer.nextName(), elevationGrid);
    }

}
