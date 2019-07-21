package com.chairbender.mahjongeye;

import com.google.common.collect.Range;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Similar to Rect, but easier to work with (openCV Rect is a really
 * basic class). Defines a rectangular region
 */
public class Box {

    /**
     * rectangle defining this box
     */
    public final Rect rect;
    /**
     * lowest X value of the defined region
     */
    public final int startX;
    /**
     * highest X value of the defined region
     */
    public final int endX;
    /**
     * lowest Y value of the defined region
     */
    public final int startY;
    /**
     * highest Y value of the defined region
     */
    public final int endY;

    /**
     * Range of the x region (startX -> endX)
     */
    public final Range<Integer> xRange;

    /**
     * Range of the y region (startY -> endY)
     */
    public final Range<Integer> yRange;

    /**
     * bottom left
     */
    public final Point tl;
    /**
     * bottom right
     */
    public final Point tr;
    /**
     * top left
     */
    public final Point bl;
    /**
     * top right
     */
    public final Point br;

    public final Collection<Point> corners;


    protected Box(Rect rect) {
        this.rect = rect;
        this.startX = rect.x;
        this.startY = rect.y;
        this.endX = rect.x + rect.width;
        this.endY = rect.y + rect.height;
        this.xRange = Range.closed(startX, endX);
        this.yRange = Range.closed(startY, endY);
        this.tl = new Point(startX, startY);
        this.tr = new Point(endX, startY);
        this.bl = new Point(startX, endY);
        this.br = new Point(endX, endY);
        corners = Arrays.asList(tl, tr, bl, br);
    }

    /**
     * @param contour
     * @return a box which bounds the specified contour
     */
    public static Box boundingContour(Mat contour) {
        return new Box(Imgproc.boundingRect(contour));
    }

    /**
     * Melds boxes in the collection which are within the threshold distance of each other.
     * If 2 boxes are adjacent to each other, they will be didMeld together. If any other boxes
     * are adjacent to those boxes, they will be didMeld into the set as well.
     *
     * Note that this only does one pass through the boxes. It may be possible that some of the final melds
     * are close to each other but are not melded.
     *
     * @param boxes boxes to meld
     * @param threshold threshold distance
     */
    public static MeldResult meldAdjacent(List<Box> boxes, double threshold) {
        //create bounding boxes for each contour, with an ID for each rect
        Map<Integer, Box> idToBox =
                IntStream.range(0, boxes.size()).boxed()
                        .collect(Collectors.toMap(Function.identity(), i -> boxes.get(i)));

        //this map will hold the sets that each rect is a part of.
        //Map from rect ID to the set of rect IDs that are part of that rect's set
        Map<Integer, Set<Integer>> idToMeld =
                IntStream.range(0, boxes.size()).boxed()
                        //each set starts with the box as the only member
                        .collect(Collectors.toMap(Function.identity(), i -> new HashSet<>(Collections.singleton(i))));

        var melded = false;
        for (var box1Entry : idToBox.entrySet()) {
            for (var box2Entry : idToBox.entrySet()) {
                var box1 = box1Entry.getValue();
                var id1 = box1Entry.getKey();
                var set1 = idToMeld.get(id1);
                var box2 = box2Entry.getValue();
                var id2 = box2Entry.getKey();

                //skip if already in a set together
                if (set1.contains(id2)) continue;

                double distance = box1.shortestDistance(box2);
                if (distance < threshold) {
                    melded = true;
                    //add to set
                    set1.add(id2);
                    //update all other sets of all members of the set, overwriting the set
                    //with this box1's set.
                    set1.forEach(id -> idToMeld.put(id, set1));
                }
            }
        }

        //get all the unique sets that were created
        Collection<Collection<Box>> uniqueSets =
                idToMeld.values().stream().distinct()
                        .map(idset -> idset.stream().map(idToBox::get).collect(Collectors.toList()))
                        .collect(Collectors.toList());

        //create the boxes enclosing each set
        var finalMelds = uniqueSets.stream().map(Box::meld).collect(Collectors.toList());
        return new MeldResult(finalMelds, melded);
    }

    /**
     *
     * @param other
     * @return shortest distance between this and other, 0 if they overlap or contain each other
     */
    public double shortestDistance(Box other) {
        if (overlap(other)) {
            return 0;
        }

        //if the top or bottom edge of this box is between the yRange of the other (or vice versa),
        //then the boxes are to the left / right of each other, so the
        // shortest distance is the shortest distance between their left and right edges.
        if (other.yRange.contains(this.startY) || other.yRange.contains(this.endY) ||
            this.yRange.contains(other.startY) || this.yRange.contains(other.endY)) {
            return Math.min(Math.abs(this.startX - other.endX), Math.abs(this.endX - other.startX));
        }
        //if the left or right edge of this box is between the xRange of the other (or vice versa),
        //then the boxes are to the top / bottom of each other, so the
        // shortest distance is the shortest distance between their top and bottom edges.
        if (other.xRange.contains(this.startX) || other.xRange.contains(this.endX) ||
                this.xRange.contains(other.startX) || this.xRange.contains(other.endX)) {
            return Math.min(Math.abs(this.startY - other.endY), Math.abs(this.endY - other.startY));
        }

        //if their edges are not within range of each other, then they diagonally away from each other,
        //so the shortest distance is the shortest distance between their closest corners
        if (startX > other.endX) {
            //this is to the right of other
            if (startY > other.endY) {
                //this is above and to the right of other
                return euclid(tl, other.br);
            } else {
                //this is below and to the right of other
                return euclid(bl, other.tr);
            }
        } else {
            //this is to the left of other
            if (startY > other.endY) {
                //this is above and to the left of other
                return euclid(tr, other.bl);
            } else {
                //this is below and to the left of other
                return euclid(br, other.tl);
            }
        }
    }

    //opencv doesn't have a euclidian distance metric that is easy to use...sigh
    private static double euclid(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    /**
     *
     * @param other
     * @return true iff other overlaps with this (including if either contains the other entirely)
     */
    private boolean overlap(Box other) {
        //does this contains one of other's corners
        if (other.corners.stream().anyMatch(rect::contains)) return true;

        //does other contains one of this's corners
        if (this.corners.stream().anyMatch(other.rect::contains)) return true;

        //does this left edge intersect with other's top edge
        if (this.yRange.contains(other.endY) && other.xRange.contains(this.startX)) return true;
        //does this left edge intersect with other's bottom edge
        if (this.yRange.contains(other.startY) && other.xRange.contains(this.startX)) return true;

        //does this right edge intersect with other's top edge
        if (this.yRange.contains(other.endY) && other.xRange.contains(this.endX)) return true;
        //does this right edge intersect with other's bottom edge
        if (this.yRange.contains(other.startY) && other.xRange.contains(this.endX)) return true;

        //does this bottom edge intersect with other's right edge
        if (this.xRange.contains(other.endX) && other.yRange.contains(this.startY)) return true;
        //does this bottom edge intersect with other's left edge
        if (this.xRange.contains(other.startX) && other.yRange.contains(this.startY)) return true;

        //does this top edge intersect with other's right edge
        if (this.xRange.contains(other.endX) && other.yRange.contains(this.endY)) return true;
        //does this top edge intersect with other's left edge
        if (this.xRange.contains(other.startX) && other.yRange.contains(this.endY)) return true;

        return false;
    }

    /**
     *
     * @param toMeld
     * @return a box minimally enclosing all of the boxes in toMeld
     */
    public static Box meld(Collection<Box> toMeld) {
        int startX = toMeld.stream().map(b -> b.startX).min(Comparator.naturalOrder()).orElseThrow();
        int endX = toMeld.stream().map(b -> b.endX).max(Comparator.naturalOrder()).orElseThrow();
        int startY = toMeld.stream().map(b -> b.startY).min(Comparator.naturalOrder()).orElseThrow();
        int endY = toMeld.stream().map(b -> b.endY).max(Comparator.naturalOrder()).orElseThrow();

        return new Box(new Rect(startX, startY, endX - startX, endY - startY));
    }

    public Point center() {
        return new Point(startX + rect.width / 2.0, startY + rect.height / 2.0);
    }
}
