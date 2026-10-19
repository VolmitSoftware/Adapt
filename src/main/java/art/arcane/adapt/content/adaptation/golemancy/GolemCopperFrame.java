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

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GolemCopperFrame extends SimpleAdaptation<GolemCopperFrame.Config> {
  static final String STAT_SOURCE = "copper_frame";
  static final String PATINA_SOURCE = "patina";
  private static final int SPEED_II_AMPLIFIER = 1;
  private static final int TICKS_PER_SECOND = 20;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final GolemStatSource frameStats = new FrameStats();
  private final GolemStatSource patinaStats = new PatinaStats();
  private final GolemCapacity.Source frameCarry = new FrameCarry();
  private final GolemWorkSpeed.Source overdriveSpeed = new OverdriveSpeed();
  private final Map<UUID, CopperFrameRules.Overdrive> overdrives = new ConcurrentHashMap<>();

  public GolemCopperFrame() {
    super("golem-copper-frame");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.copper_frame");
    setIcon(Material.OXIDIZED_COPPER);
    setInterval(SWEEP_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    GolemStats.unregister(frameStats);
    GolemStats.unregister(patinaStats);
    GolemCapacity.unregister(frameCarry);
    GolemWorkSpeed.unregister(overdriveSpeed);
    overdrives.clear();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemStats.register(frameStats);
    GolemStats.register(patinaStats);
    GolemCapacity.register(frameCarry);
    GolemWorkSpeed.register(overdriveSpeed);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.overdriveRadius = CopperFrameRules.overdriveRadius(loadedConfig.overdriveRadius);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, Math.max(0, AnimusBindingRules.atLevel(config.healthBonusByLevel, level, 0)), 1);
    statLore(v, CopperFrameRules.carry(config.carryByLevel, level), 2);
    double speed = AnimusBindingRules.atLevel(config.speedBonusByLevel, level, 0D);
    if (Double.isFinite(speed) && speed > 0D) {
      statLore(v, Form.pc(speed, 0), 3);
    }
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(GolemancyMessages.COPPER_FRAME_LORE4));
    statLore(v, CopperFrameRules.patinaArmor(1, level, config.patinaArmorPerStage, config.patinaArmorDoubleLevel), 5);
    double penalty = -CopperFrameRules.patinaSpeed(1, level, config.patinaSpeedPenaltyPerStage, config.patinaPenaltyRemovedLevel);
    if (penalty > 0D) {
      statLore(v, C.RED, "- ", Form.pc(penalty, 0), 6);
    }
    if (GolemancyRules.unlocked(level, config.overdriveLevel)) {
      statLore(v, overdriveSeconds(config), 7);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !overdrives.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    overdrives.values().removeIf(overdrive -> CopperFrameRules.expired(overdrive, now));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(LightningStrikeEvent e) {
    Location strike = e.getLightning().getLocation();
    double radius = getConfig().overdriveRadius;
    long now = M.ms();
    for (CopperGolem golem : PaperCompat.nearbyEntitiesByType(CopperGolem.class, strike, radius)) {
      if (CopperFrameRules.withinRadius(golem.getLocation().distanceSquared(strike), radius)) {
        overdrive(golem, now);
      }
    }
  }

  @Override
  public void onLevelChanged(Player player, int level) {
    GolemancySupport.recordOwnerLevel(getName(), player.getUniqueId(), level);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    AdaptPlayer owner = getServer().getOnlineAdaptPlayer(player.getUniqueId());
    if (owner != null) {
      GolemancySupport.recordOwnerLevel(getName(), player.getUniqueId(), getLevel(owner));
    }
  }

  int ownerLevel(GolemHandle handle) {
    AdaptPlayer owner = getServer().getOnlineAdaptPlayer(handle.owner());
    if (owner == null) {
      return handle.ownerLevel(getName());
    }
    int level = getLevel(owner);
    handle.recordOwnerLevel(getName(), level);
    return level;
  }

  void boost(CopperGolem golem, int ticks) {
    golem.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, SPEED_II_AMPLIFIER, false, true, true));
  }

  static List<GolemStatDelta> frameStats(Config config, GolemBody body, int level) {
    return body == GolemBody.COPPER ? CopperFrameRules.frameDeltas(config.healthBonusByLevel, config.speedBonusByLevel, level) : List.of();
  }

  static List<GolemStatDelta> patinaStats(Config config, GolemBody body, int stage, int level) {
    return body == GolemBody.COPPER
        ? CopperFrameRules.patinaDeltas(stage, level, config.patinaArmorPerStage, config.patinaSpeedPenaltyPerStage,
        config.patinaArmorDoubleLevel, config.patinaPenaltyRemovedLevel)
        : List.of();
  }

  static GolemCapacity.Contribution carry(Config config, GolemBody body, int level) {
    int carry = body == GolemBody.COPPER ? CopperFrameRules.carry(config.carryByLevel, level) : 0;
    return carry > 0 ? GolemCapacity.Contribution.atLeast(carry) : GolemCapacity.Contribution.NONE;
  }

  private void overdrive(CopperGolem golem, long now) {
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem);
    if (bound.isEmpty() || bound.get().isDormant()) {
      return;
    }
    GolemHandle handle = bound.get();
    Config config = getConfig();
    if (!GolemancyRules.unlocked(ownerLevel(handle), config.overdriveLevel)) {
      return;
    }
    AtomicBoolean claimed = new AtomicBoolean();
    overdrives.compute(handle.id(), (id, current) -> {
      CopperFrameRules.Overdrive next = CopperFrameRules.overdrive(current, now, config.overdriveSeconds, config.overdriveCooldownSeconds);
      claimed.set(next != null);
      return next == null ? current : next;
    });
    if (claimed.get()) {
      boost(golem, overdriveSeconds(config) * TICKS_PER_SECOND);
    }
  }

  private static int overdriveSeconds(Config config) {
    return Math.max(1, config.overdriveSeconds);
  }

  private final class FrameStats implements GolemStatSource {
    @Override
    public String id() {
      return STAT_SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      return handle.body() == GolemBody.COPPER ? frameStats(getConfig(), GolemBody.COPPER, ownerLevel(handle)) : List.of();
    }
  }

  private final class PatinaStats implements GolemStatSource {
    @Override
    public String id() {
      return PATINA_SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      if (handle.body() != GolemBody.COPPER || handle.patinaStage() < 1) {
        return List.of();
      }
      return patinaStats(getConfig(), GolemBody.COPPER, handle.patinaStage(), ownerLevel(handle));
    }
  }

  private final class FrameCarry implements GolemCapacity.Source {
    @Override
    public String id() {
      return STAT_SOURCE;
    }

    @Override
    public GolemCapacity.Contribution carry(GolemHandle handle) {
      return handle.body() == GolemBody.COPPER ? GolemCopperFrame.carry(getConfig(), GolemBody.COPPER, ownerLevel(handle)) : GolemCapacity.Contribution.NONE;
    }
  }

  private final class OverdriveSpeed implements GolemWorkSpeed.Source {
    @Override
    public String id() {
      return STAT_SOURCE;
    }

    @Override
    public double intervalMultiplier(GolemHandle handle) {
      if (handle.body() != GolemBody.COPPER || overdrives.isEmpty()) {
        return 1D;
      }
      return CopperFrameRules.workIntervalMultiplier(overdrives.get(handle.id()), M.ms(), getConfig().overdriveWorkIntervalMultiplier);
    }
  }

  @ConfigDescription("Lets you bind copper golems and strengthens them with extra health, carry capacity and speed, plus armor from their patina.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Extra maximum health for your bound copper golems, by Copper Frame level from level 1 upward.", impact = "Higher values make copper golems tougher; a golem heals by any gain above the highest maximum health it has had.")
    List<Integer> healthBonusByLevel = new ArrayList<>(List.of(4, 8, 12, 16, 20));
    @ConfigDoc(value = "Items your bound copper golems can carry at once, by Copper Frame level from level 1 upward. A value below the copper body's bodyCarryCopper keeps that base.", impact = "Higher values let working copper golems move more items per trip.")
    List<Integer> carryByLevel = new ArrayList<>(List.of(16, 32, 48, 64, 128));
    @ConfigDoc(value = "Fraction added to your bound copper golems' movement speed, by Copper Frame level from level 1 upward. 0.05 is 5% faster.", impact = "Higher values make copper golems walk faster.")
    List<Double> speedBonusByLevel = new ArrayList<>(List.of(0.0, 0.05, 0.10, 0.15, 0.20));
    @ConfigDoc(value = "Armor a bound copper golem gains for each oxidation stage, from 0 when unaffected to 3 when oxidized.", impact = "Higher values make weathered copper golems tougher.")
    int patinaArmorPerStage = 2;
    @ConfigDoc(value = "Fraction of movement speed a bound copper golem loses for each oxidation stage. 0.05 is 5% slower per stage.", impact = "Lower values keep weathered copper golems faster; 0 removes the slowdown.")
    double patinaSpeedPenaltyPerStage = 0.05;
    @ConfigDoc(value = "Copper Frame level at which patina armor is doubled.", impact = "Lower values double patina armor sooner.")
    int patinaArmorDoubleLevel = 3;
    @ConfigDoc(value = "Copper Frame level at which patina no longer slows copper golems.", impact = "Lower values remove the patina slowdown sooner.")
    int patinaPenaltyRemovedLevel = 5;
    @ConfigDoc(value = "Copper Frame level at which nearby lightning triggers Static Overdrive.", impact = "Lower values unlock Static Overdrive sooner.")
    int overdriveLevel = 4;
    @ConfigDoc(value = "Blocks from a lightning strike within which your bound copper golems gain Static Overdrive, from 0 to 64.", impact = "Higher values let lightning farther away overdrive your golems.")
    double overdriveRadius = 16;
    @ConfigDoc(value = "Seconds of Speed II and faster work that Static Overdrive gives.", impact = "Higher values keep golems overdriven longer.")
    int overdriveSeconds = 60;
    @ConfigDoc(value = "Seconds after a Static Overdrive starts before the same golem can be overdriven again.", impact = "Lower values let lightning overdrive a golem more often.")
    int overdriveCooldownSeconds = 300;
    @ConfigDoc(value = "Multiplier on a bound copper golem's work interval while Static Overdrive lasts. 0.5 makes it work twice as fast.", impact = "Lower values make overdriven golems work faster; 1 leaves the work interval unchanged.")
    double overdriveWorkIntervalMultiplier = 0.5;

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
