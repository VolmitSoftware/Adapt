package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Absorption that builds while you are in water or rain, up to a cap per level. The ward only ever holds its own
 * points: it never refills absorption from other sources, any hit pauses it, and once you are dry it drains away.
 */
public class SeaborneTidalWard extends SimpleAdaptation<SeaborneTidalWard.Config> {
  public static final List<PlatformNeed> NEEDS = List.of(PlatformNeed.attribute(Attributes.MAX_ABSORPTION));
  private static final String ABSORPTION_SLOT = "tidal-ward";
  private static final long TICK_MILLIS = 500L;

  private final Map<UUID, Ward> wards = playerState();

  public SeaborneTidalWard() {
    super("seaborne-pressure-diver");
    needs(NEEDS.toArray(new PlatformNeed[0]));
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.tidal_ward");
    setIcon(Id.minecraft("nautilus_shell"));
    setInterval(TICK_MILLIS);
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getCap(level) / 2D, 1) + " ♥", 1);
    statLore(v, Form.f(getGainPerSecond(level) / 2D, 2) + " ♥", 2);
    statLore(v, Form.duration(getPauseMillis(level), 1), 3);
    statLore(v, Form.duration(getLingerMillis(level), 1), 4);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    if (getActiveLevel(p) <= 0) {
      drop(p);
    }
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> refresh(player, System.currentTimeMillis(), TICK_MILLIS / 1000D));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef player) {
      Ward ward = wards.get(player.uuid());
      if (ward != null) {
        ward.hitAt = System.currentTimeMillis();
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    drop(e.player());
  }

  @Override
  public void unregister() {
    List<UUID> held = new ArrayList<>(wards.keySet());
    super.unregister();
    for (UUID id : held) {
      PlayerRef player = AdaptCore.platform().host().player(id);
      if (player != null) {
        Tasks.runEntity(player, () -> drop(player));
      }
    }
    wards.clear();
  }

  /** Builds, holds or drains the player's ward over {@code seconds}; Leviathan drives it for holders who lack the original. */
  public void refresh(PlayerRef player, long now, double seconds) {
    if (!player.isOnline() || !maxAbsorptionAvailable()) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      drop(player);
      return;
    }

    Ward ward = wards.computeIfAbsent(player.uuid(), (UUID ignored) -> new Ward());
    ward.points = Math.min(ward.points, Math.max(0D, player.absorption()));
    boolean wet = SeaborneWet.isWet(player);
    if (wet) {
      ward.wetAt = now;
    }

    double before = ward.points;
    double cap = getCap(level);
    if (wet && now - ward.hitAt >= getPauseMillis(level)) {
      ward.points = Math.min(cap, ward.points + getGainPerSecond(level) * seconds);
    } else if (!wet && now - ward.wetAt >= getLingerMillis(level)) {
      ward.points = Math.max(0D, ward.points - Math.max(0D, getConfig().drainPerSecond) * seconds);
    }

    apply(player, ward, before);
    if (ward.points <= 0D && !wet) {
      wards.remove(player.uuid());
    }
  }

  private void apply(PlayerRef player, Ward ward, double before) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (ward.points > 0D) {
      attributes.apply(player, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION, ward.points,
          AttributeOperation.ADD_VALUE);
    } else {
      attributes.remove(player, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION);
    }
    double maximum = attributes.value(player, Attributes.MAX_ABSORPTION);
    double current = player.absorption();
    double target = Math.max(0D, current + (ward.points - before));
    if (!Double.isNaN(maximum)) {
      target = Math.min(target, Math.max(0D, maximum));
    }
    if (Double.compare(target, current) != 0) {
      player.setAbsorption(target);
    }
    if (before <= 0D && ward.points > 0D) {
      fx(player.location().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
          .ring(Particles.BUBBLE, 0.7D, 12, 0.0D)
          .dustRing(0.7D, 8, 1.0F)
          .sound(Sounds.BLOCK_CONDUIT_ACTIVATE, 0.5F, 1.4F);
    }
  }

  /** Takes the ward's own points away and forgets the player. */
  public void drop(PlayerRef player) {
    Ward ward = wards.remove(player.uuid());
    if (ward == null) {
      return;
    }
    double held = Math.min(ward.points, Math.max(0D, player.absorption()));
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(player, getName(), ABSORPTION_SLOT, Attributes.MAX_ABSORPTION);
    double maximum = attributes.value(player, Attributes.MAX_ABSORPTION);
    double left = Math.max(0D, player.absorption() - held);
    if (!Double.isNaN(maximum)) {
      left = Math.min(left, Math.max(0D, maximum));
    }
    player.setAbsorption(left);
    if (held > 0D) {
      fx(player.location().add(0D, 1.0D, 0D), FxPriority.AMBIENT)
          .particle(Particles.SPLASH, 8, 0D, 0.4D, 0D, 0.3D, 0.05D)
          .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.3F, 1.2F);
    }
  }

  /** Absorption points the ward holds for the player right now; 0 when it holds none. */
  public double held(PlayerRef player) {
    Ward ward = wards.get(player.uuid());
    return ward == null ? 0D : ward.points;
  }

  private static boolean maxAbsorptionAvailable() {
    return AdaptCore.platform().host().registries().exists(RegistryKind.ATTRIBUTE, Attributes.MAX_ABSORPTION)
        || AdaptAttributeService.get().isEmulated(Attributes.MAX_ABSORPTION);
  }

  double getCap(int level) {
    return Math.max(0D, getConfig().capAtLevelOne + progress(level) * (getConfig().capAtTopLevel - getConfig().capAtLevelOne));
  }

  double getGainPerSecond(int level) {
    return Math.max(0D, getConfig().gainPerSecondAtLevelOne
        + progress(level) * (getConfig().gainPerSecondAtTopLevel - getConfig().gainPerSecondAtLevelOne));
  }

  long getPauseMillis(int level) {
    return Math.max(0L, Math.round(getConfig().pauseMillisAtLevelOne
        + progress(level) * (getConfig().pauseMillisAtTopLevel - getConfig().pauseMillisAtLevelOne)));
  }

  long getLingerMillis(int level) {
    return Math.max(0L, Math.round(getConfig().lingerMillisAtLevelOne
        + progress(level) * (getConfig().lingerMillisAtTopLevel - getConfig().lingerMillisAtLevelOne)));
  }

  private double progress(int level) {
    int max = getMaxLevel();
    return max <= 1 ? 1D : Math.max(0D, Math.min(1D, (level - 1D) / (max - 1D)));
  }

  private static final class Ward {
    private double points;
    private long wetAt;
    private long hitAt;
  }

  @ConfigDescription("Absorption hearts build while you are in water or rain, up to a cap; any hit pauses the ward and it drains away soon after you dry off.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Most absorption health the ward holds at level 1; two health points show as one heart.", impact = "Higher values soak more damage.")
    double capAtLevelOne = 4;
    @ConfigDoc(value = "Most absorption health the ward holds at the top level.", impact = "Higher values soak more damage at the top level.")
    double capAtTopLevel = 12;
    @ConfigDoc(value = "Absorption health the ward gains each second while you are wet, at level 1.", impact = "Higher values refill the ward faster.")
    double gainPerSecondAtLevelOne = 0.5;
    @ConfigDoc(value = "Absorption health the ward gains each second while you are wet, at the top level.", impact = "Higher values refill the ward faster at the top level.")
    double gainPerSecondAtTopLevel = 1;
    @ConfigDoc(value = "Time the ward stops building after you take damage, at level 1, in milliseconds.", impact = "Higher values keep the ward from refilling during a fight.")
    double pauseMillisAtLevelOne = 6000;
    @ConfigDoc(value = "Time the ward stops building after you take damage, at the top level, in milliseconds.", impact = "Lower values let the ward refill sooner after a hit.")
    double pauseMillisAtTopLevel = 4000;
    @ConfigDoc(value = "Time the ward keeps its hearts after you dry off, at level 1, in milliseconds.", impact = "Higher values let you carry the ward out of the water.")
    double lingerMillisAtLevelOne = 2000;
    @ConfigDoc(value = "Time the ward keeps its hearts after you dry off, at the top level, in milliseconds.", impact = "Higher values let you carry the ward farther at the top level.")
    double lingerMillisAtTopLevel = 6000;
    @ConfigDoc(value = "Absorption health the ward loses each second once you are dry and the linger time is over.", impact = "Higher values drain the ward faster on land.")
    double drainPerSecond = 2;

    public Config() {
      baseCost = 3;
      initialCost = 2;
      costFactor = 0.6;
      maxLevel = 5;
    }
  }
}
