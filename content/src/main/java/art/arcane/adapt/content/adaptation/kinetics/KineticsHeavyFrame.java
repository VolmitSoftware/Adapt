package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KineticsHeavyFrame extends SimpleAdaptation<KineticsHeavyFrame.Config> {
  public static final Id ID = Id.adapt("kinetics/heavy_frame");
  public static final PlayerPreference<Control> CONTROL = PlayerPreference.of("control", Control.class, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, Text.key("adapt.kineticsheavyframe.preferences.preference_control_hold"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.LATCH, Text.key("adapt.kineticsheavyframe.preferences.preference_control_latch"), Id.minecraft("iron_chain"), 1))).labelled(Text.key("adapt.kineticsheavyframe.preferences.preference_control"));

  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_KB = "plant-kb";
  private static final String SLOT_BLAST = "plant-blast";
  private static final String SLOT_SLOW = "plant-slow";

  private final Map<UUID, Boolean> latched = playerState();
  private final Map<UUID, Boolean> planted = playerState();

  public KineticsHeavyFrame() {
    super(ID);
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_chestplate"));
    setInterval(RECONCILE_INTERVAL_MS);
    pulseOwners(this::getInterval, () -> !planted.isEmpty(), this::requiresOwnerMaintenance, this::maintainOwner);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    latched.remove(player.player().uuid());
    clearStance(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getKbResist(level), 2), 1);
    statLore(v, Format.f(getExplosionResist(level), 2), 2);
    statLore(v, TextFormat.RED, "- ", Format.f(getSpeedPenalty(level), 2), 3);
  }

  @Override
  public void unregister() {
    planted.clear();
    super.unregister();
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (preference(p, CONTROL) == Control.LATCH) {
      if (e.isSneaking()) {
        if (latched.remove(p.uuid()) != null) {
          clearStance(p);
        } else {
          latched.put(p.uuid(), true);
          reconcile(p);
        }
      }
      return;
    }
    if (!e.isSneaking()) {
      clearStance(p);
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    if (!plantEligible(ItemKinds.isMace(mainHand), ItemKinds.isSpear(mainHand))) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    applyStance(p, level);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef p = e.player();
    ItemRef next = p.inventory().item(e.newSlot());
    int level = getActiveLevel(p);
    if (!shouldPlant(stanceArmed(p), ItemKinds.isMace(next), ItemKinds.isSpear(next), level)) {
      clearStance(p);
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

  static boolean requiresMaintenance(boolean learnerDemand, boolean plantedStance) {
    return learnerDemand || plantedStance;
  }

  private boolean requiresOwnerMaintenance(UUID playerId) {
    return requiresMaintenance(
        ownerLevel(playerId) > 0,
        planted.containsKey(playerId)
    );
  }

  private void maintainOwner(PlayerRef player) {
    UUID playerId = player.uuid();
    if (ownerLevel(playerId) <= 0) {
      planted.remove(playerId);
      return;
    }
    reconcile(player);
  }

  private boolean stanceArmed(PlayerRef player) {
    return preference(player, CONTROL) == Control.HOLD ? player.isSneaking() : latched.containsKey(player.uuid());
  }

  private void reconcile(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    int level = getActiveLevel(p);
    if (!shouldPlant(stanceArmed(p), ItemKinds.isMace(mainHand), ItemKinds.isSpear(mainHand), level)) {
      clearStance(p);
      return;
    }

    applyStance(p, level);
  }

  private void applyStance(PlayerRef p, int level) {
    boolean entering = planted.put(p.uuid(), Boolean.TRUE) == null;
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.apply(p, getId().toString(), SLOT_KB, Attributes.KNOCKBACK_RESISTANCE, getKbResist(level), AttributeOperation.ADD_VALUE);
    attributes.apply(p, getId().toString(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, getExplosionResist(level), AttributeOperation.ADD_VALUE);
    attributes.apply(p, getId().toString(), SLOT_SLOW, Attributes.MOVEMENT_SPEED, -getSpeedPenalty(level), AttributeOperation.ADD_MULTIPLIED_TOTAL);
    if (entering) {
      HudStatus.toggle(p.uuid(), this, true);
      fx(p, FxPriority.TRANSITION)
          .ring(Particles.CLOUD, 0.55D, 8, 0.05D)
          .sound(Sounds.BLOCK_CHAIN_PLACE, 0.35F, 0.7F);
    }
  }

  private void clearStance(PlayerRef p) {
    AdaptAttributeService.get().removeAll(p, getId().toString());
    if (planted.remove(p.uuid()) != null) {
      HudStatus.clear(p.uuid(), this, HudGlyph.TOGGLE);
      fx(p, FxPriority.TRANSITION)
          .ring(Particles.CLOUD, 0.55D, 6, 0.05D)
          .sound(Sounds.BLOCK_CHAIN_BREAK, 0.3F, 1.25F);
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
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
    }
  }

  public enum Control { HOLD, LATCH }
}
