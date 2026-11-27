package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.chronos.ChronosRewind;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chronos with Rift, replacing Convergence: shortly after a pearl, chorus fruit or blink, the clock gesture takes you
 * back to where you left, with at least the health you had there. Never into a place you may not enter.
 */
public final class RecallTheBlink extends Signature<RecallTheBlink.Config> {
  private final Map<UUID, Departure> departures = new ConcurrentHashMap<>();

  public RecallTheBlink(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_pearl"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.duration(windowMillis(level), 0), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef player = e.player();
    if (e.from() != null && RiftTeleports.byRift(e) && getActiveLevel(player) > 0) {
      departures.put(player.uuid(), new Departure(e.from(), player.health(), System.currentTimeMillis()));
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (SignatureKit.clockSwap(e) && departures.containsKey(player.uuid()) && getActiveLevel(player) > 0) {
      SignatureKit.settle(e, fire(player, null, player.location(), false));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    departures.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    departures.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Departure departure = departures.get(player.uuid());
    if (departure == null || System.currentTimeMillis() - departure.at() > windowMillis(use.level())) {
      departures.remove(player.uuid());
      return Outcome.MISSED;
    }
    Loc back = departure.from();
    if (!back.sameWorld(player.location()) || !canInteract(player, back) || !checkRegion(player, back)) {
      ChronosSoundFX.playClockReject(player);
      SignatureKit.refuse(player);
      return Outcome.MISSED;
    }
    departures.remove(player.uuid(), departure);
    Loc left = player.location();
    CompletableFuture<Boolean> teleport;
    try {
      teleport = player.teleport(back, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptApi.log().error("RecallTheBlink failed", error);
      return Outcome.MISSED;
    }
    teleport.whenComplete((Boolean landed, Throwable failure) -> AdaptApi.scheduler().runEntity(player, () -> {
      if (failure != null || !Boolean.TRUE.equals(landed) || !player.isOnline() || player.isDead()) {
        return;
      }
      player.setHealth(Math.min(player.maxHealth(), Math.max(player.health(), departure.health())));
      ChronosRewind.departFx(this, left, back);
      ChronosRewind.arriveFx(this, player);
      ChronosSoundFX.playRewindFinish(player);
    }));
    return Outcome.INSTANT;
  }

  long windowMillis(int level) {
    return (long) (SignatureKit.scaled(level, getMaxLevel(), getConfig().windowSecondsAtFirstLevel,
        getConfig().windowSecondsAtTopLevel) * 1000D);
  }

  private record Departure(Loc from, double health, long at) {
  }

  @ConfigDescription("Shortly after a rift teleport, the clock gesture takes you back to where you left.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Seconds after a rift teleport you can still go back, at level 1.", impact = "Higher values give more time to decide.")
    double windowSecondsAtFirstLevel = 3;
    @ConfigDoc(value = "Seconds after a rift teleport you can still go back, at the top level.", impact = "Higher values give more time to decide.")
    double windowSecondsAtTopLevel = 5;
    @ConfigDoc(value = "Cooldown after a recall, in milliseconds.", impact = "Higher values make recalls rarer.")
    long cooldownMillis = 30_000L;
  }
}
