package hhtznr.josm.plugins.elevation.gui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JColorChooser;

import org.openstreetmap.josm.tools.ColorHelper;

/**
 * This class implements a button for color selection. The button opens a
 * {@code JColorChooser}. The background color of the button represents the
 * selected color. The button label shows the corresponding HTML color code.
 *
 * @author Harald Hetzner
 */
public class ColorChooserButton extends JButton {

    private static final long serialVersionUID = 2450565742850315454L;

    /**
     * Constructs a new color chooser button.
     *
     * @param parent       The parent component of the button.
     * @param dialogTitle  The title of the {@code JColorChooser} opened by the
     *                     button.
     * @param initialColor The initially selected color.
     */
    public ColorChooserButton(Component parent, String dialogTitle, Color initialColor) {
        setBackground(initialColor);
        setForeground(ColorHelper.getForegroundColor(initialColor));
        setText(ColorHelper.color2html(initialColor));
        setOpaque(true);
        setBorderPainted(false);
        setRolloverEnabled(false);
        addActionListener(event -> {
            Color selectedColor = JColorChooser.showDialog(parent, dialogTitle, getSelectedColor());
            if (selectedColor != null) {
                setBackground(selectedColor);
                setForeground(ColorHelper.getForegroundColor(selectedColor));
                setText(ColorHelper.color2html(selectedColor));
            }
        });
    }

    /**
     * Returns the selected color, which corresponds to the background color of the
     * button.
     *
     * @return The selected color.
     */
    public Color getSelectedColor() {
        return getBackground();
    }
}
