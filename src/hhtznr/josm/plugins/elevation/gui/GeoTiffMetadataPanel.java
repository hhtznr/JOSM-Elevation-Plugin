package hhtznr.josm.plugins.elevation.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.DataBuffer;
import java.awt.image.RenderedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.geotools.api.geometry.Bounds;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffIIOMetadataDecoder;
import org.openstreetmap.josm.tools.Utils;

import hhtznr.josm.plugins.elevation.data.GeoTiffImage;

/**
 * Displays relevant metadata of a GeoTIFF file.
 *
 * Uses GeoTools and standard Swing components.
 *
 * @author Harald Hetzner
 */
public class GeoTiffMetadataPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String UNAVAILABLE = "Not available";

    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.####");

    private int row;

    /**
     * Creates a new {@code JPanel} with structured information on a GeoTIFF image
     * based the internal representation {@link GeoTiffImage}.
     *
     * @param geoTiffImage The GeoTIFF image.
     */
    public GeoTiffMetadataPanel(GeoTiffImage geoTiffImage) {
        this(geoTiffImage.getFile(), geoTiffImage.getRawImage(), geoTiffImage.getCoverage(),
                geoTiffImage.getMetadata());
    }

    /**
     * Creates a new {@code JPanel} with structured information on a GeoTIFF image
     * based on different Java objects representing a GeoTIFF image. If an object is
     * not available, {@code null} can be passed. This will result in the
     * corresponding info section being skipped.
     *
     * @param file     The file associated with the GeoTIFF image or {@code null}.
     * @param image    The rendered image of the GeoTIFF image or {@code null}.
     * @param coverage The GeoTools grid coverage of the GeoTIFF image or
     *                 {@code null}.
     * @param metadata The GeoTools metadata object of the GeoTIFF image or
     *                 {@code null}.
     */
    public GeoTiffMetadataPanel(File file, RenderedImage image, GridCoverage2D coverage,
            GeoTiffIIOMetadataDecoder metadata) {
        super(new BorderLayout(8, 8));

        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 4, 8, 4);

        JLabel title = new JLabel("GeoTIFF Information");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize()));

        content.add(title, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(3, 4, 3, 4);

        loadMetadata(file, image, coverage, metadata, content, gbc);

        // Keep all metadata fields aligned to the top.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = gbc.gridy + 1;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.VERTICAL;

        content.add(new JPanel(), filler);

        add(content, BorderLayout.CENTER);
    }

    private void loadMetadata(File file, RenderedImage image, GridCoverage2D coverage,
            GeoTiffIIOMetadataDecoder metadata, JPanel content, GridBagConstraints gbc) {

        if (file != null) {
            addSection(content, gbc, "File");

            addRow(content, gbc, "Name", file.getName());
            addRow(content, gbc, "Path", file.getAbsolutePath());
            addRow(content, gbc, "Size", Utils.getSizeString(file.length(), Locale.getDefault()));
            addRow(content, gbc, "Last modified",
                    file.lastModified() > 0 ? new Date(file.lastModified()).toString() : UNAVAILABLE);
        }

        if (image != null) {
            addSection(content, gbc, "Image");

            addRow(content, gbc, "Width", Integer.toString(image.getWidth()) + " pixels");

            addRow(content, gbc, "Height", Integer.toString(image.getHeight()) + " pixels");

            addRow(content, gbc, "Bands", Integer.toString(image.getSampleModel().getNumBands()));

            addRow(content, gbc, "Data type", getDataTypeName(image.getSampleModel().getDataType()));
        }

        if (coverage != null) {
            addSection(content, gbc, "Georeferencing");

            CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem2D();

            addRow(content, gbc, "Coordinate reference system", crs != null ? crs.getName().toString() : UNAVAILABLE);

            addRow(content, gbc, "CRS details", crs != null ? crs.toString() : UNAVAILABLE);

            Bounds bounds = coverage.getEnvelope();

            addRow(content, gbc, "Minimum X", formatNumber(bounds.getMinimum(0)));

            addRow(content, gbc, "Minimum Y", formatNumber(bounds.getMinimum(1)));

            addRow(content, gbc, "Maximum X", formatNumber(bounds.getMaximum(0)));

            addRow(content, gbc, "Maximum Y", formatNumber(bounds.getMaximum(1)));

            addRow(content, gbc, "Grid-to-CRS transform", coverage.getGridGeometry().getGridToCRS().toString());
        }

        if (metadata != null) {
            addSection(content, gbc, "TIFF Metadata");

            addRow(content, gbc, "Image description", getAsciiTag(metadata, "270"));

            addRow(content, gbc, "Software", getAsciiTag(metadata, "305"));

            addRow(content, gbc, "TIFF date/time", getAsciiTag(metadata, "306"));

            addRow(content, gbc, "Artist", getAsciiTag(metadata, "315"));

            addRow(content, gbc, "Copyright", getAsciiTag(metadata, "33432"));

            addSection(content, gbc, "GeoTIFF Metadata");

            addRow(content, gbc, "GeoKey directory", metadata.hasGeoKey() ? "Present" : "Not present");

            addRow(content, gbc, "Number of GeoKeys", Integer.toString(metadata.getNumGeoKeys()));

            addRow(content, gbc, "Pixel scale",
                    metadata.getModelPixelScales() != null ? metadata.getModelPixelScales().toString() : UNAVAILABLE);

            addRow(content, gbc, "Model transformation",
                    metadata.getModelTransformation() != null ? metadata.getModelTransformation().toString()
                            : UNAVAILABLE);

            addRow(content, gbc, "NoData value",
                    metadata.hasNoData() ? Double.toString(metadata.getNoData()) : UNAVAILABLE);
        }
    }

    private void addSection(JPanel panel, GridBagConstraints gbc, String title) {
        gbc.gridx = 0;
        gbc.gridy = ++row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(12, 4, 5, 4);

        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize()));
        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, label.getForeground()));

        panel.add(label, gbc);

        gbc.gridy++;
        row++;
        gbc.insets = new Insets(3, 4, 3, 4);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, String key, String value) {
        gbc.gridy = row++;

        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(3, 4, 3, 12);

        JLabel keyLabel = new JLabel(key + ":");
        keyLabel.setHorizontalAlignment(SwingConstants.LEFT);
        keyLabel.setFont(keyLabel.getFont().deriveFont(Font.BOLD));

        panel.add(keyLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(3, 4, 3, 4);

        JLabel valueLabel = new JLabel(
                "<html><div style='width: 320px;'>" + Utils.escapeReservedCharactersHTML(value) + "</div></html>");

        valueLabel.setToolTipText(value);
        panel.add(valueLabel, gbc);
    }

    private static String getAsciiTag(GeoTiffIIOMetadataDecoder metadata, String tagId) {
        try {
            String value = metadata.getAsciiTIFFTag(tagId);
            return value != null && !value.trim().isEmpty() ? value.trim() : UNAVAILABLE;
        } catch (RuntimeException e) {
            return UNAVAILABLE;
        }
    }

    private static String getDataTypeName(int type) {
        switch (type) {
        case DataBuffer.TYPE_BYTE:
            return "8-bit integer";
        case DataBuffer.TYPE_USHORT:
            return "16-bit unsigned integer";
        case DataBuffer.TYPE_SHORT:
            return "16-bit signed integer";
        case DataBuffer.TYPE_INT:
            return "32-bit integer";
        case DataBuffer.TYPE_FLOAT:
            return "32-bit floating point";
        case DataBuffer.TYPE_DOUBLE:
            return "64-bit floating point";
        default:
            return "Unknown (" + type + ")";
        }
    }

    private static String formatNumber(double value) {
        return Double.isFinite(value) ? NUMBER_FORMAT.format(value) : Double.toString(value);
    }
}
