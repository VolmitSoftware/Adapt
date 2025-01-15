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

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;


/**
 * Used to Create an instance of a spherical area based on a central location
 * Great for efficiently checking if an entity is within a spherical area.
 *
 * @author cyberpwn
 */
@Setter
@Getter
public class Area {
    /**
     * -- GETTER --
     * Get the defined center location
     * <p>
     * <p>
     * -- SETTER --
     * Set the defined center location
     */
    private Location location;
    /**
     * -- GETTER --
     * Gets the area's radius
     * <p>
     * <p>
     * -- SETTER --
     * Set the area's radius
     */
    private Double radius;

    /**
     * Used to instantiate a new "area" in which you can check if entities are
     * within this area.
     *
     * @param location The center location of the area
     * @param radius   The radius used as a double.
     */
    public Area(Location location, Double radius) {
        this.location = location;
        this.radius = radius;
    }

    /**
     * Calculate the <STRONG>ESTIMATED distance</STRONG> from the center of this
     * area, to the given location <STRONG>WARNING: This uses newton's method,
     * be careful on how accurate you need this. As it is meant for FAST
     * calculations with minimal load.</STRONG>
     *
     * @param location The given location to calculate a distance from the center.
     * @return Returns the distance of location from the center.
     */
    public Double distance(Location location) {
        double c = this.location.distanceSquared(location);
        double t = c;

        for (int i = 0; i < 3; i++) {
            t = (c / t + t) / 2.0;
        }

        return t;
    }

    /**
     * Get ALL entities within the area. <STRONG>NOTE: This is EVERY entity, not
     * just LivingEntities. Drops, Particles, Mobs, Players, Everything</STRONG>
     *
     * @return Returns an Entity[] array of all entities within the given area.
     */
    public Entity[] getNearbyEntities() {
        try {
            int chunkRadius = (int) (radius < 16 ? 1 : (radius - (radius % 16)) / 16);
            HashSet<Entity> radiusEntities = new HashSet<>();

            for (int chX = -chunkRadius; chX <= chunkRadius; chX++) {
                for (int chZ = -chunkRadius; chZ <= chunkRadius; chZ++) {
                    int x = (int) location.getX(), y = (int) location.getY(), z = (int) location.getZ();

                    for (Entity e : new Location(location.getWorld(), x + (chX * 16), y, z + (chZ * 16)).getChunk().getEntities()) {
                        if (e.getLocation().distanceSquared(location) <= radius * radius && e.getLocation().getBlock() != location.getBlock()) {
                            radiusEntities.add(e);
                        }
                    }
                }
            }

            return radiusEntities.toArray(new Entity[0]);
        } catch (Exception e) {
            return new ArrayList<Entity>().toArray(new Entity[0]);
        }
    }

    /**
     * Pick a random location in this radius
     */
    public Location random() {
        Random r = new Random();
        double x = radius * ((r.nextDouble() - 0.5) * 2);
        double y = radius * ((r.nextDouble() - 0.5) * 2);
        double z = radius * ((r.nextDouble() - 0.5) * 2);

        return location.clone().add(x, y, z);
    }
}
