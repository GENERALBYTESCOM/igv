package org.broad.igv.hic;

import org.apache.commons.math.linear.RealMatrix;
import org.broad.igv.hic.data.Block;
import org.broad.igv.hic.data.ContactRecord;
import org.broad.igv.hic.data.DensityFunction;
import org.broad.igv.hic.data.MatrixZoomData;
import org.broad.igv.renderer.ColorScale;

import java.awt.*;
import java.util.List;

/**
 * @author jrobinso
 * @date Aug 11, 2010
 */
public class HeatmapRenderer {

    // TODO -- introduce a "model" in lieu of MainWindow pointer
    HiC hic;
    MainWindow mainWindow;

    private ObservedColorScale observedColorScale;
    private ColorScale oeColorScale;
    private ColorScale pearsonColorScale;

    public HeatmapRenderer(MainWindow mainWindow, HiC hic) {
        this.mainWindow = mainWindow;
        this.hic = hic;

        int initialMaxCount = 50;  // TODO -- record stats with data and estimate this
        observedColorScale = new ObservedColorScale();
        observedColorScale.setMaxCount(initialMaxCount);
        observedColorScale.setBackground(Color.white);
        oeColorScale = new HiCColorScale();
        pearsonColorScale = new HiCColorScale();
    }

    public void render(int originX,
                       int originY,
                       int width,
                       int height,
                       final MatrixZoomData zd,
                       MainWindow.DisplayOption displayOption,
                       Graphics2D g) {

        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        int chr1 = zd.getChr1();
        int chr2 = zd.getChr2();


        int x = originX;
        int y = originY;

        boolean isWholeGenome = zd.getChr1() == 0 && zd.getChr2() == 0;
        boolean sameChr = (chr1 == chr2);
        double binSizeMB = zd.getBinSize() / (isWholeGenome ? 1000.0 : 1000000.0);

        if (sameChr) {
            // Data is transposable, transpose if neccessary.  Convention is to use lower diagonal
            if (x > y) {
                x = originY;
                y = originX;
                int tmp = width;
                width = height;
                height = tmp;
            }
        }

        int maxX = x + width;
        int maxY = y + height;

        ColorScale colorScale = getColorScale();

        if (displayOption == MainWindow.DisplayOption.PEARSON) {
            RealMatrix pearsonsMatrix = zd.getPearsons();
            if (pearsonsMatrix != null) {
                ((HiCColorScale) colorScale).setMin((float) zd.getPearsonsMin());
                ((HiCColorScale) colorScale).setMax((float) zd.getPearsonsMax());
                renderMatrix(pearsonsMatrix, originX, originY, width, height, colorScale, g);

            }
        } else {
            // Iterate through blocks overlapping visible region
            DensityFunction df = null;
            if (displayOption == MainWindow.DisplayOption.OE) {
                df = hic.getDensityFunction(zd.getZoom());
            }

            List<Block> blocks = zd.getBlocksOverlapping(x, y, maxX, maxY);
            for (Block b : blocks) {
                renderBlock(originX, originY, chr1, chr2, binSizeMB, b, colorScale, df, g);
            }
        }
    }


    private ColorScale getColorScale() {

        switch (hic.getDisplayOption()) {
            case OE:
                return oeColorScale;
            case PEARSON:
                return pearsonColorScale;
            default:
                return observedColorScale;
        }
    }


    private void renderBlock(int originX, int originY, int chr1, int chr2, double binSizeMB, Block b,
                             ColorScale colorScale, DensityFunction df, Graphics2D g) {

        MainWindow.DisplayOption displayOption = hic.getDisplayOption();
        double binSizeMB2 = binSizeMB * binSizeMB;
        boolean sameChr = (chr1 == chr2);

        ContactRecord[] recs = b.getContactRecords();
        if (recs != null) {
            for (int i = 0; i < recs.length; i++) {
                ContactRecord rec = recs[i];

                Color color = null;
                double score;
                // This is weirdly not the same as computeOE.  We opt for speed instead of
                // showing the actual O/E with the mean subtracted, which is more appropriate.
                if (displayOption == MainWindow.DisplayOption.OE && df != null) {
                    int x = rec.getX();// * binSize;
                    int y = rec.getY();// * binSize;
                    int dist = Math.abs(x - y);
                    double expected = df.getDensity(chr1, dist);
                    score = rec.getCounts() / expected;
                    score = Math.log10(score);
                } else {
                    score = rec.getCounts() / binSizeMB2;
                }

                color = colorScale.getColor((float) score);
                int px = (rec.getX() - originX);
                int py = (rec.getY() - originY);
                g.setColor(color);
                // TODO -- need to check right bounds before drawing
                if (px > -1 && py > -1) {
                    g.fillRect(px, py, MainWindow.BIN_PIXEL_WIDTH, MainWindow.BIN_PIXEL_WIDTH);
                }

                if (sameChr && (rec.getX() != rec.getY())) {
                    px = (rec.getY() - originX);
                    py = (rec.getX() - originY);
                    if (px > -1 && py > -1) {
                        g.fillRect(px, py, MainWindow.BIN_PIXEL_WIDTH, MainWindow.BIN_PIXEL_WIDTH);
                    }
                }
            }
        }
    }

    /**
     * Used for Pearsons correlation (dense matrix).  The bitmap is drawn at 1 data point per pixel, scaling
     * happens elsewhere.
     *
     * @param originX    origin in pixels
     * @param originY    origin in pixels
     * @param rm
     * @param colorScale
     * @param g
     */
    private void renderMatrix(RealMatrix rm, int originX, int originY, int width, int height,
                              ColorScale colorScale, Graphics g) {


        int endX = Math.min(originX + width, rm.getColumnDimension());
        int endY = Math.min(originY + height, rm.getRowDimension());

       RealMatrix subMatrix = rm.getSubMatrix(originY, endY-1, originX, endX-1);

        // TODO -- need to check bounds before drawing
        for (int row = 0; row < subMatrix.getRowDimension(); row++) {
            for (int col = 0; col < subMatrix.getColumnDimension(); col++) {

                double score = subMatrix.getEntry(row, col);
                Color color;
                if (Double.isNaN(score)) {
                    color = Color.gray;
                } else {
                    color = score == 0 ? Color.black : colorScale.getColor((float) score);
                }
                int px = col - originX;
                int py = row - originY;
                g.setColor(color);
                g.fillRect(px, py, MainWindow.BIN_PIXEL_WIDTH, MainWindow.BIN_PIXEL_WIDTH);
                // Assuming same chromosome
                if (col != row) {
                    px = (row - originX);
                    py = (col - originY);
                    g.fillRect(px, py, MainWindow.BIN_PIXEL_WIDTH, MainWindow.BIN_PIXEL_WIDTH);
                }
            }
        }
    }


    public void setObservedRange(int min, int max) {
        observedColorScale.setRange(min, max);
    }
}
