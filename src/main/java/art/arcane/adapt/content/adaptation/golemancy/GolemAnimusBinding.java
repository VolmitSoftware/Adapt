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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRitualMarker;
import art.arcane.adapt.api.golem.GolemScale;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.reveal.GolemLabelPolicy;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemAnimusBinding extends SimpleAdaptation<GolemAnimusBinding.Config> implements GolemLabelPolicy {
  static final String CROOK_RECIPE = "golemancy-crook";
  static final String BLANK_CORE_RECIPE = "golemancy-blank-core";
  private static final String COPPER_FRAME = "golem-copper-frame";
  private static final String IRON_FRAME = "golem-iron-frame";
  private static final int RECIPE_LEVEL = 1;
  private static final double OWNER_REACH = 4D;
  private static final long MILLIS_PER_SECOND = 1000L;
  private static final int TICKS_PER_SECOND = 20;
  private static final int SOUND_INTERVAL_TICKS = 10;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.STATUS_LABELS);

  private final Map<UUID, BindingRitual> ritualsByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, BindingRitual> ritualsByOwner = new ConcurrentHashMap<>();

  public GolemAnimusBinding() {
    super("golem-animus-binding");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.animus_binding");
    setIcon(Material.FIREWORK_STAR);
    GolemancyRuntime.setProtectionPolicy(this);
    GolemancyRuntime.setLabelPolicy(this);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CROOK_RECIPE, getConfig().crookRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, BLANK_CORE_RECIPE, getConfig().blankCoreRecipe, RECIPE_LEVEL);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, golemCap(config, level, GolemancyRuntime.settings().hardCapPerPlayer()), 1);
    statLore(v, C.YELLOW, "* ", (double) ritualTicks(config, level) / TICKS_PER_SECOND, 2);
    statLore(v, C.RED, "- ", tithe(config, GolemBody.FROST, level), 3);
    statLore(v, C.RED, "- ", tithe(config, GolemBody.COPPER, level), 4);
    statLore(v, C.RED, "- ", tithe(config, GolemBody.IRON, level), 5);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean hidesLabels(Player owner) {
    return preference(owner, GolemancyPreferences.STATUS_LABELS) == GolemancyPreferences.Labels.HIDDEN;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    Player player = e.getPlayer();
    if (e.getHand() != EquipmentSlot.HAND) {
      if (ritualsByOwner.containsKey(player.getUniqueId())) {
        e.setCancelled(true);
      }
      return;
    }
    if (!player.isSneaking() || !(e.getRightClicked() instanceof Mob golem) || !J.isOwnedByCurrentRegion(golem)) {
      return;
    }
    Optional<GolemCoreType> core = GolemItems.coreOf(player.getInventory().getItemInMainHand());
    Optional<GolemBody> body = GolemBody.of(golem);
    if (core.isEmpty() || body.isEmpty() || body.get() == GolemBody.BONE) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    e.setCancelled(true);
    begin(new BindingTarget(player, golem, body.get(), core.get()), level);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    BindingRitual ritual = ritualsByGolem.get(e.getEntity().getUniqueId());
    if (ritual != null && AnimusBindingRules.interrupts(e.getCause(), e instanceof EntityDamageByEntityEvent)) {
      abort(ritual, true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    BindingRitual ritual = ritualsByOwner.get(e.getPlayer().getUniqueId());
    if (ritual != null) {
      onGolemThread(ritual, () -> abort(ritual, false));
    }
  }

  @Override
  public void unregister() {
    for (BindingRitual ritual : List.copyOf(ritualsByGolem.values())) {
      onGolemThread(ritual, () -> abort(ritual, false));
    }
    super.unregister();
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.ritualSecondsByLevel = AnimusBindingRules.clampRitualSeconds(loadedConfig.ritualSecondsByLevel);
  }

  boolean builderExclusive(Mob golem, UUID player, long nowMillis) {
    return AnimusBindingRules.builderExclusive(GolemBuildAttribution.builderOf(golem).orElse(null), GolemBuildAttribution.builtAt(golem), player,
        nowMillis, Math.max(0, getConfig().builderExclusiveSeconds) * MILLIS_PER_SECOND);
  }

  boolean villageGolemsAllowed() {
    return getConfig().allowBindingVillageGolems;
  }

  boolean bodyUnlocked(Player player, GolemBody body) {
    return AnimusBindingRules.bodyUnlocked(body, getActiveSiblingLevel(player, COPPER_FRAME), getActiveSiblingLevel(player, IRON_FRAME));
  }

  static boolean playerCreated(Mob golem) {
    return !(golem instanceof IronGolem iron) || iron.isPlayerCreated();
  }

  static int golemCap(Config config, int level, int hardCap) {
    return AnimusBindingRules.golemCap(config.golemCapByLevel, level, hardCap);
  }

  static int ritualTicks(Config config, int level) {
    return AnimusBindingRules.ritualTicks(config.ritualSecondsByLevel, level);
  }

  static int tithe(Config config, GolemBody body, int level) {
    return switch (body) {
      case FROST -> AnimusBindingRules.tithe(config.titheSnowByLevel, level);
      case COPPER -> AnimusBindingRules.tithe(config.titheCopperByLevel, level);
      case IRON -> AnimusBindingRules.tithe(config.titheIronByLevel, level);
      case BONE -> 0;
    };
  }

  private void begin(BindingTarget target, int level) {
    Player owner = target.owner();
    Mob golem = target.golem();
    long now = System.currentTimeMillis();
    if (busy(ritualsByOwner, owner.getUniqueId(), now) || busy(ritualsByGolem, golem.getUniqueId(), now)) {
      warn(owner, GolemancyMessages.BINDING_BUSY);
      return;
    }
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      warn(owner, GolemancyMessages.BINDING_UNAVAILABLE);
      return;
    }
    Assessment assessment = assess(target, level, registry.get());
    if (assessment.verdict() != BindingVerdict.READY) {
      deny(target, assessment);
      return;
    }
    int ticks = ritualTicks(getConfig(), level);
    BindingRitual ritual = new BindingRitual(target, ticks, now);
    ritualsByOwner.put(owner.getUniqueId(), ritual);
    ritualsByGolem.put(golem.getUniqueId(), ritual);
    GolemRitualMarker.start(golem, ritual.wasAware());
    golem.setAware(false);
    FxTimeline timeline = ritualTimeline(target, ticks);
    ritual.attach(timeline);
    timeline.start();
    schedulePulse(ritual);
  }

  private void schedulePulse(BindingRitual ritual) {
    if (!J.runEntity(ritual.target().golem(), () -> pulse(ritual), 1, () -> abort(ritual, true))) {
      abort(ritual, true);
    }
  }

  private void pulse(BindingRitual ritual) {
    if (ritual.isFinished()) {
      return;
    }
    ritual.pulsed(System.currentTimeMillis());
    if (!holds(ritual)) {
      abort(ritual, true);
      return;
    }
    holdInPlace(ritual);
    ritual.advance();
    GolemScale.stepTo(ritual.target().golem(), ritual.progress());
    if (ritual.complete()) {
      complete(ritual);
      return;
    }
    schedulePulse(ritual);
  }

  private void holdInPlace(BindingRitual ritual) {
    Mob golem = ritual.target().golem();
    PaperCompat.stopPathfinding(golem);
    Vector velocity = golem.getVelocity();
    golem.setVelocity(new Vector(0D, velocity.getY(), 0D));
    Location current = golem.getLocation();
    if (ritual.drifted(current)) {
      J.teleport(golem, ritual.anchorFacing(current));
    }
  }

  private boolean holds(BindingRitual ritual) {
    BindingTarget target = ritual.target();
    Mob golem = target.golem();
    Player owner = target.owner();
    return golem.isValid() && !golem.isDead()
        && owner.isOnline() && owner.isValid() && !owner.isDead()
        && J.isOwnedByCurrentRegion(owner)
        && ritual.withinReach(OWNER_REACH)
        && ritual.stillHeld();
  }

  private void complete(BindingRitual ritual) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    BindingTarget target = ritual.target();
    Mob golem = target.golem();
    Player owner = target.owner();
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    int level = getActiveLevel(owner);
    if (registry.isEmpty() || level <= 0 || !ritual.stillHeld()) {
      fail(ritual);
      warn(owner, GolemancyMessages.BINDING_INTERRUPTED);
      return;
    }
    Assessment assessment = assess(target, level, registry.get());
    if (assessment.verdict() != BindingVerdict.READY) {
      fail(ritual);
      deny(target, assessment);
      return;
    }
    if (!payTithe(owner, assessment.tithe())) {
      fail(ritual);
      deny(target, assessment.withVerdict(BindingVerdict.NOT_ENOUGH_LEVELS));
      return;
    }
    unwind(ritual);
    Optional<GolemHandle> handle;
    try {
      handle = Optional.of(registry.get().bind(owner, golem, target.body(), target.core()));
    } catch (RuntimeException failure) {
      if (!GolemState.has(golem)) {
        refundTithe(owner, assessment.tithe());
        fizzle(golem);
        warn(owner, GolemancyMessages.BINDING_INTERRUPTED);
        Adapt.warn("Animus Binding could not bind golem " + golem.getUniqueId() + " for " + owner.getName() + "; the tithe was refunded.", failure);
        return;
      }
      Adapt.warn("Animus Binding failed after golem " + golem.getUniqueId() + " was bound to " + owner.getName() + "; the core was taken and the tithe was kept.", failure);
      handle = registry.get().handle(golem);
    }
    decrementItemstack(owner.getInventory().getItemInMainHand(), owner);
    fx(golem, FxPriority.TRANSITION)
        .burst(Particles.END_ROD, 24, 0.5D)
        .burst(bodyParticle(target.body()), 12, 0.4D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F)
        .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.5F, 1.6F);
    notify(owner, GolemancyMessages.BINDING_BOUND, trusted("body", AdaptLanguage.text(target.body().displayName())));
    handle.ifPresent(bound -> GolemancyRuntime.bindListener().bound(owner, bound));
  }

  private void abort(BindingRitual ritual, boolean announce) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    BindingTarget target = ritual.target();
    fail(ritual);
    if (announce && target.owner().isOnline()) {
      warn(target.owner(), GolemancyMessages.BINDING_INTERRUPTED);
    }
  }

  private void forget(BindingRitual ritual) {
    BindingTarget target = ritual.target();
    ritualsByOwner.remove(target.owner().getUniqueId(), ritual);
    ritualsByGolem.remove(target.golem().getUniqueId(), ritual);
  }

  private boolean busy(Map<UUID, BindingRitual> rituals, UUID key, long now) {
    BindingRitual existing = rituals.get(key);
    if (existing == null) {
      return false;
    }
    if (existing.isFinished()) {
      forget(existing);
      return false;
    }
    if (!existing.stale(now)) {
      return true;
    }
    onGolemThread(existing, () -> abort(existing, false));
    return false;
  }

  private void onGolemThread(BindingRitual ritual, Runnable task) {
    Mob golem = ritual.target().golem();
    if (J.isOwnedByCurrentRegion(golem)) {
      task.run();
    } else if (!J.runEntity(golem, task, () -> lose(ritual))) {
      lose(ritual);
    }
  }

  private void lose(BindingRitual ritual) {
    ritual.finish();
    forget(ritual);
    GolemRitualMarker.release(ritual.target().golem());
  }

  private Assessment assess(BindingTarget target, int level, GolemRegistry registry) {
    Config config = getConfig();
    Player owner = target.owner();
    Mob golem = target.golem();
    GolemCoreType core = target.core();
    int cap = golemCap(config, level, GolemancyRuntime.settings().hardCapPerPlayer());
    int tithe = owner.getGameMode() == GameMode.CREATIVE ? 0 : tithe(config, target.body(), level);
    boolean exclusive = builderExclusive(golem, owner.getUniqueId(), System.currentTimeMillis());
    boolean coreUnlocked = core == GolemCoreType.BLANK || getActiveSiblingLevel(owner, "golem-" + core.id() + "-core") >= 1;
    Location at = golem.getLocation();
    BindingCheck check = new BindingCheck(registry.bindBlock(owner, golem, target.body(), core).orElse(null),
        playerCreated(golem), config.allowBindingVillageGolems, exclusive,
        bodyUnlocked(owner, target.body()), coreUnlocked, canInteract(owner, at) && canBlockPlace(owner, at), ownedGolems(owner), cap, owner.getLevel(), tithe);
    return new Assessment(AnimusBindingRules.verdict(check), cap, tithe);
  }

  private int ownedGolems(Player owner) {
    AdaptPlayer adaptPlayer = getServer().getOnlineAdaptPlayer(owner.getUniqueId());
    return adaptPlayer == null ? 0 : adaptPlayer.getData().getGolemancy().golemCount();
  }

  private boolean payTithe(Player owner, int levels) {
    return levels <= 0 || payExperienceCost(owner, "tithe", levels, () -> spendLevels(owner, levels));
  }

  private static void refundTithe(Player owner, int levels) {
    if (levels > 0) {
      owner.giveExpLevels(levels);
    }
  }

  private void deny(BindingTarget target, Assessment assessment) {
    Player owner = target.owner();
    String body = AdaptLanguage.text(target.body().displayName());
    String core = AdaptLanguage.text(target.core().displayName());
    switch (assessment.verdict()) {
      case READY -> {
      }
      case ALREADY_BOUND -> warn(owner, GolemancyMessages.BINDING_ALREADY_BOUND);
      case VILLAGE_GOLEM -> warn(owner, GolemancyMessages.BINDING_VILLAGE);
      case NOT_BUILDER -> warn(owner, GolemancyMessages.BINDING_BUILDER);
      case BODY_LOCKED -> warn(owner, GolemancyMessages.BINDING_BODY_LOCKED, trusted("body", body));
      case CORE_NOT_ALLOWED -> warn(owner, GolemancyMessages.BINDING_CORE_NOT_ALLOWED, trusted("body", body), trusted("core", core));
      case CORE_LOCKED -> warn(owner, GolemancyMessages.BINDING_CORE_LOCKED, trusted("core", core));
      case PROTECTED -> warn(owner, GolemancyMessages.BINDING_PROTECTED);
      case UNAVAILABLE -> warn(owner, GolemancyMessages.BINDING_UNAVAILABLE);
      case GOLEM_CAP -> warn(owner, GolemancyMessages.BINDING_GOLEM_CAP, trusted("count", assessment.golemCap()));
      case CHUNK_CAP -> warn(owner, GolemancyMessages.BINDING_CHUNK_CAP,
          trusted("count", GolemancyRuntime.settings().maxBoundGolemsPerChunk()));
      case NOT_ENOUGH_LEVELS -> warn(owner, GolemancyMessages.BINDING_LEVELS, trusted("levels", assessment.tithe()));
    }
  }

  private FxTimeline ritualTimeline(BindingTarget target, int ticks) {
    Particle bodyParticle = bodyParticle(target.body());
    double height = target.golem().getHeight();
    return timeline(target.golem())
        .duration(ticks)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24D)
        .frame((fx, tick, progress) -> {
          double scale = 1D - (progress * 0.5D);
          fx.helix(Particles.END_ROD, 0.9D * scale, height * scale, 3, tick * 0.4D);
          fx.particle(bodyParticle, 1, 0D, height * 0.5D * scale, 0D, 0.25D, 0.01D);
          if (tick % SOUND_INTERVAL_TICKS == 0) {
            fx.sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 0.8F + ((float) progress * 0.8F));
          }
        });
  }

  private void fail(BindingRitual ritual) {
    if (unwind(ritual)) {
      fizzle(ritual.target().golem());
    }
  }

  private boolean unwind(BindingRitual ritual) {
    Mob golem = ritual.target().golem();
    if (!GolemRitualMarker.finish(golem) && golem.isValid()) {
      golem.setAware(ritual.wasAware());
      GolemScale.clearStep(golem);
    }
    return golem.isValid();
  }

  private void fizzle(Mob golem) {
    fx(golem, FxPriority.AMBIENT)
        .burst(Particles.SMOKE, 10, 0.4D)
        .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.6F);
  }

  private static Particle bodyParticle(GolemBody body) {
    return switch (body) {
      case FROST -> Particle.SNOWFLAKE;
      case COPPER -> Particle.WAX_ON;
      case IRON, BONE -> Particle.ASH;
    };
  }

  private static boolean spendLevels(Player player, int levels) {
    if (player.getLevel() < levels) {
      return false;
    }
    player.giveExpLevels(-levels);
    return true;
  }

  private static void notify(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, arguments)));
  }

  private static void warn(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(key, arguments)));
  }

  private record Assessment(BindingVerdict verdict, int golemCap, int tithe) {
    private Assessment withVerdict(BindingVerdict next) {
      return new Assessment(next, golemCap, tithe);
    }
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-right-click a golem while holding an Animus Core to bind it to you as a companion.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Most golems you can own at once, by Animus Binding level from level 1 upward.", impact = "Higher values let players keep more golems; hardCapPerPlayer in the Golemancy skill config still caps every level.")
    List<Integer> golemCapByLevel = new ArrayList<>(List.of(1, 2, 3, 5, 7));
    @ConfigDoc(value = "Seconds the binding ritual takes, by Animus Binding level from level 1 upward. Each value is clamped to 0.5-30.", impact = "Lower values bind golems faster.")
    List<Double> ritualSecondsByLevel = new ArrayList<>(List.of(4.0, 3.5, 3.0, 2.5, 2.0));
    @ConfigDoc(value = "Experience levels it costs to bind a frost golem, by Animus Binding level from level 1 upward.", impact = "Higher values make frost golems cost more to bind; 0 binds them free.")
    List<Integer> titheSnowByLevel = new ArrayList<>(List.of(1, 1, 1, 1, 0));
    @ConfigDoc(value = "Experience levels it costs to bind a copper golem, by Animus Binding level from level 1 upward.", impact = "Higher values make copper golems cost more to bind; 0 binds them free.")
    List<Integer> titheCopperByLevel = new ArrayList<>(List.of(2, 2, 1, 1, 1));
    @ConfigDoc(value = "Experience levels it costs to bind an iron golem, by Animus Binding level from level 1 upward.", impact = "Higher values make iron golems cost more to bind; 0 binds them free.")
    List<Integer> titheIronByLevel = new ArrayList<>(List.of(3, 3, 2, 2, 1));
    @ConfigDoc(value = "Seconds after a golem is built during which only the player who built it can bind it.", impact = "Higher values protect fresh builds longer; 0 lets anyone bind a golem at once.")
    int builderExclusiveSeconds = 300;
    @ConfigDoc(value = "Lets players bind iron golems that no player built, such as village iron golems.", impact = "Set to true to let any iron golem be bound.")
    boolean allowBindingVillageGolems = false;
    @ConfigDoc(value = "Crafting recipe for the Artificer's Crook, unlocked at Animus Binding level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe crookRecipe = new ConfiguredShapedRecipe()
        .rows(" A ", "RHR", " C ")
        .key('H', "IRON_HOE")
        .key('A', "AMETHYST_SHARD")
        .key('R', "REDSTONE")
        .key('C', "COPPER_INGOT")
        .result("golemancy:crook", 1);
    @ConfigDoc(value = "Crafting recipe for the Blank Animus Core, unlocked at Animus Binding level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe blankCoreRecipe = new ConfiguredShapedRecipe()
        .rows("ARA", "RPR", "ARA")
        .key('A', "AMETHYST_SHARD")
        .key('R', "REDSTONE")
        .key('P', "ENDER_PEARL")
        .result("golemancy:blank_core", 1)
        .tithe(1);
  }
}
