package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.hud.HudPaint;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.ui.FakeResources;
import art.arcane.adapt.client.ui.UiNineSlice;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.screen.PreviewSamples;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiIconsTest {
    private static final String AGILITY = "adapt:textures/gui/skill/agility.png";
    private static final String OVERRIDE = "adapt:textures/gui/custom/agility_alt.png";

    private final FakeResources resources = new FakeResources();
    private final UiSpriteCache sprites = new UiSpriteCache(resources);
    private final KitDraw draw = new KitDraw().withSprites(sprites);

    private static SkillView agility(String spriteId) {
        SkillView sample = PreviewSamples.catalog().skills().stream().filter((SkillView skill) -> skill.id().equals("agility")).findFirst()
                .orElseThrow();
        IconRef icon = new IconRef(sample.icon().itemId(), 0, null, spriteId);
        return new SkillView(sample.id(), sample.name(), sample.description(), icon, sample.color(),
                sample.enabled(), sample.rowGroups(), sample.preferences(), sample.adaptations());
    }

    private List<String> paint(IconRef icon) {
        draw.calls().clear();
        UiIcons.paint(draw, icon, 0, 0, UiIcons.SIZE, false);
        return List.copyOf(draw.calls());
    }

    @Test
    void skillWithoutArtFallsBackToItsItem() {
        assertEquals(List.of("item minecraft:feather cmd=0 glint=false 0,0"), paint(UiIcons.skill(agility(null))));
    }

    @Test
    void conventionSpriteIsDrawnWhenAPackProvidesIt() {
        resources.put(AGILITY, FakeResources.png(32, 32));
        assertEquals(List.of("blit " + AGILITY + " 0,0 16x16"), paint(UiIcons.skill(agility(null))));
    }

    @Test
    void explicitServerSpriteWinsOverTheConvention() {
        resources.put(AGILITY, FakeResources.png(16, 16)).put(OVERRIDE, FakeResources.png(16, 16));
        assertEquals(List.of("blit " + OVERRIDE + " 0,0 16x16"), paint(UiIcons.skill(agility(OVERRIDE))));
    }

    @Test
    void explicitSpriteTheClientLacksFallsBackToTheItem() {
        resources.put(AGILITY, FakeResources.png(16, 16));
        assertEquals(List.of("item minecraft:feather cmd=0 glint=false 0,0"), paint(UiIcons.skill(agility(OVERRIDE))));
    }

    @Test
    void reloadSwitchesBetweenSpriteAndItem() {
        IconRef icon = UiIcons.skill(agility(null));
        assertEquals("item", paint(icon).get(0).split(" ")[0]);
        resources.put(AGILITY, FakeResources.png(16, 16)).reload();
        sprites.refresh();
        assertEquals("blit", paint(icon).get(0).split(" ")[0]);
        resources.remove(AGILITY).reload();
        sprites.refresh();
        assertEquals("item", paint(icon).get(0).split(" ")[0]);
    }

    @Test
    void conventionsCoverAdaptationsToastsAndStatuses() {
        AdaptationView adaptation = PreviewSamples.catalog().skills().get(0).adaptations().get(0);
        assertEquals("adapt:textures/gui/adaptation/" + adaptation.id() + ".png", UiIcons.adaptation(adaptation).spriteId());
        assertEquals("adapt:textures/gui/toast/level_up.png", UiIcons.toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:paper")).spriteId());
        assertEquals("adapt:textures/gui/hud/rift_gate.png", UiIcons.status("adapt:status:rift_gate", IconRef.item("minecraft:ender_pearl")).spriteId());
        IconRef skillToast = new IconRef("minecraft:feather", 0, null, AGILITY);
        assertEquals(AGILITY, UiIcons.toast(ToastKind.LEVEL_UP, skillToast).spriteId());
    }

    @Test
    void hudIconsUseTheSameResolver() {
        resources.put(AGILITY, FakeResources.png(64, 64));
        draw.calls().clear();
        HudPaint.icon(draw, UiIcons.skill(agility(null)), 3, 4, 12, 1.0);
        assertEquals(List.of("blit " + AGILITY + " 3,4 12x12"), draw.calls());
    }

    @Test
    void tileSpriteNeedsAPackFileElseShowsTheItem() {
        IconTile tile = new IconTile("tile", IconRef.item("minecraft:lime_dye"), "On").setSprite(new UiTexture(AGILITY, 16, 16));
        tile.place(new UiRect(0, 0, 20, 20), draw);
        draw.calls().clear();
        tile.render(new UiFrame(draw, UiTheme.DEFAULT, new UiMotion(() -> 0L, true), -1, -1, null, null, null, false));
        assertEquals(1, draw.calls("item minecraft:lime_dye").size());
        resources.put(AGILITY, FakeResources.png(16, 16));
        sprites.refresh();
        resources.reload();
        sprites.refresh();
        draw.calls().clear();
        tile.render(new UiFrame(draw, UiTheme.DEFAULT, new UiMotion(() -> 0L, true), -1, -1, null, null, null, false));
        assertEquals(List.of("blit " + AGILITY + " 2,2 16x16"), draw.calls("blit"));
    }

    @Test
    void chromeUsesNineSliceWhenAPackProvidesItAndFlatOtherwise() {
        UiRect box = new UiRect(10, 10, 40, 20);
        draw.calls().clear();
        assertEquals(false, UiChrome.paint(draw, UiChrome.BUTTON, "hover", box));
        resources.put(UiChrome.textureId("button"), FakeResources.png(12, 12))
                .put("adapt:textures/gui/kit/button.json", FakeResources.text("{\"border\": 3}")).reload();
        sprites.refresh();
        assertEquals(true, UiChrome.paint(draw, UiChrome.BUTTON, "hover", box));
        assertEquals(9, draw.calls("blit adapt:textures/gui/kit/button.png").size());
        assertEquals(new UiNineSlice(new UiTexture(UiChrome.textureId("button"), 12, 12), 3, 1, false), sprites.nineSlice(UiChrome.textureId("button")));
    }
}
