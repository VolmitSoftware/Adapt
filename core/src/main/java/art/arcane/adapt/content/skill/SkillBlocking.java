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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.skill.AdaptationGroup;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.content.adaptation.blocking.BlockingBastionStance;
import art.arcane.adapt.content.adaptation.blocking.BlockingBulwarkBash;
import art.arcane.adapt.content.adaptation.blocking.BlockingChainArmorer;
import art.arcane.adapt.content.adaptation.blocking.BlockingCounterGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingHorseArmorer;
import art.arcane.adapt.content.adaptation.blocking.BlockingInterpose;
import art.arcane.adapt.content.adaptation.blocking.BlockingMirrorBlock;
import art.arcane.adapt.content.adaptation.blocking.BlockingMultiArmor;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingPhalanxCrafter;
import art.arcane.adapt.content.adaptation.blocking.BlockingSaddlecrafter;
import art.arcane.adapt.content.adaptation.blocking.BlockingShieldWall;
import art.arcane.adapt.content.adaptation.blocking.BlockingShieldbearersResolve;
import art.arcane.adapt.content.adaptation.blocking.BlockingTemperedGuard;
import art.arcane.adapt.content.adaptation.blocking.upgraded.LivingWall;
import art.arcane.adapt.content.adaptation.blocking.upgraded.MirrorGuard;
import art.arcane.adapt.content.adaptation.blocking.upgraded.TemperedAegis;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingTrigger;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingFightStrike;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingWorkStrike;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingBoon;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingFuel;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingState;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.GroupMessages;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final Id IRON_BLOCK = Id.minecraft("iron_block");
  private final Cooldowns blockCooldowns = cooldowns();
  private final SkillOwnerPulse.Registration ownerPulse;
  private final Map<UUID, Long> recentBlocks = playerState();

  static final List<AdaptationGroup> GROUPS = List.of(
      AdaptationGroup.of(GroupMessages.BLOCKING_GUARD, "blocking-bastion-stance", "blocking-shield-wall",
          "blocking-interpose", "blocking-tempered-guard", "blocking-shieldbearers-resolve", "blocking-perfect-guard"),
      AdaptationGroup.of(GroupMessages.BLOCKING_COUNTER, "blocking-counter-guard", "blocking-mirror-block",
          "blocking-bulwark-bash"),
      AdaptationGroup.of(GroupMessages.BLOCKING_GEAR, "blocking-chainarmorer", "blocking-horsearmorer",
          "blocking-saddlecrafter", "blocking-phalanx-crafter", "blocking-multiarmor")
  );

  public SkillBlocking() {
    super("blocking", SkillPresentation.of(SkillMessages.BLOCKING_NAME, SkillMessages.BLOCKING_ICON, SkillMessages.BLOCKING_DESCRIPTION,
        SkillMessages.BLOCKING_QUIP, SkillMessages.BLOCKING_UNLOCK_QUIP_1, SkillMessages.BLOCKING_UNLOCK_QUIP_2));
    registerConfiguration(Config.class);
    setColor(C.DARK_GRAY);
    setInterval(5000);
    setIcon(SHIELD);
    registerAdaptation(new BlockingMultiArmor());
    registerAdaptation(new BlockingChainArmorer());
    registerAdaptation(new BlockingSaddlecrafter());
    registerAdaptation(new BlockingHorseArmorer());
    registerAdaptation(new BlockingCounterGuard());
    registerAdaptation(new BlockingBastionStance());
    registerAdaptation(new BlockingMirrorBlock());
    registerAdaptation(new BlockingBulwarkBash());
    registerAdaptation(new BlockingShieldWall());
    registerAdaptation(new BlockingPerfectGuard());
    registerAdaptation(new BlockingTemperedGuard());
    registerAdaptation(new BlockingShieldbearersResolve());
    registerAdaptation(new BlockingPhalanxCrafter());
    registerAdaptation(new BlockingInterpose());
    registerUpgrades(new MirrorGuard(), new LivingWall(), new TemperedAegis());
    registerBlocks(new BlockingTrigger(), new BlockingFightStrike(), new BlockingWorkStrike(), new BlockingBoon(),
        new BlockingFuel(), new BlockingState());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_chestplate")).key("challenge_block_1k")
        .model(CustomModel.get(Id.minecraft("leather_chestplate"), "advancement", "blocking", "challenge_block_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_chestplate"))
            .key("challenge_block_5k")
            .model(CustomModel.get(Id.minecraft("chainmail_chestplate"), "advancement", "blocking", "challenge_block_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_chestplate"))
                .key("challenge_block_50k")
                .model(CustomModel.get(Id.minecraft("iron_chestplate"), "advancement", "blocking", "challenge_block_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_block_1k", "blocked.hits", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_5k", "blocked.hits", 5000, () -> getConfig().challengeBlock1kReward * 3);
    registerMilestone("challenge_block_50k", "blocked.hits", 50000, () -> getConfig().challengeBlock1kReward * 8);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate")).key("challenge_block_dmg_1k")
        .model(CustomModel.get(Id.minecraft("iron_chestplate"), "advancement", "blocking", "challenge_block_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_block_dmg_10k")
            .model(CustomModel.get(Id.minecraft("netherite_chestplate"), "advancement", "blocking", "challenge_block_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_dmg_1k", "blocked.damage", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_dmg_10k", "blocked.damage", 10000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow")).key("challenge_block_proj_100")
        .model(CustomModel.get(Id.minecraft("arrow"), "advancement", "blocking", "challenge_block_proj_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_block_proj_1k")
            .model(CustomModel.get(Id.minecraft("spectral_arrow"), "advancement", "blocking", "challenge_block_proj_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_proj_100", "blocked.projectiles", 100, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_proj_1k", "blocked.projectiles", 1000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword")).key("challenge_block_melee_500")
        .model(CustomModel.get(Id.minecraft("iron_sword"), "advancement", "blocking", "challenge_block_melee_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_block_melee_5k")
            .model(CustomModel.get(Id.minecraft("netherite_sword"), "advancement", "blocking", "challenge_block_melee_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_melee_500", "blocked.melee", 500, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_melee_5k", "blocked.melee", 5000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_chestplate")).key("challenge_block_heavy_50")
        .model(CustomModel.get(Id.minecraft("diamond_chestplate"), "advancement", "blocking", "challenge_block_heavy_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_block_heavy_500")
            .model(CustomModel.get(Id.minecraft("netherite_chestplate"), "advancement", "blocking", "challenge_block_heavy_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_heavy_50", "blocked.heavy", 50, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_heavy_500", "blocked.heavy", 500, () -> getConfig().challengeBlock1kReward * 3);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseShieldXp);
  }

  @Override
  public List<AdaptationGroup> getAdaptationGroups() {
    return GROUPS;
  }

  private boolean beginCooldown(PlayerRef p) {
    if (!blockCooldowns.isReady(p.uuid(), getConfig().cooldownDelay)) {
      return false;
    }
    blockCooldowns.mark(p.uuid());
    return true;
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !p.isBlocking()) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      if (!beginCooldown(p)) {
        return;
      }
      double damage = CombatXp.creditDefense(getPlayer(p).getData().getCombatXp(), getName(), p, e,
          Math.max(0D, e.blockedDamage()));
      if (damage <= 0D) {
        return;
      }
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("blocked.hits", 1);
      adaptPlayer.getData().addStat("blocked.damage", damage);
      if (e.damager() instanceof ProjectileRef) {
        adaptPlayer.getData().addStat("blocked.projectiles", 1);
      } else {
        adaptPlayer.getData().addStat("blocked.melee", 1);
      }
      boolean heavy = damage > 5;
      if (heavy) {
        adaptPlayer.getData().addStat("blocked.heavy", 1);
      }

      recentBlocks.put(p.uuid(), System.currentTimeMillis());
      xp(p, blockedXp(getConfig().blockedDamageXpMultiplier, getConfig().blockedAttackMinXp, damage), "blocking:blocked-hit");
      blockFlash(p, heavy);
    });
  }

  private void blockFlash(PlayerRef p, boolean heavy) {
    Loc eye = p.eyeLocation();
    Vec3 look = eye.direction();
    Loc front = eye.add(look.x() * 0.6D, look.y() * 0.6D, look.z() * 0.6D);
    FxEmitter emitter = fx(front, FxPriority.COMBAT)
        .particle(Particles.blockCrack(AdaptCore.platform().host().items().blockState(IRON_BLOCK)), heavy ? 8 : 6, 0, 0, 0, 0.2D, 0.02D)
        .burst(Particles.CRIT_MAGIC, heavy ? 8 : 4, heavy ? 0.35D : 0.2D);
    if (heavy) {
      emitter.burst(Particles.END_ROD, 3, 0.15D)
          .chord(Sounds.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sounds.ITEM_SHIELD_BLOCK, 0.9F, 0.7F);
    } else {
      emitter.chord(Sounds.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sounds.ITEM_SHIELD_BLOCK, 0.6F, 1.1F);
    }
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    super.unregister();
  }

  private void pulseShieldXp(AdaptPlayer adaptPlayer, PlayerRef player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> {
      Long blockedAt = recentBlocks.get(player.uuid());
      if (!player.isBlocking() || blockedAt == null || System.currentTimeMillis() - blockedAt > 2500L) {
        return;
      }
      long passiveXp = getConfig().passiveXpForUsingShield;
      if (passiveXp <= 0) {
        return;
      }
      if (!SHIELD.equals(player.inventory().offHand().type())
          && !SHIELD.equals(player.inventory().mainHand().type())) {
        return;
      }

      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      xpSilent(player, passiveXp * cadenceScale, "blocking:shield-hold");
    });
  }


  static double blockedXp(double perDamage, double minimum, double blockedDamage) {
    if (!Double.isFinite(blockedDamage) || blockedDamage <= 0D) {
      return 0D;
    }
    return Math.max(Math.max(0D, minimum), Math.max(0D, perDamage) * blockedDamage);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&8";
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP per point of damage your shield blocks.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockedDamageXpMultiplier = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Least XP a credited blocked hit pays.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockedAttackMinXp = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Block1k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBlock1kReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Passive Xp For Using Shield for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long passiveXpForUsingShield = 0;
  }
}
