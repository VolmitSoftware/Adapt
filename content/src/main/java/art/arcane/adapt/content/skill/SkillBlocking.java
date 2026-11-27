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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
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
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingBoon;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingFightStrike;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingFuel;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingState;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingTrigger;
import art.arcane.adapt.content.adaptation.blocking.blocks.BlockingWorkStrike;
import art.arcane.adapt.content.adaptation.blocking.upgraded.LivingWall;
import art.arcane.adapt.content.adaptation.blocking.upgraded.MirrorGuard;
import art.arcane.adapt.content.adaptation.blocking.upgraded.TemperedAegis;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
  public static final Id ID = Id.adapt("blocking");
  private final XpSource blockedHit = xpSource("blocked-hit", Text.key("adapt.xp_guide.blocking.blocked_hit"));
  private final XpSource shieldHold = xpSource("shield-hold", Text.key("adapt.xp_guide.blocking.shield_hold"));
  private static final Id SHIELD = Id.minecraft("shield");
  private static final Id IRON_BLOCK = Id.minecraft("iron_block");
  private final Cooldowns blockCooldowns = cooldowns();
  private final Map<UUID, Long> recentBlocks = playerState();


  public SkillBlocking() {
    super(ID);
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x555555));
    setInterval(5000);
    setIcon(SHIELD);
    registerAdaptation(BlockingMultiArmor.ID, BlockingMultiArmor::new);
    registerAdaptation(BlockingChainArmorer.ID, BlockingChainArmorer::new);
    registerAdaptation(BlockingSaddlecrafter.ID, BlockingSaddlecrafter::new);
    registerAdaptation(BlockingHorseArmorer.ID, BlockingHorseArmorer::new);
    registerAdaptation(BlockingCounterGuard.ID, BlockingCounterGuard::new);
    registerAdaptation(BlockingBastionStance.ID, BlockingBastionStance::new);
    registerAdaptation(BlockingMirrorBlock.ID, BlockingMirrorBlock::new);
    registerAdaptation(BlockingBulwarkBash.ID, BlockingBulwarkBash::new);
    registerAdaptation(BlockingShieldWall.ID, BlockingShieldWall::new);
    registerAdaptation(BlockingPerfectGuard.ID, BlockingPerfectGuard::new);
    registerAdaptation(BlockingTemperedGuard.ID, BlockingTemperedGuard::new);
    registerAdaptation(BlockingShieldbearersResolve.ID, BlockingShieldbearersResolve::new);
    registerAdaptation(BlockingPhalanxCrafter.ID, BlockingPhalanxCrafter::new);
    registerAdaptation(BlockingInterpose.ID, BlockingInterpose::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.blocking"), Text.key("adapt.mutated.adjective.blocking"))
        .trigger(TriggerKind.FIGHT, BlockingTrigger::new)
        .strikes(BlockingFightStrike::new, BlockingWorkStrike::new)
        .boon(BlockingBoon::new)
        .fuel(BlockingFuel::new)
        .state(BlockingState::new)
        .upgrade(MirrorGuard.ID, MirrorGuard::new,
            List.of(BlockingPerfectGuard.ID, BlockingMirrorBlock.ID, BlockingCounterGuard.ID), Set.of())
        .upgrade(LivingWall.ID, LivingWall::new,
            List.of(BlockingShieldWall.ID, BlockingInterpose.ID, BlockingBastionStance.ID), Set.of())
        .upgrade(TemperedAegis.ID, TemperedAegis::new,
            List.of(BlockingTemperedGuard.ID, BlockingShieldbearersResolve.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("item.shield.block"), Id.minecraft("item.armor.equip_iron"),
            Id.minecraft("item.armor.equip_netherite"), Id.minecraft("block.chain.step")))
        .tint(Rgb.of(0x4a68b0))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("leather_chestplate")).key("challenge_block_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Id.minecraft("chainmail_chestplate"))
            .key("challenge_block_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Id.minecraft("iron_chestplate"))
                .key("challenge_block_50k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_block_1k", "blocked.hits", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_5k", "blocked.hits", 5000, () -> getConfig().challengeBlock1kReward * 3);
    registerMilestone("challenge_block_50k", "blocked.hits", 50000, () -> getConfig().challengeBlock1kReward * 8);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate")).key("challenge_block_dmg_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_block_dmg_10k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_dmg_1k", "blocked.damage", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_dmg_10k", "blocked.damage", 10000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("arrow")).key("challenge_block_proj_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spectral_arrow"))
            .key("challenge_block_proj_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_proj_100", "blocked.projectiles", 100, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_proj_1k", "blocked.projectiles", 1000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword")).key("challenge_block_melee_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_block_melee_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_melee_500", "blocked.melee", 500, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_melee_5k", "blocked.melee", 5000, () -> getConfig().challengeBlock1kReward * 3);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_chestplate")).key("challenge_block_heavy_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_block_heavy_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_block_heavy_50", "blocked.heavy", 50, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_heavy_500", "blocked.heavy", 500, () -> getConfig().challengeBlock1kReward * 3);
    pulseLearners(this::getInterval, this::pulseShieldXp);
    registerGroup(Text.key("adapt.skill.blocking.group.guard"), List.of(Id.parseAdapt("adapt:blocking/bastion_stance"), Id.parseAdapt("adapt:blocking/shield_wall"), Id.parseAdapt("adapt:blocking/interpose"), Id.parseAdapt("adapt:blocking/tempered_guard"), Id.parseAdapt("adapt:blocking/shieldbearers_resolve"), Id.parseAdapt("adapt:blocking/perfect_guard")));
    registerGroup(Text.key("adapt.skill.blocking.group.counter"), List.of(Id.parseAdapt("adapt:blocking/counter_guard"), Id.parseAdapt("adapt:blocking/mirror_block"), Id.parseAdapt("adapt:blocking/bulwark_bash")));
    registerGroup(Text.key("adapt.skill.blocking.group.gear"), List.of(Id.parseAdapt("adapt:blocking/chainarmorer"), Id.parseAdapt("adapt:blocking/horsearmorer"), Id.parseAdapt("adapt:blocking/saddlecrafter"), Id.parseAdapt("adapt:blocking/phalanx_crafter"), Id.parseAdapt("adapt:blocking/multiarmor")));
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
      double damage = CombatXp.creditDefense(getId().toString(), p, e,
          Math.max(0D, e.blockedDamage()));
      if (damage <= 0D) {
        return;
      }
      PlayerProfile adaptPlayer = getPlayer(p);
      adaptPlayer.addStat("blocked.hits", 1);
      adaptPlayer.addStat("blocked.damage", damage);
      if (e.damager() instanceof ProjectileRef) {
        adaptPlayer.addStat("blocked.projectiles", 1);
      } else {
        adaptPlayer.addStat("blocked.melee", 1);
      }
      boolean heavy = damage > 5;
      if (heavy) {
        adaptPlayer.addStat("blocked.heavy", 1);
      }

      recentBlocks.put(p.uuid(), System.currentTimeMillis());
      blockedHit.award(p, blockedXp(getConfig().blockedDamageXpMultiplier, getConfig().blockedAttackMinXp, damage));
      blockFlash(p, heavy);
    });
  }

  private void blockFlash(PlayerRef p, boolean heavy) {
    Loc eye = p.eyeLocation();
    Vec3 look = eye.direction();
    Loc front = eye.add(look.x() * 0.6D, look.y() * 0.6D, look.z() * 0.6D);
    FxEmitter emitter = fx(front, FxPriority.COMBAT)
        .particle(Particles.blockCrack(AdaptApi.platform().host().items().blockState(IRON_BLOCK)), heavy ? 8 : 6, 0, 0, 0, 0.2D, 0.02D)
        .burst(Particles.CRIT, heavy ? 8 : 4, heavy ? 0.35D : 0.2D);
    if (heavy) {
      emitter.burst(Particles.END_ROD, 3, 0.15D)
          .chord(Sounds.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sounds.ITEM_SHIELD_BLOCK, 0.9F, 0.7F);
    } else {
      emitter.chord(Sounds.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sounds.ITEM_SHIELD_BLOCK, 0.6F, 1.1F);
    }
  }

  private void pulseShieldXp(PlayerRef player, long elapsedMillis) {
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

      double cadenceScale = (double) elapsedMillis / getInterval();
      shieldHold.awardSilent(player, passiveXp * cadenceScale);
    });
  }


  static double blockedXp(double perDamage, double minimum, double blockedDamage) {
    if (!Double.isFinite(blockedDamage) || blockedDamage <= 0D) {
      return 0D;
    }
    return Math.max(Math.max(0D, minimum), Math.max(0D, perDamage) * blockedDamage);
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "XP per point of damage your shield blocks.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockedDamageXpMultiplier = 6;
    @ConfigDoc(value = "Least XP a credited blocked hit pays.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blockedAttackMinXp = 10;
    @ConfigDoc(value = "Controls Challenge Block1k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBlock1kReward = 500;
    @ConfigDoc(value = "Controls Cooldown Delay for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @ConfigDoc(value = "Controls Passive Xp For Using Shield for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long passiveXpForUsingShield = 0;

    public Config() {
    }
  }
}
