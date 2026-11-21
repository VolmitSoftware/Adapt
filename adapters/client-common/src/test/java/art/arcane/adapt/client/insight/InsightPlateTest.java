package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightStatKind;
import art.arcane.adapt.client.view.InsightView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InsightPlateTest {
    private static final FakeHost HOST = new FakeHost(320, 240);

    @Test
    void aBarePlateIsTheNameLineAndBar() {
        InsightPlate plate = measure(view(List.of(), List.of(), InsightBond.NONE, 0D), new InsightSettings());

        assertEquals(InsightPlate.PAD * 2 + 9 + InsightPlate.ROW_GAP + InsightPlate.BAR_HEIGHT, plate.height());
        assertEquals("Zombie Villager".length() * 6 + 6 + "20/20".length() * 6 + InsightPlate.PAD * 2, plate.width());
        assertEquals("Zombie Villager", plate.name());
    }

    @Test
    void rowsAppearOnlyWithContentAndSettingsHideThem() {
        InsightView full = view(List.of(new InsightStat(InsightStatKind.SPEED, 0.234D), new InsightStat(InsightStatKind.TOUGHNESS, 0D)),
                List.of(new InsightEffect("minecraft:poison", 0, 100, true)), InsightBond.WILD, 4D);
        InsightPlate shown = measure(full, new InsightSettings());
        InsightSettings quiet = new InsightSettings();
        quiet.showStats(false);
        quiet.showEffects(false);
        quiet.showOwner(false);
        InsightPlate hidden = measure(full, quiet);

        assertEquals(List.of(new InsightPlate.Chip("minecraft:iron_chestplate", "4"),
                new InsightPlate.Chip("minecraft:sugar", "0.23")), shown.chips());
        assertEquals(hidden.height() + 3 * (InsightPlate.ROW_GAP + 1) + 9 + InsightPlate.EFFECT + 9, shown.height());
    }

    @Test
    void namesAndTexturesComeFromIds() {
        assertEquals("minecraft:textures/mob_effect/speed.png", InsightPlate.effectTexture("minecraft:speed"));
        assertNull(InsightPlate.effectTexture("speed"));
        assertEquals("12.5", InsightPlate.number(12.46D));
        assertEquals("20", InsightPlate.number(19.98D));
        assertEquals("adapt.hud.insight.tamed_by".replace("%s", "Steve"),
                InsightPlate.owner(new InsightView(1, "minecraft:wolf", "", InsightKind.PASSIVE, 1D, 1D, 0D, 0D,
                        InsightBond.OWNED, "Steve", false, List.of(), List.of()), HOST));
    }

    private static InsightPlate measure(InsightView view, InsightSettings settings) {
        return InsightPlate.measure(view, view.health(), 0D, settings, HOST, (String text) -> text.length() * 6, 9);
    }

    private static InsightView view(List<InsightStat> stats, List<InsightEffect> effects, InsightBond bond, double armor) {
        return new InsightView(1, "minecraft:zombie_villager", "", InsightKind.HOSTILE, 20D, 20D, 0D, armor, bond, "",
                false, stats, effects);
    }
}
