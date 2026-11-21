package art.arcane.adapt.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Snaps a dragged rectangle to screen edges, the safe margin, the centre lines and other widgets' edges and centres.
 * Each axis snaps on its own, to the closest target within the threshold.
 */
public final class HudSnap {
    public static final double SAFE_MARGIN = 4.0;
    public static final double THRESHOLD = 4.0;

    /** A guide line: vertical lines sit at an x position, horizontal ones at a y position. */
    public record Guide(boolean vertical, double position) {
    }

    public record Result(HudRect rect, List<Guide> guides) {
    }

    private HudSnap() {
    }

    public static Result snap(HudRect proposal, double screenWidth, double screenHeight, List<HudRect> others) {
        List<Double> targetsX = targets(screenWidth, others, true);
        List<Double> targetsY = targets(screenHeight, others, false);
        Match matchX = match(proposal.x(), proposal.width(), targetsX);
        Match matchY = match(proposal.y(), proposal.height(), targetsY);
        List<Guide> guides = new ArrayList<>(2);
        double x = proposal.x();
        double y = proposal.y();
        if (matchX != null) {
            x += matchX.delta;
            guides.add(new Guide(true, matchX.target));
        }
        if (matchY != null) {
            y += matchY.delta;
            guides.add(new Guide(false, matchY.target));
        }
        HudRect snapped = HudGeometry.clamp(proposal.at(x, y), screenWidth, screenHeight);
        return new Result(snapped, List.copyOf(guides));
    }

    private static List<Double> targets(double screen, List<HudRect> others, boolean horizontal) {
        List<Double> targets = new ArrayList<>(5 + others.size() * 3);
        targets.add(0.0);
        targets.add(SAFE_MARGIN);
        targets.add(screen / 2.0);
        targets.add(screen - SAFE_MARGIN);
        targets.add(screen);
        for (HudRect other : others) {
            targets.add(horizontal ? other.x() : other.y());
            targets.add(horizontal ? other.centerX() : other.centerY());
            targets.add(horizontal ? other.right() : other.bottom());
        }
        return targets;
    }

    private static Match match(double start, double size, List<Double> targets) {
        Match best = null;
        double[] edges = {start, start + size / 2.0, start + size};
        for (double target : targets) {
            for (double edge : edges) {
                double delta = target - edge;
                if (Math.abs(delta) <= THRESHOLD && (best == null || Math.abs(delta) < Math.abs(best.delta))) {
                    best = new Match(delta, target);
                }
            }
        }
        return best;
    }

    private record Match(double delta, double target) {
    }
}
