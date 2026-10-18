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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
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
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
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
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemOverseer extends SimpleAdaptation<GolemOverseer.Config> {
  private static final long TICK_INTERVAL_MILLIS = 1_000L;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final int TICKS_PER_SECOND = 20;
  private static final int JOIN_RECONCILE_TICKS = 20;
  private static final double SIC_RAY_SIZE = 0.3D;

  private final LongSupplier clock;
  private final Map<UUID, GolemSignal> signals = new ConcurrentHashMap<>();
  private final GolemXpCooldowns<UUID> signalCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<UUID> rallyCooldowns = new GolemXpCooldowns<>();
  private final OverseerFormation formation = new OverseerFormation(() -> getConfig().formationRingRadius);
  private final OverseerGolems golems = new OverseerGolems(this::sentryRange);
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
    setIcon(Material.BELL);
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
    loadedConfig.formationArmorPerGolem = OverseerRules.nonNegative(loadedConfig.formationArmorPerGolem);
    loadedConfig.formationArmorMax = OverseerRules.armorMax(loadedConfig.formationArmorMax);
    loadedConfig.formationArmorRange = OverseerRules.nonNegative(loadedConfig.formationArmorRange);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, (int) OverseerRules.radius(config.radiusByLevel, level), 1);
    if (OverseerRules.unlocked(level, config.sicLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE2);
    }
    if (OverseerRules.unlocked(level, config.channelFilterLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE3);
    }
    if (OverseerRules.unlocked(level, config.rallyLevel)) {
      unlockLore(v, GolemancyMessages.OVERSEER_LORE4);
    }
    if (OverseerRules.unlocked(level, config.formationLevel)) {
      statLore(v, Form.f(config.formationArmorMax, 0), 5);
    }
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
      Player player = candidate.getPlayer();
      if (player == null) {
        continue;
      }
      learners.add(player.getUniqueId());
      withPlayerThread(player, () -> reconcile(player));
    }
    formation.retain(learners);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Player player = e.getPlayer();
    signals.remove(player.getUniqueId());
    glow.clear(player);
    dropFormation(player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDeathEvent e) {
    dropFormation(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    dropFormation(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    Player player = e.getPlayer();
    J.runEntity(player, () -> withPlayerThread(player, () -> reconcile(player)), JOIN_RECONCILE_TICKS);
  }

  void cycle(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    int sicLevel = getConfig().sicLevel;
    GolemSignal next = signals.compute(player.getUniqueId(), (owner, current) -> OverseerRules.next(current, level, sicLevel));
    OverseerFeedback.cycled(this, player);
    GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SIGNAL, trusted("signal", GolemGuiSupport.localized(next.displayName())));
  }

  void send(Player player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    Config config = getConfig();
    UUID owner = player.getUniqueId();
    GolemSignal signal = OverseerRules.current(signals.get(owner), level, config.sicLevel);
    LivingEntity target = null;
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
    DyeColor channel = OverseerRules.crookChannel(level, config.channelFilterLevel, GolemItems.crookChannel(player.getInventory().getItemInMainHand()));
    OverseerSignal.Rally rally = signal == GolemSignal.FOLLOW && OverseerRules.unlocked(level, config.rallyLevel) && player.isOnGround()
        ? new OverseerRally(rallyCooldowns, owner, now, config.rallyCooldownSeconds * MILLIS_PER_SECOND)
        : null;
    Overseer.dispatch(new OverseerSignal(owner, signal, player.getLocation(), OverseerRules.radius(config.radiusByLevel, level), channel, target,
        now + config.sicHoldSeconds * MILLIS_PER_SECOND, rally, this::pulse));
    OverseerFeedback.sent(this, player, signal);
    if (target != null) {
      glow.show(player, target, config.sicGlowSeconds * TICKS_PER_SECOND);
    }
    confirm(player, signal, channel);
  }

  boolean filtersChannels(Player player) {
    return OverseerRules.unlocked(getActiveLevel(player), getConfig().channelFilterLevel);
  }

  boolean canSic(Player owner, LivingEntity target) {
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.getUniqueId(), target);
    return candidate != null && MinionTargeting.canDamageOwned(this, owner, candidate);
  }

  void reconcile(Player player) {
    Config config = getConfig();
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty() || !player.isOnline() || player.isDead() || !OverseerRules.unlocked(getActiveLevel(player), config.formationLevel)) {
      formation.forget(player.getUniqueId());
      OverseerArmor.settle(this, player, 0D);
      return;
    }
    Location at = player.getLocation();
    World world = at.getWorld();
    double reach = config.formationArmorRange * config.formationArmorRange;
    List<UUID> members = new ArrayList<>();
    int near = 0;
    for (GolemHandle handle : registry.get().loadedOf(player.getUniqueId())) {
      Mob golem = handle.entity();
      if (handle.isDormant() || !J.isOwnedByCurrentRegion(golem) || !golem.isValid() || !world.equals(golem.getWorld())
          || handle.state().mode() != GolemMode.FOLLOW) {
        continue;
      }
      members.add(handle.id());
      if (golem.getLocation().distanceSquared(at) <= reach) {
        near++;
      }
    }
    formation.update(player.getUniqueId(), members);
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

  private LivingEntity sicTarget(Player owner, double range) {
    Location eye = owner.getEyeLocation();
    RayTraceResult hit = owner.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, SIC_RAY_SIZE,
        entity -> sightable(owner, entity));
    return hit != null && hit.getHitEntity() instanceof LivingEntity target && canSic(owner, target) ? target : null;
  }

  private void pulse(Entity golem) {
    OverseerFeedback.pulse(this, golem);
  }

  private void confirm(Player player, GolemSignal signal, DyeColor channel) {
    Object name = GolemGuiSupport.localized(signal.displayName());
    if (channel == null) {
      GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SENT, trusted("signal", name));
      return;
    }
    GolemGuiSupport.notify(player, GolemancyMessages.OVERSEER_SENT_CHANNEL, trusted("signal", name),
        trusted("channel", GolemGuiSupport.localized(GolemGuiActions.channelName(ChannelMask.of(channel)))));
  }

  private void dropFormation(Player player) {
    formation.forget(player.getUniqueId());
    OverseerArmor.remove(this, player);
  }

  private static boolean sightable(Player owner, Entity entity) {
    if (!(entity instanceof LivingEntity living) || living == owner || living instanceof ArmorStand || !living.isValid() || living.isDead()) {
      return false;
    }
    if (living instanceof Player player && (player.getGameMode() == GameMode.SPECTATOR || !owner.canSee(player))) {
      return false;
    }
    MinionTargeting.Candidate candidate = MinionTargeting.captureOwned(owner.getUniqueId(), living);
    return candidate != null && !candidate.protectedFriendly();
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private final class Commander implements Overseer.Commander {
    @Override
    public void cycle(Player player) {
      GolemOverseer.this.cycle(player);
    }

    @Override
    public void send(Player player) {
      GolemOverseer.this.send(player);
    }

    @Override
    public boolean filtersChannels(Player player) {
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
