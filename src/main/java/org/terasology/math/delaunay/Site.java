package org.terasology.math.delaunay;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

import org.terasology.math.geom.Point;
import org.terasology.math.geom.Polygon;
import org.terasology.math.geom.Rectangle;

public final class Site implements ICoord {

    private double weight;
    private int siteIndex;
    
    /** 
     * the edges that define this Site's Voronoi region:
     */
    public List<Edge> _edges = new ArrayList<Edge>();
    
    /** 
     * which end of each edge hooks up with the previous edge in _edges:
     */
    private List<LR> _edgeOrientations;
    
    /** 
     * ordered list of points that define the region clipped to bounds:
     */
    private List<Point> _region;

    
    public static Site create(Point p, int index, double weight) {
        return new Site(p, index, weight);
    }

    public static void sortSites(List<Site> sites) {
        //sites.sort(Site.compare);
        Collections.sort(sites, new Comparator<Site>() {
            @Override
            public int compare(Site o1, Site o2) {
                return (int) Site.compare(o1, o2);
            }
        });
    }

    /**
     * sort sites on y, then x, coord also change each site's _siteIndex to
     * match its new position in the list so the _siteIndex can be used to
     * identify the site for nearest-neighbor queries
     *
     * haha "also" - means more than one responsibility...
     *
     */
    private static double compare(Site s1, Site s2) {
        int returnValue = Voronoi.compareByYThenX(s1, s2);

        // swap _siteIndex values if necessary to match new ordering:
        int tempIndex;
        if (returnValue == -1) {
            if (s1.siteIndex > s2.siteIndex) {
                tempIndex = s1.siteIndex;
                s1.siteIndex = s2.siteIndex;
                s2.siteIndex = tempIndex;
            }
        } else if (returnValue == 1) {
            if (s2.siteIndex > s1.siteIndex) {
                tempIndex = s2.siteIndex;
                s2.siteIndex = s1.siteIndex;
                s1.siteIndex = tempIndex;
            }

        }

        return returnValue;
    }
    final private static double EPSILON = .005;

    private static boolean closeEnough(Point p0, Point p1) {
        return Point.distance(p0, p1) < EPSILON;
    }
    private Point _coord;

    @Override
    public Point getCoord() {
        return _coord;
    }

    public Site(Point p, int index, double weight) {
        init(p, index, weight);
    }

    private Site init(Point p, int index, double weight) {
        _coord = p;
        siteIndex = index;
        this.weight = weight;
        return this;
    }

    @Override
    public String toString() {
        return "Site " + siteIndex + ": " + getCoord();
    }

    private void clear() {
        if (_edges != null) {
            _edges.clear();
            _edges = null;
        }
        if (_edgeOrientations != null) {
            _edgeOrientations.clear();
            _edgeOrientations = null;
        }
        if (_region != null) {
            _region.clear();
            _region = null;
        }
    }

    void addEdge(Edge edge) {
        _edges.add(edge);
    }

    public Edge nearestEdge() {
        // _edges.sort(Edge.compareSitesDistances);
        Collections.sort(_edges, new Comparator<Edge>() {
            @Override
            public int compare(Edge o1, Edge o2) {
                return (int) Edge.compareSitesDistances(o1, o2);
            }
        });
        return _edges.get(0);
    }

    List<Site> neighborSites() {
        if (_edges == null || _edges.isEmpty()) {
            return Collections.emptyList();
        }
        if (_edgeOrientations == null) {
            reorderEdges();
        }
        List<Site> list = new ArrayList<Site>();
        for (Edge edge : _edges) {
            list.add(neighborSite(edge));
        }
        return list;
    }

    private Site neighborSite(Edge edge) {
        if (this == edge.getLeftSite()) {
            return edge.getRightSite();
        }
        if (this == edge.getRightSite()) {
            return edge.getLeftSite();
        }
        return null;
    }

    List<Point> region(Rectangle clippingBounds) {
        if (_edges == null || _edges.isEmpty()) {
            return Collections.emptyList();
        }
        if (_edgeOrientations == null) {
            reorderEdges();
            _region = clipToBounds(clippingBounds);
            if ((new Polygon(_region)).winding() == Winding.CLOCKWISE) {
                Collections.reverse(_region);
            }
        }
        return _region;
    }

    private void reorderEdges() {
        //trace("_edges:", _edges);
        EdgeReorderer reorderer = new EdgeReorderer(_edges, Vertex.class);
        _edges = reorderer.get_edges();
        //trace("reordered:", _edges);
        _edgeOrientations = reorderer.get_edgeOrientations();
        reorderer.dispose();
    }

    private List<Point> clipToBounds(Rectangle bounds) {
        List<Point> points = new ArrayList<Point>();
        int n = _edges.size();
        int i = 0;
        Edge edge;
        while (i < n && (_edges.get(i).isVisible() == false)) {
            ++i;
        }

        if (i == n) {
            // no edges visible
            return Collections.emptyList();
        }
        edge = _edges.get(i);
        LR orientation = _edgeOrientations.get(i);
        points.add(edge.getClippedEnds().get(orientation));
        points.add(edge.getClippedEnds().get((orientation.other())));

        for (int j = i + 1; j < n; ++j) {
            edge = _edges.get(j);
            if (edge.isVisible() == false) {
                continue;
            }
            connect(points, j, bounds, false);
        }
        // close up the polygon by adding another corner point of the bounds if needed:
        connect(points, i, bounds, true);

        return points;
    }

    private void connect(List<Point> points, int j, Rectangle bounds, boolean closingUp) {
        Point rightPoint = points.get(points.size() - 1);
        Edge newEdge = _edges.get(j);
        LR newOrientation = _edgeOrientations.get(j);
        // the point that  must be connected to rightPoint:
        Point newPoint = newEdge.getClippedEnds().get(newOrientation);
        if (!closeEnough(rightPoint, newPoint)) {
            // The points do not coincide, so they must have been clipped at the bounds;
            // see if they are on the same border of the bounds:
            if (rightPoint.x != newPoint.x
                    && rightPoint.y != newPoint.y) {
                // They are on different borders of the bounds;
                // insert one or two corners of bounds as needed to hook them up:
                // (NOTE this will not be correct if the region should take up more than
                // half of the bounds rect, for then we will have gone the wrong way
                // around the bounds and included the smaller part rather than the larger)
                int rightCheck = BoundsCheck.check(rightPoint, bounds);
                int newCheck = BoundsCheck.check(newPoint, bounds);
                double px, py;
                if ((rightCheck & BoundsCheck.RIGHT) != 0) {
                    px = bounds.right;
                    if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        py = bounds.bottom;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        py = bounds.top;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        if (rightPoint.y - bounds.y + newPoint.y - bounds.y < bounds.height) {
                            py = bounds.top;
                        } else {
                            py = bounds.bottom;
                        }
                        points.add(new Point(px, py));
                        points.add(new Point(bounds.left, py));
                    }
                } else if ((rightCheck & BoundsCheck.LEFT) != 0) {
                    px = bounds.left;
                    if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        py = bounds.bottom;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        py = bounds.top;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        if (rightPoint.y - bounds.y + newPoint.y - bounds.y < bounds.height) {
                            py = bounds.top;
                        } else {
                            py = bounds.bottom;
                        }
                        points.add(new Point(px, py));
                        points.add(new Point(bounds.right, py));
                    }
                } else if ((rightCheck & BoundsCheck.TOP) != 0) {
                    py = bounds.top;
                    if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        px = bounds.right;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        px = bounds.left;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        if (rightPoint.x - bounds.x + newPoint.x - bounds.x < bounds.width) {
                            px = bounds.left;
                        } else {
                            px = bounds.right;
                        }
                        points.add(new Point(px, py));
                        points.add(new Point(px, bounds.bottom));
                    }
                } else if ((rightCheck & BoundsCheck.BOTTOM) != 0) {
                    py = bounds.bottom;
                    if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        px = bounds.right;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        px = bounds.left;
                        points.add(new Point(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        if (rightPoint.x - bounds.x + newPoint.x - bounds.x < bounds.width) {
                            px = bounds.left;
                        } else {
                            px = bounds.right;
                        }
                        points.add(new Point(px, py));
                        points.add(new Point(px, bounds.top));
                    }
                }
            }
            if (closingUp) {
                // newEdge's ends have already been added
                return;
            }
            points.add(newPoint);
        }
        Point newRightPoint = newEdge.getClippedEnds().get(newOrientation.other());
        if (!closeEnough(points.get(0), newRightPoint)) {
            points.add(newRightPoint);
        }
    }

    public double getX() {
        return _coord.x;
    }

    public double getY() {
        return _coord.y;
    }

}

final class BoundsCheck {

    final public static int TOP = 1;
    final public static int BOTTOM = 2;
    final public static int LEFT = 4;
    final public static int RIGHT = 8;

    /**
     *
     * @param point
     * @param bounds
     * @return an int with the appropriate bits set if the Point lies on the
     * corresponding bounds lines
     *
     */
    public static int check(Point point, Rectangle bounds) {
        int value = 0;
        if (point.x == bounds.left) {
            value |= LEFT;
        }
        if (point.x == bounds.right) {
            value |= RIGHT;
        }
        if (point.y == bounds.top) {
            value |= TOP;
        }
        if (point.y == bounds.bottom) {
            value |= BOTTOM;
        }
        return value;
    }

    private BoundsCheck() {
    }
}
