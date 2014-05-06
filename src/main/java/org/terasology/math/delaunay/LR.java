package org.terasology.math.delaunay;

public enum LR {

    LEFT,
    RIGHT;

    public LR other() {
        return this == LEFT ? RIGHT : LEFT;
    }
}
