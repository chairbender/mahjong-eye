package com.chairbender.mahjongeye;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles identification of tiles
 */
@Component
public class Identifier {

    //figuring this out from: https://github.com/opencv/opencv/blob/b39cd06249213220e802bb64260727711d9fc98c/modules/flann/include/opencv2/flann/params.h
    //and https://github.com/opencv/opencv/blob/5fb0f34e8ab9002b1e222fb5ab87f91db8ad7bcf/modules/flann/include/opencv2/flann/miniflann.hpp
    private static final String flannKDTreeYML = "%YAML:1.0\n" +
            "---\n" +
            "format: 3\n" +
            "indexParams:\n" +
            "  -\n" +
            "    name: algorithm\n" +
            "    type: 9\n" +
            "    value: 1\n" +
            "  -\n" +
            "    name: trees\n" +
            "    type: 4\n" +
            "    value: 5\n" +
            "searchParams:\n" +
            "  -\n" +
            "    name: checks\n" +
            "    type: 4\n" +
            "    value: 50\n" +
            "  -\n" +
            "    name: eps\n" +
            "    type: 5\n" +
            "    value: 0.\n" +
            "  -\n" +
            "    name: sorted\n" +
            "    type: 8\n" +
            "    value: 1";

    //https://github.com/opencv/opencv/blob/b39cd06249213220e802bb64260727711d9fc98c/modules/flann/include/opencv2/flann/lsh_index.h
    private static final String flannLSHYML = "%YAML:1.0\n" +
            "---\n" +
            "format: 3\n" +
            "indexParams:\n" +
            "  -\n" +
            "    name: algorithm\n" +
            "    type: 9\n" +
            "    value: 6\n" +
            "  -\n" +
            "    name: table_number\n" +
            "    type: 4\n" +
            "    value: 12\n" +
            "  -\n" +
            "    name: key_size\n" +
            "    type: 4\n" +
            "    value: 20\n" +
            "  -\n" +
            "    name: multi_probe_level\n" +
            "    type: 4\n" +
            "    value: 2\n" +
            "searchParams:\n" +
            "  -\n" +
            "    name: checks\n" +
            "    type: 4\n" +
            "    value: 50\n" +
            "  -\n" +
            "    name: eps\n" +
            "    type: 5\n" +
            "    value: 0.\n" +
            "  -\n" +
            "    name: sorted\n" +
            "    type: 8\n" +
            "    value: 1";

    @Autowired
    private MahjongEyeConfig config;
    private static final int MIN_MATCH_COUNT = 4;

    //map from img file name to the image
    private Map<String, Mat> nameToReferenceImage;
    private DescriptorMatcher flannMatcher;
    private KAZE kaze;

    public HashMap<MatBox,Map<Mat,String>> relevantReferences;

    @PostConstruct
    private void init() throws IOException {

        //generate the map
        nameToReferenceImage = Files.walk(config.getStandardDir())
                .filter(p -> p.getFileName().toString().endsWith(".jpg") &&
                        //there's some weird file
                        !p.getFileName().toString().contains("resized"))
                .collect(Collectors.toMap(p -> fileNameToTileName(p.getFileName().toString()), p ->
                        Utils.scaledImread(p.toAbsolutePath().toString(), true)));
        //initialize matcher
        //TODO: Probably there's a more sophisticated approach for this, such as a NN
        //stupidly, the only way to configure the matcher is to feed it a yml file, lol
        //https://answers.opencv.org/question/12429/matching-orb-descriptors-with-flann-lsh-on-android/?answer=12460#post-id-12460
        //https://github.com/opencv/opencv_attic/blob/master/opencv/modules/java/android_test/src/org/opencv/test/features2d/FlannBasedDescriptorMatcherTest.java
        flannMatcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        //write the config string to a file so it can be read in by flann matcher (lol)
        //TODO: Make this easier
        Files.writeString(Paths.get("flann.yml"), flannKDTreeYML);
        flannMatcher.read("flann.yml");
        kaze = KAZE.create();
    }

    private String fileNameToTileName(String filename) {
        //gives better names for the images
        String[] splits = filename.replaceAll(Pattern.quote(".jpg"), "").split(Pattern.quote("-"));
        var suit = splits[0];
        var value = splits[1];
        suit = suit
                .replaceAll("circles", "p")
                .replaceAll("sticks", "s")
                .replaceAll("wan", "m")
                .replaceAll("honor","");
        value = value
                .replaceAll("one","1")
                .replaceAll("two","2")
                .replaceAll("three","3")
                .replaceAll("four","4")
                .replaceAll("five","5")
                .replaceAll("six","6")
                .replaceAll("seven","7")
                .replaceAll("eight","8")
                .replaceAll("nine","9")
                .replaceAll("east","e")
                .replaceAll("south","s")
                .replaceAll("west","w")
                .replaceAll("north","n")
                .replaceAll("red","r")
                .replaceAll("white","w")
                .replaceAll("green","g");


        return value + suit;
    }

    /**
     * Using the reference images, for each box, finds the reference image that has the most inliers and labels
     * the box as such.
     *
     * @param melds melds to identify
     * @return a map from the box to the label detected for that box (based on the jpg file name it has
     * the most inliers with).
     */
    public Map<MatBox, String> identify(List<MatBox> melds, int threads) {

        var executorService = Executors.newFixedThreadPool(threads);

        relevantReferences = new HashMap<>();
        var result = new HashMap<MatBox, String>();
        int i = 0;
        for (MatBox meld : melds) {
            System.out.println("Comparing meld " + i++);
            var futures = new LinkedList<Future<InlierResult>>();
            //create futures to run our inlier method in parallel
            for (Map.Entry<String, Mat> referenceEntry : nameToReferenceImage.entrySet()) {
                futures.add(executorService.submit(() -> findInliers(meld, referenceEntry.getValue(), referenceEntry.getKey())));
            }

            //check the values returned by our parallel tasks
            long max = 0;
            InlierResult bestResult = null;
            var referenceMatToLabel = new HashMap<Mat, String>();
            for (var future : futures) {
                //await completion of the future
                InlierResult inliers = null;
                try {
                    inliers = future.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    continue;
                }

                if (inliers.inlierCount > max) {
                    bestResult = inliers;
                    max = bestResult.inlierCount;
                }
                if (inliers.inlierCount > 0) {
                    referenceMatToLabel.put(nameToReferenceImage.get(inliers.referenceName), inliers.referenceName + "(" + inliers.inlierCount + ")");
                }
            }

            if (bestResult != null) {
                referenceMatToLabel.put(nameToReferenceImage.get(bestResult.referenceName), bestResult.referenceName + "(" + bestResult.inlierCount + " Best Match)");
                result.put(bestResult.srcMeld, bestResult.referenceName);
            }

            relevantReferences.put(meld, referenceMatToLabel);
        }

        return result;
    }

    /**
     * Uses KAZE to find inliers, returns the count of inliers
     *
     */
    private InlierResult findInliers(MatBox src, Mat reference, String referenceName) {
        //based on this
        //https://docs.opencv.org/3.4/d7/dff/tutorial_feature_homography.html
        System.out.println("Comparing to " + referenceName);

        MatOfKeyPoint kpSrc = new MatOfKeyPoint();
        Mat desSrc = new Mat();
        kaze.detectAndCompute(src.getMat(), new Mat(), kpSrc, desSrc);
        MatOfKeyPoint kpRef = new MatOfKeyPoint();
        Mat desRef = new Mat();
        kaze.detectAndCompute(reference, new Mat(), kpRef, desRef);

        //search for matches among the descriptors.
        List<MatOfDMatch> matches = new ArrayList<>();
        flannMatcher.knnMatch(desSrc, desRef, matches, 2);

        //find good matches using lowe's ratio test
        List<DMatch> goodMatches = new ArrayList<>();
        for (var matofmatch : matches) {
            var matcharray = matofmatch.toArray();
            if (matcharray.length < 2) continue;
            var srcmatch = matcharray[0];
            var refmatch = matcharray[1];

            //TODO: Make configurable
            if (srcmatch.distance < 0.7 * refmatch.distance) {
                goodMatches.add(srcmatch);
            }
        }

        //TODO: Make configurable
        if (goodMatches.size() > MIN_MATCH_COUNT) {

            //get the keypoints from the good matches so we can do homography
            List<KeyPoint> kplistSrc = kpSrc.toList();
            List<KeyPoint> kplistRef = kpRef.toList();
            List<Point> srcPts = new ArrayList<>();
            List<Point> refPts = new ArrayList<>();
            for (var match : goodMatches) {
                srcPts.add(kplistSrc.get(match.queryIdx).pt);
                refPts.add(kplistRef.get(match.trainIdx).pt);
            }
            MatOfPoint2f srcMat = new MatOfPoint2f(), refMat = new MatOfPoint2f();
            srcMat.fromList(srcPts);
            refMat.fromList(refPts);
            //TODO: make configurable
            Mat mask = new Mat();
            Calib3d.findHomography( srcMat, refMat, Calib3d.RANSAC, 5.0, mask);
            //I think this is okay...we just need the size of the mask, that's our inlier count
            System.out.println("Done comparing to " + referenceName);
            return new InlierResult(referenceName, mask.total(), src);
        } else {
            //not enough matches
            System.out.println("Done comparing to " + referenceName);
            return new InlierResult(referenceName, 0, src);
        }

    }

    public Mat drawMatches(Mat src, Mat reference) {
        //based on this
        //https://docs.opencv.org/3.4/d7/dff/tutorial_feature_homography.html

        MatOfKeyPoint kpSrc = new MatOfKeyPoint();
        Mat desSrc = new Mat();
        kaze.detectAndCompute(src, new Mat(), kpSrc, desSrc);
        MatOfKeyPoint kpRef = new MatOfKeyPoint();
        Mat desRef = new Mat();
        kaze.detectAndCompute(reference, new Mat(), kpRef, desRef);

        //search for matches among the descriptors.
        List<MatOfDMatch> matches = new ArrayList<>();
        flannMatcher.knnMatch(desSrc, desRef, matches, 2);

        //find good matches using lowe's ratio test
        List<DMatch> goodMatches = new ArrayList<>();
        for (var matofmatch : matches) {
            var matcharray = matofmatch.toArray();
            if (matcharray.length < 2) continue;
            var srcmatch = matcharray[0];
            var refmatch = matcharray[1];

            //TODO: Make configurable
            if (srcmatch.distance < 0.7 * refmatch.distance) {
                goodMatches.add(srcmatch);
            }
        }

        //TODO: Make configurable
        if (goodMatches.size() > MIN_MATCH_COUNT) {

            MatOfDMatch matchMat = new MatOfDMatch(goodMatches.toArray(new DMatch[0]));
            Mat matchImg = new Mat();
            Features2d.drawMatches(src, kpSrc, reference, kpRef, matchMat, matchImg, Scalar.all(-1), Scalar.all(-1));
            //get the keypoints from the good matches so we can do homography
            return matchImg;
        } else {
            return null;

        }
    }

    private static class InlierResult {
        private String referenceName;
        private long inlierCount;
        private MatBox srcMeld;

        public InlierResult(String referenceName, long inlierCount, MatBox srcMeld) {
            this.referenceName = referenceName;
            this.inlierCount = inlierCount;
            this.srcMeld = srcMeld;
        }
    }
}
