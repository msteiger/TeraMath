package org.terasology.math.geom;

import java.util.ArrayList;
import java.util.List;

import org.terasology.math.delaunay.Winding;

public final class Polygon {

    private final List<Point> vertices;

    public Polygon(List<Point> vertices) {
        this.vertices = new ArrayList<Point>(vertices);
    }

    public double area() {
        return Math.abs(signedDoubleArea() * 0.5);
    }

    public Winding winding() {
        double signedDoubleArea = signedDoubleArea();
        if (signedDoubleArea < 0) {
            return Winding.CLOCKWISE;
        }
        if (signedDoubleArea > 0) {
            return Winding.COUNTERCLOCKWISE;
        }
        return Winding.NONE;
    }

    private double signedDoubleArea() {
        int index, nextIndex;
        int n = vertices.size();
        Point point, next;
        double signedDoubleArea = 0;
        for (index = 0; index < n; ++index) {
            nextIndex = (index + 1) % n;
            point = vertices.get(index);
            next = vertices.get(nextIndex);
            signedDoubleArea += point.x * next.y - next.x * point.y;
        }
        return signedDoubleArea;
    }
}