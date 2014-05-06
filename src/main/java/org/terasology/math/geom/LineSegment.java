package org.terasology.math.geom;


public final class LineSegment {

    private final Vector2d p0;
	private final Vector2d p1;

    public LineSegment(Vector2d p0, Vector2d p1) {
        this.p0 = p0;
        this.p1 = p1;
    }

	/**
	 * @return the p0
	 */
	public Vector2d getP0()
	{
		return p0;
	}

	/**
	 * @return the p1
	 */
	public Vector2d getP1()
	{
		return p1;
	}
}
