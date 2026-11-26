package art.arcane.adapt.mod.platform.item;

import art.arcane.adapt.mod.platform.ModRefs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStackTemplate;
import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.mod.platform.data.ItemDataStore;
import art.arcane.adapt.mod.platform.data.NbtDataHolder;
import art.arcane.adapt.mod.platform.text.ComponentJson;
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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wraps one vanilla stack. The empty stack is shared and immutable in vanilla, so an empty ref
 * ignores writes until it is replaced through a slot setter.
 */
public final class ModItemRef implements ItemRef {
    private static final List<DataComponentType<?>> ADDITIONAL_TOOLTIP = List.of(DataComponents.FIREWORK_EXPLOSION,
            DataComponents.FIREWORKS, DataComponents.POTION_CONTENTS, DataComponents.POT_DECORATIONS, DataComponents.CONTAINER,
            DataComponents.CONTAINER_LOOT, DataComponents.BUNDLE_CONTENTS, DataComponents.BANNER_PATTERNS, DataComponents.INSTRUMENT,
            DataComponents.JUKEBOX_PLAYABLE, DataComponents.MAP_ID, DataComponents.CHARGED_PROJECTILES, DataComponents.BEES,
            DataComponents.TROPICAL_FISH_PATTERN, DataComponents.WRITTEN_BOOK_CONTENT, DataComponents.BLOCK_ENTITY_DATA);
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
        return other instanceof ModItemRef that && ItemStack.isSameItemSameComponents(stack, that.stack);
    }

    @Override
    public String displayName() {
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        return ModText.markup(custom != null ? custom : stack.getHoverName());
    }

    @Override
    public String displayNameJson() {
        return ComponentJson.json(stack.get(DataComponents.CUSTOM_NAME));
    }

    @Override
    public boolean hasDisplayName() {
        return stack.has(DataComponents.CUSTOM_NAME);
    }

    @Override
    public void setDisplayName(String markup) {
        if (stack.isEmpty()) {
            return;
        }
        if (markup == null) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, ModText.itemText(markup));
        }
    }

    @Override
    public List<String> lore() {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            out.add(ModText.markup(line));
        }
        return out;
    }

    @Override
    public void setLore(List<String> markupLines) {
        if (stack.isEmpty()) {
            return;
        }
        if (markupLines == null || markupLines.isEmpty()) {
            restoreDefault(DataComponents.LORE);
            return;
        }
        List<Component> lines = new ArrayList<>(markupLines.size());
        for (String line : markupLines) {
            lines.add(ModText.itemText(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    @Override
    public List<String> loreJson() {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            out.add(ComponentJson.json(line));
        }
        return out;
    }

    @Override
    public void setLoreJson(List<String> jsonLines) {
        if (stack.isEmpty()) {
            return;
        }
        if (jsonLines == null || jsonLines.isEmpty()) {
            restoreDefault(DataComponents.LORE);
            return;
        }
        List<Component> lines = new ArrayList<>(jsonLines.size());
        for (String line : jsonLines) {
            lines.add(ComponentJson.line(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    @Override
    public Map<Id, Integer> enchantments() {
        return levels(DataComponents.ENCHANTMENTS);
    }

    @Override
    public int enchantmentLevel(Id enchantment) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        if (holder == null) {
            return 0;
        }
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments == null ? 0 : enchantments.getLevel(holder);
    }

    @Override
    public void setEnchantment(Id enchantment, int level) {
        update(DataComponents.ENCHANTMENTS, enchantment, level);
    }

    @Override
    public void removeEnchantment(Id enchantment) {
        update(DataComponents.ENCHANTMENTS, enchantment, 0);
    }

    @Override
    public Map<Id, Integer> storedEnchantments() {
        return levels(DataComponents.STORED_ENCHANTMENTS);
    }

    @Override
    public void setStoredEnchantment(Id enchantment, int level) {
        update(DataComponents.STORED_ENCHANTMENTS, enchantment, level);
    }

    @Override
    public void removeStoredEnchantment(Id enchantment) {
        update(DataComponents.STORED_ENCHANTMENTS, enchantment, 0);
    }

    @Override
    public boolean hasGlint() {
        return stack.hasFoil();
    }

    @Override
    public void setGlintOverride(Boolean glint) {
        if (stack.isEmpty()) {
            return;
        }
        if (glint == null) {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        } else {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
        }
    }

    @Override
    public void clearComponents() {
        if (stack.isEmpty()) {
            return;
        }
        DataComponentPatch.SplitResult patch = stack.getComponentsPatch().split();
        List<DataComponentType<?>> changed = new ArrayList<>(patch.added().keySet());
        changed.addAll(patch.removed());
        for (DataComponentType<?> type : changed) {
            restoreDefault(type);
        }
    }

    private <T> void restoreDefault(DataComponentType<T> type) {
        T prototype = stack.getPrototype().get(type);
        if (prototype == null) {
            stack.remove(type);
        } else {
            stack.set(type, prototype);
        }
    }

    @Override
    public void setHideTooltip(boolean hidden) {
        if (stack.isEmpty()) {
            return;
        }
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(hidden, display.hiddenComponents()));
    }

    @Override
    public void hideTooltip(TooltipPart part) {
        if (stack.isEmpty()) {
            return;
        }
        DataComponentType<?> component = switch (part) {
            case ATTRIBUTES -> DataComponents.ATTRIBUTE_MODIFIERS;
            case ENCHANTMENTS -> DataComponents.ENCHANTMENTS;
            case POTION_EFFECTS -> DataComponents.POTION_CONTENTS;
            case UNBREAKABLE -> DataComponents.UNBREAKABLE;
            case DYE -> DataComponents.DYED_COLOR;
            case ADDITIONAL -> null;
        };
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (component != null) {
            display = display.withHidden(component, true);
        } else {
            for (DataComponentType<?> detail : ADDITIONAL_TOOLTIP) {
                display = display.withHidden(detail, true);
            }
        }
        stack.set(DataComponents.TOOLTIP_DISPLAY, display);
    }

    @Override
    public void setMaxStackSize(int maxStackSize) {
        if (!stack.isEmpty()) {
            stack.set(DataComponents.MAX_STACK_SIZE, Math.max(1, Math.min(99, maxStackSize)));
        }
    }

    @Override
    public void setFireworkStarColor(Rgb color) {
        if (stack.getItem() == Items.FIREWORK_STAR) {
            stack.set(DataComponents.FIREWORK_EXPLOSION, new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                    IntList.of(color.packed()), IntList.of(), false, false));
        }
    }

    @Override
    public ShieldDesign shieldDesign() {
        if (stack.getItem() != Items.SHIELD) {
            return null;
        }
        List<BannerLayer> layers = new ArrayList<>();
        BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        for (BannerPatternLayers.Layer layer : patterns.layers()) {
            Optional<ResourceKey<BannerPattern>> key = layer.pattern().unwrapKey();
            if (key.isPresent()) {
                layers.add(new BannerLayer(dye(layer.color()), ModConversions.id(key.get().identifier())));
            }
        }
        net.minecraft.world.item.DyeColor base = stack.get(DataComponents.BASE_COLOR);
        return new ShieldDesign(base == null ? null : dye(base), layers);
    }

    @Override
    public void setShieldDesign(ShieldDesign design) {
        if (stack.getItem() != Items.SHIELD) {
            return;
        }
        MinecraftServer server = ModPlatform.server();
        List<BannerPatternLayers.Layer> layers = new ArrayList<>();
        if (server != null) {
            HolderLookup.RegistryLookup<BannerPattern> registry = server.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
            for (BannerLayer layer : design.patterns()) {
                registry.get(ResourceKey.create(Registries.BANNER_PATTERN, ModConversions.identifier(layer.pattern())))
                    .ifPresent((Holder.Reference<BannerPattern> pattern) -> layers.add(new BannerPatternLayers.Layer(pattern,
                        net.minecraft.world.item.DyeColor.valueOf(layer.color().name()))));
            }
        }
        if (design.baseColor() == null) {
            stack.remove(DataComponents.BASE_COLOR);
        } else {
            stack.set(DataComponents.BASE_COLOR, net.minecraft.world.item.DyeColor.valueOf(design.baseColor().name()));
        }
        if (layers.isEmpty()) {
            stack.remove(DataComponents.BANNER_PATTERNS);
        } else {
            stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
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
        if (stack.isDamageableItem()) {
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, maxDamage));
        }
    }

    @Override
    public boolean isUnbreakable() {
        return stack.has(DataComponents.UNBREAKABLE);
    }

    @Override
    public void setUnbreakable(boolean unbreakable) {
        if (stack.isEmpty()) {
            return;
        }
        if (unbreakable) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        } else {
            stack.remove(DataComponents.UNBREAKABLE);
        }
    }

    @Override
    public Id itemModel() {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        Identifier prototype = stack.getPrototype().get(DataComponents.ITEM_MODEL);
        return model == null || model.equals(prototype) ? null : ModConversions.id(model);
    }

    @Override
    public void setAttributeModifier(Id attribute, AttributeMod modifier, EquipmentSlot slot) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        if (stack.isEmpty() || holder == null) {
            return;
        }
        AttributeModifier.Operation operation = switch (modifier.operation()) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
        ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, current.withModifierAdded(holder,
                new AttributeModifier(ModConversions.identifier(modifier.key()), modifier.amount(), operation),
                EquipmentSlotGroup.bySlot(ModConversions.slot(slot))));
    }

    @Override
    public void setAttributeModifier(Id attribute, AttributeMod modifier, art.arcane.adapt.platform.entity.EquipmentSlotGroup group) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        if (stack.isEmpty() || holder == null) {
            return;
        }
        AttributeModifier.Operation operation = switch (modifier.operation()) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        };
        EquipmentSlotGroup slots = switch (group) {
            case ANY -> EquipmentSlotGroup.ANY;
            case MAINHAND -> EquipmentSlotGroup.MAINHAND;
            case OFFHAND -> EquipmentSlotGroup.OFFHAND;
            case HAND -> EquipmentSlotGroup.HAND;
            case FEET -> EquipmentSlotGroup.FEET;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case HEAD -> EquipmentSlotGroup.HEAD;
            case ARMOR -> EquipmentSlotGroup.ARMOR;
            case BODY -> EquipmentSlotGroup.BODY;
            case SADDLE -> EquipmentSlotGroup.SADDLE;
        };
        ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, current.withModifierAdded(holder,
                new AttributeModifier(ModConversions.identifier(modifier.key()), modifier.amount(), operation), slots));
    }

    @Override
    public void removeAttributeModifier(Id attribute, Id key) {
        Holder<Attribute> holder = ModConversions.attribute(attribute);
        if (stack.isEmpty() || holder == null) {
            return;
        }
        Identifier id = ModConversions.identifier(key);
        ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        List<ItemAttributeModifiers.Entry> kept = new ArrayList<>(current.modifiers().size());
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            if (!entry.attribute().equals(holder) || !entry.modifier().id().equals(id)) {
                kept.add(entry);
            }
        }
        if (kept.size() != current.modifiers().size()) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(kept));
        }
    }

    @Override
    public void setItemModel(Id model) {
        if (stack.isEmpty()) {
            return;
        }
        if (model == null) {
            Identifier prototype = stack.getPrototype().get(DataComponents.ITEM_MODEL);
            if (prototype == null) {
                stack.remove(DataComponents.ITEM_MODEL);
            } else {
                stack.set(DataComponents.ITEM_MODEL, prototype);
            }
        } else {
            stack.set(DataComponents.ITEM_MODEL, ModConversions.identifier(model));
        }
    }

    @Override
    public List<ItemRef> storedItems() {
        List<ItemRef> stored = new ArrayList<>();
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            Compat.bundleItems(bundle).forEach((ItemStack inner) -> stored.add(new ModItemRef(inner)));
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            Compat.containerItems(contents).forEach((ItemStack inner) -> stored.add(new ModItemRef(inner)));
        }
        return stored;
    }

    @Override
    public Optional<List<ItemRef>> bundleContents() {
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle == null) {
            return Optional.empty();
        }
        List<ItemRef> items = new ArrayList<>();
        Compat.bundleItems(bundle).forEach((ItemStack inner) -> items.add(new ModItemRef(inner)));
        return Optional.of(items);
    }

    @Override
    public void setBundleContents(List<ItemRef> items) {
        if (stack.get(DataComponents.BUNDLE_CONTENTS) == null) {
            return;
        }
        List<ItemStackTemplate> templates = new ArrayList<>(items.size());
        for (ItemRef item : items) {
            ItemStack inner = ModRefs.item(item);
            if (!inner.isEmpty()) {
                templates.add(ItemStackTemplate.fromNonEmptyStack(inner));
            }
        }
        stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));
    }

    @Override
    public Optional<List<ItemRef>> containerContents() {
        int size = containerSize();
        if (size <= 0) {
            return Optional.empty();
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(slots);
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
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            stacks.add(slot < slots.size() && slots.get(slot) != null ? ModRefs.item(slots.get(slot)).copy() : ItemStack.EMPTY);
        }
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
    }

    private int containerSize() {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return 0;
        }
        BlockEntity entity = entityBlock.newBlockEntity(BlockPos.ZERO, blockItem.getBlock().defaultBlockState());
        return entity instanceof Container container ? container.getContainerSize() : 0;
    }

    @Override
    public boolean clearStoredItems() {
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            stack.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            return true;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            return true;
        }
        return false;
    }

    @Override
    public Optional<art.arcane.adapt.platform.item.PotionContents> potionContents() {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return Optional.empty();
        }
        List<EffectInstance> effects = new ArrayList<>(contents.customEffects().size());
        for (MobEffectInstance effect : contents.customEffects()) {
            Optional<ResourceKey<MobEffect>> key = effect.getEffect().unwrapKey();
            if (key.isPresent()) {
                effects.add(new EffectInstance(ModConversions.id(key.get().identifier()),
                    effect.isInfiniteDuration() ? EffectInstance.INFINITE : effect.getDuration(), effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()));
            }
        }
        Id potion = contents.potion()
            .flatMap((Holder<Potion> holder) -> holder.unwrapKey())
            .map((ResourceKey<Potion> key) -> ModConversions.id(key.identifier()))
            .orElse(null);
        Rgb color = contents.customColor().map((Integer packed) -> Rgb.of(packed & 0xFFFFFF)).orElse(null);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return Optional.of(new art.arcane.adapt.platform.item.PotionContents(potion, color, effects,
            name == null ? null : ModText.markup(name)));
    }

    @Override
    public void setPotionContents(art.arcane.adapt.platform.item.PotionContents contents) {
        if (stack.isEmpty() || !stack.has(DataComponents.POTION_CONTENTS)) {
            return;
        }
        List<MobEffectInstance> effects = new ArrayList<>(contents.customEffects().size());
        for (EffectInstance effect : contents.customEffects()) {
            Holder<MobEffect> type = ModConversions.effect(effect.type());
            if (type != null) {
                int duration = effect.isInfinite() ? MobEffectInstance.INFINITE_DURATION : effect.durationTicks();
                effects.add(new MobEffectInstance(type, duration, effect.amplifier(), effect.ambient(), effect.particles(),
                    effect.icon()));
            }
        }
        Optional<Holder<Potion>> potion = contents.potion() == null ? Optional.empty()
            : BuiltInRegistries.POTION.get(ModConversions.identifier(contents.potion())).map((Holder.Reference<Potion> ref) -> ref);
        Optional<Integer> color = contents.color() == null ? Optional.empty() : Optional.of(contents.color().packed());
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion, color, effects, Optional.empty()));
        Component current = stack.get(DataComponents.CUSTOM_NAME);
        if (Objects.equals(current == null ? null : ModText.markup(current), contents.name())) {
            return;
        }
        if (contents.name() == null) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, potionName(contents.name()));
        }
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
        return new ModItemRef(item.isPresent() ? stack.transmuteCopy(item.get()) : stack.copy());
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

    private Map<Id, Integer> levels(DataComponentType<ItemEnchantments> component) {
        ItemEnchantments enchantments = stack.get(component);
        if (enchantments == null || enchantments.isEmpty()) {
            return Map.of();
        }
        Map<Id, Integer> out = new LinkedHashMap<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Id id = ModConversions.enchantmentId(entry.getKey());
            if (id != null) {
                out.put(id, entry.getIntValue());
            }
        }
        return out;
    }

    private void update(DataComponentType<ItemEnchantments> component, Id enchantment, int level) {
        if (stack.isEmpty()) {
            return;
        }
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        if (holder == null) {
            return;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(component, ItemEnchantments.EMPTY));
        mutable.set(holder, Math.max(0, Math.min(255, level)));
        ItemEnchantments result = mutable.toImmutable();
        if (result.isEmpty() && component == DataComponents.STORED_ENCHANTMENTS) {
            stack.remove(component);
        } else {
            stack.set(component, result);
        }
    }
}
