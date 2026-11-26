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

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.skill.AdaptationGroup;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.nether.NetherAshwalker;
import art.arcane.adapt.content.adaptation.nether.NetherBlazeLeech;
import art.arcane.adapt.content.adaptation.nether.NetherCrimsonFeast;
import art.arcane.adapt.content.adaptation.nether.NetherFireResist;
import art.arcane.adapt.content.adaptation.nether.NetherGhastWard;
import art.arcane.adapt.content.adaptation.nether.NetherLavaWalker;
import art.arcane.adapt.content.adaptation.nether.NetherMagmaSkin;
import art.arcane.adapt.content.adaptation.nether.NetherNetherrackMason;
import art.arcane.adapt.content.adaptation.nether.NetherPiglinBroker;
import art.arcane.adapt.content.adaptation.nether.NetherSkullYeet;
import art.arcane.adapt.content.adaptation.nether.NetherSoulStrider;
import art.arcane.adapt.content.adaptation.nether.NetherStriderBond;
import art.arcane.adapt.content.adaptation.nether.NetherWitherHarvest;
import art.arcane.adapt.content.adaptation.nether.NetherWitherResist;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherBoon;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherFightStrike;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherFuel;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherState;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherTrigger;
import art.arcane.adapt.content.adaptation.nether.blocks.NetherWorkStrike;
import art.arcane.adapt.content.adaptation.nether.upgraded.Brimstone;
import art.arcane.adapt.content.adaptation.nether.upgraded.Hellwalker;
import art.arcane.adapt.content.adaptation.nether.upgraded.WitherKing;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

public class SkillNether extends SimpleSkill<SkillNether.Config> {
  private final Cooldowns witherRoseCooldowns = cooldowns();
  private final Cooldowns witherDamageCooldowns = cooldowns();
  private final Cooldowns witherAttackCooldowns = cooldowns();

  static final List<AdaptationGroup> GROUPS = List.of(
      AdaptationGroup.of(GroupMessages.NETHER_FIRE, "nether-fire-resist", "nether-ashwalker", "nether-magma-skin",
          "nether-blaze-leech", "nether-lava-walker", "nether-crimson-feast"),
      AdaptationGroup.of(GroupMessages.NETHER_WITHER, "nether-wither-resist", "nether-ghast-ward",
          "nether-wither-harvest", "nether-skull-toss"),
      AdaptationGroup.of(GroupMessages.NETHER_EXPLORING, "nether-strider-bond", "nether-soul-strider",
          "nether-netherrack-mason", "nether-piglin-broker")
  );

  public SkillNether() {
    super("nether", SkillPresentation.of(SkillMessages.NETHER_NAME, SkillMessages.NETHER_ICON, SkillMessages.NETHER_DESCRIPTION,
        SkillMessages.NETHER_QUIP, SkillMessages.NETHER_UNLOCK_QUIP_1, SkillMessages.NETHER_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setInterval(7425);
    setColor(C.DARK_GRAY);
    setIcon(Id.minecraft("nether_star"));
    registerAdaptation(new NetherWitherResist());
    registerAdaptation(new NetherSkullYeet());
    registerAdaptation(new NetherFireResist());
    registerAdaptation(new NetherLavaWalker());
    registerAdaptation(new NetherGhastWard());
    registerAdaptation(new NetherBlazeLeech());
    registerAdaptation(new NetherPiglinBroker());
    registerAdaptation(new NetherSoulStrider());
    registerAdaptation(new NetherMagmaSkin());
    registerAdaptation(new NetherNetherrackMason());
    registerAdaptation(new NetherStriderBond());
    registerAdaptation(new NetherCrimsonFeast());
    registerAdaptation(new NetherAshwalker());
    registerAdaptation(new NetherWitherHarvest());
    registerUpgrades(new Hellwalker(), new WitherKing(), new Brimstone());
    registerBlocks(new NetherTrigger(), new NetherFightStrike(), new NetherWorkStrike(),
        new NetherBoon(), new NetherFuel(), new NetherState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_skeleton_skull"))
        .key("challenge_nether_50")
        .model(CustomModel.get(Id.minecraft("wither_skeleton_skull"), "advancement", "nether", "challenge_nether_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_nether_500")
            .model(CustomModel.get(Id.minecraft("nether_star"), "advancement", "nether", "challenge_nether_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("beacon"))
                .key("challenge_nether_5k")
                .model(CustomModel.get(Id.minecraft("beacon"), "advancement", "nether", "challenge_nether_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_nether_50", "nether.kills", 50, () -> getConfig().getChallengeNetherReward());
    registerMilestone("challenge_nether_500", "nether.kills", 500, () -> getConfig().getChallengeNetherReward() * 3);
    registerMilestone("challenge_nether_5k", "nether.kills", 5000, () -> getConfig().getChallengeNetherReward() * 8);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_wither_dmg_500")
        .model(CustomModel.get(Id.minecraft("wither_rose"), "advancement", "nether", "challenge_wither_dmg_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_lantern"))
            .key("challenge_wither_dmg_5k")
            .model(CustomModel.get(Id.minecraft("soul_lantern"), "advancement", "nether", "challenge_wither_dmg_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_wither_dmg_500", "nether.wither.damage", 500, () -> getConfig().getChallengeWitherDmgReward());
    registerMilestone("challenge_wither_dmg_5k", "nether.wither.damage", 5000, () -> getConfig().getChallengeWitherDmgReward() * 3);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_wither_skel_25")
        .model(CustomModel.get(Id.minecraft("bone"), "advancement", "nether", "challenge_wither_skel_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("wither_skeleton_skull"))
            .key("challenge_wither_skel_250")
            .model(CustomModel.get(Id.minecraft("wither_skeleton_skull"), "advancement", "nether", "challenge_wither_skel_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_wither_skel_25", "nether.skeleton.kills", 25, () -> getConfig().getChallengeWitherSkelReward());
    registerMilestone("challenge_wither_skel_250", "nether.skeleton.kills", 250, () -> getConfig().getChallengeWitherSkelReward() * 3);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("nether_star"))
        .key("challenge_wither_boss_1")
        .model(CustomModel.get(Id.minecraft("nether_star"), "advancement", "nether", "challenge_wither_boss_1"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("beacon"))
            .key("challenge_wither_boss_10")
            .model(CustomModel.get(Id.minecraft("beacon"), "advancement", "nether", "challenge_wither_boss_10"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_wither_boss_1", "nether.boss.kills", 1, () -> getConfig().getChallengeWitherBossReward());
    registerMilestone("challenge_wither_boss_10", "nether.boss.kills", 10, () -> getConfig().getChallengeWitherBossReward() * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wither_rose"))
        .key("challenge_roses_10")
        .model(CustomModel.get(Id.minecraft("wither_rose"), "advancement", "nether", "challenge_roses_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("flower_pot"))
            .key("challenge_roses_100")
            .model(CustomModel.get(Id.minecraft("flower_pot"), "advancement", "nether", "challenge_roses_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_roses_10", "nether.roses.broken", 10, () -> getConfig().getChallengeRosesReward());
    registerMilestone("challenge_roses_100", "nether.roses.broken", 100, () -> getConfig().getChallengeRosesReward() * 3);
  }

  @Override
  public List<AdaptationGroup> getAdaptationGroups() {
    return GROUPS;
  }

  private static final Id WITHER_ROSE = Id.minecraft("wither_rose");
  private static final Id WITHER_SKELETON = Id.minecraft("wither_skeleton");
  private static final Id WITHER = Id.minecraft("wither");
  private static final Set<Id> NETHER_MOBS = Set.of(Id.minecraft("blaze"), Id.minecraft("ghast"), Id.minecraft("piglin"),
      Id.minecraft("piglin_brute"), Id.minecraft("hoglin"), Id.minecraft("zoglin"), Id.minecraft("magma_cube"),
      Id.minecraft("zombified_piglin"));
  private static final Id NETHER_QUARTZ_ORE = Id.minecraft("nether_quartz_ore");
  private static final Id NETHER_GOLD_ORE = Id.minecraft("nether_gold_ore");
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");

  private boolean isWitherDamageCause(DamageCause cause) {
    return cause == DamageCause.WITHER;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (!this.isEnabled() || !(e.entity() instanceof PlayerRef p) || !isWitherDamageCause(e.cause()) || e instanceof EntityDamageByBlockEvent) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!witherDamageCooldowns.isReady(p.uuid(), getConfig().getWitherDamageXpCooldown())) {
        return;
      }
      witherDamageCooldowns.mark(p.uuid());
      addStat(p, "nether.wither.damage", e.damage());
      xp(p, getConfig().getWitherDamageXp());
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    double oreXp = oreXp(e.block().type());
    if (oreXp > 0D) {
      PlayerRef miner = e.player();
      shouldReturnForPlayer(miner, e, () -> {
        if (XpProvenance.breakXpMultiplier(e.block()) > 0D) {
          xp(miner, e.block().center(), blockXP(e.block(), oreXp), "nether:ore");
        }
      });
      return;
    }
    if (!e.block().type().equals(WITHER_ROSE)) {
      return;
    }

    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if (XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }
      long cooldownMillis = Math.max(0L, (long) getConfig().getWitherRoseBreakCooldown() * 50L);
      if (!witherRoseCooldowns.isReady(p.uuid(), cooldownMillis)) {
        return;
      }

      witherRoseCooldowns.mark(p.uuid());
      addStat(p, "nether.roses.broken", 1);
      Loc roseLocation = e.block().location().add(.5D, .5D, .5D);
      xp(p, roseLocation, getConfig().getWitherRoseBreakXp());
      fx(roseLocation, FxPriority.TRANSITION)
          .particle(Particles.SOUL, 6, 0D, 0.1D, 0D, 0.2D, 0.02D)
          .particle(Particles.SMOKE, 4, 0D, 0.1D, 0D, 0.2D, 0.02D)
          .sound(Sounds.PARTICLE_SOUL_ESCAPE, 0.4F, 1.0F);
    });
  }

  double oreXp(Id type) {
    if (NETHER_QUARTZ_ORE.equals(type)) {
      return getConfig().getNetherQuartzOreXp();
    }
    if (NETHER_GOLD_ORE.equals(type)) {
      return getConfig().getNetherGoldOreXp();
    }
    return ANCIENT_DEBRIS.equals(type) ? getConfig().getAncientDebrisXp() : 0D;
  }

  static double netherMobKillXp(double perMaxHealth, double maxHealth) {
    return Double.isFinite(maxHealth) && maxHealth > 0D ? Math.max(0D, perMaxHealth) * maxHealth : 0D;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef dead = e.entity();
    PlayerRef p = e.killer();
    if (p == null || GolemKillCredit.isGolemKill(dead)) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      if (NETHER_MOBS.contains(dead.type())) {
        addStat(p, "nether.kills", 1);
        if (MobOrigin.paysKillReward(p, dead)) {
          xp(p, dead.location(), netherMobKillXp(getConfig().getNetherMobMaxHealthXpMultiplier(), dead.maxHealth()),
              "nether:kill");
        }
      } else if (dead.type().equals(WITHER_SKELETON)) {
        addStat(p, "nether.kills", 1);
        addStat(p, "nether.skeleton.kills", 1);
        xp(p, getConfig().getWitherSkeletonKillXp());
      } else if (dead.type().equals(WITHER)) {
        addStat(p, "nether.kills", 1);
        addStat(p, "nether.boss.kills", 1);
        xp(p, getConfig().getWitherKillXp());
        fx(dead.location().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
            .ring(Particles.SOUL, 2.2D, 20, 0.3D)
            .column(Particles.END_ROD, 8, 2.4D)
            .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sounds.ENTITY_WITHER_HURT, 0.4F, 0.7F);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p)
        || !e.entity().type().equals(WITHER)
        || e.cause() != DamageCause.ENTITY_ATTACK) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!witherAttackCooldowns.isReady(p.uuid(), getConfig().getWitherAttackXpCooldown())) {
        return;
      }
      if (CombatXp.credit(getPlayer(p).getData().getCombatXp(), getName(), p, e) <= 0D) {
        return;
      }
      witherAttackCooldowns.mark(p.uuid());
      xp(p, getConfig().getWitherAttackXp());
    });
  }

  @Override
  public boolean isEnabled() {
    return getConfig().isEnabled();
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Config extends SkillConfig {
    String skillColor = "&8";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    private boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Damage Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double witherDamageXp = 26.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between XP awards for taking wither effect damage.", impact = "Higher values reduce repeated wither damage XP frequency; lower values reward ticks more often.")
    private long witherDamageXpCooldown = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Attack Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double witherAttackXp = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between XP awards for melee-attacking a wither boss.", impact = "Higher values reduce repeated wither attack XP frequency; lower values reward hits more often.")
    private long witherAttackXpCooldown = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Kill Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double witherSkeletonKillXp = 225;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Kill Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double witherKillXp = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Rose Break Xp for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double witherRoseBreakXp = 125;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Rose Break Cooldown for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private int witherRoseBreakCooldown = 60 * 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Nether Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double challengeNetherReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Damage Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double challengeWitherDmgReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Skeleton Kill Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double challengeWitherSkelReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Wither Boss Kill Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double challengeWitherBossReward = 1000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Roses Broken Reward for the Nether skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double challengeRosesReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP per point of maximum health for killing blazes, ghasts, piglins, piglin brutes, hoglins, zoglins, magma cubes, and zombified piglins.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double netherMobMaxHealthXpMultiplier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for mining nether quartz ore.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double netherQuartzOreXp = 62.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for mining nether gold ore.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double netherGoldOreXp = 52.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP for mining ancient debris.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    private double ancientDebrisXp = 105;
  }
}
