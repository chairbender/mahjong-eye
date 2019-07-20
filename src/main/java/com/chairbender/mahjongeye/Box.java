package com.chairbender.mahjongeye;

import com.google.common.collect.Range;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

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
    public final Point bl;
    /**
     * bottom right
     */
    public final Point br;
    /**
     * top left
     */
    public final Point tl;
    /**
     * top right
     */
    public final Point tr;

    public final Collection<Point> corners;


    public Box(Rect rect) {
        this.rect = rect;
        this.startX = rect.x;
        this.startY = rect.y;
        this.endX = rect.x + rect.width;
        this.endY = rect.y + rect.height;
        this.xRange = Range.closed(startX, endX);
        this.yRange = Range.closed(startY, endY);
        this.bl  = new Point(startX, startY);
        this.br  = new Point(endX, startY);
        this.tl  = new Point(startX, endY);
        this.tr  = new Point(endX, endY);
        corners = Arrays.asList(bl, br, tl, tr);
    }

    /**
     * @param contour
     * @return a box which bounds the specified contour
     */
    public static Box boundingContour(Mat contour) {
        return new Box(Imgproc.boundingRect(contour));
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
                return euclid(bl, other.tr);
            } else {
                //this is below and to the right of other
                return euclid(tl, other.br);
            }
        } else {
            //this is to the left of other
            if (startY > other.endY) {
                //this is above and to the left of other
                return euclid(br, other.tl);
            } else {
                //this is below and to the left of other
                return euclid(tr, other.bl);
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
}
