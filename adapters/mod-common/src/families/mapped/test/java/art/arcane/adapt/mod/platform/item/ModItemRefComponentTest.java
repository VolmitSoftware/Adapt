package art.arcane.adapt.mod.platform.item;

import art.arcane.adapt.mod.platform.text.ComponentJson;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round trips through the 1.21.1 item components, each checked again after an NBT save and reload of the stack.
 * Enchantment levels are not covered: on this version they live in the server's dynamic registry, which a unit test
 * has no server for.
 */
class ModItemRefComponentTest {
    private static final String DURATION_LINE = "{\"translate\":\"potion.withDuration\",\"with\":[{\"translate\":\"effect.minecraft.speed\"},"
            + "{\"text\":\"03:00\"}],\"color\":\"blue\",\"italic\":false}";
    private static final String MULTI_ITEM_LINE = "{\"text\":\"\",\"extra\":[{\"text\":\"Iron Pickaxe\",\"color\":\"gray\",\"italic\":true}]}";
    private static final Id ATTACK_DAMAGE = Id.minecraft("attack_damage");
    private static final DataKey<Integer> LEVEL = DataKey.adapt("level", DataCodecs.INT);
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static ModItemRef item(net.minecraft.world.item.Item item) {
        return new ModItemRef(new ItemStack(item));
    }

    private static ModItemRef reload(ModItemRef ref) {
        return new ModItemRef(ItemStack.parse(registries, ref.stack().save(registries)).orElseThrow());
    }

    private static void assertPlain(ModItemRef ref) {
        assertTrue(ref.stack().getComponentsPatch().isEmpty(), ref.stack().getComponentsPatch().toString());
        assertTrue(ref.isSimilar(new ModItemRef(new ItemStack(ref.stack().getItem()))));
    }

    @Test
    void nameAndLoreSurviveReload() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        ref.setDisplayName("Blade");
        ref.setLore(List.of("first", "second"));
        ModItemRef loaded = reload(ref);
        assertTrue(loaded.hasDisplayName());
        assertTrue(loaded.displayName().contains("Blade"));
        assertEquals(2, loaded.lore().size());
        assertTrue(loaded.lore().get(1).contains("second"));
        loaded.setDisplayName(null);
        loaded.setLore(List.of());
        assertFalse(loaded.hasDisplayName());
        assertTrue(loaded.lore().isEmpty());
        assertPlain(loaded);
    }

    @Test
    void glintOverride() {
        ModItemRef ref = item(Items.STICK);
        ref.setGlintOverride(Boolean.TRUE);
        assertTrue(reload(ref).hasGlint());
        ref.setGlintOverride(null);
        assertFalse(ref.hasGlint());
        assertPlain(ref);
    }

    @Test
    void tooltipFlags() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        ref.hideTooltip(TooltipPart.ATTRIBUTES);
        ref.hideTooltip(TooltipPart.POTION_EFFECTS);
        ModItemRef loaded = reload(ref);
        assertFalse(loaded.stack().get(DataComponents.ATTRIBUTE_MODIFIERS).showInTooltip());
        assertTrue(loaded.stack().has(DataComponents.HIDE_ADDITIONAL_TOOLTIP));
        ref.setHideTooltip(true);
        assertTrue(reload(ref).stack().has(DataComponents.HIDE_TOOLTIP));
        ref.setHideTooltip(false);
        assertFalse(ref.stack().has(DataComponents.HIDE_TOOLTIP));
    }

    @Test
    void unbreakableDamageAndPerStackLimits() {
        ModItemRef ref = item(Items.IRON_PICKAXE);
        ref.setDamage(17);
        ref.setMaxDamage(500);
        ref.setMaxStackSize(1);
        ModItemRef loaded = reload(ref);
        assertEquals(17, loaded.damage());
        assertEquals(500, loaded.maxDamage());
        loaded.setUnbreakable(true);
        assertTrue(reload(loaded).isUnbreakable());
        assertEquals(0, loaded.damage());
        loaded.setUnbreakable(false);
        assertEquals(17, loaded.damage());

        ModItemRef diamond = item(Items.DIAMOND);
        diamond.setMaxStackSize(16);
        assertEquals(16, reload(diamond).maxStackSize());
    }

    @Test
    void itemModelIsUnsupported() {
        ModItemRef ref = item(Items.STICK);
        ref.setItemModel(Id.minecraft("diamond"));
        assertNull(ref.itemModel());
        assertPlain(ref);
    }

    @Test
    void potionContentsRoundTrip() {
        ModItemRef potion = item(Items.POTION);
        EffectInstance speed = new EffectInstance(Id.minecraft("speed"), 200, 1, false, true, true);
        EffectInstance haste = new EffectInstance(Id.minecraft("haste"), EffectInstance.INFINITE, 0, true, false, false);
        potion.setPotionContents(new PotionContents(Id.minecraft("swiftness"), Rgb.of(0x112233), List.of(speed, haste), null));
        PotionContents loaded = reload(potion).potionContents().orElseThrow();
        assertEquals(Id.minecraft("swiftness"), loaded.potion());
        assertEquals(Rgb.of(0x112233), loaded.color());
        assertEquals(List.of(speed, haste), loaded.customEffects());
        potion.setPotionContents(PotionContents.EMPTY);
        PotionContents cleared = potion.potionContents().orElseThrow();
        assertNull(cleared.potion());
        assertTrue(cleared.customEffects().isEmpty());
        assertEquals(Optional.empty(), item(Items.STICK).potionContents());
    }

    @Test
    void attributeModifiersReplaceByIdAndKeepDefaults() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        int defaults = ref.stack().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().size();
        Id key = Id.of("adapt", "bonus");
        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(key, 2.5, AttributeOperation.ADD_VALUE), EquipmentSlotGroup.HAND);
        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(key, 4.0, AttributeOperation.ADD_VALUE), EquipmentSlot.MAIN_HAND);
        List<ItemAttributeModifiers.Entry> entries = reload(ref).stack().get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
        assertEquals(defaults + 1, entries.size());
        List<Double> amounts = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : entries) {
            if (entry.modifier().id().toString().equals(key.toString())) {
                amounts.add(entry.modifier().amount());
                assertEquals(net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND, entry.slot());
            }
        }
        assertEquals(List.of(4.0), amounts);
        ref.removeAttributeModifier(ATTACK_DAMAGE, key);
        assertPlain(ref);
    }

    @Test
    void customDataLivesUnderPublicBukkitValues() {
        ModItemRef ref = item(Items.DIAMOND);
        ref.data().set(LEVEL, 7);
        ModItemRef loaded = reload(ref);
        assertEquals(7, loaded.data().get(LEVEL));
        CompoundTag root = loaded.stack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        assertTrue(root.getCompound("PublicBukkitValues").contains(LEVEL.id().toString()));
        loaded.data().remove(LEVEL);
        assertTrue(loaded.data().isEmpty());
        assertPlain(loaded);
    }

    @Test
    void bundleContentsRoundTrip() {
        ModItemRef bundle = item(Items.BUNDLE);
        bundle.setBundleContents(List.of(new ModItemRef(new ItemStack(Items.ARROW, 5)), new ModItemRef(new ItemStack(Items.FLINT, 2))));
        List<ItemRef> contents = reload(bundle).bundleContents().orElseThrow();
        assertEquals(2, contents.size());
        assertEquals(Id.minecraft("arrow"), contents.get(0).type());
        assertEquals(5, contents.get(0).amount());
        assertEquals(2, bundle.storedItems().size());
        assertTrue(bundle.clearStoredItems());
        assertTrue(bundle.bundleContents().orElseThrow().isEmpty());
        assertEquals(Optional.empty(), item(Items.STICK).bundleContents());
    }

    @Test
    void containerContentsRoundTrip() {
        ModItemRef box = item(Items.SHULKER_BOX);
        List<ItemRef> wanted = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) {
            wanted.add(slot == 4 ? new ModItemRef(new ItemStack(Items.DIAMOND, 3)) : new ModItemRef(ItemStack.EMPTY));
        }
        box.setContainerContents(wanted);
        List<ItemRef> loaded = reload(box).containerContents().orElseThrow();
        assertEquals(27, loaded.size());
        assertEquals(Id.minecraft("diamond"), loaded.get(4).type());
        assertEquals(3, loaded.get(4).amount());
        assertTrue(loaded.get(0).isEmpty());
        assertEquals(1, box.storedItems().stream().filter((ItemRef stored) -> !stored.isEmpty()).count());
        assertTrue(box.clearStoredItems());
        assertTrue(box.storedItems().stream().allMatch(ItemRef::isEmpty));
        assertEquals(Optional.empty(), item(Items.STICK).containerContents());
    }

    @Test
    void stacksWithFreshItemAfterAllDataIsRemoved() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        Id key = Id.of("adapt", "bonus");
        ref.setDisplayName("Blade");
        ref.setLore(List.of("line"));
        ref.setGlintOverride(Boolean.TRUE);
        ref.setUnbreakable(true);
        ref.setDamage(3);
        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(key, 1, AttributeOperation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        ref.data().set(LEVEL, 1);
        assertFalse(ref.isSimilar(item(Items.DIAMOND_SWORD)));

        ref.setDisplayName(null);
        ref.setLore(null);
        ref.setGlintOverride(null);
        ref.setUnbreakable(false);
        ref.setDamage(0);
        ref.removeAttributeModifier(ATTACK_DAMAGE, key);
        ref.data().remove(LEVEL);
        assertPlain(ref);
    }

    @Test
    void copyAsKeepsComponents() {
        ModItemRef ref = item(Items.IRON_SWORD);
        ref.setDisplayName("Blade");
        ItemRef copy = ref.copyAs(Id.minecraft("golden_sword"));
        assertEquals(Id.minecraft("golden_sword"), copy.type());
        assertNotNull(copy.displayName());
        assertTrue(copy.displayName().contains("Blade"));
    }

    @Test
    void loreJsonLinesRoundTripAndAnUnreadableLineChangesNothing() {
        ModItemRef potion = item(Items.POTION);
        List<String> written = List.of(DURATION_LINE, MULTI_ITEM_LINE);
        potion.setLoreJson(written);
        assertEquals(2, potion.lore().size());
        assertEquals(ComponentJson.line(DURATION_LINE), ComponentJson.line(potion.loreJson().get(0)));
        assertEquals(ComponentJson.line(MULTI_ITEM_LINE), ComponentJson.line(potion.loreJson().get(1)));

        assertThrows(IllegalArgumentException.class, () -> potion.setLoreJson(List.of(MULTI_ITEM_LINE, "{\"color\":\"blue\"}")));
        assertEquals(2, potion.lore().size());
    }
}
