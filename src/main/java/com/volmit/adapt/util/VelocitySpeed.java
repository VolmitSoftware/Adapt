package com.volmit.adapt.util;

import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class VelocitySpeed {
    public static final double EPSILON = 1.0E-6;

    private VelocitySpeed() {
    }

    public static InputSnapshot readInput(Player p, double fallbackThresholdSquared) {
        try {
            Input input = p.getCurrentInput();
            if (input != null) {
                return new InputSnapshot(input.isForward(), input.isBackward(), input.isLeft(), input.isRight());
            }
        } catch (NoSuchMethodError ignored) {
            // Fallback path for runtimes without Player#getCurrentInput.
        }

        Vector horizontal = horizontalOnly(p.getVelocity());
        if (horizontal.lengthSquared() <= Math.max(0, fallbackThresholdSquared)) {
            return InputSnapshot.NONE;
        }

        Vector movementDirection = horizontal.normalize();
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() <= EPSILON) {
            return InputSnapshot.NONE;
        }

        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        double forwardDot = movementDirection.dot(look);
        double sideDot = movementDirection.dot(right);
        return new InputSnapshot(forwardDot > 0.2, forwardDot < -0.2, sideDot < -0.2, sideDot > 0.2);
    }

    public static Vector resolveHorizontalDirection(Player p, InputSnapshot input) {
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() <= EPSILON) {
            return new Vector();
        }

        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        Vector direction = new Vector();
        if (input.forward()) {
            direction.add(look);
        }
        if (input.backward()) {
            direction.subtract(look);
        }
        if (input.right()) {
            direction.add(right);
        }
        if (input.left()) {
            direction.subtract(right);
        }

        if (direction.lengthSquared() <= EPSILON) {
            return direction;
        }

        return direction.normalize();
    }

    public static Vector horizontalOnly(Vector velocity) {
        return new Vector(velocity.getX(), 0, velocity.getZ());
    }

    public static Vector moveTowards(Vector current, Vector target, double maxDelta) {
        if (maxDelta <= 0) {
            return current.clone();
        }

        Vector delta = target.clone().subtract(current);
        double distance = delta.length();
        if (distance <= EPSILON || distance <= maxDelta) {
            return target.clone();
        }

        return current.clone().add(delta.multiply(maxDelta / distance));
    }

    public static Vector clampHorizontal(Vector horizontal, double maxSpeed) {
        double clamped = Math.max(0, maxSpeed);
        if (clamped <= 0) {
            return new Vector();
        }

        if (horizontal.lengthSquared() <= clamped * clamped) {
            return horizontal;
        }

        return horizontal.normalize().multiply(clamped);
    }

    public static void setHorizontalVelocity(Player p, Vector horizontal) {
        Vector velocity = p.getVelocity();
        p.setVelocity(new Vector(horizontal.getX(), velocity.getY(), horizontal.getZ()));
    }

    public static void hardStopHorizontal(Player p) {
        Vector velocity = p.getVelocity();
        p.setVelocity(new Vector(0, velocity.getY(), 0));
    }

    public static double speedAmplifierScalar(int amplifier) {
        return 1.0 + (Math.max(0, amplifier) + 1) * 0.2;
    }

    public record InputSnapshot(boolean forward, boolean backward, boolean left, boolean right) {
        public static final InputSnapshot NONE = new InputSnapshot(false, false, false, false);

        public boolean hasHorizontal() {
            return forward || backward || left || right;
        }

        public boolean isForwardOnly() {
            return forward && !backward && !left && !right;
        }
    }
}
