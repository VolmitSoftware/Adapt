package art.arcane.adapt.client.theme;

import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.ThemeGenerator;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** A theme the server generated for a skill without art composes like an authored one, alone and mutated. */
class GeneratedThemeTest {
    @Test
    void aGeneratedKitComposesAloneAndBesideAnAuthoredKit() {
        for (Id icon : List.of(Id.minecraft("brewing_stand"), Id.minecraft("oak_sapling"))) {
            ThemeKit generated = ThemeKit.parse("alchemist", new String(ThemeGenerator.generate(
                    new SkillLook(Id.of("alchemist", "alchemy"), 0x7A3FD1, icon), null).kitJson(), StandardCharsets.UTF_8));
            for (PageTheme theme : List.of(new PageTheme(generated, null), new PageTheme(generated, ThemeFixtures.kit("rift")),
                    new PageTheme(ThemeFixtures.kit("herbalism"), generated))) {
                for (int leaf = 0; leaf < 3; leaf++) {
                    ThemedBook book = ThemedBook.of(DressingRulesTest.book(leaf, 640, 360), theme);
                    RecordingDraw draw = new RecordingDraw();
                    for (int frame = 0; frame < 120; frame++) {
                        book.paintBack(draw, frame * 50L);
                        book.paintFront(draw, frame * 50L);
                    }
                    book.divider(draw, 120, 320, 60);
                    new TileFrame().paint(draw, theme, new UiRect(300, 120, 106, 22), true, 500L, 0.5, false);
                    assertFalse(draw.blits.isEmpty(), "nothing drawn for leaf " + leaf);
                }
            }
        }
    }
}
