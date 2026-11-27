package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiAssetCheck;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every skill's atlas and data agree: every sprite the renderer or a hover frame asks for exists and lies in the atlas. */
class ThemeAtlasTest {
    private static final Pattern METHOD = Pattern.compile("private static void ([a-z]+)[A-Za-z]*\\(FrameCanvas F");
    private static final Pattern SPRITE_KEY = Pattern.compile("F\\.(sprite|raw)\\(([^;]*)");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]+)\"");

    @Test
    void everySkillHasATheme() throws IOException {
        assertEquals(24, ThemeFixtures.SKILLS.size());
        for (String skill : ThemeFixtures.SKILLS) {
            ThemeKit kit = ThemeFixtures.kit(skill);
            assertEquals(skill, kit.skill());
            byte[] png = read("/assets/adapt/textures/gui/theme/" + skill + ".png");
            UiAssetCheck.Size size = UiAssetCheck.pngSize(png);
            assertNotNull(size, skill + " atlas is a PNG");
            assertEquals(kit.texture().width(), size.width(), skill);
            assertEquals(kit.texture().height(), size.height(), skill);
            assertEquals(java.util.List.of(), UiAssetCheck.problems(Map.of("theme/" + skill + ".png", png)));
        }
    }

    @Test
    void everyReferencedSpriteExistsInsideItsAtlas() throws IOException {
        for (String skill : ThemeFixtures.SKILLS) {
            ThemeKit kit = ThemeFixtures.kit(skill);
            JsonObject data = JsonParser.parseString(new String(read("/assets/adapt/themes/" + skill + ".json"), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (String name : data.getAsJsonObject("sprites").keySet()) {
                ThemeSprite sprite = kit.sprite(name);
                assertTrue(sprite.u() >= 0 && sprite.v() >= 0 && sprite.u() + sprite.width() <= kit.texture().width()
                        && sprite.v() + sprite.height() <= kit.texture().height(), skill + " " + name + " lies outside the atlas");
            }
            for (String name : new String[]{"mat", "matT", "div"}) {
                assertNotNull(kit.sprite(name), skill + " " + name);
            }
            if (kit.top() == null) {
                assertNotNull(kit.sprite("band"), skill + " band");
                assertNotNull(kit.sprite("hem"), skill + " hem");
            }
            for (int row = 0; row < 2 && kit.foot() == null && kit.footBand(); row++) {
                for (int mirrored = 0; mirrored < 2; mirrored++) {
                    for (String pose : new String[]{"a", "b"}) {
                        assertNotNull(kit.sprite("foot/" + row + "/" + mirrored + "/" + pose), skill + " foot tile");
                    }
                }
            }
            for (DressSlot slot : DressSlot.values()) {
                DressSprite[] sprites = kit.dress(slot);
                boolean banded = (slot == DressSlot.TOP || slot == DressSlot.CORNER) && kit.top() != null
                        || (slot == DressSlot.FOOT || slot == DressSlot.PILE) && kit.foot() != null;
                assertTrue(banded || slot == DressSlot.TRIM || sprites.length > 0, skill + " has " + slot.key());
                for (DressSprite sprite : sprites) {
                    for (int frame = 0; frame < sprite.frameCount(); frame++) {
                        assertEquals(sprite.width(), sprite.frame(frame, false).width(), skill + " " + slot.key());
                        assertEquals(sprite.height(), sprite.frame(frame, true).height(), skill + " " + slot.key());
                    }
                }
            }
            assertTrue(kit.dress(DressSlot.BIT).length >= 3, skill + " has a drop bit");
            assertTrue(kit.dress(DressSlot.HANG).length >= 3, skill + " has three hangs for the spine");
            assertTrue(kit.foot() != null || kit.dress(DressSlot.FOOT).length >= 2, skill + " has two foot pieces for the spine");
            if (kit.flyer() != null) {
                for (int frame = 0; frame < 2; frame++) {
                    assertNotNull(kit.castSprite(kit.flyer(), frame, false), skill + " flyer");
                    assertNotNull(kit.castSprite(kit.flyer(), frame, true), skill + " flyer mirrored");
                }
            }
            if (kit.hopper() != null) {
                assertNotNull(kit.castSprite(kit.hopper(), 0, false), skill + " hopper");
                assertNotNull(kit.castSprite(kit.hopper(), 1, false), skill + " hopper landing");
            }
        }
    }

    /** Every sprite key a skill's hover frame names (read from its source) is in that skill's atlas, in every turn. */
    @Test
    void everyHoverFrameSpriteExists() throws IOException {
        String source = Files.readString(Path.of("../client-common/src/main/java/art/arcane/adapt/client/theme/HoverFrames.java"),
                StandardCharsets.UTF_8);
        Map<String, String> bodies = new LinkedHashMap<>();
        Matcher method = METHOD.matcher(source);
        int start = -1;
        String name = null;
        while (method.find()) {
            if (name != null) {
                bodies.merge(name, source.substring(start, method.start()), String::concat);
            }
            name = method.group(1);
            start = method.start();
        }
        bodies.merge(name, source.substring(start), String::concat);
        Matcher glyphs = Pattern.compile("ENCHANTING_GLYPHS = \\{([^;]*)").matcher(source);
        assertTrue(glyphs.find());
        assertEquals(24, bodies.size());
        int checked = 0;
        for (Map.Entry<String, String> entry : bodies.entrySet()) {
            ThemeKit kit = ThemeFixtures.kit(entry.getKey().equals("pickaxes") ? "pickaxe" : entry.getKey());
            String body = entry.getKey().equals("enchanting") ? entry.getValue() + "F.sprite(" + glyphs.group(1) : entry.getValue();
            Matcher call = SPRITE_KEY.matcher(body);
            while (call.find()) {
                Matcher literal = LITERAL.matcher(call.group(2));
                while (literal.find()) {
                    String key = literal.group(1);
                    if (call.group(1).equals("raw")) {
                        assertNotNull(kit.frameRaw(key), kit.skill() + " raw " + key);
                    } else {
                        for (int side = 0; side < 4; side++) {
                            assertNotNull(kit.frameSprite(key, side, false), kit.skill() + " " + key + " side " + side);
                            assertNotNull(kit.frameSprite(key, side, true), kit.skill() + " " + key + " mirrored, side " + side);
                        }
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 80, "found the frames' sprite keys: " + checked);
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream stream = ThemeAtlasTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }
}
