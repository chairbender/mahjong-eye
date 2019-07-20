# mahjong-eye
Using computer vision to enhance the game of mahjong.

# Setup
1. Install Java 12 or higher SDK.
1. Install JavaFX SDK 12 or higher: https://gluonhq.com/products/javafx/. 
1. Set the PATH_TO_FX environment variable to point to the place you installed the JavaFX SDK (you may need to restart your system to pick up this change). You can also do this via your IDE.
3. Download OpenCV for your system and put the correct JAR and dll into this project's folder.
4. Modify build.gradle to point to your opencv JAR
1. Try to run the MahjongeyeApplication via your IDE. It will probably fail but create a run configuration.
2. Add VM options to the run configuration to ensure JavaFX is loaded. It should look like below, but the add-modules argument should match the modules defined for javafx in build.gradle
   ```
   --module-path="C:\path\to\javafx-sdk-12.0.1\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web`
   ```

