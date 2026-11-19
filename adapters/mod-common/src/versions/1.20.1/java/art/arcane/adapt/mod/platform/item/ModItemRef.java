package art.arcane.adapt.mod.platform.item;

import art.arcane.adapt.mod.platform.ModAttributeModifiers;
import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.mod.platform.data.ItemDataStore;
import art.arcane.adapt.mod.platform.data.NbtDataHolder;
import art.arcane.adapt.mod.platform.text.ComponentJson;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.item.BannerLayer;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wraps one vanilla stack. The empty stack is shared and immutable in vanilla, so an empty ref
 * ignores writes until it is replaced through a slot setter.
 *
 * <p>This version keeps item data in the stack's NBT tag, in the layout vanilla 1.20.1 and Bukkit read: display name
 * and lore under {@code display}, enchantments as {@code id}/{@code lvl} lists, attribute modifiers keyed by UUID,
 * hidden tooltip parts as {@code HideFlags} bits. Removals go through {@link ItemStack#removeTagKey}, which drops a tag
 * left empty, so an edited stack still stacks with a fresh one. There is no per-stack maximum stack size, maximum damage or item model; those setters
 * change nothing.
 */
public final class ModItemRef implements ItemRef {
    private static final String DISPLAY = "display";
    private static final String NAME = "Name";
    private static final String LORE = "Lore";
    private static final String UNBREAKABLE = "Unbreakable";
    private static final String HIDE_FLAGS = "HideFlags";
    private static final String ATTRIBUTE_MODIFIERS = "AttributeModifiers";
    private static final String ATTRIBUTE_NAME = "AttributeName";
    private static final String BUNDLE_ITEMS = "Items";
    private static final String CONTAINER_ITEMS = "Items";
    private static final String ENCHANTMENT_ID = "id";
    private static final int ALL_HIDE_FLAGS = 0xFF;
    private final ItemStack stack;

    public ModItemRef(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack, "stack");
    }

    public ItemStack stack() {
        return stack;
    }

    @Override
    public Id type() {
        return ModConversions.id(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @Override
    public int amount() {
        return stack.getCount();
    }

    @Override
    public void setAmount(int amount) {
        if (stack != ItemStack.EMPTY) {
            stack.setCount(amount);
        }
    }

    @Override
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    @Override
    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    @Override
    public ItemRef copy() {
        return new ModItemRef(stack.copy());
    }

    @Override
    public ItemRef copyWithAmount(int amount) {
        return new ModItemRef(stack.copyWithCount(amount));
    }

    @Override
    public boolean isSimilar(ItemRef other) {
        return other instanceof ModItemRef that && ItemStack.isSameItemSameTags(stack, that.stack);
    }

    @Override
    public String displayName() {
        return ModText.markup(stack.getHoverName());
    }

    @Override
    public String displayNameJson() {
        return stack.hasCustomHoverName() ? ComponentJson.json(stack.getHoverName()) : null;
    }

    @Override
    public boolean hasDisplayName() {
        return stack.hasCustomHoverName();
    }

    @Override
    public void setDisplayName(String markup) {
        if (stack.isEmpty()) {
            return;
        }
        if (markup == null) {
            stack.resetHoverName();
        } else {
            stack.setHoverName(ModText.itemText(markup));
        }
    }

    @Override
    public List<String> lore() {
        List<String> out = new ArrayList<>();
        for (Component line : loreLines()) {
            out.add(ModText.markup(line));
        }
        return out;
    }

    @Override
    public void setLore(List<String> markupLines) {
        if (stack.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>(markupLines == null ? 0 : markupLines.size());
        if (markupLines != null) {
            for (String line : markupLines) {
                lines.add(ModText.itemText(line));
            }
        }
        writeLore(lines);
    }

    @Override
    public List<String> loreJson() {
        List<String> out = new ArrayList<>();
        for (Component line : loreLines()) {
            String json = ComponentJson.json(line);
            out.add(json == null ? "\"\"" : json);
        }
        return out;
    }

    @Override
    public void setLoreJson(List<String> jsonLines) {
        if (stack.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>(jsonLines == null ? 0 : jsonLines.size());
        if (jsonLines != null) {
            for (String line : jsonLines) {
                Component parsed = ComponentJson.component(line);
                if (parsed != null) {
                    lines.add(parsed);
                }
            }
        }
        writeLore(lines);
    }

    private List<Component> loreLines() {
        CompoundTag display = stack.getTagElement(DISPLAY);
        if (display == null || !display.contains(LORE, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag stored = display.getList(LORE, Tag.TAG_STRING);
        List<Component> lines = new ArrayList<>(stored.size());
        for (int index = 0; index < stored.size(); index++) {
            Component line = ComponentJson.component(stored.getString(index));
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void writeLore(List<Component> lines) {
        if (lines.isEmpty()) {
            CompoundTag display = stack.getTagElement(DISPLAY);
            if (display != null) {
                display.remove(LORE);
                if (display.isEmpty()) {
                    stack.removeTagKey(DISPLAY);
                }
            }
            return;
        }
        ListTag stored = new ListTag();
        for (Component line : lines) {
            stored.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        stack.getOrCreateTagElement(DISPLAY).put(LORE, stored);
    }

    @Override
    public Map<Id, Integer> enchantments() {
        return levels(ItemStack.TAG_ENCH);
    }

    @Override
    public int enchantmentLevel(Id enchantment) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        if (holder == null) {
            return 0;
        }
        ResourceLocation key = BuiltInRegistries.ENCHANTMENT.getKey(holder.value());
        CompoundTag tag = stack.getTag();
        if (key == null || tag == null) {
            return 0;
        }
        ListTag list = tag.getList(ItemStack.TAG_ENCH, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (key.equals(EnchantmentHelper.getEnchantmentId(entry))) {
                return EnchantmentHelper.getEnchantmentLevel(entry);
            }
        }
        return 0;
    }

    @Override
    public void setEnchantment(Id enchantment, int level) {
        update(ItemStack.TAG_ENCH, enchantment, level);
    }

    @Override
    public void removeEnchantment(Id enchantment) {
        update(ItemStack.TAG_ENCH, enchantment, 0);
    }

    @Override
    public Map<Id, Integer> storedEnchantments() {
        return levels(EnchantedBookItem.TAG_STORED_ENCHANTMENTS);
    }

    @Override
    public void setStoredEnchantment(Id enchantment, int level) {
        update(EnchantedBookItem.TAG_STORED_ENCHANTMENTS, enchantment, level);
    }

    @Override
    public void removeStoredEnchantment(Id enchantment) {
        update(EnchantedBookItem.TAG_STORED_ENCHANTMENTS, enchantment, 0);
    }

    @Override
    public boolean hasGlint() {
        return stack.hasFoil();
    }

    /**
     * This version has no glint override. A glint is forced with an enchantment entry that names no enchantment:
     * vanilla draws the glint for any non-empty enchantment list and skips the unknown entry everywhere else.
     * Forcing the glint off is not possible; {@code false} only drops the marker.
     */
    @Override
    public void setGlintOverride(Boolean glint) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        ListTag list = tag == null ? new ListTag() : tag.getList(ItemStack.TAG_ENCH, Tag.TAG_COMPOUND);
        ListTag kept = new ListTag();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.contains(ENCHANTMENT_ID)) {
                kept.add(entry);
            }
        }
        if (Boolean.TRUE.equals(glint)) {
            kept.add(new CompoundTag());
        }
        writeList(ItemStack.TAG_ENCH, kept);
    }

    @Override
    public void clearComponents() {
        if (!stack.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** No whole-tooltip switch on this version: hiding sets every {@code HideFlags} bit, so only name and lore show. */
    @Override
    public void setHideTooltip(boolean hidden) {
        if (!stack.isEmpty() && hidden) {
            stack.getOrCreateTag().putInt(HIDE_FLAGS, ALL_HIDE_FLAGS);
        }
    }

    @Override
    public void hideTooltip(TooltipPart part) {
        if (stack.isEmpty()) {
            return;
        }
        stack.hideTooltipPart(switch (part) {
            case ATTRIBUTES -> ItemStack.TooltipPart.MODIFIERS;
            case ENCHANTMENTS -> ItemStack.TooltipPart.ENCHANTMENTS;
            case UNBREAKABLE -> ItemStack.TooltipPart.UNBREAKABLE;
            case DYE -> ItemStack.TooltipPart.DYE;
            case POTION_EFFECTS, ADDITIONAL -> ItemStack.TooltipPart.ADDITIONAL;
        });
    }

    @Override
    public void setMaxStackSize(int maxStackSize) {
    }

    @Override
    public void setFireworkStarColor(Rgb color) {
        if (stack.getItem() != Items.FIREWORK_STAR) {
            return;
        }
        CompoundTag explosion = new CompoundTag();
        explosion.putByte(FireworkRocketItem.TAG_EXPLOSION_TYPE, (byte) FireworkRocketItem.Shape.SMALL_BALL.getId());
        explosion.putIntArray(FireworkRocketItem.TAG_EXPLOSION_COLORS, new int[] {color.packed()});
        stack.getOrCreateTag().put(FireworkRocketItem.TAG_EXPLOSION, explosion);
    }

    @Override
    public ShieldDesign shieldDesign() {
        if (stack.getItem() != Items.SHIELD) {
            return null;
        }
        CompoundTag banner = BlockItem.getBlockEntityData(stack);
        if (banner == null) {
            return new ShieldDesign(null, List.of());
        }
        List<BannerLayer> layers = new ArrayList<>();
        ListTag patterns = banner.getList(BannerBlockEntity.TAG_PATTERNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < patterns.size(); index++) {
            CompoundTag layer = patterns.getCompound(index);
            Holder<BannerPattern> pattern = BannerPattern.byHash(layer.getString(BannerBlockEntity.TAG_PATTERN));
            Optional<ResourceKey<BannerPattern>> key = pattern == null ? Optional.empty() : pattern.unwrapKey();
            if (key.isPresent()) {
                layers.add(new BannerLayer(dye(net.minecraft.world.item.DyeColor.byId(layer.getInt(BannerBlockEntity.TAG_COLOR))),
                    ModConversions.id(key.get().location())));
            }
        }
        DyeColor base = banner.contains(ShieldItem.TAG_BASE_COLOR, Tag.TAG_ANY_NUMERIC)
            ? dye(net.minecraft.world.item.DyeColor.byId(banner.getInt(ShieldItem.TAG_BASE_COLOR)))
            : null;
        return new ShieldDesign(base, layers);
    }

    @Override
    public void setShieldDesign(ShieldDesign design) {
        if (stack.getItem() != Items.SHIELD) {
            return;
        }
        ListTag patterns = new ListTag();
        for (BannerLayer layer : design.patterns()) {
            ResourceKey<BannerPattern> key = ResourceKey.create(Registries.BANNER_PATTERN, ModConversions.identifier(layer.pattern()));
            Optional<Holder.Reference<BannerPattern>> pattern = BuiltInRegistries.BANNER_PATTERN.getHolder(key);
            if (pattern.isPresent()) {
                CompoundTag entry = new CompoundTag();
                entry.putString(BannerBlockEntity.TAG_PATTERN, pattern.get().value().getHashname());
                entry.putInt(BannerBlockEntity.TAG_COLOR, net.minecraft.world.item.DyeColor.valueOf(layer.color().name()).getId());
                patterns.add(entry);
            }
        }
        CompoundTag banner = stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG);
        if (design.baseColor() == null) {
            banner.remove(ShieldItem.TAG_BASE_COLOR);
        } else {
            banner.putInt(ShieldItem.TAG_BASE_COLOR, net.minecraft.world.item.DyeColor.valueOf(design.baseColor().name()).getId());
        }
        if (patterns.isEmpty()) {
            banner.remove(BannerBlockEntity.TAG_PATTERNS);
        } else {
            banner.put(BannerBlockEntity.TAG_PATTERNS, patterns);
        }
        if (banner.isEmpty()) {
            stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
        }
    }

    private static DyeColor dye(net.minecraft.world.item.DyeColor color) {
        return DyeColor.valueOf(color.name());
    }

    @Override
    public int damage() {
        return stack.isDamageableItem() ? stack.getDamageValue() : 0;
    }

    @Override
    public void setDamage(int damage) {
        if (stack.isDamageableItem()) {
            stack.setDamageValue(damage);
        }
    }

    @Override
    public int maxDamage() {
        return stack.isDamageableItem() ? stack.getMaxDamage() : 0;
    }

    @Override
    public void setMaxDamage(int maxDamage) {
    }

    @Override
    public boolean isUnbreakable() {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(UNBREAKABLE);
    }

    @Override
    public void setUnbreakable(boolean unbreakable) {
        if (stack.isEmpty()) {
            return;
        }
        if (unbreakable) {
            stack.getOrCreateTag().putBoolean(UNBREAKABLE, true);
        } else {
            stack.removeTagKey(UNBREAKABLE);
        }
    }

    @Override
    public Id itemModel() {
        return null;
    }

    @Override
    public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlot slot) {
        net.minecraft.world.entity.EquipmentSlot vanilla = ModConversions.slot(slot);
        if (vanilla != null) {
            addModifier(attribute, modifier, List.of(vanilla));
        }
    }

    /** Body and saddle groups have no item slot on this version; {@code ANY} is an entry without a slot. */
    @Override
    public void setAttributeModifier(Id attribute, AttributeMod modifier, art.arcane.adapt.platform.entity.EquipmentSlotGroup group) {
        List<net.minecraft.world.entity.EquipmentSlot> slots = switch (group) {
            case ANY -> List.of();
            case MAINHAND -> List.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            case OFFHAND -> List.of(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            case HAND -> List.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
            case FEET -> List.of(net.minecraft.world.entity.EquipmentSlot.FEET);
            case LEGS -> List.of(net.minecraft.world.entity.EquipmentSlot.LEGS);
            case CHEST -> List.of(net.minecraft.world.entity.EquipmentSlot.CHEST);
            case HEAD -> List.of(net.minecraft.world.entity.EquipmentSlot.HEAD);
            case ARMOR -> List.of(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.HEAD);
            case BODY, SADDLE -> null;
        };
        if (slots != null) {
            addModifier(attribute, modifier, slots);
        }
    }

    /**
     * Replaces the modifier with this id on the attribute. The first edit copies the item's default modifiers into
     * the tag, because a modifier list in the tag replaces the defaults. Each slot gets its own UUID, derived from the
     * id and the slot, so two worn items with the same modifier id never collide on the wearer.
     */
    private void addModifier(Id attribute, AttributeMod modifier, List<net.minecraft.world.entity.EquipmentSlot> slots) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        if (stack.isEmpty() || holder == null) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ATTRIBUTE_MODIFIERS, Tag.TAG_LIST)) {
            ListTag defaults = defaultModifiers();
            if (!defaults.isEmpty()) {
                stack.getOrCreateTag().put(ATTRIBUTE_MODIFIERS, defaults);
            }
        }
        removeModifiers(BuiltInRegistries.ATTRIBUTE.getKey(holder.value()), modifier.key());
        AttributeModifier.Operation operation = ModAttributeModifiers.operation(modifier.operation());
        if (slots.isEmpty()) {
            stack.addAttributeModifier(holder.value(), new AttributeModifier(modifierUuid(modifier.key(), null), modifier.key().toString(),
                modifier.amount(), operation), null);
            return;
        }
        for (net.minecraft.world.entity.EquipmentSlot slot : slots) {
            stack.addAttributeModifier(holder.value(), new AttributeModifier(modifierUuid(modifier.key(), slot), modifier.key().toString(),
                modifier.amount(), operation), slot);
        }
    }

    private static UUID modifierUuid(Id key, net.minecraft.world.entity.EquipmentSlot slot) {
        String slotName = slot == null ? "any" : slot.getName();
        return UUID.nameUUIDFromBytes(("adapt:item_attribute_modifier/" + key + "/" + slotName).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void removeAttributeModifier(Id attribute, Id key) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        if (stack.isEmpty() || holder == null) {
            return;
        }
        removeModifiers(BuiltInRegistries.ATTRIBUTE.getKey(holder.value()), key);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ATTRIBUTE_MODIFIERS, Tag.TAG_LIST)
            && tag.getList(ATTRIBUTE_MODIFIERS, Tag.TAG_COMPOUND).equals(defaultModifiers())) {
            stack.removeTagKey(ATTRIBUTE_MODIFIERS);
        }
    }

    private void removeModifiers(ResourceLocation attribute, Id key) {
        CompoundTag tag = stack.getTag();
        if (attribute == null || tag == null || !tag.contains(ATTRIBUTE_MODIFIERS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(ATTRIBUTE_MODIFIERS, Tag.TAG_COMPOUND);
        String name = key.toString();
        list.removeIf((Tag entry) -> entry instanceof CompoundTag modifier && name.equals(modifier.getString("Name"))
            && attribute.equals(ResourceLocation.tryParse(modifier.getString(ATTRIBUTE_NAME))));
        if (list.isEmpty()) {
            stack.removeTagKey(ATTRIBUTE_MODIFIERS);
        }
    }

    /** The item's default modifiers in tag form, as the first edit copies them; a list equal to it is dropped again. */
    private ListTag defaultModifiers() {
        ItemStack probe = new ItemStack(stack.getItem());
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            Multimap<Attribute, AttributeModifier> defaults = stack.getItem().getDefaultAttributeModifiers(slot);
            defaults.forEach((Attribute type, AttributeModifier value) -> probe.addAttributeModifier(type, value, slot));
        }
        CompoundTag tag = probe.getTag();
        return tag == null ? new ListTag() : tag.getList(ATTRIBUTE_MODIFIERS, Tag.TAG_COMPOUND);
    }

    @Override
    public void setItemModel(Id model) {
    }

    @Override
    public List<ItemRef> storedItems() {
        List<ItemRef> stored = new ArrayList<>();
        if (stack.getItem() instanceof BundleItem) {
            for (ItemStack inner : stacks(stack.getTag(), BUNDLE_ITEMS)) {
                stored.add(new ModItemRef(inner));
            }
        }
        for (ItemStack inner : stacks(BlockItem.getBlockEntityData(stack), CONTAINER_ITEMS)) {
            stored.add(new ModItemRef(inner));
        }
        return stored;
    }

    @Override
    public Optional<List<ItemRef>> bundleContents() {
        if (!(stack.getItem() instanceof BundleItem)) {
            return Optional.empty();
        }
        List<ItemRef> items = new ArrayList<>();
        for (ItemStack inner : stacks(stack.getTag(), BUNDLE_ITEMS)) {
            items.add(new ModItemRef(inner));
        }
        return Optional.of(items);
    }

    @Override
    public void setBundleContents(List<ItemRef> items) {
        if (!(stack.getItem() instanceof BundleItem)) {
            return;
        }
        ListTag stored = new ListTag();
        for (ItemRef item : items) {
            ItemStack inner = ModRefs.item(item);
            if (!inner.isEmpty()) {
                stored.add(inner.save(new CompoundTag()));
            }
        }
        writeList(BUNDLE_ITEMS, stored);
    }

    private static List<ItemStack> stacks(CompoundTag holder, String key) {
        if (holder == null || !holder.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = holder.getList(key, Tag.TAG_COMPOUND);
        List<ItemStack> stacks = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            ItemStack inner = ItemStack.of(list.getCompound(index));
            if (!inner.isEmpty()) {
                stacks.add(inner);
            }
        }
        return stacks;
    }

    @Override
    public Optional<List<ItemRef>> containerContents() {
        int size = containerSize();
        if (size <= 0) {
            return Optional.empty();
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        if (data != null) {
            ContainerHelper.loadAllItems(data, slots);
        }
        List<ItemRef> items = new ArrayList<>(size);
        for (ItemStack slot : slots) {
            items.add(new ModItemRef(slot.copy()));
        }
        return Optional.of(items);
    }

    @Override
    public void setContainerContents(List<ItemRef> slots) {
        int size = containerSize();
        if (size <= 0) {
            return;
        }
        NonNullList<ItemStack> stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int slot = 0; slot < size; slot++) {
            if (slot < slots.size() && slots.get(slot) != null) {
                stacks.set(slot, ModRefs.item(slots.get(slot)).copy());
            }
        }
        ContainerHelper.saveAllItems(stack.getOrCreateTagElement(BlockItem.BLOCK_ENTITY_TAG), stacks);
    }

    private int containerSize() {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return 0;
        }
        BlockEntity entity = entityBlock.newBlockEntity(BlockPos.ZERO, blockItem.getBlock().defaultBlockState());
        return entity instanceof Container container ? container.getContainerSize() : 0;
    }

    /** Shulker boxes always count as holding a container, as their 26.x container component is always present. */
    @Override
    public boolean clearStoredItems() {
        CompoundTag tag = stack.getTag();
        if (stack.getItem() instanceof BundleItem && tag != null && !tag.getList(BUNDLE_ITEMS, Tag.TAG_COMPOUND).isEmpty()) {
            stack.removeTagKey(BUNDLE_ITEMS);
            return true;
        }
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        boolean shulker = stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
        if (data != null && data.contains(CONTAINER_ITEMS, Tag.TAG_LIST)) {
            data.remove(CONTAINER_ITEMS);
            if (data.isEmpty()) {
                stack.removeTagKey(BlockItem.BLOCK_ENTITY_TAG);
            }
            return true;
        }
        return shulker;
    }

    @Override
    public Optional<art.arcane.adapt.platform.item.PotionContents> potionContents() {
        if (!holdsPotion()) {
            return Optional.empty();
        }
        List<MobEffectInstance> custom = PotionUtils.getCustomEffects(stack);
        List<EffectInstance> effects = new ArrayList<>(custom.size());
        for (MobEffectInstance effect : custom) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            if (key != null) {
                effects.add(new EffectInstance(ModContentAliases.modern(RegistryKind.EFFECT, key),
                    effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
        }
        Potion stored = PotionUtils.getPotion(stack);
        Id potion = stored == Potions.EMPTY ? null : ModContentAliases.modern(RegistryKind.POTION, BuiltInRegistries.POTION.getKey(stored));
        CompoundTag tag = stack.getTag();
        Rgb color = tag != null && tag.contains(PotionUtils.TAG_CUSTOM_POTION_COLOR, Tag.TAG_ANY_NUMERIC)
            ? Rgb.of(tag.getInt(PotionUtils.TAG_CUSTOM_POTION_COLOR) & 0xFFFFFF)
            : null;
        return Optional.of(new art.arcane.adapt.platform.item.PotionContents(potion, color, effects,
            stack.hasCustomHoverName() ? ModText.markup(stack.getHoverName()) : null));
    }

    @Override
    public void setPotionContents(art.arcane.adapt.platform.item.PotionContents contents) {
        if (stack.isEmpty() || !holdsPotion()) {
            return;
        }
        List<MobEffectInstance> effects = new ArrayList<>(contents.customEffects().size());
        for (EffectInstance effect : contents.customEffects()) {
            Holder<MobEffect> type = ModConversions.effect(effect.type());
            if (type != null) {
                int duration = effect.isInfinite() ? MobEffectInstance.INFINITE_DURATION : effect.durationTicks();
                effects.add(new MobEffectInstance(type.value(), duration, effect.amplifier(), effect.ambient(), effect.particles(),
                    effect.icon()));
            }
        }
        stack.removeTagKey(PotionUtils.TAG_CUSTOM_POTION_EFFECTS);
        if (!effects.isEmpty()) {
            PotionUtils.setCustomEffects(stack, effects);
        }
        Holder<Potion> potion = contents.potion() == null ? null
            : ModContentAliases.holder(BuiltInRegistries.POTION, RegistryKind.POTION, contents.potion());
        PotionUtils.setPotion(stack, potion == null ? Potions.EMPTY : potion.value());
        if (contents.color() == null) {
            stack.removeTagKey(PotionUtils.TAG_CUSTOM_POTION_COLOR);
        } else {
            stack.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, contents.color().packed());
        }
        String current = stack.hasCustomHoverName() ? ModText.markup(stack.getHoverName()) : null;
        if (!Objects.equals(current, contents.name())) {
            if (contents.name() == null) {
                stack.resetHoverName();
            } else {
                stack.setHoverName(potionName(contents.name()));
            }
        }
    }

    private boolean holdsPotion() {
        Item item = stack.getItem();
        return item instanceof PotionItem || item instanceof TippedArrowItem;
    }

    private static Component potionName(String markup) {
        if (markup.indexOf('<') < 0 && markup.indexOf('§') < 0 && markup.indexOf('&') < 0) {
            return Component.literal(markup).withStyle((Style style) -> style.withItalic(Boolean.FALSE));
        }
        return ModText.itemText(markup);
    }

    @Override
    public ItemRef copyAs(Id type) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(type));
        if (item.isEmpty()) {
            return new ModItemRef(stack.copy());
        }
        ItemStack copy = new ItemStack(item.get(), stack.getCount());
        CompoundTag tag = stack.getTag();
        copy.setTag(tag == null ? null : tag.copy());
        return new ModItemRef(copy);
    }

    @Override
    public DataHolder data() {
        return new NbtDataHolder(new ItemDataStore(this::stack));
    }

    @Override
    public Object unwrap() {
        return stack;
    }

    @Override
    public String toString() {
        return "ModItemRef[" + stack + "]";
    }

    private Map<Id, Integer> levels(String listKey) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(listKey, Tag.TAG_LIST)) {
            return Map.of();
        }
        ListTag list = tag.getList(listKey, Tag.TAG_COMPOUND);
        Map<Id, Integer> out = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation key = EnchantmentHelper.getEnchantmentId(entry);
            Optional<Enchantment> enchantment = key == null ? Optional.empty() : BuiltInRegistries.ENCHANTMENT.getOptional(key);
            int level = EnchantmentHelper.getEnchantmentLevel(entry);
            if (enchantment.isPresent() && level > 0) {
                Id id = ModConversions.enchantmentId(BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment.get()));
                if (id != null) {
                    out.putIfAbsent(id, level);
                }
            }
        }
        return out;
    }

    /** Sets or removes one entry in place, keeping the order of the others and any glint marker. */
    private void update(String listKey, Id enchantment, int level) {
        if (stack.isEmpty()) {
            return;
        }
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        ResourceLocation key = holder == null ? null : BuiltInRegistries.ENCHANTMENT.getKey(holder.value());
        if (key == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(255, level));
        CompoundTag tag = stack.getTag();
        ListTag list = tag == null ? new ListTag() : tag.getList(listKey, Tag.TAG_COMPOUND);
        ListTag result = new ListTag();
        boolean written = false;
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!key.equals(EnchantmentHelper.getEnchantmentId(entry))) {
                result.add(entry);
            } else if (clamped > 0 && !written) {
                result.add(EnchantmentHelper.storeEnchantment(key, clamped));
                written = true;
            }
        }
        if (clamped > 0 && !written) {
            result.add(EnchantmentHelper.storeEnchantment(key, clamped));
        }
        writeList(listKey, result);
    }

    private void writeList(String key, ListTag list) {
        if (list.isEmpty()) {
            stack.removeTagKey(key);
        } else {
            stack.getOrCreateTag().put(key, list);
        }
    }
}
