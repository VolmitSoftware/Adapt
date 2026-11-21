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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemFormation;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiActions;
import art.arcane.adapt.api.golem.gui.GolemGuiSupport;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemOverseer extends SimpleAdaptation<GolemOverseer.Config> {
  private static final long TICK_INTERVAL_MILLIS = 1_000L;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final int TICKS_PER_SECOND = 20;
  private static final int JOIN_RECONCILE_TICKS = 20;
  private static final double SIC_RAY_SIZE = 0.3D;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.SIGNAL_FEEDBACK);
  private static final Consumer<EntityRef> NO_PULSE = golem -> {
  };

  private final LongSupplier clock;
  private final Map<UUID, GolemSignal> signals = new ConcurrentHashMap<>();
  private final GolemXpCooldowns<UUID> signalCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<UUID> rallyCooldowns = new GolemXpCooldowns<>();
  private final OverseerFormation formation = new OverseerFormation(() -> getConfig().formationRingRadius);
  private final OverseerGolems golems = new OverseerGolems(this::sentryRange, GolemOverseer::noteIgnoredSic);
  private final OverseerGlow glow = new OverseerGlow();
  private final Overseer.Commander commander = new Commander();

  public GolemOverseer() {
    this(M::ms);
  }

  GolemOverseer(LongSupplier clock) {
    super("golem-overseer");
    this.clock = clock;
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.overseer");
    setIcon(Id.minecraft("bell"));
    setInterval(TICK_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    Overseer.clearCommander(commander);
    Overseer.unregister(golems);
    GolemFormation.unregister(formation);
    formation.clear();
    signals.clear();
    glow.clearAll();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    Overseer.setCommander(commander);
    Overseer.register(golems);
    GolemFormation.register(formation);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.signalCooldownMillis = Math.max(0L, loadedConfig.signalCooldownMillis);
    loadedConfig.sicRange = OverseerRules.sicRange(loadedConfig.sicRange);
    loadedConfig.sicGlowSeconds = Math.max(0, loadedConfig.sicGlowSeconds);
    loadedConfig.sicHoldSeconds = OverseerRules.holdSeconds(loadedConfig.sicHoldSeconds);
    loadedConfig.rallyCooldownSeconds = Math.max(0, loadedConfig.rallyCooldownSeconds);
    loadedConfig.formationRingRadius = OverseerRules.ringRadius(loadedConfig.formationRingRadius);
    loadedConfig.formationArmorPerGolem = GolemancyRules.nonNegative(loadedConfig.formationArmorPerGolem);
    loadedConfig.formationArmorMax = OverseerRules.armorMax(loadedConfig.formationArmorMax);
    loadedConfig.formationArmorRange = GolemancyRules.nonNegative(loadedConfig.formationArmorRange);
  }

  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, (int) OverseerRules.radius(config.radiusByLevel, level), 1);
    if (GolemancyRules.unlocked(level, config.sicLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE2);
    }
    if (GolemancyRules.unlocked(level, config.channelFilterLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE3);
    }
    if (GolemancyRules.unlocked(level, config.rallyLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.formationLevel)) {
      statLore(v, Form.f(config.formationArmorMax, 0), 5);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public void onTick() {
    long now = clock.getAsLong();
    Config config = getConfig();
    signalCooldowns.sweep(now, config.signalCooldownMillis);
    rallyCooldowns.sweep(now, config.rallyCooldownSeconds * MILLIS_PER_SECOND);
    List<AdaptPlayer> candidates = learnedCandidates(now);
    Set<UUID> learners = new HashSet<>(candidates.size());
    for (AdaptPlayer candidate : candidates) {
      PlayerRef player = candidate.getPlayer();
      if (player == null) {
        continue;
      }
      learners.add(player.uuid());
      withPlayerThread(player, () -> reconcile(player));
    }
    formation.retain(learners);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    signals.remove(player.uuid());
    glow.clear(player);
    dropFormation(player);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    dropFormation(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    dropFormation(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef player = e.player();
    Tasks.runEntity(player, () -> withPlayerThread(player, () -> reconcile(player)), JOIN_RECONCILE_TICKS);
  }

  void cycle(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    int sicLevel = getConfig().sicLevel;
    GolemSignal next = signals.compute(player.uuid(), (owner, current) -> OverseerRules.next(current, level, sicLevel));
    if (chimes(player)) {
      OverseerFeedback.cycled(this, player);
    }
    GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SIGNAL, trusted("signal", GolemGuiSupport.localized(next.displayName())));
  }

  void send(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    Config config = getConfig();
    UUID owner = player.uuid();
    GolemSignal signal = OverseerRules.current(signals.get(owner), level, config.sicLevel);
    LivingRef target = null;
    if (signal == GolemSignal.SIC) {
      target = sicTarget(player, config.sicRange);
      if (target == null) {
        GolemGuiSupport.warn(player, GolemancyMessages.OVERSEER_NO_TARGET, trusted("range", (int) config.sicRange));
        return;
      }
    }
    long now = clock.getAsLong();
    if (!signalCooldowns.tryClaim(owner, now, config.signalCooldownMillis)) {
      return;
    }
    DyeColor channel = OverseerRules.crookChannel(level, config.channelFilterLevel, GolemItems.crookChannel(player.inventory().mainHand()));
    long rallyCooldownMillis = config.rallyCooldownSeconds * MILLIS_PER_SECOND;
    OverseerSignal.Rally rally = signal == GolemSignal.FOLLOW && GolemancyRules.unlocked(level, config.rallyLevel) && player.isOnGround()
        ? new OverseerRally(rallyCooldowns, owner, now, rallyCooldownMillis,
            () -> AbilityGlyphs.cooldown(owner, this, now, rallyCooldownMillis))
        : null;
    boolean chime = chimes(player);
    Overseer.dispatch(new OverseerSignal(owner, signal, player.location(), OverseerRules.radius(config.radiusByLevel, level), channel, target,
        now + config.sicHoldSeconds * MILLIS_PER_SECOND, rally, chime ? this::pulse : NO_PULSE));
    if (chime) {
      OverseerFeedback.sent(this, player, signal);
    }
    if (target != null) {
      glow.show(player, target, config.sicGlowSeconds * TICKS_PER_SECOND);
    }
    confirm(player, signal, channel);
  }

  boolean filtersChannels(PlayerRef player) {
    return GolemancyRules.unlocked(getActiveLevel(player), getConfig().channelFilterLevel);
  }

  boolean canSic(PlayerRef owner, LivingRef target) {
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.uuid(), target);
    return candidate != null && MinionTargeting.canDamageOwned(this, owner, candidate);
  }

  void reconcile(PlayerRef player) {
    Config config = getConfig();
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty() || !player.isOnline() || player.isDead() || !GolemancyRules.unlocked(getActiveLevel(player), config.formationLevel)) {
      formation.forget(player.uuid());
      OverseerArmor.settle(this, player, 0D);
      return;
    }
    Loc at = player.location();
    WorldRef world = at.world();
    double reach = config.formationArmorRange * config.formationArmorRange;
    List<UUID> members = new ArrayList<>();
    int near = 0;
    for (GolemHandle handle : registry.get().loadedOf(player.uuid())) {
      MobRef golem = handle.entity();
      if (handle.isDormant() || !Tasks.isOwnedByCurrentRegion(golem) || !golem.isValid() || !world.equals(golem.world())
          || handle.state().mode() != GolemMode.FOLLOW) {
        continue;
      }
      members.add(handle.id());
      if (golem.location().distanceSquared(at) <= reach) {
        near++;
      }
    }
    formation.update(player.uuid(), members);
    OverseerArmor.settle(this, player, OverseerRules.formationArmor(near, config.formationArmorPerGolem, config.formationArmorMax));
  }

  double sentryRange() {
    Skill<?> skill = getSkill();
    if (skill != null) {
      for (Adaptation<?> sibling : skill.getAdaptations()) {
        if (sibling instanceof GolemGuardianCore guardian) {
          return guardian.config().sentryRange;
        }
      }
    }
    return GuardianRules.sentryRange(Double.NaN);
  }

  private LivingRef sicTarget(PlayerRef owner, double range) {
    Loc eye = owner.eyeLocation();
    Vec3 start = eye.position();
    Vec3 direction = eye.direction();
    WorldRef world = owner.world();
    BlockRef block = world.rayTraceBlock(start, direction, range, false);
    double reach = block == null ? range : Math.min(range, blockDistance(block, start, direction));
    EntityRef hit = world.rayTraceEntity(start, direction, reach, SIC_RAY_SIZE, entity -> sightable(owner, entity));
    return hit instanceof LivingRef target && canSic(owner, target) ? target : null;
  }

  static double blockDistance(BlockRef block, Vec3 start, Vec3 direction) {
    Vec3 unit = direction.normalize();
    double nearest = Double.POSITIVE_INFINITY;
    for (Box box : block.collisionBoxes()) {
      nearest = Math.min(nearest, entry(box, start, unit));
    }
    return Double.isFinite(nearest) ? nearest : start.distance(block.center().position());
  }

  private static double entry(Box box, Vec3 start, Vec3 unit) {
    double near = 0D;
    double far = Double.POSITIVE_INFINITY;
    double[] origin = {start.x(), start.y(), start.z()};
    double[] step = {unit.x(), unit.y(), unit.z()};
    double[] min = {box.minX(), box.minY(), box.minZ()};
    double[] max = {box.maxX(), box.maxY(), box.maxZ()};
    for (int axis = 0; axis < 3; axis++) {
      if (Math.abs(step[axis]) < 1.0E-9D) {
        if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
          return Double.POSITIVE_INFINITY;
        }
        continue;
      }
      double first = (min[axis] - origin[axis]) / step[axis];
      double second = (max[axis] - origin[axis]) / step[axis];
      near = Math.max(near, Math.min(first, second));
      far = Math.min(far, Math.max(first, second));
      if (near > far) {
        return Double.POSITIVE_INFINITY;
      }
    }
    return near;
  }

  private boolean chimes(PlayerRef player) {
    return preference(player, GolemancyPreferences.SIGNAL_FEEDBACK) == GolemancyPreferences.SignalFeedback.CHIME_AND_PARTICLES;
  }

  private void pulse(EntityRef golem) {
    OverseerFeedback.pulse(this, golem);
  }

  private void confirm(PlayerRef player, GolemSignal signal, DyeColor channel) {
    Object name = GolemGuiSupport.localized(signal.displayName());
    if (channel == null) {
      GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SENT, trusted("signal", name));
      return;
    }
    GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SENT_CHANNEL, trusted("signal", name),
        trusted("channel", GolemGuiSupport.localized(GolemGuiActions.channelName(ChannelMask.of(channel)))));
  }

  private void dropFormation(PlayerRef player) {
    formation.forget(player.uuid());
    OverseerArmor.remove(this, player);
  }

  private static void noteIgnoredSic(UUID owner, int count) {
    PlayerRef player = AdaptCore.platform().host().player(owner);
    if (player != null) {
      GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SIC_IGNORED, trusted("count", count));
    }
  }

  private static boolean sightable(PlayerRef owner, EntityRef entity) {
    if (!(entity instanceof LivingRef living) || living.uuid().equals(owner.uuid()) || living.has(EntityTrait.ARMOR_STAND) || !living.isValid()
        || living.isDead()) {
      return false;
    }
    if (living instanceof PlayerRef player && (player.gameMode() == GameMode.SPECTATOR || !AdaptCore.platform().viewers().canSee(owner, player))) {
      return false;
    }
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.uuid(), living);
    return candidate != null && !candidate.protectedFriendly();
  }

  private final class Commander implements Overseer.Commander {
    @Override
    public void cycle(PlayerRef player) {
      GolemOverseer.this.cycle(player);
    }

    @Override
    public void send(PlayerRef player) {
      GolemOverseer.this.send(player);
    }

    @Override
    public boolean filtersChannels(PlayerRef player) {
      return GolemOverseer.this.filtersChannels(player);
    }
  }

  @ConfigDescription("Lets you signal your bound golems with the Artificer's Crook to follow you, hold, go home or work, and as it grows, to attack what you look at, answer by channel, rally to you and guard you in formation.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Blocks from you within which your golems hear a Crook signal, by Overseer level from level 1 upward. Each value is clamped to 1-128.", impact = "Higher values let signals reach golems farther away.")
    List<Integer> radiusByLevel = new ArrayList<>(List.of(16, 24, 32, 48, 64));
    @ConfigDoc(value = "Milliseconds after you send a signal before you can send another.", impact = "Lower values let you send signals faster.")
    long signalCooldownMillis = 1_000L;
    @ConfigDoc(value = "Overseer level at which Sic joins the signal cycle and sends your fighting golems after the creature you look at.", impact = "Lower values unlock Sic sooner.")
    int sicLevel = 2;
    @ConfigDoc(value = "Blocks, from 1 to 64, within which you must look at a creature to send Sic. Blocks in the way stop your look.", impact = "Higher values let you point at creatures farther away.")
    double sicRange = 24.0;
    @ConfigDoc(value = "Seconds the creature you send Sic at glows for you alone. 0 turns the glow off.", impact = "Higher values keep the target marked longer.")
    int sicGlowSeconds = 5;
    @ConfigDoc(value = "Seconds, at least 1, your golems keep attacking a Sic target before they choose their own targets again. They stop sooner when the target dies or leaves their world.", impact = "Higher values keep golems on the target longer.")
    int sicHoldSeconds = 20;
    @ConfigDoc(value = "Overseer level at which the Crook channel set in the Golem Roster limits your signals to golems on that channel.", impact = "Lower values unlock the Crook channel filter sooner.")
    int channelFilterLevel = 3;
    @ConfigDoc(value = "Overseer level at which a Follow signal also teleports your golems beyond the signal radius in the same world to you.", impact = "Lower values unlock Rally sooner.")
    int rallyLevel = 4;
    @ConfigDoc(value = "Seconds after a Rally before Follow can rally golems again. While it cools down, Follow reaches only golems within the signal radius.", impact = "Lower values let you rally golems more often.")
    int rallyCooldownSeconds = 60;
    @ConfigDoc(value = "Overseer level at which your following golems stand in a ring around you and give you armor.", impact = "Lower values unlock Formation sooner.")
    int formationLevel = 5;
    @ConfigDoc(value = "Blocks from you, from 1 to 8, at which following golems stand in the formation ring.", impact = "Higher values spread the ring wider.")
    double formationRingRadius = 2.5;
    @ConfigDoc(value = "Armor you gain for each following golem within formationArmorRange blocks of you.", impact = "Higher values give more armor per golem.")
    double formationArmorPerGolem = 1.0;
    @ConfigDoc(value = "Most armor, from 0 to 30, the formation gives you.", impact = "Higher values let larger formations protect you more.")
    double formationArmorMax = 6.0;
    @ConfigDoc(value = "Blocks from you within which a following golem counts toward your formation armor.", impact = "Higher values count golems farther away.")
    double formationArmorRange = 8.0;

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
