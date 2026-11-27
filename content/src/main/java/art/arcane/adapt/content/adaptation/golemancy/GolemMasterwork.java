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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRuneLayout;
import art.arcane.adapt.content.golem.GolemStat;
import art.arcane.adapt.content.golem.GolemStatDelta;
import art.arcane.adapt.content.golem.GolemStatSource;
import art.arcane.adapt.content.golem.GolemStats;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.plinth.InfusionRecipe;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GolemMasterwork extends SimpleAdaptation<GolemMasterwork.Config> {
  public static final Id ID = Id.adapt("golem/masterwork");
  static final String SOURCE = "masterwork";
  private static final int HEART_COST_LORE = 7;
  private static final long MILLIS_PER_TICK = 50L;
  private static final double MAX_TIER_THREE_BOOST = 4D;
  private static final Rgb AURA_COLOR = Rgb.of(0xFFC83D);
  private static final double AURA_RADIUS = 0.6D;
  private static final int AURA_POINTS = 6;
  private static final float AURA_SIZE = 0.8F;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.AURA);

  private final GolemStatSource healthStats = new HealthStats();
  private final GolemMastery.Source mastery = new Mastery();

  public GolemMasterwork() {
    super(ID);
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_STACK_LIMITS, "golem items stack up to their base item's limit"));
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.golemancy.masterwork");
    setIcon(Id.minecraft("heart_of_the_sea"));
    setInterval(auraIntervalMillis(getConfig()));
  }

  @Override
  public void unregister() {
    GolemStats.unregister(healthStats);
    GolemMastery.unregister(mastery);
    super.unregister();
  }




  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, maxMasterworks(config, level), 1);
    statLore(v, config.extraRuneSlots, 2);
    statLore(v, Format.pc(config.healthBonusFraction, 0), 3);
    if (GolemancyRules.unlocked(level, config.dualCoreLevel)) {
      unlockLore(v, Text.key("adapt.golemancy.masterwork.lore4"));
    }
    if (GolemancyRules.unlocked(level, config.tierUpLevel)) {
      unlockLore(v, Text.key("adapt.golemancy.masterwork.lore5"));
      statLore(v, Format.pc(config.tierThreeBoost, 0), 6);
    }
    GolemancyRuntime.plinthSettings().recipes()
        .levelSpan(infusion -> InfusionRecipe.MASTERWORK.equals(Id.tryParseAdapt(infusion.unlockAdaptation())) && infusion.runeTier() == 0 && infusion.unlockLevel() <= level)
        .ifPresent(span -> statLore(v, TextFormat.RED, "- ", span.label(), HEART_COST_LORE));
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
    for (PlayerProfile learner : learnedCandidates(System.currentTimeMillis())) {
      UUID owner = learner.player().uuid();
      if (ownerLevel(owner) < 1 || !showsAura(learner)) {
        continue;
      }
      for (GolemHandle handle : registry.get().loadedOf(owner)) {
        if (handle.state().masterwork() && !handle.isDormant()) {
          AdaptApi.scheduler().runEntity(handle.entity(), () -> aura(handle));
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
    PlayerProfile player = AdaptApi.players().find(owner).orElse(null);
    return player != null && getActiveSiblingLevel(player.player(), Id.adapt("golem/" + core.id() + "_core")) >= 1;
  }

  boolean showsAura(PlayerProfile learner) {
    return preferenceFor(getLevel(learner), learner.preferences().saved(getId(), GolemancyPreferences.AURA.id()).orElse(null),
        GolemancyPreferences.AURA) == CommonPreferences.Toggle.ON;
  }

  void aura(GolemHandle handle) {
    MobRef golem = handle.entity();
    if (handle.isRetired() || handle.isDormant() || !golem.isValid() || !handle.state().masterwork()) {
      return;
    }
    auraFx(golem);
  }

  void auraFx(MobRef golem) {
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

  static List<GolemStatDelta> healthStats(Config config, boolean masterwork, int level) {
    if (!masterwork || level < 1 || !(config.healthBonusFraction > 0D)) {
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
      return healthStats(getConfig(), true, ownerLevel(handle.owner()));
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
      costFactor = 0.6;
      initialCost = 3;
      baseCost = 5;
      maxLevel = 3;
      requires = new ArrayList<>(List.of("adapt:golem/runic_augments@3", "adapt:golem/animus_binding@5"));
    }
  }
}
