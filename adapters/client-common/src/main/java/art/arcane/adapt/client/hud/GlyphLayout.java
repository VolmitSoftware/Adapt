package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where one glyph widget puts its glyphs. Rebuilt only when the statuses, options, screen or protected zone change, so
 * steady frames do no work. The room comes from the arrangement's region: it never reaches the protected zone or a
 * vanilla HUD area and is capped by the footprint share of the screen. Within it glyphs fill lines up to the line caps,
 * shrink two units at a time down to the minimum size, then (optionally) fold each crowded skill into one glyph with a
 * count, and finally keep the most urgent glyphs (charges and stances first, then the soonest to end) and fold the rest
 * into a "+n" cell. The kept glyphs are drawn in the widget's sort order, which is fixed per glyph.
 */
final class GlyphLayout {
    static final int GAP = 3;
    static final int RING_GAP = 3;
    static final double ARC_LIMIT = Math.toRadians(95.0);
    static final double ARC_STEP = Math.toRadians(1.0);
    /** Semi-axis of an ellipse through the corners of a rectangle, per half side: the square root of two. */
    static final double ELLIPSE = Math.sqrt(2.0);
    static final double REGION_MARGIN = 4.0;
    private static final int CAPACITY = HudOptions.Abilities.MAX_SHOWN + 1;
    private static final Comparator<HudStatusEntry> URGENCY = (HudStatusEntry left, HudStatusEntry right) -> {
        int byKind = Integer.compare(rank(left), rank(right));
        if (byKind != 0) {
            return byKind;
        }
        int byEnd = Long.compare(left.endsAt, right.endsAt);
        return byEnd != 0 ? byEnd : left.purpose.compareTo(right.purpose);
    };
    private static final Comparator<HudStatusEntry> BY_SKILL = (HudStatusEntry left, HudStatusEntry right) -> {
        int bySkill = left.skillId.compareTo(right.skillId);
        return bySkill != 0 ? bySkill : left.purpose.compareTo(right.purpose);
    };
    private static final Comparator<HudStatusEntry> BY_READY = (HudStatusEntry left, HudStatusEntry right) -> {
        int byEnd = Long.compare(left.endsAt, right.endsAt);
        return byEnd != 0 ? byEnd : left.purpose.compareTo(right.purpose);
    };
    private static final Comparator<HudStatusEntry> BY_RECENT = (HudStatusEntry left, HudStatusEntry right) -> {
        int byShown = Long.compare(left.shownAt, right.shownAt);
        return byShown != 0 ? byShown : left.purpose.compareTo(right.purpose);
    };

    final List<HudStatusEntry> shown = new ArrayList<>(CAPACITY);
    final int[] groupCount = new int[CAPACITY];
    final double[] cellX = new double[CAPACITY];
    final double[] cellY = new double[CAPACITY];
    int hidden;
    int iconSize;
    int glyph;
    double cellWidth;
    double cellHeight;
    double width;
    double height;
    /** For the arc: the box's top left relative to the screen centre, in widget units. */
    double originX;
    double originY;
    boolean names;
    boolean arc;

    private final List<HudStatusEntry> candidates = new ArrayList<>(64);
    private final List<HudStatusEntry> folded = new ArrayList<>(64);
    private final int[] foldedCount = new int[512];
    private final double[] slotX = new double[CAPACITY];
    private final double[] slotY = new double[CAPACITY];
    private int slots;
    private double arcBase;
    private int builtRevision = Integer.MIN_VALUE;
    private HudModel builtModel;
    private HudOptions.Abilities builtOptions;
    private HudAnchor builtAnchor;
    private double builtWidth;
    private double builtHeight;
    private double builtZoneWidth;
    private double builtZoneHeight;
    private double builtScale;
    private boolean builtNames;

    /** The number of cells drawn, the "+n" cell included. */
    int cells() {
        return shown.size() + (hidden > 0 ? 1 : 0);
    }

    void update(UiDraw draw, HudWidget.Frame frame, HudWidgetConfig config, HudOptions.Abilities options, boolean showNames) {
        HudModel model = frame.model();
        HudBox zone = frame.zone();
        HudAnchor anchor = config.placement().anchor();
        if (model == builtModel && model.statusRevision() == builtRevision && options == builtOptions && anchor == builtAnchor
                && frame.screenWidth() == builtWidth && frame.screenHeight() == builtHeight && zone.width == builtZoneWidth
                && zone.height == builtZoneHeight && frame.unitScale() == builtScale && showNames == builtNames) {
            return;
        }
        builtModel = model;
        builtRevision = model.statusRevision();
        builtOptions = options;
        builtAnchor = anchor;
        builtWidth = frame.screenWidth();
        builtHeight = frame.screenHeight();
        builtZoneWidth = zone.width;
        builtZoneHeight = zone.height;
        builtScale = frame.unitScale();
        builtNames = showNames;
        build(draw, frame, model, options, anchor, showNames);
    }

    private void build(UiDraw draw, HudWidget.Frame frame, HudModel model, HudOptions.Abilities options, HudAnchor anchor,
                       boolean showNames) {
        candidates.clear();
        List<HudStatusEntry> statuses = model.statuses();
        int nameWidth = 0;
        for (int index = 0; index < statuses.size(); index++) {
            HudStatusEntry status = statuses.get(index);
            if (options.shows(status.glyph, status.skillId)) {
                candidates.add(status);
                if (showNames) {
                    nameWidth = Math.max(nameWidth, draw.textWidth(status.label));
                }
            }
        }
        names = showNames;
        arc = options.arrangement() == GlyphArrangement.ARC;
        shown.clear();
        hidden = 0;
        width = 0.0;
        height = 0.0;
        if (candidates.isEmpty()) {
            return;
        }
        if (arc) {
            buildArc(frame, options);
        } else {
            buildLines(draw, frame, options, anchor, nameWidth);
        }
    }

    private void buildLines(UiDraw draw, HudWidget.Frame frame, HudOptions.Abilities options, HudAnchor anchor, int nameWidth) {
        double scale = frame.unitScale();
        double roomWidth = roomWidth(frame, options, anchor) / scale;
        double roomHeight = roomHeight(frame, options, anchor) / scale;
        boolean rows = options.arrangement().rows();
        int count = candidates.size();
        int perLine = 0;
        int lines = 0;
        for (int icon = options.iconSize(); ; icon -= 2) {
            int size = Math.max(icon, options.minIconSize());
            sizeCells(draw, size, rows, nameWidth);
            double major = rows ? roomWidth : roomHeight;
            double minor = rows ? roomHeight : roomWidth;
            double cellMajor = rows ? cellWidth : cellHeight;
            double cellMinor = rows ? cellHeight : cellWidth;
            perLine = Math.max(0, Math.min(options.maxColumns(), (int) Math.floor((major + GAP) / (cellMajor + GAP))));
            lines = Math.max(0, Math.min(options.maxRows(), (int) Math.floor((minor + GAP) / (cellMinor + GAP))));
            if (count <= capacity(options, perLine, lines) || size <= options.minIconSize()) {
                break;
            }
        }
        int capacity = capacity(options, perLine, lines);
        if (capacity <= 0) {
            return;
        }
        choose(options, capacity);
        int cells = cells();
        if (options.arrangement() == GlyphArrangement.GRID) {
            int square = (int) Math.ceil(Math.sqrt(cells));
            perLine = Math.min(perLine, Math.max(square, (cells + lines - 1) / lines));
        }
        int usedLines = (cells + perLine - 1) / perLine;
        int widest = Math.min(cells, perLine);
        double lineMajor = rows ? cellWidth : cellHeight;
        double lineMinor = rows ? cellHeight : cellWidth;
        double spanMajor = widest * (lineMajor + GAP) - GAP;
        double spanMinor = usedLines * (lineMinor + GAP) - GAP;
        width = rows ? spanMajor : spanMinor;
        height = rows ? spanMinor : spanMajor;
        boolean reversedLines = linesReversed(options, anchor) != (options.growth() == GlyphGrowth.REVERSED);
        boolean alongReversed = rows ? anchor.column() == 2 : anchor.row() == 2;
        boolean centred = rows ? anchor.column() == 1 : anchor.row() == 1;
        for (int index = 0; index < cells; index++) {
            int line = index / perLine;
            int position = index % perLine;
            int inLine = Math.min(perLine, cells - line * perLine);
            double lineSpan = inLine * (lineMajor + GAP) - GAP;
            double start = centred ? (spanMajor - lineSpan) / 2.0 : alongReversed ? spanMajor - lineSpan : 0.0;
            double along = alongReversed && !centred ? start + (inLine - 1 - position) * (lineMajor + GAP)
                    : start + position * (lineMajor + GAP);
            int placedLine = reversedLines ? usedLines - 1 - line : line;
            double across = placedLine * (lineMinor + GAP);
            cellX[index] = rows ? along : across;
            cellY[index] = rows ? across : along;
        }
    }

    /**
     * Elliptic rings of slots around the protected zone, starting straight down and alternating right and left up to a
     * little above level; slots that would touch a vanilla area or leave the screen are skipped.
     */
    private void buildArc(HudWidget.Frame frame, HudOptions.Abilities options) {
        double scale = frame.unitScale();
        double screenWidth = frame.screenWidth();
        double screenHeight = frame.screenHeight();
        double halfZoneWidth = Math.max(frame.zone().width / 2.0, 8.0) / scale;
        double halfZoneHeight = Math.max(frame.zone().height / 2.0, 8.0) / scale;
        double capWidth = capWidth(frame, options) / scale;
        double capHeight = capHeight(frame, options) / scale;
        int count = candidates.size();
        for (int icon = options.iconSize(); ; icon -= 2) {
            int size = Math.max(icon, options.minIconSize());
            iconSize = AbilityWidget.iconSize(size);
            glyph = AbilityWidget.glyphSize(iconSize);
            cellWidth = glyph;
            cellHeight = glyph;
            arcSlots(options, scale, screenWidth, screenHeight, halfZoneWidth, halfZoneHeight, capWidth, capHeight);
            if (count <= Math.min(slots, options.maxShown()) || size <= options.minIconSize()) {
                break;
            }
        }
        int capacity = Math.min(slots, options.maxShown());
        if (capacity <= 0) {
            return;
        }
        choose(options, capacity);
        int cells = cells();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int index = 0; index < cells; index++) {
            minX = Math.min(minX, slotX[index]);
            minY = Math.min(minY, slotY[index]);
            maxX = Math.max(maxX, slotX[index] + glyph);
            maxY = Math.max(maxY, slotY[index] + glyph);
        }
        originX = minX;
        originY = minY;
        width = maxX - minX;
        height = maxY - minY;
        for (int index = 0; index < cells; index++) {
            cellX[index] = slotX[index] - minX;
            cellY[index] = slotY[index] - minY;
        }
    }

    private void arcSlots(HudOptions.Abilities options, double scale, double screenWidth, double screenHeight,
                          double halfZoneWidth, double halfZoneHeight, double capWidth, double capHeight) {
        slots = 0;
        double half = glyph / 2.0;
        arcBase = (halfZoneHeight + half + 2.0) * ELLIPSE;
        int limit = Math.min(CAPACITY, options.maxShown() + 1);
        for (int ring = 0; ring < options.maxRows() && slots < limit; ring++) {
            double step = ring * (glyph + RING_GAP);
            double reachX = (halfZoneWidth + half + 2.0) * ELLIPSE + step;
            double reachY = (halfZoneHeight + half + 2.0) * ELLIPSE + step;
            int bottom = arcSlot(0.0, reachX, reachY, half, scale, screenWidth, screenHeight, capWidth, capHeight) ? slots - 1 : -1;
            double right = 0.0;
            double left = 0.0;
            boolean rightOpen = true;
            boolean leftOpen = true;
            int rightLast = bottom;
            int leftLast = bottom;
            while ((rightOpen || leftOpen) && slots < limit) {
                if (rightOpen) {
                    right = nextAngle(right, 1.0, rightLast, reachX, reachY, half);
                    if (right > ARC_LIMIT) {
                        rightOpen = false;
                    } else if (arcSlot(right, reachX, reachY, half, scale, screenWidth, screenHeight, capWidth, capHeight)) {
                        rightLast = slots - 1;
                    }
                }
                if (leftOpen && slots < limit) {
                    left = nextAngle(left, -1.0, leftLast, reachX, reachY, half);
                    if (-left > ARC_LIMIT) {
                        leftOpen = false;
                    } else if (arcSlot(left, reachX, reachY, half, scale, screenWidth, screenHeight, capWidth, capHeight)) {
                        leftLast = slots - 1;
                    }
                }
            }
        }
    }

    /** The next angle on one side whose cell clears the last cell placed on that side by the glyph gap. */
    private double nextAngle(double angle, double direction, int last, double reachX, double reachY, double half) {
        double next = angle;
        while (Math.abs(next) <= ARC_LIMIT) {
            next += direction * ARC_STEP;
            if (last < 0) {
                return next;
            }
            double x = centreX(next, reachX, reachY);
            double y = centreY(next, reachX, reachY);
            double dx = Math.abs(x - (slotX[last] + half));
            double dy = Math.abs(y - (slotY[last] + half));
            if (dx >= glyph + GAP - 0.01 || dy >= glyph + GAP - 0.01) {
                return next;
            }
        }
        return next;
    }

    private boolean arcSlot(double angle, double reachX, double reachY, double half, double scale, double screenWidth,
                            double screenHeight, double capWidth, double capHeight) {
        double x = centreX(angle, reachX, reachY) - half;
        double y = centreY(angle, reachX, reachY) - half;
        if (Math.abs(x + half) + half > capWidth / 2.0 || y + glyph > capHeight / 2.0 + arcBase) {
            return false;
        }
        double screenX = screenWidth / 2.0 + x * scale;
        double screenY = screenHeight / 2.0 + y * scale;
        double size = glyph * scale;
        if (screenX < 0 || screenY < 0 || screenX + size > screenWidth || screenY + size > screenHeight
                || HudVanilla.overlaps(screenX, screenY, size, size, screenWidth, screenHeight)) {
            return false;
        }
        for (int other = 0; other < slots; other++) {
            if (Math.abs(slotX[other] - x) < glyph + GAP - 0.01 && Math.abs(slotY[other] - y) < glyph + GAP - 0.01) {
                return false;
            }
        }
        slotX[slots] = x;
        slotY[slots] = y;
        slots++;
        return true;
    }

    /**
     * A point on the ellipse with semi-axes {@code reachX} and {@code reachY} at {@code angle} (0 straight down,
     * positive to the right). The first ring's ellipse passes through the corners of the zone grown by half a glyph,
     * so every glyph on it clears the zone.
     */
    private static double centreX(double angle, double reachX, double reachY) {
        return Math.sin(angle) * reachX;
    }

    private static double centreY(double angle, double reachX, double reachY) {
        return Math.cos(angle) * reachY;
    }

    private void sizeCells(UiDraw draw, int size, boolean rows, int nameWidth) {
        iconSize = AbilityWidget.iconSize(size);
        glyph = AbilityWidget.glyphSize(iconSize);
        if (!names) {
            cellWidth = glyph;
            cellHeight = glyph;
        } else if (rows) {
            cellWidth = Math.max(glyph, nameWidth);
            cellHeight = glyph + 2 + draw.lineHeight();
        } else {
            cellWidth = glyph + 3 + nameWidth;
            cellHeight = glyph;
        }
    }

    private static int capacity(HudOptions.Abilities options, int perLine, int lines) {
        return Math.min(options.maxShown(), perLine * lines);
    }

    /**
     * Fills {@link #shown} with what fits {@code capacity} cells: everything, or each crowded skill folded into one
     * glyph, or the most urgent glyphs plus a "+n" cell; then puts them in display order.
     */
    private void choose(HudOptions.Abilities options, int capacity) {
        folded.clear();
        int count = candidates.size();
        if (count <= capacity) {
            folded.addAll(candidates);
            for (int index = 0; index < count; index++) {
                foldedCount[index] = 1;
            }
        } else if (options.collapseGroups()) {
            fold();
        } else {
            folded.addAll(candidates);
            for (int index = 0; index < count && index < foldedCount.length; index++) {
                foldedCount[index] = 1;
            }
        }
        if (folded.size() > capacity) {
            keepMostUrgent(capacity - 1);
        }
        displayOrder(options.sort());
    }

    /** One glyph per skill (its most urgent) carrying the skill's glyph count; glyphs without a skill stay single. */
    private void fold() {
        candidates.sort(URGENCY);
        for (int index = 0; index < candidates.size(); index++) {
            HudStatusEntry entry = candidates.get(index);
            int group = -1;
            if (!entry.skillId.isEmpty()) {
                for (int existing = 0; existing < folded.size(); existing++) {
                    if (folded.get(existing).skillId.equals(entry.skillId)) {
                        group = existing;
                        break;
                    }
                }
            }
            if (group >= 0) {
                foldedCount[group]++;
            } else if (folded.size() < foldedCount.length) {
                foldedCount[folded.size()] = 1;
                folded.add(entry);
            }
        }
    }

    private void keepMostUrgent(int keep) {
        int total = 0;
        for (int index = 0; index < folded.size(); index++) {
            total += foldedCount[index];
        }
        sortFolded(URGENCY);
        while (folded.size() > keep) {
            folded.remove(folded.size() - 1);
        }
        int kept = 0;
        for (int index = 0; index < folded.size(); index++) {
            kept += foldedCount[index];
        }
        hidden = total - kept;
    }

    private void displayOrder(GlyphSort sort) {
        sortFolded(switch (sort) {
            case SKILL -> BY_SKILL;
            case READY -> BY_READY;
            case RECENT -> BY_RECENT;
        });
        shown.clear();
        for (int index = 0; index < folded.size(); index++) {
            shown.add(folded.get(index));
            groupCount[index] = foldedCount[index];
        }
    }

    /** Sorts {@link #folded} keeping each glyph's count with it (insertion sort: lists are short and mostly sorted). */
    private void sortFolded(Comparator<HudStatusEntry> order) {
        for (int index = 1; index < folded.size(); index++) {
            HudStatusEntry entry = folded.get(index);
            int count = foldedCount[index];
            int at = index - 1;
            while (at >= 0 && order.compare(folded.get(at), entry) > 0) {
                folded.set(at + 1, folded.get(at));
                foldedCount[at + 1] = foldedCount[at];
                at--;
            }
            folded.set(at + 1, entry);
            foldedCount[at + 1] = count;
        }
    }

    private static int rank(HudStatusEntry entry) {
        return entry.glyph == HudGlyph.CHARGES || entry.glyph == HudGlyph.TOGGLE ? 0 : 1;
    }

    /** Whether the arrangement adds lines toward the top or left by default: away from the edge it is attached to. */
    private static boolean linesReversed(HudOptions.Abilities options, HudAnchor anchor) {
        return switch (options.arrangement()) {
            case ROW -> false;
            case BAR -> true;
            case COLUMN_LEFT -> false;
            case COLUMN_RIGHT -> true;
            case GRID -> anchor.row() == 2;
            case ARC -> false;
        };
    }

    static double capWidth(HudWidget.Frame frame, HudOptions.Abilities options) {
        return frame.screenWidth() * Math.min(0.9, options.footprint() * 1.5);
    }

    static double capHeight(HudWidget.Frame frame, HudOptions.Abilities options) {
        return frame.screenHeight() * Math.min(0.9, options.footprint() * 2.0);
    }

    /** Width in scaled units the arrangement's region leaves, before the widget's own scale. */
    static double roomWidth(HudWidget.Frame frame, HudOptions.Abilities options, HudAnchor anchor) {
        return region(frame, options, anchor, true);
    }

    static double roomHeight(HudWidget.Frame frame, HudOptions.Abilities options, HudAnchor anchor) {
        return region(frame, options, anchor, false);
    }

    /**
     * The region each arrangement may fill, in scaled units: under the zone (wide while above chat and clear of the
     * scoreboard, hotbar-wide below that), on top of the hotbar up to the zone, the left edge between the skill rows
     * and chat, or a corner between vanilla areas; always within the footprint cap.
     */
    private static double region(HudWidget.Frame frame, HudOptions.Abilities options, HudAnchor anchor, boolean horizontal) {
        double width = frame.screenWidth();
        double height = frame.screenHeight();
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        double zoneHalfWidth = Math.max(frame.zone().width / 2.0, 8.0);
        double zoneHalfHeight = Math.max(frame.zone().height / 2.0, 8.0);
        double zoneBottom = halfHeight + zoneHalfHeight + 2.0;
        double capWidth = capWidth(frame, options);
        double capHeight = capHeight(frame, options);
        double hotbarTop = height - HudVanilla.HOTBAR_HEIGHT - 2.0;
        double chatTop = height - HudVanilla.CHAT_RISE - 2.0;
        double scoreboardBottom = halfHeight + HudVanilla.SCOREBOARD_HALF_HEIGHT + REGION_MARGIN;
        double besideHotbar = halfWidth - HudVanilla.HOTBAR_HALF_WIDTH - REGION_MARGIN * 2;
        double regionWidth;
        double regionHeight;
        switch (corner(options, anchor)) {
            case 0 -> {
                if (chatTop - zoneBottom >= AbilityWidget.glyphSize(AbilityWidget.iconSize(options.iconSize()))) {
                    regionWidth = clearOfScoreboard(width, zoneBottom, scoreboardBottom);
                    regionHeight = chatTop - zoneBottom;
                } else {
                    regionWidth = Math.min(clearOfScoreboard(width, zoneBottom, scoreboardBottom), HudVanilla.HOTBAR_HALF_WIDTH * 2);
                    regionHeight = hotbarTop - zoneBottom;
                }
            }
            case 1 -> {
                regionWidth = Math.min(clearOfScoreboard(width, zoneBottom, scoreboardBottom), HudVanilla.HOTBAR_HALF_WIDTH * 2);
                regionHeight = height - 76.0 - zoneBottom;
            }
            case 2 -> {
                regionWidth = Math.min(besideHotbar, halfWidth - zoneHalfWidth - REGION_MARGIN * 2);
                regionHeight = chatTop - 62.0;
            }
            case 3 -> {
                regionWidth = besideHotbar;
                regionHeight = height - REGION_MARGIN - Math.max(scoreboardBottom, zoneBottom);
            }
            case 4 -> {
                regionWidth = Math.min(besideHotbar, halfWidth - zoneHalfWidth - REGION_MARGIN * 2);
                regionHeight = Math.min(halfHeight - HudVanilla.SCOREBOARD_HALF_HEIGHT, halfHeight - zoneHalfHeight) - REGION_MARGIN - 56.0;
            }
            default -> {
                regionWidth = besideHotbar;
                regionHeight = height - REGION_MARGIN - (height - 38.0);
            }
        }
        return Math.max(0.0, Math.min(horizontal ? capWidth : capHeight, horizontal ? regionWidth : regionHeight));
    }

    /** The width a centred region starting at {@code top} may take without reaching the scoreboard band. */
    private static double clearOfScoreboard(double width, double top, double scoreboardBottom) {
        return top < scoreboardBottom ? width - (HudVanilla.SCOREBOARD_WIDTH + REGION_MARGIN) * 2 : width - 8;
    }

    /** 0 under the zone, 1 on the hotbar, 2 top left, 3 bottom right, 4 top right, 5 bottom left. */
    private static int corner(HudOptions.Abilities options, HudAnchor anchor) {
        return switch (options.arrangement()) {
            case ROW, ARC -> 0;
            case BAR -> 1;
            case COLUMN_LEFT -> 2;
            case COLUMN_RIGHT -> 3;
            case GRID -> switch (anchor) {
                case TOP_LEFT -> 2;
                case TOP_RIGHT -> 4;
                case BOTTOM_LEFT -> 5;
                case BOTTOM_RIGHT -> 3;
                case BOTTOM_CENTER -> 1;
                default -> 0;
            };
        };
    }
}
