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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GolemMasterwork extends SimpleAdaptation<GolemMasterwork.Config> {
  static final String SOURCE = "masterwork";
  private static final long MILLIS_PER_TICK = 50L;
  private static final double MAX_TIER_THREE_BOOST = 4D;
  private static final Color AURA_COLOR = Color.fromRGB(0xFFC83D);
  private static final double AURA_RADIUS = 0.6D;
  private static final int AURA_POINTS = 6;
  private static final float AURA_SIZE = 0.8F;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.AURA);

  private final GolemStatSource healthStats = new HealthStats();
  private final GolemMastery.Source mastery = new Mastery();

  public GolemMasterwork() {
    super("golem-masterwork");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.masterwork");
    setIcon(Material.HEART_OF_THE_SEA);
    setInterval(auraIntervalMillis(getConfig()));
  }

  @Override
  public void unregister() {
    GolemStats.unregister(healthStats);
    GolemMastery.unregister(mastery);
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, maxMasterworks(config, level), 1);
    statLore(v, config.extraRuneSlots, 2);
    statLore(v, Form.pc(config.healthBonusFraction, 0), 3);
    if (GolemancyRules.unlocked(level, config.dualCoreLevel)) {
      unlockLore(v, GolemancyMessages.MASTERWORK_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.tierUpLevel)) {
      unlockLore(v, GolemancyMessages.MASTERWORK_LORE5);
      statLore(v, Form.pc(config.tierThreeBoost, 0), 6);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public void onTick() {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (AdaptPlayer learner : learnedCandidates(M.ms())) {
      UUID owner = learner.getPlayer().getUniqueId();
      if (ownerLevel(owner) < 1 || !showsAura(learner)) {
        continue;
      }
      for (GolemHandle handle : registry.get().loadedOf(owner)) {
        if (handle.state().masterwork() && !handle.isDormant()) {
          J.runEntity(handle.entity(), () -> aura(handle));
        }
      }
    }
  }

  @Override
  protected void onRuntimeActivated() {
    GolemStats.register(healthStats);
    GolemMastery.register(mastery);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.healthBonusFraction = GolemancyRules.nonNegative(loadedConfig.healthBonusFraction);
    loadedConfig.extraRuneSlots = Math.clamp(loadedConfig.extraRuneSlots, 0, GolemRuneLayout.MAX_SLOTS);
    loadedConfig.dualCoreLevel = Math.max(1, loadedConfig.dualCoreLevel);
    loadedConfig.tierUpLevel = Math.max(1, loadedConfig.tierUpLevel);
    loadedConfig.tierThreeBoost = Math.min(MAX_TIER_THREE_BOOST, GolemancyRules.nonNegative(loadedConfig.tierThreeBoost));
    loadedConfig.heartDeathDropChance = Double.isFinite(loadedConfig.heartDeathDropChance)
        ? Math.clamp(loadedConfig.heartDeathDropChance, 0D, 1D)
        : 0D;
    loadedConfig.auraIntervalTicks = Math.max(1, loadedConfig.auraIntervalTicks);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    setInterval(auraIntervalMillis(newConfig));
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  boolean coreUnlocked(UUID owner, GolemCoreType core) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player != null && getActiveSiblingLevel(player.getPlayer(), "golem-" + core.id() + "-core") >= 1;
  }

  boolean showsAura(AdaptPlayer learner) {
    return PlayerPreferences.resolve(this, learner.getData(), getLevel(learner), GolemancyPreferences.AURA) == CommonPreferences.Toggle.ON;
  }

  void aura(GolemHandle handle) {
    Mob golem = handle.entity();
    if (handle.isRetired() || handle.isDormant() || !golem.isValid() || !handle.state().masterwork()) {
      return;
    }
    auraFx(golem);
  }

  void auraFx(Mob golem) {
    fx(golem, FxPriority.AMBIENT).dustRing(AURA_COLOR, AURA_RADIUS, AURA_POINTS, AURA_SIZE);
  }

  static GolemMastery.Perks perks(Config config, int level) {
    if (level < 1) {
      return GolemMastery.Perks.NONE;
    }
    boolean tierUp = GolemancyRules.unlocked(level, config.tierUpLevel);
    return new GolemMastery.Perks(config.extraRuneSlots, GolemancyRules.unlocked(level, config.dualCoreLevel), tierUp, tierUp ? config.tierThreeBoost : 0D);
  }

  static int maxMasterworks(Config config, int level) {
    return level < 1 ? 0 : Math.max(0, AnimusBindingRules.atLevel(config.maxMasterworksByLevel, level, 0));
  }

  static List<GolemStatDelta> healthStats(Config config, boolean masterwork, GolemBody body, int level) {
    if (!masterwork || body == GolemBody.BONE || level < 1 || !(config.healthBonusFraction > 0D)) {
      return List.of();
    }
    return List.of(GolemStatDelta.multiplyTotal(GolemStat.MAX_HEALTH, config.healthBonusFraction));
  }

  private static long auraIntervalMillis(Config config) {
    return Math.max(1, config.auraIntervalTicks) * MILLIS_PER_TICK;
  }

  private final class HealthStats implements GolemStatSource {
    @Override
    public String id() {
      return SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      if (!handle.state().masterwork()) {
        return List.of();
      }
      return healthStats(getConfig(), true, handle.body(), ownerLevel(handle.owner()));
    }
  }

  private final class Mastery implements GolemMastery.Source {
    @Override
    public GolemMastery.Perks perks(UUID owner) {
      return GolemMasterwork.perks(getConfig(), ownerLevel(owner));
    }

    @Override
    public int maxMasterworks(UUID owner) {
      return GolemMasterwork.maxMasterworks(getConfig(), ownerLevel(owner));
    }

    @Override
    public double heartDropChance() {
      return getConfig().heartDeathDropChance;
    }

    @Override
    public boolean coreUnlocked(UUID owner, GolemCoreType core) {
      return GolemMasterwork.this.coreUnlocked(owner, core);
    }
  }

  @ConfigDescription("Infuse a Masterwork Heart at the Animus Plinth and slot it into a golem for an extra rune slot, more health, a second core and stronger runes.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Masterwork golems you can have at once, by Masterwork level from level 1 upward. Masterworks that are unloaded, packed or shattered count.", impact = "Higher values let you designate more masterwork golems.")
    List<Integer> maxMasterworksByLevel = new ArrayList<>(List.of(1, 1, 2));
    @ConfigDoc(value = "Fraction added to a masterwork golem's maximum health. 0.25 is 25% more. Values below 0 load as 0.", impact = "Higher values make masterwork golems tougher.")
    double healthBonusFraction = 0.25;
    @ConfigDoc(value = "Rune slots a masterwork golem has on top of its body's slots and your Runic Augments slots, from 0 to 9.", impact = "Higher values let masterwork golems hold more runes.")
    int extraRuneSlots = 1;
    @ConfigDoc(value = "Masterwork level at which a masterwork golem takes a second core and works both roles.", impact = "Lower values unlock the second core sooner.")
    int dualCoreLevel = 2;
    @ConfigDoc(value = "Masterwork level at which a masterwork golem's runes act one tier higher.", impact = "Lower values unlock stronger runes sooner.")
    int tierUpLevel = 3;
    @ConfigDoc(value = "Fraction added to the numbers of a tier 3 rune in a masterwork golem once its runes act one tier higher, from 0 to 4. 0.25 is 25% more. Values outside that range load as the nearest limit.", impact = "Higher values make tier 3 runes in masterwork golems stronger.")
    double tierThreeBoost = 0.25;
    @ConfigDoc(value = "Chance a masterwork golem drops its Masterwork Heart when it dies, from 0 to 1. A golem that leaves a Shattered Core keeps its Heart instead.", impact = "Lower values make a masterwork's death cost its Heart more often.")
    double heartDeathDropChance = 1.0;
    @ConfigDoc(value = "Ticks between the gold sparkles around your awake masterwork golems, at least 1.", impact = "Higher values show the aura less often.")
    int auraIntervalTicks = 40;

    public Config() {
      maxLevel = 3;
      requires = new ArrayList<>(List.of("golem-runic-augments:3", "golem-animus-binding:5"));
    }
  }
}
