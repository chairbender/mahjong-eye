package com.chairbender.mahjongeye;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class MainController {
    @FXML
    public TextField contourApproxEpsilon;
    @FXML
    private ComboBox<IndexedWebcam> webcamSelection;
    @FXML
    private ComboBox<MatProcessor> preprocessorSelection;
    @FXML
    private ImageView currentFrame;

    @FXML
    private TextField blockSize;
    @FXML
    private TextField thresholdC;
    @FXML
    private TextField minContourArea;
    @FXML
    private TextField maxContourArea;

    private List<MatProcessor> preprocessors;

    private ScheduledExecutorService frameGrabberExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<Runnable> currentFrameGrabber;

    private boolean stream = false;

    @FXML
    private void initialize() {
        initializeWebcamDropdown();
        initializeProcessors();
        blockSize.textProperty().addListener((obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
        thresholdC.textProperty().addListener((obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
        minContourArea.textProperty().addListener((obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
        maxContourArea.textProperty().addListener((obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
        contourApproxEpsilon.textProperty().addListener((obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
    }

    private void initializeWebcamDropdown() {
        List<IndexedWebcam> indexedWebcamList = new ArrayList<>();
        int i = 0;
        for (Webcam cam : Webcam.getWebcams()) {
            indexedWebcamList.add(new IndexedWebcam(i++, cam));
        }
        webcamSelection.setItems(FXCollections.observableArrayList(indexedWebcamList));

        webcamSelection.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> resetFeed(old, newVal));
    }

    private void initializeProcessors() {
        preprocessors = new ArrayList<>();

        //preprocessors.add(new MatProcessor("blur", this::blur));
        preprocessors.add(new MatProcessor("grayscale", this::grayscale));
        preprocessors.add(new MatProcessor("threshold", this::threshold));
        preprocessors.add(new MatProcessor("contour", this::contour));

        preprocessorSelection.setItems(FXCollections.observableArrayList(preprocessors));
        preprocessorSelection.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));
    }

    //TODO: Maybe blur?
    private Mat blur(Mat src) {
        var dst = new Mat();
        Imgproc.blur(src, dst, new Size(7, 7));
        return dst;
    }

    private Mat grayscale(Mat src) {
        var dst = new Mat();
        Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2GRAY);
        return dst;
    }

    private Mat threshold(Mat src) {
        var dst = new Mat();
        //Imgproc.adaptiveThreshold(src, dst, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV,
        //        Integer.parseInt(blockSize.getText()), Integer.parseInt(thresholdC.getText()));
        Imgproc.threshold(src, dst, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
        return dst;
    }

    private Mat contour(Mat src) {
        var hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(src, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        //filter out contours by area
        int min = Integer.parseInt(minContourArea.getText());
        int max = Integer.parseInt(maxContourArea.getText());
        List<MatOfPoint> filteredContours = contours.stream()
                //min / max area
                .filter(cont -> {
                    var area = Imgproc.contourArea(cont);
                    return area > min && area < max;
                })
                //contour approximation
                .map( cont -> {
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(new MatOfPoint2f(cont.toArray()), approx, Double.parseDouble(contourApproxEpsilon.getText()), true);
                    return new MatOfPoint(approx.toArray());
                })
                //convex hull
                .map( cont -> {
                    MatOfInt hull = new MatOfInt();
                    Imgproc.convexHull(cont, hull);
                    return convertIndexesToPoints(cont, hull);
                })
                //remove empty contours
                .filter(cont -> !cont.empty())
                .collect(Collectors.toList());


        Mat color = new Mat();
        Imgproc.cvtColor(src, color, Imgproc.COLOR_GRAY2BGR);
        // if any contour exist...
        for (int i = 0; i < filteredContours.size(); i++) {
            Imgproc.drawContours(color, filteredContours, i, new Scalar(0, 0, 255), 3);
        }
        return color;
    }

    public static MatOfPoint convertIndexesToPoints(MatOfPoint contour, MatOfInt indexes) {
        int[] arrIndex = indexes.toArray();
        Point[] arrContour = contour.toArray();
        Point[] arrPoints = new Point[arrIndex.length];

        for (int i=0;i<arrIndex.length;i++) {
            arrPoints[i] = arrContour[arrIndex[i]];
        }

        MatOfPoint hull = new MatOfPoint();
        hull.fromArray(arrPoints);
        return hull;
    }

    //must call this any time we change a setting
    private void resetFeed(IndexedWebcam oldWebcam, IndexedWebcam newWebcam) {
        //stop the old if needed
        if (oldWebcam != null && oldWebcam != newWebcam) {
            oldWebcam.webcam.close();
        }

        //start the new if webcam changed
        if (oldWebcam != newWebcam) {
            newWebcam.webcam.setViewSize(WebcamResolution.VGA.getSize());
            newWebcam.webcam.open();
        }

        //always stop the old grabber
        if (this.currentFrameGrabber != null) {
            this.currentFrameGrabber.cancel(true);
            this.currentFrameGrabber = null;
        }

        if (stream) {
            Runnable grabber = () -> {
                onSnap();
            };

            this.currentFrameGrabber = (ScheduledFuture<Runnable>) this.frameGrabberExecutor.scheduleAtFixedRate(grabber, 0, 500, TimeUnit.MILLISECONDS);
        }
    }

    public void shutdown() {
        if (webcamSelection.getValue() != null) {
            webcamSelection.getValue().webcam.close();
        }

        frameGrabberExecutor.shutdown();
    }

    public void onSnap() {
        BufferedImage img = webcamSelection.getValue().webcam.getImage();
        Mat mat = null;
        try {
            mat = Utils.bufferedImage2Mat(img);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (var processor : preprocessors) {
            mat = processor.preprocess.apply(mat);
            if (preprocessorSelection.getValue().equals(processor)) {
                break;
            }
        }

        BufferedImage finalImage = null;
        try {
            finalImage = Utils.mat2BufferedImage(mat);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        final AtomicReference<WritableImage> ref = new AtomicReference<>();
        ref.set(SwingFXUtils.toFXImage(finalImage, ref.get()));
        finalImage.flush();

        Utils.onFXThread(currentFrame.imageProperty(), ref.get());
    }

    private class IndexedWebcam {
        private int index;
        private Webcam webcam;

        public IndexedWebcam(int index, Webcam webcam) {
            this.index = index;
            this.webcam = webcam;
        }

        @Override
        public String toString() {
            return webcam.getName();
        }
    }

    private class MatProcessor {
        public String name;
        public Function<Mat, Mat> preprocess;

        public MatProcessor(String name, Function<Mat, Mat> preprocess) {
            this.name = name;
            this.preprocess = preprocess;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}


