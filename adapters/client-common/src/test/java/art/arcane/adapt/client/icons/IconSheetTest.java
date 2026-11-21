package art.arcane.adapt.client.icons;

import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.screen.PreviewSamples;
import art.arcane.adapt.client.ui.FakeResources;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.UiMotion;
import art.arcane.adapt.client.ui.kit.UiTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IconSheetTest {
    @Test
    void listsSkillsThenAdaptationsThenToastsWithTheirConventionPaths() {
        List<IconSheet.Entry> entries = IconSheet.entries(PreviewSamples.catalog());
        assertEquals("skill", entries.get(0).kind());
        assertEquals("adapt:textures/gui/skill/" + entries.get(0).id() + ".png", entries.get(0).icon().spriteId());
        List<String> toasts = entries.stream().filter((IconSheet.Entry entry) -> entry.kind().equals("toast"))
                .map(IconSheet.Entry::id).toList();
        assertEquals(List.of("level_up", "knowledge", "unlock", "discovery", "generic", "challenge"), toasts);
        assertEquals("toast", entries.get(entries.size() - 1).kind());
        long skills = entries.stream().filter((IconSheet.Entry entry) -> entry.kind().equals("skill")).count();
        assertEquals(PreviewSamples.catalog().skills().size(), skills);
    }

    @Test
    void sourceSaysSpriteItemOrInvalid() {
        List<IconSheet.Entry> entries = IconSheet.entries(PreviewSamples.catalog());
        IconSheet.Entry first = entries.get(0);
        IconSheet.Entry second = entries.get(1);
        FakeResources resources = new FakeResources()
                .put(first.icon().spriteId(), FakeResources.png(32, 32))
                .put(second.icon().spriteId(), FakeResources.png(20, 20));
        UiSpriteCache sprites = new UiSpriteCache(resources);
        assertEquals(new IconSheet.Source(IconSheet.Kind.SPRITE, "32x32"), IconSheet.source(sprites, first));
        assertEquals(IconSheet.Kind.INVALID, IconSheet.source(sprites, second).kind());
        assertEquals(new IconSheet.Source(IconSheet.Kind.ITEM, entries.get(2).icon().itemId()), IconSheet.source(sprites, entries.get(2)));
    }

    @Test
    void screenReportsCountsToTheProbe() {
        List<IconSheet.Entry> entries = IconSheet.entries(PreviewSamples.catalog());
        UiSpriteCache sprites = new UiSpriteCache(FakeResources.of(entries.get(0).icon().spriteId(), FakeResources.png(16, 16)));
        IconSheetScreen screen = new IconSheetScreen(entries, true, UiTheme.DEFAULT, new UiMotion(() -> 0L, true));
        KitDraw draw = new KitDraw(427, 4000, 2.0).withSprites(sprites);
        FakeHost host = new FakeHost(427, 4000);
        host.open(screen);
        screen.render(draw, -1, -1, 0.0F);
        String snapshot = UiProbe.snapshot();
        host.close();
        assertTrue(snapshot.contains("\"screen\":\"adapt-icons\""), snapshot);
        assertTrue(snapshot.contains("\"sprite\":\"1\""), snapshot);
        assertTrue(snapshot.contains("\"item\":\"" + (entries.size() - 1) + "\""), snapshot);
        assertTrue(snapshot.contains("\"sprites\":\"skill:" + entries.get(0).id() + "\""), snapshot);
    }
}
