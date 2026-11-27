package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The hand-made effect of one mutation slot, replacing the adaptation its recipe would generate. Registered with
 * {@code ContentRegistrar.signature} by any addon, for a pair of any addons' skills.
 *
 * <p>A signature listens, ticks and loads config like an adaptation, but no player learns it: its level for a
 * player is their level in the mutated adaptation it replaces, which keeps the recipe's price and power slot.
 *
 * <p>A subclass has a constructor taking the {@link MutationSlot}, which it passes to {@code super}, registers a
 * config that extends {@link Config}, and calls {@link #fire} from its {@code @Handle} methods once its action
 * happened. The engine then checks the level, the cooldown and the farm rule, runs {@link #run} and plays the
 * pair's sounds. A Barter or Attunement signature needs no handler: the engine fires it on the pair's gesture or
 * on its interval.
 *
 * <p>Its messages are in the registering addon's bundle under {@link TextKeys#pair}: {@code .name},
 * {@code .description} and the stat labels {@code .lore1}, {@code .lore2}.
 *
 * @param <C> the signature's config
 */
@ApiStatus.Experimental
public abstract class Signature<C extends Signature.Config> extends SimpleAdaptation<C> {
  private final MutationSlot slot;
  private final List<MutationTool<?>> tools = new ArrayList<>(1);

  /**
   * @param slot the mutation slot whose generated adaptation this signature replaces
   */
  protected Signature(MutationSlot slot) {
    super(id(slot));
    this.slot = slot;
    setLocalizationKey(TextKeys.pair(Engine.bridge().mutations().signatureOwner(slot), slot));
  }

  /**
   * The id a signature registers under: the mutated skill's id followed by {@code /signature/<recipe>}, such as
   * {@code adapt:mutated/swords/adapt/stealth/signature/convergence}, whichever addon registers it.
   */
  public static Id id(MutationSlot slot) {
    return slot.pair().child("signature/" + slot.recipe().name().toLowerCase(Locale.ROOT));
  }

  /** The effect. {@code use.adaptation()} is the mutated adaptation it replaces, {@code use.level()} its level. */
  public abstract Outcome run(BlockUse use);

  /** Whether the effect gives items, XP, healing, repairs or growth; then it does not run for a farmed action. */
  public boolean paysOut() {
    return false;
  }

  /**
   * How long after a landed run it waits; by default the replaced recipe's cooldown from the server's mutation
   * config: by level for Graft and Echo, fixed for Convergence and Barter, none for Attunement.
   */
  public long cooldownMillis(int level) {
    return Engine.bridge().mutations().recipeCooldownMillis(slot.recipe(), level);
  }

  /** The sounds of the pair's lead skill. */
  public final SoundPalette getSoundPalette() {
    return Engine.bridge().mutations().kit(slot.pair().lead()).sounds();
  }

  /** Stat lines shown on the mutated adaptation it replaces; label them with {@code statLore(lines, value, n)}. */
  public void addSignatureStats(int level, StatLines lines) {
  }

  /**
   * The action happened: the engine runs the effect for the player if they hold the pair's mutation and this slot's
   * adaptation at an active level, it is ready, and (for an effect that {@linkplain #paysOut pays out}) neither
   * {@code farmed} nor a farmed target. Nothing runs while another mutation effect of the player is running. A
   * landed run starts the cooldown, plays the pair's cue, and for a lasting outcome the lingering tick.
   *
   * @return true when the effect ran and landed
   */
  protected final boolean fire(PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean farmed) {
    return usable() && Engine.bridge().mutations().fire(this, player, target, at, farmed);
  }

  /** The mutation slot this signature fills. */
  public final MutationSlot getSlot() {
    return slot;
  }

  /** The replaced recipe's level count; a config cannot change it. */
  @Override
  public final int getMaxLevel() {
    return slot.recipe().price().maxLevel();
  }

  /** Whether it may run now: enabled in its config and both parent skills registered and enabled. */
  public final boolean usable() {
    return isEnabled() && Engine.bridge().mutations().parentsEnabled(slot.pair());
  }

  @Override
  public final void addStats(int level, StatLines lines) {
  }

  /** Signatures never pay skill XP, not even through an original's code they reuse. */
  @Override
  public final double payableXp(PlayerRef player, double amount) {
    return 0D;
  }

  /** Registers a tool this signature hands out; its recipes are gated at the tool's required level. */
  protected final <T extends MutationTool<?>> T registerTool(T tool) {
    tools.add(tool);
    registerItem(tool.item());
    for (RecipeSpec recipe : tool.recipes()) {
      registerRecipe(recipe.withRequiredLevel(Math.max(recipe.requiredLevel(), tool.requiredLevel())));
    }
    return tool;
  }

  /** The tools this signature registered, in registration order. */
  public final List<MutationTool<?>> getTools() {
    return List.copyOf(tools);
  }

  /** Every signature's config extends this. */
  public static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 0;
      initialCost = 0;
    }
  }
}
