package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSpriteCacheTest {
    private static final String AGILITY = "adapt:textures/gui/skill/agility.png";
    private static final String PANEL = "adapt:textures/gui/kit/panel.png";
    private static final String PANEL_SLICE = "adapt:textures/gui/kit/panel.json";

    @Test
    void foundTextureCarriesItsPixelSize() {
        UiSpriteCache cache = new UiSpriteCache(FakeResources.of(AGILITY, FakeResources.png(64, 64)));
        assertEquals(new UiTexture(AGILITY, 64, 64), cache.texture(AGILITY));
        assertTrue(cache.exists(AGILITY));
    }

    @Test
    void missingAndFoundLookupsAreReadOnce() {
        FakeResources resources = FakeResources.of(AGILITY, FakeResources.png(16, 16));
        UiSpriteCache cache = new UiSpriteCache(resources);
        for (int frame = 0; frame < 5; frame++) {
            cache.texture(AGILITY);
            cache.texture("adapt:textures/gui/skill/axes.png");
        }
        assertEquals(2, resources.opens());
    }

    @Test
    void filesBreakingTheIconRulesAreNotDrawn() {
        FakeResources resources = new FakeResources()
                .put(AGILITY, FakeResources.png(16, 32))
                .put("adapt:textures/gui/skill/axes.png", FakeResources.text("not an image at all, just text"))
                .put("adapt:textures/gui/adaptation/big.png", FakeResources.png(1024, 1024))
                .put("adapt:textures/gui/adaptation/odd.png", FakeResources.png(24, 24));
        UiSpriteCache cache = new UiSpriteCache(resources);
        assertEquals("icon must be square, is 16x32", cache.lookup(AGILITY).problem());
        assertEquals("not a PNG", cache.lookup("adapt:textures/gui/skill/axes.png").problem());
        assertNull(cache.texture("adapt:textures/gui/adaptation/big.png"));
        assertNull(cache.texture("adapt:textures/gui/adaptation/odd.png"));
        assertEquals(UiSpriteLookup.MISSING, cache.lookup("adapt:textures/gui/skill/missing.png"));
    }

    @Test
    void reloadForgetsLookupsSoAddedAndRemovedFilesShow() {
        FakeResources resources = new FakeResources();
        UiSpriteCache cache = new UiSpriteCache(resources);
        cache.refresh();
        assertFalse(cache.exists(AGILITY));
        resources.put(AGILITY, FakeResources.png(32, 32));
        cache.refresh();
        assertFalse(cache.exists(AGILITY), "without a reload the cached miss stands");
        resources.reload();
        cache.refresh();
        assertTrue(cache.exists(AGILITY));
        resources.remove(AGILITY).reload();
        cache.refresh();
        assertFalse(cache.exists(AGILITY));
    }

    @Test
    void nineSliceNeedsBothFiles() {
        FakeResources resources = FakeResources.of(PANEL, FakeResources.png(32, 32));
        UiSpriteCache cache = new UiSpriteCache(resources);
        assertNull(cache.nineSlice(PANEL));
        resources.put(PANEL_SLICE, FakeResources.text("{\"border\": 8, \"scale\": 2}")).reload();
        cache.refresh();
        UiNineSlice slice = cache.nineSlice(PANEL);
        assertNotNull(slice);
        assertEquals(8, slice.border());
        assertEquals(2, slice.scale());
        resources.put(PANEL_SLICE, FakeResources.text("{\"border\": 16}")).reload();
        cache.refresh();
        assertNull(cache.nineSlice(PANEL), "a border with no centre left is rejected");
    }
}
