package hhtznr.josm.plugins.elevation.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides useful drawing methods for non-standard shapes.
 *
 * @author Harald Hetzner
 */
public class DrawUtils {

    private DrawUtils() {
    }

    /**
     * Draws and equilateral triangle.
     *
     * @param g            The 2D graphics context on which to draw.
     * @param centerX      The center of the triangle in x direction (screen
     *                     coordinate).
     * @param centerY      The center of the triangle in y direction (screen
     *                     coordinate).
     * @param sideLength   The side length of the triangle in px.
     * @param upright      If {@code true} the triangle is drawn upright (flat base
     *                     is parallel to the bottom a single vertex points straight
     *                     up. Otherwise, the triangle is drawn upside down.
     * @param colorFill    The color to use for filling the interior of the
     *                     triangle.
     * @param colorOutline The color to use for the outline of the triangle.
     */
    public static void drawEquilateralTriangle(Graphics2D g, int centerX, int centerY, int sideLength, boolean upright,
            Color colorFill, Color colorOutline) {
        // Calculate height of the triangle
        double height = sideLength * (Math.sqrt(3.0) / 2.0);

        int[] xPoints = new int[3];
        int[] yPoints = new int[3];

        if (upright) {
            // Calculate the vertices
            // Top vertex: (Cx, Cy - 2/3*H)
            int topX = centerX;
            int topY = (int) (centerY - (2.0 / 3.0) * height);

            // Bottom-left vertex: (Cx - S/2, Cy + 1/3*H)
            int bottomLeftX = (int) (centerX - sideLength / 2.0);
            int bottomLeftY = (int) (centerY + (1.0 / 3.0) * height);

            // Bottom-right vertex: (Cx + S/2, Cy + 1/3*H)
            int bottomRightX = (int) (centerX + sideLength / 2.0);
            int bottomRightY = (int) (centerY + (1.0 / 3.0) * height);

            // Define the polygon coordinates
            xPoints[0] = topX;
            xPoints[1] = bottomLeftX;
            xPoints[2] = bottomRightX;
            yPoints[0] = topY;
            yPoints[1] = bottomLeftY;
            yPoints[2] = bottomRightY;
        } else {
            // Calculate the vertices
            // Bottom vertex: (Cx, Cy + 2/3*H)
            int bottomX = centerX;
            int bottomY = (int) (centerY + (2.0 / 3.0) * height);

            // Top-left vertex: (Cx - S/2, Cy - 1/3*H)
            int topLeftX = (int) (centerX - sideLength / 2.0);
            int topLeftY = (int) (centerY - (1.0 / 3.0) * height);

            // Top-right vertex: (Cx + S/2, Cy - 1/3*H)
            int topRightX = (int) (centerX + sideLength / 2.0);
            int topRightY = (int) (centerY - (1.0 / 3.0) * height);

            // Define the polygon coordinates
            xPoints[0] = bottomX;
            xPoints[1] = topRightX;
            xPoints[2] = topLeftX;
            yPoints[0] = bottomY;
            yPoints[1] = topRightY;
            yPoints[2] = topLeftY;
        }
        int nPoints = 3;

        // Draw and fill the triangle
        g.setColor(colorFill);
        g.fillPolygon(xPoints, yPoints, nPoints);
        if (colorOutline != null) {
            g.setColor(colorOutline);
            g.drawPolygon(xPoints, yPoints, nPoints);
        }
    }

    /**
     * Draws a text with an outline in a different color to improve readability of
     * the text on a mixed-color background like a map.
     *
     * @param g            The 2D graphics context on which to draw.
     * @param x            The x position where to draw the text.
     * @param y            The y position where to draw the text.
     * @param font         The font to use for the text.
     * @param colorFill    The color to use for filling the interior of the font.
     * @param colorOutline The color to use for the outline of the font.
     * @param outlineWidth The width of the outline in px.
     * @param text         The text to draw.
     */
    public static void drawOutlinedText(Graphics2D g, int x, int y, Font font, Color colorFill, Color colorOutline,
            float outlineWidth, String text) {
        // Set hints for smooth edges
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        TextLayout tl = new TextLayout(text, font, g.getFontRenderContext());

        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        Shape textShape = tl.getOutline(transform);

        // Draw the outline
        g.setColor(colorOutline);
        g.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(textShape);

        // Fill the interior
        g.setColor(colorFill);
        g.fill(textShape);
    }

    /**
     * This class implements a text renderer which ensures that rendered texts do
     * not overlap. It achieves this by omitting to draw text that would overlap
     * with text that has already been drawn.
     */
    public static class NonOverlappingTextRenderer {

        // A list to keep track of the screen areas where text has been drawn
        private final List<Rectangle> occupiedTextAreas = new ArrayList<>();

        /**
         * Draws the provided text using using
         * {@link DrawUtils#drawOutlinedText(Graphics2D, int, int, Font, Color, Color, float, String)}
         * if it does not overlap with text that has already been drawn by this
         * renderer.
         *
         * @param g            The 2D graphics context on which to draw.
         * @param x            The x position where to draw the text.
         * @param y            The y position where to draw the text.
         * @param colorFill    The color to use for filling the interior of the font.
         * @param colorOutline The color to use for the outline of the font.
         * @param text         The text to draw.
         */
        public void drawTextSafely(Graphics2D g, int x, int y, Color colorFill, Color colorOutline, String text) {
            // Get font metrics to measure the text size
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();
            int ascent = metrics.getAscent();

            y = y - textHeight / 2 + ascent;
            /*
             * Calculate the bounding box for the potential text label We use p.y +
             * metrics.getAscent() because drawString draws from the baseline up;
             * metrics.getAscent() = Y start position (top edge of text)
             */
            Rectangle textBounds = new Rectangle(x, y - ascent, textWidth, textHeight);

            // Check for overlap with any previously drawn text areas
            boolean overlaps = false;
            for (Rectangle existingArea : occupiedTextAreas) {
                if (textBounds.intersects(existingArea)) {
                    overlaps = true;
                    break; // Overlap found, stop checking
                }
            }

            // 3. Draw and record only if no overlap
            if (!overlaps) {
                DrawUtils.drawOutlinedText(g, x, y, g.getFont(), colorFill, colorOutline, 2.0f, text);

                // 4. Add this area to our list of occupied spaces
                occupiedTextAreas.add(textBounds);
            }
        }
    }
}
