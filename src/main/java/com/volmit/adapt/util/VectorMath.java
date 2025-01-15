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

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector utilities
 *
 * @author cyberpwn
 */
public class VectorMath {

    public static Vector rotate90CX(Vector v) {
        return new Vector(v.getX(), -v.getZ(), v.getY());
    }

    public static Vector rotate90CCX(Vector v) {
        return new Vector(v.getX(), v.getZ(), -v.getY());
    }

    public static Vector rotate90CY(Vector v) {
        return new Vector(-v.getZ(), v.getY(), v.getX());
    }

    public static Vector rotate90CCY(Vector v) {
        return new Vector(v.getZ(), v.getY(), -v.getX());
    }

    public static Vector rotate90CZ(Vector v) {
        return new Vector(v.getY(), -v.getX(), v.getZ());
    }

    public static Vector rotate90CCZ(Vector v) {
        return new Vector(-v.getY(), v.getX(), v.getZ());
    }

    /**
     * Get all SIMPLE block faces from a more specific block face (SOUTH_EAST) =
     * (south, east)
     *
     * @param f the block face
     * @return multiple faces, or one if the face is already simple
     */
    public static List<BlockFace> split(BlockFace f) {
        List<BlockFace> faces = new ArrayList<>();

        switch (f) {
            case DOWN:
                faces.add(BlockFace.DOWN);
                break;
            case EAST:
                faces.add(BlockFace.EAST);
                break;
            case EAST_NORTH_EAST:
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.NORTH);
                break;
            case EAST_SOUTH_EAST:
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.EAST);
                faces.add(BlockFace.SOUTH);
                break;
            case NORTH:
                faces.add(BlockFace.NORTH);
                break;
            case NORTH_EAST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.EAST);
                break;
            case NORTH_NORTH_EAST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.EAST);
                break;
            case NORTH_NORTH_WEST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.WEST);
                break;
            case NORTH_WEST:
                faces.add(BlockFace.NORTH);
                faces.add(BlockFace.WEST);
                break;
            case SELF:
                faces.add(BlockFace.SELF);
                break;
            case SOUTH:
                faces.add(BlockFace.SOUTH);
                break;
            case SOUTH_EAST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.EAST);
                break;
            case SOUTH_SOUTH_EAST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.EAST);
                break;
            case SOUTH_SOUTH_WEST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.WEST);
                break;
            case SOUTH_WEST:
                faces.add(BlockFace.SOUTH);
                faces.add(BlockFace.WEST);
                break;
            case UP:
                faces.add(BlockFace.UP);
                break;
            case WEST:
                faces.add(BlockFace.WEST);
                break;
            case WEST_NORTH_WEST:
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.NORTH);
                break;
            case WEST_SOUTH_WEST:
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.WEST);
                faces.add(BlockFace.SOUTH);
                break;
            default:
                break;
        }

        return faces;
    }

    /**
     * Get a normalized vector going from a location to another
     *
     * @param from from here
     * @param to   to here
     * @return the normalized vector direction
     */
    public static Vector direction(Location from, Location to) {
        return to.clone().subtract(from.clone()).toVector().normalize();
    }

    public static Vector directionNoNormal(Location from, Location to) {
        return to.clone().subtract(from.clone()).toVector();
    }

    /**
     * Shift all vectors based on the given vector
     *
     * @param vector  the vector direction to shift the vectors
     * @param vectors the vectors to be shifted
     * @return the shifted vectors
     */
    public static List<Vector> shift(Vector vector, List<Vector> vectors) {
        return new ArrayList<>(new GListAdapter<Vector, Vector>() {
            @Override
            public Vector onAdapt(Vector from) {
                return from.add(vector);
            }
        }.adapt(vectors));
    }

    /**
     * (clone) Force normalize the vector into three points, 1, 0, or -1. If the
     * value is > 0.333 (1) if the value is less than -0.333 (-1) else 0
     *
     * @param direction the direction
     * @return the vector
     */
    public static Vector triNormalize(Vector direction) {
        Vector v = direction.clone();
        v.normalize();

        if (v.getX() > 0.333) {
            v.setX(1);
        } else if (v.getX() < -0.333) {
            v.setX(-1);
        } else {
            v.setX(0);
        }

        if (v.getY() > 0.333) {
            v.setY(1);
        } else if (v.getY() < -0.333) {
            v.setY(-1);
        } else {
            v.setY(0);
        }

        if (v.getZ() > 0.333) {
            v.setZ(1);
        } else if (v.getZ() < -0.333) {
            v.setZ(-1);
        } else {
            v.setZ(0);
        }

        return v;
    }
}
