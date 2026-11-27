package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiTome;

import java.util.ArrayList;
import java.util.List;

/**
 * A book dressed in its skill's theme (the demo's A1 re-skinned tome, and B1 for a mutated skill), laid out once for
 * one geometry: the cover, bands, page washes, foot scene ground, parchment sheets under the text, spine and bookmark as a
 * recorded layer, then the foot band or foot scenes, dressing, particles, page life and, last, the wisps. Every frame
 * replays the layer and draws the moving parts at offsets from the clock; nothing is laid out or allocated per frame.
 */
public final class ThemedBook {
    private static final int[] DECKLE = {0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0};
    private static final int[] TONGUE = {0, 0, 1, 2, 3, 4, 4, 4, 3, 1, 0, -1, -3, -4, -4, -3, -2, -2, -1, 0, 1, 3, 4, 3, 2, 0, -1, -2,
            -4, -4, -3, -1};
    private static final int[] FINGER = {0, 3, 3, -3, -3, 3, 0, 0, 0};
    private static final int[] STEP4 = {0, 1, 0, -1};
    private static final int SPARK = 0xFFFFF6D8;
    private static final int WISP_TRAIL = 16;
    private static final int PAPER = UiRamp.PAGE[1];

    private final BookGeometry g;
    private final PageTheme theme;
    private final DrawList layer = new DrawList();
    private final List<Band> bands = new ArrayList<>();
    private final Dressing dressing;
    private final PageLife life;
    private final List<BandStage> edges;
    private final int[] jitter = new int[64];
    private final int[] kick = new int[64];

    private record Band(ThemeKit kit, int x, int tiles) {
    }

    private ThemedBook(BookGeometry geometry, PageTheme pageTheme) {
        g = geometry;
        theme = pageTheme;
        dressing = Dressing.compose(geometry, pageTheme);
        life = PageLife.of(geometry, pageTheme, dressing);
        edges = BandStage.of(geometry, pageTheme);
        background();
        layer.close();
    }

    public static ThemedBook of(BookGeometry geometry, PageTheme theme) {
        return new ThemedBook(geometry, theme);
    }

    public BookGeometry geometry() {
        return g;
    }

    public PageTheme theme() {
        return theme;
    }

    public Dressing dressing() {
        return dressing;
    }

    public PageLife life() {
        return life;
    }

    /** How many quads the recorded layer replays each frame. */
    public int layerSize() {
        return layer.size();
    }

    /** Whose kit dresses the page part at {@code x}. */
    public ThemeKit owner(int x) {
        return theme.owner(x, g.fold());
    }

    private void background() {
        BookGeometry.Box b = g.book();
        int f = g.fold();
        layer.fill(b.x0() + 4, b.y0() + 4, b.x1() - 4, b.y1() - 4, PAPER);
        for (BookGeometry.Box p : g.paper()) {
            layer.fill(p.x0(), p.y0(), p.x1(), p.y1(), owner((p.x0() + p.x1()) / 2).color('4'));
        }
        if (!g.single()) {
            if (theme.mutated()) {
                int leadWash = theme.lead().color('4');
                int partnerWash = theme.partner().color('4');
                for (int y = b.y0() + 4; y < b.y1() - 4; y++) {
                    int t = TONGUE[(y - b.y0()) % TONGUE.length];
                    if (t > 0) {
                        layer.fill(f, y, f + t, y + 1, leadWash);
                    } else if (t < 0) {
                        layer.fill(f + t, y, f, y + 1, partnerWash);
                    }
                }
            }
            int shade = ThemeColor.alpha(UiRamp.INK[0], 0.12);
            layer.fill(f - 5, b.y0() + 4, f, b.y1() - 4, shade);
            layer.fill(f - 2, b.y0() + 4, f + 2, b.y1() - 4, shade);
            layer.fill(f, b.y0() + 4, f + 1, b.y1() - 4, ThemeColor.alpha(UiRamp.INK[0], 0.4));
        }
        for (BookGeometry.Page page : g.pages()) {
            sheet(page.x() - 3, g.bodyTop() - 3, page.right() + 3, b.y1() - 10);
        }
        for (BookGeometry.Span span : g.spans(theme)) {
            cover(span);
        }
        if (theme.mutated() && !g.single()) {
            bandJoint();
        }
        // tops and feet over the cover, the partner's first so the lead's end laps over it at the spine
        for (int index = edges.size() - 1; index >= 0; index--) {
            if (!edges.get(index).behind()) {
                edges.get(index).ground(layer);
            }
        }
        if (!theme.mutated() && !g.single()) {
            ThemeKit kit = theme.lead();
            int bx = f + 6;
            int by = b.y0() - 6;
            int k = kit.color('k');
            int p = kit.color('p');
            layer.fill(bx, by, bx + 9, by + 14, k);
            layer.fill(bx + 1, by + 1, bx + 8, by + 13, p);
            layer.fill(bx + 1, by + 1, bx + 2, by + 13, ThemeColor.lerp(p, 0xFFFFFFFF, 0.35));
            layer.fill(bx + 3, by + 11, bx + 6, by + 13, k);
            layer.fill(bx + 4, by + 10, bx + 5, by + 11, k);
        }
    }

    /** A clean parchment sheet: the contrast treatment under all text, its edge a drawn deckle. */
    private void sheet(int x0, int y0, int x1, int y1) {
        layer.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, PAPER);
        for (int x = x0 + 2; x < x1 - 2; x++) {
            if (DECKLE[(x - x0) % DECKLE.length] == 0) {
                layer.pixel(x, y0, PAPER, false);
            }
        }
        for (int x = x0 + 2; x < x1 - 2; x++) {
            if (DECKLE[(x - x0 + 9) % DECKLE.length] == 0) {
                layer.pixel(x, y1 - 1, PAPER, false);
            }
        }
        for (int y = y0 + 2; y < y1 - 2; y++) {
            if (DECKLE[(y - y0 + 5) % DECKLE.length] == 0) {
                layer.pixel(x0, y, PAPER, true);
            }
        }
        for (int y = y0 + 2; y < y1 - 2; y++) {
            if (DECKLE[(y - y0 + 14) % DECKLE.length] == 0) {
                layer.pixel(x1 - 1, y, PAPER, true);
            }
        }
    }

    private void post(int x, int y, ThemeKit kit) {
        layer.fill(x, y, x + 7, y + 7, kit.color('k'));
        layer.fill(x + 1, y + 1, x + 6, y + 6, kit.color('b'));
        layer.fill(x + 1, y + 1, x + 6, y + 2, kit.color('c'));
        layer.fill(x + 1, y + 1, x + 2, y + 6, kit.color('c'));
        layer.fill(x + 3, y + 3, x + 4, y + 4, kit.color('p'));
    }

    /**
     * The cover along one span: the kit's material in place of leather on the sides and foot, posts where a strip ends,
     * and the band over the top in whole tiles closed at both ends. A ghost kit keeps only a thin outline, and a kit with a
     * trim not even that: its puffs hide the edge.
     */
    private void cover(BookGeometry.Span s) {
        BookGeometry.Box b = g.book();
        BookGeometry.Box c = g.cover();
        ThemeKit kit = s.kit();
        int x0 = s.x0();
        int x1 = s.x1();
        boolean wide = x1 - x0 >= 24;
        if (!kit.solid()) {
            if (kit.dress(DressSlot.TRIM).length > 0) {
                return;
            }
            int line = ThemeColor.lerp(kit.color('4'), kit.color('k'), 0.6);
            int px0 = Math.max(x0, b.x0() + 3);
            int px1 = Math.min(x1, b.x1() - 3);
            if (px1 > px0) {
                layer.fill(px0, b.y0() + 3, px1, b.y0() + 4, line);
                layer.fill(px0, b.y1() - 4, px1, b.y1() - 3, line);
            }
            if (s.outerLeft()) {
                layer.fill(b.x0() + 3, b.y0() + 4, b.x0() + 4, b.y1() - 4, line);
            }
            if (s.outerRight()) {
                layer.fill(b.x1() - 4, b.y0() + 4, b.x1() - 3, b.y1() - 4, line);
            }
            return;
        }
        UiTexture texture = kit.texture();
        if (wide) {
            ThemeSprite mat = kit.sprite("mat");
            for (int x = x0; x < x1; x += 8) {
                layer.blit(texture, mat, x, b.y1() - 2, x1 - x, c.y1() - (b.y1() - 2));
            }
        }
        ThemeSprite side = kit.sprite("matT");
        for (int y = c.y0(); y < c.y1() - 5; y += 8) {
            if (s.outerLeft()) {
                layer.blit(texture, side, c.x0() + 1, y, side.width(), c.y1() - y);
            }
            if (s.outerRight()) {
                layer.blit(texture, side, b.x1() - 2, y, side.width(), c.y1() - y);
            }
        }
        int k = kit.color('k');
        int a = kit.color('a');
        int edge = UiRamp.PAGE[5];
        if (s.outerLeft()) {
            layer.fill(c.x0(), c.y0(), c.x0() + 1, c.y1() - 1, k);
            layer.fill(c.x0() + 5, c.y0(), c.x0() + 6, b.y1() - 3, a);
        }
        if (s.outerRight()) {
            layer.fill(c.x1() - 1, c.y0(), c.x1(), c.y1() - 1, k);
            layer.fill(b.x1() - 3, c.y0(), b.x1() - 2, b.y1() - 3, a);
            layer.fill(b.x1() - 4, b.y0() + 4, b.x1() - 3, b.y1() - 3, edge);
        }
        if (!wide) {
            post(x0 - 1, c.y1() - 7, kit);
            post(x0 - 1, c.y0(), kit);
            return;
        }
        layer.fill(x0, c.y1() - 1, x1, c.y1(), k);
        layer.fill(x0 + (s.outerLeft() ? 5 : 0), b.y1() - 3, x1 - (s.outerRight() ? 5 : 0), b.y1() - 2, a);
        layer.fill(Math.max(x0, b.x0() + 4), b.y1() - 4, Math.min(x1, b.x1() - 3), b.y1() - 3, edge);
        post(x0, c.y1() - 7, kit);
        post(x1 - 7, c.y1() - 7, kit);
        if (kit.top() != null) {
            return;
        }
        int n = Math.floorDiv(x1 - x0 - 1, 16);
        int start = s.outerLeft() ? x0 : x1 - 1 - n * 16;
        layer.fill(start, c.y0(), start + n * 16, c.y0() + 6, ThemeColor.lerp(kit.color('4'), k, 0.45));
        ThemeSprite band = kit.sprite("band");
        for (int i = 0; i < n; i++) {
            layer.blit(texture, band, start + i * 16, c.y0() - 3);
        }
        if (kit.cloth()) {
            bands.add(new Band(kit, start, n));
        }
        layer.fill(start - 1, c.y0() - 2, start, c.y0() + 3, k);
        layer.fill(start + n * 16, c.y0() - 2, start + n * 16 + 1, c.y0() + 3, k);
    }

    /**
     * Where the two parents' bands meet over the spine they dovetail: the lead's upper courses run three units past the
     * fold, the partner's lower courses three units back, with one outline between. A ghost parent, or one with a designed
     * top, has no band, so the other's closes at the fold.
     */
    private void bandJoint() {
        int f = g.fold();
        BookGeometry.Box c = g.cover();
        ThemeKit lead = theme.lead();
        ThemeKit partner = theme.partner();
        boolean leadBand = lead.solid() && lead.top() == null;
        boolean partnerBand = partner.solid() && partner.top() == null;
        if (leadBand) {
            layer.fill(f - 8, c.y0(), f, c.y0() + 6, ThemeColor.lerp(lead.color('4'), lead.color('k'), 0.45));
        }
        if (partnerBand) {
            layer.fill(f, c.y0(), f + 8, c.y0() + 6, ThemeColor.lerp(partner.color('4'), partner.color('k'), 0.45));
        }
        for (int r = 0; r < 9; r++) {
            for (int col = 0; col < 16; col++) {
                int edge = 8 + (leadBand && partnerBand ? FINGER[r] : 0);
                boolean mine = col < edge;
                ThemeKit kit = mine ? lead : partner;
                if (!(mine ? leadBand : partnerBand)) {
                    continue;
                }
                String row = kit.band()[r];
                char ch = col < row.length() ? row.charAt(col) : '.';
                if (ch == '.') {
                    continue;
                }
                boolean seam = mine ? col == edge - 1 : col == edge && !leadBand;
                layer.pixel(f - 8 + col, c.y0() - 3 + r, seam && r > 0 && r < 6 ? kit.color('k') : kit.color(ch), false);
            }
        }
    }

    /** Everything behind the page's content, at {@code millis} on the page clock. */
    public void paintBack(UiDraw draw, long millis) {
        double t = millis;
        double seconds = millis / 1000.0;
        layer.paint(draw);
        int hemY = g.cover().y0() + 4;
        for (int index = 0; index < bands.size(); index++) {
            Band band = bands.get(index);
            ThemeSprite hem = band.kit().sprite("hem");
            for (int i = 0; i < band.tiles(); i++) {
                if (((int) Math.floor(t / 420 - i * 0.7) & 3) == 0) {
                    blit(draw, band.kit(), hem, band.x() + i * 16, hemY, 1.0);
                }
            }
        }
        List<PageLife.FootTile> foot = life.foot();
        for (int index = 0; index < foot.size(); index++) {
            PageLife.FootTile tile = foot.get(index);
            blit(draw, tile.kit(), ((int) Math.floor(t / 700 + tile.phase()) & 1) == 1 ? tile.swayed() : tile.rest(), tile.x(), tile.y(), 1.0);
        }
        for (int index = 0; index < edges.size(); index++) {
            edges.get(index).paint(draw, seconds);
        }
        List<Dressing.Piece> pieces = dressing.pieces();
        for (int index = 0; index < pieces.size(); index++) {
            piece(draw, pieces.get(index), seconds);
        }
        emitters(draw, seconds);
        flyers(draw, seconds);
        hoppers(draw, seconds);
        glints(draw, seconds);
    }

    /** The wisps of a mutated book, over everything else. */
    public void paintFront(UiDraw draw, long millis) {
        double seconds = millis / 1000.0;
        List<PageLife.Wisp> wisps = life.wisps();
        for (int index = 0; index < wisps.size(); index++) {
            wisp(draw, wisps.get(index), seconds);
        }
    }

    private static void blit(UiDraw draw, ThemeKit kit, ThemeSprite sprite, int x, int y, double alpha) {
        int a = KitFrameCanvas.alphaByte(alpha);
        if (sprite == null || a == 0) {
            return;
        }
        draw.blit(kit.texture(), x, y, sprite.width(), sprite.height(), sprite.u(), sprite.v(), sprite.width(), sprite.height(),
                a << 24 | 0xFFFFFF);
    }

    /** Rows {@code from} to {@code to} of a sprite, drawn {@code dx} across. */
    private static void rows(UiDraw draw, ThemeKit kit, ThemeSprite sprite, int x, int y, int from, int to) {
        if (to > from) {
            draw.blit(kit.texture(), x, y + from, sprite.width(), to - from, sprite.u(), sprite.v() + from, sprite.width(), to - from);
        }
    }

    private void piece(UiDraw draw, Dressing.Piece p, double seconds) {
        ThemeKit kit = p.kit();
        DressMove move = kit.move(p.slot());
        DressSprite sprite = p.sprite();
        ThemeSprite image = sprite.frame(p.frame(seconds), p.flip());
        int s = STEP4[(int) Math.floor(seconds * 1.6 - p.x() / 34.0 - p.y() / 70.0) & 3];
        int x = p.x();
        int y = p.y();
        int h = sprite.height();
        if (move == DressMove.BOB) {
            int dy = STEP4[(int) Math.floor(seconds * 1.1 + p.phase() * 4) & 3];
            int dx = p.slot() == DressSlot.BIT ? 0 : STEP4[(int) Math.floor(seconds * 0.7 + p.phase() * 9) & 3];
            blit(draw, kit, image, x + dx, y + dy, 1.0);
        } else if (move == DressMove.SWAY && (s != 0 || p.slot() == DressSlot.HANG)) {
            if (p.slot() == DressSlot.FOOT) {
                int cut = (int) Math.round(h * 0.4);
                rows(draw, kit, image, x + s, y, 0, cut);
                rows(draw, kit, image, x, y, cut, h);
            } else if (p.slot() == DressSlot.HANG && h >= 10) {
                int a = (int) Math.round(h * 0.35);
                int z = (int) Math.round(h * 0.7);
                int s2 = STEP4[(int) Math.floor(seconds * 1.6 - p.x() / 34.0 - p.y() / 70.0 - 0.6) & 3];
                rows(draw, kit, image, x, y, 0, a);
                rows(draw, kit, image, x + s, y, a, z);
                rows(draw, kit, image, x + s + s2, y, z, h);
            } else {
                int cut = (int) Math.round(h * 0.5);
                rows(draw, kit, image, x, y, 0, cut);
                rows(draw, kit, image, x + s, y, cut, h);
            }
        } else {
            blit(draw, kit, image, x, y, 1.0);
        }
        if (p.drips() != 0) {
            double period = 2.6 + p.phase() * 2;
            double q = (seconds / period + p.phase()) % 1;
            int room = Math.min(22, p.drips() - (y + h) - 3);
            if (room > 5 && q < 0.6) {
                DressSprite[] bits = kit.dress(DressSlot.BIT);
                ThemeSprite drop = bits[2 % bits.length].frame(0, false);
                double fall = q / 0.6;
                blit(draw, kit, drop, x + (sprite.width() >> 1) - (drop.width() >> 1), (int) Math.round(y + h + fall * fall * room),
                        Math.sin(fall * Math.PI));
            }
        }
    }

    /** Particles: the kit's loose bits leaving a cluster on a short path, fading at both ends, in lanes outside the book. */
    private void emitters(UiDraw draw, double seconds) {
        List<Dressing.Emitter> emitters = dressing.emitters();
        for (int index = 0; index < emitters.size(); index++) {
            Dressing.Emitter e = emitters.get(index);
            ThemeKit kit = e.kit();
            String mode = kit.emit();
            double period = 2.4 + e.phase() * 2.6;
            double q = (seconds / period + e.phase() * 5) % 1;
            long epoch = (long) Math.floor(seconds / period + e.phase() * 5);
            DressSprite[] bits = kit.dress(DressSlot.BIT);
            ThemeSprite bit = bits[kit.emitBit() % bits.length].frame(0, false);
            double j = ThemeRandom.h01(jitterPrefix(e.index()), epoch);
            int lane = e.lane() == Dressing.Lane.LEFT ? -1 : e.lane() == Dressing.Lane.RIGHT ? 1 : 0;
            boolean up = e.lane() == Dressing.Lane.UP;
            double x = e.x();
            double y = e.y();
            switch (mode) {
                case "rise" -> {
                    y -= q * (10 + j * 8);
                    x += Math.sin(q * 5 + j * 6) * 2 + lane * 2;
                }
                case "fall" -> {
                    if (up) {
                        x += (j - 0.5) * 16;
                        y -= 6 - q * 4;
                    } else {
                        y += q * (10 + j * 10);
                    }
                    x += Math.sin(q * 6 + j * 6) * 2.5 + lane * 3;
                }
                case "drift" -> {
                    x += (q - 0.5) * 26;
                    y += Math.sin(q * 6.283 + j * 6) * 2 - (up ? 5 : -3);
                }
                default -> {
                    x += (j - 0.5) * 14 + lane * 4;
                    y += (ThemeRandom.h01(kickPrefix(e.index()), epoch) - 0.5) * 8 - (up ? 5 : 0);
                }
            }
            int px = (int) Math.round(x - bit.width() / 2.0);
            int py = (int) Math.round(y - bit.height() / 2.0);
            if (px < 1 || py < 1 || px + bit.width() > g.screenWidth() - 1 || py + bit.height() > g.screenHeight() - 1) {
                continue;
            }
            blit(draw, kit, bit, px, py, Math.sin(q * Math.PI));
        }
    }

    private int jitterPrefix(int index) {
        if (index >= jitter.length) {
            return ThemeRandom.prefix("j" + index);
        }
        if (jitter[index] == 0) {
            jitter[index] = ThemeRandom.prefix("j" + index);
        }
        return jitter[index];
    }

    private int kickPrefix(int index) {
        if (index >= kick.length) {
            return ThemeRandom.prefix("k" + index);
        }
        if (kick[index] == 0) {
            kick[index] = ThemeRandom.prefix("k" + index);
        }
        return kick[index];
    }

    private void flyers(UiDraw draw, double seconds) {
        int ceiling = g.cover().y0() - 6;
        List<PageLife.Flyer> flyers = life.flyers();
        for (int index = 0; index < flyers.size(); index++) {
            PageLife.Flyer f = flyers.get(index);
            double a = seconds * f.speed() + f.phase();
            double x = Math.max(f.from(), Math.min(f.to(), f.centre() + Math.sin(a) * f.reach()));
            boolean butterfly = f.name().equals("butterfly");
            double y = f.height() + Math.sin(a * 2.3 + f.phase()) * 2 + (butterfly ? Math.sin(seconds * 5 + f.phase()) : 0);
            int frame = (int) Math.floor(seconds * (f.name().equals("bird") ? 3 : 7) + f.phase()) & 1;
            ThemeSprite sprite = f.kit().castSprite(f.name(), frame, Math.cos(a) < 0);
            if (sprite == null) {
                continue;
            }
            int py = (int) Math.max(f.top() + 1, Math.min(ceiling, Math.round(y)));
            blit(draw, f.kit(), sprite, (int) Math.round(x - sprite.width() / 2.0), py, 1.0);
        }
    }

    private void hoppers(UiDraw draw, double seconds) {
        List<PageLife.Hopper> hoppers = life.hoppers();
        for (int index = 0; index < hoppers.size(); index++) {
            PageLife.Hopper h = hoppers.get(index);
            double q = (seconds / h.period() + h.phase()) % 1;
            long epoch = (long) Math.floor(seconds / h.period() + h.phase());
            double k = q / 0.28;
            if (k >= 1) {
                continue;
            }
            double x = h.from() + ThemeRandom.h01(h.prefix(), epoch) * (h.to() - h.from()) + (k - 0.5) * 10;
            ThemeSprite sprite = h.kit().castSprite(h.name(), k < 0.5 ? 0 : 1, false);
            if (sprite == null) {
                continue;
            }
            blit(draw, h.kit(), sprite, (int) Math.round(x), (int) Math.round(g.book().y1() - 9 - Math.sin(k * Math.PI) * 6),
                    Math.min(1, Math.sin(k * Math.PI) * 2.5));
        }
    }

    private void glints(UiDraw draw, double seconds) {
        List<PageLife.Glint> glints = life.glints();
        for (int index = 0; index < glints.size(); index++) {
            PageLife.Glint w = glints.get(index);
            List<Dressing.Piece> candidates = w.pieces();
            if (candidates.isEmpty()) {
                continue;
            }
            double q = (seconds / w.period() + w.phase()) % 1;
            long epoch = (long) Math.floor(seconds / w.period() + w.phase());
            Dressing.Piece p = candidates.get((int) Math.floor(ThemeRandom.h01(w.pickPrefix(), epoch) * candidates.size()));
            DressSprite sprite = p.sprite();
            int row = (int) Math.floor(ThemeRandom.h01(w.rowPrefix(), epoch) * sprite.height() * 0.6);
            int col = (int) Math.floor(ThemeRandom.h01(w.columnPrefix(), epoch) * sprite.width());
            int n = 0;
            while (n < sprite.width() && !sprite.opaque(p.flip(), col, row)) {
                col = (col + 1) % sprite.width();
                n++;
            }
            if (n >= sprite.width()) {
                continue;
            }
            int x = p.x() + col;
            int y = p.y() + row;
            double a = Math.sin(q * Math.PI);
            draw.fill(x, y, x + 1, y + 1, ThemeColor.alpha(SPARK, a));
            if (a > 0.6) {
                int soft = ThemeColor.alpha(SPARK, a * 0.8);
                draw.fill(x - 1, y, x + 2, y + 1, soft);
                draw.fill(x, y - 1, x + 1, y + 2, soft);
            }
            if (a > 0.93) {
                int faint = ThemeColor.alpha(SPARK, 0.5);
                draw.fill(x - 2, y, x + 3, y + 1, faint);
                draw.fill(x, y - 2, x + 1, y + 3, faint);
            }
        }
    }

    /** A wisp: a pale head with a soft halo and a fading tail that loops as it drifts from one parent's side to the other's. */
    private void wisp(UiDraw draw, PageLife.Wisp w, double seconds) {
        double q = (seconds / w.period() + w.phase()) % 1;
        double fade = Math.min(1, Math.sin(q * Math.PI) * 1.6);
        int f = g.fold();
        BookGeometry.Box c = g.cover();
        for (int j = WISP_TRAIL - 1; j >= 0; j--) {
            double qj = q - j * 0.009;
            if (qj < 0) {
                continue;
            }
            double th = qj * 6.283 * w.turns() + w.phase() * 6;
            double ax;
            double ay;
            if (w.kind() == 2) {
                ax = f + Math.cos(th) * w.radius();
                ay = w.y() - (qj - 0.5) * 26 + Math.sin(th) * 2;
            } else {
                double s = Math.sin(qj * Math.PI);
                ax = f + w.direction() * (qj * 2 - 1) * w.distance() + Math.cos(th) * w.radius();
                double lift = Math.sin(th) * w.radius() * 0.8;
                ay = w.kind() == 0 ? c.y0() - 11 - s * 2 + lift : c.y1() + 11 + s * 2 + lift;
            }
            int x = (int) Math.round(ax);
            int y = (int) Math.round(ay);
            if (x < 3 || y < 3 || x > g.screenWidth() - 5 || y > g.screenHeight() - 5) {
                continue;
            }
            double alpha = fade * (1 - j / (double) WISP_TRAIL) * (j < 6 ? 1 : 0.7);
            int color = ThemeColor.alpha(j < 2 ? SPARK : qj < 0.5 ? w.fromColor() : w.toColor(), alpha);
            int size = j < 7 ? 2 : 1;
            draw.fill(x, y, x + size, y + size, color);
            if (j == 0) {
                int halo = ThemeColor.alpha(w.halo(), fade * 0.4);
                draw.fill(x - 1, y, x + 3, y + 2, halo);
                draw.fill(x, y - 1, x + 2, y + 3, halo);
                draw.fill(x, y, x + 2, y + 2, ThemeColor.alpha(SPARK, fade));
            }
        }
    }

    /**
     * The title ribbon behind the breadcrumbs in the lead's colours, its fill darkened until gilt lettering keeps 5.2:1
     * on it, with a swallowtail at its right end.
     */
    public void ribbon(UiDraw draw, int x, int y, int width) {
        ThemeKit kit = theme.lead();
        int k = kit.color('k');
        int fill = kit.color('a');
        for (int i = 0; i < 8 && ThemeColor.contrast(UiRamp.GOLD[3], fill) < 5.2; i++) {
            fill = ThemeColor.lerp(fill, 0xFF000000, 0.15);
        }
        int end = x + width;
        draw.fill(x, y + 1, end - 4, y + 15, k);
        draw.fill(end - 4, y + 1, end, y + 7, k);
        draw.fill(end - 4, y + 9, end, y + 15, k);
        draw.fill(x + 1, y + 2, end - 4, y + 14, fill);
        draw.fill(end - 4, y + 2, end - 1, y + 6, fill);
        draw.fill(end - 4, y + 10, end - 1, y + 14, fill);
        draw.fill(x + 1, y + 2, end - 4, y + 3, kit.color('b'));
    }

    /** A slip of paper under a header title that sits on the dyed page, so it keeps the sheets' contrast. */
    public void slip(UiDraw draw, int textX, int y, int textWidth) {
        UiTome.soft(draw, textX - 3, y + 2, textX + 4 + textWidth, y + 14, PAPER);
    }

    /** The ruled divider under a page's header in its owner's kit: the kit's rule tiles and a jewel in its middle. */
    public void divider(UiDraw draw, int left, int right, int top) {
        ThemeKit kit = owner(left + 20);
        ThemeSprite tile = kit.sprite("div");
        int n = Math.floorDiv(right - left - 6, 8);
        for (int i = 0; i < n; i++) {
            blit(draw, kit, tile, left + 3 + i * 8, top, 1.0);
        }
        int mid = (left + right) >> 1;
        draw.fill(mid - 2, top - 1, mid + 3, top + 4, kit.color('k'));
        draw.fill(mid - 1, top, mid + 2, top + 3, kit.color('p'));
        draw.fill(mid, top + 1, mid + 1, top + 2, kit.color('q'));
    }
}
