package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Everything one skill brings to a mutation. A skill registers its kit with {@code registerMutationKit}.
 *
 * <p>A kit is complete on its own. Any two skills can be merged, in either order, with no table naming the pair.
 * A skill that registers no kit gets one Adapt generates: its Trigger is earning the skill's XP, its Strikes, Boon,
 * Fuel and State are plain generic effects, its words are the skill's name, its tint the skill colour, and it brings
 * no upgrades. In every pair: the mutation's name is one skill's noun and the other's adjective, and its five adaptations
 * are assembled from the two kits' blocks by the fixed {@link Recipe}s. Pair-specific content is optional and
 * registered separately, with {@code ContentRegistrar.signature} and {@code ContentRegistrar.pairText}.
 *
 * <p>What a kit holds:
 *
 * <ul>
 *   <li>the six blocks, one per {@link BlockSlot}, and whether the Trigger is a fight or a work action;</li>
 *   <li>the words for the mutation's name, by default the messages {@code <skill key>.mutated.noun} and
 *       {@code .mutated.adjective};</li>
 *   <li>the upgraded adaptations a player can learn on a mutated line that contains the skill;</li>
 *   <li>four sounds and a tint, mixed with the other parent's when a mutated adaptation fires;</li>
 *   <li>the Barter gestures the skill's own abilities already use.</li>
 * </ul>
 *
 * <p>Each block's phrase is the message {@code <skill key>.block.<slot>}; the generated descriptions join the
 * phrases of both parents. The emblem and trim drawn on mutated tiles come from the addon's asset bundle
 * ({@code textures/gui/emblem/<skill>.png}, {@code textures/gui/trim/<skill>.png}) and are generated from the tint
 * when absent.
 *
 * <p>Experimental: the kit contract, the block, upgrade, signature and tool types and the five recipes may still
 * change in a minor release while the mutation system is tuned. What runs today is complete: every type here is
 * implemented by the engine, the same way for built-in and addon skills.
 */
@ApiStatus.Experimental
public final class MutationKit {
  private final Text noun;
  private final Text adjective;
  private final TriggerKind triggerKind;
  private final Map<BlockSlot, Supplier<? extends MutationBlock<?>>> blocks;
  private final List<UpgradeDeclaration> upgrades;
  private final SoundPalette sounds;
  private final Rgb tint;
  private final Set<BarterGesture> reservedGestures;

  private MutationKit(Builder builder) {
    this.noun = builder.noun;
    this.adjective = builder.adjective;
    this.triggerKind = Objects.requireNonNull(builder.triggerKind, "A kit needs a trigger");
    this.sounds = Objects.requireNonNull(builder.sounds, "A kit needs sounds");
    this.tint = builder.tint;
    for (BlockSlot slot : BlockSlot.values()) {
      if (!builder.blocks.containsKey(slot)) {
        throw new IllegalStateException("A kit needs all six blocks; missing " + slot);
      }
    }
    this.blocks = Map.copyOf(builder.blocks);
    this.upgrades = List.copyOf(builder.upgrades);
    this.reservedGestures = Set.copyOf(builder.reservedGestures);
  }

  /** An empty builder. */
  public static Builder builder() {
    return new Builder();
  }

  /** The noun the skill gives a mutation it leads; empty for the conventional message. */
  public Optional<Text> noun() {
    return Optional.ofNullable(noun);
  }

  /** The adjective the skill gives a mutation it joins as partner; empty for the conventional message. */
  public Optional<Text> adjective() {
    return Optional.ofNullable(adjective);
  }

  /** Whether the skill's Trigger is a fight action, a work action or either. */
  public TriggerKind triggerKind() {
    return triggerKind;
  }

  /** The factory of the block in a slot. */
  public Supplier<? extends MutationBlock<?>> block(BlockSlot slot) {
    return blocks.get(Objects.requireNonNull(slot, "slot"));
  }

  /** The upgraded adaptations the kit declares, in declaration order. */
  public List<UpgradeDeclaration> upgrades() {
    return upgrades;
  }

  /** The skill's four mutation sounds. */
  public SoundPalette sounds() {
    return sounds;
  }

  /** The colour the skill gives mutated tiles; empty for the skill's own colour. */
  public Optional<Rgb> tint() {
    return Optional.ofNullable(tint);
  }

  /** The Barter gestures the skill's own abilities use; a pair's Barter avoids them. */
  public Set<BarterGesture> reservedGestures() {
    return reservedGestures;
  }

  /**
   * Builds a {@link MutationKit}. A kit needs a Trigger, both Strikes, a Boon, a Fuel, a State and sounds; each block
   * slot may be set once, and setting one twice throws {@link IllegalStateException}.
   */
  public static final class Builder {
    private final Map<BlockSlot, Supplier<? extends MutationBlock<?>>> blocks = new EnumMap<>(BlockSlot.class);
    private final List<UpgradeDeclaration> upgrades = new ArrayList<>();
    private final Set<BarterGesture> reservedGestures = EnumSet.noneOf(BarterGesture.class);
    private Text noun;
    private Text adjective;
    private TriggerKind triggerKind;
    private SoundPalette sounds;
    private Rgb tint;

    private Builder() {
    }

    /** Names the mutation words explicitly instead of by the conventional keys. */
    public Builder words(Text noun, Text adjective) {
      this.noun = Objects.requireNonNull(noun, "noun");
      this.adjective = Objects.requireNonNull(adjective, "adjective");
      return this;
    }

    /** Sets the Trigger block and whether it is a fight action, a work action or either. */
    public Builder trigger(TriggerKind kind, Supplier<? extends TriggerBlock<?>> factory) {
      this.triggerKind = Objects.requireNonNull(kind, "kind");
      return block(BlockSlot.TRIGGER, factory);
    }

    /** Sets the fight Strike and the work Strike. */
    public Builder strikes(Supplier<? extends StrikeBlock<?>> fight, Supplier<? extends StrikeBlock<?>> work) {
      block(BlockSlot.STRIKE_FIGHT, fight);
      return block(BlockSlot.STRIKE_WORK, work);
    }

    /** Sets the Boon block. */
    public Builder boon(Supplier<? extends BoonBlock<?>> factory) {
      return block(BlockSlot.BOON, factory);
    }

    /** Sets the Fuel block. */
    public Builder fuel(Supplier<? extends FuelBlock<?>> factory) {
      return block(BlockSlot.FUEL, factory);
    }

    /** Sets the State block. */
    public Builder state(Supplier<? extends StateBlock<?>> factory) {
      return block(BlockSlot.STATE, factory);
    }

    /**
     * Declares an upgraded adaptation that stands in for {@code sources}, adaptations of the same skill, and keeps
     * the code of those in {@code fuses} running at the level it lends; see {@link UpgradeDeclaration}.
     */
    public Builder upgrade(Id id, Supplier<? extends SkillUpgrade<?>> factory, List<Id> sources, Set<Id> fuses) {
      upgrades.add(new UpgradeDeclaration(id, factory, sources, fuses, MutationPrice.UPGRADED));
      return this;
    }

    /** As {@link #upgrade}, for an upgrade that hands the player a craftable tool; it is priced a little higher. */
    public Builder toolUpgrade(Id id, Supplier<? extends SkillUpgrade<?>> factory, List<Id> sources, Set<Id> fuses) {
      upgrades.add(new UpgradeDeclaration(id, factory, sources, fuses, MutationPrice.TOOL));
      return this;
    }

    /** Sets the skill's mutation sounds; required. */
    public Builder sounds(SoundPalette sounds) {
      this.sounds = Objects.requireNonNull(sounds, "sounds");
      return this;
    }

    /** Sets the colour of mutated tiles; without it the skill's own colour is used. */
    public Builder tint(Rgb tint) {
      this.tint = Objects.requireNonNull(tint, "tint");
      return this;
    }

    /** Marks gestures one of the skill's own abilities fires on, so no Barter of this skill uses them. */
    public Builder reserve(BarterGesture first, BarterGesture... more) {
      reservedGestures.add(first);
      reservedGestures.addAll(Set.of(more));
      return this;
    }

    /**
     * The finished kit.
     *
     * @throws NullPointerException  when no Trigger or no sounds were set
     * @throws IllegalStateException when one of the six blocks is missing
     */
    public MutationKit build() {
      return new MutationKit(this);
    }

    private Builder block(BlockSlot slot, Supplier<? extends MutationBlock<?>> factory) {
      if (blocks.put(slot, Objects.requireNonNull(factory, "factory")) != null) {
        throw new IllegalStateException("A kit has one block per slot; " + slot + " is set twice");
      }
      return this;
    }
  }
}
