package com.chairbender.mahjongeye;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.opencv.core.Mat;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javax.imageio.ImageIO;

/**
 * Provide general purpose methods for handling OpenCV-JavaFX data conversion.
 * Moreover, expose some "low level" methods for matching few JavaFX behavior.
 *
 * @author <a href="mailto:luigi.derussis@polito.it">Luigi De Russis</a>
 * @author <a href="http://max-z.de">Maximilian Zuleger</a>
 * @version 1.0 (2016-09-17)
 * @since 1.0
 *
 */
public final class Utils
{

    private static final double FX = 0.2;
    private static final double FY = 0.2;

    /**
     * Standardizes - convert to standard size
     * @param src
     * @return
     */
    public static Mat standardize(Mat src) {
        //TODO: Make this configurable
        //TODO: This doesn't actually standardize - it applies a constant scale factor
        // which means that the image size will depend on the src image size.
        // It would be better to set a general bound on image size rather than scaling by a constant,
        //that way all images would have a standard size. I think this can be done using the Size parameter
        Mat result = new Mat();
        Imgproc.resize(src, result, new Size(0, 0), FX, FY);
        return result;
    }
    /**
     * Just like Imgcodecs.imread but scaled by a constant factor
     * @param filename
     * @return
     */
    public static Mat scaledImread(String filename) {
        return standardize(Imgcodecs.imread(filename));
    }

    /**
     * Converts the buffered image to a Mat and standardizes it
     * @param image
     * @return
     * @throws IOException
     */
    public static Mat bufferedImage2StandardizedMat(BufferedImage image) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", byteArrayOutputStream);
        byteArrayOutputStream.flush();
        return standardize(Imgcodecs.imdecode(new MatOfByte(byteArrayOutputStream.toByteArray()), Imgcodecs.IMREAD_UNCHANGED));
    }

    public static BufferedImage mat2BufferedImage(Mat matrix)throws IOException {
        MatOfByte mob=new MatOfByte();
        Imgcodecs.imencode(".jpg", matrix, mob);
        return ImageIO.read(new ByteArrayInputStream(mob.toArray()));
    }

    /**
     * Generic method for putting element running on a non-JavaFX thread on the
     * JavaFX thread, to properly update the UI
     *
     * @param property
     *            a {@link ObjectProperty}
     * @param value
     *            the value to set for the given {@link ObjectProperty}
     */
    public static <T> void onFXThread(final ObjectProperty<T> property, final T value)
    {
        Platform.runLater(() -> {
            property.set(value);
        });
    }


}