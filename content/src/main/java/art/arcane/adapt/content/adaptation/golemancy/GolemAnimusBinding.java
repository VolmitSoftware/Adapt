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

import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.content.golem.item.ConfiguredShapedRecipe;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRitualMarker;
import art.arcane.adapt.content.golem.GolemScale;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.item.OwnerBoundDrop;
import art.arcane.adapt.content.golem.reveal.GolemLabelPolicy;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class GolemAnimusBinding extends SimpleAdaptation<GolemAnimusBinding.Config> implements GolemLabelPolicy {
  public static final Id ID = Id.adapt("golem/animus_binding");
  static final String CROOK_RECIPE = "golemancy-crook";
  static final String BLANK_CORE_RECIPE = "golemancy-blank-core";
  private static final Id COPPER_FRAME = Id.adapt("golem/copper_frame");
  private static final Id IRON_FRAME = Id.adapt("golem/iron_frame");
  private static final Id BONE_FRAME = Id.adapt("golem/bone_frame");
  private static final int RECIPE_LEVEL = 1;
  private static final Id IRON_GOLEM = Id.minecraft("iron_golem");
  private static final double OWNER_REACH = 4D;
  private static final long MILLIS_PER_SECOND = 1000L;
  private static final int TICKS_PER_SECOND = 20;
  private static final int SOUND_INTERVAL_TICKS = 10;
  private static final float DENY_VOLUME = 0.7F;
  private static final float DENY_PITCH = 1F;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.STATUS_LABELS);

  private final Map<UUID, BindingRitual> ritualsByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, BindingRitual> ritualsByOwner = new ConcurrentHashMap<>();

  public GolemAnimusBinding() {
    super(ID);
    needs(PlatformNeed.partialAttribute(Attributes.SCALE, "golems keep their vanilla size"));
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_STACK_LIMITS, "golem items stack up to their base item's limit"));
    registerConfiguration(Config.class);
    registerItem(GolemItems.ITEM);
    setLocalizationKey("adapt.golemancy.animus_binding");
    setIcon(Id.minecraft("firework_star"));
    GolemancyRuntime.setProtectionPolicy(this);
    GolemancyRuntime.setLabelPolicy(this);
    ShapedRecipeParser parser = ShapedRecipeParser.platform(GolemItems.resolver());
    GolemancySupport.registerRecipe(this, parser, CROOK_RECIPE, getConfig().crookRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, BLANK_CORE_RECIPE, getConfig().blankCoreRecipe, RECIPE_LEVEL);
  }




  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, golemCap(config, level, GolemancyRuntime.settings().hardCapPerPlayer()), 1);
    statLore(v, TextFormat.YELLOW, "* ", (double) ritualTicks(config, level) / TICKS_PER_SECOND, 2);
    statLore(v, TextFormat.RED, "- ", tithe(config, GolemBody.FROST, level), 3);
    statLore(v, TextFormat.RED, "- ", tithe(config, GolemBody.COPPER, level), 4);
    statLore(v, TextFormat.RED, "- ", tithe(config, GolemBody.IRON, level), 5);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean hidesLabels(PlayerRef owner) {
    return preference(owner, GolemancyPreferences.STATUS_LABELS) == GolemancyPreferences.Labels.HIDDEN;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    if (e.hand() != Hand.MAIN_HAND) {
      if (ritualsByOwner.containsKey(player.uuid())) {
        e.setCancelled(true);
      }
      return;
    }
    if (!player.isSneaking() || !(e.clicked() instanceof MobRef golem) || !AdaptApi.scheduler().owns(golem)) {
      return;
    }
    Optional<GolemCoreType> core = GolemItems.coreOf(player.inventory().mainHand());
    Optional<GolemBody> body = GolemBody.of(golem);
    if (core.isEmpty() || body.isEmpty() || !bindable(golem, body.get())) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    e.setCancelled(true);
    begin(new BindingTarget(player, golem, body.get(), core.get()), level);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    BindingRitual ritual = ritualsByGolem.get(e.entity().uuid());
    if (ritual != null && AnimusBindingRules.interrupts(e.cause(), e instanceof EntityDamageByEntityEvent)) {
      abort(ritual, true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    BindingRitual ritual = ritualsByOwner.get(e.player().uuid());
    if (ritual != null && settle(ritual)) {
      onGolemThread(ritual, () -> fail(ritual));
    }
  }

  @Override
  public void unregister() {
    for (BindingRitual ritual : List.copyOf(ritualsByGolem.values())) {
      if (settle(ritual)) {
        onGolemThread(ritual, () -> fail(ritual));
      }
    }
    super.unregister();
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.ritualSecondsByLevel = AnimusBindingRules.clampRitualSeconds(loadedConfig.ritualSecondsByLevel);
  }

  boolean builderExclusive(MobRef golem, UUID player, long nowMillis) {
    return AnimusBindingRules.builderExclusive(GolemBuildAttribution.builderOf(golem).orElse(null), GolemBuildAttribution.builtAt(golem), player,
        nowMillis, Math.max(0, getConfig().builderExclusiveSeconds) * MILLIS_PER_SECOND);
  }

  boolean villageGolemsAllowed() {
    return getConfig().allowBindingVillageGolems;
  }

  boolean bodyUnlocked(PlayerRef player, GolemBody body) {
    return AnimusBindingRules.bodyUnlocked(body, getActiveSiblingLevel(player, COPPER_FRAME), getActiveSiblingLevel(player, IRON_FRAME),
        getActiveSiblingLevel(player, BONE_FRAME));
  }

  private static boolean bindable(MobRef golem, GolemBody body) {
    return body != GolemBody.BONE || GolemState.has(golem) || TragoulSkeletalServant.isServant(golem);
  }

  private static boolean servantOwned(PlayerRef owner, MobRef golem, GolemBody body) {
    return body != GolemBody.BONE || GolemState.has(golem) || owner.uuid().equals(TragoulSkeletalServant.getServantOwnerId(golem));
  }

  static boolean playerCreated(MobRef golem) {
    return !IRON_GOLEM.equals(golem.type()) || golem.isPlayerCreated();
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
    PlayerRef owner = target.owner();
    MobRef golem = target.golem();
    long now = System.currentTimeMillis();
    if (busy(ritualsByOwner, owner.uuid(), now) || busy(ritualsByGolem, golem.uuid(), now)) {
      warn(owner, Text.key("adapt.golemancy.binding.busy"));
      return;
    }
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      warn(owner, Text.key("adapt.golemancy.binding.unavailable"));
      return;
    }
    Assessment assessment = assess(target, level, registry.get());
    if (assessment.verdict() != BindingVerdict.READY) {
      deny(target, assessment);
      return;
    }
    int ticks = ritualTicks(getConfig(), level);
    BindingRitual ritual = new BindingRitual(target, ticks, now, takeCore(owner));
    ritualsByOwner.put(owner.uuid(), ritual);
    ritualsByGolem.put(golem.uuid(), ritual);
    HudStatus.duration(owner.uuid(), this, now, ticks * 50L);
    GolemRitualMarker.start(golem, ritual.wasAware());
    golem.setAware(false);
    FxTimeline timeline = ritualTimeline(target, ticks);
    ritual.attach(timeline);
    timeline.start();
    schedulePulse(ritual);
  }

  private void schedulePulse(BindingRitual ritual) {
    if (!AdaptApi.scheduler().runEntity(ritual.target().golem(), () -> pulse(ritual), 1, () -> abort(ritual, true))) {
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
    MobRef golem = ritual.target().golem();
    golem.stopPathing();
    Vec3 velocity = golem.velocity();
    golem.setVelocity(new Vec3(0D, velocity.y(), 0D));
    Loc current = golem.location();
    if (ritual.drifted(current)) {
      golem.teleport(ritual.anchorFacing(current), TeleportCause.PLUGIN);
    }
  }

  private boolean holds(BindingRitual ritual) {
    BindingTarget target = ritual.target();
    MobRef golem = target.golem();
    PlayerRef owner = target.owner();
    return golem.isValid() && !golem.isDead()
        && owner.isOnline() && owner.isValid() && !owner.isDead()
        && AdaptApi.scheduler().owns(owner)
        && ritual.withinReach(OWNER_REACH);
  }

  private void complete(BindingRitual ritual) {
    if (!ritual.finish()) {
      return;
    }
    forget(ritual);
    BindingTarget target = ritual.target();
    MobRef golem = target.golem();
    PlayerRef owner = target.owner();
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    int level = getActiveLevel(owner);
    if (registry.isEmpty() || level <= 0) {
      refuse(ritual);
      warn(owner, Text.key("adapt.golemancy.binding.interrupted"));
      return;
    }
    Assessment assessment = assess(target, level, registry.get());
    if (assessment.verdict() != BindingVerdict.READY) {
      refuse(ritual);
      deny(target, assessment);
      return;
    }
    if (!payTithe(owner, assessment.tithe())) {
      refuse(ritual);
      deny(target, assessment.withVerdict(BindingVerdict.NOT_ENOUGH_LEVELS));
      return;
    }
    unwind(ritual);
    if (target.body() == GolemBody.BONE && !GolemState.has(golem) && !ServantFrame.surrender(golem)) {
      refundTithe(owner, assessment.tithe());
      returnCore(ritual);
      fizzle(golem);
      warn(owner, Text.key("adapt.golemancy.binding.interrupted"));
      return;
    }
    Optional<GolemHandle> handle;
    try {
      handle = Optional.of(registry.get().bind(owner, golem, target.body(), target.core()));
    } catch (RuntimeException failure) {
      if (!GolemState.has(golem)) {
        refundTithe(owner, assessment.tithe());
        returnCore(ritual);
        fizzle(golem);
        if (target.body() == GolemBody.BONE) {
          ServantFrame.crumble(golem);
        }
        warn(owner, Text.key("adapt.golemancy.binding.interrupted"));
        AdaptApi.log().error("Animus Binding could not bind golem " + golem.uuid() + " for " + owner.name() + "; the core and the tithe were returned.", failure);
        return;
      }
      AdaptApi.log().error("Animus Binding failed after golem " + golem.uuid() + " was bound to " + owner.name() + "; the core was used and the tithe was kept.", failure);
      handle = registry.get().handle(golem);
    }
    ritual.takeCore();
    boundFx(golem, target.body());
    notify(owner, Text.key("adapt.golemancy.binding.bound").withMarkup("body", String.valueOf(target.body().displayName().resolve())));
    Mutations.landed(this, owner);
    handle.ifPresent(bound -> GolemancyRuntime.bindListener().bound(owner, bound));
  }

  private void abort(BindingRitual ritual, boolean announce) {
    if (!settle(ritual)) {
      return;
    }
    BindingTarget target = ritual.target();
    fail(ritual);
    if (announce && target.owner().isOnline()) {
      warn(target.owner(), Text.key("adapt.golemancy.binding.interrupted"));
    }
  }

  private boolean settle(BindingRitual ritual) {
    if (!ritual.finish()) {
      return false;
    }
    forget(ritual);
    returnCore(ritual);
    return true;
  }

  private void refuse(BindingRitual ritual) {
    fail(ritual);
    returnCore(ritual);
  }

  private static void returnCore(BindingRitual ritual) {
    ItemRef core = ritual.takeCore();
    if (core != null) {
      giveBack(ritual.target().owner(), core, ritual.anchor());
    }
  }

  private static void giveBack(PlayerRef owner, ItemRef core, Loc fallback) {
    AtomicBoolean settled = new AtomicBoolean();
    Runnable give = () -> {
      if (settled.compareAndSet(false, true)) {
        handBack(owner, core);
      }
    };
    Runnable drop = () -> {
      if (settled.compareAndSet(false, true)) {
        dropBack(owner.uuid(), core, fallback);
      }
    };
    if (owner.isOnline() && AdaptApi.scheduler().owns(owner)) {
      give.run();
      return;
    }
    if (!AdaptApi.scheduler().runEntity(owner, give, drop)) {
      drop.run();
    }
  }

  private static void handBack(PlayerRef owner, ItemRef core) {
    for (ItemRef leftover : owner.inventory().addItem(core)) {
      OwnerBoundDrop.drop(owner.location(), leftover, owner.uuid());
    }
  }

  private static void dropBack(UUID owner, ItemRef core, Loc at) {
    if (!AdaptApi.scheduler().runAt(at, () -> OwnerBoundDrop.drop(at, core, owner))) {
      AdaptApi.log().warn("Animus Binding could not return the Animus Core of " + owner + " at " + at + "; the core was lost.");
    }
  }

  private ItemRef takeCore(PlayerRef owner) {
    ItemRef held = owner.inventory().mainHand();
    ItemRef core = held.copyWithAmount(1);
    decrementItemstack(held, owner);
    return core;
  }

  private void forget(BindingRitual ritual) {
    BindingTarget target = ritual.target();
    if (ritualsByOwner.remove(target.owner().uuid(), ritual)) {
      HudStatus.clear(target.owner().uuid(), this, HudGlyph.DURATION);
    }
    ritualsByGolem.remove(target.golem().uuid(), ritual);
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
    MobRef golem = ritual.target().golem();
    if (AdaptApi.scheduler().owns(golem)) {
      task.run();
    } else if (!AdaptApi.scheduler().runEntity(golem, task, () -> lose(ritual))) {
      lose(ritual);
    }
  }

  private void lose(BindingRitual ritual) {
    settle(ritual);
    forget(ritual);
    GolemRitualMarker.release(ritual.target().golem());
  }

  private Assessment assess(BindingTarget target, int level, GolemRegistry registry) {
    Config config = getConfig();
    PlayerRef owner = target.owner();
    MobRef golem = target.golem();
    GolemCoreType core = target.core();
    int cap = golemCap(config, level, GolemancyRuntime.settings().hardCapPerPlayer());
    int tithe = owner.gameMode() == GameMode.CREATIVE ? 0 : tithe(config, target.body(), level);
    int have = owner.level();
    boolean exclusive = builderExclusive(golem, owner.uuid(), System.currentTimeMillis());
    boolean coreUnlocked = core == GolemCoreType.BLANK || getActiveSiblingLevel(owner, Id.adapt("golem/" + core.id() + "_core")) >= 1;
    Loc at = golem.location();
    BindingCheck check = new BindingCheck(registry.bindBlock(owner, golem, target.body(), core).orElse(null),
        servantOwned(owner, golem, target.body()), playerCreated(golem), config.allowBindingVillageGolems, exclusive,
        bodyUnlocked(owner, target.body()), coreUnlocked, canInteract(owner, at) && canBlockPlace(owner, at), ownedGolems(owner), cap, have, tithe);
    return new Assessment(AnimusBindingRules.verdict(check), cap, tithe, have);
  }

  private int ownedGolems(PlayerRef owner) {
    PlayerProfile adaptPlayer = AdaptApi.players().find(owner.uuid()).orElse(null);
    return adaptPlayer == null ? 0 : adaptPlayer.section(GolemRoster.section()).golemCount();
  }

  private boolean payTithe(PlayerRef owner, int levels) {
    return levels <= 0 || payExperienceCost(owner, "tithe", levels, () -> spendLevels(owner, levels));
  }

  private static void refundTithe(PlayerRef owner, int levels) {
    if (levels > 0) {
      owner.giveExpLevels(levels);
    }
  }

  private void deny(BindingTarget target, Assessment assessment) {
    PlayerRef owner = target.owner();
    String body = target.body().displayName().resolve();
    String core = target.core().displayName().resolve();
    switch (assessment.verdict()) {
      case READY -> {
      }
      case ALREADY_BOUND -> warn(owner, Text.key("adapt.golemancy.binding.already_bound"));
      case NOT_OWNER -> warn(owner, Text.key("adapt.golemancy.binding.not_owner"));
      case VILLAGE_GOLEM -> warn(owner, Text.key("adapt.golemancy.binding.village"));
      case NOT_BUILDER -> warn(owner, Text.key("adapt.golemancy.binding.builder"));
      case BODY_LOCKED -> warn(owner, Text.key("adapt.golemancy.binding.body_locked").withMarkup("body", String.valueOf(body)));
      case CORE_NOT_ALLOWED -> warn(owner, Text.key("adapt.golemancy.binding.core_not_allowed").withMarkup("body", String.valueOf(body)).withMarkup("core", String.valueOf(core)));
      case CORE_LOCKED -> warn(owner, Text.key("adapt.golemancy.binding.core_locked").withMarkup("core", String.valueOf(core)));
      case PROTECTED -> warn(owner, Text.key("adapt.golemancy.binding.protected"));
      case UNAVAILABLE -> warn(owner, Text.key("adapt.golemancy.binding.unavailable"));
      case GOLEM_CAP -> warn(owner, Text.key("adapt.golemancy.binding.golem_cap").withMarkup("count", String.valueOf(assessment.golemCap())));
      case CHUNK_CAP -> warn(owner, Text.key("adapt.golemancy.binding.chunk_cap").withMarkup("count", String.valueOf(GolemancyRuntime.settings().maxBoundGolemsPerChunk())));
      case NOT_ENOUGH_LEVELS -> {
        warn(owner, Text.key("adapt.golemancy.binding.levels").withMarkup("levels", String.valueOf(assessment.tithe())).withMarkup("have", String.valueOf(assessment.have())));
        denySound(owner);
      }
    }
  }

  private FxTimeline ritualTimeline(BindingTarget target, int ticks) {
    ParticleSpec bodyParticle = bodyParticle(target.body());
    double height = target.golem().height();
    return timeline(target.golem())
        .duration(ticks)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24D)
        .frame((fx, tick, progress) -> {
          double scale = 1D - (progress * 0.5D);
          fx.helix(Particles.END_ROD, 0.9D * scale, height * scale, 3, tick * 0.4D);
          fx.particle(bodyParticle, 1, 0D, height * 0.5D * scale, 0D, 0.25D, 0.01D);
          if (tick % SOUND_INTERVAL_TICKS == 0) {
            fx.sound(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 0.8F + ((float) progress * 0.8F));
          }
        });
  }

  private void fail(BindingRitual ritual) {
    if (unwind(ritual)) {
      fizzle(ritual.target().golem());
    }
  }

  private boolean unwind(BindingRitual ritual) {
    MobRef golem = ritual.target().golem();
    if (!GolemRitualMarker.finish(golem) && golem.isValid()) {
      golem.setAware(ritual.wasAware());
      GolemScale.clearStep(golem);
    }
    return golem.isValid();
  }

  void boundFx(MobRef golem, GolemBody body) {
    fx(golem, FxPriority.TRANSITION)
        .burst(Particles.END_ROD, 24, 0.5D)
        .burst(bodyParticle(body), 12, 0.4D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F)
        .sound(Sounds.BLOCK_BEACON_POWER_SELECT, 0.5F, 1.6F);
  }

  void fizzle(MobRef golem) {
    fx(golem, FxPriority.AMBIENT)
        .burst(Particles.SMOKE, 10, 0.4D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.6F);
  }

  private static ParticleSpec bodyParticle(GolemBody body) {
    return switch (body) {
      case FROST -> Particles.SNOWFLAKE;
      case COPPER -> Particles.WAX_ON;
      case IRON, BONE -> Particles.ASH;
    };
  }

  private static boolean spendLevels(PlayerRef player, int levels) {
    if (player.level() < levels) {
      return false;
    }
    player.giveExpLevels(-levels);
    return true;
  }

  private static void notify(PlayerRef player, Text key) {
    AdaptApi.scheduler().runEntity(player, () -> HudStatus.show(player, TextFormat.AQUA + key.resolve()));
  }

  private static void warn(PlayerRef player, Text key) {
    AdaptApi.scheduler().runEntity(player, () -> HudStatus.show(player, TextFormat.RED + key.resolve()));
  }

  private static void denySound(PlayerRef player) {
    AdaptApi.scheduler().runEntity(player, () -> player.playSound(player.location(), Sounds.ENTITY_VILLAGER_NO, SoundCategory.MASTER, DENY_VOLUME, DENY_PITCH));
  }

  private record Assessment(BindingVerdict verdict, int golemCap, int tithe, int have) {
    private Assessment withVerdict(BindingVerdict next) {
      return new Assessment(next, golemCap, tithe, have);
    }
  }

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
    @ConfigDoc(value = "Crafting recipe for the Artificer's Crook, unlocked at Animus Binding level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe crookRecipe = new ConfiguredShapedRecipe()
        .rows(" A ", "RHR", " C ")
        .key('H', "IRON_HOE")
        .key('A', "AMETHYST_SHARD")
        .key('R', "REDSTONE")
        .key('C', "COPPER_INGOT")
        .result("golemancy:crook", 1);
    @ConfigDoc(value = "Crafting recipe for the Blank Animus Core, unlocked at Animus Binding level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe blankCoreRecipe = new ConfiguredShapedRecipe()
        .rows("ARA", "RPR", "ARA")
        .key('A', "AMETHYST_SHARD")
        .key('R', "REDSTONE")
        .key('P', "ENDER_PEARL")
        .result("golemancy:blank_core", 1);

    public Config() {
      baseCost = 2;
      initialCost = 1;
      costFactor = 0.5;
    }
  }
}
