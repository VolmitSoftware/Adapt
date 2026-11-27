package art.arcane.adapt.api.item;

import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.data.DataCodec;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Items content invents: a vanilla base item carrying an id and typed data.
 *
 * <p>A definition is registered by an adaptation with {@code registerItem}, by a mutation tool, or, for an item that
 * belongs to no adaptation, by the addon with {@code ContentRegistrar.item}. Every registered definition is listed
 * by {@code ContentCatalog.items()}, and that list is the one registry Adapt's item guard reads: for each stack it
 * finds the definition that {@linkplain Definition#is recognises} it, refuses every vanilla use the definition does
 * not {@linkplain Definition#allowedUses(ItemRef) allow} for that stack and every recipe it is not
 * {@linkplain Definition#consumableBy consumable by}. Adapt names the item from {@code <namespace>.item.<path>.name}
 * and {@code .lore1}, {@code .lore2}.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class CustomItem {
  private CustomItem() {
  }

  /** The data of an item that carries none. */
  public enum Plain {
    INSTANCE
  }

  /**
   * One kind of custom item. Declared once as a constant and shared by everything that makes or reads the item.
   *
   * @param <D> the data each stack carries
   */
  public static final class Definition<D> {
    private final Id id;
    private final Id baseItem;
    private final DataCodec<D> codec;
    private final D defaultData;
    private final Function<ItemRef, Set<VanillaUse>> uses;
    private final @Nullable Predicate<ItemRef> recognises;
    private final Predicate<Id> recipes;
    private final Id itemModel;
    private final Id cooldownGroup;
    private final int maxStackSize;
    private final Id dataKey;
    private final Predicate<ItemRef> claimsGestures;

    private Definition(Builder<D> builder) {
      this.id = builder.id;
      this.dataKey = builder.storedUnder != null ? builder.storedUnder : builder.id;
      this.baseItem = builder.baseItem;
      this.codec = builder.codec;
      this.defaultData = builder.defaultData;
      if (builder.perStackUses != null) {
        this.uses = builder.perStackUses;
      } else {
        Set<VanillaUse> fixed = Set.copyOf(builder.allowedUses);
        this.uses = (ItemRef stack) -> fixed;
      }
      this.recognises = builder.recognises;
      String namespace = builder.id.namespace();
      this.recipes = builder.recipes != null ? builder.recipes : (Id recipe) -> namespace.equals(recipe.namespace());
      this.itemModel = builder.itemModel;
      this.cooldownGroup = builder.ownCooldownGroup ? builder.id : null;
      this.maxStackSize = builder.maxStackSize;
      this.claimsGestures = builder.claimsGestures;
    }

    /** The item's id, which every stack of it carries. */
    public Id id() {
      return id;
    }

    /** The vanilla item the stacks are made of. */
    public Id baseItem() {
      return baseItem;
    }

    /**
     * The key every stack stores its data under, and carries to be recognised: the item's id unless
     * {@link Builder#storedUnder} gave another.
     */
    public Id dataKey() {
      return dataKey;
    }

    /** How the stack data is stored on the item. */
    public DataCodec<D> codec() {
      return codec;
    }

    /** The data a stack from {@link #create(int)} carries. */
    public D defaultData() {
      return defaultData;
    }

    /**
     * The vanilla uses of the base item that {@code stack}, a stack of this item, keeps: the set given to
     * {@link Builder#allow}, or what the rule given to {@link Builder#allowPerStack} returns for the stack.
     */
    public Set<VanillaUse> allowedUses(ItemRef stack) {
      return uses.apply(Objects.requireNonNull(stack, "stack"));
    }

    /** Whether {@code stack}, a stack of this item, keeps the vanilla use {@code use}. */
    public boolean allows(ItemRef stack, VanillaUse use) {
      return allowedUses(stack).contains(use);
    }

    /**
     * Whether the recipe {@code recipe} may take this item as an ingredient. By default only recipes in the item's
     * own namespace may; {@link Builder#consumableBy} replaces that rule. False for a null recipe, which is a craft
     * whose recipe id is unknown.
     */
    public boolean consumableBy(@Nullable Id recipe) {
      return recipe != null && recipes.test(recipe);
    }

    /** The resource pack model the stack is drawn with; empty for {@code <namespace>:item/<path>}. */
    public Optional<Id> itemModel() {
      return Optional.ofNullable(itemModel);
    }

    /**
     * The cooldown group the stack uses in place of its base item's, so its cooldown never greys out plain ones: the
     * item's id when it {@linkplain Builder#ownCooldownGroup() has one}.
     */
    public Optional<Id> cooldownGroup() {
      return Optional.ofNullable(cooldownGroup);
    }

    /** The stack size, or 0 for the base item's. */
    public int maxStackSize() {
      return maxStackSize;
    }

    /**
     * Whether {@code stack}, a stack of this item held in the main hand, claims the player's sneak and swap-hand
     * gestures for itself, so mutation barters do not fire on them. False unless {@link Builder#claimsGestures} says so.
     */
    @ApiStatus.Experimental
    public boolean claimsGestures(ItemRef stack) {
      return claimsGestures.test(Objects.requireNonNull(stack, "stack"));
    }

    /** A stack of {@code amount} carrying the default data. */
    public ItemRef create(int amount) {
      return Engine.bridge().items().create(this, defaultData, amount);
    }

    /** One item carrying {@code data}. */
    public ItemRef create(D data) {
      return Engine.bridge().items().create(this, Objects.requireNonNull(data, "data"), 1);
    }

    /**
     * Whether {@code item} is a stack of this item: it carries this definition's id, as every stack from
     * {@link #create} does, or it passes the rule given to {@link Builder#recognisedBy}. False for null and empty
     * stacks.
     */
    public boolean is(@Nullable ItemRef item) {
      if (item == null || item.isEmpty()) {
        return false;
      }
      return Engine.bridge().items().is(this, item) || (recognises != null && recognises.test(item));
    }

    /**
     * The data of a stack that carries this definition's id; empty for any other stack, including one recognised
     * only by the {@link Builder#recognisedBy} rule.
     */
    public Optional<D> read(@Nullable ItemRef item) {
      return item == null ? Optional.empty() : Optional.ofNullable(Engine.bridge().items().read(this, item));
    }

    /** Replaces the data of a stack that carries this definition's id; false for any other stack. */
    public boolean write(ItemRef item, D data) {
      return Engine.bridge().items().write(this, item, Objects.requireNonNull(data, "data"));
    }

    /**
     * Turns {@code item}, whatever it was, into a stack of this item carrying {@code data}, on the same stack
     * instance: every component is cleared first, then the stack is set up as {@link #create} sets up a new one. The
     * stack keeps its base item and amount; write it back to its slot afterwards.
     */
    public void replace(ItemRef item, D data) {
      Engine.bridge().items().replace(this, Objects.requireNonNull(item, "item"), Objects.requireNonNull(data, "data"));
    }

    @Override
    public String toString() {
      return "CustomItem[" + id + "]";
    }
  }

  /** An item with typed data. */
  public static <D> Builder<D> builder(Id id, Id baseItem, DataCodec<D> codec, D defaultData) {
    return new Builder<>(id, baseItem, codec, defaultData);
  }

  /** An item without data. */
  public static Builder<Plain> builder(Id id, Id baseItem) {
    return new Builder<>(id, baseItem, DataCodecs.enumByName(Plain.class), Plain.INSTANCE);
  }

  /** Builds a {@link Definition}; every setting but the id, base item and data is optional. */
  public static final class Builder<D> {
    private final Id id;
    private final Id baseItem;
    private final DataCodec<D> codec;
    private final D defaultData;
    private final EnumSet<VanillaUse> allowedUses = EnumSet.noneOf(VanillaUse.class);
    private Function<ItemRef, Set<VanillaUse>> perStackUses;
    private Predicate<ItemRef> recognises;
    private Predicate<Id> recipes;
    private Id itemModel;
    private boolean ownCooldownGroup;
    private int maxStackSize;
    private Id storedUnder;
    private Predicate<ItemRef> claimsGestures = (ItemRef stack) -> false;

    private Builder(Id id, Id baseItem, DataCodec<D> codec, D defaultData) {
      this.id = ContentIds.require(id, "item");
      this.baseItem = Objects.requireNonNull(baseItem, "baseItem");
      this.codec = Objects.requireNonNull(codec, "codec");
      this.defaultData = Objects.requireNonNull(defaultData, "defaultData");
    }

    /**
     * Keeps these vanilla uses of the base item on every stack. Without this or {@link #allowPerStack}, the item
     * refuses all of them.
     *
     * @throws IllegalStateException after {@link #allowPerStack}
     */
    public Builder<D> allow(VanillaUse first, VanillaUse... more) {
      if (perStackUses != null) {
        throw new IllegalStateException(id + " already decides its vanilla uses per stack");
      }
      allowedUses.add(Objects.requireNonNull(first, "first"));
      allowedUses.addAll(Set.of(more));
      return this;
    }

    /**
     * Decides the kept vanilla uses for each stack, for an item whose stacks differ, such as a dormant and an awake
     * form. The rule runs on every guarded interaction, so it must be cheap, must not return null and should return
     * shared immutable sets.
     *
     * @throws IllegalStateException after {@link #allow} or a second call
     */
    public Builder<D> allowPerStack(Function<ItemRef, Set<VanillaUse>> rule) {
      if (perStackUses != null || !allowedUses.isEmpty()) {
        throw new IllegalStateException(id + " already declares its vanilla uses");
      }
      this.perStackUses = Objects.requireNonNull(rule, "rule");
      return this;
    }

    /**
     * Also recognises stacks that carry no id of this definition, for an item made or marked outside
     * {@link Definition#create}. The rule runs for every guarded stack, so it must be cheap and must not match the
     * stacks of another registered item.
     */
    public Builder<D> recognisedBy(Predicate<ItemRef> rule) {
      this.recognises = Objects.requireNonNull(rule, "rule");
      return this;
    }

    /** Replaces the default recipe rule, recipes in the item's own namespace, with {@code rule}. */
    public Builder<D> consumableBy(Predicate<Id> rule) {
      this.recipes = Objects.requireNonNull(rule, "rule");
      return this;
    }

    /** The resource pack model the stacks are drawn with, in place of {@code <namespace>:item/<path>}. */
    public Builder<D> itemModel(Id model) {
      this.itemModel = Objects.requireNonNull(model, "model");
      return this;
    }

    /**
     * A cooldown group of the item's own, keyed by its id, so its cooldown does not grey out plain stacks of the base
     * item or any other addon's item.
     */
    public Builder<D> ownCooldownGroup() {
      this.ownCooldownGroup = true;
      return this;
    }

    /**
     * Stores the stack data under {@code key} in place of the item's id, for an item whose stacks already exist under
     * an older key. Stacks are recognised by that key.
     */
    public Builder<D> storedUnder(Id key) {
      this.storedUnder = Objects.requireNonNull(key, "key");
      return this;
    }

    /**
     * The stack size, 1 to 99, in place of the base item's.
     *
     * @throws IllegalArgumentException outside 1 to 99
     */
    public Builder<D> maxStackSize(int size) {
      if (size < 1 || size > 99) {
        throw new IllegalArgumentException("Stack size must be 1 to 99, got " + size);
      }
      this.maxStackSize = size;
      return this;
    }

    /**
     * Held in the main hand, the stacks that pass {@code rule} keep the player's sneak and swap-hand gestures for the
     * item's own controls: mutation barters do not fire on them. The rule runs on every gesture, so it must be cheap.
     */
    @ApiStatus.Experimental
    public Builder<D> claimsGestures(Predicate<ItemRef> rule) {
      this.claimsGestures = Objects.requireNonNull(rule, "rule");
      return this;
    }

    /** The finished definition. */
    public Definition<D> build() {
      return new Definition<>(this);
    }
  }
}
