package com.chairbender.mahjongeye;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private Mat droppedImage;

    @FXML
    private TextField meldThreshold;
    @FXML
    private TextField minContourArea;
    @FXML
    private TextField maxContourArea;

    @FXML
    private BorderPane borderPane;

    //holds the contours calculated in the current snapshot
    private List<MatOfPoint> savedContours;

    private List<MatProcessor> preprocessors;

    private ScheduledExecutorService frameGrabberExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<Runnable> currentFrameGrabber;

    private boolean stream = false;

    @FXML
    private void initialize() {
        initializeWebcamDropdown();
        initializeProcessors();

        borderPane.setOnDragOver(e -> {
            final Dragboard db = e.getDragboard();

            final boolean isAccepted = db.getFiles().get(0).getName().toLowerCase().endsWith(".png")
                    || db.getFiles().get(0).getName().toLowerCase().endsWith(".jpeg")
                    || db.getFiles().get(0).getName().toLowerCase().endsWith(".jpg");

            if (db.hasFiles()) {
                if (isAccepted) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
            } else {
                e.consume();
            }
        });

        borderPane.setOnDragDropped(e -> {
            final Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                // Only get the first file from the list
                final File file = db.getFiles().get(0);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println(file.getAbsolutePath());
                        droppedImage = Imgcodecs.imread(file.getAbsolutePath());
                        updateImage(droppedImage);
                    }
                });
            }
            e.setDropCompleted(success);
            e.consume();
        });
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
        preprocessors.add(new MatProcessor("meld", this::meld));

        preprocessorSelection.setItems(FXCollections.observableArrayList(preprocessors));
        preprocessorSelection.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));

        //default to first item
        preprocessorSelection.getSelectionModel().select(0);
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
        savedContours = contours.stream()
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

        //only draw the contours if we are selected
        if ("contour".equals(preprocessorSelection.getSelectionModel().getSelectedItem().name)) {
            Mat color = new Mat();
            Imgproc.cvtColor(src, color, Imgproc.COLOR_GRAY2BGR);
            // if any contour exist...
            for (int i = 0; i < savedContours.size(); i++) {
                Imgproc.drawContours(color, savedContours, i, new Scalar(0, 0, 255), 3);
            }
            return color;
        } else {
            //dont draw the contours, just keep them saved.
            return src;
        }
    }

    private Mat meld(Mat src) {
        //this needs to run after contour so that the contours are already calculated
        if (savedContours == null) {
            return src;
        }

        //convert contours into boxes
        List<Box> boxes = savedContours.stream().map(Box::boundingContour).collect(Collectors.toList());
        double threshold = Double.parseDouble(meldThreshold.getText());
        var meldResult = Box.meldAdjacent(boxes, threshold);

        //TODO: Repeat melding until no more melds are made (until meldResult.didMeld = false)

        //draw the melds
        meldResult.melds.forEach(box -> Imgproc.rectangle(src, box.rect, new Scalar(0, 0, 255), 3));

        return src;
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

    private void updateImage(Mat newImage) {
        for (var processor : preprocessors) {
            newImage = processor.preprocess.apply(newImage);
            if (preprocessorSelection.getValue().equals(processor)) {
                break;
            }
        }

        BufferedImage finalImage = null;
        try {
            finalImage = Utils.mat2BufferedImage(newImage);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        final AtomicReference<WritableImage> ref = new AtomicReference<>();
        ref.set(SwingFXUtils.toFXImage(finalImage, ref.get()));
        finalImage.flush();

        Utils.onFXThread(currentFrame.imageProperty(), ref.get());
    }

    public void onSnap() {
        //if there's no webcam selected and there's a dropped image, resnap the dropped image
        if (webcamSelection.getValue() == null && droppedImage != null) {
            updateImage(droppedImage);
        } else if (webcamSelection.getValue() != null) {
            //use the webcam
            BufferedImage img = webcamSelection.getValue().webcam.getImage();
            Mat mat = null;
            try {
                mat = Utils.bufferedImage2Mat(img);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            updateImage(mat);
        }
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


