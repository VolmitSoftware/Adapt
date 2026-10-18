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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRepairXp;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemWorkSettings;
import art.arcane.adapt.api.golem.GolemWorkStats;
import art.arcane.adapt.api.golem.GolemWorkXp;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.HostileDamagePool;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.gui.CrookInteractions;
import art.arcane.adapt.api.golem.gui.GuiCursor;
import art.arcane.adapt.api.golem.gui.NodeInteractions;
import art.arcane.adapt.api.golem.gui.NodeTagPrompt;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeMatter;
import art.arcane.adapt.api.golem.node.NodeSettings;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.api.golem.plinth.Infusion;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.InfusionRecipes;
import art.arcane.adapt.api.golem.plinth.LevelCharge;
import art.arcane.adapt.api.golem.plinth.PlinthCharge;
import art.arcane.adapt.api.golem.plinth.PlinthHost;
import art.arcane.adapt.api.golem.plinth.PlinthSettings;
import art.arcane.adapt.api.golem.plinth.PlinthSlotMatter;
import art.arcane.adapt.api.golem.plinth.PlinthStructure;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger;
import art.arcane.adapt.content.adaptation.golemancy.GolemAnglerCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemAnimusBinding;
import art.arcane.adapt.content.adaptation.golemancy.GolemBoneFrame;
import art.arcane.adapt.content.adaptation.golemancy.GolemBreakerCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemCopperFrame;
import art.arcane.adapt.content.adaptation.golemancy.GolemFrostFrame;
import art.arcane.adapt.content.adaptation.golemancy.GolemGathererCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemGuardianCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemHarvesterCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemIronFrame;
import art.arcane.adapt.content.adaptation.golemancy.GolemLumberCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemMending;
import art.arcane.adapt.content.adaptation.golemancy.GolemNodeWeaving;
import art.arcane.adapt.content.adaptation.golemancy.GolemOverseer;
import art.arcane.adapt.content.adaptation.golemancy.GolemRancherCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemRunicAugments;
import art.arcane.adapt.content.adaptation.golemancy.GolemSorterCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemStasis;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.spatial.matter.SpatialMatter;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.world.WeatheringCopperState;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SkillGolemancy extends SimpleSkill<SkillGolemancy.Config> {
  private static final long CARE_CHECK_STALE_MILLIS = 5_000L;
  private static final String PRE_ATTACK_EVENT = "io.papermc.paper.event.player.PrePlayerAttackEntityEvent";
  private static final String RESOURCES_RELOADED_EVENT = "io.papermc.paper.event.server.ServerResourcesReloadedEvent";

  private final GolemBuildAttribution buildAttribution = new GolemBuildAttribution();
  private final GolemXpCooldowns<BuildCooldownKey> buildCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<WitnessCooldownKey> witnessCooldowns = new GolemXpCooldowns<>();
  private final RollingXpCap witnessCap = new RollingXpCap();
  private final RollingXpCap workCap = new RollingXpCap();
  private final GolemXpCooldowns<UUID> assistCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<UUID> tankCooldowns = new GolemXpCooldowns<>();
  private final ConcurrentHashMap<UUID, Long> careChecks = new ConcurrentHashMap<>();

  public SkillGolemancy() {
    super("golemancy", SkillPresentation.of(SkillMessages.GOLEMANCY_NAME, SkillMessages.GOLEMANCY_ICON, SkillMessages.GOLEMANCY_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_AQUA);
    setInterval(3620);
    setIcon(Material.CARVED_PUMPKIN);
    registerAdaptation(new GolemAnimusBinding());
    registerAdaptation(new GolemNodeWeaving());
    registerAdaptation(new GolemFrostFrame());
    registerAdaptation(new GolemCopperFrame());
    registerAdaptation(new GolemIronFrame());
    registerAdaptation(new GolemBoneFrame());
    registerAdaptation(new GolemGuardianCore());
    registerAdaptation(new GolemGathererCore());
    registerAdaptation(new GolemHarvesterCore());
    registerAdaptation(new GolemLumberCore());
    registerAdaptation(new GolemBreakerCore());
    registerAdaptation(new GolemRancherCore());
    registerAdaptation(new GolemSorterCore());
    registerAdaptation(new GolemAnglerCore());
    registerAdaptation(new GolemRunicAugments());
    registerAdaptation(new GolemOverseer());
    registerAdaptation(new GolemMending());
    registerAdaptation(new GolemStasis());
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
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FIREWORK_STAR)
        .key("challenge_golemancy_bound_5")
        .model(CustomModel.get(Material.FIREWORK_STAR, "advancement", "golemancy", "challenge_golemancy_bound_5"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_golemancy_bound_25")
            .model(CustomModel.get(Material.NETHER_STAR, "advancement", "golemancy", "challenge_golemancy_bound_25"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_golemancy_golem_kills_50")
        .model(CustomModel.get(Material.IRON_SWORD, "advancement", "golemancy", "challenge_golemancy_golem_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_golemancy_golem_kills_500")
            .model(CustomModel.get(Material.DIAMOND_SWORD, "advancement", "golemancy", "challenge_golemancy_golem_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.NETHERITE_SWORD)
                .key("challenge_golemancy_golem_kills_5000")
                .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "golemancy", "challenge_golemancy_golem_kills_5000"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SNOWBALL)
        .key("challenge_golemancy_first_bind_frost")
        .model(CustomModel.get(Material.SNOWBALL, "advancement", "golemancy", "challenge_golemancy_first_bind_frost"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COPPER_INGOT)
        .key("challenge_golemancy_first_bind_copper")
        .model(CustomModel.get(Material.COPPER_INGOT, "advancement", "golemancy", "challenge_golemancy_first_bind_copper"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_golemancy_first_bind_iron")
        .model(CustomModel.get(Material.IRON_INGOT, "advancement", "golemancy", "challenge_golemancy_first_bind_iron"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SHOVEL)
        .key("challenge_golemancy_tasks_1000")
        .model(CustomModel.get(Material.IRON_SHOVEL, "advancement", "golemancy", "challenge_golemancy_tasks_1000"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SHOVEL)
            .key("challenge_golemancy_tasks_25000")
            .model(CustomModel.get(Material.DIAMOND_SHOVEL, "advancement", "golemancy", "challenge_golemancy_tasks_25000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_golemancy_items_moved_10000")
        .model(CustomModel.get(Material.CHEST, "advancement", "golemancy", "challenge_golemancy_items_moved_10000"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_CHEST)
            .key("challenge_golemancy_items_moved_250000")
            .model(CustomModel.get(Material.ENDER_CHEST, "advancement", "golemancy", "challenge_golemancy_items_moved_250000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LODESTONE)
        .key("challenge_golemancy_infusions_10")
        .model(CustomModel.get(Material.LODESTONE, "advancement", "golemancy", "challenge_golemancy_infusions_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.END_CRYSTAL)
            .key("challenge_golemancy_infusions_100")
            .model(CustomModel.get(Material.END_CRYSTAL, "advancement", "golemancy", "challenge_golemancy_infusions_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_golemancy_built_10", "golemancy.built", 10, () -> getConfig().challengeBuiltReward);
    registerMilestone("challenge_golemancy_built_100", "golemancy.built", 100, () -> getConfig().challengeBuiltReward * 5);
    registerMilestone("challenge_golemancy_bound_5", "golemancy.bound", 5, () -> getConfig().challengeBoundReward);
    registerMilestone("challenge_golemancy_bound_25", "golemancy.bound", 25, () -> getConfig().challengeBoundReward * 5);
    registerMilestone("challenge_golemancy_golem_kills_50", "golemancy.golem.kills", 50, () -> getConfig().challengeGolemKillsReward);
    registerMilestone("challenge_golemancy_golem_kills_500", "golemancy.golem.kills", 500, () -> getConfig().challengeGolemKillsReward * 2);
    registerMilestone("challenge_golemancy_golem_kills_5000", "golemancy.golem.kills", 5000, () -> getConfig().challengeGolemKillsReward * 5);
    registerMilestone("challenge_golemancy_tasks_1000", "golemancy.tasks", 1000, () -> getConfig().challengeTasksReward);
    registerMilestone("challenge_golemancy_tasks_25000", "golemancy.tasks", 25000, () -> getConfig().challengeTasksReward * 5);
    registerMilestone("challenge_golemancy_items_moved_10000", "golemancy.items.moved", 10000, () -> getConfig().challengeItemsMovedReward);
    registerMilestone("challenge_golemancy_items_moved_250000", "golemancy.items.moved", 250000, () -> getConfig().challengeItemsMovedReward * 5);
    registerMilestone("challenge_golemancy_infusions_10", "golemancy.infusions", 10, () -> getConfig().challengeInfusionsReward);
    registerMilestone("challenge_golemancy_infusions_100", "golemancy.infusions", 100, () -> getConfig().challengeInfusionsReward * 5);
    GolemancyRuntime.setBindListener(this::golemBound);
    GolemancyRuntime.setPlinthHost(new GolemancyPlinthHost());
    GolemWorkXp.setSink(this::golemWorked);
    GolemRepairXp.setSink(this::payRepair);
    GolemWorkStats.setSink(new WorkStats());
    SpatialMatter.registerSliceType(new NodeMatter());
    SpatialMatter.registerSliceType(new PlinthSlotMatter());
  }

  @Override
  public void onTick() {
    long nowMillis = M.ms();
    buildAttribution.expire(System.nanoTime(), getConfig().buildAttributionWindowMillis);
    buildCooldowns.sweep(nowMillis, getConfig().buildXpCooldownMillis);
    witnessCooldowns.sweep(nowMillis, getConfig().witnessCooldownMillis);
    witnessCap.sweep(nowMillis);
    workCap.sweep(nowMillis);
    assistCooldowns.sweep(nowMillis, getConfig().assistCooldownMillis);
    tankCooldowns.sweep(nowMillis, getConfig().tankCooldownMillis);
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
    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    Entity damager = e.getDamager();
    double damage = e.getFinalDamage();
    if (HostileDamagePool.isPoolBody(victim.getType())
        && HostileDamagePool.isHostileDamager(damager)
        && !shouldReturnForWorld(victim.getWorld(), this)) {
      HostileDamagePool.add(victim, damage, maxHealthOf(victim));
      GolemancyRuntime.handle(victim).ifPresent(handle -> rewardTank(handle, victim, damager, damage));
    }
    if (!(victim instanceof Enemy)) {
      return;
    }
    Optional<GolemHandle> golem = GolemancyRuntime.handle(GolemXpRules.golemSource(damager));
    if (golem.isPresent() && !excludedMob(victim) && !shouldReturnForWorld(victim.getWorld(), this)) {
      rewardAssist(golem.get(), victim, damage);
    }
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
    Entity damager = lastDamage.getDamager();
    Optional<GolemHandle> owned = GolemancyRuntime.handle(GolemXpRules.golemSource(damager));
    Golem witnessed = owned.isPresent() ? null : GolemXpRules.witnessGolem(damager);
    if (owned.isEmpty() && witnessed == null) {
      rewardServantKill(victim, GolemXpRules.attacker(damager));
      return;
    }
    if (excludedMob(victim) || shouldReturnForWorld(victim.getWorld(), this)) {
      return;
    }
    if (owned.isPresent()) {
      rewardOwnedKill(owned.get(), victim);
      return;
    }
    rewardWitnesses(victim, witnessed);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    GolemancyRuntime.configure(golemSettings(newConfig));
    GolemancyRuntime.configureBrain(brainSettings(newConfig));
    GolemancyRuntime.configureReveal(revealSettings(newConfig));
    GolemancyRuntime.configureNodes(nodeSettings(newConfig));
    GolemancyRuntime.configurePlinths(plinthSettings(newConfig));
    GolemancyRuntime.configureWork(workSettings(newConfig));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    clampWorkXp(loadedConfig);
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    NodeTagPrompt prompt = new NodeTagPrompt();
    NodeInteractions nodes = new NodeInteractions(prompt);
    CrookInteractions crook = new CrookInteractions();
    List<Listener> listeners = new ArrayList<>(List.of(crook, nodes, prompt, new GuiCursor()));
    boolean paperAttacks = PaperCompat.hasClass(PRE_ATTACK_EVENT);
    listeners.add(paperAttacks ? new NodeInteractions.PaperAttacks(nodes) : new NodeInteractions.SpigotAttacks(nodes));
    listeners.add(paperAttacks ? new CrookInteractions.PaperAttacks(crook) : new CrookInteractions.SpigotAttacks(crook));
    if (PaperCompat.hasClass(RESOURCES_RELOADED_EVENT)) {
      listeners.add(new NodeItemTags.ReloadListener());
    }
    if (PaperCompat.hasClass("io.papermc.paper.world.WeatheringCopperState")) {
      listeners.add(new CopperCareListener());
    }
    return List.copyOf(listeners);
  }

  public static Material heldType(Player player, EquipmentSlot hand) {
    ItemStack held = player.getInventory().getItem(hand);
    return held == null ? Material.AIR : held.getType();
  }

  static GolemSettings golemSettings(Config config) {
    Map<GolemBody, Double> scales = new EnumMap<>(GolemBody.class);
    scales.put(GolemBody.FROST, config.bodyScaleFrost);
    scales.put(GolemBody.COPPER, config.bodyScaleCopper);
    scales.put(GolemBody.IRON, config.bodyScaleIron);
    scales.put(GolemBody.BONE, config.bodyScaleBone);
    Map<GolemBody, Integer> carry = new EnumMap<>(GolemBody.class);
    carry.put(GolemBody.FROST, config.bodyCarryFrost);
    carry.put(GolemBody.COPPER, config.bodyCarryCopper);
    carry.put(GolemBody.IRON, config.bodyCarryIron);
    Map<GolemBody, Set<GolemCoreType>> cores = new EnumMap<>(GolemBody.class);
    cores.put(GolemBody.FROST, GolemSettings.parseCores(config.allowedCoresFrost));
    cores.put(GolemBody.COPPER, GolemSettings.parseCores(config.allowedCoresCopper));
    cores.put(GolemBody.IRON, GolemSettings.parseCores(config.allowedCoresIron));
    return new GolemSettings(config.dormantInvulnerable, config.guardWhileOwnerOffline, config.serverMaxActiveGolems,
        config.maxBoundGolemsPerChunk, config.hardCapPerPlayer, scales, carry, cores, config.deathDropCoreChance, config.deathDropRuneChance);
  }

  static GolemBrainSettings brainSettings(Config config) {
    return new GolemBrainSettings(config.brainIntervalTicks, config.followTeleportDistance, config.leashRadius,
        config.idleReturnSeconds, config.copperRepathTicks);
  }

  static RevealSettings revealSettings(Config config) {
    return new RevealSettings(config.revealRange, config.labelRange, config.revealPollTicks);
  }

  static GolemWorkSettings workSettings(Config config) {
    return new GolemWorkSettings(config.taskLeaseSeconds, config.baseWorkRadius, config.stuckTimeoutSeconds, config.stuckReleasesBeforePause,
        config.stuckPauseSeconds);
  }

  static void clampWorkXp(Config config) {
    config.workXpHourlyCap = Double.isFinite(config.workXpHourlyCap) ? Math.max(0D, config.workXpHourlyCap) : 0D;
    config.workXpOwnerRadius = Double.isFinite(config.workXpOwnerRadius) ? Math.max(0D, config.workXpOwnerRadius) : 0D;
  }

  static double grantWorkXp(RollingXpCap cap, UUID owner, double xp, double distanceSquared, Config config, long nowMillis) {
    double radius = config.workXpOwnerRadius;
    if (!(distanceSquared <= radius * radius)) {
      return 0D;
    }
    return cap.claim(owner, xp, config.workXpHourlyCap, nowMillis);
  }

  static double bindXp(Config config, GolemBody body) {
    return switch (body) {
      case FROST -> config.bindFrostXp;
      case COPPER -> config.bindCopperXp;
      case IRON -> config.bindIronXp;
      case BONE -> 0D;
    };
  }

  static double assistBodyMultiplier(Config config, GolemBody body) {
    return switch (body) {
      case FROST -> config.assistBodyMultiplierFrost;
      case COPPER -> config.assistBodyMultiplierCopper;
      case IRON -> config.assistBodyMultiplierIron;
      case BONE -> config.assistBodyMultiplierBone;
    };
  }

  static NodeSettings nodeSettings(Config config) {
    return new NodeSettings(config.maxNodesPerChunk, config.nodeScanIntervalTicks, config.blocksPerNodeScan,
        config.maxOpenTasksPerNode, NodeVisibility.parse(config.nodeVisibility));
  }

  static PlinthSettings plinthSettings(Config config) {
    return new PlinthSettings(PlinthStructure.pedestals(config.pedestalBlocks, Material::isBlock, Adapt::warn), InfusionRecipes.bukkit(config.infusionRecipes));
  }

  static double infusionXp(Config config, NamespacedKey result) {
    String key = result.getKey();
    if (key.equals("masterwork_heart")) {
      return config.infuseMasterworkHeartXp;
    }
    String[] parts = key.split("/", -1);
    if (parts.length != 3 || !parts[0].equals("rune") || GolemRuneType.fromId(parts[1]).isEmpty()) {
      return 0D;
    }
    return switch (parts[2]) {
      case "1" -> config.infuseRuneTier1Xp;
      case "2" -> config.infuseRuneTier2Xp;
      case "3" -> config.infuseRuneTier3Xp;
      default -> 0D;
    };
  }

  static String infusionRewardKey(NamespacedKey result) {
    return "golemancy:craft:" + result.getKey();
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

  private void settleRepair(Player player, LivingEntity golem, double before) {
    GolemRepairXp.repaired(player, golem, golem.getHealth() - before);
  }

  void payRepair(Player player, LivingEntity golem, double healed) {
    double xp = GolemXpRules.repairXp(HostileDamagePool.consume(golem, healed), getConfig().repairXpPerHealth);
    if (xp <= 0D) {
      return;
    }
    Location at = golem.getLocation();
    shouldReturnForPlayer(player, () -> xp(player, at, xp, GolemRepairXp.REWARD_KEY));
    repairFx(at);
  }

  void repairFx(Location at) {
    fx(at.clone().add(0D, 1.5D, 0D), FxPriority.AMBIENT)
        .burst(Particles.VILLAGER_HAPPY, 5, 0.6D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.5F);
  }

  private void rewardWitnesses(LivingEntity victim, Golem golem) {
    if ((J.isFoliaThreading() && !J.isOwnedByCurrentRegion(golem))
        || GolemState.has(golem)
        || MinionIdentity.isMinion(golem)) {
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

  private void golemBound(Player owner, GolemHandle handle) {
    Mob golem = handle.entity();
    if (shouldReturnForWorld(golem.getWorld(), this)) {
      return;
    }
    GolemBody body = handle.body();
    boolean firstBindOfGolem = !handle.state().previouslyBound();
    Location at = golem.getLocation();
    J.runEntity(owner, () -> shouldReturnForPlayer(owner, () -> rewardBind(owner, body, firstBindOfGolem, at)));
  }

  private void golemWorked(GolemHandle handle, GolemCoreType core, double xp) {
    Mob golem = handle.entity();
    if (shouldReturnForWorld(golem.getWorld(), this)) {
      return;
    }
    Location at = golem.getLocation();
    String rewardKey = GolemWorkXp.rewardKey(core);
    rewardOwner(handle.owner(), owner -> payWork(owner, at, xp, rewardKey));
  }

  private void payWork(Player owner, Location at, double xp, String rewardKey) {
    Location ownerAt = owner.getLocation();
    World world = ownerAt.getWorld();
    double distanceSquared = world != null && world.equals(at.getWorld()) ? ownerAt.distanceSquared(at) : Double.POSITIVE_INFINITY;
    double granted = grantWorkXp(workCap, owner.getUniqueId(), xp, distanceSquared, getConfig(), M.ms());
    if (granted > 0D) {
      xp(owner, at, granted, rewardKey);
    }
  }

  private void workerStat(GolemHandle handle, Consumer<Player> record) {
    if (shouldReturnForWorld(handle.entity().getWorld(), this)) {
      return;
    }
    rewardOwner(handle.owner(), record);
  }

  private void rewardBind(Player owner, GolemBody body, boolean firstBindOfGolem, Location at) {
    if (firstBindOfGolem) {
      addStat(owner, "golemancy.bound", 1);
      xp(owner, at, bindXp(getConfig(), body), "golemancy:bind");
    }
    if (body != GolemBody.BONE && grantOnce(owner, "challenge_golemancy_first_bind_" + body.id())) {
      xp(owner, at, getConfig().firstBindXp, "golemancy:first_bind:" + body.id());
    }
  }

  private void rewardInfusion(Player player, NamespacedKey result, Location at) {
    addStat(player, "golemancy.infusions", 1);
    double xp = infusionXp(getConfig(), result);
    if (xp > 0D) {
      xp(player, at, xp, infusionRewardKey(result));
    }
  }

  private void rewardAssist(GolemHandle handle, LivingEntity victim, double damage) {
    double dealt = GolemXpRules.effectiveDamage(damage, victim.getHealth());
    if (dealt <= 0D) {
      return;
    }
    Mob golem = handle.entity();
    J.runEntity(golem, () -> handle.addDamage(dealt));
    Config config = getConfig();
    double xp = GolemXpRules.assistXp(dealt, config.assistDamageXpMultiplier, assistBodyMultiplier(config, handle.body()),
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier);
    Location at = combatLocation(golem, victim);
    rewardOwner(handle.owner(), owner -> payAssist(owner, at, dealt, xp));
  }

  private void payAssist(Player owner, Location at, double dealt, double xp) {
    addStat(owner, "golemancy.golem.damage", dealt);
    if (xp <= 0D || !assistCooldowns.tryClaim(owner.getUniqueId(), M.ms(), getConfig().assistCooldownMillis)) {
      return;
    }
    xp(owner, at, xp * distanceFactor(owner, at), "golemancy:assist");
  }

  private void rewardOwnedKill(GolemHandle handle, LivingEntity victim) {
    Mob golem = handle.entity();
    J.runEntity(golem, () -> handle.addKills(1));
    Config config = getConfig();
    double killValue = GolemXpRules.killValue(config.golemKillXpBase, maxHealthOf(victim), config.golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.ownedKillXp(killValue, GolemXpRules.inActiveRaid(victim), config.raidXpMultiplier,
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier);
    String rewardKey = "golemancy:kill:" + victim.getType().name().toLowerCase(Locale.ROOT);
    Location at = combatLocation(golem, victim);
    rewardOwner(handle.owner(), owner -> payKill(owner, at, xp, rewardKey));
  }

  private void rewardServantKill(LivingEntity victim, Entity attacker) {
    UUID ownerId = TragoulSkeletalServant.MINION_KIND.matches(attacker) ? TragoulSkeletalServant.MINION_KIND.ownerOf(attacker) : null;
    double share = ownerId == null ? 0D : ServantFrame.frame().map(frame -> frame.share(ownerId)).orElse(0D);
    if (share <= 0D || excludedMob(victim) || shouldReturnForWorld(victim.getWorld(), this)) {
      return;
    }
    Config config = getConfig();
    double killValue = GolemXpRules.killValue(config.golemKillXpBase, maxHealthOf(victim), config.golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.servantShareXp(share, GolemXpRules.ownedKillXp(killValue, GolemXpRules.inActiveRaid(victim), config.raidXpMultiplier,
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier));
    if (xp <= 0D) {
      return;
    }
    String rewardKey = GolemXpRules.servantRewardKey(victim.getType());
    Location at = victim.getLocation();
    rewardOwner(ownerId, owner -> xp(owner, at, xp * distanceFactor(owner, at), rewardKey));
  }

  private void payKill(Player owner, Location at, double xp, String rewardKey) {
    addStat(owner, "golemancy.golem.kills", 1);
    xp(owner, at, xp * distanceFactor(owner, at), rewardKey);
  }

  private void rewardTank(GolemHandle handle, LivingEntity golem, Entity damager, double damage) {
    Entity attacker = GolemXpRules.attacker(damager);
    if ((J.isFoliaThreading() && !J.isOwnedByCurrentRegion(attacker)) || excludedMob(attacker)) {
      return;
    }
    double taken = GolemXpRules.effectiveDamage(damage, golem.getHealth());
    double xp = GolemXpRules.tankXp(taken, getConfig().tankXpMultiplier, SpawnerMobTag.isTagged(attacker), getConfig().spawnerMobXpMultiplier);
    if (xp <= 0D) {
      return;
    }
    Location at = golem.getLocation();
    rewardOwner(handle.owner(), owner -> payTank(owner, at, xp));
  }

  private void payTank(Player owner, Location at, double xp) {
    if (!tankCooldowns.tryClaim(owner.getUniqueId(), M.ms(), getConfig().tankCooldownMillis)) {
      return;
    }
    xp(owner, at, xp * distanceFactor(owner, at), "golemancy:tank");
  }

  private void rewardOwner(UUID ownerId, Consumer<Player> reward) {
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null) {
      return;
    }
    J.runEntity(owner, () -> shouldReturnForPlayer(owner, () -> reward.accept(owner)));
  }

  private double distanceFactor(Player owner, Location at) {
    Location ownerAt = owner.getLocation();
    World world = ownerAt.getWorld();
    double distanceSquared = world != null && world.equals(at.getWorld()) ? ownerAt.distanceSquared(at) : Double.POSITIVE_INFINITY;
    return GolemXpRules.ownerDistanceFactor(distanceSquared, getConfig().ownerProximityRadius, getConfig().distantOwnerXpMultiplier);
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

  private static boolean excludedMob(Entity entity) {
    return MinionIdentity.isMinion(entity) || ExcavationGraveDigger.isGraveMob(entity);
  }

  private static Location combatLocation(Entity golem, Entity victim) {
    return J.isOwnedByCurrentRegion(golem) ? golem.getLocation() : victim.getLocation();
  }

  private static double maxHealthOf(LivingEntity entity) {
    return GolemVitals.maxHealth(entity);
  }

  private record BuildCooldownKey(UUID player, SpawnReason reason) {
  }

  private final class WorkStats implements GolemWorkStats.Sink {
    @Override
    public void completed(GolemHandle handle) {
      workerStat(handle, owner -> addStat(owner, "golemancy.tasks", 1));
    }

    @Override
    public void moved(GolemHandle handle, long items) {
      workerStat(handle, owner -> addStat(owner, "golemancy.items.moved", items));
    }
  }

  private record WitnessCooldownKey(UUID golem, UUID player) {
  }

  private final class GolemancyPlinthHost implements PlinthHost {
    @Override
    public int level(Player player, String adaptationId) {
      return adaptation(adaptationId).map(adaptation -> adaptation.getActiveLevel(player)).orElse(0);
    }

    @Override
    public boolean canStore(Player player, String adaptationId, Location at) {
      return adaptation(adaptationId).map(adaptation -> adaptation.canAccessChest(player, at)).orElse(false);
    }

    @Override
    public boolean canInfuse(Player player, String adaptationId, Location at) {
      return adaptation(adaptationId).map(adaptation -> adaptation.canInteract(player, at) && adaptation.canBlockPlace(player, at)).orElse(false);
    }

    @Override
    public Optional<PlinthCharge> chargeLevels(Player player, String adaptationId, int levels) {
      return adaptation(adaptationId).flatMap(adaptation -> LevelCharge.take(adaptation, player, levels));
    }

    @Override
    public FxEmitter fx(Location at, FxPriority priority) {
      return SkillGolemancy.this.fx(at, priority);
    }

    @Override
    public void infused(Player player, Infusion infusion, Location at) {
      if (shouldReturnForWorld(at.getWorld(), SkillGolemancy.this)) {
        return;
      }
      J.runEntity(player, () -> shouldReturnForPlayer(player, () -> rewardInfusion(player, infusion.result(), at)));
    }

    private Optional<Adaptation<?>> adaptation(String id) {
      for (Adaptation<?> adaptation : getAdaptations()) {
        if (adaptation.getName().equals(id)) {
          return Optional.of(adaptation);
        }
      }
      return Optional.empty();
    }
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
    @ConfigDoc(value = "Skill XP per point of health restored when repairing an iron golem, or healing a bound golem with Golem Mending, that hostile mobs damaged.", impact = "Higher values pay more for repairs; damage from players never counts.")
    double repairXpPerHealth = 2.0;
    @ConfigDoc(value = "Skill XP paid for scraping one oxidation stage off a copper golem with an axe.", impact = "Higher values reward copper golem care more.")
    double copperScrapeXp = 60;
    @ConfigDoc(value = "Skill XP paid the first time you wax a copper golem with honeycomb.", impact = "Higher values reward waxing more; each golem pays once.")
    double copperWaxXp = 30;
    @ConfigDoc(value = "Multiplier applied to Golemancy XP from mobs that came from a spawner or trial spawner.", impact = "Lower values make spawner farms pay less; 1.0 pays the same as natural mobs.")
    double spawnerMobXpMultiplier = 0.3;
    @ConfigDoc(value = "Skill XP paid by the golem building challenges.", impact = "Higher values pay more; the 100 builds challenge pays five times this value.")
    double challengeBuiltReward = 500;
    @ConfigDoc(value = "Skill XP paid the first time a frost golem is bound.", impact = "Higher values reward binding frost golems more; a golem that was bound before pays nothing.")
    double bindFrostXp = 100;
    @ConfigDoc(value = "Skill XP paid the first time a copper golem is bound.", impact = "Higher values reward binding copper golems more; a golem that was bound before pays nothing.")
    double bindCopperXp = 220;
    @ConfigDoc(value = "Skill XP paid the first time an iron golem is bound.", impact = "Higher values reward binding iron golems more; a golem that was bound before pays nothing.")
    double bindIronXp = 450;
    @ConfigDoc(value = "Skill XP paid with each first-bind challenge, the first time you bind a frost, copper or iron golem.", impact = "Higher values make each first bind worth more; each challenge pays once per player.")
    double firstBindXp = 600;
    @ConfigDoc(value = "Skill XP per point of damage your bound golems deal to hostile mobs, before the body multiplier. Damage beyond the victim's remaining health does not count.", impact = "Higher values pay more for golem combat.")
    double assistDamageXpMultiplier = 3.0;
    @ConfigDoc(value = "Body multiplier for damage XP from bound frost golems, including their snowballs.", impact = "Higher values pay more for frost golem damage.")
    double assistBodyMultiplierFrost = 2.0;
    @ConfigDoc(value = "Body multiplier for damage XP from bound copper golems.", impact = "Higher values pay more for copper golem damage.")
    double assistBodyMultiplierCopper = 1.0;
    @ConfigDoc(value = "Body multiplier for damage XP from bound iron golems.", impact = "Higher values pay more for iron golem damage.")
    double assistBodyMultiplierIron = 1.0;
    @ConfigDoc(value = "Body multiplier for damage XP from bound bone golems.", impact = "Higher values pay more for bone golem damage.")
    double assistBodyMultiplierBone = 0.35;
    @ConfigDoc(value = "Milliseconds before one player earns damage XP from their bound golems again.", impact = "Higher values limit damage XP from many golems or fast hits; damage still counts toward golemancy.golem.damage.")
    long assistCooldownMillis = 1500;
    @ConfigDoc(value = "Multiplier on kill XP when a bound golem kills a raider that belongs to an active raid.", impact = "Higher values reward defending villages from raids more; 1.0 pays the same as other kills.")
    double raidXpMultiplier = 1.5;
    @ConfigDoc(value = "Blocks from a bound golem within which its owner earns full damage, kill and tanking XP.", impact = "Higher values let owners stay farther from their golems without losing XP.")
    double ownerProximityRadius = 48.0;
    @ConfigDoc(value = "Multiplier on damage, kill and tanking XP when the owner is farther than ownerProximityRadius from the golem or in another world. Clamped to 0-1.", impact = "Lower values pay less to owners who leave their golems fighting alone; 0 pays nothing.")
    double distantOwnerXpMultiplier = 0.25;
    @ConfigDoc(value = "Skill XP per point of damage your bound golems take from hostile mobs. Damage beyond the golem's remaining health does not count.", impact = "Higher values pay more for golems that soak up hits.")
    double tankXpMultiplier = 1.5;
    @ConfigDoc(value = "Milliseconds before one player earns tanking XP from their bound golems again.", impact = "Higher values limit tanking XP from many golems or fast hits.")
    long tankCooldownMillis = 2000;
    @ConfigDoc(value = "Skill XP paid by the golem binding challenges.", impact = "Higher values pay more; the 25 binds challenge pays five times this value.")
    double challengeBoundReward = 500;
    @ConfigDoc(value = "Skill XP paid by the bound golem kill challenges.", impact = "Higher values pay more; the 500 kills challenge pays twice and the 5,000 kills challenge five times this value.")
    double challengeGolemKillsReward = 500;
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
    @ConfigDoc(value = "Items a bound frost golem can carry at once.", impact = "Higher values let working frost golems move more items per trip.")
    int bodyCarryFrost = 8;
    @ConfigDoc(value = "Items a bound copper golem can carry at once before Copper Frame raises it.", impact = "Higher values let working copper golems move more items per trip.")
    int bodyCarryCopper = 16;
    @ConfigDoc(value = "Items a bound iron golem can carry at once.", impact = "Higher values let working iron golems move more items per trip.")
    int bodyCarryIron = 64;
    @ConfigDoc(value = "Cores a frost golem can be bound with.", impact = "Remove a core to stop frost golems taking that role.")
    List<String> allowedCoresFrost = new ArrayList<>(List.of("BLANK", "GUARDIAN", "GATHERER", "HARVESTER"));
    @ConfigDoc(value = "Cores a copper golem can be bound with.", impact = "Remove a core to stop copper golems taking that role.")
    List<String> allowedCoresCopper = new ArrayList<>(List.of("BLANK", "GATHERER", "HARVESTER", "LUMBER", "RANCHER", "SORTER", "ANGLER"));
    @ConfigDoc(value = "Cores an iron golem can be bound with.", impact = "Remove a core to stop iron golems taking that role.")
    List<String> allowedCoresIron = new ArrayList<>(List.of("BLANK", "GUARDIAN", "GATHERER", "LUMBER", "BREAKER", "RANCHER", "ANGLER"));
    @ConfigDoc(value = "Chance that each core drops when a bound golem dies without leaving a Shattered Core.", impact = "Higher values return more cores on death; 1.0 always drops them.")
    double deathDropCoreChance = 0.5;
    @ConfigDoc(value = "Chance that each rune drops when a bound golem dies without leaving a Shattered Core.", impact = "Higher values return more runes on death; 1.0 always drops them.")
    double deathDropRuneChance = 0.5;
    @ConfigDoc(value = "Ticks between bound golem decisions, clamped to 2-20.", impact = "Lower values make golems react faster but cost more server time.")
    int brainIntervalTicks = 5;
    @ConfigDoc(value = "Blocks from its owner, in the same world, at which a following golem teleports to them instead of walking. The minimum is 8.", impact = "Lower values teleport golems sooner; higher values make them walk farther first.")
    double followTeleportDistance = 32;
    @ConfigDoc(value = "Blocks a fighting golem may stray from its owner, home, or stay spot before it drops the fight and walks back. It is also the farthest a linked home chest may be from the golem's home. The minimum is 8.", impact = "Higher values let golems chase enemies farther.")
    double leashRadius = 32;
    @ConfigDoc(value = "Seconds a working golem stands idle away from home before it walks back.", impact = "Lower values send idle golems home sooner; 0 sends them home at once.")
    int idleReturnSeconds = 8;
    @ConfigDoc(value = "Ticks between path refreshes for a moving copper golem, clamped to 1-20.", impact = "Lower values keep copper golems on course; above 10, their own wandering can pull them off course.")
    int copperRepathTicks = 10;
    @ConfigDoc(value = "Blocks around you within which your golems show their status labels and channel glow, your skeletal servants glow with Bone Frame, and your nodes appear while you hold the Crook, a Node, a Core or a Dormant Golem.", impact = "Higher values reveal golems and nodes farther away; lower values do less work on busy servers.")
    double revealRange = 32.0;
    @ConfigDoc(value = "Blocks within which revealed golem and node labels are drawn on screen.", impact = "Higher values let you read labels from farther away.")
    double labelRange = 8.0;
    @ConfigDoc(value = "Ticks between checks that reveal or hide golems and nodes as you move while holding the Crook, a Node, a Core or a Dormant Golem.", impact = "Lower values react to movement faster; holding none of them costs nothing.")
    int revealPollTicks = 10;
    @ConfigDoc(value = "Most nodes allowed in one chunk, counting every player's nodes.", impact = "Lower values spread nodes out; placing a node fails in a chunk that is already full.")
    int maxNodesPerChunk = 16;
    @ConfigDoc(value = "Ticks between checks of each node, clamped to 5-1200. Each check confirms the node's block is still there.", impact = "Lower values notice broken node blocks sooner but cost more server time.")
    int nodeScanIntervalTicks = 40;
    @ConfigDoc(value = "Most blocks of a node's work area looked at in one check, clamped to 1-4096.", impact = "Higher values let nodes find work faster in large areas but cost more server time.")
    int blocksPerNodeScan = 64;
    @ConfigDoc(value = "Most unfinished jobs one node can hand out to golems at once, clamped to 1-256.", impact = "Higher values let more golems work one node at the same time.")
    int maxOpenTasksPerNode = 16;
    @ConfigDoc(value = "Who can see nodes while holding the Crook, a Node, a Core or a Dormant Golem: OWNER shows each player only their own nodes, ANY_GOLEMANCER also shows every node to players with Node Weaving.", impact = "Set to ANY_GOLEMANCER to let golemancers see each other's nodes.")
    String nodeVisibility = "OWNER";
    @ConfigDoc(value = "Seconds a golem keeps its claim on a node job without making progress before another golem may take it, clamped to 5-600.", impact = "Lower values let other golems take over stalled jobs sooner; higher values tolerate slow walks to far jobs.")
    int taskLeaseSeconds = 20;
    @ConfigDoc(value = "Blocks around its home within which a working golem takes node jobs, finds Deposit Nodes and defends Guard Nodes, clamped to 4-128.", impact = "Higher values let golems work, deliver and guard farther from home.")
    double baseWorkRadius = 24;
    @ConfigDoc(value = "Seconds a golem walking to a job or a container may go without getting half a block closer before it gives up and skips that job or container for 10 seconds, clamped to 1-60.", impact = "Lower values give up on blocked paths sooner; higher values keep trying longer.")
    int stuckTimeoutSeconds = 4;
    @ConfigDoc(value = "Jobs or containers in a row a golem may give up because it got stuck before it rests for stuckPauseSeconds, clamped to 1-100.", impact = "Lower values rest trapped golems sooner; higher values keep them trying longer.")
    int stuckReleasesBeforePause = 3;
    @ConfigDoc(value = "Seconds a trapped golem rests without looking for jobs or containers after giving up stuckReleasesBeforePause in a row, clamped to 0-600.", impact = "Higher values save server time on trapped golems; 0 never rests them.")
    int stuckPauseSeconds = 15;
    @ConfigDoc(value = "Most work XP one player can earn from their golems' finished jobs in any rolling 60 minutes. Values below 0 count as 0.", impact = "Higher values allow more passive XP from golem labor per hour; 0 pays no work XP.")
    double workXpHourlyCap = 1200;
    @ConfigDoc(value = "Blocks from a working golem within which its owner earns work XP for its finished jobs. Owners farther away or in another world earn none. Values below 0 count as 0.", impact = "Higher values let owners earn work XP while farther from their golems.")
    double workXpOwnerRadius = 96;
    @ConfigDoc(value = "Skill XP paid by the golem job challenges.", impact = "Higher values pay more; the 25,000 jobs challenge pays five times this value.")
    double challengeTasksReward = 500;
    @ConfigDoc(value = "Skill XP paid by the golem item hauling challenges.", impact = "Higher values pay more; the 250,000 items challenge pays five times this value.")
    double challengeItemsMovedReward = 500;
    @ConfigDoc(value = "Blocks that work as Animus Plinth pedestals, two blocks north, east, south and west of a lodestone at the same height.", impact = "Add or remove block names to change which blocks form a plinth; unknown names and the lodestone are ignored with a warning.")
    List<String> pedestalBlocks = new ArrayList<>(PlinthStructure.DEFAULT_PEDESTALS);
    @ConfigDoc(value = "Infusions an Animus Plinth can perform, one table per infusion with result, center, pedestals, essenceLevels, durationTicks, unlockAdaptation, unlockLevel and enabled.", impact = "Edit a table to change an infusion, set enabled to false to turn it off, or add a table to create a new one; invalid tables are skipped with a warning.")
    Map<String, InfusionRecipe> infusionRecipes = InfusionRecipe.defaults();
    @ConfigDoc(value = "Skill XP paid for each tier 1 rune infused at an Animus Plinth.", impact = "Higher values reward tier 1 infusions more; 0 pays nothing.")
    double infuseRuneTier1Xp = 60;
    @ConfigDoc(value = "Skill XP paid for each tier 2 rune infused at an Animus Plinth.", impact = "Higher values reward tier 2 infusions more; 0 pays nothing.")
    double infuseRuneTier2Xp = 180;
    @ConfigDoc(value = "Skill XP paid for each tier 3 rune infused at an Animus Plinth.", impact = "Higher values reward tier 3 infusions more; 0 pays nothing.")
    double infuseRuneTier3Xp = 450;
    @ConfigDoc(value = "Skill XP paid for each Masterwork Heart infused at an Animus Plinth.", impact = "Higher values reward Masterwork Heart infusions more; 0 pays nothing.")
    double infuseMasterworkHeartXp = 1500;
    @ConfigDoc(value = "Skill XP paid by the infusion challenges.", impact = "Higher values pay more; the 100 infusions challenge pays five times this value.")
    double challengeInfusionsReward = 500;
  }
}
