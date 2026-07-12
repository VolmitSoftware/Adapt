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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptPlayer;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class SkillBlocking extends SimpleSkill<SkillBlocking.Config> {
  private static final BlockData IRON_BLOCK_DATA = Material.IRON_BLOCK.createBlockData();
  private final Cooldowns blockCooldowns = cooldowns();
  private final SkillOwnerPulse.Registration ownerPulse;

  public SkillBlocking() {
    super("blocking", Localizer.dLocalize("skill.blocking.icon"));
    registerConfiguration(Config.class);
    setColor(C.DARK_GRAY);
    setDescription(Localizer.dLocalize("skill.blocking.description"));
    setDisplayName(Localizer.dLocalize("skill.blocking.name"));
    setInterval(5000);
    setIcon(Material.SHIELD);
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
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_CHESTPLATE).key("challenge_block_1k")
        .model(CustomModel.get(Material.LEATHER_CHESTPLATE, "advancement", "blocking", "challenge_block_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
            .icon(Material.CHAINMAIL_CHESTPLATE)
            .key("challenge_block_5k")
            .model(CustomModel.get(Material.CHAINMAIL_CHESTPLATE, "advancement", "blocking", "challenge_block_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                .icon(Material.IRON_CHESTPLATE)
                .key("challenge_block_50k")
                .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "blocking", "challenge_block_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build())
            .build())
        .build());
    registerMilestone("challenge_block_1k", "blocked.hits", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_5k", "blocked.hits", 5000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_50k", "blocked.hits", 50000, () -> getConfig().challengeBlock5kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE).key("challenge_block_dmg_1k")
        .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "blocking", "challenge_block_dmg_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_CHESTPLATE)
            .key("challenge_block_dmg_10k")
            .model(CustomModel.get(Material.NETHERITE_CHESTPLATE, "advancement", "blocking", "challenge_block_dmg_10k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_block_dmg_1k", "blocked.damage", 1000, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_dmg_10k", "blocked.damage", 10000, () -> getConfig().challengeBlock5kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW).key("challenge_block_proj_100")
        .model(CustomModel.get(Material.ARROW, "advancement", "blocking", "challenge_block_proj_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPECTRAL_ARROW)
            .key("challenge_block_proj_1k")
            .model(CustomModel.get(Material.SPECTRAL_ARROW, "advancement", "blocking", "challenge_block_proj_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_block_proj_100", "blocked.projectiles", 100, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_proj_1k", "blocked.projectiles", 1000, () -> getConfig().challengeBlock5kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD).key("challenge_block_melee_500")
        .model(CustomModel.get(Material.IRON_SWORD, "advancement", "blocking", "challenge_block_melee_500"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_block_melee_5k")
            .model(CustomModel.get(Material.NETHERITE_SWORD, "advancement", "blocking", "challenge_block_melee_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_block_melee_500", "blocked.melee", 500, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_melee_5k", "blocked.melee", 5000, () -> getConfig().challengeBlock5kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_CHESTPLATE).key("challenge_block_heavy_50")
        .model(CustomModel.get(Material.DIAMOND_CHESTPLATE, "advancement", "blocking", "challenge_block_heavy_50"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_CHESTPLATE)
            .key("challenge_block_heavy_500")
            .model(CustomModel.get(Material.NETHERITE_CHESTPLATE, "advancement", "blocking", "challenge_block_heavy_500"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_block_heavy_50", "blocked.heavy", 50, () -> getConfig().challengeBlock1kReward);
    registerMilestone("challenge_block_heavy_500", "blocked.heavy", 500, () -> getConfig().challengeBlock5kReward);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseShieldXp);
  }

  private boolean beginCooldown(Player p) {
    if (!blockCooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return false;
    }
    blockCooldowns.mark(p.getUniqueId());
    return true;
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p) || !p.isBlocking()) {
      return;
    }

    shouldReturnForPlayer(p, e, () -> {
      AdaptPlayer adaptPlayer = getPlayer(p);
      adaptPlayer.getData().addStat("blocked.hits", 1);
      adaptPlayer.getData().addStat("blocked.damage", e.getDamage());
      if (e.getDamager() instanceof Projectile) {
        adaptPlayer.getData().addStat("blocked.projectiles", 1);
      } else {
        adaptPlayer.getData().addStat("blocked.melee", 1);
      }
      boolean heavy = e.getDamage() > 5;
      if (heavy) {
        adaptPlayer.getData().addStat("blocked.heavy", 1);
      }

      if (beginCooldown(p)) {
        xp(p, getConfig().xpOnBlockedAttack);
        blockFlash(p, heavy);
      }
    });
  }

  private void blockFlash(Player p, boolean heavy) {
    Location eye = p.getEyeLocation();
    Vector look = eye.getDirection();
    Location front = eye.add(look.getX() * 0.6D, look.getY() * 0.6D, look.getZ() * 0.6D);
    FxEmitter emitter = fx(front, FxPriority.COMBAT)
        .particle(Particles.BLOCK_CRACK, heavy ? 8 : 6, 0, 0, 0, 0.2D, 0.02D, IRON_BLOCK_DATA)
        .burst(Particles.CRIT_MAGIC, heavy ? 8 : 4, heavy ? 0.35D : 0.2D);
    if (heavy) {
      emitter.burst(Particles.END_ROD, 3, 0.15D)
          .chord(Sound.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sound.ITEM_SHIELD_BLOCK, 0.9F, 0.7F);
    } else {
      emitter.chord(Sound.BLOCK_IRON_DOOR_CLOSE, 0.5F, 0.77F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 0.77F, Sound.ITEM_SHIELD_BLOCK, 0.6F, 1.1F);
    }
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    super.unregister();
  }

  private void pulseShieldXp(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> {
      long passiveXp = getConfig().passiveXpForUsingShield;
      if (passiveXp <= 0) {
        return;
      }
      if (player.getInventory().getItemInOffHand().getType() != Material.SHIELD
          && player.getInventory().getItemInMainHand().getType() != Material.SHIELD) {
        return;
      }

      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      xpSilent(player, passiveXp * cadenceScale, "blocking:shield-hold");
    });
  }


  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&8";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Blocked Attack for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnBlockedAttack = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Block1k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBlock1kReward = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Block5k Reward for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeBlock5kReward = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Passive Xp For Using Shield for the Blocking skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long passiveXpForUsingShield = 0;
  }
}
