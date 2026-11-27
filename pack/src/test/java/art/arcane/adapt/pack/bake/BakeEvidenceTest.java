package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes what a client would show of one kit's pages over 12 seconds in each dressing mode, drawn by {@link PackReplay}
 * from a built 26.3 pack's files alone, as an animated GIF per mode, plus a map per mode tinting each moving region by the
 * host slot carrying it (held regions red) with the host slots outlined. Runs only with
 * {@code -Dadapt.pack.evidence=<folder>}.
 */
class BakeEvidenceTest {
    private static final int TICKS = 240;
    private static final int STEP = 2;
    private static final int ZOOM = 2;
    private static final String SKILL = "herbalism";
    private static final int[] TINTS = {0x80FF8C00, 0x8000B4FF, 0x8030D050, 0x80D040D0, 0x80FFE000};

    private record Mode(String name, PageSpec spec, List<String> layouts) {
    }

    @Test
    void writeAnimation() throws IOException {
        String evidence = System.getProperty("adapt.pack.evidence");
        Assumptions.assumeTrue(evidence != null, "evidence run only");
        DesignSpec design = DesignSpec.builtin();
        InventoryWells wells = InventoryWells.of(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tile.png")));
        KitBake.Result result = KitBake.bake(Builtin.kit(SKILL), design.page("A"), wells);
        PackReplay replay = PackReplayTest.replay(List.of(result));
        Path out = Path.of(evidence);
        Files.createDirectories(out);
        List<Mode> modes = List.of(new Mode("A", design.page("A"), List.of(KitBake.SINGLE)),
                new Mode("Afallback", design.page("A"), List.of(KitBake.FALLBACK)));
        for (Mode mode : modes) {
            PageSpec spec = mode.spec();
            List<PackManifest.PageFont> pages = mode.layouts().stream().map((String layout) -> replay.page("adapt:" + SKILL, layout)).toList();
            int width = spec.canvasWidth();
            int height = spec.canvasHeight();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out.resolve("v3-anim-" + SKILL + "-" + mode.name() + ".gif").toFile())) {
                writer.setOutput(stream);
                writer.prepareWriteSequence(null);
                for (int tick = 0; tick < TICKS; tick += STEP) {
                    BufferedImage frame = backed(replay.draw(pages, spec.canvasX(), spec.canvasY(), width, height, tick));
                    IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), null);
                    String format = meta.getNativeMetadataFormatName();
                    IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                    IIOMetadataNode control = child(root, "GraphicControlExtension");
                    control.setAttribute("delayTime", Integer.toString(STEP * 5));
                    control.setAttribute("disposalMethod", "none");
                    if (tick == 0) {
                        IIOMetadataNode apps = child(root, "ApplicationExtensions");
                        IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                        app.setAttribute("applicationID", "NETSCAPE");
                        app.setAttribute("authenticationCode", "2.0");
                        app.setUserObject(new byte[]{1, 0, 0});
                        apps.appendChild(app);
                    }
                    meta.setFromTree(format, root);
                    writer.writeToSequence(new IIOImage(frame, null, meta), null);
                }
                writer.endWriteSequence();
            }
            Files.write(out.resolve("v3-hosts-" + SKILL + "-" + mode.name() + ".png"), hostMap(result, mode, replay).png());
        }
    }

    /** The page at tick 0 with each region tinted by its host and each host slot outlined in the same tint. */
    private static Raster hostMap(KitBake.Result result, Mode mode, PackReplay replay) {
        PageSpec spec = mode.spec();
        List<PackManifest.PageFont> pages = mode.layouts().stream().map((String layout) -> replay.page("adapt:" + SKILL, layout)).toList();
        Raster map = replay.draw(pages, spec.canvasX(), spec.canvasY(), spec.canvasWidth(), spec.canvasHeight(), 0);
        for (KitBake.Page page : result.pages()) {
            if (!mode.layouts().contains(page.layout())) {
                continue;
            }
            List<Integer> slots = page.mode().slotList();
            for (MotionItems.Host host : page.hosts()) {
                int tint = TINTS[slots.indexOf(host.slot()) % TINTS.length];
                for (MotionItems.Area area : host.areas()) {
                    fill(map, area.x() - spec.canvasX(), area.y() - spec.canvasY(), area.width(), area.height(), tint);
                }
                int sx = SlotGrid.x(host.slot()) - spec.canvasX();
                int sy = SlotGrid.y(host.slot()) - spec.canvasY();
                for (int edge = -1; edge <= 16; edge++) {
                    for (int[] at : new int[][]{{sx + edge, sy - 1}, {sx + edge, sy + 16}, {sx - 1, sy + edge}, {sx + 16, sy + edge}}) {
                        map.set(at[0], at[1], tint | 0xFF000000);
                    }
                }
            }
            for (KitBake.Held hold : page.held()) {
                MotionItems.Area area = hold.area();
                fill(map, area.x() - spec.canvasX(), area.y() - spec.canvasY(), area.width(), area.height(), 0xA0FF2020);
            }
        }
        return map;
    }

    private static void fill(Raster raster, int x, int y, int width, int height, int argb) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                raster.blend(column, row, argb);
            }
        }
    }

    private static BufferedImage backed(Raster raster) {
        BufferedImage image = new BufferedImage(raster.width() * ZOOM, raster.height() * ZOOM, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = raster.get(x / ZOOM, y / ZOOM);
                int a = pixel >>> 24;
                int r = (((pixel >> 16) & 0xFF) * a + 0x2B * (255 - a)) / 255;
                int g = (((pixel >> 8) & 0xFF) * a + 0x2F * (255 - a)) / 255;
                int b = ((pixel & 0xFF) * a + 0x36 * (255 - a)) / 255;
                image.setRGB(x, y, r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int index = 0; index < root.getLength(); index++) {
            if (root.item(index).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(index);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
