package art.arcane.adapt.mod.platform.item;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round trips through the 1.20.1 NBT item layout, each checked again after an NBT save and reload of the stack. */
class ModItemRefNbtTest {
    private static final Id SHARPNESS = Id.minecraft("sharpness");
    private static final Id UNBREAKING = Id.minecraft("unbreaking");
    private static final Id ATTACK_DAMAGE = Id.minecraft("attack_damage");
    private static final Id ARMOR = Id.minecraft("armor");
    private static final DataKey<Integer> LEVEL = DataKey.adapt("level", DataCodecs.INT);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ModItemRef item(net.minecraft.world.item.Item item) {
        return new ModItemRef(new ItemStack(item));
    }

    /** Same item and tag as a fresh stack (damageable items carry Damage:0 from construction). */
    private static void assertPlain(ModItemRef ref) {
        ItemStack stack = ref.stack();
        assertTrue(ItemStack.isSameItemSameTags(stack, new ItemStack(stack.getItem())), String.valueOf(stack.getTag()));
    }

    private static ModItemRef reload(ModItemRef ref) {
        return new ModItemRef(ItemStack.of(ref.stack().save(new CompoundTag())));
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
        CompoundTag display = loaded.stack().getTagElement("display");
        assertNotNull(display);
        assertEquals(Tag.TAG_STRING, display.getList("Lore", Tag.TAG_STRING).getElementType());

        loaded.setDisplayName(null);
        loaded.setLore(List.of());
        assertFalse(loaded.hasDisplayName());
        assertTrue(loaded.lore().isEmpty());
        assertPlain(loaded);
    }

    @Test
    void enchantmentsEditInPlaceAndKeepOrder() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        ref.setEnchantment(SHARPNESS, 3);
        ref.setEnchantment(UNBREAKING, 2);
        ref.setEnchantment(SHARPNESS, 5);
        ModItemRef loaded = reload(ref);
        assertEquals(List.of(SHARPNESS, UNBREAKING), new ArrayList<>(loaded.enchantments().keySet()));
        assertEquals(5, loaded.enchantmentLevel(SHARPNESS));
        assertEquals(2, loaded.enchantmentLevel(UNBREAKING));
        loaded.removeEnchantment(SHARPNESS);
        loaded.removeEnchantment(UNBREAKING);
        assertTrue(loaded.enchantments().isEmpty());
        assertPlain(loaded);
    }

    @Test
    void storedEnchantmentsOnBooks() {
        ModItemRef book = item(Items.ENCHANTED_BOOK);
        book.setStoredEnchantment(UNBREAKING, 3);
        ModItemRef loaded = reload(book);
        assertEquals(Map.of(UNBREAKING, 3), loaded.storedEnchantments());
        assertTrue(loaded.enchantments().isEmpty());
        loaded.removeStoredEnchantment(UNBREAKING);
        assertTrue(loaded.storedEnchantments().isEmpty());
    }

    @Test
    void glintMarkerIsAnIdLessEntryThatKeepsRealEnchantments() {
        ModItemRef ref = item(Items.STICK);
        assertFalse(ref.hasGlint());
        ref.setGlintOverride(Boolean.TRUE);
        ModItemRef loaded = reload(ref);
        assertTrue(loaded.hasGlint());
        assertTrue(loaded.enchantments().isEmpty());
        loaded.setEnchantment(UNBREAKING, 1);
        loaded.setGlintOverride(Boolean.FALSE);
        assertEquals(1, loaded.stack().getTag().getList(ItemStack.TAG_ENCH, Tag.TAG_COMPOUND).size());
        assertEquals(1, loaded.enchantmentLevel(UNBREAKING));
        loaded.removeEnchantment(UNBREAKING);
        assertFalse(loaded.hasGlint());
        assertPlain(loaded);
    }

    @Test
    void hideFlagsSetVanillaBits() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        ref.hideTooltip(TooltipPart.ATTRIBUTES);
        ref.hideTooltip(TooltipPart.ENCHANTMENTS);
        ref.hideTooltip(TooltipPart.POTION_EFFECTS);
        int expected = ItemStack.TooltipPart.MODIFIERS.getMask() | ItemStack.TooltipPart.ENCHANTMENTS.getMask()
            | ItemStack.TooltipPart.ADDITIONAL.getMask();
        assertEquals(expected, reload(ref).stack().getTag().getInt("HideFlags"));
        ref.setHideTooltip(true);
        assertEquals(0xFF, reload(ref).stack().getTag().getInt("HideFlags"));
    }

    @Test
    void unbreakableAndDamage() {
        ModItemRef ref = item(Items.IRON_PICKAXE);
        ref.setDamage(17);
        ref.setUnbreakable(true);
        ModItemRef loaded = reload(ref);
        assertTrue(loaded.isUnbreakable());
        assertEquals(0, loaded.damage());
        loaded.setUnbreakable(false);
        assertFalse(loaded.isUnbreakable());
        assertEquals(17, loaded.damage());
        assertEquals(Items.IRON_PICKAXE.getMaxDamage(), loaded.maxDamage());
        loaded.setDamage(0);
        assertPlain(loaded);

        ModItemRef stick = item(Items.STICK);
        stick.setDamage(5);
        assertEquals(0, stick.damage());
        assertEquals(0, stick.maxDamage());
    }

    @Test
    void potionContentsRoundTrip() {
        ModItemRef potion = item(Items.POTION);
        EffectInstance speed = new EffectInstance(Id.minecraft("speed"), 200, 1, false, true, true);
        EffectInstance haste = new EffectInstance(Id.minecraft("haste"), EffectInstance.INFINITE, 0, true, false, false);
        potion.setPotionContents(new PotionContents(Id.minecraft("swiftness"), Rgb.of(0x112233), List.of(speed, haste), "Brew"));
        PotionContents loaded = reload(potion).potionContents().orElseThrow();
        assertEquals(Id.minecraft("swiftness"), loaded.potion());
        assertEquals(Rgb.of(0x112233), loaded.color());
        assertEquals(List.of(speed, haste), loaded.customEffects());
        assertNotNull(loaded.name());
        assertTrue(loaded.name().contains("Brew"));

        potion.setPotionContents(PotionContents.EMPTY);
        PotionContents cleared = potion.potionContents().orElseThrow();
        assertNull(cleared.potion());
        assertNull(cleared.color());
        assertTrue(cleared.customEffects().isEmpty());
        assertNull(cleared.name());
        assertEquals(Optional.empty(), item(Items.STICK).potionContents());
    }

    @Test
    void attributeModifiersGetOneUuidPerSlot() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        Id key = Id.of("adapt", "bonus");
        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(key, 2.5, AttributeOperation.ADD_VALUE), EquipmentSlotGroup.HAND);
        ListTag modifiers = reload(ref).stack().getTag().getList("AttributeModifiers", Tag.TAG_COMPOUND);
        Set<UUID> adaptUuids = new HashSet<>();
        int defaults = 0;
        for (int index = 0; index < modifiers.size(); index++) {
            CompoundTag modifier = modifiers.getCompound(index);
            if (key.toString().equals(modifier.getString("Name"))) {
                assertEquals("minecraft:generic.attack_damage", modifier.getString("AttributeName"));
                assertEquals(2.5, modifier.getDouble("Amount"));
                adaptUuids.add(modifier.getUUID("UUID"));
                assertEquals(uuid(key, modifier.getString("Slot")), modifier.getUUID("UUID"));
            } else {
                defaults++;
            }
        }
        assertEquals(2, adaptUuids.size());
        assertEquals(2, defaults);

        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(key, 4.0, AttributeOperation.ADD_VALUE), EquipmentSlot.MAIN_HAND);
        ListTag replaced = ref.stack().getTag().getList("AttributeModifiers", Tag.TAG_COMPOUND);
        int adapt = 0;
        for (int index = 0; index < replaced.size(); index++) {
            if (key.toString().equals(replaced.getCompound(index).getString("Name"))) {
                adapt++;
                assertEquals("mainhand", replaced.getCompound(index).getString("Slot"));
            }
        }
        assertEquals(1, adapt);

        ref.removeAttributeModifier(ATTACK_DAMAGE, key);
        assertPlain(ref);

        ModItemRef boots = item(Items.IRON_BOOTS);
        boots.setAttributeModifier(ARMOR, new AttributeMod(key, 1, AttributeOperation.ADD_VALUE), EquipmentSlotGroup.ANY);
        boolean slotless = false;
        ListTag bootModifiers = boots.stack().getTag().getList("AttributeModifiers", Tag.TAG_COMPOUND);
        for (int index = 0; index < bootModifiers.size(); index++) {
            CompoundTag modifier = bootModifiers.getCompound(index);
            if (key.toString().equals(modifier.getString("Name"))) {
                assertFalse(modifier.contains("Slot"));
                assertEquals(uuid(key, "any"), modifier.getUUID("UUID"));
                slotless = true;
            }
        }
        assertTrue(slotless);
    }

    private static UUID uuid(Id key, String slot) {
        return UUID.nameUUIDFromBytes(("adapt:item_attribute_modifier/" + key + "/" + slot).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void customDataLivesUnderPublicBukkitValues() {
        ModItemRef ref = item(Items.DIAMOND);
        DataHolder data = ref.data();
        data.set(LEVEL, 7);
        ModItemRef loaded = reload(ref);
        assertEquals(7, loaded.data().get(LEVEL));
        CompoundTag values = loaded.stack().getTag().getCompound("PublicBukkitValues");
        assertTrue(values.contains(LEVEL.id().toString()));
        loaded.data().remove(LEVEL);
        assertTrue(loaded.data().isEmpty());
        assertPlain(loaded);
    }

    @Test
    void bundleContentsRoundTrip() {
        ModItemRef bundle = item(Items.BUNDLE);
        assertTrue(bundle.bundleContents().orElseThrow().isEmpty());
        bundle.setBundleContents(List.of(new ModItemRef(new ItemStack(Items.ARROW, 5)), new ModItemRef(new ItemStack(Items.FLINT, 2))));
        List<ItemRef> contents = reload(bundle).bundleContents().orElseThrow();
        assertEquals(2, contents.size());
        assertEquals(Id.minecraft("arrow"), contents.get(0).type());
        assertEquals(5, contents.get(0).amount());
        assertEquals(2, bundle.storedItems().size());
        assertTrue(bundle.clearStoredItems());
        assertTrue(bundle.bundleContents().orElseThrow().isEmpty());
        assertPlain(bundle);
        assertEquals(Optional.empty(), item(Items.STICK).bundleContents());
    }

    @Test
    void containerContentsRoundTrip() {
        ModItemRef box = item(Items.SHULKER_BOX);
        List<ItemRef> slots = box.containerContents().orElseThrow();
        assertEquals(27, slots.size());
        List<ItemRef> wanted = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) {
            wanted.add(slot == 4 ? new ModItemRef(new ItemStack(Items.DIAMOND, 3)) : new ModItemRef(ItemStack.EMPTY));
        }
        box.setContainerContents(wanted);
        List<ItemRef> loaded = reload(box).containerContents().orElseThrow();
        assertEquals(Id.minecraft("diamond"), loaded.get(4).type());
        assertEquals(3, loaded.get(4).amount());
        assertTrue(loaded.get(0).isEmpty());
        assertEquals(1, box.storedItems().size());
        assertTrue(box.clearStoredItems());
        assertTrue(box.storedItems().isEmpty());
        assertPlain(box);
        assertEquals(Optional.empty(), item(Items.STICK).containerContents());
    }

    @Test
    void stacksWithFreshItemAfterAllDataIsRemoved() {
        ModItemRef ref = item(Items.DIAMOND_SWORD);
        ref.setDisplayName("Blade");
        ref.setLore(List.of("line"));
        ref.setEnchantment(SHARPNESS, 2);
        ref.setGlintOverride(Boolean.TRUE);
        ref.setUnbreakable(true);
        ref.setDamage(3);
        ref.setAttributeModifier(ATTACK_DAMAGE, new AttributeMod(Id.of("adapt", "bonus"), 1, AttributeOperation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
        ref.data().set(LEVEL, 1);
        ModItemRef fresh = item(Items.DIAMOND_SWORD);
        assertFalse(ref.isSimilar(fresh));

        ref.setDisplayName(null);
        ref.setLore(null);
        ref.removeEnchantment(SHARPNESS);
        ref.setGlintOverride(null);
        ref.setUnbreakable(false);
        ref.setDamage(0);
        ref.removeAttributeModifier(ATTACK_DAMAGE, Id.of("adapt", "bonus"));
        ref.data().remove(LEVEL);
        assertPlain(ref);
        assertTrue(ref.isSimilar(fresh));

        ModItemRef diamond = item(Items.DIAMOND);
        diamond.data().set(LEVEL, 1);
        diamond.data().remove(LEVEL);
        assertTrue(ItemStack.isSameItemSameTags(diamond.stack(), new ItemStack(Items.DIAMOND)));
    }

    @Test
    void copyAsKeepsTheTag() {
        ModItemRef ref = item(Items.IRON_SWORD);
        ref.setDisplayName("Blade");
        ItemRef copy = ref.copyAs(Id.minecraft("golden_sword"));
        assertEquals(Id.minecraft("golden_sword"), copy.type());
        assertTrue(copy.displayName().contains("Blade"));
    }
}
