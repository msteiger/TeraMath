package org.terasology.math.delaunay;

import org.terasology.math.geom.Vector2d;

final class Vertex implements ICoord {

    final public static Vertex VERTEX_AT_INFINITY = new Vertex(Double.NaN, Double.NaN);
    
    private Vector2d coord;

	private static Vertex create(double x, double y)
	{
		if (Double.isNaN(x) || Double.isNaN(y))
		{
			return VERTEX_AT_INFINITY;
		}
		else
		{
			return new Vertex(x, y);
		}
	}

    @Override
    public Vector2d getCoord() {
        return coord;
    }

    private Vertex(double x, double y) {
        coord = new Vector2d(x, y);
    }

    @Override
    public String toString() {
        return "Vertex (" + coord + ")";
    }

    /**
     * This is the only way to make a Vertex
     * @param halfedge0
     * @param halfedge1
     * @return
     */
    public static Vertex intersect(Halfedge halfedge0, Halfedge halfedge1) {
        Edge edge0, edge1, edge;
        Halfedge halfedge;
        double determinant, intersectionX, intersectionY;
        boolean rightOfSite;

        edge0 = halfedge0.edge;
        edge1 = halfedge1.edge;
        if (edge0 == null || edge1 == null) {
            return null;
        }
        if (edge0.getRightSite() == edge1.getRightSite()) {
            return null;
        }

        determinant = edge0.a * edge1.b - edge0.b * edge1.a;
        if (-1.0e-10 < determinant && determinant < 1.0e-10) {
            // the edges are parallel
            return null;
        }

        intersectionX = (edge0.c * edge1.b - edge1.c * edge0.b) / determinant;
        intersectionY = (edge1.c * edge0.a - edge0.c * edge1.a) / determinant;

        if (Voronoi.compareByYThenX(edge0.getRightSite(), edge1.getRightSite()) < 0) {
            halfedge = halfedge0;
            edge = edge0;
        } else {
            halfedge = halfedge1;
            edge = edge1;
        }
        rightOfSite = intersectionX >= edge.getRightSite().getX();
        if ((rightOfSite && halfedge.leftRight == LR.LEFT)
                || (!rightOfSite && halfedge.leftRight == LR.RIGHT)) {
            return null;
        }

        return Vertex.create(intersectionX, intersectionY);
    }

    public double getX() {
        return coord.x();
    }

    public double getY() {
        return coord.y();
    }
}
