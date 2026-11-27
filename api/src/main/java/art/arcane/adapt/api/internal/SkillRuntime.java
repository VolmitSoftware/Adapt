package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.LearnerPulse;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.api.mutation.MutationBlock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.LongSupplier;

/** What the engine does for one attached skill. */
@ApiStatus.Internal
public interface SkillRuntime extends ComponentRuntime {
  /** The adaptations registered under the skill and usable on this server, in registration order. */
  List<Adaptation<?>> adaptations();

  List<Adaptation<?>> upgrades();

  List<MutationBlock<?>> blocks();

  boolean hasUsePermission(PlayerRef player);

  boolean shouldSkipPlayer(PlayerRef player);

  void withPlayer(PlayerRef player, @Nullable Cancellable cancellable, Runnable action);

  /** Pulses each online player with loaded data on their thread until the skill unregisters. */
  void pulseLearners(LongSupplier cadenceMillis, LearnerPulse pulse);

  boolean shouldSkipWorld(WorldRef world);

  boolean isWorldBlacklisted(PlayerRef player);

  void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey, boolean silent, boolean visual);

  void spatialXp(Loc at, double amount, int radius, long durationMillis);

  String emoji();

  String localizedName();

  String description();

  String displayName(int level);

  String shortName();
}
