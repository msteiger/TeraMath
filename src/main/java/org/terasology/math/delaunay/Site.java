package org.terasology.math.delaunay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.terasology.math.geom.Polygon;
import org.terasology.math.geom.Rect2d;
import org.terasology.math.geom.Vector2d;
import org.terasology.math.geom.Winding;

public final class Site implements ICoord {

    private static final double EPSILON = .005;
    
    private Vector2d coord;

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
    private List<Vector2d> _region;

    
    public static Site create(Vector2d p, int index, double weight) {
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

    private static boolean closeEnough(Vector2d p0, Vector2d p1) {
        return Vector2d.distance(p0, p1) < EPSILON;
    }

    @Override
    public Vector2d getCoord() {
        return coord;
    }

    public Site(Vector2d p, int index, double weight) {
        init(p, index, weight);
    }

    private Site init(Vector2d p, int index, double weight) {
        coord = p;
        siteIndex = index;
        this.weight = weight;
        return this;
    }

    @Override
    public String toString() {
        return "Site " + siteIndex + ": " + getCoord();
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

    List<Vector2d> region(Rect2d clippingBounds) {
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

    private List<Vector2d> clipToBounds(Rect2d bounds) {
        List<Vector2d> points = new ArrayList<Vector2d>();
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

    private void connect(List<Vector2d> points, int j, Rect2d bounds, boolean closingUp) {
        Vector2d rightPoint = points.get(points.size() - 1);
        Edge newEdge = _edges.get(j);
        LR newOrientation = _edgeOrientations.get(j);
        // the point that  must be connected to rightPoint:
        Vector2d newPoint = newEdge.getClippedEnds().get(newOrientation);
        if (!closeEnough(rightPoint, newPoint)) {
            // The points do not coincide, so they must have been clipped at the bounds;
            // see if they are on the same border of the bounds:
            if (rightPoint.getX() != newPoint.getX()
                    && rightPoint.getY() != newPoint.getY()) {
                // They are on different borders of the bounds;
                // insert one or two corners of bounds as needed to hook them up:
                // (NOTE this will not be correct if the region should take up more than
                // half of the bounds rect, for then we will have gone the wrong way
                // around the bounds and included the smaller part rather than the larger)
                int rightCheck = BoundsCheck.check(rightPoint, bounds);
                int newCheck = BoundsCheck.check(newPoint, bounds);
                double px, py;
                if ((rightCheck & BoundsCheck.RIGHT) != 0) {
                    px = bounds.maxX();
                    if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        py = bounds.maxY();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        py = bounds.minY();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        if (rightPoint.getY() - bounds.minY() + newPoint.getY() - bounds.minY() < bounds.height()) {
                            py = bounds.minY();
                        } else {
                            py = bounds.maxY();
                        }
                        points.add(new Vector2d(px, py));
                        points.add(new Vector2d(bounds.minX(), py));
                    }
                } else if ((rightCheck & BoundsCheck.LEFT) != 0) {
                    px = bounds.minX();
                    if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        py = bounds.maxY();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        py = bounds.minY();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        if (rightPoint.getY() - bounds.minY() + newPoint.getY() - bounds.minY() < bounds.height()) {
                            py = bounds.minY();
                        } else {
                            py = bounds.maxY();
                        }
                        points.add(new Vector2d(px, py));
                        points.add(new Vector2d(bounds.maxX(), py));
                    }
                } else if ((rightCheck & BoundsCheck.TOP) != 0) {
                    py = bounds.minY();
                    if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        px = bounds.maxX();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        px = bounds.minX();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.BOTTOM) != 0) {
                        if (rightPoint.getX() - bounds.minX() + newPoint.getX() - bounds.minX() < bounds.width()) {
                            px = bounds.minX();
                        } else {
                            px = bounds.maxX();
                        }
                        points.add(new Vector2d(px, py));
                        points.add(new Vector2d(px, bounds.maxY()));
                    }
                } else if ((rightCheck & BoundsCheck.BOTTOM) != 0) {
                    py = bounds.maxY();
                    if ((newCheck & BoundsCheck.RIGHT) != 0) {
                        px = bounds.maxX();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.LEFT) != 0) {
                        px = bounds.minX();
                        points.add(new Vector2d(px, py));
                    } else if ((newCheck & BoundsCheck.TOP) != 0) {
                        if (rightPoint.getX() - bounds.minX() + newPoint.getX() - bounds.minX() < bounds.width()) {
                            px = bounds.minX();
                        } else {
                            px = bounds.maxX();
                        }
                        points.add(new Vector2d(px, py));
                        points.add(new Vector2d(px, bounds.minY()));
                    }
                }
            }
            if (closingUp) {
                // newEdge's ends have already been added
                return;
            }
            points.add(newPoint);
        }
        Vector2d newRightPoint = newEdge.getClippedEnds().get(newOrientation.other());
        if (!closeEnough(points.get(0), newRightPoint)) {
            points.add(newRightPoint);
        }
    }

    public double getX() {
        return coord.x();
    }

    public double getY() {
        return coord.y();
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
    public static int check(Vector2d point, Rect2d bounds) {
        int value = 0;
        if (point.getX() == bounds.minX()) {
            value |= LEFT;
        }
        if (point.getX() == bounds.maxX()) {
            value |= RIGHT;
        }
        if (point.getY() == bounds.minY()) {
            value |= TOP;
        }
        if (point.getY() == bounds.maxY()) {
            value |= BOTTOM;
        }
        return value;
    }

    private BoundsCheck() {
    }
}
