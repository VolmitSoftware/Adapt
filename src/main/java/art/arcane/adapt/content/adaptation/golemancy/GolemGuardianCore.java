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
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
  private static final int TICKS_PER_SECOND = 20;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final GuardNodeBehavior guard = new GuardNodeBehavior(this);
  private final GuardianTargetPolicy targets = new GuardianTargetPolicy(this, guard, M::ms);
  private final Map<UUID, Attack> attacks = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastStands = new ConcurrentHashMap<>();

  public GolemGuardianCore() {
    super("golem-guardian-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.guardian_core");
    setIcon(Material.SHIELD);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, CORE_RECIPE, getConfig().guardianCoreRecipe);
    registerGolemRecipe(parser, NODE_RECIPE, getConfig().guardNodeRecipe);
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
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, GuardianRules.perception(config.perceptionByLevel, level, 0D, GolemancyRuntime.brainSettings().leashRadius()), 1);
    if (GuardianRules.unlocked(level, config.interceptLevel)) {
      unlockLore(v, GolemancyMessages.GUARDIAN_CORE_LORE2);
    }
    if (GuardianRules.unlocked(level, config.retaliateLevel)) {
      unlockLore(v, GolemancyMessages.GUARDIAN_CORE_LORE3);
    }
    if (GuardianRules.unlocked(level, config.bulwarkLevel)) {
      statLore(v, Form.pc(config.bulwarkDamageReduction, 0), 4);
    }
    if (GuardianRules.unlocked(level, config.lastStandLevel)) {
      statLore(v, Math.max(1, config.lastStandTicks) / TICKS_PER_SECOND, 5);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !attacks.isEmpty() || !lastStands.isEmpty() || targets.pending();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    long window = getConfig().retaliateWindowMillis;
    attacks.values().removeIf(attack -> now - attack.at() >= window);
    lastStands.values().removeIf(readyAt -> now >= readyAt);
    targets.sweep(now);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player owner)) {
      return;
    }
    LivingEntity attacker = attacker(e.getDamager());
    if (attacker == null || attacker.getUniqueId().equals(owner.getUniqueId())) {
      return;
    }
    if (GuardianRules.unlocked(getActiveLevel(owner), getConfig().retaliateLevel)) {
      attacks.put(owner.getUniqueId(), new Attack(attacker, M.ms()));
    }
    alertGuardians(owner.getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    LivingEntity target = e.getTarget();
    if (target instanceof Player owner) {
      alertGuardians(owner.getUniqueId());
      return;
    }
    if (target instanceof Mob golem) {
      GolemancyRuntime.handle(golem).filter(GolemGuardianCore::awakeGuardian).ifPresent(GolemGuardianCore::alert);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void shieldOwner(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player owner) || !GuardianRules.shields(e.getCause())) {
      return;
    }
    Config config = getConfig();
    if (!GuardianRules.unlocked(getActiveLevel(owner), config.bulwarkLevel) || !nearGuardian(owner, config.bulwarkRadius)) {
      return;
    }
    e.setDamage(GuardianRules.bulwarkDamage(e.getDamage(), config.bulwarkDamageReduction));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void guardianHurt(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Mob golem)) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem).filter(GolemGuardianCore::awakeGuardian);
    if (bound.isEmpty()) {
      return;
    }
    GolemHandle handle = bound.get();
    alert(handle);
    Config config = getConfig();
    if (!GuardianRules.unlocked(ownerLevel(handle), config.lastStandLevel)
        || !GuardianRules.lastStand(golem.getHealth() - e.getFinalDamage(), GolemVitals.maxHealth(golem), config.lastStandHealthFraction)) {
      return;
    }
    long now = M.ms();
    Long readyAt = lastStands.get(handle.id());
    if (readyAt != null && now < readyAt) {
      return;
    }
    lastStands.put(handle.id(), now + Math.max(0L, config.lastStandCooldownMillis));
    golem.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Math.max(1, config.lastStandTicks), Math.max(0, config.lastStandAmplifier),
        false, true, true));
  }

  Config config() {
    return getConfig();
  }

  int ownerLevel(GolemHandle handle) {
    AdaptPlayer owner = getServer().getOnlineAdaptPlayer(handle.owner());
    return owner == null ? 0 : getLevel(owner);
  }

  boolean canEngage(Player owner, MinionTargeting.Candidate candidate) {
    return MinionTargeting.canDamageOwned(this, owner, candidate);
  }

  LivingEntity recentAttacker(UUID owner, long nowMillis) {
    Attack attack = attacks.get(owner);
    return attack != null && nowMillis - attack.at() < getConfig().retaliateWindowMillis ? attack.attacker() : null;
  }

  private boolean nearGuardian(Player owner, double radius) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return false;
    }
    Location at = owner.getLocation();
    World world = at.getWorld();
    double reach = radius * radius;
    for (GolemHandle handle : registry.get().loadedOf(owner.getUniqueId())) {
      if (!awakeGuardian(handle)) {
        continue;
      }
      Mob golem = handle.entity();
      if (J.isOwnedByCurrentRegion(golem) && golem.isValid() && world.equals(golem.getWorld()) && golem.getLocation().distanceSquared(at) <= reach) {
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

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static LivingEntity attacker(Entity damager) {
    if (damager instanceof Projectile projectile) {
      return projectile.getShooter() instanceof LivingEntity shooter ? shooter : null;
    }
    return damager instanceof LivingEntity living ? living : null;
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private record Attack(LivingEntity attacker, long at) {
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
    @ConfigDoc(value = "Resistance amplifier Last Stand gives. 0 is Resistance I and 1 is Resistance II.", impact = "Higher values make Last Stand block more damage.")
    int lastStandAmplifier = 1;
    @ConfigDoc(value = "Milliseconds after Last Stand triggers before it can trigger again on the same Guardian.", impact = "Lower values let Last Stand trigger more often.")
    long lastStandCooldownMillis = 60_000L;
    @ConfigDoc(value = "Blocks within which frost Guardians shoot, from 1 to 10, the snow golem's throwing range. Frost Guardians attack only mobs this close to them and never chase mobs farther away.", impact = "Higher values let frost Guardians shoot mobs farther away.")
    double sentryRange = 10.0;
    @ConfigDoc(value = "Crafting recipe for the Guardian Core, unlocked at Guardian Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe guardianCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_SWORD")
        .key('Y', "IRON_INGOT")
        .key('Z', "SHIELD")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/guardian", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Guard Node, unlocked at Guardian Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
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
