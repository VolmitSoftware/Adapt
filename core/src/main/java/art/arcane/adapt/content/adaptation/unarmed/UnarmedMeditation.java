/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.attribute.LegacyModifierKey;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UnarmedMeditation extends SimpleAdaptation<UnarmedMeditation.Config> {
  static final Id ABSORPTION_MODIFIER_KEY = Id.adapt("unarmed-meditation");
  private static final LegacyModifierKey ABSORPTION_MODIFIER = LegacyModifierKey.of(ABSORPTION_MODIFIER_KEY);
  private static final Id ABSORPTION_EFFECT = Id.minecraft("absorption");
  private static final double ABSORPTION_PER_EFFECT_LEVEL = 4D;

  private final Cooldowns combatCooldown = cooldowns();
  private final AdaptationOwnerPulse.Registration ownerMaintenance;
  private final Map<UUID, Loc> lastPositions = playerState();
  private final Map<UUID, Boolean> medState = playerState();
  private final Set<UUID> activeSessions = ConcurrentHashMap.newKeySet();
  private final Set<UUID> capacityPlayers = ConcurrentHashMap.newKeySet();

  public UnarmedMeditation() {
    super("unarmed-meditation");
    needs(PlatformNeed.attribute(Attributes.MAX_ABSORPTION));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("amethyst_cluster"));
    setInterval(1000);
    ownerMaintenance = AdaptationOwnerPulse.register(
        this,
        this::getInterval,
        () -> !activeSessions.isEmpty() || !capacityPlayers.isEmpty(),
        this::requiresOwnerMaintenance,
        this::maintainPlayerOwned
    );
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_meditate_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_meditate_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_meditate_500", "unarmed.meditation.absorption-gained", 500, 400);
    registerMilestone("challenge_unarmed_meditate_5k", "unarmed.meditation.absorption-gained", 5000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    stopMeditation(player.getPlayer());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.MANUAL, UnarmedPreferences.ARMED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getAbsorptionCap(level)), 1);
    statLore(v, Form.f(getConfig().gainPerPulse), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().combatLockoutMillis, 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef attacker) {
      combatCooldown.mark(attacker.uuid());
      stopMeditation(attacker);
    }
    if (e.entity() instanceof PlayerRef victim) {
      combatCooldown.mark(victim.uuid());
      stopMeditation(victim);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      stopMeditation(e.player());
      return;
    }
    startMeditation(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    activeSessions.remove(id);
    lastPositions.remove(id);
    medState.remove(id);
    removePlayerCapacity(e.player());
  }

  @Override
  public void unregister() {
    ownerMaintenance.unregister();
    activeSessions.clear();
    lastPositions.clear();
    medState.clear();
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> removePlayerCapacity(player));
    }
    capacityPlayers.clear();
    super.unregister();
  }

  static boolean requiresMaintenance(boolean learnerDemand, boolean activeSession, boolean capacityApplied) {
    return learnerDemand || activeSession || capacityApplied;
  }

  private boolean requiresOwnerMaintenance(UUID playerId) {
    return requiresMaintenance(
        getServer().hasOnlineLearner(playerId, getName()),
        activeSessions.contains(playerId),
        capacityPlayers.contains(playerId)
    );
  }

  private void maintainPlayerOwned(PlayerRef player) {
    if (!player.isOnline()) {
      return;
    }

    UUID id = player.uuid();
    boolean hasCapacity = capacityPlayers.contains(id);
    boolean hasSession = activeSessions.contains(id);
    boolean sneaking = player.isSneaking();
    if (!sneaking && !hasCapacity && !hasSession) {
      return;
    }

    if (!isEnabled() || !hasActiveAdaptation(player)
        || (preferenceEnabled(player, UnarmedPreferences.MANUAL) && !preferenceEnabled(player, UnarmedPreferences.ARMED))) {
      stopMeditation(player);
      if (hasCapacity) {
        removePlayerCapacity(player);
      }
      return;
    }

    if (hasCapacity) {
      cleanupDepletedCapacity(player);
    }
    if (!sneaking) {
      stopMeditation(player);
      return;
    }

    if (!hasSession) {
      startMeditation(player);
    }
  }

  private void startMeditation(PlayerRef player) {
    UUID id = player.uuid();
    if (!activeSessions.add(id)) {
      return;
    }
    lastPositions.put(id, player.location());
    if (!Tasks.runEntity(player, () -> pulseMeditation(player), 20)) {
      activeSessions.remove(id);
      lastPositions.remove(id);
    }
  }

  private void pulseMeditation(PlayerRef player) {
    UUID id = player.uuid();
    if (!activeSessions.contains(id) || !player.isOnline() || player.isDead() || !player.isSneaking()
        || (preferenceEnabled(player, UnarmedPreferences.MANUAL) && !preferenceEnabled(player, UnarmedPreferences.ARMED))
        || isItem(player.inventory().mainHand()) || isItem(player.inventory().offHand())
        || !combatCooldown.isReady(id, getConfig().combatLockoutMillis)) {
      stopMeditation(player);
      return;
    }

    Loc current = player.location();
    Loc previous = lastPositions.put(id, current);
    if (previous == null || !previous.sameWorld(current)
        || previous.distanceSquared(current) > getConfig().stationaryEpsilonSquared) {
      breakMeditation(player, id);
      scheduleNextPulse(player);
      return;
    }

    int level = getActiveLevel(player);
    if (level <= 0) {
      stopMeditation(player);
      return;
    }

    enterMeditation(player, id);
    double cap = getAbsorptionCap(level);
    if (!ensurePlayerCapacity(player, cap)) {
      scheduleNextPulse(player);
      return;
    }

    double absorption = player.absorption();
    if (absorption < cap) {
      double gained = Math.min(cap - absorption, Math.max(0D, getConfig().gainPerPulse));
      if (gained <= 0D) {
        cleanupDepletedCapacity(player);
        scheduleNextPulse(player);
        return;
      }
      player.setAbsorption(Math.min(cap, absorption + gained));
      double fill = cap <= 0 ? 1.0D : Math.min(1.0D, (absorption + gained) / cap);
      fx(current.add(0, 1.1D, 0), FxPriority.AMBIENT)
          .column(Particles.END_ROD, 2, 1.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0, 0, 0.4D, 0.04D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, (float) (1.4D + (fill * 0.3D)));
      xpSilent(player, getConfig().xpPerPulse, "meditation");
      getPlayer(player).getData().addStat("unarmed.meditation.absorption-gained", gained);
    }
    scheduleNextPulse(player);
  }

  private void scheduleNextPulse(PlayerRef player) {
    if (!Tasks.runEntity(player, () -> pulseMeditation(player), 20)) {
      stopMeditation(player);
    }
  }

  private void stopMeditation(PlayerRef player) {
    UUID id = player.uuid();
    activeSessions.remove(id);
    lastPositions.remove(id);
    breakMeditation(player, id);
  }

  private boolean ensurePlayerCapacity(PlayerRef player, double cap) {
    double absorption = player.absorption();
    if (!ensureAbsorptionCapacity(player, cap)) {
      capacityPlayers.remove(player.uuid());
      return false;
    }

    if (player.absorption() < absorption) {
      player.setAbsorption(Math.min(absorption, player.attributeValue(Attributes.MAX_ABSORPTION)));
    }
    capacityPlayers.add(player.uuid());
    return true;
  }

  static boolean ensureAbsorptionCapacity(LivingRef target, double cap) {
    if (target == null || !Double.isFinite(cap) || cap <= 0D) {
      return false;
    }
    if (!target.hasAttribute(Attributes.MAX_ABSORPTION)) {
      return AdaptAttributeService.get().isEmulated(Attributes.MAX_ABSORPTION);
    }

    for (AttributeMod modifier : ABSORPTION_MODIFIER.find(target, Attributes.MAX_ABSORPTION)) {
      if (modifier.operation() == AttributeOperation.ADD_VALUE && Double.compare(modifier.amount(), cap) == 0) {
        return true;
      }
    }

    ABSORPTION_MODIFIER.setTransient(target, Attributes.MAX_ABSORPTION, cap, AttributeOperation.ADD_VALUE);
    return true;
  }

  private void cleanupDepletedCapacity(PlayerRef player) {
    if (capacityPlayers.contains(player.uuid()) && player.absorption() <= 0D) {
      removePlayerCapacity(player);
    }
  }

  private void removePlayerCapacity(PlayerRef player) {
    removeAbsorptionCapacity(player);
    capacityPlayers.remove(player.uuid());
  }

  static void removeAbsorptionCapacity(LivingRef target) {
    if (target != null) {
      ABSORPTION_MODIFIER.remove(target, Attributes.MAX_ABSORPTION);
      if (!target.hasAttribute(Attributes.MAX_ABSORPTION)) {
        dropUncappedAbsorption(target);
      }
    }
  }

  /**
   * Without a max_absorption attribute nothing caps absorption, so dropping the capacity would leave meditation's
   * hearts behind. Do what the cap drop does where the attribute exists: keep only what an Absorption effect allows.
   */
  static void dropUncappedAbsorption(LivingRef target) {
    EffectInstance effect = target.effect(ABSORPTION_EFFECT);
    double allowed = effect == null ? 0D : ABSORPTION_PER_EFFECT_LEVEL * (effect.amplifier() + 1);
    if (target.absorption() > allowed) {
      target.setAbsorption(allowed);
    }
  }

  private void enterMeditation(PlayerRef p, UUID id) {
    if (Boolean.TRUE.equals(medState.put(id, Boolean.TRUE))) {
      return;
    }
    AbilityGlyphs.toggle(id, this, true);

    fx(p.location().add(0, 0.1D, 0), FxPriority.TRANSITION)
        .ring(Particles.END_ROD, 0.6D, 6, 0.1D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4F, 1.2F);
  }

  private void breakMeditation(PlayerRef p, UUID id) {
    if (!Boolean.TRUE.equals(medState.remove(id))) {
      return;
    }
    AbilityGlyphs.clear(id, this, HudGlyph.TOGGLE);

    fx(p.location().add(0, 0.2D, 0), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_BREAK, 0.3F, 0.8F);
  }

  private double getAbsorptionCap(int level) {
    return getConfig().absorptionCapBase + (getLevelPercent(level) * getConfig().absorptionCapFactor);
  }

  @ConfigDescription("Meditate while sneaking, still, and empty-handed to slowly build absorption hearts.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base absorption cap in health points at level 1.", impact = "Higher values allow more stored absorption early.")
    double absorptionCapBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorption cap granted at max level.", impact = "Higher values allow more stored absorption as levels increase.")
    double absorptionCapFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Absorption health points gained per meditation pulse.", impact = "Higher values build absorption faster.")
    double gainPerPulse = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds after combat before meditation can resume.", impact = "Higher values force a longer calm period after fighting.")
    long combatLockoutMillis = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum squared movement distance still considered stationary.", impact = "Higher values tolerate more drift while meditating.")
    double stationaryEpsilonSquared = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Silent XP granted per meditation pulse.", impact = "Higher values speed up unarmed skill progression from meditating.")
    double xpPerPulse = 1.2;

    public Config() {
      costFactor = 0.55;
      initialCost = 5;
    }
  }
}
