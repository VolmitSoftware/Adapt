package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** What the engine does for any attached skill or adaptation: config, ticking, lifetime and shared helpers. */
@ApiStatus.Internal
public interface ComponentRuntime {
  /** The live config, loaded from disk on the first call. Null when no configuration class was registered. */
  @Nullable Object config(@Nullable Class<?> type);

  /** The namespace of the addon whose {@code register} built this component. */
  String owner();

  long interval();

  void setInterval(long millis);

  void burst(int ticks);

  void stopBursting();

  boolean isBursting();

  boolean isRegistered();

  /** Retires the component: no more ticks or events. Safe to call twice. */
  void unregister();

  boolean particlesEnabled();

  boolean soundsEnabled();

  /** The profile of an online player whose data has loaded, or null. */
  @Nullable PlayerProfile profile(@Nullable PlayerRef player);

  void addStat(PlayerRef player, String stat, double amount);

  double stat(PlayerRef player, String stat);

  boolean grantOnce(PlayerRef player, String advancementKey);

  /** A per-player map the engine clears of players who leave. */
  <V> Map<UUID, V> playerState();

  Cooldowns cooldowns();

  double materialValue(Id material);

  double blockXpMultiplier(BlockRef block);

  double armorValue(PlayerRef player);

  void damageItem(PlayerRef player, boolean offHand, int damage);

  void addPotionStacks(PlayerRef player, Id effect, int amplifier, int durationTicks, boolean overlap);

  boolean addPotionStacksNow(PlayerRef player, Id effect, int amplifier, int durationTicks, boolean overlap);

  boolean canSnatchItem(PlayerRef player, ItemEntityRef item);

  boolean giveItem(PlayerRef player, EntityRef itemEntity, ItemRef item);

  void giveItem(PlayerRef player, ItemRef item);

  void giveExp(PlayerRef player, int amount);

  @Nullable BlockFace blockFace(PlayerRef player, int maxRange);

  FxEmitter fx(Loc location, FxPriority priority);

  FxEmitter fx(EntityRef entity, FxPriority priority);

  FxTimeline timeline(Loc location);

  FxTimeline timeline(EntityRef entity);

  void sfx(Loc location, Id sound, float volume, float pitch);
}
