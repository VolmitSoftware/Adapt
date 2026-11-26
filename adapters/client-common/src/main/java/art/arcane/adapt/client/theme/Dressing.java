package art.arcane.adapt.client.theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A book's dressing, composed once per layout: a list of whole drawn sprites placed round its edge, and the points its
 * particles leave from. The rules are the demo's: a sprite is placed whole or not at all, and one that would touch
 * content or leave the screen is dropped, never cut; an edge is clusters of two to four overlapping sprites with gaps
 * between them, or single pieces with gaps for a solo kit; a mutated book also gets the crossing at the spine head
 * and foot and the braid down the gutter; a kit with a foot scene has no foot spills, hangs under the book or corner pile,
 * one with a designed top no top pieces, header hangs or corner piece. The same geometry and kits always give the same dressing.
 */
public final class Dressing {
    private static final int NO_BAND = 0;
    private static final int FOOT_BAND = 1;
    private static final int TOP_BAND = 2;

    private final List<Piece> pieces;
    private final List<Emitter> emitters;
    private final long seed;

    /**
     * One placed sprite. {@code phase} offsets its animation; {@code drips} is the y its drops fade before, or 0. A
     * self-timed sprite's copy starts at frame step {@code start} and steps in {@code direction}, 1 or -1.
     */
    public record Piece(ThemeKit kit, DressSlot slot, int index, DressSprite sprite, int x, int y, boolean flip, double phase,
                        int drips, boolean spine, int start, int direction) {

        /** The frame this piece shows at {@code seconds}. */
        public int frame(double seconds) {
            if (sprite.fps() > 0) {
                return sprite.frameAt(seconds, start, direction);
            }
            if (sprite.frameCount() > 1) {
                return (int) Math.floor(seconds * (kit.move(slot) == DressMove.SPIN ? 2.5 : 6) + phase * 7);
            }
            return 0;
        }
    }

    /** Where particles leave from and which way they go. */
    public enum Lane {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    /** One particle source: its kit, its point and lane, its phase and its index among the book's emitters. */
    public record Emitter(ThemeKit kit, int x, int y, Lane lane, double phase, int index) {
    }

    private Dressing(List<Piece> pieces, List<Emitter> emitters, long seed) {
        this.pieces = List.copyOf(pieces);
        this.emitters = List.copyOf(emitters);
        this.seed = seed;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<Emitter> emitters() {
        return emitters;
    }

    /** The layout seed, which the page's life and its particles also start from. */
    public long seed() {
        return seed;
    }

    /** The demo's seed for a book: the skills' names and the leaf, hashed. */
    static long seed(PageTheme theme, int leaf) {
        String key = theme.lead().seed() + (theme.partner() != null ? theme.partner().seed() : "") + leaf;
        return ThemeRandom.unsigned(ThemeRandom.hash(key)) % 100000L;
    }

    /** Whether {@code sprite} at (x, y) lies inside the screen with a unit to spare and touches no keep-out box. */
    static boolean fits(DressSprite sprite, int x, int y, boolean flip, List<BookGeometry.Box> keep, int width, int height) {
        if (x < 1 || y < 1 || x + sprite.width() > width - 1 || y + sprite.height() > height - 1) {
            return false;
        }
        for (BookGeometry.Box box : keep) {
            if (x >= box.x1() || x + sprite.width() <= box.x0() || y >= box.y1() || y + sprite.height() <= box.y0()) {
                continue;
            }
            for (int yy = Math.max(y, box.y0()); yy < Math.min(y + sprite.height(), box.y1()); yy++) {
                for (int xx = Math.max(x, box.x0()); xx < Math.min(x + sprite.width(), box.x1()); xx++) {
                    if (sprite.opaque(flip, xx - x, yy - y)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Composes the dressing of {@code theme} on a book laid out as {@code geometry}. */
    public static Dressing compose(BookGeometry geometry, PageTheme theme) {
        Composer composer = new Composer(geometry, theme, seed(theme, geometry.leaf()));
        composer.run();
        List<Emitter> emitters = new ArrayList<>();
        int prefix = ThemeRandom.prefix("e" + composer.seed);
        for (Pending pending : composer.emit) {
            if (pending.kit().emit() != null && !(pending.band() == FOOT_BAND && pending.kit().foot() != null)
                    && !(pending.band() == TOP_BAND && pending.kit().top() != null)) {
                int index = emitters.size();
                emitters.add(new Emitter(pending.kit(), pending.x(), pending.y(), pending.lane(), ThemeRandom.h01(prefix, index), index));
            }
        }
        return new Dressing(composer.list, emitters, composer.seed);
    }

    /** A particle source before the kits' rules apply; {@code band} says which edge's pieces it leaves from. */
    private record Pending(ThemeKit kit, int x, int y, Lane lane, int band) {
    }

    /** Places one sprite at an edge position and says how much of the edge it used, or 0 when nothing fit. */
    private interface Place {
        int at(int n, double at, double end);
    }

    private static final class Composer {
        private final BookGeometry g;
        private final PageTheme theme;
        private final long seed;
        private final ThemeRandom r;
        private final List<Piece> list = new ArrayList<>();
        private final List<Pending> emit = new ArrayList<>();
        private final Map<String, Integer> last = new HashMap<>();
        private final Map<ThemeKit, int[]> started = new HashMap<>();
        private final Map<ThemeKit, int[]> turned = new HashMap<>();
        private int trims;

        private Composer(BookGeometry geometry, PageTheme pageTheme, long layoutSeed) {
            g = geometry;
            theme = pageTheme;
            seed = layoutSeed;
            r = new ThemeRandom(layoutSeed);
        }

        private Piece add(ThemeKit kit, DressSlot slot, int index, int x, int y, boolean flip, int drips, boolean spine) {
            return add(kit, slot, index, x, y, flip, drips, spine, Double.NaN);
        }

        /** {@code phase} NaN takes the phase from the stream; a piece placed by hashes passes its own. */
        private Piece add(ThemeKit kit, DressSlot slot, int index, int x, int y, boolean flip, int drips, boolean spine, double given) {
            DressSprite[] sprites = kit.dress(slot);
            int wrapped = index % sprites.length;
            DressSprite sprite = sprites[wrapped];
            if (!fits(sprite, x, y, flip, g.keep(), g.screenWidth(), g.screenHeight())) {
                return null;
            }
            double phase = Double.isNaN(given) ? r.next() : given;
            int start = (int) Math.floor(phase * sprite.loop());
            if (sprite.step() > 0) {
                start = started.computeIfAbsent(kit, (ThemeKit ignored) -> new int[1])[0]++ * sprite.step() % sprite.loop();
            }
            int direction = 1;
            if (sprite.turn()) {
                direction = ((++turned.computeIfAbsent(kit, (ThemeKit ignored) -> new int[1])[0] & 1) == 1 ? 1 : -1) * (flip ? -1 : 1);
            }
            Piece piece = new Piece(kit, slot, wrapped, sprite, x, y, flip, phase, drips, spine, start, direction);
            list.add(piece);
            return piece;
        }

        private static DressSprite pick(ThemeKit kit, DressSlot slot, int index) {
            DressSprite[] sprites = kit.dress(slot);
            return sprites[index % sprites.length];
        }

        /** Which variant comes next: never the one just used, so no piece repeats back to back. */
        private int vary(ThemeKit kit, DressSlot slot) {
            int n = kit.dress(slot).length;
            String key = kit.seed() + slot.key();
            int i = (int) (r.next() * n);
            Integer previous = last.get(key);
            if (n > 1 && previous != null && i == previous) {
                i = (i + 1 + (int) (r.next() * (n - 1))) % n;
            }
            last.put(key, i);
            return i;
        }

        private void strand(double a, double end, ThemeKit kit, Place place) {
            double c = a;
            int n = 0;
            while (c < end) {
                int size = kit.ring() ? 2 + (int) (r.next() * 4) : kit.solo() ? 1 : 1 + (int) (r.next() * 3);
                boolean any = false;
                for (int i = 0; i < size && c < end; i++, n++) {
                    int used = place.at(n, c, end);
                    if (used == 0) {
                        c += 6;
                        continue;
                    }
                    any = true;
                    c += used - 1 - (int) (r.next() * 5);
                }
                c += kit.ring() ? r.next() * 7 : (any ? 12 + r.next() * 30 : 8) / kit.density();
            }
        }

        private void run() {
            BookGeometry.Box c = g.cover();
            BookGeometry.Box b = g.book();
            List<BookGeometry.Span> spans = g.spans(theme);
            for (BookGeometry.Span s : spans) {
                ThemeKit kit = s.kit();
                if (kit.dress(DressSlot.TRIM).length == 0) {
                    continue;
                }
                int from = s.outerLeft() ? b.x0() + 2 : s.x0();
                int to = s.outerRight() ? b.x1() - 2 : s.x1();
                chain(kit, true, b.y0() + 3, from, to, 1);
                chain(kit, true, b.y1() - 4, from, to, -1);
                if (s.outerLeft()) {
                    chain(kit, false, b.x0() + 3, b.y0() + 2, b.y1() - 2, 1);
                }
                if (s.outerRight()) {
                    chain(kit, false, b.x1() - 4, b.y0() + 2, b.y1() - 2, -1);
                }
            }
            for (BookGeometry.Span s : spans) {
                ThemeKit kit = s.kit();
                int yB = g.headerTop() - 2;
                if (kit.top() == null) {
                    strand(s.x0() + (s.outerLeft() ? 11 : 2), s.x1() - (s.outerRight() ? 11 : 2), kit, (int n, double at, double end) -> {
                    DressSprite[] tops = kit.dress(DressSlot.TOP);
                    for (int v = 0, i0 = vary(kit, DressSlot.TOP); v < tops.length; v++) {
                        DressSprite sprite = pick(kit, DressSlot.TOP, i0 + v);
                        if (at + sprite.width() > end) {
                            continue;
                        }
                        int x = (int) Math.round(at);
                        int y = yB - sprite.height() - (r.next() < 0.3 ? 1 : 0);
                        boolean flip = r.next() < 0.5 && !kit.noflip();
                        Piece piece = add(kit, DressSlot.TOP, i0 + v, x, y, flip, 0, false);
                        if (piece != null) {
                            if (n % 2 == 0) {
                                emit.add(new Pending(kit, piece.x() + (sprite.width() >> 1), piece.y(), Lane.UP, TOP_BAND));
                            }
                            return sprite.width();
                        }
                    }
                    return 0;
                });
                }
                if (kit.foot() == null) {
                    strand(s.x0() + (s.outerLeft() ? 12 : 2), s.x1() - (s.outerRight() ? 12 : 2), kit, (int n, double at, double end) -> {
                    DressSprite[] feet = kit.dress(DressSlot.FOOT);
                    for (int v = 0, i0 = vary(kit, DressSlot.FOOT); v < feet.length; v++) {
                        DressSprite sprite = pick(kit, DressSlot.FOOT, i0 + v);
                        if (at + sprite.width() > end) {
                            continue;
                        }
                        int x = (int) Math.round(at);
                        int y = b.y1() - 8 + (r.next() < 0.3 ? 1 : 0);
                        boolean flip = r.next() < 0.5 && !kit.noflip();
                        Piece piece = add(kit, DressSlot.FOOT, i0 + v, x, y, flip, 0, false);
                        if (piece != null) {
                            if (n % 2 == 1) {
                                emit.add(new Pending(kit, piece.x() + (sprite.width() >> 1), c.y1() + 2, Lane.DOWN, FOOT_BAND));
                            }
                            return sprite.width();
                        }
                    }
                    return 0;
                });
                }
                for (int side = 0; side < 2; side++) {
                    boolean right = side == 1;
                    if (right ? !s.outerRight() : !s.outerLeft()) {
                        continue;
                    }
                    if (kit.column()) {
                        column(kit, right);
                        continue;
                    }
                    strand(c.y0() + 9, c.y1() - 12, kit, (int n, double at, double end) -> {
                        DressSprite[] sides = kit.dress(DressSlot.SIDE);
                        for (int v = 0, i0 = vary(kit, DressSlot.SIDE); v < sides.length; v++) {
                            DressSprite sprite = pick(kit, DressSlot.SIDE, i0 + v);
                            if (at + sprite.height() > end) {
                                continue;
                            }
                            int x = (right ? c.x1() - 8 : c.x0() + 8 - sprite.width()) + (r.next() < 0.3 ? (right ? 1 : -1) : 0);
                            Piece piece = add(kit, DressSlot.SIDE, i0 + v, x, (int) Math.round(at), right, 0, false);
                            if (piece != null) {
                                if (n % 2 == 0) {
                                    emit.add(new Pending(kit, right ? piece.x() + sprite.width() - 2 : piece.x() + 1,
                                            piece.y() + (sprite.height() >> 1), right ? Lane.RIGHT : Lane.LEFT, NO_BAND));
                                }
                                return sprite.height();
                            }
                        }
                        return 0;
                    });
                }
                boolean drip = kit.move(DressSlot.HANG) == DressMove.DRIP;
                int hy = kit.solid() ? c.y0() + 4 : b.y0() + 4;
                if (s.hangs() && kit.top() == null) {
                    int i = 0;
                    for (double x = s.hangFrom() + 3 + r.next() * 8; x < s.hangTo(); i++) {
                        int index = i + (int) (seed & 3);
                        DressSprite sprite = pick(kit, DressSlot.HANG, index);
                        Piece piece = add(kit, DressSlot.HANG, index, (int) Math.round(x), hy, false, drip ? g.headerTop() + 15 : 0, false);
                        x += (piece != null ? sprite.width() : 0) + (9 + r.next() * 20) / kit.density();
                    }
                }
                if (kit.foot() == null) {
                    int i = 1;
                    for (double x = s.x0() + 30 + r.next() * 30; x < s.x1() - 30; i++) {
                        DressSprite sprite = pick(kit, DressSlot.HANG, i);
                        Piece piece = add(kit, DressSlot.HANG, i, (int) Math.round(x), c.y1() - 1, false, drip ? g.screenHeight() - 4 : 0, false);
                        x += (piece != null ? sprite.width() : 0) + 40 + r.next() * 70;
                    }
                }
            }
            for (BookGeometry.Span s : spans) {
                ThemeKit kit = s.kit();
                boolean piles = kit.foot() == null || !kit.foot().cornered();
                boolean corners = kit.top() == null || !kit.top().cornered();
                int inset = kit.solid() ? 0 : 5;
                if (s.outerLeft()) {
                    if (piles) {
                        add(kit, DressSlot.PILE, 0, c.x0() - 8 + inset, c.y1() + 6 - kit.dress(DressSlot.PILE)[0].height() - inset, false, 0, false);
                    }
                    if (corners) {
                        add(kit, DressSlot.CORNER, 0, c.x0() - 8 + inset, c.y0() - 8 + inset, false, 0, false);
                    }
                }
                if (s.outerRight()) {
                    if (piles) {
                        DressSprite pile = kit.dress(DressSlot.PILE)[0];
                        add(kit, DressSlot.PILE, 0, c.x1() + 8 - pile.width() - inset, c.y1() + 6 - pile.height() - inset, true, 0, false);
                    }
                    if (corners) {
                        add(kit, DressSlot.CORNER, 0, c.x1() + 8 - kit.dress(DressSlot.CORNER)[0].width() - inset, c.y0() - 8 + inset, true, 0, false);
                    }
                }
            }
            if (theme.mutated()) {
                tangle();
            }
        }

        /**
         * A kit with a column has one whole piece a side, from under the top band's corner down into the foot's corner
         * smoke, the variant picked by a hash, so it moves nothing else; it is drawn first, under the trim, and its
         * emitters leave from four points down its outer edge.
         */
        private void column(ThemeKit kit, boolean right) {
            BookGeometry.Box c = g.cover();
            DressSprite[] sprites = kit.dress(DressSlot.SIDE);
            String key = seed + ":" + (right ? 1 : 0);
            int index = (int) Math.floor(ThemeRandom.h01("side" + key, 0) * sprites.length);
            DressSprite sprite = sprites[index];
            Piece piece = add(kit, DressSlot.SIDE, index, right ? c.x1() - 8 : c.x0() + 8 - sprite.width(), c.y0() + 6, right, 0, false,
                    ThemeRandom.h01("sideph" + key, 0));
            if (piece == null) {
                return;
            }
            // drawn first, under the trim, whose puffs billow over its inner edge onto the page
            list.remove(list.size() - 1);
            list.add(0, piece);
            for (int k = 0; k < 4; k++) {
                emit.add(new Pending(kit, right ? piece.x() + sprite.width() - 4 : piece.x() + 3,
                        piece.y() + (int) Math.round(sprite.height() * (k + 0.5) / 4), right ? Lane.RIGHT : Lane.LEFT, NO_BAND));
            }
        }

        /**
         * A kit with a trim billows over the page's inner edge instead of ending on a ruled line: a chain of puffs along
         * one edge from {@code a} to {@code z}, each reaching up to five units past the line where the content leaves
         * room, thinning to wisps at its ends and stopping at the spine. It runs before everything else and from
         * hashes, not the stream, so no other piece moves.
         */
        private void chain(ThemeKit kit, boolean across, int line, int a, int z, int inward) {
            DressSprite[] sprites = kit.dress(DressSlot.TRIM);
            for (int c = a; c < z; ) {
                int d0 = c - a;
                int d1 = z - c;
                int want = d0 < 4 || d1 < 8 ? 3 : d0 < 12 || d1 < 18 ? 2 : ThemeRandom.h01("trim" + kit.seed(), trims) < 0.55 ? 0 : 1;
                boolean flip = ThemeRandom.h01("tflip" + seed, trims) < 0.5;
                int most = 2 + (int) Math.floor(ThemeRandom.h01("reach" + seed, trims) * 4);
                int placed = 0;
                for (int index = Math.min(want, sprites.length - 1); index < sprites.length && placed == 0; index++) {
                    DressSprite sprite = sprites[index];
                    int length = across ? sprite.width() : sprite.height();
                    int deep = across ? sprite.height() : sprite.width();
                    if (c + length > z) {
                        continue;
                    }
                    for (int reach = Math.min(most, deep - 2); reach >= 1 && placed == 0; reach--) {
                        int offset = inward > 0 ? line + reach + 1 - deep : line - reach;
                        int x = across ? c : offset;
                        int y = across ? offset : c;
                        if (!fits(sprite, x, y, flip, g.keep(), g.screenWidth(), g.screenHeight())) {
                            continue;
                        }
                        double phase = ThemeRandom.h01("trim" + seed, list.size());
                        list.add(new Piece(kit, DressSlot.TRIM, index, sprite, x, y, flip, phase, 0, false, (int) Math.floor(phase * sprite.loop()), 1));
                        placed = length - Math.max(2, (int) Math.round(length * 0.35));
                    }
                }
                trims++;
                if (placed == 0 && c + 4 > z) {
                    break;
                }
                c += placed != 0 ? placed : 3;
            }
        }

        private Piece reach(ThemeKit kit, int y, int cross) {
            DressSprite sprite = kit.dress(DressSlot.REACH)[0];
            boolean mine = kit == theme.lead();
            int f = g.fold();
            return add(kit, DressSlot.REACH, 0, mine ? f + cross - sprite.width() : f - cross, y, !mine, 0, true);
        }

        private Piece hook(ThemeKit kit, int y) {
            DressSprite sprite = kit.dress(DressSlot.HOOK)[0];
            boolean mine = kit == theme.lead();
            int f = g.fold();
            return add(kit, DressSlot.HOOK, 0, mine ? f - 6 : f + 7 - sprite.width(), y, !mine, 0, true);
        }

        private void top(ThemeKit kit, int index, double cx) {
            DressSprite sprite = pick(kit, DressSlot.TOP, index);
            add(kit, DressSlot.TOP, index, (int) Math.round(cx - sprite.width() / 2.0), g.headerTop() - 2 - sprite.height(),
                    kit == theme.partner() && !kit.noflip(), 0, true);
        }

        /**
         * The mutation: over the spine head the two parents' top pieces run into each other, a reach of each crosses on
         * the band and a hang of each drops into the gutter; hooks braid down the gutter; the same crossing closes the
         * foot. Every piece touches the band or another piece.
         */
        private void tangle() {
            BookGeometry.Box c = g.cover();
            BookGeometry.Box b = g.book();
            ThemeKit lead = theme.lead();
            ThemeKit partner = theme.partner();
            int f = g.fold();
            reach(lead, c.y0() - 8, 12);
            reach(partner, c.y0() - 3, 12);
            if (partner.top() == null) {
                top(partner, 1, f - 27);
            }
            if (lead.top() == null) {
                top(lead, 0, f - 7);
            }
            if (partner.top() == null) {
                top(partner, 0, f + 9);
            }
            if (lead.top() == null) {
                top(lead, 1, f + 28);
            }
            for (ThemeKit kit : List.of(lead, partner)) {
                for (int i = 0; i < 3; i++) {
                    DressSprite sprite = pick(kit, DressSlot.HANG, i);
                    if (add(kit, DressSlot.HANG, i, kit == lead ? f - 5 - sprite.width() : f + 4, c.y0() + 4, false, 0, true) != null) {
                        break;
                    }
                }
            }
            double from = g.bodyTop() + 1;
            int end = b.y1() - 19;
            int i = 0;
            for (double y = from; y < end; ) {
                int n = g.single() ? 99 : 3 + (int) (r.next() * 3);
                for (int j = 0; j < n && y < end; j++, i++) {
                    ThemeKit kit = (i & 1) == 1 ? partner : lead;
                    Piece piece = hook(kit, (int) Math.round(y));
                    y += (piece != null ? piece.sprite().height() : 5) - 1 + (int) (r.next() * 2);
                }
                y += 12 + r.next() * 22;
            }
            reach(lead, b.y1() - 14, 13);
            reach(partner, b.y1() - 10, 12);
            if (partner.foot() == null) {
                add(partner, DressSlot.FOOT, 1, f - 9, b.y1() - 8, true, 0, true);
            }
            if (lead.foot() == null) {
                add(lead, DressSlot.FOOT, 1, f + 9 - pick(lead, DressSlot.FOOT, 1).width(), b.y1() - 7, false, 0, true);
            }
            emit.add(new Pending(lead, f + 6, c.y0() - 6, Lane.UP, TOP_BAND));
            emit.add(new Pending(partner, f - 6, c.y0() - 6, Lane.UP, TOP_BAND));
            emit.add(new Pending(lead, f + 4, c.y1() + 2, Lane.DOWN, FOOT_BAND));
            emit.add(new Pending(partner, f - 4, c.y1() + 2, Lane.DOWN, FOOT_BAND));
        }
    }
}
