package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.raster.RasterDraw;
import art.arcane.adapt.pack.raster.RasterTextures;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One kit baked for the pack's page. The page is baked on the lead's side and on the partner's, so page one of a mutated
 * skill shows the lead's kit and page two the partner's with no bake per pair. The book's top is the kit's own (see
 * {@link BookTop}); every layout of the kit shares it.
 */
public final class KitBake {
    public static final String SINGLE = "single";
    public static final String SINGLE_PARTNER = "single-partner";
    /** The single page for servers that drive only the chest rows: the player's inventory rows sit in baked wells. */
    public static final String FALLBACK = "fallback";
    /** {@link #SINGLE} and {@link #FALLBACK} for screens that place no host items: every moving region shown still. */
    public static final String SINGLE_STILL = PackArt.still(SINGLE);
    public static final String SINGLE_PARTNER_STILL = PackArt.still(SINGLE_PARTNER);
    public static final String FALLBACK_STILL = PackArt.still(FALLBACK);
    /** Wide enough that a ribbon's ends and its plain middle all show; the pack stretches the middle. */
    public static final int RIBBON_WIDTH = 122;
    public static final int RIBBON_HEIGHT = 16;
    static final int FIRST_INVENTORY_SLOT = 54;

    /** The largest side of one moving piece: wide bands are cut so each piece fits one transform's reach. */
    static final int PIECE = 64;
    /** Why a moving region is shown still: no host of its mode reaches it, or the page is for screens without host items. */
    public static final String REACH = "reach";
    public static final String SCREEN = "screen";

    private KitBake() {
    }

    /** A moving region shown still in its first frame, and why. */
    public record Held(MotionItems.Area area, String reason) {
    }

    /**
     * One baked page part: its layout name, its dressing mode's hosts, where its still layer's top left sits in container
     * pixels, the still layer and its glyphs, the moving regions, the host items carrying them and the regions held still
     * instead.
     */
    public record Page(String layout, DressingHosts mode, int left, int top, Raster still, List<StillTiles.Tile> tiles,
                       List<LayerBake.Motion> motions, int originX, int originY, List<MotionItems.Host> hosts, List<Held> held) {
        public Page {
            tiles = List.copyOf(tiles);
            motions = List.copyOf(motions);
            hosts = List.copyOf(hosts);
            held = List.copyOf(held);
        }

        /** Texture pixels the moving regions on hosts need. */
        public long motionArea() {
            long area = 0;
            for (MotionItems.Host host : hosts) {
                for (MotionItems.Area region : host.areas()) {
                    LayerBake.Motion motion = motions.get(region.index());
                    area += (long) motion.width() * motion.height() * motion.frames().size();
                }
            }
            return area;
        }

        /**
         * This page as {@code name} with no hosts and every region held still in its pose at tick 0, so a screen that puts
         * no host items shows each piece of dressing whole instead of the still layer's holes under it. A pose pixel that is
         * not opaque (a fading particle or glint) is left out: a piece is drawn whole or not at all.
         */
        public Page allStill(String name) {
            Raster base = stillBase();
            List<Held> still = new ArrayList<>();
            for (int index = 0; index < motions.size(); index++) {
                LayerBake.Motion motion = motions.get(index);
                Held kept = null;
                for (Held hold : held) {
                    if (hold.area().index() == index) {
                        kept = hold;
                    }
                }
                still.add(kept != null ? kept : new Held(new MotionItems.Area(index, left + motion.x(), top + motion.y(), motion.width(), motion.height()),
                        SCREEN));
                Raster pose = motion.frames().get(PagePreview.frameAt(motion, 0));
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        if (motion.mask()[y * motion.width() + x] && pose.get(x, y) >>> 24 == 0xFF) {
                            base.set(motion.x() + x, motion.y() + y, pose.get(x, y));
                        }
                    }
                }
            }
            return new Page(name, mode, left, top, base, StillTiles.slice(base, left, top), motions, originX, originY, List.of(), still);
        }

        /** The still layer without the held regions' first frames. */
        private Raster stillBase() {
            Raster base = still.copy();
            for (Held hold : held) {
                LayerBake.Motion motion = motions.get(hold.area().index());
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        if (motion.mask()[y * motion.width() + x]) {
                            base.set(hold.area().x() - left + x, hold.area().y() - top + y, 0);
                        }
                    }
                }
            }
            return base;
        }
    }

    /** A baked kit: its pages, and its crumb ribbon drawn {@link #RIBBON_WIDTH} wide. */
    public record Result(ThemeAssets kit, List<Page> pages, Raster ribbon) {
        public Result {
            pages = List.copyOf(pages);
        }
    }

    /**
     * The kit's single page and partner page with their {@link #SINGLE_STILL} and {@link #SINGLE_PARTNER_STILL} copies;
     * with {@code wells}, also the {@link #FALLBACK} page and its {@link #FALLBACK_STILL} copy.
     */
    public static Result bake(ThemeAssets kit, PageSpec framing, InventoryWells wells) {
        PageSpec single = BookTop.fit(kit, framing);
        List<Page> pages = new ArrayList<>();
        BakedLayer lead = closed(kit, single, 1);
        Page leadPage = page(SINGLE, single, single.hosts(), lead);
        pages.add(leadPage);
        pages.add(leadPage.allStill(SINGLE_STILL));
        if (wells != null && single.fallbackHosts() != null) {
            Raster still = wells.over(lead.still(), single.canvasX(), single.canvasY());
            Page fallback = page(FALLBACK, single, single.fallbackHosts(), new BakedLayer(still, lead.motions()));
            pages.add(fallback);
            pages.add(fallback.allStill(FALLBACK_STILL));
        }
        Page partner = page(SINGLE_PARTNER, single, single.hosts(), closed(kit, single, 2));
        pages.add(partner);
        pages.add(partner.allStill(SINGLE_PARTNER_STILL));
        return new Result(kit, pages, new KitRender(kit, single, 1).ribbon(RIBBON_WIDTH));
    }

    /** Leaf {@code leaf} of {@code kit}'s page, still along the header row (see {@link HeaderHold}) and its window closed (see {@link WindowBacking}). */
    private static BakedLayer closed(ThemeAssets kit, PageSpec spec, int leaf) {
        BakedLayer layer = HeaderHold.hold(LayerBake.bake(kit, spec, leaf), spec.canvasX(), spec.canvasY());
        return WindowBacking.of(new KitRender(kit, spec, leaf).back(), spec).close(layer);
    }

    /** A kit piece nine-sliced to {@code width} by {@code height} with {@code border}, as the client's composer draws it. */
    public static Raster nine(byte[] png, int width, int height, int border) {
        Raster piece = Raster.decode(png);
        UiTexture texture = new UiTexture("adapt:textures/gui/kit/piece.png", piece.width(), piece.height());
        RasterTextures textures = RasterTextures.of(Map.of(texture.id(), png));
        Raster out = new Raster(width, height);
        new RasterDraw(out, UiSprites.NONE, textures).nineSlice(texture, 0, 0, width, height, 0, 0, piece.width(), piece.height(), border);
        return out;
    }

    /** A page whose regions go to {@code mode}'s hosts by the design's rule. */
    private static Page page(String layout, PageSpec spec, DressingHosts mode, BakedLayer layer) {
        int left = spec.canvasX();
        int top = spec.canvasY();
        List<LayerBake.Motion> motions = new ArrayList<>();
        for (LayerBake.Motion motion : layer.motions()) {
            motions.addAll(cut(motion, spec));
        }
        Map<Integer, List<MotionItems.Area>> held = new TreeMap<>();
        List<Held> still = new ArrayList<>();
        for (int index = 0; index < motions.size(); index++) {
            LayerBake.Motion motion = motions.get(index);
            MotionItems.Area area = new MotionItems.Area(index, spec.canvasX() + motion.x(), top + motion.y(), motion.width(), motion.height());
            Integer slot = mode.host(area, DressingHosts.Reach.DRAWABLE, (int any) -> true);
            if (slot == null) {
                still.add(new Held(area, REACH));
            } else {
                held.computeIfAbsent(slot, (Integer key) -> new ArrayList<>()).add(area);
            }
        }
        List<MotionItems.Host> hosts = new ArrayList<>();
        for (Map.Entry<Integer, List<MotionItems.Area>> entry : held.entrySet()) {
            hosts.add(new MotionItems.Host(entry.getKey(), entry.getValue()));
        }
        return assemble(layout, mode, left, top, layer.still(), motions, spec.bookX(), spec.bookY(), hosts, still);
    }

    private static Page assemble(String layout, DressingHosts mode, int left, int top, Raster base, List<LayerBake.Motion> motions, int originX,
                                 int originY, List<MotionItems.Host> hosts, List<Held> held) {
        Raster still = base.copy();
        for (Held hold : held) {
            LayerBake.Motion motion = motions.get(hold.area().index());
            still.paste(motion.frames().get(PagePreview.frameAt(motion, 0)), hold.area().x() - left, hold.area().y() - top);
        }
        return new Page(layout, mode, left, top, still, StillTiles.slice(still, left, top), motions, originX, originY, hosts, held);
    }

    /**
     * A region cut where the host rule's bands meet (the top and foot lines, the middle column) and then into pieces no
     * larger than {@link #PIECE}, so each piece lies in one band and fits one transform.
     */
    static List<LayerBake.Motion> cut(LayerBake.Motion motion, PageSpec spec) {
        int middle = DressingHosts.MIDDLE - spec.canvasX();
        int[] columns = {Integer.MIN_VALUE, middle, Integer.MAX_VALUE};
        int[] rows = {Integer.MIN_VALUE, DressingHosts.TOP_LINE - spec.canvasY(), DressingHosts.FOOT_LINE - spec.canvasY(), Integer.MAX_VALUE};
        List<LayerBake.Motion> pieces = new ArrayList<>();
        for (int row = 0; row + 1 < rows.length; row++) {
            for (int column = 0; column + 1 < columns.length; column++) {
                LayerBake.Motion part = motion.part(columns[column], rows[row], columns[column + 1], rows[row + 1]);
                if (part != null) {
                    pieces.addAll(part.pieces(PIECE));
                }
            }
        }
        return pieces;
    }
}
