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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.SeabornMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SeaborneHydroJet extends SimpleAdaptation<SeaborneHydroJet.Config> {
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", SeabornMessages.SEABORNEHYDROJET_PREFERENCE_GESTURE, Gesture.SINGLE, List.of(
          new PlayerPreference.Choice<>(Gesture.SINGLE, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_GESTURE_SINGLE, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_GESTURE_DOUBLE, Id.minecraft("rabbit_foot"), 1))));
  public static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("reserve", SeabornMessages.SEABORNEHYDROJET_PREFERENCE_RESERVE, Reserve.NONE, List.of(
          new PlayerPreference.Choice<>(Reserve.NONE, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_RESERVE_NONE, Id.minecraft("bowl"), 1),
          new PlayerPreference.Choice<>(Reserve.FOUR, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_RESERVE_FOUR, Id.minecraft("bread"), 1),
          new PlayerPreference.Choice<>(Reserve.EIGHT, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_RESERVE_EIGHT, Id.minecraft("cooked_beef"), 1))));
  public static final PlayerPreference<Priority> PRIORITY = new PlayerPreference<>(Priority.class,
      new PlayerPreference.Definition<>("priority", SeabornMessages.SEABORNEHYDROJET_PREFERENCE_PRIORITY, Priority.HYDRO, List.of(
          new PlayerPreference.Choice<>(Priority.HYDRO, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_PRIORITY_HYDRO, Id.minecraft("heart_of_the_sea"), 1),
          new PlayerPreference.Choice<>(Priority.TIDE, SeabornMessages.SEABORNEHYDROJET_PREFERENCE_PRIORITY_TIDE, Id.minecraft("trident"), 1))));

  private static final double MAX_RESULTING_VELOCITY = 2.6;

  private final Map<UUID, Long> lastTap = playerState();
  private final Map<UUID, ChargeState> charges = playerState();

  public SeaborneHydroJet() {
    super("seaborne-hydro-jet");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.hydro_jet");
    setIcon(Id.minecraft("prismarine_crystals"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("prismarine_crystals"))
        .key("challenge_seaborne_hydro_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_seaborne_hydro_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_hydro_200", "seaborne.hydro-jet.jets", 200, 300);
    registerMilestone("challenge_seaborne_hydro_5k", "seaborne.hydro-jet.jets", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GESTURE, RESERVE, PRIORITY);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    lastTap.remove(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getBurstForce(level), 2), 1);
    statLore(v, getMaxCharges(level), 2);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    PlayerRef p = e.player();
    if (!reservesSneak(p)) {
      return;
    }

    if (preference(p, GESTURE) == Gesture.DOUBLE) {
      long now = System.currentTimeMillis();
      Long previous = lastTap.put(p.uuid(), now);
      if (previous == null || now - previous > 350L) {
        return;
      }
      lastTap.remove(p.uuid());
    }
    withAdaptedPlayer(p, () -> tryJet(p));
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    charges.remove(e.player().uuid());
  }

  private void publishCharges(PlayerRef p, ChargeState state, int max, long now) {
    UUID id = p.uuid();
    long regenMillis = getConfig().chargeRegenMillis;
    int lit = (int) Math.floor(state.value);
    if (lit >= max || regenMillis <= 0L) {
      AbilityGlyphs.clear(id, this, HudGlyph.CHARGES);
      return;
    }
    long rechargeStartedAt = now - (long) ((state.value - lit) * regenMillis);
    if (!AbilityGlyphs.charges(id, this, lit, max, rechargeStartedAt, regenMillis) || state.refreshPending) {
      return;
    }
    int waitTicks = (int) Math.max(1L, (rechargeStartedAt + regenMillis - now + 49L) / 50L);
    state.refreshPending = Tasks.runEntity(p, () -> {
      state.refreshPending = false;
      if (charges.get(id) != state) {
        return;
      }
      long at = System.currentTimeMillis();
      state.refill(max, regenMillis, at);
      publishCharges(p, state, max, at);
    }, waitTicks);
  }

  public boolean reservesSneak(PlayerRef p) {
    if (!isPlayerEnabled(p) || getActiveLevel(p) <= 0 || !p.isSwimming()) {
      return false;
    }
    if (preference(p, PRIORITY) == Priority.TIDE) {
      for (Adaptation<?> candidate : getSkill().getAdaptations()) {
        if (candidate instanceof SeaborneTidecaller tide && tide.usesSneakTrigger(p)) {
          return false;
        }
      }
    }
    return true;
  }

  static boolean respectsFoodReserve(int food, float saturation, float exhaustion, double cost, int reserve) {
    if (food <= 0) {
      return false;
    }
    if (reserve <= 0) {
      return true;
    }
    int exhaustionUnits = (int) Math.ceil(Math.max(0D, exhaustion + cost) / 4D);
    int saturationUnits = (int) Math.ceil(Math.max(0D, saturation));
    return food - Math.max(0, exhaustionUnits - saturationUnits) >= reserve;
  }

  private void tryJet(PlayerRef p) {
    int level = getActiveLevel(p);
    if (level <= 0 || !p.isSwimming()) {
      return;
    }

    if (!respectsFoodReserve(p.foodLevel(), p.saturation(), p.exhaustion(), getConfig().hungerCost, preference(p, RESERVE).ordinal() * 4)) {
      FxPresets.failFizzle(this, p);
      return;
    }

    UUID id = p.uuid();
    int max = getMaxCharges(level);
    long now = System.currentTimeMillis();
    ChargeState state = charges.computeIfAbsent(id, key -> new ChargeState(max, now));
    state.refill(max, getConfig().chargeRegenMillis, now);
    if (state.value < 1D) {
      publishCharges(p, state, max, now);
      FxPresets.failFizzle(this, p);
      return;
    }

    state.value -= 1D;
    publishCharges(p, state, max, now);
    Vec3 direction = p.eyeLocation().direction().normalize();
    Vec3 velocity = p.velocity().multiply(0.4D).add(direction.multiply(getBurstForce(level)));
    if (velocity.lengthSquared() > MAX_RESULTING_VELOCITY * MAX_RESULTING_VELOCITY) {
      velocity = velocity.normalize().multiply(MAX_RESULTING_VELOCITY);
    }

    p.setVelocity(velocity);
    p.setExhaustion(p.exhaustion() + (float) getConfig().hungerCost);
    xp(p, getConfig().jetXp);
    addStat(p, "seaborne.hydro-jet.jets", 1);
    fx(p.location().add(0D, 0.5D, 0D), FxPriority.GAMEPLAY)
        .trail(Particles.BUBBLE, -direction.x(), -direction.y(), -direction.z(), 1.6D, 12)
        .particle(Particles.SPLASH, 10, 0D, 0.2D, 0D, 0.35D, 0.05D)
        .ring(Particles.BUBBLE, 0.6D, 8, 0.0D)
        .chord(Sounds.ENTITY_DOLPHIN_SPLASH, 0.6F, 1.3F, Sounds.ENTITY_PLAYER_SPLASH, 0.5F, 1.2F);
  }

  private double getBurstForce(int level) {
    return getConfig().burstForceBase + (getLevelPercent(level) * getConfig().burstForceFactor);
  }

  private int getMaxCharges(int level) {
    return maxCharges(getConfig().maxChargesBase, getConfig().maxChargesFactor, getLevelPercent(level));
  }

  static int maxCharges(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  static double refillCharges(double current, long elapsedMillis, long regenMillis, double max) {
    if (regenMillis <= 0L) {
      return max;
    }

    double refilled = current + ((double) Math.max(0L, elapsedMillis) / regenMillis);
    return Math.min(max, refilled);
  }

  @ConfigDescription("Tap sneak while swimming to burst forward on a jet of water. Costs hunger and consumes a charge.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base forward burst force applied while swimming.", impact = "Higher values launch you farther at low levels.")
    double burstForceBase = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional burst force gained across levels.", impact = "Higher values launch you much farther at higher levels.")
    double burstForceFactor = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of stored jet charges.", impact = "Higher values allow more consecutive bursts at low levels.")
    int maxChargesBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional stored charges gained across levels.", impact = "Higher values allow far more consecutive bursts at higher levels.")
    double maxChargesFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds to regenerate one jet charge.", impact = "Lower values refill charges faster.")
    long chargeRegenMillis = 2500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Exhaustion (hunger) cost applied per jet.", impact = "Higher values drain hunger faster per burst.")
    double hungerCost = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted per jet burst.", impact = "Higher values reward jetting with more skill XP.")
    double jetXp = 6;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 3;
    }
  }

  private static final class ChargeState {
    private double value;
    private long lastRefillAt;
    private boolean refreshPending;

    private ChargeState(double value, long lastRefillAt) {
      this.value = value;
      this.lastRefillAt = lastRefillAt;
    }

    private void refill(double max, long regenMillis, long now) {
      value = refillCharges(value, now - lastRefillAt, regenMillis, max);
      lastRefillAt = now;
    }
  }

  public enum Gesture { SINGLE, DOUBLE }

  public enum Reserve { NONE, FOUR, EIGHT }

  public enum Priority { HYDRO, TIDE }
}
