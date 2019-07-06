package com.chairbender.mahjongeye;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Controller
public class MainController {
    @FXML
    private ComboBox<IndexedWebcam> webcams;
    @FXML
    private ImageView currentFrame;

    private ScheduledExecutorService frameGrabberExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<Runnable> currentFrameGrabber;

    @FXML
    private void initialize() {
        initializeWebcamDropdown();
    }

    private void initializeWebcamDropdown() {
        List<IndexedWebcam> indexedWebcamList = new ArrayList<>();
        int i = 0;
        for (Webcam cam : Webcam.getWebcams()) {
            indexedWebcamList.add(new IndexedWebcam(i++, cam));
        }
        webcams.setItems(FXCollections.observableArrayList(indexedWebcamList));

        webcams.getSelectionModel().selectedItemProperty().addListener(this::onSelectionChanged);
    }

    private void onSelectionChanged(ObservableValue<? extends IndexedWebcam> observableValue, IndexedWebcam oldValue, IndexedWebcam newValue) {
        if (oldValue != null) {
            stopWebcam(oldValue.webcam);
        }

        if (newValue == null) return;

        newValue.webcam.setViewSize(WebcamResolution.VGA.getSize());
        newValue.webcam.open();

        // grab a frame every 33 ms (30 frames/sec)
        Runnable grabber = () -> {
            BufferedImage img = newValue.webcam.getImage();
            final AtomicReference<WritableImage> ref = new AtomicReference<>();
            ref.set(SwingFXUtils.toFXImage(img, ref.get()));
            img.flush();
            Utils.onFXThread(currentFrame.imageProperty(), ref.get());
        };

        this.currentFrameGrabber = (ScheduledFuture<Runnable>) this.frameGrabberExecutor.scheduleAtFixedRate(grabber, 0, 33, TimeUnit.MILLISECONDS);
    }

    private void stopWebcam(Webcam toStop) {
        if (toStop == null) return;
        if (this.currentFrameGrabber != null) {
            this.currentFrameGrabber.cancel(true);
        }
        toStop.close();
    }

    public void shutdown() {
        if (webcams.getValue() != null) {
            stopWebcam(webcams.getValue().webcam);
        }

        frameGrabberExecutor.shutdown();
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
}


