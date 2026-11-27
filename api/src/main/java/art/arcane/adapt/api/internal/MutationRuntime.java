package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** What the mutation engine does for blocks, upgrades and signatures. */
@ApiStatus.Internal
public interface MutationRuntime {
  void fire(TriggerBlock<?> trigger, PlayerRef player, @Nullable EntityRef target, @Nullable Loc at, boolean slain,
            boolean farmed);

  boolean fire(Signature<?> signature, PlayerRef player, @Nullable EntityRef target, @Nullable Loc at,
               boolean farmed);

  /** The namespace of the addon whose registration is building a signature for the slot right now. */
  String signatureOwner(MutationSlot slot);

  boolean parentsEnabled(SkillPair pair);

  /** Pays the partner skill's Fuel for the upgrade and returns the overcharge strength, or 1 when nothing was paid. */
  double overcharge(SkillUpgrade<?> upgrade, PlayerRef player);

  List<Id> sources(SkillUpgrade<?> upgrade);

  Set<Id> fuses(SkillUpgrade<?> upgrade);

  boolean farmed(PlayerRef player, EntityRef entity);

  boolean farmed(BlockRef block);

  boolean farmedHarvest(BlockRef crop);

  boolean fused(Adaptation<?> original);

  int lentLevel(Adaptation<?> original, ToIntFunction<Adaptation<?>> upgradeLevel);

  Adaptation.@Nullable BlockActionContext lentContext(
      Adaptation<?> original, Function<Adaptation<?>, Adaptation.BlockActionContext> upgradeContext);

  void landed(Adaptation<?> original, PlayerRef player);

  double strength(Adaptation<?> original, PlayerRef player);

  /** The skill's registered kit, or the kit Adapt generates for a skill that registered none. */
  MutationKit kit(Skill<?> skill);

  /** {@link #kit(Skill)} of a registered skill by id; throws for an id no registered skill has. */
  MutationKit kit(Id skill);

  /** The server's overcharge multiplier. */
  double overchargeStrength();

  /** The recipe's cooldown at {@code level}: by level for Graft and Echo, fixed for Convergence and Barter. */
  long recipeCooldownMillis(Recipe recipe, int level);

  /** The stat line naming the level a fused original works at. */
  String grantedLine(Adaptation<?> original, int level);

  /** What of {@code amount} an upgrade may still pay the player this minute. */
  double claimXp(SkillUpgrade<?> upgrade, PlayerRef player, double amount);

  /** A mob that is neither farmed nor a minion nor the player's pet, or another player. */
  boolean naturalMob(PlayerRef player, EntityRef mob);

  /** {@code damager}, or its shooter, is a natural mob or a player. */
  boolean realAttacker(PlayerRef player, EntityRef damager);

  boolean cue(Adaptation<?> source, SoundPalette palette, PlayerRef player, MutationCue cue);

  LingeringSound linger(Adaptation<?> source, SoundPalette palette, EntityRef entity);

  @Nullable MutationTool<?> tool(ItemRef item);

  @Nullable MutationTool<?> tool(Id id);

  boolean isToolRecipe(Id recipe);
}
