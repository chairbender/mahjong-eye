# mahjong-eye
Using computer vision to enhance the game of mahjong.

# Setup
1. Install JavaFX SDK. Make sure to set the PATH_TO_FX environment variable as well
as in your IDE.
2. Add VM options to ensure JavaFX is loaded. It should look like below, but the add-modules argument should match the modules defined for javafx in build.gradle
   ```
   --module-path="C:\path\to\javafx-sdk-12.0.1\lib" --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.web`
   ```
3. Download OpenCV for your system and put the correct JAR and dll into this project's folder.
4. Modify build.gradle to point to your opencv JAR
