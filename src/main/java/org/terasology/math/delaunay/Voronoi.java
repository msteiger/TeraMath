package org.terasology.math.delaunay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.terasology.math.geom.Circle;
import org.terasology.math.geom.LineSegment;
import org.terasology.math.geom.Vector2d;
import org.terasology.math.geom.Rect2d;

public final class Voronoi {

    private SiteList _sites;
    private HashMap<Vector2d, Site> _sitesIndexedByLocation;

    private final List<Edge> _edges = new ArrayList<Edge>();
    // TODO generalize this so it doesn't have to be a rectangle;
    // then we can make the fractal voronois-within-voronois
    private Rect2d _plotBounds;

    public Rect2d get_plotBounds() {
        return _plotBounds;
    }

    public Voronoi(List<Vector2d> points, Rect2d plotBounds) {
        init(points, plotBounds);
        fortunesAlgorithm();
    }

    public Voronoi(List<Vector2d> points) {
        double maxWidth = 0, maxHeight = 0;
        for (Vector2d p : points) {
            maxWidth = Math.max(maxWidth, p.getX());
            maxHeight = Math.max(maxHeight, p.getY());
        }
        System.out.println(maxWidth + "," + maxHeight);
        init(points, Rect2d.createFromMinAndSize(0, 0, maxWidth, maxHeight));
        fortunesAlgorithm();
    }

    public Voronoi(int numSites, double maxWidth, double maxHeight, Random r) {
        List<Vector2d> points = new ArrayList<Vector2d>();
        for (int i = 0; i < numSites; i++) {
            points.add(new Vector2d(r.nextDouble() * maxWidth, r.nextDouble() * maxHeight));
        }
        init(points, Rect2d.createFromMinAndSize(0, 0, maxWidth, maxHeight));
        fortunesAlgorithm();
    }

    private void init(List<Vector2d> points, Rect2d plotBounds) {
        _sites = new SiteList();
        _sitesIndexedByLocation = new HashMap<Vector2d, Site>();
        addSites(points);
        _plotBounds = plotBounds;
    }

    private void addSites(List<Vector2d> points) {
        int length = points.size();
        for (int i = 0; i < length; ++i) {
            addSite(points.get(i), i);
        }
    }

    private void addSite(Vector2d p, int index) {
        double weight = Math.random() * 100;
        Site site = Site.create(p, index, weight);
        _sites.push(site);
        _sitesIndexedByLocation.put(p, site);
    }

    public List<Edge> edges() {
        return _edges;
    }

    public List<Vector2d> region(Vector2d p) {
        Site site = _sitesIndexedByLocation.get(p);
        if (site == null) {
            return Collections.emptyList();
        }
        return site.region(_plotBounds);
    }

    // TODO: bug: if you call this before you call region(), something goes wrong :(
    public List<Vector2d> neighborSitesForSite(Vector2d coord) {
        List<Vector2d> points = new ArrayList<Vector2d>();
        Site site = _sitesIndexedByLocation.get(coord);
        if (site == null) {
            return points;
        }
        List<Site> sites = site.neighborSites();
        for (Site neighbor : sites) {
            points.add(neighbor.getCoord());
        }
        return points;
    }

    public List<Circle> circles() {
        return _sites.circles();
    }

    private List<Edge> selectEdgesForSitePoint(Vector2d coord, List<Edge> edgesToTest) {
        List<Edge> filtered = new ArrayList<Edge>();

        for (Edge e : edgesToTest) {
            if (((e.getLeftSite() != null && e.getLeftSite().getCoord() == coord)
                    || (e.getRightSite() != null && e.getRightSite().getCoord() == coord))) {
                filtered.add(e);
            }
        }
        return filtered;
    }

    private List<LineSegment> visibleLineSegments(List<Edge> edges) {
        List<LineSegment> segments = new ArrayList<LineSegment>();

        for (Edge edge : edges) {
            if (edge.isVisible()) {
                Vector2d p1 = edge.getClippedEnds().get(LR.LEFT);
                Vector2d p2 = edge.getClippedEnds().get(LR.RIGHT);
                segments.add(new LineSegment(p1, p2));
            }
        }

        return segments;
    }

    private List<LineSegment> delaunayLinesForEdges(List<Edge> edges) {
        List<LineSegment> segments = new ArrayList<LineSegment>();

        for (Edge edge : edges) {
            segments.add(edge.delaunayLine());
        }

        return segments;
    }

    public List<LineSegment> voronoiBoundaryForSite(Vector2d coord) {
        return visibleLineSegments(selectEdgesForSitePoint(coord, _edges));
    }

    public List<LineSegment> delaunayLinesForSite(Vector2d coord) {
        return delaunayLinesForEdges(selectEdgesForSitePoint(coord, _edges));
    }

    public List<LineSegment> voronoiDiagram() {
        return visibleLineSegments(_edges);
    }

    public List<LineSegment> hull() {
        return delaunayLinesForEdges(hullEdges());
    }

    private List<Edge> hullEdges() {
        List<Edge> filtered = new ArrayList<Edge>();

        for (Edge e : _edges) {
            if (e.isPartOfConvexHull()) {
                filtered.add(e);
            }
        }



        return filtered;

        /*function myTest(edge:Edge, index:int, vector:Vector.<Edge>):Boolean
         {
         return (edge.isPartOfConvexHull());
         }*/
    }

    public List<Vector2d> hullPointsInOrder() {
        List<Edge> hullEdges = hullEdges();

        List<Vector2d> points = new ArrayList<Vector2d>();
        if (hullEdges.isEmpty()) {
            return points;
        }

        EdgeReorderer reorderer = new EdgeReorderer(hullEdges, Site.class);
        hullEdges = reorderer.get_edges();
        List<LR> orientations = reorderer.get_edgeOrientations();
        reorderer.dispose();

        LR orientation;

        int n = hullEdges.size();
        for (int i = 0; i < n; ++i) {
            Edge edge = hullEdges.get(i);
            orientation = orientations.get(i);
            points.add(edge.getSite(orientation).getCoord());
        }
        return points;
    }

    public List<List<Vector2d>> regions() {
        return _sites.regions(_plotBounds);
    }

    public List<Vector2d> siteCoords() {
        return _sites.siteCoords();
    }

    private void fortunesAlgorithm() {
        Site newSite, bottomSite, topSite, tempSite;
        Vertex v, vertex;
        Vector2d newintstar = null;
        LR leftRight;
        Halfedge lbnd, rbnd, llbnd, rrbnd, bisector;
        Edge edge;

        Rect2d dataBounds = _sites.getSitesBounds();

        int sqrt_nsites = (int) Math.sqrt(_sites.getLength() + 4);
        HalfedgePriorityQueue heap = new HalfedgePriorityQueue(dataBounds.minY(), dataBounds.height(), sqrt_nsites);
        EdgeList edgeList = new EdgeList(dataBounds.minX(), dataBounds.width(), sqrt_nsites);
        List<Halfedge> halfEdges = new ArrayList<Halfedge>();
        List<Vertex> vertices = new ArrayList<Vertex>();

        Site bottomMostSite = _sites.next();
        newSite = _sites.next();

        for (;;) {
            if (heap.empty() == false) {
                newintstar = heap.min();
            }

            if (newSite != null
                    && (heap.empty() || compareByYThenX(newSite, newintstar) < 0)) {
                /* new site is smallest */
                //trace("smallest: new site " + newSite);

                // Step 8:
                lbnd = edgeList.edgeListLeftNeighbor(newSite.getCoord());    // the Halfedge just to the left of newSite
                //trace("lbnd: " + lbnd);
                rbnd = lbnd.edgeListRightNeighbor;    // the Halfedge just to the right
                //trace("rbnd: " + rbnd);
                bottomSite = rightRegion(lbnd, bottomMostSite);   // this is the same as leftRegion(rbnd)
                // this Site determines the region containing the new site
              //trace("new Site is in region of existing site: " + bottomSite);

                // Step 9:
                edge = Edge.createBisectingEdge(bottomSite, newSite);
                //trace("new edge: " + edge);
                _edges.add(edge);

                bisector = Halfedge.create(edge, LR.LEFT);
                halfEdges.add(bisector);
                // inserting two Halfedges into edgeList constitutes Step 10:
                // insert bisector to the right of lbnd:
                edgeList.insert(lbnd, bisector);

                // first half of Step 11:
                if ((vertex = Vertex.intersect(lbnd, bisector)) != null) {
                    vertices.add(vertex);
                    heap.remove(lbnd);
                    lbnd.vertex = vertex;
                    lbnd.ystar = vertex.getY() + Vector2d.distance(newSite.getCoord(), vertex.getCoord());
                    heap.insert(lbnd);
                }

                lbnd = bisector;
                bisector = Halfedge.create(edge, LR.RIGHT);
                halfEdges.add(bisector);
                // second Halfedge for Step 10:
                // insert bisector to the right of lbnd:
                edgeList.insert(lbnd, bisector);

                // second half of Step 11:
                if ((vertex = Vertex.intersect(bisector, rbnd)) != null) {
                    vertices.add(vertex);
                    bisector.vertex = vertex;
                    bisector.ystar = vertex.getY() + Vector2d.distance(newSite.getCoord(), vertex.getCoord());
                    heap.insert(bisector);
                }

                newSite = _sites.next();
            } else if (heap.empty() == false) {
                /* intersection is smallest */
                lbnd = heap.extractMin();
                llbnd = lbnd.edgeListLeftNeighbor;
                rbnd = lbnd.edgeListRightNeighbor;
                rrbnd = rbnd.edgeListRightNeighbor;
                bottomSite = leftRegion(lbnd, bottomMostSite);
                topSite = rightRegion(rbnd, bottomMostSite);
                // these three sites define a Delaunay triangle
                // (not actually using these for anything...)
                //_triangles.push(new Triangle(bottomSite, topSite, rightRegion(lbnd)));

                v = lbnd.vertex;
                lbnd.edge.setVertex(lbnd.leftRight, v);
                rbnd.edge.setVertex(rbnd.leftRight, v);
                edgeList.remove(lbnd);
                heap.remove(rbnd);
                edgeList.remove(rbnd);
                leftRight = LR.LEFT;
                if (bottomSite.getY() > topSite.getY()) {
                    tempSite = bottomSite;
                    bottomSite = topSite;
                    topSite = tempSite;
                    leftRight = LR.RIGHT;
                }
                edge = Edge.createBisectingEdge(bottomSite, topSite);
                _edges.add(edge);
                bisector = Halfedge.create(edge, leftRight);
                halfEdges.add(bisector);
                edgeList.insert(llbnd, bisector);
                edge.setVertex(leftRight.other(), v);
                if ((vertex = Vertex.intersect(llbnd, bisector)) != null) {
                    vertices.add(vertex);
                    heap.remove(llbnd);
                    llbnd.vertex = vertex;
                    llbnd.ystar = vertex.getY() + Vector2d.distance(bottomSite.getCoord(), vertex.getCoord());
                    heap.insert(llbnd);
                }
                if ((vertex = Vertex.intersect(bisector, rrbnd)) != null) {
                    vertices.add(vertex);
                    bisector.vertex = vertex;
                    bisector.ystar = vertex.getY() + Vector2d.distance(bottomSite.getCoord(), vertex.getCoord());
                    heap.insert(bisector);
                }
            } else {
                break;
            }
        }

        // heap should be empty now
        heap.dispose();

        for (Halfedge halfEdge : halfEdges) {
            halfEdge.reallyDispose();
        }
        halfEdges.clear();

        // we need the vertices to clip the edges
        for (Edge e : _edges) {
            e.clipVertices(_plotBounds);
        }
        // but we don't actually ever use them again!
        vertices.clear();
    }

    Site leftRegion(Halfedge he, Site bottomMostSite) {
        Edge edge = he.edge;
        if (edge == null) {
            return bottomMostSite;
        }
        return edge.getSite(he.leftRight);
    }

    Site rightRegion(Halfedge he, Site bottomMostSite) {
        Edge edge = he.edge;
        if (edge == null) {
            return bottomMostSite;
        }
        return edge.getSite(he.leftRight.other());
    }

    public static int compareByYThenX(Site s1, Site s2) {
        if (s1.getY() < s2.getY()) {
            return -1;
        }
        if (s1.getY() > s2.getY()) {
            return 1;
        }
        if (s1.getX() < s2.getX()) {
            return -1;
        }
        if (s1.getX() > s2.getX()) {
            return 1;
        }
        return 0;
    }

    public static int compareByYThenX(Site s1, Vector2d s2) {
        if (s1.getY() < s2.getY()) {
            return -1;
        }
        if (s1.getY() > s2.getY()) {
            return 1;
        }
        if (s1.getX() < s2.getX()) {
            return -1;
        }
        if (s1.getX() > s2.getX()) {
            return 1;
        }
        return 0;
    }
}
