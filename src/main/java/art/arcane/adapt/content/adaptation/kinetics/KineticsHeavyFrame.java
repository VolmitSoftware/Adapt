package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

public class KineticsHeavyFrame extends SimpleAdaptation<KineticsHeavyFrame.Config> {
  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_KB = "plant-kb";
  private static final String SLOT_BLAST = "plant-blast";
  private static final String SLOT_SLOW = "plant-slow";

  public KineticsHeavyFrame() {
    super("kinetics-heavy-frame");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_CHESTPLATE);
    setInterval(RECONCILE_INTERVAL_MS);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getKbResist(level), 2), 1);
    statLore(v, Form.f(getExplosionResist(level), 2), 2);
    statLore(v, C.RED, "- ", Form.f(getSpeedPenalty(level), 2), 3);
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking()) {
      AdaptAttributeService.get().removeAll(p, getName());
      return;
    }

    ItemStack mainHand = p.getInventory().getItemInMainHand();
    if (!plantEligible(isMace(mainHand), isSpear(mainHand))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    applyStance(p, level);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    ItemStack next = p.getInventory().getItem(e.getNewSlot());
    int level = getActiveLevel(p);
    if (!shouldPlant(p.isSneaking(), isMace(next), isSpear(next), level)) {
      AdaptAttributeService.get().removeAll(p, getName());
      return;
    }

    applyStance(p, level);
  }

  static boolean plantEligible(boolean holdingMace, boolean holdingSpear) {
    return holdingMace || holdingSpear;
  }

  static boolean shouldPlant(boolean sneaking, boolean holdingMace, boolean holdingSpear, int level) {
    return sneaking && level > 0 && plantEligible(holdingMace, holdingSpear);
  }

  private void reconcile(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    ItemStack mainHand = p.getInventory().getItemInMainHand();
    int level = getActiveLevel(p);
    if (!shouldPlant(p.isSneaking(), isMace(mainHand), isSpear(mainHand), level)) {
      AdaptAttributeService.get().removeAll(p, getName());
      return;
    }

    applyStance(p, level);
  }

  private void applyStance(Player p, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (Attributes.KNOCKBACK_RESISTANCE != null) {
      attributes.apply(p, getName(), SLOT_KB, Attributes.KNOCKBACK_RESISTANCE, getKbResist(level), AttributeModifier.Operation.ADD_NUMBER);
    }
    if (Attributes.EXPLOSION_KNOCKBACK_RESISTANCE != null) {
      attributes.apply(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, getExplosionResist(level), AttributeModifier.Operation.ADD_NUMBER);
    }
    if (Attributes.MOVEMENT_SPEED != null) {
      attributes.apply(p, getName(), SLOT_SLOW, Attributes.MOVEMENT_SPEED, -getSpeedPenalty(level), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }
  }

  private double getKbResist(int level) {
    return getConfig().kbResistBase + (getLevelPercent(level) * getConfig().kbResistFactor);
  }

  private double getExplosionResist(int level) {
    return getConfig().explosionResistBase + (getLevelPercent(level) * getConfig().explosionResistFactor);
  }

  private double getSpeedPenalty(int level) {
    return getConfig().speedPenaltyBase + (getLevelPercent(level) * getConfig().speedPenaltyFactor);
  }

  @ConfigDescription("Plant your feet while sneaking with a mace or spear: heavy knockback resistance at the cost of speed.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base knockback resistance granted while planted.", impact = "Higher values shrug off more melee and projectile knockback while sneaking with a mace or spear.")
    double kbResistBase = 0.3;
    @ConfigDoc(value = "Additional knockback resistance gained at maximum level.", impact = "Higher values make the max-level stance harder to move.")
    double kbResistFactor = 0.5;
    @ConfigDoc(value = "Base explosion knockback resistance granted while planted.", impact = "Higher values shrug off more blast knockback while planted.")
    double explosionResistBase = 0.3;
    @ConfigDoc(value = "Additional explosion knockback resistance gained at maximum level.", impact = "Higher values make the max-level stance harder to blast away.")
    double explosionResistFactor = 0.5;
    @ConfigDoc(value = "Base movement speed penalty while planted.", impact = "Higher values slow planted players more while the stance is held.")
    double speedPenaltyBase = 0.15;
    @ConfigDoc(value = "Additional movement speed penalty gained at maximum level.", impact = "Higher values slow max-level planted players more.")
    double speedPenaltyFactor = 0.15;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
