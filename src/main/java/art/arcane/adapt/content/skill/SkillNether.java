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

import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class SkillNether extends SimpleSkill<SkillNether.Config> {
  private final Cooldowns witherRoseCooldowns = cooldowns();
  private final Cooldowns witherDamageCooldowns = cooldowns();
  private final Cooldowns witherAttackCooldowns = cooldowns();

  public SkillNether() {
    super("nether", SkillPresentation.of(SkillMessages.NETHER_NAME, SkillMessages.NETHER_ICON, SkillMessages.NETHER_DESCRIPTION));
    registerConfiguration(Config.class);
    setInterval(7425);
    setColor(C.DARK_GRAY);
    setIcon(Material.NETHER_STAR);
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
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_SKELETON_SKULL)
        .key("challenge_nether_50")
        .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "nether", "challenge_nether_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_nether_500")
            .model(CustomModel.get(Material.NETHER_STAR, "advancement", "nether", "challenge_nether_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .child(AdaptAdvancement.builder()
                .icon(Material.BEACON)
                .key("challenge_nether_5k")
                .model(CustomModel.get(Material.BEACON, "advancement", "nether", "challenge_nether_5k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_nether_50", "nether.kills", 50, () -> getConfig().getChallengeNetherReward());
    registerMilestone("challenge_nether_500", "nether.kills", 500, () -> getConfig().getChallengeNetherReward() * 2);
    registerMilestone("challenge_nether_5k", "nether.kills", 5000, () -> getConfig().getChallengeNetherReward() * 5);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_ROSE)
        .key("challenge_wither_dmg_500")
        .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "nether", "challenge_wither_dmg_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_LANTERN)
            .key("challenge_wither_dmg_5k")
            .model(CustomModel.get(Material.SOUL_LANTERN, "advancement", "nether", "challenge_wither_dmg_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_wither_dmg_500", "nether.wither.damage", 500, () -> getConfig().getChallengeWitherDmgReward());
    registerMilestone("challenge_wither_dmg_5k", "nether.wither.damage", 5000, () -> getConfig().getChallengeWitherDmgReward() * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_wither_skel_25")
        .model(CustomModel.get(Material.BONE, "advancement", "nether", "challenge_wither_skel_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WITHER_SKELETON_SKULL)
            .key("challenge_wither_skel_250")
            .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "nether", "challenge_wither_skel_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_wither_skel_25", "nether.skeleton.kills", 25, () -> getConfig().getChallengeWitherSkelReward());
    registerMilestone("challenge_wither_skel_250", "nether.skeleton.kills", 250, () -> getConfig().getChallengeWitherSkelReward() * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHER_STAR)
        .key("challenge_wither_boss_1")
        .model(CustomModel.get(Material.NETHER_STAR, "advancement", "nether", "challenge_wither_boss_1"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BEACON)
            .key("challenge_wither_boss_10")
            .model(CustomModel.get(Material.BEACON, "advancement", "nether", "challenge_wither_boss_10"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_wither_boss_1", "nether.boss.kills", 1, () -> getConfig().getChallengeWitherBossReward());
    registerMilestone("challenge_wither_boss_10", "nether.boss.kills", 10, () -> getConfig().getChallengeWitherBossReward() * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.WITHER_ROSE)
        .key("challenge_roses_10")
        .model(CustomModel.get(Material.WITHER_ROSE, "advancement", "nether", "challenge_roses_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.FLOWER_POT)
            .key("challenge_roses_100")
            .model(CustomModel.get(Material.FLOWER_POT, "advancement", "nether", "challenge_roses_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_roses_10", "nether.roses.broken", 10, () -> getConfig().getChallengeRosesReward());
    registerMilestone("challenge_roses_100", "nether.roses.broken", 100, () -> getConfig().getChallengeRosesReward() * 2);
  }

  private boolean isWitherDamageCause(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.WITHER;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (!this.isEnabled() || !(e.getEntity() instanceof Player p) || !isWitherDamageCause(e.getCause()) || e instanceof EntityDamageByBlockEvent) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!witherDamageCooldowns.isReady(p.getUniqueId(), getConfig().getWitherDamageXpCooldown())) {
        return;
      }
      witherDamageCooldowns.mark(p.getUniqueId());
      addStat(p, "nether.wither.damage", e.getDamage());
      xp(p, getConfig().getWitherDamageXp());
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (e.getBlock().getType() != Material.WITHER_ROSE) {
      return;
    }

    Player p = e.getPlayer();
    shouldReturnForPlayer(p, e, () -> {
      long cooldownMillis = Math.max(0L, (long) getConfig().getWitherRoseBreakCooldown() * 50L);
      if (!witherRoseCooldowns.isReady(p.getUniqueId(), cooldownMillis)) {
        return;
      }

      witherRoseCooldowns.mark(p.getUniqueId());
      addStat(p, "nether.roses.broken", 1);
      Location roseLocation = e.getBlock().getLocation().add(.5D, .5D, .5D);
      xp(p, roseLocation, getConfig().getWitherRoseBreakXp());
      fx(roseLocation, FxPriority.TRANSITION)
          .particle(Particle.SOUL, 6, 0D, 0.1D, 0D, 0.2D, 0.02D)
          .particle(Particles.SMOKE, 4, 0D, 0.1D, 0D, 0.2D, 0.02D)
          .sound(Sound.PARTICLE_SOUL_ESCAPE, 0.4F, 1.0F);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      if (e.getEntityType() == EntityType.WITHER_SKELETON) {
        addStat(p, "nether.kills", 1);
        addStat(p, "nether.skeleton.kills", 1);
        xp(p, getConfig().getWitherSkeletonKillXp());
      } else if (e.getEntityType() == EntityType.WITHER) {
        addStat(p, "nether.kills", 1);
        addStat(p, "nether.boss.kills", 1);
        xp(p, getConfig().getWitherKillXp());
        fx(e.getEntity().getLocation().add(0D, 1.0D, 0D), FxPriority.TRANSITION)
            .ring(Particle.SOUL, 2.2D, 20, 0.3D)
            .column(Particles.END_ROD, 8, 2.4D)
            .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sound.ENTITY_WITHER_HURT, 0.4F, 0.7F);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)
        || !(e.getEntity() instanceof Wither)
        || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      return;
    }
    shouldReturnForPlayer(p, e, () -> {
      if (!witherAttackCooldowns.isReady(p.getUniqueId(), getConfig().getWitherAttackXpCooldown())) {
        return;
      }
      witherAttackCooldowns.mark(p.getUniqueId());
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
  public static class Config {
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
  }
}
