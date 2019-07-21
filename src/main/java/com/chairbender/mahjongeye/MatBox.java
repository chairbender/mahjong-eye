package com.chairbender.mahjongeye;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * Box with an associated Mat, representing the portion of the image that the
 * box is around.
 */
public class MatBox extends Box {
    private Mat mat;

    protected MatBox(Rect rect, Mat mat) {
        super(rect);
        this.mat = mat;
    }

    /**
     *
     * @param box box to use
     * @param srcImage src image that box is selecting a region within
     * @param padding padding to include around box when getting the image
     * @return a MatBox whose Mat is taken from the region in srcImage that box is selecting.
     */
    public static MatBox fromImage(Box box, Mat srcImage, int padding) {
        //note: using math.min/max to avoid having a box that extends past the edges of the src image
        int startX = Math.max(0, box.rect.x - padding);
        int startY = Math.max(0, box.rect.y - padding);
        int endX = Math.min(srcImage.width(), box.endX + padding);
        int endY = Math.min(srcImage.height(), box.endY + padding);
        Rect paddedRect = new Rect(startX, startY, endX - startX, endY - startY);
        Mat mat = srcImage.submat(paddedRect);
        return new MatBox(box.rect, mat);
    }

    /**
     *
     * @return the Mat containing the portion of the src image this box is around
     */
    public Mat getMat() {
        return mat;
    }
}
