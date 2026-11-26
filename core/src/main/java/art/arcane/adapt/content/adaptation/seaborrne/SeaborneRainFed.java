package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismBoonFood;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * While you are in water or rain your own activity costs no hunger, and a level-scaled allowance per minute tops your
 * food and saturation back up. Natural regeneration, the Hunger effect and every ability's hunger cost still drain as
 * usual; the allowance is all that refills them, and the food it gives is boon food, never eaten food.
 */
public class SeaborneRainFed extends SimpleAdaptation<SeaborneRainFed.Config> {
  private static final int MAX_FOOD = 20;
  private static final long TICK_MILLIS = 1000L;
  private static final Set<EntityExhaustionEvent.Reason> HELD = EnumSet.of(
      EntityExhaustionEvent.Reason.BLOCK_MINED, EntityExhaustionEvent.Reason.DAMAGED, EntityExhaustionEvent.Reason.ATTACK,
      EntityExhaustionEvent.Reason.JUMP_SPRINT, EntityExhaustionEvent.Reason.JUMP, EntityExhaustionEvent.Reason.SWIM,
      EntityExhaustionEvent.Reason.WALK_UNDERWATER, EntityExhaustionEvent.Reason.WALK_ON_WATER,
      EntityExhaustionEvent.Reason.SPRINT, EntityExhaustionEvent.Reason.CROUCH, EntityExhaustionEvent.Reason.WALK);

  private final Map<UUID, Feed> feeds = playerState();

  public SeaborneRainFed() {
    super("seaborne-rain-fed");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.rain_fed");
    setIcon(Id.minecraft("kelp"));
    setInterval(TICK_MILLIS);
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRefillPerMinute(level), 1), 1);
    long grace = getGraceMillis(level);
    if (grace > 0L) {
      statLore(v, Form.duration(grace, 0), 2);
    }
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    feeds.remove(player.getPlayer().uuid());
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
      withPlayerThread(player, () -> refill(player, System.currentTimeMillis(), TICK_MILLIS / 1000D));
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityExhaustionEvent e) {
    if (!HELD.contains(e.reason()) || e.exhaustion() <= 0F) {
      return;
    }
    PlayerRef player = e.player();
    if (getActiveLevel(player) > 0 && fed(player, System.currentTimeMillis())) {
      e.setExhaustion(0F);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    forget(e.player());
  }

  public void forget(PlayerRef player) {
    feeds.remove(player.uuid());
  }

  /** Tops the player up for {@code seconds} of being wet; Leviathan drives it for holders who lack the original. */
  public void refill(PlayerRef player, long now, double seconds) {
    if (!player.isOnline()) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      feeds.remove(player.uuid());
      return;
    }
    Feed feed = feeds.computeIfAbsent(player.uuid(), (UUID ignored) -> new Feed());
    if (!fed(player, now)) {
      feed.allowance = 0D;
      return;
    }
    feed.allowance = Math.min(Math.max(1D, getRefillPerMinute(level)),
        feed.allowance + getRefillPerMinute(level) * seconds / 60D);

    int food = player.foodLevel();
    int foodGained = Math.min(MAX_FOOD - food, (int) Math.floor(feed.allowance));
    if (foodGained > 0) {
      player.setFoodLevel(food + foodGained);
      HerbalismBoonFood.restored(player, foodGained);
      feed.allowance -= foodGained;
    }
    float saturation = player.saturation();
    float room = player.foodLevel() >= MAX_FOOD ? Math.max(0F, MAX_FOOD - saturation) : 0F;
    float saturationGained = (float) Math.min(room, feed.allowance);
    if (saturationGained > 0F) {
      player.setSaturation(saturation + saturationGained);
      feed.allowance = Math.max(0D, feed.allowance - saturationGained);
    }
    if (!feed.shown) {
      feed.shown = true;
      fx(player.location().add(0D, 1.2D, 0D), FxPriority.AMBIENT)
          .particle(Particles.SPLASH, 6, 0D, 0.3D, 0D, 0.3D, 0.05D)
          .sound(Sounds.ENTITY_GENERIC_EAT, 0.25F, 1.6F);
    }
  }

  private boolean fed(PlayerRef player, long now) {
    Feed feed = feeds.computeIfAbsent(player.uuid(), (UUID ignored) -> new Feed());
    if (SeaborneWet.isWet(player)) {
      feed.wetAt = now;
      return true;
    }
    long grace = getGraceMillis(getActiveLevel(player));
    boolean lingering = grace > 0L && feed.wetAt > 0L && now - feed.wetAt <= grace;
    if (!lingering) {
      feed.shown = false;
    }
    return lingering;
  }

  double getRefillPerMinute(int level) {
    return Math.max(0D, getConfig().refillPerMinuteAtLevelOne
        + progress(level) * (getConfig().refillPerMinuteAtTopLevel - getConfig().refillPerMinuteAtLevelOne));
  }

  long getGraceMillis(int level) {
    if (level < getConfig().graceFromLevel) {
      return 0L;
    }
    int top = getMaxLevel();
    double span = Math.max(1D, top - getConfig().graceFromLevel);
    double share = top <= getConfig().graceFromLevel ? 1D : Math.min(1D, (level - getConfig().graceFromLevel) / span);
    return Math.max(0L, Math.round(getConfig().graceMillisAtFirstLevel
        + share * (getConfig().graceMillisAtTopLevel - getConfig().graceMillisAtFirstLevel)));
  }

  private double progress(int level) {
    int max = getMaxLevel();
    return max <= 1 ? 1D : Math.max(0D, Math.min(1D, (level - 1D) / (max - 1D)));
  }

  private static final class Feed {
    private double allowance;
    private long wetAt;
    private boolean shown;
  }

  @ConfigDescription("While you are in water or rain, moving, mining and fighting cost no hunger and your food and saturation are topped up from an allowance each minute; at higher levels it lasts a short while after you dry off.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Food and saturation points restored each minute while wet, at level 1.", impact = "Higher values refill hunger that regeneration and abilities spend faster.")
    double refillPerMinuteAtLevelOne = 2;
    @ConfigDoc(value = "Food and saturation points restored each minute while wet, at the top level.", impact = "Higher values refill faster at the top level.")
    double refillPerMinuteAtTopLevel = 10;
    @ConfigDoc(value = "First level that keeps you fed for a while after you dry off.", impact = "Lower values give the grace period earlier.")
    int graceFromLevel = 3;
    @ConfigDoc(value = "Grace after drying off at that first level, in milliseconds.", impact = "Higher values keep you fed longer on land.")
    double graceMillisAtFirstLevel = 5000;
    @ConfigDoc(value = "Grace after drying off at the top level, in milliseconds.", impact = "Higher values keep you fed longer on land at the top level.")
    double graceMillisAtTopLevel = 15000;

    public Config() {
      baseCost = 3;
      initialCost = 2;
      costFactor = 0.6;
      maxLevel = 5;
    }
  }
}
