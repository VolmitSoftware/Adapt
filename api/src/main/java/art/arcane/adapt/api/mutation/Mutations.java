package art.arcane.adapt.api.mutation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * What blocks, upgrades and the originals they reuse ask the mutation engine: whether an action is farmed, and what
 * level an upgrade lends an original the player no longer holds.
 *
 * <p>When a player mutates a skill, an upgrade stands in for some of the skill's originals, its sources, and fuses
 * some of those: a fused original's own code keeps running for that player, at a level the upgrade lends. Code that
 * a source unlocked reads its level through {@link #level} instead of its own level alone.
 */
@ApiStatus.Experimental
public final class Mutations {
  private Mutations() {
  }

  /** A spawner, egg, bucket or bred mob, a minion, a grave mob, or the player's own pet. */
  public static boolean farmed(PlayerRef player, @Nullable EntityRef entity) {
    return entity != null && Engine.bridge().mutations().farmed(player, entity);
  }

  /** A block a player placed, one broken and replaced shortly before, or a crop grown with bone meal. */
  public static boolean farmed(BlockRef block) {
    return Engine.bridge().mutations().farmed(block);
  }

  /** A crop grown with bone meal or by Adapt, or a placed plant that can be placed already at its last stage. */
  public static boolean farmedHarvest(BlockRef crop) {
    return Engine.bridge().mutations().farmedHarvest(crop);
  }

  /** Whether any upgrade of the original's skill fuses it; see {@link UpgradeDeclaration#fuses()}. */
  public static boolean fused(Adaptation<?> original) {
    return Engine.bridge().mutations().fused(original);
  }

  /**
   * The level {@code original} works at: its own level as {@code levelOf} reads it, or the highest level an upgrade
   * it is a source of lends it, reading each upgrade's level with the same function.
   */
  public static int level(Adaptation<?> original, ToIntFunction<Adaptation<?>> levelOf) {
    return level(original, levelOf.applyAsInt(original), levelOf);
  }

  /** {@code own} when it is above 0, otherwise the highest level an upgrade it is a source of lends the original. */
  public static int level(Adaptation<?> original, int own, ToIntFunction<Adaptation<?>> upgradeLevel) {
    return own > 0 ? own : Engine.bridge().mutations().lentLevel(original, upgradeLevel);
  }

  /** The original's block context, or one built from a fusing upgrade's context with the lent level. */
  public static Adaptation.@Nullable BlockActionContext context(
      Adaptation<?> original, Adaptation.@Nullable BlockActionContext own,
      Function<Adaptation<?>, Adaptation.BlockActionContext> upgradeContext) {
    return own != null ? own : Engine.bridge().mutations().lentContext(original, upgradeContext);
  }

  /** Tells each held upgrade that fuses {@code original} that the original landed an effect for the player. */
  public static void landed(Adaptation<?> original, PlayerRef player) {
    Engine.bridge().mutations().landed(original, player);
  }

  /** The multiplier a held fusing upgrade puts on what {@code original} grants the player; 1 when none is held. */
  public static double strength(Adaptation<?> original, PlayerRef player) {
    return Engine.bridge().mutations().strength(original, player);
  }

  /** The registered mutation tool this stack is, if any. */
  public static Optional<MutationTool<?>> tool(@Nullable ItemRef item) {
    return item == null || item.isEmpty() ? Optional.empty()
        : Optional.ofNullable(Engine.bridge().mutations().tool(item));
  }

  /** The registered mutation tool with this item id, if any. */
  public static Optional<MutationTool<?>> tool(Id id) {
    return Optional.ofNullable(Engine.bridge().mutations().tool(Objects.requireNonNull(id, "id")));
  }

  /** Whether the stack is a registered mutation tool. */
  public static boolean isTool(@Nullable ItemRef item) {
    return tool(item).isPresent();
  }

  /**
   * Whether {@code recipe} makes a mutation tool: a registered tool's recipe, or any recipe keyed below a tool id
   * ({@code <owner>/tool/<name>/...}), registered on this server or not.
   */
  public static boolean isToolRecipe(@Nullable Id recipe) {
    return recipe != null && Engine.bridge().mutations().isToolRecipe(recipe);
  }
}
