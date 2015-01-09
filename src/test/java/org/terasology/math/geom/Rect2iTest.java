/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.math.geom;


import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rect2iTest {

    @Test
    public void testSubtraction0() {
        Rect2i a = Rect2i.createFromMinAndSize(1, 2, 3, 3);
        Rect2i b = Rect2i.createFromMinAndSize(0, 4, 3, 3);

        List<Rect2i> sub = Rect2i.difference(a, b);

        assertEquals(2, sub.size());

        int area = 0;
        for (Rect2i r : sub) {
            area += r.area();
        }

        assertEquals(7, area);

        assertTrue(sub.contains(Rect2i.createFromMinAndSize(1, 2, 3, 2)));
        assertTrue(sub.contains(Rect2i.createFromMinAndSize(3, 4, 1, 1)));
    }

    @Test
    public void testSubtraction1() {
        Rect2i a = Rect2i.createFromMinAndSize(1, 2, 3, 3);
        Rect2i b = Rect2i.createFromMinAndSize(3, 2, 3, 3);

        List<Rect2i> sub = Rect2i.difference(a, b);

        assertEquals(1, sub.size());
        assertEquals(Rect2i.createFromMinAndSize(1, 2, 2, 3), sub.get(0));
    }

    @Test
    public void overlap() {
        assertTrue(Rect2i.createFromMinAndSize(5, 5, 472, 17).overlaps(Rect2i.createFromMinAndSize(5, 5, 1, 16)));
    }

    @Test
    public void testEncompass() {
        // encompass self
        assertTrue(Rect2i.createFromMinAndSize(5, 5, 47, 57).encompasses(Rect2i.createFromMinAndSize(5, 5, 47, 57)));

        assertTrue(Rect2i.createFromMinAndSize(5, 5, 47, 57).encompasses(Rect2i.createFromMinAndSize(45, 35, 5, 20)));
        assertTrue(Rect2i.createFromMinAndSize(5, 5, 47, 57).encompasses(Rect2i.createFromMinAndSize(50, 60, 2, 2)));

        assertFalse(Rect2i.createFromMinAndSize(5, 5, 47, 57).encompasses(Rect2i.createFromMinAndSize(50, 60, 3, 2)));
        assertFalse(Rect2i.createFromMinAndSize(5, 5, 47, 57).encompasses(Rect2i.createFromMinAndSize(50, 60, 2, 3)));
    }

    @Test
    public void testContainsEmpty() {
        Rect2i a = Rect2i.createFromMinAndMax(0, 0, 0, 0);
        assertTrue(a.contains(0, 0));

        assertFalse(a.contains(1, 0));
        assertFalse(a.contains(0, 1));
        assertFalse(a.contains(1, 1));
        assertFalse(a.contains(-1, 0));
        assertFalse(a.contains(0, -1));
        assertFalse(a.contains(-1, 1));
        assertFalse(a.contains(1, -1));
        assertFalse(a.contains(-1, -1));
    }

    @Test
    public void testContains() {
        Rect2i a = Rect2i.createFromMinAndMax(1, 2, 3, 4);
        assertTrue(a.contains(1, 2));
        assertTrue(a.contains(2, 3));
        assertTrue(a.contains(2, 4));
        assertFalse(a.contains(4, 3));
        assertFalse(a.contains(2, 5));
        assertFalse(a.contains(3, 5));
    }

    @Test
    public void testTouches() {
        Rect2i a = Rect2i.createFromMinAndMax(1, 2, 10, 20);
        assertTrue(a.touches(1, 2));
        assertTrue(a.touches(10, 20));
        assertTrue(a.touches(1, 5));
        assertTrue(a.touches(5, 2));
        assertTrue(a.touches(10, 5));
        assertTrue(a.touches(5, 20));
        assertFalse(a.touches(4, 3));
        assertFalse(a.touches(1, 1));
        assertFalse(a.contains(11, 20));
        assertFalse(a.contains(10, 21));
    }
}