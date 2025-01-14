/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.util;

import java.io.Serial;

/**
 * A 3-element vector that is represented by single-precision floating point
 * x,y,z coordinates.  If this value represents a normal, then it should
 * be normalized.
 */
public class Vector3f extends Tuple3f implements java.io.Serializable {

    // Combatible with 1.1
    @Serial
    private static final long serialVersionUID = -7031930069184524614L;

    /**
     * Constructs and initializes a Vector3f from the specified xyz coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    public Vector3f(float x, float y, float z) {
        super(x, y, z);
    }


    /**
     * Constructs and initializes a Vector3f from the array of length 3.
     *
     * @param v the array of length 3 containing xyz in order
     */
    public Vector3f(float[] v) {
        super(v);
    }


    /**
     * Constructs and initializes a Vector3f from the specified Vector3f.
     *
     * @param v1 the Vector3f containing the initialization x y z data
     */
    public Vector3f(Vector3f v1) {
        super(v1);
    }


    /**
     * Constructs and initializes a Vector3f from the specified Vector3d.
     *
     * @param v1 the Vector3d containing the initialization x y z data
     */
    public Vector3f(Vector3d v1) {
        super(v1);
    }


    /**
     * Constructs and initializes a Vector3f from the specified Tuple3f.
     *
     * @param t1 the Tuple3f containing the initialization x y z data
     */
    public Vector3f(Tuple3f t1) {
        super(t1);
    }


    /**
     * Constructs and initializes a Vector3f from the specified Tuple3d.
     *
     * @param t1 the Tuple3d containing the initialization x y z data
     */
    public Vector3f(Tuple3d t1) {
        super(t1);
    }


    /**
     * Constructs and initializes a Vector3f to (0,0,0).
     */
    public Vector3f() {
        super();
    }


    /**
     * Returns the squared length of this vector.
     *
     * @return the squared length of this vector
     */
    public final float lengthSquared() {
        return (this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /**
     * Returns the length of this vector.
     *
     * @return the length of this vector
     */
    public final float length() {
        return (float)
                Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }


    /**
     * Sets this vector to be the vector cross product of vectors v1 and v2.
     *
     * @param v1 the first vector
     * @param v2 the second vector
     */
    public final void cross(Vector3f v1, Vector3f v2) {
        float x, y;

        x = v1.y * v2.z - v1.z * v2.y;
        y = v2.x * v1.z - v2.z * v1.x;
        this.z = v1.x * v2.y - v1.y * v2.x;
        this.x = x;
        this.y = y;
    }

    /**
     * Computes the dot product of this vector and vector v1.
     *
     * @param v1 the other vector
     * @return the dot product of this vector and v1
     */
    public final float dot(Vector3f v1) {
        return (this.x * v1.x + this.y * v1.y + this.z * v1.z);
    }

    /**
     * Sets the value of this vector to the normalization of vector v1.
     *
     * @param v1 the un-normalized vector
     */
    public final void normalize(Vector3f v1) {
        float norm;

        norm = (float) (1.0 / Math.sqrt(v1.x * v1.x + v1.y * v1.y + v1.z * v1.z));
        this.x = v1.x * norm;
        this.y = v1.y * norm;
        this.z = v1.z * norm;
    }

    /**
     * Normalizes this vector in place.
     */
    public final void normalize() {
        float norm;

        norm = (float)
                (1.0 / Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z));
        this.x *= norm;
        this.y *= norm;
        this.z *= norm;
    }


    /**
     * Returns the angle in radians between this vector and the vector
     * parameter; the return value is constrained to the range [0,PI].
     *
     * @param v1 the other vector
     * @return the angle in radians in the range [0,PI]
     */
    public final float angle(Vector3f v1) {
        double vDot = this.dot(v1) / (this.length() * v1.length());
        if (vDot < -1.0) vDot = -1.0;
        if (vDot > 1.0) vDot = 1.0;
        return ((float) (Math.acos(vDot)));
    }

}
