package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KineticsHeavyFrame extends SimpleAdaptation<KineticsHeavyFrame.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", KineticsMessages.KINETICSHEAVYFRAME_PREFERENCE_CONTROL, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, KineticsMessages.KINETICSHEAVYFRAME_PREFERENCE_CONTROL_HOLD, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.LATCH, KineticsMessages.KINETICSHEAVYFRAME_PREFERENCE_CONTROL_LATCH, Id.minecraft("iron_chain"), 1))));

  private static final long RECONCILE_INTERVAL_MS = 1000L;
  private static final String SLOT_KB = "plant-kb";
  private static final String SLOT_BLAST = "plant-blast";
  private static final String SLOT_SLOW = "plant-slow";

  private final Map<UUID, Boolean> latched = playerState();
  private final Map<UUID, Boolean> planted = playerState();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;

  public KineticsHeavyFrame() {
    super("kinetics-heavy-frame");
    needs(PlatformNeed.item("mace"));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_chestplate"));
    setInterval(RECONCILE_INTERVAL_MS);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        () -> !planted.isEmpty(),
        this::requiresOwnerMaintenance,
        this::maintainOwner
    );
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    latched.remove(player.getPlayer().uuid());
    clearStance(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getKbResist(level), 2), 1);
    statLore(v, Form.f(getExplosionResist(level), 2), 2);
    statLore(v, C.RED, "- ", Form.f(getSpeedPenalty(level), 2), 3);
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
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
    if (!plantEligible(isMace(mainHand), isSpear(mainHand))) {
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
    if (!shouldPlant(stanceArmed(p), isMace(next), isSpear(next), level)) {
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
        getServer().hasOnlineLearner(playerId, getName()),
        planted.containsKey(playerId)
    );
  }

  private void maintainOwner(PlayerRef player) {
    UUID playerId = player.uuid();
    if (!getServer().hasOnlineLearner(playerId, getName())) {
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
    if (!shouldPlant(stanceArmed(p), isMace(mainHand), isSpear(mainHand), level)) {
      clearStance(p);
      return;
    }

    applyStance(p, level);
  }

  private void applyStance(PlayerRef p, int level) {
    boolean entering = planted.put(p.uuid(), Boolean.TRUE) == null;
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.apply(p, getName(), SLOT_KB, Attributes.KNOCKBACK_RESISTANCE, getKbResist(level), AttributeOperation.ADD_VALUE);
    attributes.apply(p, getName(), SLOT_BLAST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, getExplosionResist(level), AttributeOperation.ADD_VALUE);
    attributes.apply(p, getName(), SLOT_SLOW, Attributes.MOVEMENT_SPEED, -getSpeedPenalty(level), AttributeOperation.ADD_MULTIPLIED_TOTAL);
    if (entering) {
      AbilityGlyphs.toggle(p.uuid(), this, true);
      fx(p, FxPriority.TRANSITION)
          .ring(Particles.CLOUD, 0.55D, 8, 0.05D)
          .sound(Sounds.BLOCK_CHAIN_PLACE, 0.35F, 0.7F);
    }
  }

  private void clearStance(PlayerRef p) {
    AdaptAttributeService.get().removeAll(p, getName());
    if (planted.remove(p.uuid()) != null) {
      AbilityGlyphs.clear(p.uuid(), this, HudGlyph.TOGGLE);
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
      baseCost = 4;
      costFactor = 0.45;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  public enum Control { HOLD, LATCH }
}
