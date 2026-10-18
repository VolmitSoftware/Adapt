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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.HostileDamagePool;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.skill.golemancy.CopperCare;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
import art.arcane.adapt.content.skill.golemancy.GolemXpRules;
import art.arcane.adapt.content.skill.golemancy.RollingXpCap;
import art.arcane.adapt.content.skill.golemancy.SpawnerMobTag;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.world.WeatheringCopperState;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillGolemancy extends SimpleSkill<SkillGolemancy.Config> {
  private static final long CARE_CHECK_STALE_MILLIS = 5_000L;

  private final GolemBuildAttribution buildAttribution = new GolemBuildAttribution();
  private final GolemXpCooldowns<BuildCooldownKey> buildCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<WitnessCooldownKey> witnessCooldowns = new GolemXpCooldowns<>();
  private final RollingXpCap witnessCap = new RollingXpCap();
  private final ConcurrentHashMap<UUID, Long> careChecks = new ConcurrentHashMap<>();

  public SkillGolemancy() {
    super("golemancy", SkillPresentation.of(SkillMessages.GOLEMANCY_NAME, SkillMessages.GOLEMANCY_ICON, SkillMessages.GOLEMANCY_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_AQUA);
    setInterval(3620);
    setIcon(Material.CARVED_PUMPKIN);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CARVED_PUMPKIN)
        .key("challenge_golemancy_built_10")
        .model(CustomModel.get(Material.CARVED_PUMPKIN, "advancement", "golemancy", "challenge_golemancy_built_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_BLOCK)
            .key("challenge_golemancy_built_100")
            .model(CustomModel.get(Material.IRON_BLOCK, "advancement", "golemancy", "challenge_golemancy_built_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_golemancy_built_10", "golemancy.built", 10, () -> getConfig().challengeBuiltReward);
    registerMilestone("challenge_golemancy_built_100", "golemancy.built", 100, () -> getConfig().challengeBuiltReward * 5);
  }

  @Override
  public void onTick() {
    long nowMillis = M.ms();
    buildAttribution.expire(System.nanoTime(), getConfig().buildAttributionWindowMillis);
    buildCooldowns.sweep(nowMillis, getConfig().buildXpCooldownMillis);
    witnessCooldowns.sweep(nowMillis, getConfig().witnessCooldownMillis);
    witnessCap.sweep(nowMillis);
    careChecks.values().removeIf(scheduledAt -> nowMillis - scheduledAt > CARE_CHECK_STALE_MILLIS);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Block block = e.getBlockPlaced();
    Material type = block.getType();
    if ((type != Material.CARVED_PUMPKIN && type != Material.JACK_O_LANTERN) || shouldReturnForWorld(block.getWorld(), this)) {
      return;
    }
    GolemBuildAttribution.HeadPlacement placement = new GolemBuildAttribution.HeadPlacement(
        block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), e.getPlayer().getUniqueId(), System.nanoTime());
    buildAttribution.recordPlacement(placement, getConfig().buildAttributionRadius, getConfig().buildAttributionWindowMillis)
        .ifPresent(build -> J.runEntity(build.spawn().golem(), () -> applyBuild(build)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CreatureSpawnEvent e) {
    SpawnReason reason = e.getSpawnReason();
    LivingEntity golem = e.getEntity();
    if (SpawnerMobTag.isSpawnerReason(reason) && isEnabled()) {
      SpawnerMobTag.mark(golem);
      return;
    }
    if (GolemBuildAttribution.bodyKey(reason) == null || shouldReturnForWorld(golem.getWorld(), this)) {
      return;
    }
    Location at = golem.getLocation();
    GolemBuildAttribution.GolemSpawn spawn = new GolemBuildAttribution.GolemSpawn(
        golem, reason, golem.getWorld().getName(), at.getX(), at.getY(), at.getZ(), System.nanoTime());
    buildAttribution.recordSpawn(spawn, getConfig().buildAttributionRadius, getConfig().buildAttributionWindowMillis)
        .ifPresent(this::applyBuild);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof LivingEntity golem)
        || !HostileDamagePool.isPoolBody(golem.getType())
        || !HostileDamagePool.isHostileDamager(e.getDamager())
        || shouldReturnForWorld(golem.getWorld(), this)) {
      return;
    }
    HostileDamagePool.add(golem, e.getFinalDamage(), maxHealthOf(golem));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (!(e.getRightClicked() instanceof IronGolem golem) || !golem.isPlayerCreated()) {
      return;
    }
    Player player = e.getPlayer();
    if (heldType(player, e.getHand()) != Material.IRON_INGOT) {
      return;
    }
    double before = golem.getHealth();
    shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleRepair(player, golem, before)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    if (!(victim instanceof Enemy) || !(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent lastDamage)) {
      return;
    }
    Golem golem = GolemXpRules.witnessGolem(lastDamage.getDamager());
    if (golem == null
        || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(golem))
        || MinionIdentity.isMinion(victim)
        || ExcavationGraveDigger.isGraveMob(victim)
        || MinionIdentity.isMinion(golem)
        || shouldReturnForWorld(victim.getWorld(), this)) {
      return;
    }
    double killValue = GolemXpRules.killValue(getConfig().golemKillXpBase, maxHealthOf(victim), getConfig().golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.witnessXp(killValue, getConfig().witnessXpMultiplier, SpawnerMobTag.isTagged(victim), getConfig().spawnerMobXpMultiplier);
    if (xp <= 0D) {
      return;
    }
    Location center = golem.getLocation();
    double radius = getConfig().witnessRadius;
    UUID golemId = golem.getUniqueId();
    for (Player witness : PaperCompat.nearbyPlayers(center, radius)) {
      if (witness.getLocation().distanceSquared(center) <= radius * radius) {
        shouldReturnForPlayer(witness, () -> payWitness(witness, golemId, center, xp));
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    GolemancyRuntime.configure(golemSettings(newConfig));
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("io.papermc.paper.world.WeatheringCopperState")) {
      return List.of();
    }
    return List.of(new CopperCareListener());
  }

  static GolemSettings golemSettings(Config config) {
    Map<GolemBody, Double> scales = new EnumMap<>(GolemBody.class);
    scales.put(GolemBody.FROST, config.bodyScaleFrost);
    scales.put(GolemBody.COPPER, config.bodyScaleCopper);
    scales.put(GolemBody.IRON, config.bodyScaleIron);
    scales.put(GolemBody.BONE, config.bodyScaleBone);
    Map<GolemBody, Set<GolemCoreType>> cores = new EnumMap<>(GolemBody.class);
    cores.put(GolemBody.FROST, GolemSettings.parseCores(config.allowedCoresFrost));
    cores.put(GolemBody.COPPER, GolemSettings.parseCores(config.allowedCoresCopper));
    cores.put(GolemBody.IRON, GolemSettings.parseCores(config.allowedCoresIron));
    return new GolemSettings(config.dormantInvulnerable, config.guardWhileOwnerOffline, config.serverMaxActiveGolems,
        config.maxBoundGolemsPerChunk, config.hardCapPerPlayer, scales, cores, config.deathDropCoreChance, config.deathDropRuneChance);
  }

  private void applyBuild(GolemBuildAttribution.Build build) {
    Entity golem = build.spawn().golem();
    GolemBuildAttribution.stamp(golem, build.builder(), System.currentTimeMillis());
    Player builder = Bukkit.getPlayer(build.builder());
    if (builder == null) {
      return;
    }
    Location at = golem.getLocation();
    SpawnReason reason = build.spawn().reason();
    shouldReturnForPlayer(builder, () -> rewardBuild(builder, reason, at));
  }

  private void rewardBuild(Player builder, SpawnReason reason, Location at) {
    addStat(builder, "golemancy.built", 1);
    double xp = buildXp(reason);
    if (xp <= 0D || !buildCooldowns.tryClaim(new BuildCooldownKey(builder.getUniqueId(), reason), M.ms(), getConfig().buildXpCooldownMillis)) {
      return;
    }
    xp(builder, at, xp, "golemancy:build:" + GolemBuildAttribution.bodyKey(reason));
    fx(at, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 5, 1.2D)
        .particle(Particles.VILLAGER_HAPPY, 4, 0D, 0.8D, 0D, 0.3D, 0D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
  }

  private double buildXp(SpawnReason reason) {
    return switch (reason) {
      case BUILD_SNOWMAN -> getConfig().buildSnowXp;
      case BUILD_COPPERGOLEM -> getConfig().buildCopperXp;
      case BUILD_IRONGOLEM -> getConfig().buildIronXp;
      default -> 0D;
    };
  }

  private void settleRepair(Player player, IronGolem golem, double before) {
    double healed = golem.getHealth() - before;
    if (!(healed > 0D)) {
      return;
    }
    double xp = GolemXpRules.repairXp(HostileDamagePool.consume(golem, healed), getConfig().repairXpPerHealth);
    if (xp <= 0D) {
      return;
    }
    Location at = golem.getLocation();
    shouldReturnForPlayer(player, () -> xp(player, at, xp, "golemancy:repair"));
    fx(at.clone().add(0D, 1.5D, 0D), FxPriority.AMBIENT)
        .burst(Particles.VILLAGER_HAPPY, 5, 0.6D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.5F);
  }

  private void payWitness(Player witness, UUID golemId, Location center, double xp) {
    long nowMillis = M.ms();
    if (!witnessCooldowns.tryClaim(new WitnessCooldownKey(golemId, witness.getUniqueId()), nowMillis, getConfig().witnessCooldownMillis)) {
      return;
    }
    double granted = witnessCap.claim(witness.getUniqueId(), xp, getConfig().witnessHourlyCap, nowMillis);
    if (granted > 0D) {
      xp(witness, center, granted, "golemancy:witness");
    }
  }

  private void scheduleCareCheck(Entity golem, Runnable settle) {
    UUID golemId = golem.getUniqueId();
    long scheduledAt = M.ms();
    if (careChecks.putIfAbsent(golemId, scheduledAt) != null) {
      return;
    }
    Runnable check = () -> {
      careChecks.remove(golemId, scheduledAt);
      settle.run();
    };
    if (!J.runEntity(golem, check, 1)) {
      careChecks.remove(golemId, scheduledAt);
    }
  }

  private static Material heldType(Player player, EquipmentSlot hand) {
    ItemStack held = player.getInventory().getItem(hand);
    return held == null ? Material.AIR : held.getType();
  }

  private static double maxHealthOf(LivingEntity entity) {
    if (Attributes.MAX_HEALTH == null) {
      return 20D;
    }
    IAttribute attribute = Version.get().getAttribute(entity, Attributes.MAX_HEALTH);
    return attribute == null ? 20D : attribute.getValue();
  }

  private record BuildCooldownKey(UUID player, SpawnReason reason) {
  }

  private record WitnessCooldownKey(UUID golem, UUID player) {
  }

  private final class CopperCareListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerInteractEntityEvent e) {
      if (!(e.getRightClicked() instanceof CopperGolem golem)) {
        return;
      }
      Player player = e.getPlayer();
      Material held = heldType(player, e.getHand());
      if (Tag.ITEMS_AXES.isTagged(held)) {
        WeatheringCopperState before = golem.getWeatheringState();
        shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleScrape(player, golem, before)));
      } else if (held == Material.HONEYCOMB) {
        boolean waxedBefore = CopperCare.waxed(golem.getOxidizing());
        shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleWax(player, golem, waxedBefore)));
      }
    }

    private void settleScrape(Player player, CopperGolem golem, WeatheringCopperState before) {
      if (!CopperCare.scraped(before, golem.getWeatheringState())) {
        return;
      }
      Location at = golem.getLocation();
      shouldReturnForPlayer(player, () -> xp(player, at, getConfig().copperScrapeXp, "golemancy:copper:scrape"));
    }

    private void settleWax(Player player, CopperGolem golem, boolean waxedBefore) {
      if (!CopperCare.newlyWaxed(waxedBefore, CopperCare.waxed(golem.getOxidizing())) || !CopperCare.claimWaxPayment(golem)) {
        return;
      }
      Location at = golem.getLocation();
      shouldReturnForPlayer(player, () -> xp(player, at, getConfig().copperWaxXp, "golemancy:copper:wax"));
    }
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Enables or disables the Golemancy skill.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @ConfigDoc(value = "Legacy ampersand color code used for Golemancy in menus and text.", impact = "Changes only how the skill name is colored.")
    String skillColor = "&3";
    @ConfigDoc(value = "Skill XP paid for building a snow golem.", impact = "Higher values reward snow golem builds more; 0 pays nothing.")
    double buildSnowXp = 40;
    @ConfigDoc(value = "Skill XP paid for building a copper golem.", impact = "Higher values reward copper golem builds more; 0 pays nothing.")
    double buildCopperXp = 90;
    @ConfigDoc(value = "Skill XP paid for building an iron golem.", impact = "Higher values reward iron golem builds more; 0 pays nothing.")
    double buildIronXp = 180;
    @ConfigDoc(value = "Milliseconds before one player earns build XP again for the same golem type.", impact = "Higher values limit repeated build XP; the builder record is still written during the cooldown.")
    long buildXpCooldownMillis = 20000;
    @ConfigDoc(value = "Milliseconds a placed carved pumpkin or jack o'lantern and a golem spawn may be apart and still credit the builder.", impact = "Higher values tolerate more delay between the head placement and the spawn.")
    long buildAttributionWindowMillis = 250;
    @ConfigDoc(value = "Blocks between the placed head block and the spawned golem for the build to credit the player.", impact = "Higher values match builds farther from the head block.")
    double buildAttributionRadius = 3.0;
    @ConfigDoc(value = "Blocks from a golem within which players earn witness XP when it kills a hostile mob.", impact = "Higher values let players farther away share in golem kills.")
    double witnessRadius = 16.0;
    @ConfigDoc(value = "Fraction of the golem kill value paid to each witnessing player.", impact = "Higher values pay more witness XP per kill.")
    double witnessXpMultiplier = 0.35;
    @ConfigDoc(value = "Milliseconds before one player earns witness XP again from the same golem.", impact = "Higher values limit witness XP from a single busy golem.")
    long witnessCooldownMillis = 6000;
    @ConfigDoc(value = "Most witness XP one player can earn in any rolling 60 minutes.", impact = "Higher values allow more passive witness XP per hour.")
    double witnessHourlyCap = 600;
    @ConfigDoc(value = "Base skill XP value of a golem kill before the victim's health is added.", impact = "Higher values raise the value of every golem kill.")
    double golemKillXpBase = 15;
    @ConfigDoc(value = "Skill XP added to a golem kill value per point of the victim's maximum health.", impact = "Higher values make tougher victims worth more.")
    double golemKillMaxHealthMultiplier = 1.0;
    @ConfigDoc(value = "Skill XP per point of health restored when repairing an iron golem that hostile mobs damaged.", impact = "Higher values pay more for repairs; damage from players never counts.")
    double repairXpPerHealth = 2.0;
    @ConfigDoc(value = "Skill XP paid for scraping one oxidation stage off a copper golem with an axe.", impact = "Higher values reward copper golem care more.")
    double copperScrapeXp = 60;
    @ConfigDoc(value = "Skill XP paid the first time you wax a copper golem with honeycomb.", impact = "Higher values reward waxing more; each golem pays once.")
    double copperWaxXp = 30;
    @ConfigDoc(value = "Multiplier applied to Golemancy XP from mobs that came from a spawner or trial spawner.", impact = "Lower values make spawner farms pay less; 1.0 pays the same as natural mobs.")
    double spawnerMobXpMultiplier = 0.3;
    @ConfigDoc(value = "Knowledge paid by the golem building challenges.", impact = "Higher values pay more knowledge; the 100 builds challenge pays five times this value.")
    double challengeBuiltReward = 500;
    @ConfigDoc(value = "Makes bound golems invulnerable while they are dormant.", impact = "Set to false to let dormant golems take damage while their owner is offline.")
    boolean dormantInvulnerable = true;
    @ConfigDoc(value = "Keeps Guardian golems awake while their owner is offline.", impact = "Set to true to let Guardian golems keep defending while their owner is away.")
    boolean guardWhileOwnerOffline = false;
    @ConfigDoc(value = "Most bound golems that may be awake on the whole server at once.", impact = "Lower values save server performance; golems beyond the limit stay dormant until room frees.")
    int serverMaxActiveGolems = 1500;
    @ConfigDoc(value = "Most bound golems allowed in one chunk when binding.", impact = "Lower values spread golems out; binding fails in a chunk that is already full.")
    int maxBoundGolemsPerChunk = 8;
    @ConfigDoc(value = "Most golems one player can own at once, counting bound, packed, and shattered golems, and missing golems until they are released.", impact = "Lower values limit every player, whatever their adaptation levels allow.")
    int hardCapPerPlayer = 32;
    @ConfigDoc(value = "Size of a bound frost golem as a multiple of its normal size.", impact = "Lower values make bound frost golems smaller; 1.0 keeps the normal size.")
    double bodyScaleFrost = 0.50;
    @ConfigDoc(value = "Size of a bound copper golem as a multiple of its normal size.", impact = "Lower values make bound copper golems smaller; 1.0 keeps the normal size.")
    double bodyScaleCopper = 1.00;
    @ConfigDoc(value = "Size of a bound iron golem as a multiple of its normal size.", impact = "Lower values make bound iron golems smaller; 1.0 keeps the normal size.")
    double bodyScaleIron = 0.36;
    @ConfigDoc(value = "Size of a bound bone golem as a multiple of its normal size.", impact = "Lower values make bound bone golems smaller; 1.0 keeps the normal size.")
    double bodyScaleBone = 1.00;
    @ConfigDoc(value = "Cores a frost golem can be bound with.", impact = "Remove a core to stop frost golems taking that role.")
    List<String> allowedCoresFrost = new ArrayList<>(List.of("BLANK", "GUARDIAN", "GATHERER", "HARVESTER"));
    @ConfigDoc(value = "Cores a copper golem can be bound with.", impact = "Remove a core to stop copper golems taking that role.")
    List<String> allowedCoresCopper = new ArrayList<>(List.of("BLANK", "GATHERER", "HARVESTER", "LUMBER", "RANCHER", "SORTER", "ANGLER"));
    @ConfigDoc(value = "Cores an iron golem can be bound with.", impact = "Remove a core to stop iron golems taking that role.")
    List<String> allowedCoresIron = new ArrayList<>(List.of("BLANK", "GUARDIAN", "GATHERER", "LUMBER", "BREAKER", "RANCHER", "ANGLER"));
    @ConfigDoc(value = "Chance that each core drops when a bound golem dies.", impact = "Higher values return more cores on death; 1.0 always drops them.")
    double deathDropCoreChance = 0.5;
    @ConfigDoc(value = "Chance that each rune drops when a bound golem dies.", impact = "Higher values return more runes on death; 1.0 always drops them.")
    double deathDropRuneChance = 0.5;
  }
}
