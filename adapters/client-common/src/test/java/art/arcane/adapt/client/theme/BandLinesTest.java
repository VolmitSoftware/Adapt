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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every band on its shared line, measured from the strip's own pixels, on every plain page and on both halves of every
 * ordered pair, so a mutated spread never steps at the spine.
 */
class BandLinesTest {
    private static final Map<String, BufferedImage> ATLASES = new HashMap<>();

    private static BufferedImage atlas(String skill) throws IOException {
        BufferedImage cached = ATLASES.get(skill);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = BandLinesTest.class.getResourceAsStream("/assets/adapt/textures/gui/theme/" + skill + ".png")) {
            assertNotNull(stream, skill + " atlas");
            BufferedImage image = ImageIO.read(stream);
            ATLASES.put(skill, image);
            return image;
        }
    }

    private static boolean full(BufferedImage image, BandScene scene, int row) {
        int filled = 0;
        int total = 0;
        for (int index = 0; index < scene.tileCount(); index++) {
            ThemeSprite tile = scene.tile(index, 0, false);
            for (int x = 0; x < tile.width(); x++) {
                total++;
                if ((image.getRGB(tile.u() + x, tile.v() + row) >>> 24) != 0) {
                    filled++;
                }
            }
        }
        return filled * 10 >= total * 9;
    }

    /**
     * Where a stage's base meets its line, rows at least 90% filled counting as base: the top of a foot's last run of
     * full rows (ropes or crests above it are its rise), the bottom of a top's last full row (scallops below it are its
     * hang).
     */
    private static int line(BandStage stage) throws IOException {
        BandScene scene = stage.scene();
        BufferedImage image = atlas(stage.kit().skill());
        int row = scene.groundHeight() - 1;
        while (row >= 0 && !full(image, scene, row)) {
            row--;
        }
        if (stage.top()) {
            return stage.groundY() + row + 1;
        }
        while (row > 0 && full(image, scene, row - 1)) {
            row--;
        }
        return stage.groundY() + row;
    }

    @Test
    void everyPairMeetsOnTheSharedLines() throws IOException {
        BookGeometry geometry = DressingRulesTest.book(0, 640, 360);
        int footLine = BandStage.footLine(geometry);
        int topLine = BandStage.topLine(geometry);
        List<String> off = new ArrayList<>();
        int pairs = 0;
        for (String lead : ThemeFixtures.SKILLS) {
            List<String> partners = new ArrayList<>();
            partners.add(null);
            for (String other : ThemeFixtures.SKILLS) {
                if (!other.equals(lead)) {
                    partners.add(other);
                }
            }
            for (String partner : partners) {
                pairs += partner == null ? 0 : 1;
                List<Integer> feet = new ArrayList<>();
                List<Integer> tops = new ArrayList<>();
                for (BandStage stage : BandStage.of(geometry, ThemeFixtures.theme(lead, partner))) {
                    (stage.top() ? tops : feet).add(line(stage));
                }
                String name = lead + (partner == null ? "" : " x " + partner);
                if (feet.stream().anyMatch((Integer y) -> y != footLine)) {
                    off.add(name + " foot " + feet);
                }
                if (tops.stream().anyMatch((Integer y) -> y != topLine)) {
                    off.add(name + " top " + tops);
                }
            }
        }
        assertEquals(ThemeFixtures.SKILLS.size() * (ThemeFixtures.SKILLS.size() - 1), pairs);
        assertTrue(off.isEmpty(), "off the shared lines (foot " + footLine + ", top " + topLine + "): " + off);
    }
}
