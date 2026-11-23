package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.item.ItemRef;

public class KineticsPhalanxReach extends SimpleAdaptation<KineticsPhalanxReach.Config> {
  private static final String SLOT_REACH = "reach";

  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsPhalanxReach() {
    super("kinetics-phalanx-reach");
    needs(PlatformNeed.item("iron_spear"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("copper_spear"));
    setInterval(2000);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        this::reconcile
    );
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    reconcile(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getReach(level), 2), 1);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef p = e.player();
    reconcileWith(p, p.inventory().item(e.newSlot()));
  }

  private void reconcile(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    reconcileWith(p, p.inventory().mainHand());
  }

  private void reconcileWith(PlayerRef p, ItemRef mainHand) {
    if (p == null || !p.isOnline() || !p.hasAttribute(Attributes.ENTITY_INTERACTION_RANGE)) {
      return;
    }

    int level = getActiveLevel(p);
    if (shouldApplyReach(level, isSpear(mainHand))) {
      AdaptAttributeService.get().apply(p, getName(), SLOT_REACH, Attributes.ENTITY_INTERACTION_RANGE, getReach(level), AttributeOperation.ADD_VALUE);
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
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
