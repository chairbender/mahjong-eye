package com.chairbender.mahjongeye;

import java.io.*;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
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
    private ImageView meldReferenceView;

    private Mat droppedImage;

    //holds the image prior to preprocessing
    private Mat rawImage;

    @FXML
    private ComboBox<MeldMat> meldSelection;
    @FXML
    private ComboBox<ReferenceImage> referenceSelection;

    @FXML
    private TextField threads;
    @FXML
    private TextField meldThreshold;
    @FXML
    private TextField minContourArea;
    @FXML
    private TextField maxContourArea;

    @FXML
    private BorderPane borderPane;

    @Autowired
    private Identifier identifier;

    //holds the contours calculated in the current snapshot
    private List<MatOfPoint> savedContours;
    //holds the saved calculated melds
    private MeldResult savedMelds;

    private List<MatProcessor> preprocessors;

    private ScheduledExecutorService frameGrabberExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<Runnable> currentFrameGrabber;

    private boolean stream = false;

    public String path = System.getProperty("user.dir");

    private String standardDir = path + "\\standard";

    @FXML
    private void initialize() {
        loadProperties();
        initializeReferences();
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
                        droppedImage = Utils.scaledImread(file.getAbsolutePath(), false);
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
        preprocessors.add(new MatProcessor("identify", this::identify));

        preprocessorSelection.setItems(FXCollections.observableArrayList(preprocessors));
        preprocessorSelection.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, newVal) -> resetFeed(webcamSelection.getValue(), webcamSelection.getValue()));

        //default to first item
        preprocessorSelection.getSelectionModel().select(0);
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
        Imgproc.threshold(src, dst, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);
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
        savedMelds = Box.meldAdjacent(boxes, threshold);

        //TODO: Repeat melding until no more melds are made (until meldResult.didMeld = false)

        //draw the melds if this is selected
        Mat drawMat = src.clone();
        if ("meld".equals(preprocessorSelection.getSelectionModel().getSelectedItem().name)) {
            savedMelds.melds.forEach(box -> Imgproc.rectangle(drawMat, box.rect, new Scalar(255, 255, 255), 3));

        }

        initializeSavedMelds(savedMelds, rawImage);

        return drawMat;
    }

    private Mat identify(Mat src) {
        //cant do anything if we haven't calculated melds - this needs to run after meld
        if (savedMelds == null) {
            return src;
        }

        //create matboxes from the source image
        var matBoxes = savedMelds.melds.stream()
                //TODO: Configurable padding
                .map(box -> MatBox.fromImage(box, rawImage, 5))
                .collect(Collectors.toList());

        Map<MatBox, String> identifications = identifier.identify(matBoxes, Integer.parseInt(threads.getText()));
        reinitializeSavedMelds(identifications);
        Mat textMat = rawImage.clone();
        for (var idEntry : identifications.entrySet()) {
            //skip unknown
            if (idEntry.getValue().equals("?")) {
                continue;
            }
            Imgproc.putText(textMat, idEntry.getValue(),
                    new Point(idEntry.getKey().startX, idEntry.getKey().startY + idEntry.getKey().rect.height / 2),
                    0, 2, new Scalar(0, 0, 255), 3);
        }
        return textMat;
    }
    //Allows for the selection of melds in a ComboBox
    private void initializeSavedMelds (MeldResult savedMelds, Mat rawImage) {

        var matBoxes = savedMelds.melds.stream()
                //TODO: Configurable padding
                .map(box -> MatBox.fromImage(box, rawImage, 5))
                .collect(Collectors.toList());

        List <MatBox> melds = matBoxes;

        List <MeldMat> meldMats = new ArrayList<>();

        int i = 0;
        for (MatBox meld: melds) {
            meldMats.add(new MeldMat("Meld" + String.valueOf(i), meld));
            i++;
        }

        meldSelection.setItems(FXCollections.observableArrayList(meldMats));

    }
    //After Identification has been done, reinitializes the meldSelection to show the identified names of melds
    private void reinitializeSavedMelds (Map<MatBox, String> identifications) {

        List <MeldMat> meldMats = new ArrayList<>();

        for (Map.Entry<MatBox, String> identification: identifications.entrySet()) {
            meldMats.add(new MeldMat(identification.getValue(), identification.getKey()));
        }

        meldSelection.setItems(FXCollections.observableArrayList(meldMats));
    }
    //Allows to choose a referenceImage through combobox
    private  void initializeReferences () {

        File[] files = new File(standardDir).listFiles();
        List <ReferenceImage> referenceImages = new ArrayList<>();

        for (File file: files) {
            if (file.isFile()) {
                if (file.getName().toLowerCase().endsWith(".jpg")) {
                    Mat mat = Utils.scaledImread(file.getPath(), true);
                    referenceImages.add(new ReferenceImage(file.getName(), mat));
                }
            }
        }
        referenceSelection.setItems(FXCollections.observableArrayList(referenceImages));
    }
    //Reinitializes referenceImages in order to only show those who have inliers
    private void reinitializeReferences(MatBox meld) {
        if (identifier.relevantReferences == null) return;
        Map<Mat,String> references = identifier.relevantReferences.get(meld);
        List <ReferenceImage> referenceImages = new ArrayList<>();

        for(Map.Entry<Mat,String> reference: references.entrySet()) {
            referenceImages.add(new ReferenceImage(reference.getValue(), reference.getKey()));
        }
        referenceSelection.setItems(FXCollections.observableArrayList(referenceImages));
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
        rawImage = newImage;
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
                mat = Utils.bufferedImage2StandardizedMat(img, false);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            updateImage(mat);
        }
    }

    public void onDisplayMeld() {

        MatBox meld = meldSelection.getSelectionModel().getSelectedItem().meld;

        Mat mat = meld.getMat();

        reinitializeReferences(meld);

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

        Utils.onFXThread(meldReferenceView.imageProperty(), ref.get());

    }

    public void onDisplayReference() {

        Mat mat = referenceSelection.getSelectionModel().getSelectedItem().mat;

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

        Utils.onFXThread(meldReferenceView.imageProperty(), ref.get());
    }

    public void onDisplayMatches() {
        MatBox meldBox = meldSelection.getSelectionModel().getSelectedItem().meld;
        Mat meld = meldBox.getMat();
        Mat reference = referenceSelection.getSelectionModel().getSelectedItem().mat;

        Mat matchImg = identifier.drawMatches(meld, reference);

        if (matchImg == null) {
            return;
        }

        BufferedImage finalImage = null;

        try {
            finalImage = Utils.mat2BufferedImage(matchImg);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        final AtomicReference<WritableImage> ref = new AtomicReference<>();
        ref.set(SwingFXUtils.toFXImage(finalImage, ref.get()));
        finalImage.flush();

        Utils.onFXThread(meldReferenceView.imageProperty(), ref.get());


    }

    //Triggers on Save button in rightmost region in UI
    public void onSave() throws IOException{
        String propDirectory = path + "\\src\\main\\resources\\config.properties";
        File prop = new File(propDirectory);
        if (prop.createNewFile()) {
            //Produces new properties file if one not found with along with warning message
            System.out.println("Warning: config.properties was not found so a new one was created");
            updateProperties(propDirectory);
        } else {
            updateProperties(propDirectory);
        }
    }

    //Updates the setting file with the current TextField Information in rightmost region in UI
    public void updateProperties (String propDirectory) throws IOException {

        try (OutputStream output = new FileOutputStream(propDirectory)) {
            Properties prop = new Properties();
            prop.setProperty("minContourArea", minContourArea.getText());
            prop.setProperty("maxContourArea", maxContourArea.getText());
            prop.setProperty("contourApproxEpsilon", contourApproxEpsilon.getText());
            prop.setProperty("meldThreshold", meldThreshold.getText());
            prop.store(output, null);
        }
    }


    public void loadProperties () {
        String path = System.getProperty("user.dir");
        String propDirectory = path + "\\src\\main\\resources\\config.properties";
        try (InputStream input = new FileInputStream(propDirectory)) {
            Properties prop = new Properties();
            prop.load(input);

            minContourArea.setText(prop.getProperty("minContourArea"));
            maxContourArea.setText(prop.getProperty("maxContourArea"));
            contourApproxEpsilon.setText(prop.getProperty("contourApproxEpsilon"));
            meldThreshold.setText(prop.getProperty("meldThreshold"));

        } catch (IOException ioe) {
            ioe.printStackTrace();
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

    private class MeldMat {
        public String name;
        public MatBox meld;

        public MeldMat(String name, MatBox meld) {
            this.name = name;
            this.meld = meld;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class ReferenceImage {
        public String name;
        public Mat mat;

        public ReferenceImage(String name, Mat mat) {
            this.name = name;
            this.mat = mat;
        }

        @Override
        public String toString() { return name; }
    }
}


