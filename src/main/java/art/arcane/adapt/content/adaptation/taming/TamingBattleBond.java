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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TamingBattleBond extends SimpleAdaptation<TamingBattleBond.Config> {
  private static final int HARD_MAX_PACK = 24;
  private static final int HARD_MAX_CANDIDATES = 96;
  private final Cooldowns fxCd = cooldowns();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();

  static int buffTicks(double levelPercent, double base, double factor) {
    return Math.max(20, (int) Math.round(base + (levelPercent * factor)));
  }

  public TamingBattleBond() {
    super("tame-battle-bond");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.battle_bond");
    setIcon(Material.DIAMOND_SWORD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_taming_bond_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_taming_bond_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_bond_250", "taming.battle-bond.kills", 250, 400);
    registerMilestone("challenge_taming_bond_2500", "taming.battle-bond.kills", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, 1 + getBuffTier(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getBuffTicks(level) * 50D, 1), 2);
  }

  @Override
  public void unregister() {
    lifecycleCleanupStarted.set(true);
    super.unregister();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingEntity dead = e.getEntity();
    Tameable pet = resolveKillingPet(dead.getLastDamageCause());
    if (pet == null) {
      return;
    }
    J.runEntity(pet, () -> beginBondFromPet(pet));
  }

  private void beginBondFromPet(Tameable pet) {
    if (lifecycleCleanupStarted.get() || !pet.isTamed() || !(pet.getOwner() instanceof Player owner)) {
      return;
    }
    J.runEntity(owner, () -> beginBondFromOwner(owner));
  }

  private void beginBondFromOwner(Player owner) {
    if (lifecycleCleanupStarted.get() || !owner.isOnline()) {
      return;
    }
    int level = getActiveLevel(owner);
    if (level <= 0) {
      return;
    }

    int amplifier = getBuffTier(level);
    int duration = getBuffTicks(level);
    double radius = Math.max(1.0, getConfig().packRadius);
    bondPack(owner, amplifier, duration, radius);
  }

  private void bondPack(Player owner, int amplifier, int duration, double radius) {
    if (lifecycleCleanupStarted.get() || !owner.isOnline()) {
      return;
    }

    applyBuffs(owner, amplifier, duration);

    UUID ownerId = owner.getUniqueId();
    int limit = getPackLimit();
    Location ownerChest = owner.getLocation().add(0, 1, 0);
    BondBatch batch = new BondBatch(ownerId, ownerChest, amplifier, duration, getGlowTicks(), limit);
    int candidates = 0;
    for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
      if (candidates >= HARD_MAX_CANDIDATES) {
        break;
      }
      if (entity instanceof LivingEntity living && entity instanceof Tameable tameable) {
        candidates++;
        J.runEntity(living, () -> applyPetBond(living, tameable, batch));
      }
    }

    if (fxCd.isReady(ownerId, 800L)) {
      fxCd.mark(ownerId);
      fx(owner.getLocation().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(Color.fromRGB(0xFF5B4A), 1.6D, 10, 1.0F)
          .chord(Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.25F, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5F, 1.5F);
    }

    addStat(owner, "taming.battle-bond.kills", 1);
    xp(owner, getConfig().xpPerKill);
  }

  static Tameable resolveKillingPet(EntityDamageEvent damageCause) {
    if (!(damageCause instanceof EntityDamageByEntityEvent cause)
        || !(cause.getDamager() instanceof Tameable pet)) {
      return null;
    }
    return pet;
  }

  static void applyBuffs(LivingEntity entity, int amplifier, int duration) {
    if (!entity.isValid() || entity.isDead()) {
      return;
    }

    for (BondBuff buff : buffTypes(PotionEffectTypes.INCREASE_DAMAGE != null)) {
      PotionEffectType effectType = switch (buff) {
        case SPEED -> PotionEffectType.SPEED;
        case REGENERATION -> PotionEffectType.REGENERATION;
        case ATTACK_STRENGTH -> PotionEffectTypes.INCREASE_DAMAGE;
      };
      entity.addPotionEffect(new PotionEffect(effectType, duration, amplifier, false, true, true));
    }
  }

  static List<BondBuff> buffTypes(boolean attackStrengthAvailable) {
    if (!attackStrengthAvailable) {
      return List.of(BondBuff.SPEED, BondBuff.REGENERATION);
    }
    return List.of(BondBuff.SPEED, BondBuff.REGENERATION, BondBuff.ATTACK_STRENGTH);
  }

  private void applyPetBond(LivingEntity pet, Tameable tameable, BondBatch batch) {
    if (lifecycleCleanupStarted.get() || !tameable.isTamed() || !pet.isValid() || pet.isDead()
        || !isOwnedBy(tameable, batch.ownerId())
        || !batch.claim()) {
      return;
    }
    applyBuffs(pet, batch.amplifier(), batch.duration());
    applyGlow(pet, batch.glowTicks());
    fx(pet, FxPriority.COMBAT)
        .burst(Particle.ELECTRIC_SPARK, 5, 0.25D)
        .line(Particle.END_ROD, batch.ownerChest().getX(), batch.ownerChest().getY(), batch.ownerChest().getZ(), 10)
        .sound(Sound.BLOCK_BEACON_ACTIVATE, 0.45F, 1.6F);
  }

  private void applyGlow(LivingEntity pet, int glowTicks) {
    PotionEffect current = pet.getPotionEffect(PotionEffectType.GLOWING);
    if (current != null && current.getDuration() >= glowTicks) {
      return;
    }
    pet.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, false, false, false));
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
  }

  private int getBuffTier(int level) {
    int max = Math.max(0, getConfig().maxBuffTier);
    return Math.min(max, (int) Math.floor(getLevelPercent(level) * (max + 1)));
  }

  private int getBuffTicks(int level) {
    return buffTicks(getLevelPercent(level), getConfig().buffTicksBase, getConfig().buffTicksFactor);
  }

  private int getPackLimit() {
    return Math.max(1, Math.min(getConfig().maxPack, HARD_MAX_PACK));
  }

  private int getGlowTicks() {
    return Math.max(10, Math.min(getConfig().glowTicks, 60));
  }

  private record BondBatch(UUID ownerId, Location ownerChest, int amplifier, int duration, int glowTicks,
                           int limit, AtomicInteger claimed) {
    private BondBatch(UUID ownerId, Location ownerChest, int amplifier, int duration, int glowTicks, int limit) {
      this(ownerId, ownerChest, amplifier, duration, glowTicks, limit, new AtomicInteger());
    }

    private boolean claim() {
      while (true) {
        int current = claimed.get();
        if (current >= limit) {
          return false;
        }
        if (claimed.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }
  }

  enum BondBuff {
    SPEED,
    REGENERATION,
    ATTACK_STRENGTH
  }

  @ConfigDescription("When one of your pets lands a kill, you and the nearby pack gain brief strength, speed, and regeneration.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Highest buff amplifier (tier) reachable at max level; level 1 grants tier 1.", impact = "Higher values grant stronger strength, speed, and regeneration at high levels.")
    int maxBuffTier = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Ticks Base for the Taming Battle Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Buff Ticks Factor for the Taming Battle Bond adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double buffTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius searched for pack members to buff on a pet kill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double packRadius = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time Battle Bond triggers on a pet kill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerKill = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pack members buffed per kill, capped internally at 24.", impact = "Lower values reduce entity scheduling in very large packs.")
    int maxPack = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Number of ticks bonded pets glow, capped internally at 60.", impact = "Higher values keep the Battle Bond activation marker visible longer.")
    int glowTicks = 30;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 3;
    }
  }
}
