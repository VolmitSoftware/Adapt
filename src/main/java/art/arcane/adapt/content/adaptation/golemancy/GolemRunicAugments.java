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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.golemancy.GolemXpRules;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemRunicAugments extends SimpleAdaptation<GolemRunicAugments.Config> {
  static final String SOURCE = "runic_augments";
  static final String BLANK_RUNE_RECIPE = "golemancy-blank-rune";
  private static final int RECIPE_LEVEL = 1;
  private static final int TICKS_PER_SECOND = 20;
  private static final double MIN_WORK_INTERVAL = 0.05D;

  private final GolemStatSource runeStats = new RuneStats();
  private final GolemCapacity.Source runeCarry = new RuneCarry();
  private final GolemWorkSpeed.Source runeWorkSpeed = new RuneWorkSpeed();
  private final GolemRunes.Augments augments = new Augments();
  private volatile LayoutCache layouts;

  public GolemRunicAugments() {
    super("golem-runic-augments");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.runic_augments");
    setIcon(Material.DISC_FRAGMENT_5);
    registerBlankRuneRecipe(ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook()), getConfig().blankRuneRecipe);
  }

  @Override
  public void unregister() {
    GolemStats.unregister(runeStats);
    GolemCapacity.unregister(runeCarry);
    GolemWorkSpeed.unregister(runeWorkSpeed);
    GolemRunes.unregister(augments);
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemStats.register(runeStats);
    GolemCapacity.register(runeCarry);
    GolemWorkSpeed.register(runeWorkSpeed);
    GolemRunes.register(augments);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    int extra = RunicAugmentsRules.extraSlots(config.extraSlotsByLevel, level);
    if (extra > 0) {
      statLore(v, extra, 1);
    }
    statLore(v, RunicAugmentsRules.maxTier(config.maxTierByLevel, level), 2);
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(GolemancyMessages.RUNIC_AUGMENTS_LORE3));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    Entity source = GolemXpRules.golemSource(e.getDamager());
    Optional<GolemHandle> bound = GolemancyRuntime.handle(source);
    if (bound.isEmpty() || bound.get().isDormant()) {
      return;
    }
    ActiveRunes active = runes(bound.get());
    if (active.isEmpty()) {
      return;
    }
    Config config = getConfig();
    int igniteTicks = active.count(config.emberIgniteSecondsByTier, GolemRuneType.EMBER) * TICKS_PER_SECOND;
    if (igniteTicks > 0) {
      ignite(victim, igniteTicks);
    }
    int weakness = active.count(config.entropyWeaknessLevelByTier, GolemRuneType.ENTROPY);
    int weaknessTicks = active.count(config.entropyWeaknessSecondsByTier, GolemRuneType.ENTROPY) * TICKS_PER_SECOND;
    if (weakness > 0 && weaknessTicks > 0) {
      weaken(victim, weakness - 1, weaknessTicks);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    Ward ward = ward(e.getCause());
    if (ward == null) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(e.getEntity());
    if (bound.isEmpty()) {
      return;
    }
    ActiveRunes active = runes(bound.get());
    if (!active.isEmpty() && wards(getConfig(), active, ward)) {
      e.setCancelled(true);
    }
  }

  int level(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  GolemRuneLayout layout(GolemBody body, UUID owner, boolean masterwork) {
    return layout(body, level(owner), GolemMastery.perks(owner, masterwork).extraRuneSlots());
  }

  ActiveRunes runes(GolemHandle handle) {
    List<GolemRune> runes = handle.state().runes();
    if (runes.isEmpty()) {
      return ActiveRunes.NONE;
    }
    GolemMastery.Perks perks = GolemMastery.perks(handle);
    Map<GolemRuneType, Integer> active = layout(handle.body(), level(handle.owner()), perks.extraRuneSlots()).active(runes);
    return active.isEmpty() ? ActiveRunes.NONE : new ActiveRunes(active, perks.tierUp(), perks.tierThreeBoost());
  }

  GolemRuneEffects effects(GolemHandle handle) {
    ActiveRunes active = runes(handle);
    return active.isEmpty() ? GolemRuneEffects.NONE : runeEffects(getConfig(), active);
  }

  void ignite(LivingEntity victim, int ticks) {
    victim.setFireTicks(Math.max(victim.getFireTicks(), ticks));
  }

  void weaken(LivingEntity victim, int amplifier, int ticks) {
    victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, amplifier, false, true, true));
  }

  static List<GolemStatDelta> runeStats(Config config, ActiveRunes active) {
    List<GolemStatDelta> deltas = new ArrayList<>(6);
    addDelta(deltas, GolemStat.MOVEMENT_SPEED, true, active.value(config.swiftnessSpeedByTier, GolemRuneType.SWIFTNESS));
    addDelta(deltas, GolemStat.ATTACK_DAMAGE, false, active.value(config.mightAttackByTier, GolemRuneType.MIGHT));
    addDelta(deltas, GolemStat.FOLLOW_RANGE, false, active.value(config.tideRadiusByTier, GolemRuneType.TIDE));
    addDelta(deltas, GolemStat.WATER_MOVEMENT_EFFICIENCY, false, active.value(config.tideSwimSpeedByTier, GolemRuneType.TIDE));
    addDelta(deltas, GolemStat.ARMOR, false, active.value(config.bulwarkArmorByTier, GolemRuneType.BULWARK));
    addDelta(deltas, GolemStat.ARMOR_TOUGHNESS, false, active.value(config.bulwarkToughnessByTier, GolemRuneType.BULWARK));
    addDelta(deltas, GolemStat.KNOCKBACK_RESISTANCE, false, active.value(config.bulwarkKnockbackResistanceByTier, GolemRuneType.BULWARK));
    return deltas;
  }

  static GolemCapacity.Contribution runeCarry(Config config, ActiveRunes active) {
    double bonus = active.value(config.mightCarryByTier, GolemRuneType.MIGHT);
    return Double.isFinite(bonus) && bonus != 0D ? GolemCapacity.Contribution.bonus(bonus) : GolemCapacity.Contribution.NONE;
  }

  static double runeWorkInterval(Config config, ActiveRunes active) {
    double reduction = active.value(config.swiftnessWorkIntervalByTier, GolemRuneType.SWIFTNESS);
    return Double.isFinite(reduction) ? Math.clamp(1D - reduction, MIN_WORK_INTERVAL, 1D) : 1D;
  }

  static GolemRuneEffects runeEffects(Config config, ActiveRunes active) {
    return new GolemRuneEffects(
        active.value(config.tideRadiusByTier, GolemRuneType.TIDE),
        active.count(config.orderChannelsByTier, GolemRuneType.ORDER),
        1D - active.value(config.orderReclaimDelayByTier, GolemRuneType.ORDER),
        active.value(config.entropyBonusYieldByTier, GolemRuneType.ENTROPY));
  }

  private GolemRuneLayout layout(GolemBody body, int level, int masterworkSlots) {
    Config config = getConfig();
    LayoutCache cache = layouts;
    if (cache == null || cache.config() != config) {
      cache = new LayoutCache(config);
      layouts = cache;
    }
    return cache.layout(body, level, masterworkSlots);
  }

  private void registerBlankRuneRecipe(ConfiguredRecipeParser parser, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, BLANK_RUNE_RECIPE);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static void addDelta(List<GolemStatDelta> deltas, GolemStat stat, boolean multiply, double amount) {
    if (!Double.isFinite(amount) || amount == 0D) {
      return;
    }
    deltas.add(multiply ? GolemStatDelta.multiplyBase(stat, amount) : GolemStatDelta.add(stat, amount));
  }

  private static boolean wards(Config config, ActiveRunes active, Ward ward) {
    return switch (ward) {
      case FIRE -> active.flag(config.emberFireImmuneByTier, GolemRuneType.EMBER);
      case LAVA -> active.flag(config.emberLavaImmuneByTier, GolemRuneType.EMBER);
      case WATER -> active.flag(config.tideWaterBreathingByTier, GolemRuneType.TIDE);
    };
  }

  private static Ward ward(EntityDamageEvent.DamageCause cause) {
    return switch (cause) {
      case FIRE, FIRE_TICK, HOT_FLOOR, CAMPFIRE -> Ward.FIRE;
      case LAVA -> Ward.LAVA;
      case DROWNING -> Ward.WATER;
      default -> null;
    };
  }

  private static int baseSlots(Config config, GolemBody body) {
    return switch (body) {
      case FROST -> config.frostSlots;
      case COPPER -> config.copperSlots;
      case IRON -> config.ironSlots;
      case BONE -> 0;
    };
  }

  private enum Ward {
    FIRE,
    LAVA,
    WATER
  }

  private static final class LayoutCache {
    private final Config config;
    private final Map<GolemBody, Set<GolemRuneType>> blocks;
    private final int maxLevel;
    private final Map<LayoutKey, GolemRuneLayout> layouts = new ConcurrentHashMap<>();

    private LayoutCache(Config config) {
      this.config = config;
      this.blocks = RunicAugmentsRules.bodyBlocks(config.runeBodyBlocks, Adapt::warn);
      this.maxLevel = Math.max(1, config.maxLevel);
    }

    private Config config() {
      return config;
    }

    private GolemRuneLayout layout(GolemBody body, int level, int masterworkSlots) {
      if (body == GolemBody.BONE) {
        return GolemRuneLayout.NONE;
      }
      LayoutKey key = new LayoutKey(body, Math.clamp(level, 0, maxLevel), Math.clamp(masterworkSlots, 0, GolemRuneLayout.MAX_SLOTS));
      return layouts.computeIfAbsent(key, this::compute);
    }

    private GolemRuneLayout compute(LayoutKey key) {
      return RunicAugmentsRules.layout(baseSlots(config, key.body()), config.extraSlotsByLevel, config.maxTierByLevel, maxLevel,
          blocks.getOrDefault(key.body(), Set.of()), key.level(), key.masterworkSlots());
    }
  }

  private record LayoutKey(GolemBody body, int level, int masterworkSlots) {
  }

  private final class RuneStats implements GolemStatSource {
    @Override
    public String id() {
      return SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      ActiveRunes active = runes(handle);
      return active.isEmpty() ? List.of() : runeStats(getConfig(), active);
    }
  }

  private final class RuneCarry implements GolemCapacity.Source {
    @Override
    public String id() {
      return SOURCE;
    }

    @Override
    public GolemCapacity.Contribution carry(GolemHandle handle) {
      ActiveRunes active = runes(handle);
      return active.isEmpty() ? GolemCapacity.Contribution.NONE : runeCarry(getConfig(), active);
    }
  }

  private final class RuneWorkSpeed implements GolemWorkSpeed.Source {
    @Override
    public String id() {
      return SOURCE;
    }

    @Override
    public double intervalMultiplier(GolemHandle handle) {
      ActiveRunes active = runes(handle);
      return active.isEmpty() ? 1D : runeWorkInterval(getConfig(), active);
    }
  }

  private final class Augments implements GolemRunes.Augments {
    @Override
    public GolemRuneLayout layout(GolemBody body, UUID owner, boolean masterwork) {
      return GolemRunicAugments.this.layout(body, owner, masterwork);
    }

    @Override
    public GolemRuneEffects effects(GolemHandle handle) {
      return GolemRunicAugments.this.effects(handle);
    }
  }

  @ConfigDescription("Lets you infuse runes at the Animus Plinth and slot them into your bound golems for speed, carrying, fire, reach, channels, yield and armor.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Extra rune slots every bound golem you own gets on top of its body's slots, by Runic Augments level from level 1 upward.", impact = "Higher values let golems hold more runes.")
    List<Integer> extraSlotsByLevel = new ArrayList<>(List.of(0, 0, 1, 1, 2));
    @ConfigDoc(value = "Highest rune tier your bound golems can use, by Runic Augments level from level 1 upward. Slotted runes above it stay in place but do nothing.", impact = "Higher values let golems use stronger runes sooner.")
    List<Integer> maxTierByLevel = new ArrayList<>(List.of(1, 1, 2, 2, 3));
    @ConfigDoc(value = "Rune slots a bound frost golem has before Runic Augments adds extra slots, from 0 to 9.", impact = "Higher values let frost golems hold more runes.")
    int frostSlots = 1;
    @ConfigDoc(value = "Rune slots a bound copper golem has before Runic Augments adds extra slots, from 0 to 9.", impact = "Higher values let copper golems hold more runes.")
    int copperSlots = 2;
    @ConfigDoc(value = "Rune slots a bound iron golem has before Runic Augments adds extra slots, from 0 to 9.", impact = "Higher values let iron golems hold more runes.")
    int ironSlots = 3;
    @ConfigDoc(value = "Runes a golem body cannot use, as aspect:body entries such as ember:frost.", impact = "Listed runes cannot be slotted into that body, and any already slotted do nothing.")
    List<String> runeBodyBlocks = new ArrayList<>(List.of("ember:frost"));
    @ConfigDoc(value = "Crafting recipe for the Blank Rune, unlocked at Runic Augments level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe blankRuneRecipe = new ConfiguredShapedRecipe()
        .rows(" A ", "ASA", " A ")
        .key('A', "AMETHYST_SHARD")
        .key('S', "SMOOTH_STONE")
        .result("golemancy:blank_rune", 2);
    @ConfigDoc(value = "Fraction added to a golem's movement speed by a Swiftness rune of tier 1, 2 and 3. 0.1 is 10% faster.", impact = "Higher values make Swiftness golems move faster.")
    List<Double> swiftnessSpeedByTier = new ArrayList<>(List.of(0.10, 0.20, 0.30));
    @ConfigDoc(value = "Fraction cut from the time between a golem's work actions by a Swiftness rune of tier 1, 2 and 3. 0.1 is 10% shorter.", impact = "Higher values make Swiftness golems work faster.")
    List<Double> swiftnessWorkIntervalByTier = new ArrayList<>(List.of(0.10, 0.20, 0.30));
    @ConfigDoc(value = "Fraction added to a golem's carry capacity by a Might rune of tier 1, 2 and 3. 0.5 is 50% more.", impact = "Higher values let Might golems carry more items.")
    List<Double> mightCarryByTier = new ArrayList<>(List.of(0.5, 1.0, 1.5));
    @ConfigDoc(value = "Attack damage added to a golem by a Might rune of tier 1, 2 and 3.", impact = "Higher values make Might golems hit harder.")
    List<Double> mightAttackByTier = new ArrayList<>(List.of(2.0, 4.0, 6.0));
    @ConfigDoc(value = "Seconds an Ember rune of tier 1, 2 and 3 sets whatever its golem hits on fire.", impact = "Higher values make targets burn longer.")
    List<Integer> emberIgniteSecondsByTier = new ArrayList<>(List.of(2, 4, 6));
    @ConfigDoc(value = "Whether an Ember rune of tier 1, 2 and 3 makes its golem immune to fire damage.", impact = "Set a tier to true to protect its golems from fire and burning.")
    List<Boolean> emberFireImmuneByTier = new ArrayList<>(List.of(false, true, true));
    @ConfigDoc(value = "Whether an Ember rune of tier 1, 2 and 3 makes its golem immune to lava damage.", impact = "Set a tier to true to protect its golems from lava.")
    List<Boolean> emberLavaImmuneByTier = new ArrayList<>(List.of(false, false, true));
    @ConfigDoc(value = "Blocks a Tide rune of tier 1, 2 and 3 adds to its golem's work radius, to the range in which a Companion or Guardian golem attacks mobs (up to the skill's leashRadius), and to its follow range.", impact = "Higher values let Tide golems work, guard and notice mobs farther away.")
    List<Double> tideRadiusByTier = new ArrayList<>(List.of(4.0, 8.0, 12.0));
    @ConfigDoc(value = "Whether a Tide rune of tier 1, 2 and 3 lets its golem breathe underwater.", impact = "Set a tier to true to protect its golems from drowning and water damage.")
    List<Boolean> tideWaterBreathingByTier = new ArrayList<>(List.of(false, true, true));
    @ConfigDoc(value = "Water movement efficiency a Tide rune of tier 1, 2 and 3 adds to its golem. 1.0 moves through water as fast as on land.", impact = "Higher values make Tide golems move faster in water.")
    List<Double> tideSwimSpeedByTier = new ArrayList<>(List.of(0.0, 0.0, 1.0));
    @ConfigDoc(value = "Extra channels an Order rune of tier 1, 2 and 3 gives its golem on top of its one channel.", impact = "Higher values let Order golems listen to more channels at once.")
    List<Integer> orderChannelsByTier = new ArrayList<>(List.of(1, 2, 3));
    @ConfigDoc(value = "Fraction cut from the time an Order golem waits before it retries a job it gave up on, by rune tier 1, 2 and 3. 0.5 is half as long.", impact = "Higher values make Order golems retry blocked or failed jobs sooner.")
    List<Double> orderReclaimDelayByTier = new ArrayList<>(List.of(0.0, 0.5, 0.75));
    @ConfigDoc(value = "Chance for each drop of a Harvester, Lumber, Breaker or Angler golem to be doubled by an Entropy rune of tier 1, 2 and 3. 0.05 is 5%.", impact = "Higher values give Entropy golems more bonus drops.")
    List<Double> entropyBonusYieldByTier = new ArrayList<>(List.of(0.05, 0.10, 0.15));
    @ConfigDoc(value = "Weakness level an Entropy rune of tier 1, 2 and 3 puts on whatever its golem hits. 0 applies none.", impact = "Higher values weaken targets more.")
    List<Integer> entropyWeaknessLevelByTier = new ArrayList<>(List.of(0, 1, 2));
    @ConfigDoc(value = "Seconds the Weakness from an Entropy rune of tier 1, 2 and 3 lasts.", impact = "Higher values keep targets weakened longer.")
    List<Integer> entropyWeaknessSecondsByTier = new ArrayList<>(List.of(0, 3, 3));
    @ConfigDoc(value = "Armor added to a golem by a Bulwark rune of tier 1, 2 and 3.", impact = "Higher values make Bulwark golems take less damage.")
    List<Double> bulwarkArmorByTier = new ArrayList<>(List.of(4.0, 8.0, 12.0));
    @ConfigDoc(value = "Armor toughness added to a golem by a Bulwark rune of tier 1, 2 and 3.", impact = "Higher values make Bulwark golems take less damage from strong hits.")
    List<Double> bulwarkToughnessByTier = new ArrayList<>(List.of(0.0, 2.0, 4.0));
    @ConfigDoc(value = "Knockback resistance added to a golem by a Bulwark rune of tier 1, 2 and 3. 1.0 means no knockback.", impact = "Higher values make Bulwark golems harder to push back.")
    List<Double> bulwarkKnockbackResistanceByTier = new ArrayList<>(List.of(0.0, 0.0, 0.5));

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:2"));
    }
  }
}
