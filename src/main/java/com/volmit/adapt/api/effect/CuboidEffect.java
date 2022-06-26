package com.volmit.adapt.api.effect;


import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.EffectType;

public class CuboidEffect extends Effect {

    /**
     * Particle of the cube
     */
    public Particle particle = Particle.FLAME;

    /**
     * Particles in each row
     */
    public int particles = 8;

    /**
     * Length of x component of cuboid
     */
    public double xLength = 0;

    /**
     * Length of y component of cuboid
     */
    public double yLength = 0;

    /**
     * Length of z component of cuboid
     */
    public double zLength = 0;

    /**
     * Amount of padding to add around the cube
     */
    public double padding = 0;

    /**
     * Use corners of blocks
     */
    public boolean blockSnap = false;

    /**
     * Calculated length
     */
    private double useXLength = 0;
    private double useYLength = 0;
    private double useZLength = 0;

    /**
     * State variables
     */
    protected Location minCorner;
    protected boolean initialized;

    public CuboidEffect(EffectManager effectManager) {
        super(effectManager);
        type = EffectType.REPEATING;
        period = 5;
        iterations = 200;
    }

    @Override
    public void reloadParameters() {
        initialized = false;
    }

    @Override
    public void onRun() {
        Location target = getTarget();
        Location location = getLocation();
        if (target == null || location == null) return;
        if (!initialized) {
            if (blockSnap) {
                target = target.getBlock().getLocation();
                minCorner = location.getBlock().getLocation();
            } else {
                minCorner = location.clone();
            }
            if (xLength == 0 && yLength == 0 && zLength == 0) {
                if (target == null || !target.getWorld().equals(location.getWorld())) {
                    cancel();
                    return;
                }
                if (target.getX() < minCorner.getX()) {
                    minCorner.setX(target.getX());
                }
                if (target.getY() < minCorner.getY()) {
                    minCorner.setY(target.getY());
                }
                if (target.getZ() < minCorner.getZ()) {
                    minCorner.setZ(target.getZ());
                }
                useXLength = Math.abs(location.getX() - target.getX());
                useYLength = Math.abs(location.getY() - target.getY());
                useZLength = Math.abs(location.getZ() - target.getZ());
            } else {
                useXLength = xLength;
                useYLength = yLength;
                useZLength = zLength;
            }
            double extra = padding * 2;
            if (blockSnap) extra++;
            useXLength += extra;
            useYLength += extra;
            useZLength += extra;
            if (padding != 0) {
                minCorner.add(-padding, -padding, -padding);
            }
            initialized = true;
        }
        drawOutline();
    }

    private void drawOutline() {
        Vector v = new Vector();
        for (int i = 0; i < particles; i++) {
            // X edges
            drawEdge(v, i, 0, 2, 2);
            drawEdge(v, i, 0, 1, 2);
            drawEdge(v, i, 0, 1, 1);
            drawEdge(v, i, 0, 2, 1);

            // Y edges
            drawEdge(v, i, 2, 0, 2);
            drawEdge(v, i, 1,0, 2);
            drawEdge(v, i, 1,0, 1);
            drawEdge(v, i, 2,0, 1);

            // Z edges
            drawEdge(v, i, 2, 2, 0);
            drawEdge(v, i, 1, 2, 0);
            drawEdge(v, i, 1, 1, 0);
            drawEdge(v, i, 2, 1, 0);
        }
    }

    private void drawEdge(Vector v, int i, int dx, int dy, int dz) {
        if (dx == 0) {
            v.setX(useXLength * i / particles);
        } else {
            v.setX(useXLength * (dx - 1));
        }
        if (dy == 0) {
            v.setY(useYLength * i / particles);
        } else {
            v.setY(useYLength * (dy - 1));
        }
        if (dz == 0) {
            v.setZ(useZLength * i / particles);
        } else {
            v.setZ(useZLength * (dz - 1));
        }
        display(particle, minCorner.add(v));
        minCorner.subtract(v);
    }
}
