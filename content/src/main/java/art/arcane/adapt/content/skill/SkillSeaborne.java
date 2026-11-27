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
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneBrineSkin;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneDeepSalvager;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishWhisperer;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishersFantasy;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneHydroJet;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneInkVeil;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneOxygen;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneRainFed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneSpeed;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneStormStrike;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTidalWard;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTidecaller;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTridentMastery;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesMiningSpeed;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneBoon;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneFightStrike;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneFuel;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneState;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneTrigger;
import art.arcane.adapt.content.adaptation.seaborrne.blocks.SeaborneWorkStrike;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.DeepFortune;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.Leviathan;
import art.arcane.adapt.content.adaptation.seaborrne.upgraded.Maelstrom;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SkillSeaborne extends SimpleSkill<SkillSeaborne.Config> {
  public static final Id ID = Id.adapt("seaborne");
  private final XpSource swim = xpSource("swim", Text.key("adapt.xp_guide.seaborne.swim"));
  private final XpSource fish = xpSource("fish", Text.key("adapt.xp_guide.seaborne.fish"));
  private final XpSource seaPickle = xpSource("sea-pickle", Text.key("adapt.xp_guide.seaborne.underwater"));
  private final XpSource underwaterBlock = xpSource("underwater-block", Text.key("adapt.xp_guide.seaborne.underwater"));
  private final XpSource seaCreatureKill = xpSource("sea-kill", Text.key("adapt.xp_guide.seaborne.kill"));
  private final XpSource drownedHit = xpSource("drowned-hit", Text.key("adapt.xp_guide.seaborne.hit"));
  private final XpSource tridentHit = xpSource("trident-hit", Text.key("adapt.xp_guide.seaborne.hit"));
  private static final long WATER_MOVEMENT_GRACE_MILLIS = 2500L;
  private static final Id TRIDENT = Id.minecraft("trident");
  private static final Id DROWNED = Id.minecraft("drowned");
  private static final Id GUARDIAN = Id.minecraft("guardian");
  private static final Id ELDER_GUARDIAN = Id.minecraft("elder_guardian");
  private static final Id SEA_PICKLE = Id.minecraft("sea_pickle");
  private static final Id WATER_BREATHING = Id.minecraft("water_breathing");
  private static final Id CONDUIT_POWER = Id.minecraft("conduit_power");
  private static final double MINIMUM_SWIM_DISPLACEMENT = 0.5D;
  private static final Set<Id> TREASURE = Set.of(Id.minecraft("enchanted_book"), Id.minecraft("name_tag"),
      Id.minecraft("nautilus_shell"), Id.minecraft("saddle"));
  private static final Set<Id> ENCHANTABLE_TREASURE = Set.of(Id.minecraft("bow"), Id.minecraft("fishing_rod"));
  private static final Set<Id> SEA_CREATURES = Set.of(DROWNED, GUARDIAN, ELDER_GUARDIAN, Id.minecraft("squid"),
      Id.minecraft("glow_squid"), Id.minecraft("dolphin"), Id.minecraft("turtle"), Id.minecraft("axolotl"),
      Id.minecraft("cod"), Id.minecraft("salmon"), Id.minecraft("pufferfish"), Id.minecraft("tropical_fish"),
      Id.minecraft("tadpole"), Id.minecraft("nautilus"), Id.minecraft("zombie_nautilus"));

  private final Cooldowns underwaterBlockCooldowns = cooldowns();
  private final Cooldowns drownedDamageCooldowns = cooldowns();
  private final Cooldowns tridentDamageCooldowns = cooldowns();
  private final Cooldowns fishCooldowns = cooldowns();
  private final Map<UUID, Boolean> swimming = playerState();
  private final Map<UUID, Long> lastWaterMovementAt = playerState();
  private final Map<UUID, Double> swimDistance = playerState();


  public SkillSeaborne() {
    super(ID);
    xpNote(Text.key("adapt.xp_guide.farmed_mobs"));
    xpNote(Text.key("adapt.xp_guide.placed_blocks"));
    registerConfiguration(Config.class);
    setColor(Rgb.of(0x5555FF));
    setInterval(2120);
    setIcon(Id.minecraft("trident"));
    registerAdaptation(SeaborneOxygen.ID, SeaborneOxygen::new);
    registerAdaptation(SeaborneSpeed.ID, SeaborneSpeed::new);
    registerAdaptation(SeaborneFishersFantasy.ID, SeaborneFishersFantasy::new);
    registerAdaptation(SeaborneTurtlesMiningSpeed.ID, SeaborneTurtlesMiningSpeed::new);
    registerAdaptation(SeaborneTidecaller.ID, SeaborneTidecaller::new);
    registerAdaptation(SeaborneTidalWard.ID, SeaborneTidalWard::new);
    registerAdaptation(SeaborneCoralGardener.ID, SeaborneCoralGardener::new);
    registerAdaptation(SeaborneDeepSalvager.ID, SeaborneDeepSalvager::new);
    registerAdaptation(SeaborneInkVeil.ID, SeaborneInkVeil::new);
    registerAdaptation(SeaborneTridentMastery.ID, SeaborneTridentMastery::new);
    registerAdaptation(SeaborneFishWhisperer.ID, SeaborneFishWhisperer::new);
    registerAdaptation(SeaborneHydroJet.ID, SeaborneHydroJet::new);
    registerAdaptation(SeaborneBrineSkin.ID, SeaborneBrineSkin::new);
    registerAdaptation(SeaborneStormStrike.ID, SeaborneStormStrike::new);
    registerAdaptation(SeaborneRainFed.ID, SeaborneRainFed::new);
    registerMutationKit(MutationKit.builder()
        .words(Text.key("adapt.mutated.noun.seaborne"), Text.key("adapt.mutated.adjective.seaborne"))
        .trigger(TriggerKind.EITHER, SeaborneTrigger::new)
        .strikes(SeaborneFightStrike::new, SeaborneWorkStrike::new)
        .boon(SeaborneBoon::new)
        .fuel(SeaborneFuel::new)
        .state(SeaborneState::new)
        .toolUpgrade(Leviathan.ID, Leviathan::new,
            List.of(SeaborneOxygen.ID, SeaborneSpeed.ID, SeaborneTidalWard.ID, SeaborneBrineSkin.ID, SeaborneRainFed.ID), Set.of(SeaborneTidalWard.ID, SeaborneRainFed.ID))
        .upgrade(Maelstrom.ID, Maelstrom::new,
            List.of(SeaborneTidecaller.ID, SeaborneHydroJet.ID, SeaborneTridentMastery.ID, SeaborneStormStrike.ID), Set.of(SeaborneStormStrike.ID))
        .upgrade(DeepFortune.ID, DeepFortune::new,
            List.of(SeaborneFishersFantasy.ID, SeaborneFishWhisperer.ID, SeaborneDeepSalvager.ID), Set.of())
        .sounds(SoundPalette.of(Id.minecraft("entity.dolphin.attack"), Id.minecraft("entity.fishing_bobber.retrieve"),
            Id.minecraft("entity.generic.swim"), Id.minecraft("entity.puffer_fish.blow_up")))
        .tint(Rgb.of(0x6ab8f0))
        // Hydro Jet's double-tap preference; Tidecaller's and Maelstrom's swings.
        .reserve(BarterGesture.DOUBLE_SNEAK, BarterGesture.SNEAK_LEFT_CLICK)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("turtle_helmet"))
        .key("challenge_swim_1nm")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("heart_of_the_sea"))
            .key("challenge_swim_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .child(AdaptAdvancement.builder()
                .icon(Id.minecraft("trident"))
                .key("challenge_swim_20k")
                .frame(AdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_swim_1nm", "move.swim", 1852, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_swim_5k", "move.swim", 5000, () -> getConfig().challengeSwim5kReward);
    registerMilestone("challenge_swim_20k", "move.swim", 20000, () -> getConfig().challengeSwim20kReward);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("fishing_rod"))
        .key("challenge_fish_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tropical_fish"))
            .key("challenge_fish_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_fish_25", "seaborne.fish.caught", 25, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_fish_250", "seaborne.fish.caught", 250, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("rotten_flesh"))
        .key("challenge_drowned_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("trident"))
            .key("challenge_drowned_250")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_drowned_25", "seaborne.drowned.kills", 25, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_drowned_250", "seaborne.drowned.kills", 250, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("prismarine_shard"))
        .key("challenge_guardian_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("sea_lantern"))
            .key("challenge_guardian_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_guardian_10", "seaborne.guardian.kills", 10, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_guardian_100", "seaborne.guardian.kills", 100, () -> getConfig().challengeSwim1nmReward * 2);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("prismarine"))
        .key("challenge_underwater_blocks_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("conduit"))
            .key("challenge_underwater_blocks_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_underwater_blocks_100", "seaborne.underwater.blocks", 100, () -> getConfig().challengeSwim1nmReward);
    registerMilestone("challenge_underwater_blocks_1k", "seaborne.underwater.blocks", 1000, () -> getConfig().challengeSwim1nmReward * 2);
    pulseLearners(this::getInterval, this::pulseSwimmingXp);
    registerGroup(Text.key("adapt.skill.seaborne.group.swimming"), List.of(Id.parseAdapt("adapt:seaborne/oxygen"), Id.parseAdapt("adapt:seaborne/speed"), Id.parseAdapt("adapt:seaborne/hydro_jet"), Id.parseAdapt("adapt:seaborne/tidecaller"), Id.parseAdapt("adapt:seaborne/turtles_mining_speed"), Id.parseAdapt("adapt:seaborne/rain_fed")));
    registerGroup(Text.key("adapt.skill.seaborne.group.combat"), List.of(Id.parseAdapt("adapt:seaborne/brine_skin"), Id.parseAdapt("adapt:seaborne/pressure_diver"), Id.parseAdapt("adapt:seaborne/ink_veil"), Id.parseAdapt("adapt:seaborne/trident_mastery"), Id.parseAdapt("adapt:seaborne/storm_strike")));
    registerGroup(Text.key("adapt.skill.seaborne.group.harvest"), List.of(Id.parseAdapt("adapt:seaborne/fish_whisperer"), Id.parseAdapt("adapt:seaborne/fishers_fantasy"), Id.parseAdapt("adapt:seaborne/deep_salvager"), Id.parseAdapt("adapt:seaborne/coral_gardener")));
  }


  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent event) {
    lastWaterMovementAt.remove(event.player().uuid());
    swimDistance.remove(event.player().uuid());
  }

  @Override
  public void unregister() {
    swimming.clear();
    lastWaterMovementAt.clear();
    swimDistance.clear();
    super.unregister();
  }

  private void pulseSwimmingXp(PlayerRef player, long elapsedMillis) {
    shouldReturnForPlayer(player, () -> {
      UUID playerId = player.uuid();
      Double swum = swimDistance.remove(playerId);
      double distance = swum == null ? 0D : swum;
      boolean waterBreathing = hasWaterBreathingEffect(player);
      boolean recentWaterMovement = isRecentWaterMovement(
          lastWaterMovementAt.get(playerId),
          System.currentTimeMillis(),
          WATER_MOVEMENT_GRACE_MILLIS);
      boolean qualifies = qualifiesForPassiveSwimXp(
          player.isInWater(),
          player.isSwimming(),
          player.remainingAir(),
          player.maximumAir(),
          waterBreathing,
          recentWaterMovement) && distance >= MINIMUM_SWIM_DISPLACEMENT;
      boolean wasSwimming = swimming.getOrDefault(playerId, false);
      if (!qualifies) {
        if (wasSwimming) {
          swimming.remove(playerId);
        }
        return;
      }

      double xpAmount = passiveSwimXp(
          getConfig().swimXpPerSecond,
          elapsedMillis / 1000D,
          waterBreathing,
          getConfig().waterBreathingSwimXpBonusMultiplier)
          + Math.max(0D, getConfig().swimXpPerBlock) * distance;
      swim.awardSilent(player, xpAmount);
      if (!wasSwimming) {
        swimming.put(playerId, true);
        fx(player.location(), FxPriority.AMBIENT)
            .column(Particles.END_ROD, 4, 1.0D)
            .particle(Particles.BUBBLE, 4, 0D, 0.5D, 0D, 0.3D, 0.02D)
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Loc from = e.from();
    Loc to = e.to();
    if (to == null || !from.sameWorld(to)) {
      return;
    }
    double horizontal = horizontalDistance(from, to);
    if (horizontal <= 0D || horizontal > 8D) {
      return;
    }

    PlayerRef player = e.player();
    if (player.isInWater() && player.vehicle() == null && !player.isFlying()) {
      lastWaterMovementAt.put(player.uuid(), System.currentTimeMillis());
      swimDistance.merge(player.uuid(), horizontal, Double::sum);
    }
  }


  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerFishEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      double amount;
      if (e.state() == PlayerFishEvent.State.CAUGHT_FISH) {
        addStat(p, "seaborne.fish.caught", 1);
        amount = e.caught() instanceof ItemEntityRef catchEntity && isTreasure(catchEntity.item())
            ? getConfig().treasureCaughtXp : getConfig().fishCaughtXp;
      } else if (e.state() == PlayerFishEvent.State.CAUGHT_ENTITY) {
        amount = getConfig().entityCaughtXp;
      } else {
        return;
      }
      if (!fishCooldowns.isReady(p.uuid(), getConfig().fishXpCooldown)) {
        return;
      }
      fishCooldowns.mark(p.uuid());
      fish.award(p, amount);
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    shouldReturnForPlayer(p, e, () -> {
      if ((!p.isSwimming() && !p.isInWater()) || XpProvenance.breakXpMultiplier(e.block()) <= 0D) {
        return;
      }

      if (!underwaterBlockCooldowns.isReady(p.uuid(), getConfig().seaPickleCooldown)) {
        return;
      }

      underwaterBlockCooldowns.mark(p.uuid());
      addStat(p, "seaborne.underwater.blocks", 1);
      if (e.block().type().equals(SEA_PICKLE) && p.isSwimming() && p.remainingAir() < p.maximumAir()) { // BECAUSE I LIKE PICKLES
        seaPickle.awardSilent(p, 10);
      } else {
        underwaterBlock.awardSilent(p, 3);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    PlayerRef p = e.killer();
    if (e.platformCancelled() || p == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }
    shouldReturnForPlayer(p, () -> {
      boolean seaKill = false;
      Id type = e.entity().type();
      if (type.equals(DROWNED)) {
        addStat(p, "seaborne.drowned.kills", 1);
        seaKill = true;
      } else if (type.equals(GUARDIAN) || type.equals(ELDER_GUARDIAN)) {
        addStat(p, "seaborne.guardian.kills", 1);
        seaKill = true;
      }

      LivingRef victim = e.entity();
      if (isSeaCreature(victim) && MobOrigin.paysKillReward(p, victim)) {
        seaCreatureKill.award(p, victim.location(), seaCreatureKillXp(getConfig().seaCreatureKillXp,
            getConfig().seaCreatureMaxHealthXpMultiplier, victim.maxHealth()));
      }

      if (seaKill) {
        fx(e.entity().location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .particle(Particles.SPLASH, 8, 0D, 0D, 0D, 0.3D, 0.08D)
            .particle(Particles.CRIT, 5, 0D, 0D, 0D, 0.3D, 0.05D)
            .dustBurst(4, 0.3D, 1.0F)
            .chord(Sounds.ENTITY_DOLPHIN_SPLASH, 0.5F, 1.2F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
      }
    });
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef)) {
      return;
    }

    EntityRef damager = e.damager();
    if (e.entity().type().equals(DROWNED) && damager instanceof PlayerRef p) {
      shouldReturnForPlayer(p, e, () -> {
        if (!drownedDamageCooldowns.isReady(p.uuid(), getConfig().drownedDamageXpCooldown)) {
          return;
        }
        double damage = CombatXp.credit(getId().toString(), p, e);
        if (damage <= 0D) {
          return;
        }
        drownedDamageCooldowns.mark(p.uuid());
        drownedHit.award(p, getConfig().damagedrownxpmultiplier * damage);
      });
    } else if (damager.type().equals(TRIDENT)) {
      if (damager instanceof ProjectileRef trident && trident.shooter() instanceof PlayerRef p) {
        shouldReturnForPlayer(p, e, () -> grantTridentDamageXp(p, e));
      }
    } else if (damager instanceof PlayerRef p) {
      if (p.inventory().mainHand().type().equals(TRIDENT)) {
        shouldReturnForPlayer(p, e, () -> grantTridentDamageXp(p, e));
      }
    }
  }

  private void grantTridentDamageXp(PlayerRef p, EntityDamageByEntityEvent event) {
    if (!tridentDamageCooldowns.isReady(p.uuid(), getConfig().tridentDamageXpCooldown)) {
      return;
    }
    double damage = CombatXp.credit(getId().toString(), p, event);
    if (damage <= 0D) {
      return;
    }
    tridentDamageCooldowns.mark(p.uuid());
    tridentHit.award(p, getConfig().tridentxpmultiplier * damage);
  }

  static boolean isTreasure(ItemRef item) {
    if (item == null || item.isEmpty()) {
      return false;
    }
    Id type = item.type();
    return TREASURE.contains(type) || (ENCHANTABLE_TREASURE.contains(type) && !item.enchantments().isEmpty());
  }

  static double horizontalDistance(Loc from, Loc to) {
    double dx = to.x() - from.x();
    double dz = to.z() - from.z();
    double distance = Math.sqrt(dx * dx + dz * dz);
    return Double.isFinite(distance) ? distance : 0D;
  }

  static double seaCreatureKillXp(double baseXp, double maxHealthMultiplier, double maxHealth) {
    double healthBonus = Double.isFinite(maxHealth) && maxHealth > 0D ? Math.max(0D, maxHealthMultiplier) * maxHealth : 0D;
    return Math.max(0D, baseXp) + healthBonus;
  }

  static boolean isSeaCreature(EntityRef entity) {
    return entity.has(EntityTrait.WATER_MOB) || entity.has(EntityTrait.FISH) || SEA_CREATURES.contains(entity.type());
  }

  static boolean qualifiesForPassiveSwimXp(
      boolean inWater,
      boolean swimming,
      int remainingAir,
      int maximumAir,
      boolean waterBreathing,
      boolean recentWaterMovement) {
    if (!recentWaterMovement) {
      return false;
    }
    if (waterBreathing) {
      return inWater;
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

  private boolean hasWaterBreathingEffect(PlayerRef player) {
    return hasWaterBreathingEffect(
        player.hasEffect(WATER_BREATHING),
        player.hasEffect(CONDUIT_POWER));
  }


  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Controls Sea Pickle Cooldown for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long seaPickleCooldown = 60000;
    @ConfigDoc(value = "Cooldown between XP awards for damaging drowned.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    public long drownedDamageXpCooldown = 1500;
    @ConfigDoc(value = "Cooldown between XP awards for trident damage.", impact = "Higher values reduce repeated combat XP frequency; lower values reward hits more often.")
    public long tridentDamageXpCooldown = 1500;
    @ConfigDoc(value = "XP awarded for catching a fish.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double fishCaughtXp = 120;
    @ConfigDoc(value = "XP for reeling in treasure: enchanted books, name tags, nautilus shells, saddles, or an enchanted bow or rod.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double treasureCaughtXp = 250;
    @ConfigDoc(value = "XP awarded for reeling in an entity.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double entityCaughtXp = 10;
    @ConfigDoc(value = "Cooldown between fishing XP awards.", impact = "Higher values reduce AFK fishing XP frequency; lower values reward catches more often.")
    public long fishXpCooldown = 5000;
    @ConfigDoc(value = "Controls Tridentxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double tridentxpmultiplier = 4.0;
    @ConfigDoc(value = "Controls Damagedrownxpmultiplier for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagedrownxpmultiplier = 3;
    @ConfigDoc(value = "Controls Challenge Swim1nm Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim1nmReward = 750;
    @ConfigDoc(value = "Controls Challenge Swim5k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim5kReward = 1500;
    @ConfigDoc(value = "Controls Challenge Swim20k Reward for the Seaborne skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeSwim20kReward = 3750;
    @ConfigDoc(value = "XP per second spent swimming while moving horizontally.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double swimXpPerSecond = 1.0;
    @ConfigDoc(value = "XP per block of horizontal distance swum.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double swimXpPerBlock = 0.5;
    @ConfigDoc(value = "XP for killing a sea creature such as fish, squid, dolphins, guardians, drowned, turtles, or axolotls.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double seaCreatureKillXp = 150;
    @ConfigDoc(value = "Extra sea creature kill XP per point of the victim's maximum health.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double seaCreatureMaxHealthXpMultiplier = 5.0;
    @ConfigDoc(value = "Additional passive swimming XP multiplier for players actively moving in water with Water Breathing or Conduit Power.", impact = "1.0 adds one base swim XP award for 2x total; 0 disables the bonus while retaining the base moving-swimmer award.")
    double waterBreathingSwimXpBonusMultiplier = 1.0;

    public Config() {
    }
  }
}
