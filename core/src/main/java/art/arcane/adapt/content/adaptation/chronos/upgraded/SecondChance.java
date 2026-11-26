package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.damage.PlayerDeathSave;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.chronos.ChronosBorrowedTime;
import art.arcane.adapt.content.adaptation.chronos.ChronosDebt;
import art.arcane.adapt.content.adaptation.chronos.ChronosGesture;
import art.arcane.adapt.content.adaptation.chronos.ChronosHourglass;
import art.arcane.adapt.content.adaptation.chronos.ChronosRecallHistory;
import art.arcane.adapt.content.adaptation.chronos.ChronosRewind;
import art.arcane.adapt.content.adaptation.chronos.ChronosSoundFX;
import art.arcane.adapt.content.adaptation.kinetics.KineticsMassShift;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Rewind, Instant Recall and Hourglass Guard fused: one charged rewind to where you stood seconds ago, with your health
 * and hunger from then. Sneak and swap hands to spend it, or a killing blow spends it for you, with the hourglass save
 * on top; the save leaves the health you had before the blow, at most half. Part of every hit is owed later, as
 * Borrowed Time owes it, and any rewind clears what is still owed.
 */
public class SecondChance extends SkillUpgrade<SecondChance.Config> implements PlayerDeathSave {
  private static final DataKey<String> DEBT_STAMP = DataKey.adapt("chronos_second_chance_debt", DataCodecs.STRING);

  private final ChronosRecallHistory history = new ChronosRecallHistory(playerState(), playerState());
  private final ChronosHourglass hourglass = new ChronosHourglass(this, playerState());
  private final Map<UUID, Rewind> pendingRewinds = playerState();
  private final ChronosDebt debt = new ChronosDebt(DEBT_STAMP, playerState(), playerState(), new ChronosDebt.Hooks() {
    @Override
    public void pay(PlayerRef player, double damage) {
      applyPlayerDamage(player, damage);
    }

    @Override
    public void deferred(PlayerRef player, double amount) {
      addStat(player, "chronos.second-chance.damage-deferred", amount);
    }

    @Override
    public void paid(PlayerRef player, double damage, boolean cleared) {
      fx(player.location().add(0D, 1D, 0D), FxPriority.TRAIL).particle(Particles.SOUL, cleared ? 6 : 2, 0D, 0.1D, 0D, 0.2D, 0.02D);
    }
  });

  public SecondChance() {
    super("upgraded-chronos-second-chance");
    registerConfiguration(Config.class);
    needs(PlatformNeed.emulatedFeature(EngineFeature.PLAYER_INPUT));
    setIcon(Id.minecraft("recovery_compass"));
    setInterval(1000);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY, ChronosRewind.CLOCK_SOUNDS);
  }

  @Override
  public int deathSavePriority(PlayerRef player) {
    return preference(player, CommonPreferences.DEATH_SAVE_PRIORITY).ordinal();
  }

  @Override
  public int defaultOrder() {
    return 1;
  }

  @Override
  public boolean protectsFromDamage(EntityDamageEvent event) {
    return event.entity() instanceof PlayerRef player && hourglass.isInvulnerable(player.uuid(), M.ms());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getRewindMillis(level), 1), 1);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    statLore(v, Form.duration(getConfig().invulnerabilityMillis, 1), 3);
    statLore(v, Form.pc(getDeferShare(level), 0), 4);
    statLore(v, Form.pc(getConfig().saveHealthShare, 0), 5);
  }

  @Override
  protected void onRuntimeActivated() {
    debt.start();
  }

  @Override
  public void unregister() {
    debt.stop();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!pendingRewinds.containsKey(p.uuid()) && getActiveLevel(p) > 0) {
      history.capture(p, getConfig().snapshotIntervalMillis, getRewindMillis(getMaxLevel()) + 2000L);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef p = e.player();
    if (!pendingRewinds.containsKey(p.uuid()) && e.to() != null && getActiveLevel(p) > 0) {
      history.reset(p, e.to());
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    history.forget(id);
    hourglass.forget(id);
    pendingRewinds.remove(id);
    debt.quit(e.player());
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    debt.restore(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    debt.cancel(e.player());
  }

  /** HIGH, ahead of the death saves; with Borrowed Time also active only the difference is held here. */
  @Handle(priority = EventPriority.HIGH)
  public void defer(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.isCancelled() || debt.isPaying()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }
    double share = getDeferShare(level);
    double borrowed = borrowedTimeShare(p);
    double own = borrowed >= share ? 0D : (share - borrowed) / (1D - borrowed);
    debt.defer(p, e, own, getConfig().deferMinimumDamage, getConfig().deferPaybackPulses);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void finishDamage(EntityDamageEvent e) {
    debt.finish(e);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking() || KineticsMassShift.claimsSwap(p)) {
      return;
    }
    Adaptation.BlockActionContext context = resolveInteractContext(p, p.location());
    if (context == null) {
      return;
    }

    ChronosGesture.use(e);
    long now = M.ms();
    long cooldownMillis = getCooldownMillis(context.level());
    ChronosRecallHistory.Snapshot snapshot = history.find(p, getRewindMillis(context.level()));
    if (snapshot == null || pendingRewinds.containsKey(p.uuid())
        || !DeathSaveCooldown.HOURGLASS_GUARD.activate(p, now, cooldownMillis)) {
      if (clockSounds(p)) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }
    AbilityGlyphs.cooldown(p.uuid(), this, now, cooldownMillis);
    rewind(p, new Rewind(snapshot, 0D));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  @Override
  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    long now = M.ms();
    if (hourglass.isInvulnerable(p.uuid(), now)) {
      e.setCancelled(true);
      return;
    }

    int level = getActiveLevel(p);
    if (p.health() - e.finalDamage() > 0D || level <= 0) {
      return;
    }

    long cooldownMillis = getCooldownMillis(level);
    if (!DeathSaveCooldown.HOURGLASS_GUARD.activate(p, now, cooldownMillis)) {
      return;
    }
    double savedHealth = Math.max(0.5D, Math.min(p.health(), p.maxHealth() * getConfig().saveHealthShare));
    hourglass.save(p, e, new ChronosHourglass.Save(savedHealth, getConfig().invulnerabilityMillis,
        cooldownMillis, getConfig().enemySlowRadius, getConfig().enemySlowTicks, getConfig().enemySlowAmplifier), now);
    LingeringSound lingering = linger(p);
    Tasks.runEntity(p, lingering::stop, (int) Math.max(1L, getConfig().invulnerabilityMillis / 50L));
    addStat(p, "chronos.second-chance.saves", 1);
    ChronosRecallHistory.Snapshot snapshot = history.find(p, getRewindMillis(level));
    if (snapshot != null) {
      rewind(p, new Rewind(snapshot, savedHealth));
    }
  }

  @Override
  public void onTick() {
    hourglass.prune(M.ms());
    debt.tick();
  }

  private void rewind(PlayerRef p, Rewind rewind) {
    UUID id = p.uuid();
    if (pendingRewinds.putIfAbsent(id, rewind) != null) {
      return;
    }

    Loc departure = p.location();
    Loc destination = ChronosRecallHistory.toLocation(rewind.snapshot(), p.world());
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error(error);
      teleport = null;
    }
    if (teleport == null) {
      pendingRewinds.remove(id, rewind);
      return;
    }

    teleport.whenComplete((Boolean success, Throwable failure) -> {
      if (!Tasks.runEntity(p, () -> finish(p, rewind, departure, destination,
          failure == null && Boolean.TRUE.equals(success)))) {
        pendingRewinds.remove(id, rewind);
      }
    });
  }

  private void finish(PlayerRef p, Rewind rewind, Loc departure, Loc destination, boolean landed) {
    if (!pendingRewinds.remove(p.uuid(), rewind) || !landed || !p.isOnline() || p.isDead()) {
      return;
    }

    ChronosRecallHistory.applySnapshotState(p, rewind.snapshot());
    if (rewind.savedHealth() > 0D) {
      p.setHealth(Math.min(p.maxHealth(), rewind.savedHealth()));
    }
    debt.cancel(p);
    ChronosBorrowedTime borrowed = borrowedTime();
    if (borrowed != null) {
      borrowed.cancelDebt(p);
    }
    ChronosRewind.departFx(this, departure, destination);
    ChronosRewind.arriveFx(this, p);
    if (clockSounds(p)) {
      ChronosSoundFX.playRewindFinish(p);
    }
    history.reset(p, p.location());
    addStat(p, "chronos.second-chance.rewinds", 1);
    cue(p, MutationCue.BOON);
  }

  private ChronosBorrowedTime borrowedTime() {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof ChronosBorrowedTime original) {
        return original;
      }
    }
    return null;
  }

  private double borrowedTimeShare(PlayerRef p) {
    ChronosBorrowedTime borrowed = borrowedTime();
    return borrowed == null ? 0D : Math.min(0.99D, borrowed.deferShare(p));
  }

  double getDeferShare(int level) {
    return Math.max(0D, Math.min(0.9D, getConfig().deferShareBase + (level * getConfig().deferSharePerLevel)));
  }

  private boolean clockSounds(PlayerRef p) {
    return preferenceEnabled(p, ChronosRewind.CLOCK_SOUNDS);
  }

  long getRewindMillis(int level) {
    return (long) ((getConfig().rewindSecondsBase + (level * getConfig().rewindSecondsPerLevel)) * 1000D);
  }

  long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  /** A rewind; {@code savedHealth} is the health a killing blow's save left, 0 for a rewind you chose. */
  private record Rewind(ChronosRecallHistory.Snapshot snapshot, double savedHealth) {
  }

  @ConfigDescription("One charged rewind to a few seconds ago: sneak and swap hands to use it, or a killing blow uses it for you.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "How far back the rewind goes before level scaling, in seconds.", impact = "Higher values rewind further.")
    double rewindSecondsBase = 3;
    @ConfigDoc(value = "Seconds added to the rewind per level.", impact = "Higher values rewind further at higher levels.")
    double rewindSecondsPerLevel = 1;
    @ConfigDoc(value = "Milliseconds between two recorded positions.", impact = "Lower values land the rewind more precisely at more cost.")
    int snapshotIntervalMillis = 100;
    @ConfigDoc(value = "Charge cooldown before level scaling, in milliseconds.", impact = "Higher values make the rewind rarer.")
    long cooldownMillisBase = 240000;
    @ConfigDoc(value = "Charge cooldown removed per level, in milliseconds.", impact = "Higher values recharge faster at higher levels.")
    long cooldownMillisPerLevel = 40000;
    @ConfigDoc(value = "Shortest charge cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent rewinds.")
    long minimumCooldownMillis = 90000;
    @ConfigDoc(value = "Most health a killing blow's save leaves, as a share of your maximum health; the save never leaves more than you had before the blow.", impact = "Higher values leave you healthier after a save.")
    double saveHealthShare = 0.5;
    @ConfigDoc(value = "Invulnerability after a killing blow is stopped, in milliseconds.", impact = "Higher values protect you longer.")
    long invulnerabilityMillis = 2500;
    @ConfigDoc(value = "Radius in blocks of the slow on nearby enemies after a killing blow.", impact = "Higher values slow enemies from further away; capped at 12.")
    double enemySlowRadius = 6;
    @ConfigDoc(value = "Duration of that slow, in ticks.", impact = "Higher values keep enemies slowed longer.")
    int enemySlowTicks = 60;
    @ConfigDoc(value = "Slowness amplifier on nearby enemies.", impact = "Higher values slow enemies more.")
    int enemySlowAmplifier = 2;
    @ConfigDoc(value = "Share of each hit owed later instead, before level scaling.", impact = "Higher values move more of each hit into later pulses.")
    double deferShareBase = 0.05;
    @ConfigDoc(value = "Share of each hit owed later added per level.", impact = "Higher values defer more at higher levels; capped at 0.9.")
    double deferSharePerLevel = 0.1;
    @ConfigDoc(value = "Smallest hit that is partly owed later.", impact = "Higher values leave small hits whole.")
    double deferMinimumDamage = 1;
    @ConfigDoc(value = "One-second pulses the owed damage is paid over.", impact = "Higher values spread the debt thinner over a longer time.")
    int deferPaybackPulses = 10;
  }
}
