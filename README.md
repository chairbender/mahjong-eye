# mahjong-eye
Using computer vision to enhance the game of mahjong.

# Setup
1. Install Java 12 or higher SDK.
1. Install JavaFX SDK 12 or higher: https://gluonhq.com/products/javafx/. 
1. Set the PATH_TO_FX environment variable to point to the javafx/lib folder. On windows, you will need to restart to pick up this change. Here's how it looks on my system:
    ````
    PATH_TO_FX=C:\Programming\javafx-sdk-12.0.1\lib
    ````
3. Download latest OpenCV for your system and extract it: https://opencv.org/releases/
4. From the extracted opencv folder, put the correct JAR and dll into this project's folder. For example, on Windows 64 bit and opencv 4.1, you should copy the jar from build/java and the dll from build/java/x64/ into mahjong-eye/.
4. If you are using a version of OpenCV newer than 4.1, modify the reference to the opencv-410.jar to point to the newer JAR you just copied.
5. You can run using gradle wrapper:
````
gradlew.bat run
````

If you want to launch via your IDE (not recommended):
1. Try to run the MahjongeyeApplication via your IDE. It will probably fail but create a run configuration.
2. Add VM options to the run configuration to ensure JavaFX is loaded. It should look like below, but the add-modules argument should match the modules defined for javafx in build.gradle
   ```
   --module-path="C:\path\to\javafx-sdk-12.0.1\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web`
   ```
3. Try running again, it should now work.

Note that if launching via IDE, you will need to update the --add-modules if you ever add a new module. This it is recommended to run via Gradle.

