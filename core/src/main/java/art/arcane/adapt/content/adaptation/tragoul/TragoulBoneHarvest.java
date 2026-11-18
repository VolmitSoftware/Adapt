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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryPickupItemEvent;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TragoulBoneHarvest extends SimpleAdaptation<TragoulBoneHarvest.Config> {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id FIRE_RESISTANCE = Id.minecraft("fire_resistance");
  private static final Id ABSORPTION = Id.minecraft("absorption");
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");

  private static List<Id> effectPool() {
    return List.of(
        SPEED,
        REGENERATION,
        RESISTANCE,
        FIRE_RESISTANCE,
        ABSORPTION,
        JUMP_BOOST,
        NIGHT_VISION);
  }

  private static final Rgb GLOBE_BLOOD = new Rgb(170, 0, 0);
  private static final Rgb GLOBE_BONE = new Rgb(230, 225, 205);
  private static final DataKey<Byte> GLOBE_KEY = DataKey.adapt("tragoul-globe", DataCodecs.BYTE);
  private static final Id MAGMA_CREAM = Id.minecraft("magma_cream");
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final long GLOBE_TRACK_GRACE_MILLIS = 5000L;
  private final Set<UUID> bloodGlobes = ConcurrentHashMap.newKeySet();
  private final Set<UUID> boneGlobes = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> globeTrackExpiry = new ConcurrentHashMap<>();

  public TragoulBoneHarvest() {
    super("tragoul-bone-harvest");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone_block"));
    setInterval(10000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_tragoul_bone_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone_block"))
            .key("challenge_tragoul_bone_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_bone_500", "tragoul.bone-harvest.orbs-collected", 500, 300);
    registerMilestone("challenge_tragoul_bone_5k", "tragoul.bone-harvest.orbs-collected", 5000, 1000);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    globeTrackExpiry.entrySet().removeIf(entry -> {
      if (entry.getValue() >= now) {
        return false;
      }
      bloodGlobes.remove(entry.getKey());
      boneGlobes.remove(entry.getKey());
      return true;
    });
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.BLOOD, TragoulPreferences.BONE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getGlobeChance(level), 0), 1);
    statLore(v, Form.duration(getGlobeLifetimeTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.entity().killer();
    if (killer == null || GolemKillCredit.isGolemKill(e.entity())) {
      return;
    }

    withAdaptedPlayer(killer, () -> {
      if (!canDamageTarget(killer, e.entity())) {
        return;
      }

      int level = getActiveLevel(killer);
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (random.nextDouble() > getGlobeChance(level)) {
        return;
      }

      spawnGlobe(killer, e, random.nextBoolean(), level);
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityPickupItemEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      UUID id = e.item().uuid();
      boolean blood = bloodGlobes.contains(id);
      boolean bone = boneGlobes.contains(id);
      if (!blood && !bone) {
        return;
      }

      e.setCancelled(true);
      if ((blood && !preferenceEnabled(p, TragoulPreferences.BLOOD))
          || (bone && !preferenceEnabled(p, TragoulPreferences.BONE))) { return; }
      e.item().remove();
      bloodGlobes.remove(id);
      boneGlobes.remove(id);
      globeTrackExpiry.remove(id);
      applyBuff(p, blood, getLevel(p));
      addStat(p, "tragoul.bone-harvest.orbs-collected", 1);
    });
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(InventoryPickupItemEvent e) {
    if (e.item().data().has(GLOBE_KEY)) {
      e.setCancelled(true);
    }
  }

  private void spawnGlobe(PlayerRef owner, EntityDeathEvent e, boolean blood, int level) {
    ItemRef item = AdaptCore.platform().host().items().create(blood ? MAGMA_CREAM : SNOWBALL);
    if (!item.isEmpty()) {
      item.setDisplayName((blood ? C.RED : C.WHITE) + AdaptLanguage.text(
          blood ? TragoulMessages.BONE_HARVEST_BLOOD_GLOBE : TragoulMessages.BONE_HARVEST_BONE_GLOBE
      ));
    }

    ItemEntityRef dropped = e.entity().world().dropItemNaturally(e.entity().location().add(0, 0.35, 0).position(), item);
    dropped.setPickupDelay(10);
    dropped.setOwner(owner.uuid());
    dropped.setCanMobPickup(false);
    dropped.data().set(GLOBE_KEY, (byte) 1);
    UUID droppedId = dropped.uuid();
    if (blood) {
      bloodGlobes.add(droppedId);
    } else {
      boneGlobes.add(droppedId);
    }

    fx(dropped.location().add(0, 0.2, 0), FxPriority.AMBIENT)
        .dustBurst(blood ? GLOBE_BLOOD : GLOBE_BONE, 3, 0.15, 1.0F)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, blood ? 0.9F : 1.4F);

    int life = getGlobeLifetimeTicks(level);
    globeTrackExpiry.put(droppedId, System.currentTimeMillis() + (life * 50L) + GLOBE_TRACK_GRACE_MILLIS);
    Tasks.runEntity(dropped, () -> {
      bloodGlobes.remove(droppedId);
      boneGlobes.remove(droppedId);
      globeTrackExpiry.remove(droppedId);
      if (dropped.isValid()) {
        fx(dropped.location().add(0, 0.2, 0), FxPriority.AMBIENT)
            .burst(Particles.SMOKE, 4, 0.2)
            .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.2F, 1.5F);
        dropped.remove();
      }
    }, life);
    xp(owner, getConfig().xpPerGlobeSpawned);
  }

  private void applyBuff(PlayerRef p, boolean blood, int level) {
    if (blood) {
      p.addEffect(new EffectInstance(REGENERATION, getConfig().bloodBuffTicks, getConfig().bloodBuffAmplifier, false, true, true));
      playBloodPickup(p);
    } else {
      applyRandomBoneBuffs(p, level);
      fx(p.location().add(0, 1.0, 0), FxPriority.TRANSITION)
          .dustBurst(GLOBE_BONE, 8, 0.4, 1.0F)
          .burst(Particles.CRIT, 4, 0.4)
          .chord(Sounds.ENTITY_SKELETON_HURT, 0.7F, 1.2F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
    }
  }

  private void playBloodPickup(PlayerRef p) {
    ParticleData.DustTransition trans = new ParticleData.DustTransition(new Rgb(200, 0, 20), new Rgb(255, 120, 150), 1.0F);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.DUST_COLOR_TRANSITION, 1.2 - (1.0 * progress), 8, 1.0, trans);
          if (tick == 0) {
            fx.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
          }
        })
        .start();
  }

  private void applyRandomBoneBuffs(PlayerRef p, int level) {
    int buffs = Math.max(1, (int) Math.round(getConfig().boneBuffCountBase + (getLevelPercent(level) * getConfig().boneBuffCountFactor)));
    List<Id> pool = new ArrayList<>(effectPool());
    Collections.shuffle(pool);
    buffs = Math.min(pool.size(), buffs);

    int duration = getConfig().boneBuffTicks;
    int amp = Math.max(0, getConfig().boneBuffAmplifier);
    for (int i = 0; i < buffs; i++) {
      Id type = pool.get(i);
      int a = type.equals(ABSORPTION) && getLevelPercent(level) >= 0.75 ? amp + 1 : amp;
      if (type.equals(SPEED)) {
        grantSpeedBoost(p, a, duration);
        continue;
      }
      if (type.equals(JUMP_BOOST)) {
        grantJumpBoost(p, a, duration);
        continue;
      }
      p.addEffect(new EffectInstance(type, duration, a, false, true, true));
    }
  }

  private void grantSpeedBoost(PlayerRef p, int amplifier, int durationTicks) {
    if (durationTicks <= 0) {
      return;
    }
    AdaptAttributeService.get().applyTimed(p, getName(), "speed", Attributes.MOVEMENT_SPEED, speedBonus(amplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
  }

  private void grantJumpBoost(PlayerRef p, int amplifier, int durationTicks) {
    if (durationTicks <= 0) {
      return;
    }
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(p, getName(), "jump", Attributes.JUMP_STRENGTH, jumpStrengthBonus(amplifier), AttributeOperation.ADD_VALUE, durationTicks);
    attributes.applyTimed(p, getName(), "fall", Attributes.SAFE_FALL_DISTANCE, safeFallBonus(amplifier), AttributeOperation.ADD_VALUE, durationTicks);
  }

  static double speedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  static double jumpStrengthBonus(int amplifier) {
    return 0.1D * (amplifier + 1);
  }

  static double safeFallBonus(int amplifier) {
    return amplifier + 1.0D;
  }

  private double getGlobeChance(int level) {
    return Math.min(getConfig().maxGlobeChance, getConfig().globeChanceBase + (getLevelPercent(level) * getConfig().globeChanceFactor));
  }

  private int getGlobeLifetimeTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().globeLifetimeTicksBase + (getLevelPercent(level) * getConfig().globeLifetimeTicksFactor)));
  }

  @ConfigDescription("Kills can spawn temporary blood and bone globes that grant short buffs when picked up.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Globe Chance Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double globeChanceBase = 0.16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Globe Chance Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double globeChanceFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Globe Chance for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxGlobeChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Globe Lifetime Ticks Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double globeLifetimeTicksBase = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Globe Lifetime Ticks Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double globeLifetimeTicksFactor = 220;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blood Buff Ticks for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int bloodBuffTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blood Buff Amplifier for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int bloodBuffAmplifier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Ticks for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int boneBuffTicks = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Amplifier for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int boneBuffAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Count Base for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneBuffCountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Buff Count Factor for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneBuffCountFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Globe Spawned for the Tragoul Bone Harvest adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGlobeSpawned = 8;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
