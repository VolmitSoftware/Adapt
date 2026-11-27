package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationRequirement;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** What the engine does for one attached adaptation, mutation block, upgrade or signature. */
@ApiStatus.Internal
public interface AdaptationRuntime extends ComponentRuntime {
  /**
   * The skill the adaptation was registered under; null while that skill is not a public type (a built-in skill
   * before the base-class switch).
   */
  @Nullable Skill<?> skill();

  /** The id of the skill the adaptation is built for, from the attach on. */
  Id skillId();

  /** The requirements parsed from the live config. */
  List<AdaptationRequirement> requirements();

  int level(@Nullable PlayerRef player);

  int level(@Nullable PlayerProfile profile);

  /**
   * The level when every gate passes, else 0: learned, enabled, world and game mode allowed, no usage conflict,
   * the world policy of {@code gate} at {@code location} or against {@code target}, {@code requirement}, and
   * survival or adventure mode when {@code survivalOnly}.
   */
  int activeLevel(@Nullable PlayerRef player, LevelGate gate, @Nullable Loc location, @Nullable EntityRef target,
                  @Nullable Predicate<PlayerRef> requirement, boolean survivalOnly);

  boolean hasOnlineLearner();

  List<PlayerProfile> learnedCandidates(long now);

  void withPlayerThread(PlayerRef player, @Nullable Cancellable cancellable, Runnable action);

  void withAdaptedPlayer(PlayerRef player, @Nullable Cancellable cancellable, Runnable action);

  void withActiveLevel(PlayerRef player, @Nullable Cancellable cancellable, IntConsumer action);

  <E extends Enum<E>> E preference(@Nullable PlayerRef player, PlayerPreference<E> preference);

  <E extends Enum<E>> E preferenceFor(int level, @Nullable String saved, PlayerPreference<E> preference);

  boolean isPlayerEnabled(@Nullable PlayerRef player);

  /** Pays {@code amount} plus the usage baseline through the skill, under the adaptation's reward key. */
  void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey, boolean silent);

  void awardUsageBaselineXp(PlayerRef player, int level);

  <F> @Nullable F storage(PlayerRef player, String key, @Nullable F fallback);

  boolean setStorage(PlayerRef player, String key, @Nullable Object value);

  boolean canUse(PlayerRef player);

  boolean hasUsePermission(PlayerRef player);

  boolean allows(ProtectionCheck check, PlayerRef player, @Nullable Loc location, @Nullable EntityRef victim);

  boolean canDamageTarget(PlayerRef attacker, EntityRef target);

  boolean isProtectedFriendly(@Nullable PlayerRef actor, EntityRef target);

  boolean isProtectedFriendlyOwned(@Nullable UUID actor, EntityRef target);

  Adaptation.@Nullable MeleeContext meleeContext(EntityDamageByEntityEvent event,
                                                  @Nullable Predicate<ItemRef> mainHand);

  Adaptation.@Nullable AttackContext attackContext(EntityDamageByEntityEvent event,
                                                    @Nullable Predicate<ItemRef> mainHand);

  Adaptation.@Nullable ProjectileContext projectileContext(EntityDamageByEntityEvent event,
                                                            @Nullable Predicate<ProjectileRef> projectile);

  boolean charge(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit, int amount,
                 AbilityDefaultCost defaultCost);

  AbilityCharge chargeDeferred(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit,
                               int amount, AbilityDefaultCost defaultCost);

  boolean settle(UUID activationId);

  boolean refund(UUID activationId, AbilityRefundReason reason);

  /** Pulses each online learner of the adaptation on their thread until the adaptation unregisters. */
  void pulseOwners(LongSupplier cadenceMillis, Consumer<PlayerRef> pulse);

  void pulseOwners(LongSupplier cadenceMillis, BooleanSupplier extraDemand, Predicate<UUID> interest,
                   Consumer<PlayerRef> pulse);

  int ownActiveLevel(@Nullable PlayerRef player);

  boolean confirm(PlayerRef player, String action, @Nullable ItemRef[] items);

  /** Damages the player as a health cost, without knockback. */
  void applyPlayerDamage(PlayerRef player, double amount);

  @Nullable String localizedName();

  @Nullable String localizedShortName();

  String description();

  String displayName(int level);

  String shortName(int level);

  /** One stat line in the server's stat format; a floating-point value is cut to two decimals. */
  String statLine(String prefix, Object value, String labelFormat, String label);
}
