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
import art.arcane.adapt.api.skill.SkillOwnerPulse;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneBrineSkin;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneDeepSalvager;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishWhisperer;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishersFantasy;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneHydroJet;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneInkVeil;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneOxygen;
import art.arcane.adapt.content.adaptation.seaborrne.SeabornePressureDiver;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSpeed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTidecaller;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTridentMastery;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesMiningSpeed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesVision;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
  private static final long WATER_MOVEMENT_GRACE_MILLIS = 2500L;

  private final Cooldowns underwaterBlockCooldowns = cooldowns();
  private final Cooldowns drownedDamageCooldowns = cooldowns();
  private final Cooldowns tridentDamageCooldowns = cooldowns();
  private final Cooldowns fishCooldowns = cooldowns();
  private final Map<UUID, Boolean> swimming = playerState();
  private final Map<UUID, Long> lastWaterMovementAt = playerState();
  private final SkillOwnerPulse.Registration ownerPulse;

  public SkillSeaborne() {
    super("seaborne", SkillPresentation.of(SkillMessages.SEABORNE_NAME, SkillMessages.SEABORNE_ICON, SkillMessages.SEABORNE_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.BLUE);
    setInterval(2120);
    setIcon(Material.TRIDENT);
    registerAdaptation(new SeaborneOxygen());
    registerAdaptation(new SeaborneSpeed());
    registerAdaptation(new SeaborneFishersFantasy());
    registerAdaptation(new SeaborneTurtlesVision());
    registerAdaptation(new SeaborneTurtlesMiningSpeed());
    registerAdaptation(new SeaborneTidecaller());
    registerAdaptation(new SeabornePressureDiver());
    registerAdaptation(new SeaborneCoralGardener());
    registerAdaptation(new SeaborneDeepSalvager());
    registerAdaptation(new SeaborneInkVeil());
    registerAdaptation(new SeaborneTridentMastery());
    registerAdaptation(new SeaborneFishWhisperer());
    registerAdaptation(new SeaborneHydroJet());
    registerAdaptation(new SeaborneBrineSkin());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TURTLE_HELMET)
        .key("challenge_swim_1nm")
        .model(CustomModel.get(Material.TURTLE_HELMET, "advancement", "seaborne", "challenge_swim_1nm"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.HEART_OF_THE_SEA)
            .key("challenge_swim_5k")
            .model(CustomModel.get(Material.HEART_OF_THE_SEA, "advancement", "seaborne", "challenge_swim_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Material.TRIDENT)
                .key("challenge_swim_20k")
                .model(CustomModel.get(Material.TRIDENT, "advancement", "seaborne", "challenge_swim_20k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_swim_1nm", "move.swim", 1852, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_swim_5k", "move.swim", 5000, () -> getConfig().challengeSwim5kReward);
    registerMilestone("challenge_swim_20k", "move.swim", 20000, () -> getConfig().challengeSwim20kReward);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FISHING_ROD)
        .key("challenge_fish_25")
        .model(CustomModel.get(Material.FISHING_ROD, "advancement", "seaborne", "challenge_fish_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.TROPICAL_FISH)
            .key("challenge_fish_250")
            .model(CustomModel.get(Material.TROPICAL_FISH, "advancement", "seaborne", "challenge_fish_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_fish_25", "seaborne.fish.caught", 25, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_fish_250", "seaborne.fish.caught", 250, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ROTTEN_FLESH)
        .key("challenge_drowned_25")
        .model(CustomModel.get(Material.ROTTEN_FLESH, "advancement", "seaborne", "challenge_drowned_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.TRIDENT)
            .key("challenge_drowned_250")
            .model(CustomModel.get(Material.TRIDENT, "advancement", "seaborne", "challenge_drowned_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_drowned_25", "seaborne.drowned.kills", 25, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_drowned_250", "seaborne.drowned.kills", 250, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.PRISMARINE_SHARD)
        .key("challenge_guardian_10")
        .model(CustomModel.get(Material.PRISMARINE_SHARD, "advancement", "seaborne", "challenge_guardian_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SEA_LANTERN)
            .key("challenge_guardian_100")
            .model(CustomModel.get(Material.SEA_LANTERN, "advancement", "seaborne", "challenge_guardian_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_guardian_10", "seaborne.guardian.kills", 10, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_guardian_100", "seaborne.guardian.kills", 100, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.PRISMARINE)
        .key("challenge_underwater_blocks_100")
        .model(CustomModel.get(Material.PRISMARINE, "advancement", "seaborne", "challenge_underwater_blocks_100"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.CONDUIT)
            .key("challenge_underwater_blocks_1k")
            .model(CustomModel.get(Material.CONDUIT, "advancement", "seaborne", "challenge_underwater_blocks_1k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_underwater_blocks_100", "seaborne.underwater.blocks", 100, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_underwater_blocks_1k", "seaborne.underwater.blocks", 1000, () -> getConfig().challengeSwim1nmReward * 2);
    ownerPulse = SkillOwnerPulse.register(this, this::getInterval, this::pulseSwimmingXp);
  }

  @Override
  public void unregister() {
    ownerPulse.unregister();
    swimming.clear();
    lastWaterMovementAt.clear();
    super.unregister();
  }

  private void pulseSwimmingXp(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis) {
    shouldReturnForPlayer(player, () -> {
      UUID playerId = player.getUniqueId();
      boolean waterBreathing = hasWaterBreathingEffect(player);
      boolean recentWaterMovement = waterBreathing && isRecentWaterMovement(
          lastWaterMovementAt.get(playerId),
          System.currentTimeMillis(),
          WATER_MOVEMENT_GRACE_MILLIS);
      boolean qualifies = qualifiesForPassiveSwimXp(
          player.isInWater(),
          player.isSwimming(),
          player.getRemainingAir(),
          player.getMaximumAir(),
          waterBreathing,
          recentWaterMovement);
      boolean wasSwimming = swimming.getOrDefault(playerId, false);
      if (!qualifies) {
        if (wasSwimming) {
          swimming.remove(playerId);
        }
        return;
      }

      double cadenceScale = (double) elapsedMillis / cadenceMillis;
      double xpAmount = passiveSwimXp(
          getConfig().swimXP,
          cadenceScale,
          waterBreathing,
          getConfig().waterBreathingSwimXpBonusMultiplier);
      xpSilent(player, xpAmount, "seaborne:swim");
      if (!wasSwimming) {
        swimming.put(playerId, true);
        fx(player.getLocation(), FxPriority.AMBIENT)
            .column(Particle.END_ROD, 4, 1.0D)
            .particle(Particle.BUBBLE, 4, 0D, 0.5D, 0D, 0.3D, 0.02D)
            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e instanceof PlayerTeleportEvent || !hasPositionChanged(e)) {
      return;
    }

    Player player = e.getPlayer();
    if (player.isInWater() && hasWaterBreathingEffect(player)) {
      lastWaterMovementAt.put(player.getUniqueId(), System.currentTimeMillis());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerFishEvent e) {
    Player p = e.getPlayer();
    shouldReturnForPlayer(e.getPlayer(), e, () -> {
      double amount;
      if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
        addStat(p, "seaborne.fish.caught", 1);
        amount = getConfig().fishCaughtXp;
      } else if (e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
        amount = getConfig().entityCaughtXp;
      } else {
        return;
      }
      if (!fishCooldowns.isReady(p.getUniqueId(), getConfig().fishXpCooldown)) {
        return;
      }
      fishCooldowns.mark(p.getUniqueId());
      xp(p, amount);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    shouldReturnForPlayer(e.getPlayer(), e, () -> {
      if (!p.isSwimming() && !p.isInWater()) {
        return;
      }

      if (!underwaterBlockCooldowns.isReady(p.getUniqueId(), getConfig().seaPickleCooldown)) {
        return;
      }

      underwaterBlockCooldowns.mark(p.getUniqueId());
      addStat(p, "seaborne.underwater.blocks", 1);
      if (e.getBlock().getType().equals(Material.SEA_PICKLE) && p.isSwimming() && p.getRemainingAir() < p.getMaximumAir()) { // BECAUSE I LIKE PICKLES
        xpSilent(p, 10, "seaborne:sea-pickle");
      } else {
        xpSilent(p, 3, "seaborne:underwater-block");
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      boolean seaKill = false;
      if (e.getEntityType() == EntityType.DROWNED) {
        addStat(p, "seaborne.drowned.kills", 1);
        seaKill = true;
      } else if (e.getEntityType() == EntityType.GUARDIAN || e.getEntityType() == EntityType.ELDER_GUARDIAN) {
        addStat(p, "seaborne.guardian.kills", 1);
        seaKill = true;
      }

      if (seaKill) {
        fx(e.getEntity().getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .particle(Particle.SPLASH, 8, 0D, 0D, 0D, 0.3D, 0.08D)
            .particle(Particle.CRIT, 5, 0D, 0D, 0D, 0.3D, 0.05D)
            .dustBurst(4, 0.3D, 1.0F)
            .chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.5F, 1.2F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof LivingEntity entity))
      return;

    if (e.getEntity().getType() == EntityType.DROWNED && e.getDamager() instanceof Player p) {
      shouldReturnForPlayer(p, e, () -> {
        if (!drownedDamageCooldowns.isReady(p.getUniqueId(), getConfig().drownedDamageXpCooldown)) {
          return;
        }
        drownedDamageCooldowns.mark(p.getUniqueId());
        xp(p, getConfig().damagedrownxpmultiplier * Math.min(e.getDamage(), getBaseHealth(entity)));
      });
    } else if (e.getDamager().getType() == EntityType.TRIDENT) {
      org.bukkit.projectiles.ProjectileSource shooter = ((Trident) e.getDamager()).getShooter();
      if (shooter instanceof Player p) {
        shouldReturnForPlayer(p, e, () -> grantTridentDamageXp(p, e.getDamage(), entity));
      }
    } else if (e.getDamager() instanceof Player p) {
      if (p.getInventory().getItemInMainHand().getType().equals(Material.TRIDENT)) {
        shouldReturnForPlayer(p, e, () -> grantTridentDamageXp(p, e.getDamage(), entity));
      }
    }
  }

  private void grantTridentDamageXp(Player p, double damage, LivingEntity entity) {
    if (!tridentDamageCooldowns.isReady(p.getUniqueId(), getConfig().tridentDamageXpCooldown)) {
      return;
    }
    tridentDamageCooldowns.mark(p.getUniqueId());
    xp(p, getConfig().tridentxpmultiplier * Math.min(damage, getBaseHealth(entity)));
  }

  private double getBaseHealth(LivingEntity entity) {
    IAttribute attribute = Version.get().getAttribute(entity, Attributes.MAX_HEALTH);
    return attribute == null ? 0 : attribute.getBaseValue();
  }

  static boolean qualifiesForPassiveSwimXp(
      boolean inWater,
      boolean swimming,
      int remainingAir,
      int maximumAir,
      boolean waterBreathing,
      boolean recentWaterMovement) {
    if (waterBreathing) {
      return inWater && recentWaterMovement;
    }
    return (inWater || swimming) && remainingAir < maximumAir;
  }

  static boolean isRecentWaterMovement(Long lastMovementAt, long now, long graceMillis) {
    return lastMovementAt != null
        && graceMillis >= 0L
        && now >= lastMovementAt
        && now - lastMovementAt <= graceMillis;
  }

  static double passiveSwimXp(double baseXp, double cadenceScale, boolean waterBreathing, double bonusMultiplier) {
    double safeBonusMultiplier = Double.isFinite(bonusMultiplier) ? Math.max(0D, bonusMultiplier) : 0D;
    double multiplier = waterBreathing ? 1D + safeBonusMultiplier : 1D;
    return baseXp * cadenceScale * multiplier;
  }

  static boolean hasWaterBreathingEffect(boolean waterBreathing, boolean conduitPower) {
    return waterBreathing || conduitPower;
  }

  private boolean hasWaterBreathingEffect(Player player) {
    return hasWaterBreathingEffect(
        player.hasPotionEffect(PotionEffectType.WATER_BREATHING),
        player.hasPotionEffect(PotionEffectType.CONDUIT_POWER));
  }

  private boolean hasPositionChanged(PlayerMoveEvent event) {
    return event.getTo() != null
        && event.getFrom().getWorld() == event.getTo().getWorld()
        && (event.getFrom().getX() != event.getTo().getX()
        || event.getFrom().getY() != event.getTo().getY()
        || event.getFrom().getZ() != event.getTo().getZ());
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sea Pickle Cooldown for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long seaPickleCooldown = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between XP awards for damaging drowned.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    public long drownedDamageXpCooldown = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between XP awards for trident damage.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    public long tridentDamageXpCooldown = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP awarded for catching a fish.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double fishCaughtXp = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP awarded for reeling in an entity.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double entityCaughtXp = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between fishing XP awards.", impact = "Higher values reduce AFK fishing XP frequency; lower values reward catches more often.")
    public long fishXpCooldown = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tridentxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double tridentxpmultiplier = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damagedrownxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagedrownxpmultiplier = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&9";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim1nm Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim1nmReward = 750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim5k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim5kReward = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Swim20k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim20kReward = 3750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Swim XP for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double swimXP = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional passive swimming XP multiplier for players actively moving in water with Water Breathing or Conduit Power.", impact = "1.0 adds one base swim XP award for 2x total; 0 disables the bonus while retaining the base moving-swimmer award.")
    double waterBreathingSwimXpBonusMultiplier = 1.0;
  }
}
