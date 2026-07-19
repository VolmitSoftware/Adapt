package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.world.AdaptPlayer;
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
import org.bukkit.inventory.ItemStack;

public class KineticsPhalanxReach extends SimpleAdaptation<KineticsPhalanxReach.Config> {
  private static final String SLOT_REACH = "reach";

  public KineticsPhalanxReach() {
    super("kinetics-phalanx-reach");
    registerConfiguration(Config.class);
    setIcon(Material.COPPER_SPEAR);
    setInterval(2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getReach(level), 2), 1);
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    reconcileWith(p, p.getInventory().getItem(e.getNewSlot()));
  }

  private void reconcile(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    reconcileWith(p, p.getInventory().getItemInMainHand());
  }

  private void reconcileWith(Player p, ItemStack mainHand) {
    if (p == null || !p.isOnline() || Attributes.ENTITY_INTERACTION_RANGE == null || getLevel(p) <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (shouldApplyReach(level, isSpear(mainHand))) {
      AdaptAttributeService.get().apply(p, getName(), SLOT_REACH, Attributes.ENTITY_INTERACTION_RANGE, getReach(level), AttributeModifier.Operation.ADD_NUMBER);
    } else {
      AdaptAttributeService.get().remove(p, getName(), SLOT_REACH, Attributes.ENTITY_INTERACTION_RANGE);
    }
  }

  private double getReach(int level) {
    return getConfig().reachBase + (getLevelPercent(level) * getConfig().reachFactor);
  }

  static boolean shouldApplyReach(int activeLevel, boolean holdingSpear) {
    return activeLevel > 0 && holdingSpear;
  }

  @ConfigDescription("Spears strike farther in your hands.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base bonus entity-interaction reach in blocks while holding a spear.", impact = "Higher values extend spear strike range from level one.")
    double reachBase = 0.5;
    @ConfigDoc(value = "Additional reach in blocks granted as levels increase.", impact = "Higher values extend spear strike range further at max level.")
    double reachFactor = 1.25;

    public Config() {
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
