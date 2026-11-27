package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

public class KineticsPhalanxReach extends SimpleAdaptation<KineticsPhalanxReach.Config> {
  public static final Id ID = Id.adapt("kinetics/phalanx_reach");
  private final KineticsReach reach = new KineticsReach(this);

  public KineticsPhalanxReach() {
    super(ID);
    needs(PlatformNeed.item("iron_spear"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("copper_spear"));
    setInterval(2000);
    pulseOwners(this::getInterval, this::reconcile);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    reconcile(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getReach(level), 2), 1);
  }

  @Override
  public void unregister() {
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
    reach.reconcile(p, ItemKinds.isSpear(mainHand), getReach(getActiveLevel(p)));
  }

  private double getReach(int level) {
    return getConfig().reachBase + (getLevelPercent(level) * getConfig().reachFactor);
  }

  public static boolean shouldApplyReach(int activeLevel, boolean holdingSpear) {
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
