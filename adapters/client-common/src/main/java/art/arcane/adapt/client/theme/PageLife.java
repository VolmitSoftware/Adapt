package art.arcane.adapt.client.theme;

import java.util.ArrayList;
import java.util.List;

/**
 * What moves on a book besides its dressing's sway, laid out once per layout: each kit's flyers over the book (not over a
 * designed top), a hopper jumping out of the foot band (not for a kit with a foot scene), glints wandering over the dressing, the foot band's tiles and, on a mutated book, the
 * wisps curling between the two parents at the spine. Everything is a small sprite on a closed-form path, so any moment
 * can be drawn alone and reduced motion holds one.
 */
public final class PageLife {
    private final List<Flyer> flyers = new ArrayList<>();
    private final List<Hopper> hoppers = new ArrayList<>();
    private final List<Glint> glints = new ArrayList<>();
    private final List<Wisp> wisps = new ArrayList<>();
    private final List<FootTile> foot = new ArrayList<>();

    /** A flyer swinging {@code reach} either side of {@code centre} between {@code from} and {@code to}. */
    public record Flyer(ThemeKit kit, String name, double centre, double reach, double height, double speed, double phase, int from,
                        int to, int top) {
    }

    public record Hopper(ThemeKit kit, String name, int from, int to, double phase, double period, int prefix) {
    }

    /** A glint over one of {@code pieces}, the kit's dressing that glints can land on. */
    public record Glint(ThemeKit kit, double period, double phase, List<Dressing.Piece> pieces, int pickPrefix, int rowPrefix,
                        int columnPrefix) {
    }

    /** A wisp: {@code kind} 0 over the spine head, 1 under its foot, 2 in the gutter. */
    public record Wisp(int kind, double y, double distance, double radius, double period, double phase, double turns, int direction,
                       int fromColor, int toColor, int halo) {
    }

    /** One foot band tile: its sprites at rest and swayed and its sway phase. */
    public record FootTile(ThemeKit kit, ThemeSprite rest, ThemeSprite swayed, int x, int y, double phase) {
    }

    public List<Flyer> flyers() {
        return flyers;
    }

    public List<Hopper> hoppers() {
        return hoppers;
    }

    public List<Glint> glints() {
        return glints;
    }

    public List<Wisp> wisps() {
        return wisps;
    }

    public List<FootTile> foot() {
        return foot;
    }

    /** The life of a book laid out as {@code g} and dressed with {@code dressing}. */
    public static PageLife of(BookGeometry g, PageTheme theme, Dressing dressing) {
        PageLife life = new PageLife();
        long seed = dressing.seed();
        ThemeRandom r = new ThemeRandom(seed + 77);
        BookGeometry.Box c = g.cover();
        List<BookGeometry.Box> paper = g.paper();
        int sky = Math.max(0, c.y0() - 20);
        int glintCount = 0;
        for (BookGeometry.Span s : g.spans(theme)) {
            ThemeKit kit = s.kit();
            int x0 = s.x0() + 22;
            int x1 = s.x1() - 22;
            int p0 = Math.max(s.x0(), paper.get(0).x0()) + 12;
            int p1 = Math.min(s.x1(), paper.get(paper.size() - 1).x1()) - 12;
            String flyer = kit.flyer();
            if (flyer != null && kit.top() == null && x1 - x0 > 40) {
                for (int i = 0; i < kit.flyers(); i++) {
                    double centre = x0 + (x1 - x0) * (0.2 + 0.6 * r.next());
                    double reach = flyer.equals("butterfly") ? 16 + r.next() * 14 : Math.min(90, (x1 - x0) * 0.4);
                    double height = sky + 5 + r.next() * (c.y0() - sky - 15);
                    double speed = flyer.equals("bird") ? 0.18 + r.next() * 0.1 : flyer.equals("bat") ? 0.5 + r.next() * 0.3 : 0.35 + r.next() * 0.2;
                    double phase = r.next() * 6.3;
                    life.flyers.add(new Flyer(kit, flyer, centre, reach, height, speed, phase, x0, x1, sky));
                }
            }
            if (kit.hopper() != null && kit.foot() == null && p1 - p0 > 30) {
                double phase = r.next();
                double period = 3.2 + r.next() * 1.5;
                life.hoppers.add(new Hopper(kit, kit.hopper(), p0, p1, phase, period, ThemeRandom.prefix("hop" + seed)));
            }
            List<Dressing.Piece> mine = new ArrayList<>();
            for (Dressing.Piece piece : dressing.pieces()) {
                if (piece.kit() == kit && piece.slot() != DressSlot.BIT && piece.slot() != DressSlot.HOOK) {
                    mine.add(piece);
                }
            }
            for (int i = 0; i < kit.glints(); i++) {
                double period = 1.3 + r.next() * 1.4;
                double phase = r.next();
                int index = glintCount++;
                life.glints.add(new Glint(kit, period, phase, List.copyOf(mine), ThemeRandom.prefix("tw" + seed + index),
                        ThemeRandom.prefix("ty" + index), ThemeRandom.prefix("tx" + index)));
            }
        }
        if (theme.mutated()) {
            wisps(life, g, theme);
        }
        footTiles(life, g, theme);
        return life;
    }

    private static void wisps(PageLife life, BookGeometry g, PageTheme theme) {
        ThemeRandom r = new ThemeRandom(ThemeRandom.unsigned(ThemeRandom.hash("wisp" + theme.lead().seed() + theme.partner().seed())));
        double top = g.bodyTop() + 14;
        double span = g.book().y1() - 40 - top;
        int[] kinds = {0, 0, 1, 2, 0, 1, 2};
        for (int i = 0; i < kinds.length; i++) {
            int kind = kinds[i];
            boolean mine = (i & 1) == 1;
            ThemeKit from = mine ? theme.lead() : theme.partner();
            ThemeKit to = mine ? theme.partner() : theme.lead();
            double y = top + r.next() * span;
            double distance = kind == 2 ? 0 : 14 + r.next() * 16;
            double radius = kind == 2 ? 3 : 3.5 + r.next() * 1.5;
            double period = 4.2 + r.next() * 2.6;
            double phase = r.next();
            double turns = 2 + r.next() * 1.5;
            life.wisps.add(new Wisp(kind, y, distance, radius, period, phase, turns, mine ? 1 : -1, from.color('3'), to.color('3'),
                    from.color('p')));
        }
    }

    /**
     * The foot band: whole 16 wide tiles in two rows, the paler row behind, half a tile across and three units higher.
     * On a mutated spread the back row changes hands a tile and a half past the front row, so the two bands overlap,
     * unless either parent plays a foot scene: then each keeps to its own half. A kit with a scene has no band.
     */
    private static void footTiles(PageLife life, BookGeometry g, PageTheme theme) {
        List<BookGeometry.Box> paper = g.paper();
        int p0 = paper.get(0).x0();
        int p1 = paper.get(paper.size() - 1).x1();
        int y1 = paper.get(0).y1();
        for (int row = 0; row < 2; row++) {
            int a = p0 + (row == 1 ? 0 : 8);
            int z = p1 - (row == 1 ? 0 : 8);
            boolean split = theme.lead().foot() != null || (theme.mutated() && theme.partner().foot() != null);
            int cut = !theme.mutated() ? z : g.single() ? (g.leaf() == 1 ? z : a) : split ? g.fold() : g.fold() + (row == 1 ? -16 : 8);
            footRun(life, theme.lead(), a, cut, row, y1);
            if (theme.mutated()) {
                footRun(life, theme.partner(), cut, z, row, y1);
            }
        }
    }

    private static void footRun(PageLife life, ThemeKit kit, int from, int to, int row, int y1) {
        if (!kit.footBand() || kit.foot() != null) {
            return;
        }
        int n = Math.floorDiv(to - from, 16);
        int start = from + ((to - from - n * 16) >> 1);
        int mirror = ThemeRandom.prefix(kit.seed() + row);
        int jitter = ThemeRandom.prefix("j" + kit.seed());
        for (int i = 0; i < n; i++) {
            int x = start + i * 16;
            boolean mirrored = ThemeRandom.h01(mirror, x) < 0.45;
            String name = "foot/" + row + "/" + (mirrored ? 1 : 0) + "/";
            int y = y1 - (row == 1 ? 9 : 12) - (row == 0 && ThemeRandom.h01(jitter, i) < 0.3 ? 1 : 0);
            life.foot.add(new FootTile(kit, kit.sprite(name + "a"), kit.sprite(name + "b"), x, y, x / 45.0 + (row == 1 ? 0 : 1.5)));
        }
    }
}
