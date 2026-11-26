package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.content.adaptation.chronos.ChronosAura;
import art.arcane.adapt.content.adaptation.chronos.ChronosBottle;
import art.arcane.adapt.content.adaptation.chronos.ChronosEffectStretch;
import art.arcane.adapt.content.adaptation.chronos.ChronosRestoration;
import art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Time in a Bottle, Accelerate and Overtime fused: carried chrono bottles hold and gather far more time, the passive
 * aura reaches further and hits harder, and beneficial potions on you last much longer. Sneak and right-click a block
 * with a clock to restore what explosions, fire and endermen destroyed around it, paid in stored bottle time.
 */
public class TimeLord extends SkillUpgrade<TimeLord.Config> {
  private static final ChronosTimeInABottle.Config DEFAULT_BOTTLE = new ChronosTimeInABottle.Config();
  private static final Id CLOCK = Id.minecraft("clock");
  private static final int REDO_PACE = 8;

  private final ChronosBottle bottle = new ChronosBottle(this, this::bottleConfig,
      () -> getConfig().bottleCapacityMultiplier, () -> getConfig().bottleChargeMultiplier,
      (PlayerRef p, ChronosBottle.TimeSpendResult result) -> {
        addStat(p, "chronos.time-lord.bottle-seconds-spent", result.spentSeconds());
        cue(p, MutationCue.WORK);
      }, playerState(), playerState());
  private final ChronosAura aura = new ChronosAura(this, new ChronosAura.Tuning() {
    @Override
    public long pulseIntervalMillis() {
      return getConfig().auraPulseMillis;
    }

    @Override
    public int maxPlayersPerPass() {
      return ChronosAura.HARD_MAX_PLAYERS_PER_PASS;
    }

    @Override
    public int maxSamplesPerPass() {
      return ChronosAura.HARD_MAX_SAMPLES_PER_PASS;
    }

    @Override
    public int samples(int level) {
      return getConfig().auraSamplesBase + (level * getConfig().auraSamplesPerLevel);
    }

    @Override
    public double radius(int level) {
      return getAuraRadius(level);
    }

    @Override
    public double radiusScale(PlayerRef player) {
      return 1D;
    }

    @Override
    public double growChance(int level) {
      return Math.min(1D, getConfig().auraGrowChanceBase + (level * getConfig().auraGrowChancePerLevel));
    }

    @Override
    public double cookBoost(int level) {
      return Math.min(0.9D, getConfig().auraCookBoostBase + (level * getConfig().auraCookBoostPerLevel));
    }

    @Override
    public boolean crops(PlayerRef player) {
      return true;
    }

    @Override
    public boolean furnaces(PlayerRef player) {
      return true;
    }

    @Override
    public boolean brewing(PlayerRef player) {
      return true;
    }

    @Override
    public void accelerated(PlayerRef player, int count) {
      addStat(player, "chronos.time-lord.blocks-accelerated", count);
    }
  }, playerState());
  private final ChronosRestoration restoration = new ChronosRestoration(this, new ChronosRestoration.Tuning() {
    @Override
    public long windowMillis(int level) {
      return getRestoreWindowMillis(level);
    }

    @Override
    public double radius(int level) {
      return getRestoreRadius(level);
    }

    @Override
    public long longestWindowMillis() {
      return getRestoreWindowMillis(getMaxLevel());
    }

    @Override
    public double recordRadius() {
      return getConfig().restoreRecordRadius;
    }

    @Override
    public int maxLoggedBlocks() {
      return getConfig().restoreMaxLoggedBlocks;
    }

    @Override
    public double chargeSeconds() {
      return Math.max(1D, getConfig().restoreChargeSeconds);
    }

    @Override
    public int blocksPerCharge() {
      return Math.max(1, getConfig().restoreBlocksPerCharge);
    }
  });
  private final ChronosEffectStretch stretch = new ChronosEffectStretch(this, playerState(), (PlayerRef p, int bonus) -> {
    addStat(p, "chronos.time-lord.seconds-extended", bonus / 20D);
    cue(p, MutationCue.BOON);
  });

  public TimeLord() {
    super("upgraded-chronos-time-lord");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("clock"));
    setInterval(1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getBottleCapacity(level), 0) + "s", 1);
    statLore(v, Form.f(getAuraRadius(level), 1), 2);
    statLore(v, Form.pc(getPotionExtension(level), 0), 3);
    statLore(v, Form.f(getRestoreRadius(level), 0), 4);
    statLore(v, Form.duration(getRestoreWindowMillis(level), 0), 5);
    statLore(v, Math.max(1, getConfig().restoreBlocksPerCharge), 6);
    statLore(v, C.RED, "- ", Form.f(getConfig().restoreChargeSeconds, 0) + "s", 7);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    bottle.tick();
    aura.tick();
    restoration.tick(M.ms());
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    bottle.forget(e);
    aura.forget(e.player().uuid());
    stretch.forget(e.player().uuid());
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    bottle.interact(e);
    restore(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityExplodeEvent e) {
    restoration.exploded(e.blocks(), null, M.ms());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockExplodeEvent e) {
    restoration.exploded(e.blocks(), e.block(), M.ms());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBurnEvent e) {
    restoration.burned(e.block(), M.ms());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityChangeBlockEvent e) {
    restoration.changed(e.entity(), e.block(), e.to(), M.ms());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemSpawnEvent e) {
    restoration.itemSpawned(e.item(), e.location(), M.ms());
  }

  private void restore(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    BlockRef clicked = e.clickedBlock();
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND || clicked == null
        || !p.isSneaking() || e.useBlock() == UseResult.DENY || !CLOCK.equals(p.inventory().mainHand().type())) {
      return;
    }
    Adaptation.BlockActionContext context = resolveInteractContext(p, clicked.location());
    if (context == null || restoration.isRestoring(p) || BuildBatches.get().isRunning(p)) {
      return;
    }
    e.setCancelled(true);
    int level = context.level();
    restoration.restore(p, clicked, level, M.ms(), (ChronosRestoration.Outcome outcome) -> {
      boolean redoing = BuildBatches.get().redo(p, this, clicked, getRestoreRadius(level), REDO_PACE,
          (BuildResult result) -> redone(p, clicked, result));
      if (!redoing || outcome.shortfall() != ChronosRestoration.Shortfall.NOTHING) {
        report(p, clicked, outcome);
      }
    });
  }

  /** A build the player undid with Long Undo, placed back by the same restoration. */
  private void redone(PlayerRef p, BlockRef center, BuildResult result) {
    if (result.placed() <= 0) {
      return;
    }
    addStat(p, "chronos.time-lord.blocks-restored", result.placed());
    ChronosTimeInABottle.blockUseFx(this, center, center.center());
    AdaptHud.actionBar(p, C.AQUA + AdaptLanguage.text(ChronosMessages.TIME_LORD_RESTORED,
        trusted("count", String.valueOf(result.placed()))));
  }

  private void report(PlayerRef p, BlockRef center, ChronosRestoration.Outcome outcome) {
    if (outcome.restored() > 0) {
      addStat(p, "chronos.time-lord.blocks-restored", outcome.restored());
      cue(p, MutationCue.WORK);
      ChronosTimeInABottle.blockUseFx(this, center, center.center());
    }
    TextKey message = switch (outcome.shortfall()) {
      case NOTHING -> ChronosMessages.TIME_LORD_RESTORE_NOTHING;
      case NO_TIME -> ChronosMessages.TIME_LORD_RESTORE_NO_TIME;
      case DROPS_MISSING -> ChronosMessages.TIME_LORD_RESTORE_DROPS_MISSING;
      case NONE -> outcome.stop() == BuildResult.Stop.PROTECTED ? ChronosMessages.TIME_LORD_RESTORE_PROTECTED : null;
    };
    String restored = outcome.restored() > 0
        ? AdaptLanguage.text(ChronosMessages.TIME_LORD_RESTORED, trusted("count", String.valueOf(outcome.restored())))
        : "";
    if (message == null) {
      AdaptHud.actionBar(p, C.AQUA + restored);
    } else if (restored.isEmpty()) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(message));
    } else {
      AdaptHud.actionBar(p, C.AQUA + restored + C.GRAY + " · " + C.RED + AdaptLanguage.text(message));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEntityEvent e) {
    bottle.interactEntity(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPotionEffectEvent e) {
    stretch.stretch(e, (PlayerRef p, Id type) -> true, new ChronosEffectStretch.Stretch(this::getPotionExtension,
        getConfig().potionMaxBonusTicks, getConfig().potionMinimumDurationTicks, getConfig().potionMaximumBaseDurationTicks));
  }

  private ChronosTimeInABottle.Config bottleConfig() {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof ChronosTimeInABottle original) {
        return original.getConfig();
      }
    }
    return DEFAULT_BOTTLE;
  }

  double getBottleCapacity(int level) {
    return bottle.getMaxStoredSeconds(level);
  }

  double getAuraRadius(int level) {
    return getConfig().auraRadiusBase + (level * getConfig().auraRadiusPerLevel);
  }

  double getRestoreRadius(int level) {
    return getConfig().restoreRadiusBase + (level * getConfig().restoreRadiusPerLevel);
  }

  long getRestoreWindowMillis(int level) {
    double seconds = switch (Math.max(1, Math.min(3, level))) {
      case 1 -> getConfig().restoreWindowSecondsLevel1;
      case 2 -> getConfig().restoreWindowSecondsLevel2;
      default -> getConfig().restoreWindowSecondsLevel3;
    };
    return (long) (seconds * 1000D);
  }

  double getPotionExtension(int level) {
    return Math.min(getConfig().potionMaxExtension, getConfig().potionExtensionBase + (level * getConfig().potionExtensionPerLevel));
  }

  @ConfigDescription("Chrono bottles hold and gather far more time, the time aura is stronger, and potions on you last much longer.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "How many times more time a bottle holds than Time in a Bottle allows.", impact = "Higher values store more time per bottle.")
    double bottleCapacityMultiplier = 3;
    @ConfigDoc(value = "How many times faster bottles gather time than with Time in a Bottle.", impact = "Higher values fill bottles sooner.")
    double bottleChargeMultiplier = 2;
    @ConfigDoc(value = "Time between two aura pulses, in milliseconds.", impact = "Lower values pulse more often at more server cost.")
    long auraPulseMillis = 2000;
    @ConfigDoc(value = "Aura radius before level scaling, in blocks.", impact = "Higher values reach further.")
    double auraRadiusBase = 7;
    @ConfigDoc(value = "Aura radius added per level, in blocks.", impact = "Higher values widen the aura at higher levels.")
    double auraRadiusPerLevel = 1.5;
    @ConfigDoc(value = "Blocks sampled per pulse before level scaling.", impact = "Higher values accelerate more blocks each pulse.")
    int auraSamplesBase = 6;
    @ConfigDoc(value = "Blocks sampled per pulse added per level.", impact = "Higher values reward higher levels more.")
    int auraSamplesPerLevel = 2;
    @ConfigDoc(value = "Chance a sampled crop grows a stage, before level scaling.", impact = "Higher values grow crops faster.")
    double auraGrowChanceBase = 0.45;
    @ConfigDoc(value = "Crop growth chance added per level.", impact = "Higher values reward higher levels more.")
    double auraGrowChancePerLevel = 0.1;
    @ConfigDoc(value = "Share of a cook or brew skipped per hit, before level scaling.", impact = "Higher values finish cooking sooner; capped at 0.9.")
    double auraCookBoostBase = 0.35;
    @ConfigDoc(value = "Cook and brew share added per level.", impact = "Higher values reward higher levels more.")
    double auraCookBoostPerLevel = 0.1;
    @ConfigDoc(value = "Beneficial potion extension before level scaling, as a share of its duration.", impact = "Higher values make potions last longer.")
    double potionExtensionBase = 0.5;
    @ConfigDoc(value = "Potion extension added per level.", impact = "Higher values reward higher levels more.")
    double potionExtensionPerLevel = 0.25;
    @ConfigDoc(value = "Highest potion extension at any level, as a share of its duration.", impact = "Caps the extension.")
    double potionMaxExtension = 1.5;
    @ConfigDoc(value = "Most ticks added to one potion.", impact = "Higher values let long potions gain more.")
    int potionMaxBonusTicks = 12000;
    @ConfigDoc(value = "Shortest potion that gets extended, in ticks.", impact = "Higher values ignore short pulsing effects.")
    int potionMinimumDurationTicks = 60;
    @ConfigDoc(value = "Longest potion that gets extended, in ticks.", impact = "Lower values leave near-permanent effects alone.")
    int potionMaximumBaseDurationTicks = 72000;
    @ConfigDoc(value = "How long destroyed blocks can still be restored at level 1, in seconds.", impact = "Higher values let older damage be restored.")
    double restoreWindowSecondsLevel1 = 60;
    @ConfigDoc(value = "How long destroyed blocks can still be restored at level 2, in seconds.", impact = "Higher values let older damage be restored.")
    double restoreWindowSecondsLevel2 = 120;
    @ConfigDoc(value = "How long destroyed blocks can still be restored at level 3, in seconds.", impact = "Higher values let older damage be restored.")
    double restoreWindowSecondsLevel3 = 300;
    @ConfigDoc(value = "Restoration radius before level scaling, in blocks.", impact = "Higher values restore a wider area.")
    double restoreRadiusBase = 4;
    @ConfigDoc(value = "Restoration radius added per level, in blocks.", impact = "Higher values widen restoration at higher levels.")
    double restoreRadiusPerLevel = 2;
    @ConfigDoc(value = "Stored bottle seconds one restoration charge costs.", impact = "Higher values make restoration drain bottles faster.")
    double restoreChargeSeconds = 60;
    @ConfigDoc(value = "Blocks one restoration charge restores.", impact = "Higher values make restoration cheaper.")
    int restoreBlocksPerCharge = 16;
    @ConfigDoc(value = "Blocks destroyed within this distance of a holder are remembered, in blocks.", impact = "Higher values remember damage further away at more server cost.")
    double restoreRecordRadius = 32;
    @ConfigDoc(value = "Most destroyed blocks remembered across the server.", impact = "Higher values remember more at more memory cost.")
    int restoreMaxLoggedBlocks = 20000;
  }
}
