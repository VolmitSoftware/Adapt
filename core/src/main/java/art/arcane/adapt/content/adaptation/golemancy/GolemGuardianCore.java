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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemGuardianCore extends SimpleAdaptation<GolemGuardianCore.Config> {
  static final String CORE_RECIPE = "golemancy-guardian-core";
  static final String NODE_RECIPE = "golemancy-guard-node";
  private static final int RECIPE_LEVEL = 1;
  private static final int MAX_LAST_STAND_AMPLIFIER = 3;
  private static final int TICKS_PER_SECOND = 20;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final List<PlayerPreference<?>> PREFERENCES =
      List.of(CommonPreferences.ENABLED, GolemancyPreferences.INTERCEPT, GolemancyPreferences.RETALIATE_PLAYERS);

  private final OwnerPreferences owners = new OwnerPreferences(this);
  private final GuardNodeBehavior guard = new GuardNodeBehavior(this);
  private final GuardianTargetPolicy targets = new GuardianTargetPolicy(this, guard, M::ms);
  private final Map<UUID, Attack> attacks = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastStands = new ConcurrentHashMap<>();

  public GolemGuardianCore() {
    super("golem-guardian-core");
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_STACK_LIMITS, "golem items stack up to their base item's limit"));
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.guardian_core");
    setIcon(Id.minecraft("shield"));
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().guardianCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().guardNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    GolemancyRuntime.clearTargetPolicy(GolemCoreType.GUARDIAN, targets);
    NodeBehaviors.unregister(NodeType.GUARD, guard);
    attacks.clear();
    lastStands.clear();
    targets.forget();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemancyRuntime.setTargetPolicy(GolemCoreType.GUARDIAN, targets);
    NodeBehaviors.register(NodeType.GUARD, guard);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.sentryRange = GuardianRules.sentryRange(loadedConfig.sentryRange);
    loadedConfig.interceptIntervalMillis = GuardianRules.interceptInterval(loadedConfig.interceptIntervalMillis);
    loadedConfig.bulwarkRadius = GuardianRules.bulwarkRadius(loadedConfig.bulwarkRadius);
    loadedConfig.bulwarkDamageReduction = GuardianRules.fraction(loadedConfig.bulwarkDamageReduction, 0D);
    loadedConfig.lastStandHealthFraction = GuardianRules.fraction(loadedConfig.lastStandHealthFraction, 0D);
  }

  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, GuardianRules.perception(config.perceptionByLevel, level, 0D, GolemancyRuntime.brainSettings().leashRadius()), 1);
    if (GolemancyRules.unlocked(level, config.interceptLevel)) {
      unlockLore(v, GolemancyMessages.GUARDIAN_CORE_LORE2);
    }
    if (GolemancyRules.unlocked(level, config.retaliateLevel)) {
      unlockLore(v, GolemancyMessages.GUARDIAN_CORE_LORE3);
    }
    if (GolemancyRules.unlocked(level, config.bulwarkLevel)) {
      statLore(v, Form.pc(config.bulwarkDamageReduction, 0), 4);
    }
    if (GolemancyRules.unlocked(level, config.lastStandLevel)) {
      statLore(v, Math.max(1, config.lastStandTicks) / TICKS_PER_SECOND, 5);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    Config config = getConfig();
    if (preference == GolemancyPreferences.INTERCEPT) {
      return GolemancyRules.unlocked(getLevel(player), config.interceptLevel);
    }
    return preference != GolemancyPreferences.RETALIATE_PLAYERS || GolemancyRules.unlocked(getLevel(player), config.retaliateLevel);
  }

  @Override
  public boolean hasTickDemand() {
    return !attacks.isEmpty() || !lastStands.isEmpty() || targets.pending();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    long window = retaliateWindow();
    attacks.values().removeIf(attack -> now - attack.at() >= window);
    lastStands.values().removeIf(readyAt -> now >= readyAt);
    targets.sweep(now);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef owner)) {
      return;
    }
    LivingRef attacker = attacker(e.damager());
    if (attacker == null || attacker.uuid().equals(owner.uuid())) {
      return;
    }
    if (GolemancyRules.unlocked(getActiveLevel(owner), getConfig().retaliateLevel)) {
      attacks.put(owner.uuid(), new Attack(attacker, M.ms()));
    }
    alertGuardians(owner.uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityTargetEvent e) {
    LivingRef target = e.target();
    if (target instanceof PlayerRef owner) {
      alertGuardians(owner.uuid());
      return;
    }
    if (target instanceof MobRef golem) {
      GolemancyRuntime.handle(golem).filter(GolemGuardianCore::awakeGuardian).ifPresent(GolemGuardianCore::alert);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void shieldOwner(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef owner) || !GuardianRules.shields(e.cause())) {
      return;
    }
    Config config = getConfig();
    if (!GolemancyRules.unlocked(getActiveLevel(owner), config.bulwarkLevel) || !nearGuardian(owner, config.bulwarkRadius)) {
      return;
    }
    e.setDamage(GuardianRules.bulwarkDamage(e.damage(), config.bulwarkDamageReduction));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void guardianHurt(EntityDamageEvent e) {
    if (!(e.entity() instanceof MobRef golem)) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem).filter(GolemGuardianCore::awakeGuardian);
    if (bound.isEmpty()) {
      return;
    }
    GolemHandle handle = bound.get();
    alert(handle);
    Config config = getConfig();
    if (!GolemancyRules.unlocked(ownerLevel(handle), config.lastStandLevel)
        || !GuardianRules.lastStand(golem.health() - e.finalDamage(), GolemVitals.maxHealth(golem), config.lastStandHealthFraction)) {
      return;
    }
    long now = M.ms();
    Long readyAt = lastStands.get(handle.id());
    if (readyAt != null && now < readyAt) {
      return;
    }
    lastStands.put(handle.id(), now + Math.max(0L, config.lastStandCooldownMillis));
    golem.addEffect(new EffectInstance(RESISTANCE, Math.max(1, config.lastStandTicks),
        Math.min(MAX_LAST_STAND_AMPLIFIER, Math.max(0, config.lastStandAmplifier)), false, true, true));
  }

  Config config() {
    return getConfig();
  }

  int ownerLevel(GolemHandle handle) {
    return ownerLevel(handle.owner());
  }

  boolean canEngage(PlayerRef owner, MinionTargeting.Candidate candidate) {
    return MinionTargeting.canDamageOwned(this, owner, candidate);
  }

  boolean intercepts(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.INTERCEPT);
  }

  boolean retaliatesAgainstPlayers(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.RETALIATE_PLAYERS);
  }

  LivingRef recentAttacker(UUID owner, long nowMillis) {
    Attack attack = attacks.get(owner);
    return attack != null && nowMillis - attack.at() < retaliateWindow() ? attack.attacker() : null;
  }

  private long retaliateWindow() {
    return Math.max(0L, getConfig().retaliateWindowMillis);
  }

  private boolean nearGuardian(PlayerRef owner, double radius) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return false;
    }
    Loc at = owner.location();
    double reach = radius * radius;
    for (GolemHandle handle : registry.get().loadedOf(owner.uuid())) {
      if (!awakeGuardian(handle)) {
        continue;
      }
      MobRef golem = handle.entity();
      if (Tasks.isOwnedByCurrentRegion(golem) && golem.isValid() && at.world().equals(golem.world()) && golem.location().distanceSquared(at) <= reach) {
        return true;
      }
    }
    return false;
  }

  private static void alertGuardians(UUID owner) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (GolemHandle handle : registry.get().loadedOf(owner)) {
      if (awakeGuardian(handle)) {
        alert(handle);
      }
    }
  }

  private static void alert(GolemHandle handle) {
    GolemancyRuntime.brain(handle.entity()).ifPresent(GolemBrain::alert);
  }

  private static boolean awakeGuardian(GolemHandle handle) {
    return !handle.isDormant() && GolemMastery.cores(handle).contains(GolemCoreType.GUARDIAN);
  }

  private static LivingRef attacker(EntityRef damager) {
    if (damager instanceof ProjectileRef projectile) {
      return projectile.shooter() instanceof LivingRef shooter ? shooter : null;
    }
    return damager instanceof LivingRef living ? living : null;
  }

  private record Attack(LivingRef attacker, long at) {
  }

  @ConfigDescription("Golems bound with a Guardian Core defend their home, you or a Guard Node's area, and shield you as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Blocks around a Guardian's guard point within which it attacks hostile mobs, by Guardian Core level from level 1 upward. The guard point is you while the golem follows you, the spot where it was told to stay while it stays, and its home otherwise. Each value, plus the golem's Tide rune bonus, is clamped to 1 up to the skill's leashRadius.", impact = "Higher values let Guardians defend a wider area.")
    List<Integer> perceptionByLevel = new ArrayList<>(List.of(8, 12, 16, 20, 24));
    @ConfigDoc(value = "Lets Guardians attack creepers.", impact = "Turn on to have Guardians fight creepers, which can explode next to them.")
    boolean targetCreepers = false;
    @ConfigDoc(value = "Keeps Guardians from attacking mobs that have a custom name, unless Retaliate sends them after one that hurt you.", impact = "Turn off to let Guardians attack named mobs too.")
    boolean ignoreNamedMobs = true;
    @ConfigDoc(value = "Guardian Core level at which Guardians pull mobs that target you onto themselves with Intercept.", impact = "Lower values unlock Intercept sooner.")
    int interceptLevel = 2;
    @ConfigDoc(value = "Milliseconds between a Guardian's Intercept checks, at least 500.", impact = "Lower values pull mobs off you more often.")
    long interceptIntervalMillis = 3_000L;
    @ConfigDoc(value = "Guardian Core level at which Guardians attack whatever hurt you with Retaliate, including players where region protection allows PvP.", impact = "Lower values unlock Retaliate sooner.")
    int retaliateLevel = 3;
    @ConfigDoc(value = "Milliseconds after something hurts you during which your Guardians retaliate against it.", impact = "Higher values make Guardians chase attackers longer.")
    long retaliateWindowMillis = 5_000L;
    @ConfigDoc(value = "Guardian Core level at which you take less damage near your Guardians with Bulwark Presence.", impact = "Lower values unlock Bulwark Presence sooner.")
    int bulwarkLevel = 4;
    @ConfigDoc(value = "Blocks from one of your awake Guardians within which Bulwark Presence protects you, from 0 to 16.", impact = "Higher values protect you farther from your Guardians.")
    double bulwarkRadius = 4.0;
    @ConfigDoc(value = "Fraction of damage Bulwark Presence removes, from 0 to 1. 0.15 is 15% less. More Guardians nearby do not add to it.", impact = "Higher values make you take less damage near your Guardians.")
    double bulwarkDamageReduction = 0.15;
    @ConfigDoc(value = "Guardian Core level at which badly hurt Guardians gain Resistance with Last Stand.", impact = "Lower values unlock Last Stand sooner.")
    int lastStandLevel = 5;
    @ConfigDoc(value = "Fraction of maximum health, from 0 to 1, that a Guardian must drop below for Last Stand to trigger. 0.2 is 20%.", impact = "Higher values trigger Last Stand earlier in a fight.")
    double lastStandHealthFraction = 0.2;
    @ConfigDoc(value = "Ticks of Resistance that Last Stand gives.", impact = "Higher values make Last Stand last longer.")
    int lastStandTicks = 100;
    @ConfigDoc(value = "Resistance amplifier Last Stand gives. 0 is Resistance I and 1 is Resistance II. Values above 3 count as 3.", impact = "Higher values make Last Stand block more damage.")
    int lastStandAmplifier = 1;
    @ConfigDoc(value = "Milliseconds after Last Stand triggers before it can trigger again on the same Guardian.", impact = "Lower values let Last Stand trigger more often.")
    long lastStandCooldownMillis = 60_000L;
    @ConfigDoc(value = "Blocks within which frost Guardians shoot, from 1 to 10, the snow golem's throwing range. Frost Guardians attack only mobs this close to them and never chase mobs farther away.", impact = "Higher values let frost Guardians shoot mobs farther away.")
    double sentryRange = 10.0;
    @ConfigDoc(value = "Crafting recipe for the Guardian Core, unlocked at Guardian Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe guardianCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_SWORD")
        .key('Y', "IRON_INGOT")
        .key('Z', "SHIELD")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/guardian", 1);
    @ConfigDoc(value = "Crafting recipe for the Guard Node, unlocked at Guardian Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe guardNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "IRON_SWORD")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/guard", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
