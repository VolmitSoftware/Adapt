package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiDraw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One kit's top or foot band laid out on one book: the stretch it plays on (the whole edge for a plain skill, each
 * parent's side for a mutation, mirrored on the partner's side so both face the binding), the line its scene stands
 * on (the ground's top on the book's bottom edge for a foot, the strip's bottom on the cover's top edge for a top) and
 * its cast with every step resolved to screen coordinates. An element that would at any moment touch content, leave
 * the screen or, unless it crosses, leave its own side is dropped whole with whatever needs it. A frame is a step
 * lookup, an ease and a blit or two per element (one per live particle for an emitter), and allocates nothing.
 */
public final class BandStage {
    private static final int SAMPLES = 8;
    private static final int PARTICLE_STRIDE = 64;
    private static final int CORNER_REACH = 12;

    private final ThemeKit kit;
    private final BandScene scene;
    private final boolean top;
    private final boolean mirror;
    private final boolean neighbour;
    private final boolean mutated;
    private final int groundFrom;
    private final int groundTo;
    private final int cornerFrom;
    private final int cornerTo;
    private final int stageFrom;
    private final int stageTo;
    private final int width;
    private final int groundY;
    private final int base;
    private final int spanFrom;
    private final int spanTo;
    private final List<Actor> actors = new ArrayList<>();
    private final long[] scratch = new long[2];
    private final boolean behind;
    private final List<ThemeSprite> strip = new ArrayList<>();
    private final List<Integer> stripX = new ArrayList<>();
    private final List<Integer> stripTile = new ArrayList<>();
    private int stripUsed;

    /** An element's resolved timeline: per step its start, length, path, ease, clip, facing, carried items and particles. */
    static final class Actor {
        final String id;
        final BandScene.Sprite sprite;
        final int[] on;
        final boolean cross;
        final boolean behind;
        final boolean corner;
        final int steps;
        final double[] start;
        final double[] length;
        final double[] x0;
        final double[] y0;
        final double[] x1;
        final double[] y1;
        final double[] arc;
        final double[] radiusX;
        final double[] radiusY;
        final double[] angleFrom;
        final double[] angleTo;
        final int[] ease;
        final BandScene.Clip[] clip;
        final BandScene.Clip[] alt;
        final int[][] loops;
        final boolean[] flip;
        final BandScene.Sprite[][] carry;
        final BandScene.Emit[] emit;
        final double[] speedXFrom;
        final double[] speedXTo;
        final int prefixX;
        final int prefixY;

        private Actor(String id, BandScene.Sprite sprite, int[] on, boolean cross, boolean behind, boolean corner, int steps, String key) {
            this.id = id;
            this.sprite = sprite;
            this.on = on;
            this.cross = cross;
            this.behind = behind;
            this.corner = corner;
            this.steps = steps;
            start = new double[steps];
            length = new double[steps];
            x0 = new double[steps];
            y0 = new double[steps];
            x1 = new double[steps];
            y1 = new double[steps];
            arc = new double[steps];
            radiusX = new double[steps];
            radiusY = new double[steps];
            angleFrom = new double[steps];
            angleTo = new double[steps];
            ease = new int[steps];
            clip = new BandScene.Clip[steps];
            alt = new BandScene.Clip[steps];
            loops = new int[steps][];
            flip = new boolean[steps];
            carry = new BandScene.Sprite[steps][];
            emit = new BandScene.Emit[steps];
            speedXFrom = new double[steps];
            speedXTo = new double[steps];
            prefixX = ThemeRandom.prefix("emit" + key + id + "x");
            prefixY = ThemeRandom.prefix("emit" + key + id + "y");
        }

        int step(double q) {
            int i = 0;
            while (i < steps - 1 && q >= start[i] + length[i]) {
                i++;
            }
            return i;
        }

        /** Step {@code i}'s anchor at progress {@code p}, rounded into {@code out}: path, ease, arc and orbit. */
        void at(int i, double p, long[] out) {
            double k = BandScene.ease(ease[i], p);
            double x = x0[i] + (x1[i] - x0[i]) * k;
            double y = y0[i] + (y1[i] - y0[i]) * k - arc[i] * 4 * p * (1 - p);
            if (radiusX[i] != 0 || radiusY[i] != 0) {
                double a = angleFrom[i] + (angleTo[i] - angleFrom[i]) * k;
                x += radiusX[i] * sin(a + Math.PI / 2);
                y -= radiusY[i] * sin(a);
            }
            out[0] = Math.round(x);
            out[1] = Math.round(y);
        }

        /** Sine by the demo's fixed polynomial, so an orbit lands on the same pixels as in the demo. */
        private static double sin(double a) {
            double x = a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
            if (x > Math.PI / 2) {
                x = Math.PI - x;
            } else if (x < -Math.PI / 2) {
                x = -Math.PI - x;
            }
            double x2 = x * x;
            return x * (1 - x2 / 6 * (1 - x2 / 20 * (1 - x2 / 42 * (1 - x2 / 72 * (1 - x2 / 110)))));
        }

        String id() {
            return id;
        }

        boolean cross() {
            return cross;
        }
    }

    /**
     * {@code from} and {@code to} are the corners the stage is measured from (the paper's edges on a foot, the cover's on
     * a top); the strip runs from {@code groundFrom} to {@code groundTo}, which on a foot's outer ends reach on to the
     * cover's edges.
     */
    private BandStage(ThemeKit kit, BandScene scene, boolean top, int from, int to, int groundFrom, int groundTo, boolean mirror, int outer,
                      int inner, int groundY, int base, boolean neighbour, boolean mutated, BookGeometry.Box cover) {
        this.kit = kit;
        this.scene = scene;
        this.top = top;
        this.mirror = mirror;
        this.neighbour = neighbour;
        this.mutated = mutated;
        this.groundFrom = groundFrom;
        this.groundTo = groundTo;
        cornerFrom = from;
        cornerTo = to;
        stageFrom = from + (mirror ? inner : outer);
        stageTo = to - (mirror ? outer : inner);
        width = stageTo - stageFrom;
        this.groundY = groundY;
        this.base = base;
        spanFrom = cover.x0();
        spanTo = cover.x1();
        behind = scene.moving();
        layStrip();
    }

    /** The bands of a book: its foot stages, then its top stages, one per kit with a scene for that edge. */
    public static List<BandStage> of(BookGeometry g, PageTheme theme) {
        List<BandStage> out = new ArrayList<>();
        feet(out, g, theme);
        tops(out, g, theme);
        for (BandStage stage : out) {
            stage.cast(g);
        }
        return out;
    }

    /**
     * The line every foot's base starts on, the same for every skill so the halves of a mutated spread meet level: two
     * units above the book's bottom edge, inside the Chronos pipe. Actors stand on it.
     */
    static int footLine(BookGeometry g) {
        return g.book().y1() - 2;
    }

    /** The line every top's base ends on, where the old checker band met the page. */
    static int topLine(BookGeometry g) {
        return g.cover().y0() + 6;
    }

    /**
     * The foot: each kit's base on the foot line, its crests above it. The stage is measured from the paper's edges; a
     * strip's outer ends run on to the cover's edges, under the corner posts, and its ends at the spine stay put.
     */
    private static void feet(List<BandStage> out, BookGeometry g, PageTheme theme) {
        List<BookGeometry.Box> paper = g.paper();
        BookGeometry.Box first = paper.get(0);
        BookGeometry.Box last = paper.get(paper.size() - 1);
        BookGeometry.Box c = g.cover();
        boolean mutated = theme.mutated();
        int inner = mutated ? 14 : 6;
        if (!mutated) {
            foot(out, g, theme.lead(), first.x0(), last.x1(), c.x0(), c.x1(), false, inner, false, false);
        } else if (!g.single()) {
            foot(out, g, theme.lead(), first.x0(), g.fold(), c.x0(), g.fold(), false, inner, true, true);
            foot(out, g, theme.partner(), g.fold(), last.x1(), g.fold(), c.x1(), true, inner, true, true);
        } else if (g.leaf() == 1) {
            foot(out, g, theme.lead(), first.x0(), first.x1(), c.x0(), first.x1(), false, inner, false, true);
        } else {
            foot(out, g, theme.partner(), first.x0(), first.x1(), first.x0(), c.x1(), true, inner, false, true);
        }
    }

    private static void foot(List<BandStage> out, BookGeometry g, ThemeKit kit, int from, int to, int groundFrom, int groundTo, boolean mirror,
                             int inner, boolean neighbour, boolean mutated) {
        BandScene scene = kit.foot();
        if (scene != null) {
            int line = footLine(g);
            out.add(new BandStage(kit, scene, false, from, to, groundFrom, groundTo, mirror, 10, inner, line - scene.groundPast(), line + 1,
                    neighbour, mutated, g.cover()));
        }
    }

    /** The top: each kit's base on the top line, its fringe below it; on a mutated spread the two strips lap three units over the spine. */
    private static void tops(List<BandStage> out, BookGeometry g, PageTheme theme) {
        BookGeometry.Box c = g.cover();
        BookGeometry.Box b = g.book();
        int f = g.fold();
        boolean mutated = theme.mutated();
        if (!mutated) {
            top(out, g, theme.lead(), c.x0(), c.x1(), false, 14, false, false);
        } else if (!g.single()) {
            top(out, g, theme.lead(), c.x0(), f + 3, false, 32, true, true);
            top(out, g, theme.partner(), f - 3, c.x1(), true, 32, true, true);
        } else if (g.leaf() == 1) {
            top(out, g, theme.lead(), c.x0(), b.x1(), false, 10, false, true);
        } else {
            top(out, g, theme.partner(), b.x0(), c.x1(), true, 10, false, true);
        }
    }

    private static void top(List<BandStage> out, BookGeometry g, ThemeKit kit, int from, int to, boolean mirror, int inner, boolean neighbour,
                            boolean mutated) {
        BandScene scene = kit.top();
        if (scene != null) {
            int line = topLine(g) + scene.groundPast();
            out.add(new BandStage(kit, scene, true, from, to, from, to, mirror, 14, inner, line - scene.groundHeight(), line, neighbour, mutated,
                    g.cover()));
        }
    }

    public ThemeKit kit() {
        return kit;
    }

    /** Whether this is a top band; otherwise a foot. */
    public boolean top() {
        return top;
    }

    public boolean mirror() {
        return mirror;
    }

    public int groundFrom() {
        return groundFrom;
    }

    public int groundTo() {
        return groundTo;
    }

    int stageFrom() {
        return stageFrom;
    }

    int stageTo() {
        return stageTo;
    }

    public int groundY() {
        return groundY;
    }

    int base() {
        return base;
    }

    BandScene scene() {
        return scene;
    }

    List<Actor> actors() {
        return actors;
    }

    private double x(double u) {
        return mirror ? stageTo - u : stageFrom + u;
    }

    private double u(double[] spot) {
        return spot[0] * width + spot[1];
    }

    private void cast(BookGeometry g) {
        Map<String, Actor> byId = new HashMap<>();
        for (BandScene.Element e : scene.cast()) {
            if (("mutated".equals(e.when()) && !neighbour) || ("plain".equals(e.when()) && neighbour)) {
                continue;
            }
            if (e.min() > 0 && width < e.min()) {
                continue;
            }
            if ("end".equals(e.corner()) && mutated) {
                continue;
            }
            boolean missing = false;
            for (String need : e.needs()) {
                missing |= !byId.containsKey(need);
            }
            if (missing) {
                continue;
            }
            Actor actor = resolve(e, byId);
            if (actor == null) {
                continue;
            }
            byId.put(actor.id, actor);
            // a hop or throw too high for the room under the text is flattened, halving its arcs, before it is dropped
            boolean fit = fits(actor, g);
            for (int k = 0; k < 4 && !fit && arced(actor); k++) {
                for (int i = 0; i < actor.steps; i++) {
                    actor.arc[i] = k < 3 ? actor.arc[i] / 2 : 0;
                }
                fit = fits(actor, g);
            }
            if (!fit) {
                byId.remove(actor.id);
                continue;
            }
            actors.add(actor);
        }
    }

    private static boolean arced(Actor actor) {
        for (int i = 0; i < actor.steps; i++) {
            if (actor.arc[i] != 0) {
                return true;
            }
        }
        return false;
    }

    private Actor resolve(BandScene.Element e, Map<String, Actor> byId) {
        List<BandScene.Step> steps = e.steps();
        Actor a = new Actor(e.id(), scene.sprite(e.sprite()), e.on(), e.cross(), e.behind(), e.corner() != null, steps.size(),
                (top ? "top:" : "foot:") + kit.seed());
        int face = e.face() != 0 ? e.face() : "end".equals(e.corner()) ? -1 : 1;
        double u;
        if (e.corner() != null) {
            // centred on its corner, overhanging it; a flush piece stands wholly on the strip, its outer edge on the strip's end
            BandScene.Sprite sprite = a.sprite;
            boolean flip = (face < 0) != mirror;
            int anchor = flip ? sprite.width - 1 - sprite.anchorX : sprite.anchorX;
            boolean right = "end".equals(e.corner()) != mirror;
            int x = !e.flush() ? (right ? cornerTo : cornerFrom) : right ? groundTo - sprite.width + anchor : groundFrom + anchor;
            u = mirror ? stageTo - x : x - stageFrom;
        } else {
            u = u(e.at());
        }
        double t = 0;
        double sign = mirror ? -1 : 1;
        for (int i = 0; i < steps.size(); i++) {
            BandScene.Step s = steps.get(i);
            a.start[i] = t;
            a.length[i] = s.duration();
            a.ease[i] = s.ease();
            String clip = s.clip();
            int[] on = s.on();
            if (s.when() != null && "mutated".equals(s.when()) != neighbour) {
                clip = s.alt();
                on = null;
            }
            if (s.mutatedOn() != null && neighbour) {
                on = s.mutatedOn();
            }
            a.clip[i] = clip == null ? null : a.sprite.clips.get(clip);
            a.alt[i] = s.alt() == null ? null : a.sprite.clips.get(s.alt());
            a.loops[i] = on;
            if (s.carry() != null) {
                BandScene.Sprite[] items = new BandScene.Sprite[s.carry().length];
                for (int k = 0; k < items.length; k++) {
                    items[k] = scene.sprite(s.carry()[k]);
                }
                a.carry[i] = items;
            }
            if (s.from() != null) {
                double[] from = point(s.from(), t, byId);
                double[] to = point(s.flyTo(), t + s.duration(), byId);
                if (from == null || to == null) {
                    return null;
                }
                a.x0[i] = from[0];
                a.y0[i] = from[1];
                a.x1[i] = to[0];
                a.y1[i] = to[1];
                a.arc[i] = s.arc();
                a.flip[i] = to[0] < from[0];
            } else if (s.at() != null) {
                double[] at = point(s.at(), t, byId);
                if (at == null) {
                    return null;
                }
                a.x0[i] = at[0];
                a.y0[i] = at[1];
                a.x1[i] = at[0];
                a.y1[i] = at[1];
                a.flip[i] = (face < 0) != mirror;
            } else {
                double u0 = u;
                if (s.to() != null) {
                    u = u(s.to());
                }
                if (u != u0) {
                    face = u > u0 ? 1 : -1;
                }
                if (s.face() != 0) {
                    face = s.face();
                }
                a.x0[i] = x(u0);
                a.x1[i] = x(u);
                a.y0[i] = base - s.liftFrom();
                a.y1[i] = base - s.liftTo();
                a.arc[i] = s.arc();
                a.flip[i] = (face < 0) != mirror;
            }
            BandScene.Orbit orbit = s.orbit();
            if (orbit != null) {
                a.radiusX[i] = orbit.radiusX() * sign;
                a.radiusY[i] = orbit.radiusY();
                a.angleFrom[i] = orbit.from() * Math.PI / 180;
                a.angleTo[i] = a.angleFrom[i] + orbit.turns() * 2 * Math.PI;
            }
            BandScene.Emit emit = s.emit();
            if (emit != null) {
                a.emit[i] = emit;
                a.speedXFrom[i] = emit.speedXFrom() * sign;
                a.speedXTo[i] = emit.speedXTo() * sign;
            }
            t += s.duration();
        }
        return a;
    }

    /** Where a target is at loop time {@code q}: an element's point, or a spot on the line. Null when the element is gone. */
    private double[] point(BandScene.Target target, double q, Map<String, Actor> byId) {
        if (target.element() == null) {
            return new double[]{Math.round(x(u(target.spot()))), base - target.lift()};
        }
        Actor a = byId.get(target.element());
        if (a == null) {
            return null;
        }
        double p = scene.period();
        double at = ((q % p) + p) % p;
        int i = a.step(at);
        long[] xy = new long[2];
        a.at(i, Math.min(1, Math.max(0, (at - a.start[i]) / a.length[i])), xy);
        int[] hand = pointOf(a.sprite, target.point(), (int) xy[0], (int) xy[1], a.flip[i]);
        return new double[]{hand[0], hand[1]};
    }

    private static int[] pointOf(BandScene.Sprite sprite, String name, int x, int y, boolean flip) {
        int[] pt = name != null && sprite.points.containsKey(name) ? sprite.points.get(name) : new int[]{sprite.anchorX, sprite.anchorY};
        int ax = flip ? sprite.width - 1 - sprite.anchorX : sprite.anchorX;
        int px = flip ? sprite.width - 1 - pt[0] : pt[0];
        return new int[]{x - ax + px, y - sprite.anchorY + pt[1]};
    }

    /** Whether {@code a} stays in its room. */
    private boolean fits(Actor a, BookGeometry g) {
        int lo = a.corner ? spanFrom - CORNER_REACH : a.cross ? spanFrom : groundFrom;
        int hi = a.corner ? spanTo + CORNER_REACH : a.cross ? spanTo : groundTo;
        long[] xy = new long[2];
        for (int i = 0; i < a.steps; i++) {
            if (a.clip[i] == null && a.alt[i] == null) {
                continue;
            }
            for (int n = 0; n <= SAMPLES; n++) {
                double q = a.start[i] + a.length[i] * n / SAMPLES - (n == SAMPLES ? 1e-9 : 0);
                int at = a.step(q);
                a.at(at, Math.min(1, Math.max(0, (q - a.start[at]) / a.length[at])), xy);
                boolean flip = a.flip[at];
                BandScene.Sprite sp = a.sprite;
                int ax = flip ? sp.width - 1 - sp.anchorX : sp.anchorX;
                BandScene.Emit emit = a.emit[at];
                if (emit != null) {
                    double age = emit.life() * n / SAMPLES * 0.999;
                    for (double vx : new double[]{a.speedXFrom[at], a.speedXTo[at]}) {
                        for (double vy : new double[]{emit.speedYFrom(), emit.speedYTo()}) {
                            int px = (int) Math.round(xy[0] + vx * age);
                            int py = (int) Math.round(xy[1] - vy * age + emit.gravity() * age * age / 2);
                            if (!clear(sp, px - ax, py - sp.anchorY, flip, lo, hi, g)) {
                                return false;
                            }
                        }
                    }
                } else if (!clear(sp, (int) xy[0] - ax, (int) xy[1] - sp.anchorY, flip, lo, hi, g)) {
                    return false;
                }
                BandScene.Sprite[] items = a.carry[at];
                if (items != null) {
                    int[] hand = pointOf(sp, "hand", (int) xy[0], (int) xy[1], flip);
                    for (BandScene.Sprite item : items) {
                        int ix = hand[0] - (flip ? item.width - 1 - item.anchorX : item.anchorX);
                        if (!clear(item, ix, hand[1] - item.anchorY, flip, lo, hi, g)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean clear(BandScene.Sprite sp, int x, int y, boolean flip, int lo, int hi, BookGeometry g) {
        if (x < lo || x + sp.width > hi) {
            return false;
        }
        if (x < 1 || y < 1 || x + sp.width > g.screenWidth() - 1 || y + sp.height > g.screenHeight() - 1) {
            return false;
        }
        for (BookGeometry.Box box : g.keep()) {
            if (x >= box.x1() || x + sp.width <= box.x0() || y >= box.y1() || y + sp.height <= box.y0()) {
                continue;
            }
            for (int yy = Math.max(y, box.y0()); yy < Math.min(y + sp.height, box.y1()); yy++) {
                for (int xx = Math.max(x, box.x0()); xx < Math.min(x + sp.width, box.x1()); xx++) {
                    if (sp.opaque(flip, xx - x, yy - y)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** The strip from the outer end: start cap, tiles never repeating back to back, fill columns over the seams, end cap. */
    private void layStrip() {
        int length = groundTo - groundFrom - scene.startWidth() - scene.endWidth();
        int tw = scene.tileWidth();
        int n = Math.max(0, Math.floorDiv(length, tw));
        int r = length - n * tw;
        ThemeRandom pick = new ThemeRandom(ThemeRandom.unsigned(ThemeRandom.hash("strip" + kit.seed())));
        stripPiece(scene.start(mirror), -1);
        if (n == 0) {
            for (int k = 0; k < r; k++) {
                stripPiece(scene.fill(mirror), -1);
            }
        }
        int prefix = ThemeRandom.prefix("ground" + kit.seed());
        int last = -1;
        for (int i = 0; i < n; i++) {
            int v = scene.pickTile(scene.legacyOrder() ? ThemeRandom.h01(prefix, i) : pick.next());
            if (v == last && scene.tileCount() > 1) {
                v = (v + 1) % scene.tileCount();
            }
            last = v;
            stripPiece(scene.tile(v, 0, mirror), v);
            for (int k = Math.floorDiv(i * r, n); k < Math.floorDiv((i + 1) * r, n); k++) {
                stripPiece(scene.fill(mirror), -1);
            }
        }
        stripPiece(scene.end(mirror), -1);
    }

    private void stripPiece(ThemeSprite sprite, int tile) {
        strip.add(sprite);
        stripTile.add(tile);
        stripX.add(mirror ? groundTo - stripUsed - sprite.width() : groundFrom + stripUsed);
        stripUsed += sprite.width();
    }

    /** Whether the strip is drawn with the scene each frame, because some of the cast stands behind it or it moves. */
    boolean behind() {
        return behind;
    }

    /** Records the strip into a static layer. */
    void ground(DrawList layer) {
        for (int index = 0; index < strip.size(); index++) {
            layer.blit(kit.texture(), strip.get(index), stripX.get(index), groundY);
        }
    }

    /** The strip at loop time {@code time}: tiles step through their frames together, each a frame on from the last. */
    private void paintStrip(UiDraw draw, double time) {
        int frames = scene.stripFrames();
        boolean moves = frames > 1 && scene.stripFps() > 0;
        long step = moves ? (long) Math.floor(time * scene.stripFps()) : 0;
        for (int index = 0; index < strip.size(); index++) {
            int tile = stripTile.get(index);
            ThemeSprite sprite = moves && tile >= 0 ? scene.tile(tile, (int) ((step + index) % frames), mirror) : strip.get(index);
            blit(draw, sprite, stripX.get(index), groundY);
        }
    }

    private static int frameOf(BandScene.Clip clip, double time) {
        int frames = clip.frames().length;
        int step = (int) Math.floor(time * clip.fps());
        return clip.frames()[frames == 1 ? 0 : clip.once() ? Math.min(step, frames - 1) : step % frames];
    }

    /** Draws the cast at {@code seconds} on the page clock; 0 is the scene's still pose. */
    public void paint(UiDraw draw, double seconds) {
        double period = scene.period();
        double time = seconds + scene.rest();
        long loop = (long) Math.floor(time / period);
        double q = time - loop * period;
        long[] xy = scratch;
        boolean stripDrawn = !behind;
        for (int index = 0; index < actors.size(); index++) {
            Actor a = actors.get(index);
            if (!stripDrawn && !a.behind) {
                stripDrawn = true;
                paintStrip(draw, time);
            }
            if (a.on != null && loop % a.on[0] != a.on[1]) {
                continue;
            }
            int i = a.step(q);
            BandScene.Clip clip = a.loops[i] != null && loop % a.loops[i][0] != a.loops[i][1] ? a.alt[i] : a.clip[i];
            if (clip == null) {
                continue;
            }
            double local = q - a.start[i];
            a.at(i, local / a.length[i], xy);
            BandScene.Sprite sp = a.sprite;
            boolean flip = a.flip[i];
            int ax = flip ? sp.width - 1 - sp.anchorX : sp.anchorX;
            BandScene.Emit emit = a.emit[i];
            if (emit != null) {
                for (int k = 0; k < emit.count(); k++) {
                    double age = local - k * emit.every();
                    if (age < 0 || age >= emit.life()) {
                        continue;
                    }
                    long v = loop * PARTICLE_STRIDE + k;
                    double vx = a.speedXFrom[i] + (a.speedXTo[i] - a.speedXFrom[i]) * ThemeRandom.h01(a.prefixX, v);
                    double vy = emit.speedYFrom() + (emit.speedYTo() - emit.speedYFrom()) * ThemeRandom.h01(a.prefixY, v);
                    int px = (int) Math.round(xy[0] + vx * age);
                    int py = (int) Math.round(xy[1] - vy * age + emit.gravity() * age * age / 2);
                    blit(draw, sp.frame(frameOf(clip, age), flip), px - ax, py - sp.anchorY);
                }
                continue;
            }
            int left = (int) xy[0] - ax;
            int top = (int) xy[1] - sp.anchorY;
            blit(draw, sp.frame(frameOf(clip, local), flip), left, top);
            BandScene.Sprite[] items = a.carry[i];
            if (items != null) {
                BandScene.Sprite item = items[(int) (loop % items.length)];
                int[] pt = sp.points.get("hand");
                int hx = left + (flip ? sp.width - 1 - (pt != null ? pt[0] : sp.anchorX) : (pt != null ? pt[0] : sp.anchorX));
                int hy = top + (pt != null ? pt[1] : sp.anchorY);
                blit(draw, item.frame(0, flip), hx - (flip ? item.width - 1 - item.anchorX : item.anchorX), hy - item.anchorY);
            }
        }
        if (!stripDrawn) {
            paintStrip(draw, time);
        }
    }

    private void blit(UiDraw draw, ThemeSprite s, int x, int y) {
        draw.blit(kit.texture(), x, y, s.width(), s.height(), s.u(), s.v(), s.width(), s.height());
    }
}
