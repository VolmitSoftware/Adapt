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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemNames;
import art.arcane.adapt.api.golem.GolemRepairXp;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemWorkSettings;
import art.arcane.adapt.api.golem.GolemWorkStats;
import art.arcane.adapt.api.golem.GolemWorkXp;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.HostileDamagePool;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.gui.CrookInteractions;
import art.arcane.adapt.api.golem.gui.NodeAreaSelector;
import art.arcane.adapt.api.golem.gui.NodeInteractions;
import art.arcane.adapt.api.golem.gui.NodeTagPrompt;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeSettings;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.api.golem.plinth.Infusion;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.InfusionRecipes;
import art.arcane.adapt.api.golem.plinth.LevelCharge;
import art.arcane.adapt.api.golem.plinth.PlinthCharge;
import art.arcane.adapt.api.golem.plinth.PlinthHost;
import art.arcane.adapt.api.golem.plinth.PlinthSettings;
import art.arcane.adapt.api.golem.plinth.PlinthStructure;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.skill.AdaptationGroup;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CombatXp;
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
import art.arcane.adapt.content.adaptation.golemancy.GolemLastJobs;
import art.arcane.adapt.content.adaptation.golemancy.GolemLumberCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemMasterwork;
import art.arcane.adapt.content.adaptation.golemancy.GolemMending;
import art.arcane.adapt.content.adaptation.golemancy.GolemNodeWeaving;
import art.arcane.adapt.content.adaptation.golemancy.GolemOverseer;
import art.arcane.adapt.content.adaptation.golemancy.GolemRancherCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemRunicAugments;
import art.arcane.adapt.content.adaptation.golemancy.GolemSorterCore;
import art.arcane.adapt.content.adaptation.golemancy.GolemStasis;
import art.arcane.adapt.content.adaptation.golemancy.GolemTaskDoneEvent;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyBoon;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyFightStrike;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyFuel;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyState;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyTrigger;
import art.arcane.adapt.content.adaptation.golemancy.blocks.GolemancyWorkStrike;
import art.arcane.adapt.content.adaptation.golemancy.upgraded.GrandOverseer;
import art.arcane.adapt.content.adaptation.golemancy.upgraded.Forgemaster;
import art.arcane.adapt.content.adaptation.golemancy.upgraded.Runeforged;
import art.arcane.adapt.content.skill.golemancy.CopperCare;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;
import art.arcane.adapt.content.skill.golemancy.GolemXpRules;
import art.arcane.adapt.content.skill.golemancy.RollingXpCap;
import art.arcane.adapt.content.skill.golemancy.SpawnerMobTag;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent.Reason;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class SkillGolemancy extends SimpleSkill<SkillGolemancy.Config> {
  private static final long CARE_CHECK_STALE_MILLIS = 5_000L;
  private static final Id CARVED_PUMPKIN = Id.minecraft("carved_pumpkin");
  private static final Id JACK_O_LANTERN = Id.minecraft("jack_o_lantern");
  private static final Id IRON_GOLEM = Id.minecraft("iron_golem");
  private static final Id IRON_INGOT = Id.minecraft("iron_ingot");
  private static final Id HONEYCOMB = Id.minecraft("honeycomb");
  private static final Id AXES = Id.minecraft("axes");

  private final GolemBuildAttribution buildAttribution = new GolemBuildAttribution();
  private final GolemXpCooldowns<BuildCooldownKey> buildCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<WitnessCooldownKey> witnessCooldowns = new GolemXpCooldowns<>();
  private final RollingXpCap witnessCap = new RollingXpCap();
  private final RollingXpCap workCap = new RollingXpCap();
  private final RollingXpCap bindCap = new RollingXpCap();
  private final GolemXpCooldowns<UUID> assistCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<UUID> tankCooldowns = new GolemXpCooldowns<>();
  private final GolemXpCooldowns<BindCooldownKey> bindCooldowns = new GolemXpCooldowns<>();
  private final ConcurrentHashMap<UUID, Long> careChecks = new ConcurrentHashMap<>();

  static final List<AdaptationGroup> GROUPS = List.of(
      AdaptationGroup.of(GroupMessages.GOLEMANCY_BINDING, "golem-animus-binding", "golem-node-weaving"),
      AdaptationGroup.of(GroupMessages.GOLEMANCY_FRAMES, "golem-frost-frame", "golem-copper-frame", "golem-iron-frame",
          "golem-bone-frame"),
      AdaptationGroup.of(GroupMessages.GOLEMANCY_CORES, "golem-guardian-core", "golem-gatherer-core",
          "golem-harvester-core", "golem-lumber-core", "golem-breaker-core", "golem-rancher-core", "golem-sorter-core",
          "golem-angler-core"),
      AdaptationGroup.of(GroupMessages.GOLEMANCY_COMMAND, "golem-runic-augments", "golem-masterwork", "golem-overseer",
          "golem-mending", "golem-stasis")
  );

  public SkillGolemancy() {
    super("golemancy", SkillPresentation.of(SkillMessages.GOLEMANCY_NAME, SkillMessages.GOLEMANCY_ICON, SkillMessages.GOLEMANCY_DESCRIPTION,
        SkillMessages.GOLEMANCY_QUIP, SkillMessages.GOLEMANCY_UNLOCK_QUIP_1, SkillMessages.GOLEMANCY_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setColor(C.DARK_AQUA);
    setInterval(3620);
    setIcon(Id.minecraft("carved_pumpkin"));
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
    registerAdaptation(new GolemMasterwork());
    registerUpgrades(new GrandOverseer(), new Forgemaster(), new Runeforged());
    registerBlocks(new GolemancyTrigger(), new GolemancyFightStrike(), new GolemancyWorkStrike(), new GolemancyBoon(),
        new GolemancyFuel(), new GolemancyState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("carved_pumpkin"))
        .key("challenge_golemancy_built_10")
        .model(CustomModel.get(Id.minecraft("carved_pumpkin"), "advancement", "golemancy", "challenge_golemancy_built_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("iron_block"))
            .key("challenge_golemancy_built_100")
            .model(CustomModel.get(Id.minecraft("iron_block"), "advancement", "golemancy", "challenge_golemancy_built_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("firework_star"))
        .key("challenge_golemancy_bound_5")
        .model(CustomModel.get(Id.minecraft("firework_star"), "advancement", "golemancy", "challenge_golemancy_bound_5"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_golemancy_bound_25")
            .model(CustomModel.get(Id.minecraft("nether_star"), "advancement", "golemancy", "challenge_golemancy_bound_25"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_golemancy_golem_kills_50")
        .model(CustomModel.get(Id.minecraft("iron_sword"), "advancement", "golemancy", "challenge_golemancy_golem_kills_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_golemancy_golem_kills_500")
            .model(CustomModel.get(Id.minecraft("diamond_sword"), "advancement", "golemancy", "challenge_golemancy_golem_kills_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("netherite_sword"))
                .key("challenge_golemancy_golem_kills_5000")
                .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "golemancy", "challenge_golemancy_golem_kills_5000"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("snowball"))
        .key("challenge_golemancy_first_bind_frost")
        .model(CustomModel.get(Id.minecraft("snowball"), "advancement", "golemancy", "challenge_golemancy_first_bind_frost"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("copper_ingot"))
        .key("challenge_golemancy_first_bind_copper")
        .model(CustomModel.get(Id.minecraft("copper_ingot"), "advancement", "golemancy", "challenge_golemancy_first_bind_copper"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_golemancy_first_bind_iron")
        .model(CustomModel.get(Id.minecraft("iron_ingot"), "advancement", "golemancy", "challenge_golemancy_first_bind_iron"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_shovel"))
        .key("challenge_golemancy_tasks_1000")
        .model(CustomModel.get(Id.minecraft("iron_shovel"), "advancement", "golemancy", "challenge_golemancy_tasks_1000"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_shovel"))
            .key("challenge_golemancy_tasks_25000")
            .model(CustomModel.get(Id.minecraft("diamond_shovel"), "advancement", "golemancy", "challenge_golemancy_tasks_25000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_golemancy_items_moved_10000")
        .model(CustomModel.get(Id.minecraft("chest"), "advancement", "golemancy", "challenge_golemancy_items_moved_10000"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_chest"))
            .key("challenge_golemancy_items_moved_250000")
            .model(CustomModel.get(Id.minecraft("ender_chest"), "advancement", "golemancy", "challenge_golemancy_items_moved_250000"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lodestone"))
        .key("challenge_golemancy_infusions_10")
        .model(CustomModel.get(Id.minecraft("lodestone"), "advancement", "golemancy", "challenge_golemancy_infusions_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("end_crystal"))
            .key("challenge_golemancy_infusions_100")
            .model(CustomModel.get(Id.minecraft("end_crystal"), "advancement", "golemancy", "challenge_golemancy_infusions_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_golemancy_built_10", "golemancy.built", 10, () -> getConfig().challengeBuiltReward);
    registerMilestone("challenge_golemancy_built_100", "golemancy.built", 100, () -> getConfig().challengeBuiltReward * 3);
    registerMilestone("challenge_golemancy_bound_5", "golemancy.bound", 5, () -> getConfig().challengeBoundReward);
    registerMilestone("challenge_golemancy_bound_25", "golemancy.bound", 25, () -> getConfig().challengeBoundReward * 3);
    registerMilestone("challenge_golemancy_golem_kills_50", "golemancy.golem.kills", 50, () -> getConfig().challengeGolemKillsReward);
    registerMilestone("challenge_golemancy_golem_kills_500", "golemancy.golem.kills", 500, () -> getConfig().challengeGolemKillsReward * 3);
    registerMilestone("challenge_golemancy_golem_kills_5000", "golemancy.golem.kills", 5000, () -> getConfig().challengeGolemKillsReward * 8);
    registerMilestone("challenge_golemancy_tasks_1000", "golemancy.tasks", 1000, () -> getConfig().challengeTasksReward);
    registerMilestone("challenge_golemancy_tasks_25000", "golemancy.tasks", 25000, () -> getConfig().challengeTasksReward * 3);
    registerMilestone("challenge_golemancy_items_moved_10000", "golemancy.items.moved", 10000, () -> getConfig().challengeItemsMovedReward);
    registerMilestone("challenge_golemancy_items_moved_250000", "golemancy.items.moved", 250000, () -> getConfig().challengeItemsMovedReward * 3);
    registerMilestone("challenge_golemancy_infusions_10", "golemancy.infusions", 10, () -> getConfig().challengeInfusionsReward);
    registerMilestone("challenge_golemancy_infusions_100", "golemancy.infusions", 100, () -> getConfig().challengeInfusionsReward * 3);
    GolemancyRuntime.setBindListener(this::golemBound);
    GolemancyRuntime.setPlinthHost(new GolemancyPlinthHost());
    GolemWorkXp.setSink(this::golemWorked);
    GolemRepairXp.setSink(this::payRepair);
    GolemWorkStats.setSink(new WorkStats());
    GolemItems.craftCounter().observe(this::rewardCraft);
  }

  @Override
  public List<AdaptationGroup> getAdaptationGroups() {
    return GROUPS;
  }

  @Override
  public void onTick() {
    long nowMillis = M.ms();
    buildAttribution.expire(System.nanoTime(), getConfig().buildAttributionWindowMillis);
    buildCooldowns.sweep(nowMillis, getConfig().buildXpCooldownMillis);
    witnessCooldowns.sweep(nowMillis, getConfig().witnessCooldownMillis);
    witnessCap.sweep(nowMillis);
    workCap.sweep(nowMillis);
    bindCap.sweep(nowMillis);
    assistCooldowns.sweep(nowMillis, getConfig().assistCooldownMillis);
    tankCooldowns.sweep(nowMillis, getConfig().tankCooldownMillis);
    bindCooldowns.sweep(nowMillis, getConfig().bindXpCooldownMillis);
    careChecks.values().removeIf(scheduledAt -> nowMillis - scheduledAt > CARE_CHECK_STALE_MILLIS);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    BlockRef block = e.block();
    Id type = block.type();
    if ((!CARVED_PUMPKIN.equals(type) && !JACK_O_LANTERN.equals(type)) || shouldReturnForWorld(block.world(), this)) {
      return;
    }
    GolemBuildAttribution.HeadPlacement placement = new GolemBuildAttribution.HeadPlacement(
        block.world().name(), block.x(), block.y(), block.z(), e.player().uuid(), System.nanoTime());
    buildAttribution.recordPlacement(placement, getConfig().buildAttributionRadius, getConfig().buildAttributionWindowMillis)
        .ifPresent(build -> Tasks.runEntity(build.spawn().golem(), () -> applyBuild(build)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(CreatureSpawnEvent e) {
    Reason reason = e.reason();
    LivingRef golem = e.entity();
    if (SpawnerMobTag.isSpawnerReason(reason) && isEnabled()) {
      SpawnerMobTag.mark(golem);
      return;
    }
    if (reason == Reason.BUILD_COPPERGOLEM && golem instanceof MobRef copper) {
      CopperCare.recordStageFloor(copper, copper.weatheringStage());
    }
    if (GolemBuildAttribution.bodyKey(reason) == null || shouldReturnForWorld(golem.world(), this)) {
      return;
    }
    Loc at = golem.location();
    GolemBuildAttribution.GolemSpawn spawn = new GolemBuildAttribution.GolemSpawn(
        golem, reason, golem.world().name(), at.x(), at.y(), at.z(), System.nanoTime());
    buildAttribution.recordSpawn(spawn, getConfig().buildAttributionRadius, getConfig().buildAttributionWindowMillis)
        .ifPresent(this::applyBuild);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef victim)) {
      return;
    }
    EntityRef damager = e.damager();
    double damage = e.finalDamage();
    if (HostileDamagePool.isPoolBody(victim.type())
        && HostileDamagePool.isHostileDamager(damager)
        && !excludedMob(GolemXpRules.attacker(damager))
        && !shouldReturnForWorld(victim.world(), this)) {
      HostileDamagePool.add(victim, damage, maxHealthOf(victim));
      GolemancyRuntime.handle(victim).ifPresent(handle -> rewardTank(handle, victim, damager, damage));
    }
    if (!victim.has(EntityTrait.ENEMY)) {
      return;
    }
    Optional<GolemHandle> golem = GolemancyRuntime.handle(GolemXpRules.golemSource(damager));
    if (golem.isPresent() && !excludedMob(victim) && !shouldReturnForWorld(victim.world(), this)) {
      rewardAssist(golem.get(), victim, damage);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    if (!(e.clicked() instanceof MobRef golem) || !IRON_GOLEM.equals(golem.type()) || !golem.isPlayerCreated()) {
      return;
    }
    PlayerRef player = e.player();
    if (!IRON_INGOT.equals(heldType(player, e.hand()))) {
      return;
    }
    double before = golem.health();
    shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleRepair(player, golem, before)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    EntityRef damager = e.directDamager();
    if (!victim.has(EntityTrait.ENEMY) || damager == null) {
      return;
    }
    Optional<GolemHandle> owned = GolemancyRuntime.handle(GolemXpRules.golemSource(damager));
    MobRef witnessed = owned.isPresent() ? null : GolemXpRules.witnessGolem(damager);
    if (owned.isEmpty() && witnessed == null) {
      rewardServantKill(victim, GolemXpRules.attacker(damager));
      return;
    }
    if (excludedMob(victim) || shouldReturnForWorld(victim.world(), this)) {
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
    GolemancyRuntime.names().ifPresent(GolemNames::evaluate);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    clampXpCaps(loadedConfig);
  }

  @Override
  protected List<?> createCompanionListeners() {
    NodeAreaSelector areas = new NodeAreaSelector();
    NodeTagPrompt prompt = new NodeTagPrompt(areas);
    NodeInteractions nodes = new NodeInteractions(prompt, areas);
    CrookInteractions crook = new CrookInteractions(areas);
    return List.of(areas, crook, nodes, prompt, new NodeItemTags.ReloadListener(), new CopperCareListener());
  }

  public static Id heldType(PlayerRef player, Hand hand) {
    ItemRef held = player.inventory().hand(hand);
    return held == null || held.isEmpty() ? Id.minecraft("air") : held.type();
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

  static void clampXpCaps(Config config) {
    config.workXpHourlyCap = Double.isFinite(config.workXpHourlyCap) ? Math.max(0D, config.workXpHourlyCap) : 0D;
    config.workXpOwnerRadius = Double.isFinite(config.workXpOwnerRadius) ? Math.max(0D, config.workXpOwnerRadius) : 0D;
    config.bindXpHourlyCap = Double.isFinite(config.bindXpHourlyCap) ? Math.max(0D, config.bindXpHourlyCap) : 0D;
  }

  static double grantWorkXp(RollingXpCap cap, UUID owner, double xp, double distanceSquared, Config config, long nowMillis) {
    double radius = config.workXpOwnerRadius;
    if (!(distanceSquared <= radius * radius)) {
      return 0D;
    }
    return cap.claim(owner, xp, config.workXpHourlyCap, nowMillis);
  }

  static double grantBindXp(RollingXpCap cap, UUID player, double xp, Config config, long nowMillis) {
    return cap.claim(player, xp, config.bindXpHourlyCap, nowMillis);
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
      case BONE -> 0D;
    };
  }

  static NodeSettings nodeSettings(Config config) {
    return new NodeSettings(config.maxNodesPerChunk, config.nodeScanIntervalTicks, config.blocksPerNodeScan,
        config.maxOpenTasksPerNode, NodeVisibility.parse(config.nodeVisibility));
  }

  static PlinthSettings plinthSettings(Config config) {
    return new PlinthSettings(PlinthStructure.pedestals(config.pedestalBlocks, type -> AdaptCore.platform().host().items().isBlock(type), AdaptLog::warn), InfusionRecipes.bukkit(config.infusionRecipes));
  }

  static double infusionXp(Config config, Id result) {
    String key = result.path();
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

  static String infusionRewardKey(Id result) {
    return "golemancy:craft:" + result.path();
  }

  private void applyBuild(GolemBuildAttribution.Build build) {
    EntityRef golem = build.spawn().golem();
    GolemBuildAttribution.stamp(golem, build.builder(), System.currentTimeMillis());
    PlayerRef builder = AdaptCore.platform().host().player(build.builder());
    if (builder == null) {
      return;
    }
    Loc at = golem.location();
    Reason reason = build.spawn().reason();
    shouldReturnForPlayer(builder, () -> rewardBuild(builder, reason, at));
  }

  private void rewardBuild(PlayerRef builder, Reason reason, Loc at) {
    addStat(builder, "golemancy.built", 1);
    double xp = buildXp(reason);
    long nowMillis = M.ms();
    if (xp <= 0D || !buildCooldowns.tryClaim(new BuildCooldownKey(builder.uuid(), reason), nowMillis, getConfig().buildXpCooldownMillis)) {
      return;
    }
    double granted = grantBindXp(bindCap, builder.uuid(), xp, getConfig(), nowMillis);
    if (granted <= 0D) {
      return;
    }
    xp(builder, at, granted, "golemancy:build:" + GolemBuildAttribution.bodyKey(reason));
    fx(at, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 5, 1.2D)
        .particle(Particles.VILLAGER_HAPPY, 4, 0D, 0.8D, 0D, 0.3D, 0D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
  }

  private double buildXp(Reason reason) {
    return switch (reason) {
      case BUILD_SNOWMAN -> getConfig().buildSnowXp;
      case BUILD_COPPERGOLEM -> getConfig().buildCopperXp;
      case BUILD_IRONGOLEM -> getConfig().buildIronXp;
      default -> 0D;
    };
  }

  private void settleRepair(PlayerRef player, LivingRef golem, double before) {
    GolemRepairXp.repaired(player, golem, golem.health() - before);
  }

  void payRepair(PlayerRef player, LivingRef golem, double healed) {
    double xp = GolemXpRules.repairXp(HostileDamagePool.consume(golem, healed), getConfig().repairXpPerHealth);
    if (xp <= 0D) {
      return;
    }
    Loc at = golem.location();
    shouldReturnForPlayer(player, () -> xp(player, at, xp, GolemRepairXp.REWARD_KEY));
    repairFx(at);
  }

  void repairFx(Loc at) {
    fx(at.add(0D, 1.5D, 0D), FxPriority.AMBIENT)
        .burst(Particles.VILLAGER_HAPPY, 5, 0.6D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.5F);
  }

  private void rewardWitnesses(LivingRef victim, MobRef golem) {
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(golem))
        || GolemState.has(golem)
        || MinionIdentity.isMinion(golem)) {
      return;
    }
    double killValue = GolemXpRules.killValue(getConfig().golemKillXpBase, maxHealthOf(victim), getConfig().golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.witnessXp(killValue, getConfig().witnessXpMultiplier, SpawnerMobTag.isTagged(victim), getConfig().spawnerMobXpMultiplier);
    if (xp <= 0D) {
      return;
    }
    Loc center = golem.location();
    double radius = getConfig().witnessRadius;
    UUID golemId = golem.uuid();
    for (PlayerRef witness : center.world().players()) {
      if (witness.location().distanceSquared(center) <= radius * radius) {
        shouldReturnForPlayer(witness, () -> payWitness(witness, golemId, center, xp));
      }
    }
  }

  private void golemBound(PlayerRef owner, GolemHandle handle) {
    MobRef golem = handle.entity();
    if (shouldReturnForWorld(golem.world(), this)) {
      return;
    }
    GolemBody body = handle.body();
    boolean firstBindOfGolem = !handle.state().previouslyBound();
    Loc at = golem.location();
    Tasks.runEntity(owner, () -> shouldReturnForPlayer(owner, () -> rewardBind(owner, body, firstBindOfGolem, at)));
  }

  private void golemWorked(GolemHandle handle, GolemCoreType core, double xp) {
    MobRef golem = handle.entity();
    if (shouldReturnForWorld(golem.world(), this)) {
      return;
    }
    Loc at = golem.location();
    String rewardKey = GolemWorkXp.rewardKey(core);
    rewardOwner(handle.owner(), owner -> payWork(owner, at, xp, rewardKey));
  }

  private void payWork(PlayerRef owner, Loc at, double xp, String rewardKey) {
    double distanceSquared = ownerDistanceSquared(owner, at);
    double granted = grantWorkXp(workCap, owner.uuid(), xp, distanceSquared, getConfig(), M.ms());
    if (granted > 0D) {
      xp(owner, at, granted, rewardKey);
    }
  }

  private void workerStat(GolemHandle handle, Consumer<PlayerRef> record) {
    if (shouldReturnForWorld(handle.entity().world(), this)) {
      return;
    }
    rewardOwner(handle.owner(), record);
  }

  private void rewardBind(PlayerRef owner, GolemBody body, boolean firstBindOfGolem, Loc at) {
    if (firstBindOfGolem) {
      addStat(owner, "golemancy.bound", 1);
      long nowMillis = M.ms();
      if (bindCooldowns.tryClaim(new BindCooldownKey(owner.uuid(), body), nowMillis, getConfig().bindXpCooldownMillis)) {
        double granted = grantBindXp(bindCap, owner.uuid(), bindXp(getConfig(), body), getConfig(), nowMillis);
        if (granted > 0D) {
          xp(owner, at, granted, "golemancy:bind");
        }
      }
    }
    if (body != GolemBody.BONE && grantOnce(owner, "challenge_golemancy_first_bind_" + body.id())) {
      xp(owner, at, getConfig().firstBindXp, "golemancy:first_bind:" + body.id());
    }
  }

  private void rewardCraft(PlayerRef player, Id recipe, ItemRef result, int crafts) {
    GolemItemKind kind = GolemItems.kindOf(result).orElse(null);
    if (kind == null) {
      return;
    }
    double xp = craftXp(getConfig(), kind) * craftUnits(kind, crafts, result.amount());
    if (xp <= 0D) {
      return;
    }
    shouldReturnForPlayer(player, () -> xp(player, player.location(), xp, "golemancy:craft:" + kind.id()));
  }

  static int craftUnits(GolemItemKind kind, int crafts, int resultAmount) {
    return kind == GolemItemKind.BLANK_NODE ? crafts * Math.max(1, resultAmount) : crafts;
  }

  static double craftXp(Config config, GolemItemKind kind) {
    return switch (kind) {
      case CROOK -> config.craftCrookXp;
      case BLANK_CORE -> config.craftBlankCoreXp;
      case CORE -> config.craftRoleCoreXp;
      case BLANK_NODE -> config.craftBlankNodeXp;
      case NODE -> config.craftNodeXp;
      case BLANK_RUNE -> config.craftBlankRuneXp;
      case RUNE, DORMANT_GOLEM, SHATTERED_CORE, MASTERWORK_HEART -> 0D;
    };
  }

  private void rewardInfusion(PlayerRef player, Id result, Loc at) {
    addStat(player, "golemancy.infusions", 1);
    double xp = infusionXp(getConfig(), result);
    if (xp > 0D) {
      xp(player, at, xp, infusionRewardKey(result));
    }
  }

  private void rewardAssist(GolemHandle handle, LivingRef victim, double damage) {
    double dealt = GolemXpRules.effectiveDamage(damage, victim.health());
    if (dealt <= 0D) {
      return;
    }
    MobRef golem = handle.entity();
    Tasks.runEntity(golem, () -> handle.addDamage(dealt));
    Config config = getConfig();
    double xp = GolemXpRules.assistXp(dealt, config.assistDamageXpMultiplier, assistBodyMultiplier(config, handle.body()),
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier);
    Loc at = combatLocation(golem, victim);
    rewardOwner(handle.owner(), owner -> payAssist(owner, at, dealt, xp));
  }

  private void payAssist(PlayerRef owner, Loc at, double dealt, double xp) {
    addStat(owner, "golemancy.golem.damage", dealt);
    if (xp <= 0D || !assistCooldowns.tryClaim(owner.uuid(), M.ms(), getConfig().assistCooldownMillis)) {
      return;
    }
    xp(owner, at, xp * distanceFactor(owner, at), "golemancy:assist");
  }

  private void rewardOwnedKill(GolemHandle handle, LivingRef victim) {
    MobRef golem = handle.entity();
    Tasks.runEntity(golem, () -> handle.addKills(1));
    Config config = getConfig();
    double killValue = GolemXpRules.killValue(config.golemKillXpBase, maxHealthOf(victim), config.golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.ownedKillXp(killValue, GolemXpRules.inActiveRaid(victim), config.raidXpMultiplier,
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier);
    String rewardKey = "golemancy:kill:" + victim.type().path();
    Loc at = combatLocation(golem, victim);
    rewardOwner(handle.owner(), owner -> payKill(owner, at, xp, rewardKey));
  }

  private void rewardServantKill(LivingRef victim, EntityRef attacker) {
    UUID ownerId = TragoulSkeletalServant.MINION_KIND.matches(attacker) ? TragoulSkeletalServant.MINION_KIND.ownerOf(attacker) : null;
    double share = ownerId == null ? 0D : ServantFrame.frame().map(frame -> frame.share(ownerId)).orElse(0D);
    if (share <= 0D || excludedMob(victim) || shouldReturnForWorld(victim.world(), this)) {
      return;
    }
    Config config = getConfig();
    double killValue = GolemXpRules.killValue(config.golemKillXpBase, maxHealthOf(victim), config.golemKillMaxHealthMultiplier);
    double xp = GolemXpRules.servantShareXp(share, GolemXpRules.ownedKillXp(killValue, GolemXpRules.inActiveRaid(victim), config.raidXpMultiplier,
        SpawnerMobTag.isTagged(victim), config.spawnerMobXpMultiplier));
    if (xp <= 0D) {
      return;
    }
    String rewardKey = GolemXpRules.servantRewardKey(victim.type());
    Loc at = victim.location();
    rewardOwner(ownerId, owner -> xp(owner, at, xp * distanceFactor(owner, at), rewardKey));
  }

  private void payKill(PlayerRef owner, Loc at, double xp, String rewardKey) {
    addStat(owner, "golemancy.golem.kills", 1);
    xp(owner, at, xp * distanceFactor(owner, at), rewardKey);
  }

  private void rewardTank(GolemHandle handle, LivingRef golem, EntityRef damager, double damage) {
    EntityRef attacker = GolemXpRules.attacker(damager);
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(attacker)) || excludedMob(attacker)) {
      return;
    }
    double taken = GolemXpRules.effectiveDamage(damage, golem.health());
    double xp = GolemXpRules.tankXp(taken, getConfig().tankXpMultiplier, SpawnerMobTag.isTagged(attacker), getConfig().spawnerMobXpMultiplier);
    if (xp <= 0D) {
      return;
    }
    Loc at = golem.location();
    rewardOwner(handle.owner(), owner -> payTank(owner, at, xp));
  }

  private void payTank(PlayerRef owner, Loc at, double xp) {
    if (!tankCooldowns.tryClaim(owner.uuid(), M.ms(), getConfig().tankCooldownMillis)) {
      return;
    }
    xp(owner, at, xp * distanceFactor(owner, at), "golemancy:tank");
  }

  private void rewardOwner(UUID ownerId, Consumer<PlayerRef> reward) {
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner == null) {
      return;
    }
    Tasks.runEntity(owner, () -> shouldReturnForPlayer(owner, () -> reward.accept(owner)));
  }

  private double distanceFactor(PlayerRef owner, Loc at) {
    return GolemXpRules.ownerDistanceFactor(ownerDistanceSquared(owner, at), getConfig().ownerProximityRadius, getConfig().distantOwnerXpMultiplier);
  }

  private static double ownerDistanceSquared(PlayerRef owner, Loc at) {
    Loc ownerAt = owner.location();
    return ownerAt.world() != null && ownerAt.sameWorld(at) ? ownerAt.distanceSquared(at) : Double.POSITIVE_INFINITY;
  }

  private void payWitness(PlayerRef witness, UUID golemId, Loc center, double xp) {
    long nowMillis = M.ms();
    if (!witnessCooldowns.tryClaim(new WitnessCooldownKey(golemId, witness.uuid()), nowMillis, getConfig().witnessCooldownMillis)) {
      return;
    }
    double granted = witnessCap.claim(witness.uuid(), xp, getConfig().witnessHourlyCap, nowMillis);
    if (granted > 0D) {
      xp(witness, center, granted, "golemancy:witness");
    }
  }

  private void scheduleCareCheck(EntityRef golem, Runnable settle) {
    UUID golemId = golem.uuid();
    long scheduledAt = M.ms();
    if (careChecks.putIfAbsent(golemId, scheduledAt) != null) {
      return;
    }
    Runnable check = () -> {
      careChecks.remove(golemId, scheduledAt);
      settle.run();
    };
    if (!Tasks.runEntity(golem, check, 1)) {
      careChecks.remove(golemId, scheduledAt);
    }
  }

  private static boolean excludedMob(EntityRef entity) {
    return MinionIdentity.isMinion(entity) || CombatXp.isGraveMob(entity);
  }

  private static Loc combatLocation(EntityRef golem, EntityRef victim) {
    return Tasks.isOwnedByCurrentRegion(golem) ? golem.location() : victim.location();
  }

  private static double maxHealthOf(LivingRef entity) {
    return GolemVitals.maxHealth(entity);
  }

  private record BuildCooldownKey(UUID player, Reason reason) {
  }

  private record BindCooldownKey(UUID player, GolemBody body) {
  }

  private final class WorkStats implements GolemWorkStats.Sink {
    @Override
    public void completed(GolemHandle handle, GolemTask task) {
      Loc at = handle.entity().location();
      GolemLastJobs.finished(handle, task);
      workerStat(handle, owner -> {
        addStat(owner, "golemancy.tasks", 1);
        AdaptCore.get().getBus().post(new GolemTaskDoneEvent(owner, handle, at, task));
      });
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
    public int level(PlayerRef player, String adaptationId) {
      return adaptation(adaptationId).map(adaptation -> adaptation.getActiveLevel(player)).orElse(0);
    }

    @Override
    public boolean canStore(PlayerRef player, String adaptationId, Loc at) {
      return adaptation(adaptationId).map(adaptation -> adaptation.canAccessChest(player, at)).orElse(false);
    }

    @Override
    public boolean canInfuse(PlayerRef player, String adaptationId, Loc at) {
      return adaptation(adaptationId).map(adaptation -> adaptation.canInteract(player, at) && adaptation.canBlockPlace(player, at)).orElse(false);
    }

    @Override
    public Optional<PlinthCharge> chargeLevels(PlayerRef player, String adaptationId, int levels) {
      return adaptation(adaptationId).flatMap(adaptation -> LevelCharge.take(adaptation, player, levels));
    }

    @Override
    public FxEmitter fx(Loc at, FxPriority priority) {
      return SkillGolemancy.this.fx(at, priority);
    }

    @Override
    public FxTimeline timeline(Loc at) {
      return SkillGolemancy.this.timeline(at);
    }

    @Override
    public void infused(PlayerRef player, Infusion infusion, Loc at) {
      if (shouldReturnForWorld(at.world(), SkillGolemancy.this)) {
        return;
      }
      Tasks.runEntity(player, () -> shouldReturnForPlayer(player, () -> rewardInfusion(player, infusion.result(), at)));
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

  private final class CopperCareListener {
    @Handle(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEntityEvent e) {
      if (!(e.clicked() instanceof MobRef golem) || !CopperCare.COPPER_GOLEM.equals(golem.type())) {
        return;
      }
      PlayerRef player = e.player();
      Id held = heldType(player, e.hand());
      if (AdaptCore.platform().host().registries().isTagged(RegistryKind.ITEM, AXES, held)) {
        int before = golem.weatheringStage();
        shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleScrape(player, golem, before)));
      } else if (HONEYCOMB.equals(held)) {
        boolean waxedBefore = golem.isWaxed();
        shouldReturnForPlayer(player, e, () -> scheduleCareCheck(golem, () -> settleWax(player, golem, waxedBefore)));
      }
    }

    private void settleScrape(PlayerRef player, MobRef golem, int before) {
      if (!CopperCare.claimScrape(golem, before, golem.weatheringStage())) {
        return;
      }
      Loc at = golem.location();
      shouldReturnForPlayer(player, () -> xp(player, at, getConfig().copperScrapeXp, "golemancy:copper:scrape"));
    }

    private void settleWax(PlayerRef player, MobRef golem, boolean waxedBefore) {
      if (!CopperCare.newlyWaxed(waxedBefore, golem.isWaxed()) || !CopperCare.claimWaxPayment(golem)) {
        return;
      }
      Loc at = golem.location();
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
    double witnessHourlyCap = 1200;
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
    @ConfigDoc(value = "Skill XP paid for each Artificer's Crook crafted.", impact = "Higher values pay more for crafting the Crook.")
    double craftCrookXp = 60;
    @ConfigDoc(value = "Skill XP paid for each Blank Animus Core crafted.", impact = "Higher values pay more for crafting blank cores.")
    double craftBlankCoreXp = 40;
    @ConfigDoc(value = "Skill XP paid for each role core crafted.", impact = "Higher values pay more for crafting role cores.")
    double craftRoleCoreXp = 90;
    @ConfigDoc(value = "Skill XP paid for each Blank Node crafted, counted per node in the result.", impact = "Higher values pay more for crafting blank nodes.")
    double craftBlankNodeXp = 5;
    @ConfigDoc(value = "Skill XP paid for each typed node crafted.", impact = "Higher values pay more for crafting nodes.")
    double craftNodeXp = 15;
    @ConfigDoc(value = "Skill XP paid for each Blank Rune crafted, counted per craft, not per rune in the result.", impact = "Higher values pay more for crafting blank runes.")
    double craftBlankRuneXp = 10;
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
    @ConfigDoc(value = "Milliseconds before one player earns bind XP again for the same golem type. Bindings during the cooldown still count toward golemancy.bound.", impact = "Higher values stop build, bind and release loops from paying bind XP quickly.")
    long bindXpCooldownMillis = 20000;
    @ConfigDoc(value = "Most build and bind XP one player can earn together in any rolling 60 minutes. First-bind challenges are not counted. Values below 0 count as 0.", impact = "Higher values let build, bind and release loops pay more per hour; 0 pays no build or bind XP.")
    double bindXpHourlyCap = 1200;
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
    double bodyScaleBone = 0.49;
    @ConfigDoc(value = "Items a bound frost golem can carry at once.", impact = "Higher values let working frost golems move more items per trip.")
    int bodyCarryFrost = 8;
    @ConfigDoc(value = "Items a bound copper golem can carry at once before Copper Frame raises it.", impact = "Higher values let working copper golems move more items per trip.")
    int bodyCarryCopper = 16;
    @ConfigDoc(value = "Items a bound iron golem can carry at once.", impact = "Higher values let working iron golems move more items per trip.")
    int bodyCarryIron = 64;
    @ConfigDoc(value = "Cores a frost golem can be bound with.", impact = "Remove a core to stop frost golems taking that role.")
    List<String> allowedCoresFrost = new ArrayList<>(List.of("BLANK", "GUARDIAN", "GATHERER", "HARVESTER"));
    @ConfigDoc(value = "Cores a copper golem can be bound with.", impact = "Remove a core to stop copper golems taking that role.")
    List<String> allowedCoresCopper = new ArrayList<>(List.of("BLANK", "GATHERER", "HARVESTER", "LUMBER", "BREAKER", "RANCHER", "SORTER", "ANGLER"));
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
    @ConfigDoc(value = "Blocks a fighting or working golem may stray from its owner, home, or stay spot before it drops the fight or job and walks back. It is also the farthest a linked home chest may be from the golem's home. The minimum is 8.", impact = "Higher values let golems chase enemies and work farther out; keep it at least baseWorkRadius plus the largest Tide rune bonus so golems can finish jobs at the edge of their reach.")
    double leashRadius = 48;
    @ConfigDoc(value = "Seconds a working golem stands idle away from home before it walks back.", impact = "Lower values send idle golems home sooner; 0 sends them home at once.")
    int idleReturnSeconds = 8;
    @ConfigDoc(value = "Ticks between path refreshes for a moving copper golem, clamped to 2-20.", impact = "Lower values keep copper golems on course; higher values leave a copper golem standing longer after something, such as taking damage, stops its path.")
    int copperRepathTicks = 10;
    @ConfigDoc(value = "Blocks around you within which your golems glow in their channel color and show you their status, your skeletal servants glow with Bone Frame, and your nodes appear while you hold the Crook, a Node, a Core or a Dormant Golem.", impact = "Higher values reveal golems and nodes farther away; lower values do less work on busy servers.")
    double revealRange = 32.0;
    @ConfigDoc(value = "Blocks within which revealed golem and node labels are drawn on screen, and within which a golem you look at shows you its status in the Gloss entity overlay.", impact = "Higher values let you read labels and golem status from farther away.")
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
    double baseWorkRadius = 32;
    @ConfigDoc(value = "Seconds a golem walking to a job or a container may go without getting half a block closer before it gives up and skips that job or container for 10 seconds, clamped to 1-60.", impact = "Lower values give up on blocked paths sooner; higher values keep trying longer.")
    int stuckTimeoutSeconds = 4;
    @ConfigDoc(value = "Jobs or containers in a row a golem may give up because it got stuck before it rests for stuckPauseSeconds, clamped to 1-100.", impact = "Lower values rest trapped golems sooner; higher values keep them trying longer.")
    int stuckReleasesBeforePause = 3;
    @ConfigDoc(value = "Seconds a trapped golem rests without looking for jobs or containers after giving up stuckReleasesBeforePause in a row, clamped to 0-600.", impact = "Higher values save server time on trapped golems; 0 never rests them.")
    int stuckPauseSeconds = 15;
    @ConfigDoc(value = "Most work XP one player can earn from their golems' finished jobs in any rolling 60 minutes. Values below 0 count as 0.", impact = "Higher values allow more passive XP from golem labor per hour; 0 pays no work XP.")
    double workXpHourlyCap = 2400;
    @ConfigDoc(value = "Blocks from a working golem within which its owner earns work XP for its finished jobs. Owners farther away or in another world earn none. Values below 0 count as 0.", impact = "Higher values let owners earn work XP while farther from their golems.")
    double workXpOwnerRadius = 96;
    @ConfigDoc(value = "Skill XP paid by the golem job challenges.", impact = "Higher values pay more; the 25,000 jobs challenge pays five times this value.")
    double challengeTasksReward = 500;
    @ConfigDoc(value = "Skill XP paid by the golem item hauling challenges.", impact = "Higher values pay more; the 250,000 items challenge pays five times this value.")
    double challengeItemsMovedReward = 500;
    @ConfigDoc(value = "Blocks that work as Animus Plinth pedestals, two blocks north, east, south and west of a lodestone at the same height.", impact = "Add or remove block names to change which blocks form a plinth; unknown names and the lodestone are ignored with a warning.")
    List<String> pedestalBlocks = new ArrayList<>(PlinthStructure.defaultPedestals());
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
