package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.kinetics.KineticsMotion;
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

import java.util.Map;
import java.util.UUID;

public class KineticsRubberSoul extends SimpleAdaptation<KineticsRubberSoul.Config> {
  private static final String SLOT_SOLE = "sole";
  private static final String SLOT_SPRINGLOAD = "springload";

  private final Map<UUID, Boolean> airborne = playerState();

  public KineticsRubberSoul() {
    super("kinetics-rubber-soul");
    registerConfiguration(Config.class);
    setIcon(Material.SLIME_BALL);
    setInterval(1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getBounciness(level), 2), 1);
    statLore(v, Form.f(getSoftBlockBonus(level), 2), 2);
  }

  @Override
  public void onTick() {
    if (Attributes.BOUNCINESS == null) {
      return;
    }

    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> maintainSole(player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      airborne.remove(p.getUniqueId());
      return;
    }
    boolean onGround = p.isOnGround();
    Boolean wasAirborne = airborne.put(p.getUniqueId(), !onGround);
    if (!isLanding(onGround, wasAirborne != null && wasAirborne)) {
      return;
    }

    if (Attributes.BOUNCINESS == null || !KineticsMotion.isBouncySurface(landingSurface(p))) {
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

  private void maintainSole(Player player) {
    if (!player.isOnline() || player.isDead()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (!hasActiveAdaptation(player)) {
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

  @ConfigDescription("Your landings carry spring. Bouncy blocks send you higher, and every landing keeps more momentum.")
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
