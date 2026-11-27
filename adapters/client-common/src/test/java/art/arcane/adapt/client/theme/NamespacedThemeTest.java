package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.FakeResources;
import art.arcane.adapt.client.ui.UiAssetCheck;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Theme files and sprites are looked up in the namespace of the skill they belong to. */
class NamespacedThemeTest {
    @Test
    void kitAndAtlasIdsTakeTheSkillsNamespace() {
        assertEquals("adapt:themes/herbalism.json", ThemeKit.dataId(Id.ADAPT, "herbalism"));
        assertEquals("adapt:textures/gui/theme/herbalism.png", ThemeKit.atlasId(Id.ADAPT, "herbalism"));
        assertEquals("otherns:themes/alchemy.json", ThemeKit.dataId("otherns", "alchemy"));
        assertEquals("otherns:textures/gui/theme/alchemy.png", ThemeKit.atlasId("otherns", "alchemy"));
        ThemeKit foreign = ForeignKits.kit("alchemy", null);
        assertEquals("otherns:textures/gui/theme/alchemy.png", foreign.texture().id());
        assertEquals("adapt:textures/gui/theme/herbalism.png", ThemeFixtures.kit("herbalism").texture().id());
    }

    @Test
    void aForeignSkillsThemeComesFromItsOwnNamespaceOnly() {
        FakeResources resources = new FakeResources()
                .put("otherns:themes/alchemy.json", FakeResources.text(ForeignKits.json("alchemy", ForeignKits.FRAME)))
                .put("otherns:textures/gui/theme/alchemy.png", FakeResources.png(128, 128))
                .put("otherns:themes/tinkering.json", FakeResources.text(ForeignKits.json("tinkering", null)))
                .put("otherns:textures/gui/theme/tinkering.png", FakeResources.png(128, 128))
                .put("adapt:themes/herbalism.json", FakeResources.text(ForeignKits.json("herbalism", null)))
                .put("adapt:textures/gui/theme/herbalism.png", FakeResources.png(128, 128))
                .put("adapt:themes/rift.json", FakeResources.text(ForeignKits.json("rift", null)))
                .put("adapt:textures/gui/theme/rift.png", FakeResources.png(128, 128));
        UiSpriteCache sprites = new UiSpriteCache(resources);
        ThemeLibrary library = new ThemeLibrary();
        PageTheme alchemy = library.theme(sprites, "otherns:alchemy");
        assertEquals(Id.of("otherns", "alchemy"), alchemy.lead().id());
        assertFalse(alchemy.mutated());
        assertEquals(Id.adapt("herbalism"), library.theme(sprites, "herbalism").lead().id());
        assertNull(library.theme(sprites, "otherns:herbalism"), "an adapt theme is not another namespace's");
        assertNull(library.theme(sprites, "alchemy"));
        PageTheme mutated = library.theme(sprites, "herbalism+rift");
        assertEquals(Id.adapt("herbalism"), mutated.lead().id());
        assertEquals(Id.adapt("rift"), mutated.partner().id());
        assertSame(mutated, library.theme(sprites, "herbalism+rift"));
        assertNull(library.theme(sprites, "otherns:herbalism+otherns:rift"), "each parent's theme is read in its own namespace");
        PageTheme mixed = library.theme(sprites, "otherns:alchemy+adapt:herbalism");
        assertEquals(Id.of("otherns", "alchemy"), mixed.lead().id());
        assertEquals(Id.adapt("herbalism"), mixed.partner().id());
        assertEquals(Id.of("otherns", "tinkering"), library.kit(sprites, Id.of("otherns", "tinkering")).id());
        assertSame(alchemy.lead(), library.kit(sprites, Id.of("otherns", "alchemy")));
    }

    @Test
    void foreignGuiArtIsCheckedByTheSameRules() {
        assertEquals("skill/alchemy.png", UiAssetCheck.relative("otherns:textures/gui/skill/alchemy.png"));
        assertEquals("skill/agility.png", UiAssetCheck.relative("adapt:textures/gui/skill/agility.png"));
        assertNull(UiAssetCheck.relative("otherns:textures/item/alchemy.png"));
        UiSpriteCache sprites = new UiSpriteCache(new FakeResources()
                .put("otherns:textures/gui/skill/alchemy.png", FakeResources.png(16, 16))
                .put("otherns:textures/gui/skill/wide.png", FakeResources.png(32, 16)));
        assertTrue(sprites.lookup("otherns:textures/gui/skill/alchemy.png").usable());
        assertFalse(sprites.lookup("otherns:textures/gui/skill/wide.png").usable(), "a foreign icon must be square too");
    }
}
