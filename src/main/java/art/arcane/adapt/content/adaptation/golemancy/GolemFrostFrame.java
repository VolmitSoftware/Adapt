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
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GolemFrostFrame extends SimpleAdaptation<GolemFrostFrame.Config> {
  static final String STAT_SOURCE = "frost_frame";
  private static final int EXTRA_SHOT_DELAY_TICKS = 5;
  private static final double TICKS_PER_SECOND = 20D;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final GolemStatSource frameStats = new FrameStats();
  private final Map<UUID, FrostFrameRules.FrostbiteState> frostbites = new ConcurrentHashMap<>();
  private final Set<UUID> throwingExtra = ConcurrentHashMap.newKeySet();

  public GolemFrostFrame() {
    super("golem-frost-frame");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.frost_frame");
    setIcon(Material.SNOW_BLOCK);
    setInterval(SWEEP_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    GolemStats.unregister(frameStats);
    frostbites.clear();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemStats.register(frameStats);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, FrostFrameRules.atLevel(config.healthBonusByLevel, level), 1);
    statLore(v, FrostFrameRules.atLevel(config.snowballDamageByLevel, level), 2);
    statLore(v, C.YELLOW, "* ", Form.pc(FrostFrameRules.warmMultiplier(config.warmBiomeDamageMultiplierByLevel, level), 0), 3);
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(GolemancyMessages.FROST_FRAME_LORE4));
    if (FrostFrameRules.unlocked(level, config.chillLevel)) {
      statLore(v, Math.max(1, config.chillTicks) / TICKS_PER_SECOND, 5);
    }
    if (FrostFrameRules.unlocked(level, config.fireRateBonusLevel)) {
      statLore(v, Form.pc(config.extraSnowballChance, 0), 6);
    }
    if (FrostFrameRules.unlocked(level, config.frostbiteLevel)) {
      statLore(v, Math.max(1, config.frostbiteHits), 7);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !frostbites.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    long window = getConfig().frostbiteWindowMillis;
    frostbites.values().removeIf(state -> FrostFrameRules.expired(state, now, window));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (!(e.getEntity() instanceof Snowball snowball) || !(snowball.getShooter() instanceof Snowman golem)) {
      return;
    }
    Optional<GolemHandle> handle = GolemancyRuntime.handle(golem);
    int level = handle.isEmpty() ? 0 : ownerLevel(handle.get());
    if (level < 1) {
      return;
    }
    LivingEntity target = golem.getTarget();
    GolemSnowball.tag(snowball, level, target == null ? null : target.getUniqueId());
    Config config = getConfig();
    if (target != null && !throwingExtra.contains(golem.getUniqueId())
        && FrostFrameRules.extraShot(level, config.fireRateBonusLevel, config.extraSnowballChance, ThreadLocalRandom.current().nextDouble())) {
      J.runEntity(golem, () -> throwExtra(golem, target), EXTRA_SHOT_DELAY_TICKS);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    DamageCause cause = e.getCause();
    if ((cause != DamageCause.DROWNING && cause != DamageCause.MELTING) || !(e.getEntity() instanceof Snowman golem)) {
      return;
    }
    Optional<GolemHandle> handle = GolemancyRuntime.handle(golem);
    if (handle.isEmpty()) {
      return;
    }
    double damage = e.getDamage();
    double weathered = FrostFrameRules.weatherDamage(cause, damage, ownerLevel(handle.get()), getConfig().warmBiomeDamageMultiplierByLevel);
    if (weathered <= 0D) {
      e.setCancelled(true);
    } else if (weathered != damage) {
      e.setDamage(weathered);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    snowballHit(e).ifPresent(hit -> e.setDamage(FrostFrameRules.hitDamage(e.getDamage(), getConfig().snowballDamageByLevel, hit.level())));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSnowballLanded(EntityDamageByEntityEvent e) {
    snowballHit(e).ifPresent(this::applyFrost);
  }

  int ownerLevel(GolemHandle handle) {
    AdaptPlayer owner = getServer().getOnlineAdaptPlayer(handle.owner());
    return owner == null ? 0 : getLevel(owner);
  }

  static List<GolemStatDelta> frameStats(Config config, GolemBody body, int level) {
    double bonus = body == GolemBody.FROST ? FrostFrameRules.atLevel(config.healthBonusByLevel, level) : 0D;
    return bonus > 0D ? List.of(GolemStatDelta.add(GolemStat.MAX_HEALTH, bonus)) : List.of();
  }

  private Optional<SnowballHit> snowballHit(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Snowball snowball) || !(e.getEntity() instanceof LivingEntity victim)) {
      return Optional.empty();
    }
    return GolemSnowball.read(snowball)
        .filter(tag -> FrostFrameRules.affects(tag.target(), victim.getUniqueId(), victim instanceof Enemy))
        .map(tag -> new SnowballHit(victim, tag.level()));
  }

  private void applyFrost(SnowballHit hit) {
    Config config = getConfig();
    LivingEntity victim = hit.victim();
    if (FrostFrameRules.unlocked(hit.level(), config.chillLevel)) {
      victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Math.max(1, config.chillTicks), 0, false, true, true));
    }
    if (!FrostFrameRules.unlocked(hit.level(), config.frostbiteLevel)) {
      return;
    }
    long now = M.ms();
    FrostFrameRules.FrostbiteState state = frostbites.compute(victim.getUniqueId(), (id, current) -> FrostFrameRules.frostbite(current, now,
        config.frostbiteHits, config.frostbiteWindowMillis, config.frostbitePerTargetCooldownMillis));
    if (state.triggered()) {
      victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), config.frostbiteFreezeTicks));
    }
  }

  private void throwExtra(Snowman golem, LivingEntity target) {
    boolean awake = GolemancyRuntime.handle(golem).filter(handle -> !handle.isDormant()).isPresent();
    if (!awake || !golem.isValid() || golem.isDead() || !target.isValid() || target.isDead() || !golem.getWorld().equals(target.getWorld())) {
      return;
    }
    throwingExtra.add(golem.getUniqueId());
    try {
      PaperCompat.rangedAttack(golem, target);
    } finally {
      throwingExtra.remove(golem.getUniqueId());
    }
  }

  private record SnowballHit(LivingEntity victim, int level) {
  }

  private final class FrameStats implements GolemStatSource {
    @Override
    public String id() {
      return STAT_SOURCE;
    }

    @Override
    public List<GolemStatDelta> deltas(GolemHandle handle) {
      return frameStats(getConfig(), handle.body(), ownerLevel(handle));
    }
  }

  @ConfigDescription("Strengthens your bound frost golems with extra health, damaging snowballs and protection from rain and heat.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Extra maximum health for your bound frost golems, by Frost Frame level from level 1 upward.", impact = "Higher values make frost golems tougher; a raise also heals them by the difference.")
    List<Integer> healthBonusByLevel = new ArrayList<>(List.of(4, 8, 12, 16, 20));
    @ConfigDoc(value = "Damage a bound frost golem's snowball deals to the golem's target or a hostile mob, by Frost Frame level from level 1 upward.", impact = "Higher values make snowballs hit harder; snowballs never deal less than vanilla, such as 3 against blazes.")
    List<Integer> snowballDamageByLevel = new ArrayList<>(List.of(1, 2, 2, 3, 4));
    @ConfigDoc(value = "Multiplier on the damage bound frost golems take in warm biomes, by Frost Frame level from level 1 upward.", impact = "Lower values protect frost golems in warm biomes; 0 makes them immune.")
    List<Double> warmBiomeDamageMultiplierByLevel = new ArrayList<>(List.of(1.0, 0.5, 0.0, 0.0, 0.0));
    @ConfigDoc(value = "Frost Frame level at which snowball hits slow the target with Chill.", impact = "Lower values unlock Chill sooner.")
    int chillLevel = 3;
    @ConfigDoc(value = "Ticks of Slowness I a Chill snowball hit applies.", impact = "Higher values keep targets slowed longer.")
    int chillTicks = 40;
    @ConfigDoc(value = "Frost Frame level at which bound frost golems can throw an extra snowball.", impact = "Lower values unlock the faster fire rate sooner.")
    int fireRateBonusLevel = 4;
    @ConfigDoc(value = "Chance, from 0 to 1, that each snowball a bound frost golem throws is followed 5 ticks later by a second one at the same target.", impact = "Higher values raise the fire rate.")
    double extraSnowballChance = 0.25;
    @ConfigDoc(value = "Frost Frame level at which snowball hits can freeze the target with Frostbite.", impact = "Lower values unlock Frostbite sooner.")
    int frostbiteLevel = 5;
    @ConfigDoc(value = "Snowball hits that must land on one target within frostbiteWindowMillis to freeze it.", impact = "Lower values freeze targets sooner.")
    int frostbiteHits = 4;
    @ConfigDoc(value = "Milliseconds within which the frostbiteHits snowball hits must land.", impact = "Higher values make Frostbite easier to trigger.")
    long frostbiteWindowMillis = 5000;
    @ConfigDoc(value = "Freeze ticks Frostbite gives the target.", impact = "Higher values keep targets frozen longer; 140 is fully frozen.")
    int frostbiteFreezeTicks = 140;
    @ConfigDoc(value = "Milliseconds before Frostbite can freeze the same target again.", impact = "Lower values let Frostbite freeze a target more often.")
    long frostbitePerTargetCooldownMillis = 10000;

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
