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
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemScale;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.minion.MinionTargeting;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GolemIronFrame extends SimpleAdaptation<GolemIronFrame.Config> {
  static final String STAT_SOURCE = "iron_frame";
  static final String COLOSSUS_SOURCE = "colossus";
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final long REPAIR_WINDOW_MILLIS = 1_000L;

  private final GolemStatSource frameStats = new FrameStats();
  private final GolemStatSource colossusStats = new ColossusStats();
  private final GolemColossus.Form colossusForm = new ColossusForm();
  private final Map<UUID, PendingRepair> repairs = new ConcurrentHashMap<>();
  private final Map<UUID, Resize> resizes = new ConcurrentHashMap<>();
  private final Set<UUID> sweeping = ConcurrentHashMap.newKeySet();
  private final AtomicLong resizeTokens = new AtomicLong();

  public GolemIronFrame() {
    super("golem-iron-frame");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.iron_frame");
    setIcon(Material.IRON_BLOCK);
    setInterval(SWEEP_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    GolemStats.unregister(frameStats);
    GolemStats.unregister(colossusStats);
    GolemColossus.unregister(colossusForm);
    repairs.clear();
    snapResizes();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemStats.register(frameStats);
    GolemStats.register(colossusStats);
    GolemColossus.register(colossusForm);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.sweepRadius = IronFrameRules.sweepRadius(loadedConfig.sweepRadius);
    loadedConfig.colossusScale = IronFrameRules.colossusScale(loadedConfig.colossusScale);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, Math.max(0, AnimusBindingRules.atLevel(config.healthBonusByLevel, level, 0)), 1);
    statLore(v, Math.max(0, AnimusBindingRules.atLevel(config.armorBonusByLevel, level, 0)), 2);
    statLore(v, Math.max(0D, AnimusBindingRules.atLevel(config.attackBonusByLevel, level, 0D)), 3);
    statLore(v, IronFrameRules.ingotHealBonus(config.ingotHealBonusByLevel, level), 4);
    int sweepTargets = IronFrameRules.sweepTargets(config.sweepTargetsByLevel, level, config.sweepLevel);
    if (sweepTargets > 0) {
      statLore(v, sweepTargets, 5);
    }
    if (GolemancyRules.unlocked(level, config.colossusLevel)) {
      statLore(v, Form.pc(config.colossusAttackBonus, 0), 6);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !repairs.isEmpty() || !resizes.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    repairs.values().removeIf(repair -> now >= repair.until());
    for (Map.Entry<UUID, Resize> entry : resizes.entrySet()) {
      Resize resize = entry.getValue();
      if (IronFrameRules.leaked(resize.leakDeadline(), now) && resizes.remove(entry.getKey(), resize)) {
        J.runEntity(resize.handle().entity(), () -> snap(resize));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (!(e.getRightClicked() instanceof IronGolem golem) || SkillGolemancy.heldType(e.getPlayer(), e.getHand()) != Material.IRON_INGOT) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem);
    if (bound.isEmpty()) {
      return;
    }
    double bonus = IronFrameRules.ingotHealBonus(getConfig().ingotHealBonusByLevel, ownerLevel(bound.get()));
    if (bonus <= 0D) {
      return;
    }
    UUID golemId = golem.getUniqueId();
    PendingRepair repair = new PendingRepair(bonus, M.ms() + REPAIR_WINDOW_MILLIS);
    repairs.put(golemId, repair);
    if (!J.runEntity(golem, () -> repairs.remove(golemId, repair), 1)) {
      repairs.remove(golemId, repair);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityRegainHealthEvent e) {
    if (e.getRegainReason() != RegainReason.CUSTOM || repairs.isEmpty()) {
      return;
    }
    PendingRepair repair = repairs.remove(e.getEntity().getUniqueId());
    if (repair != null && M.ms() < repair.until()) {
      e.setAmount(e.getAmount() + repair.bonus());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof IronGolem golem) || !(e.getEntity() instanceof LivingEntity victim) || sweeping.contains(golem.getUniqueId())) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem);
    if (bound.isEmpty() || bound.get().isDormant()) {
      return;
    }
    GolemHandle handle = bound.get();
    Config config = getConfig();
    int cap = IronFrameRules.sweepTargets(config.sweepTargetsByLevel, ownerLevel(handle), config.sweepLevel);
    double damage = IronFrameRules.sweepDamage(e.getDamage(), config.sweepDamageFraction);
    if (cap < 1 || damage <= 0D) {
      return;
    }
    Player owner = owner(handle);
    if (owner == null) {
      return;
    }
    List<LivingEntity> victims = sweepVictims(golem, victim, owner, cap, config.sweepRadius);
    if (victims.isEmpty()) {
      return;
    }
    sweeping.add(golem.getUniqueId());
    try {
      for (LivingEntity target : victims) {
        target.damage(damage, golem);
      }
    } finally {
      sweeping.remove(golem.getUniqueId());
    }
  }

  int ownerLevel(GolemHandle handle) {
    return ownerLevel(handle.owner());
  }

  Player owner(GolemHandle handle) {
    AdaptPlayer owner = getServer().getOnlineAdaptPlayer(handle.owner());
    return owner == null ? null : owner.getPlayer();
  }

  boolean canSweep(Player owner, LivingEntity candidate) {
    MinionTargeting.Candidate captured = MinionTargeting.captureOwned(owner.getUniqueId(), candidate);
    return captured != null && MinionTargeting.canDamageOwned(this, owner, captured);
  }

  boolean colossusAvailable(GolemHandle handle) {
    return handle.body() == GolemBody.IRON && GolemancyRules.unlocked(ownerLevel(handle), getConfig().colossusLevel);
  }

  GolemColossus.Toggle toggleColossus(GolemHandle handle) {
    Config config = getConfig();
    GolemColossus.Toggle toggle = IronFrameRules.colossusToggle(handle.body(), ownerLevel(handle), config.colossusLevel,
        handle.state().colossus(), busy(handle), () -> hasRoom(handle.entity(), config.colossusScale));
    switch (toggle) {
      case GROW -> transform(handle, true, config);
      case SHRINK -> transform(handle, false, config);
      case UNAVAILABLE, BUSY, NO_ROOM -> {
      }
    }
    return toggle;
  }

  void settleColossus(GolemHandle handle) {
    Config config = getConfig();
    if (busy(handle) || !IronFrameRules.outgrown(handle.state().colossus(), ownerLevel(handle), config.colossusLevel)) {
      return;
    }
    transform(handle, false, config);
  }

  boolean hasRoom(Mob golem, double scale) {
    Location at = golem.getLocation();
    World world = at.getWorld();
    return world != null && !PaperCompat.hasCollisionsIn(world, IronFrameRules.colossusBox(at.getX(), at.getY(), at.getZ(), scale));
  }

  void resize(Mob golem, double from, double to, double fraction) {
    GolemScale.stepTo(golem, from, to, fraction);
  }

  void refreshStats(GolemHandle handle) {
    GolemStats.recompute(handle);
  }

  static List<GolemStatDelta> frameStats(Config config, int level) {
    return IronFrameRules.frameDeltas(config.healthBonusByLevel, config.armorBonusByLevel, config.attackBonusByLevel, level);
  }

  static List<GolemStatDelta> colossusStats(Config config, int level) {
    return IronFrameRules.colossusDeltas(level, config.colossusLevel, config.colossusAttackBonus);
  }

  private List<LivingEntity> sweepVictims(IronGolem golem, LivingEntity victim, Player owner, int cap, double radius) {
    Location center = victim.getLocation();
    List<LivingEntity> nearby = PaperCompat.nearbyLivingEntities(center, radius);
    List<IronFrameRules.SweepCandidate<LivingEntity>> candidates = new ArrayList<>(nearby.size());
    for (LivingEntity candidate : nearby) {
      boolean excluded = candidate == victim || candidate == golem;
      candidates.add(new IronFrameRules.SweepCandidate<>(candidate, excluded, candidate instanceof Enemy, candidate.getLocation().distanceSquared(center)));
    }
    return IronFrameRules.sweepVictims(candidates, radius, cap, candidate -> canSweep(owner, candidate));
  }

  private boolean busy(GolemHandle handle) {
    Resize resize = resizes.get(handle.id());
    return resize != null && !resize.handle().isRetired();
  }

  private void transform(GolemHandle handle, boolean colossus, Config config) {
    double bound = GolemScale.target(GolemBody.IRON);
    double from = colossus ? bound : config.colossusScale;
    double to = colossus ? config.colossusScale : bound;
    int ticks = IronFrameRules.growTicks(config.colossusGrowTicks);
    Resize resize = new Resize(resizeTokens.incrementAndGet(), handle, from, to, ticks, IronFrameRules.leakDeadline(M.ms(), ticks));
    resizes.put(handle.id(), resize);
    handle.setColossus(colossus);
    resize(handle.entity(), from, to, 0D);
    refreshStats(handle);
    scheduleStep(resize, 1);
  }

  private void scheduleStep(Resize resize, int index) {
    UUID id = resize.handle().id();
    if (!J.runEntity(resize.handle().entity(), () -> step(resize, index), 1, () -> resizes.remove(id, resize))) {
      resizes.remove(id, resize);
    }
  }

  private void step(Resize resize, int index) {
    GolemHandle handle = resize.handle();
    if (resizes.get(handle.id()) != resize) {
      return;
    }
    Mob golem = handle.entity();
    if (handle.isRetired() || !golem.isValid()) {
      resizes.remove(handle.id(), resize);
      return;
    }
    resize(golem, resize.from(), resize.to(), (double) index / resize.ticks());
    if (index >= resize.ticks()) {
      resizes.remove(handle.id(), resize);
      return;
    }
    scheduleStep(resize, index + 1);
  }

  private void snap(Resize resize) {
    GolemHandle handle = resize.handle();
    Mob golem = handle.entity();
    if (!handle.isRetired() && golem.isValid()) {
      resize(golem, resize.from(), resize.to(), 1D);
    }
  }

  private void snapResizes() {
    List<Resize> pending = List.copyOf(resizes.values());
    resizes.clear();
    for (Resize resize : pending) {
      J.runEntity(resize.handle().entity(), () -> snap(resize));
    }
  }

  private record PendingRepair(double bonus, long until) {
  }

  private record Resize(long token, GolemHandle handle, double from, double to, int ticks, long leakDeadline) {
  }

  private final class FrameStats implements GolemStatSource {
    @Override
    public String id() {
      return STAT_SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      return handle.body() == GolemBody.IRON ? frameStats(getConfig(), ownerLevel(handle)) : List.of();
    }
  }

  private final class ColossusStats implements GolemStatSource {
    @Override
    public String id() {
      return COLOSSUS_SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      if (handle.body() != GolemBody.IRON || !handle.state().colossus()) {
        return List.of();
      }
      return colossusStats(getConfig(), ownerLevel(handle));
    }
  }

  private final class ColossusForm implements GolemColossus.Form {
    @Override
    public boolean available(GolemHandle handle) {
      return colossusAvailable(handle);
    }

    @Override
    public GolemColossus.Toggle toggle(GolemHandle handle) {
      return toggleColossus(handle);
    }

    @Override
    public void settle(GolemHandle handle) {
      settleColossus(handle);
    }
  }

  @ConfigDescription("Lets you bind iron golems and strengthens them with extra health, armor and attack, stronger ingot repairs, sweeping punches and a full-size Colossus Form.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Extra maximum health for your bound iron golems, by Iron Frame level from level 1 upward.", impact = "Higher values make iron golems tougher; a golem heals by any gain above the highest maximum health it has had.")
    List<Integer> healthBonusByLevel = new ArrayList<>(List.of(10, 20, 30, 40, 50));
    @ConfigDoc(value = "Extra armor for your bound iron golems, by Iron Frame level from level 1 upward.", impact = "Higher values make iron golems take less damage.")
    List<Integer> armorBonusByLevel = new ArrayList<>(List.of(2, 4, 6, 8, 10));
    @ConfigDoc(value = "Extra attack damage for your bound iron golems, by Iron Frame level from level 1 upward.", impact = "Higher values make iron golems hit harder.")
    List<Double> attackBonusByLevel = new ArrayList<>(List.of(1.5, 3.0, 4.5, 6.0, 7.5));
    @ConfigDoc(value = "Extra health an iron ingot repair restores on your bound iron golems, on top of the vanilla 25, by Iron Frame level from level 1 upward.", impact = "Higher values make each ingot heal more.")
    List<Integer> ingotHealBonusByLevel = new ArrayList<>(List.of(5, 10, 15, 20, 25));
    @ConfigDoc(value = "Iron Frame level at which your bound iron golems' punches also hit nearby hostile mobs with Sweeping Fists.", impact = "Lower values unlock Sweeping Fists sooner.")
    int sweepLevel = 3;
    @ConfigDoc(value = "Extra hostile mobs each punch of a bound iron golem also hits with Sweeping Fists, by Iron Frame level from level 1 upward.", impact = "Higher values let each punch hit more mobs.")
    List<Integer> sweepTargetsByLevel = new ArrayList<>(List.of(0, 0, 1, 1, 2));
    @ConfigDoc(value = "Fraction of a punch's damage that Sweeping Fists deals to each extra mob. 0.5 is half.", impact = "Higher values make Sweeping Fists hit harder.")
    double sweepDamageFraction = 0.5;
    @ConfigDoc(value = "Blocks around the punched mob within which Sweeping Fists finds extra hostile mobs, from 0 to 8.", impact = "Higher values let Sweeping Fists reach mobs farther from the punched one.")
    double sweepRadius = 2.0;
    @ConfigDoc(value = "Iron Frame level at which the Golem menu offers Colossus Form for your bound iron golems. Golems in Colossus Form shrink back when you drop below it.", impact = "Lower values unlock Colossus Form sooner.")
    int colossusLevel = 5;
    @ConfigDoc(value = "Scale a bound iron golem grows to in Colossus Form, from 0.36 to 1.5. 1.0 is the vanilla iron golem size.", impact = "Higher values make golems in Colossus Form larger and need more room to grow.")
    double colossusScale = 1.0;
    @ConfigDoc(value = "Fraction added to a bound iron golem's total attack damage, frame bonus included, while it is in Colossus Form. 0.2 is 20% more.", impact = "Higher values make golems in Colossus Form hit harder.")
    double colossusAttackBonus = 0.20;
    @ConfigDoc(value = "Ticks a bound iron golem takes to grow into Colossus Form or shrink back.", impact = "Lower values make the change faster.")
    int colossusGrowTicks = 20;

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
