package art.arcane.adapt.client.theme;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No band ends in a bare cut: a foot strip's outer ends reach the cover's edges, every strip's outermost cap column is
 * outline or empty, and a flush corner piece stands with its outer edge on its strip's end, on plain pages, single pages
 * and both halves of every ordered pair.
 */
class BandEndsTest {
    private static final Map<String, BufferedImage> ATLASES = new HashMap<>();

    private static BufferedImage atlas(String skill) throws IOException {
        BufferedImage cached = ATLASES.get(skill);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = BandEndsTest.class.getResourceAsStream("/assets/adapt/textures/gui/theme/" + skill + ".png")) {
            assertNotNull(stream, skill + " atlas");
            BufferedImage image = ImageIO.read(stream);
            ATLASES.put(skill, image);
            return image;
        }
    }

    /** The rows of a cap's column {@code x} that are drawn in anything but the kit's outline. */
    private static List<Integer> open(BufferedImage image, ThemeSprite cap, int x, int outline) {
        List<Integer> rows = new ArrayList<>();
        for (int y = 0; y < cap.height(); y++) {
            int argb = image.getRGB(cap.u() + x, cap.v() + y);
            if ((argb >>> 24) != 0 && (argb | 0xFF000000) != (outline | 0xFF000000)) {
                rows.add(y);
            }
        }
        return rows;
    }

    @Test
    void stripEndsAreMadeEdges() throws IOException {
        List<String> bare = new ArrayList<>();
        for (String skill : ThemeFixtures.SKILLS) {
            ThemeKit kit = ThemeFixtures.theme(skill, null).lead();
            BufferedImage image = atlas(kit.skill());
            for (BandScene scene : new BandScene[]{kit.top(), kit.foot()}) {
                if (scene == null) {
                    continue;
                }
                String name = skill + (scene == kit.top() ? " top" : " foot");
                List<Integer> start = open(image, scene.start(false), 0, kit.color('k'));
                List<Integer> end = open(image, scene.end(false), scene.end(false).width() - 1, kit.color('k'));
                if (!start.isEmpty()) {
                    bare.add(name + " start rows " + start);
                }
                if (!end.isEmpty()) {
                    bare.add(name + " end rows " + end);
                }
            }
        }
        assertTrue(bare.isEmpty(), "strip ends cut bare: " + bare);
    }

    @Test
    void feetReachTheCornersAndFlushPiecesSitOnTheirEnds() {
        List<String> off = new ArrayList<>();
        for (int leaf = 0; leaf <= 2; leaf++) {
            BookGeometry geometry = DressingRulesTest.book(leaf, 640, 360);
            BookGeometry.Box cover = geometry.cover();
            for (String lead : ThemeFixtures.SKILLS) {
                List<String> partners = new ArrayList<>();
                if (leaf < 2) {
                    partners.add(null);
                }
                for (String other : ThemeFixtures.SKILLS) {
                    if (!other.equals(lead)) {
                        partners.add(other);
                    }
                }
                for (String partner : partners) {
                    PageTheme theme = ThemeFixtures.theme(lead, partner);
                    String name = lead + (partner == null ? "" : " x " + partner) + " leaf " + leaf;
                    for (BandStage stage : BandStage.of(geometry, theme)) {
                        boolean mutated = theme.mutated();
                        boolean spineLeft = mutated && (!geometry.single() ? stage.mirror() : geometry.leaf() != 1);
                        boolean spineRight = mutated && (!geometry.single() ? !stage.mirror() : geometry.leaf() == 1);
                        if (!stage.top() && !spineLeft && stage.groundFrom() != cover.x0()) {
                            off.add(name + " foot " + stage.kit().skill() + " stops short of the left corner");
                        }
                        if (!stage.top() && !spineRight && stage.groundTo() != cover.x1()) {
                            off.add(name + " foot " + stage.kit().skill() + " stops short of the right corner");
                        }
                        for (BandStage.Actor actor : stage.actors()) {
                            BandScene.Element element = stage.scene().cast().stream()
                                    .filter((BandScene.Element e) -> e.id().equals(actor.id)).findFirst().orElseThrow();
                            if (!element.flush()) {
                                continue;
                            }
                            BandScene.Sprite sprite = actor.sprite;
                            int anchor = actor.flip[0] ? sprite.width - 1 - sprite.anchorX : sprite.anchorX;
                            int left = (int) Math.round(actor.x0[0]) - anchor;
                            boolean right = "end".equals(element.corner()) != stage.mirror();
                            if (right ? left + sprite.width != stage.groundTo() : left != stage.groundFrom()) {
                                off.add(name + " " + (stage.top() ? "top " : "foot ") + stage.kit().skill() + " " + actor.id + " is not flush");
                            }
                        }
                    }
                }
            }
        }
        assertTrue(off.isEmpty(), "band ends: " + off);
    }
}
