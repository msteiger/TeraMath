package org.terasology.math.geom;


public final class LineSegment {

    private final Point p0;
	private final Point p1;

    public LineSegment(Point p0, Point p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

	/**
	 * @return the p0
	 */
	public Point getP0()
	{
		return p0;
	}

	/**
	 * @return the p1
	 */
	public Point getP1()
	{
		return p1;
	}
}
