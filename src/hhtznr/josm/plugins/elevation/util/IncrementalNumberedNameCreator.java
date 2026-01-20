package hhtznr.josm.plugins.elevation.util;

/**
 * This class implements a name creator that provides names consisting of a base
 * name and an appended integer that is increment with each name created.
 *
 * @author Harald Hetzner
 */
public class IncrementalNumberedNameCreator {

    private int counter = 0;
    private final String baseName;

    /**
     * Creates a new numbered name creator.
     *
     * @param baseName The base name.
     */
    public IncrementalNumberedNameCreator(String baseName) {
        this.baseName = baseName;
    }

    /**
     * Returns the next numbered name. The numbered name consists of the base name
     * and the next integer. Integers start with {@code 1}.
     *
     * @return The next name.
     */
    public synchronized String nextName() {
        counter += 1;
        return baseName + " " + counter;
    }
}
