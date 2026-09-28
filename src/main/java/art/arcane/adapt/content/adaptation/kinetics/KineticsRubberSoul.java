package art.arcane.adapt.content.adaptation.kinetics;

import java.util.List;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;

public class KineticsRubberSoul extends SimpleAdaptation<KineticsRubberSoul.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> BOUNCE = CommonPreferences.toggle("bounce", KineticsMessages.KINETICSRUBBERSOUL_PREFERENCE_BOUNCE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPRINGY = CommonPreferences.toggle("springy", KineticsMessages.KINETICSRUBBERSOUL_PREFERENCE_SPRINGY, CommonPreferences.Toggle.ON);

  private static final String SLOT_SOLE = "sole";
  private static final String SLOT_SPRINGLOAD = "springload";

  private final Map<UUID, Boolean> airborne = playerState();
  private final Map<UUID, LandingObservation> pendingLandings = playerState();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsRubberSoul() {
    super("kinetics-rubber-soul");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BALL);
    setInterval(1000);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        this::maintainOwner
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BOUNCE, SPRINGY);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getBounciness(level), 2), 1);
    statLore(v, Form.f(getSoftBlockBonus(level), 2), 2);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    airborne.clear();
    pendingLandings.clear();
    super.unregister();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    if (e.isCancelled() || !hasActiveAdaptation(p)) {
      airborne.remove(playerId);
      pendingLandings.remove(playerId);
      return;
    }
    if (!p.isOnGround()) {
      airborne.put(playerId, true);
    } else if (!Boolean.TRUE.equals(airborne.get(playerId))) {
      return;
    }
    LandingObservation pending = pendingLandings.get(playerId);
    if (pending != null) {
      pending.event = e;
      return;
    }
    LandingObservation observation = new LandingObservation(e);
    pendingLandings.put(playerId, observation);
    scheduleObservation(p, observation);
  }

  private void scheduleObservation(Player p, LandingObservation observation) {
    if (!J.runEntity(p, () -> observeLanding(p, observation), 1)) {
      pendingLandings.remove(p.getUniqueId(), observation);
      airborne.remove(p.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    if (!e.isCancelled()) {
      airborne.remove(e.getPlayer().getUniqueId());
      pendingLandings.remove(e.getPlayer().getUniqueId());
    }
  }

  private void observeLanding(Player p, LandingObservation observation) {
    UUID playerId = p.getUniqueId();
    if (pendingLandings.get(playerId) != observation) {
      return;
    }
    PlayerMoveEvent event = observation.event;
    if (!isRuntimeRegistered() || !p.isOnline() || !p.isValid() || p.isDead()
        || event.isCancelled() || !hasActiveAdaptation(p)) {
      pendingLandings.remove(playerId, observation);
      airborne.remove(playerId);
      return;
    }
    boolean onGround = p.isOnGround();
    Boolean wasAirborne = airborne.put(playerId, !onGround);
    if (!onGround) {
      scheduleObservation(p, observation);
      return;
    }
    pendingLandings.remove(playerId, observation);
    if (!isLanding(onGround, wasAirborne != null && wasAirborne)) {
      return;
    }
    applySpringload(p, event);
  }

  void applySpringload(Player p, PlayerMoveEvent e) {
    if (!preferenceEnabled(p, SPRINGY) || Attributes.BOUNCINESS == null || !KineticsMotion.isBouncySurface(landingSurface(p))) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      AdaptAttributeService.get().applyTimed(p, getName(), SLOT_SPRINGLOAD, Attributes.BOUNCINESS, getSoftBlockBonus(level), AttributeModifier.Operation.ADD_NUMBER, getConfig().bonusWindowTicks);
      fx(p.getLocation(), FxPriority.GAMEPLAY)
          .particle(Particle.ITEM_SLIME, 6, 0, 0.2D, 0, 0.3D, 0.05D)
          .sound(Sound.BLOCK_SLIME_BLOCK_FALL, 0.5F, 1.4F);
    });
  }

  static boolean isLanding(boolean onGround, boolean wasAirborne) {
    return onGround && wasAirborne;
  }

  static double bounciness(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (!Double.isFinite(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  static double softBlockBonus(double base, double factor, double levelPercent) {
    double value = base + (levelPercent * factor);
    if (!Double.isFinite(value)) {
      return 0D;
    }
    return Math.max(0D, value);
  }

  private void maintainOwner(Player player) {
    if (Attributes.BOUNCINESS != null) {
      maintainSole(player);
    }
  }

  private void maintainSole(Player player) {
    if (!player.isOnline() || player.isDead()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!hasActiveAdaptation(player) || !preferenceEnabled(player, BOUNCE)) {
      attributes.remove(player, getName(), SLOT_SOLE, Attributes.BOUNCINESS);
      return;
    }

    attributes.apply(player, getName(), SLOT_SOLE, Attributes.BOUNCINESS, getBounciness(getLevel(player)), AttributeModifier.Operation.ADD_NUMBER);
  }

  private Material landingSurface(Player p) {
    Block feet = p.getLocation().getBlock();
    if (KineticsMotion.isBouncySurface(feet.getType())) {
      return feet.getType();
    }
    return feet.getRelative(BlockFace.DOWN).getType();
  }

  private double getBounciness(int level) {
    return bounciness(getConfig().bouncinessBase, getConfig().bouncinessFactor, getLevelPercent(level));
  }

  private double getSoftBlockBonus(int level) {
    return softBlockBonus(getConfig().softBlockBonusBase, getConfig().softBlockBonusFactor, getLevelPercent(level));
  }

  private static final class LandingObservation {
    private PlayerMoveEvent event;

    private LandingObservation(PlayerMoveEvent event) {
      this.event = event;
    }
  }

  @ConfigDescription("Passive bounciness preserves landing momentum. Slime, honey, and bed landings grant a short springload bonus.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base passive bounciness bonus applied while the adaptation is active.", impact = "Higher values make every landing springier at every level.")
    double bouncinessBase = 0.15;
    @ConfigDoc(value = "Additional passive bounciness granted at max level.", impact = "Higher values widen the springiness gain from leveling.")
    double bouncinessFactor = 0.35;
    @ConfigDoc(value = "Base extra bounciness granted after landing on a bouncy block before level scaling.", impact = "Higher values make bouncy-block landings launch harder at every level.")
    double softBlockBonusBase = 0.3;
    @ConfigDoc(value = "Additional bouncy-block bounciness granted at max level.", impact = "Higher values widen the bouncy-block launch gain from leveling.")
    double softBlockBonusFactor = 0.5;
    @ConfigDoc(value = "Duration in ticks of the bouncy-block bounciness bonus window.", impact = "Higher values keep the springload bonus active longer after each bouncy landing.")
    long bonusWindowTicks = 40;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
