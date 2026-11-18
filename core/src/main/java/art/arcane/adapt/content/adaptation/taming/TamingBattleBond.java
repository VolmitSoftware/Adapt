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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TamingBattleBond extends SimpleAdaptation<TamingBattleBond.Config> {
  private static final int HARD_MAX_PACK = 24;
  private static final int HARD_MAX_CANDIDATES = 96;
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id REGENERATION = Id.minecraft("regeneration");
  private static final Id STRENGTH = Id.minecraft("strength");
  private static final Id GLOWING = Id.minecraft("glowing");
  private final Cooldowns fxCd = cooldowns();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();

  static int buffTicks(double levelPercent, double base, double factor) {
    return Math.max(20, (int) Math.round(base + (levelPercent * factor)));
  }

  public TamingBattleBond() {
    super("tame-battle-bond");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.battle_bond");
    setIcon(Id.minecraft("diamond_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("diamond_sword"))
        .key("challenge_taming_bond_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_sword"))
            .key("challenge_taming_bond_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_bond_250", "taming.battle-bond.kills", 250, 400);
    registerMilestone("challenge_taming_bond_2500", "taming.battle-bond.kills", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.SPEED, TamingPreferences.VISUALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, 1 + getBuffTier(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getBuffTicks(level) * 50D, 1), 2);
  }

  @Override
  public void unregister() {
    lifecycleCleanupStarted.set(true);
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef dead = e.entity();
    EntityRef pet = resolveKillingPet(dead.lastDamage());
    if (pet == null) {
      return;
    }
    Tasks.runEntity(pet, () -> beginBondFromPet(pet));
  }

  private void beginBondFromPet(EntityRef pet) {
    UUID ownerId = pet.tameOwner();
    PlayerRef owner = lifecycleCleanupStarted.get() || !AdaptCore.platform().host().animals().isTamed(pet) || ownerId == null
        ? null
        : AdaptCore.platform().host().player(ownerId);
    if (owner == null) {
      return;
    }
    Tasks.runEntity(owner, () -> beginBondFromOwner(owner));
  }

  private void beginBondFromOwner(PlayerRef owner) {
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

  private void bondPack(PlayerRef owner, int amplifier, int duration, double radius) {
    if (lifecycleCleanupStarted.get() || !owner.isOnline()) {
      return;
    }

    applyBuffs(owner, amplifier, duration, preferenceEnabled(owner, TamingPreferences.SPEED), preferenceEnabled(owner, TamingPreferences.VISUALS));

    UUID ownerId = owner.uuid();
    int limit = getPackLimit();
    Loc ownerChest = owner.location().add(0, 1, 0);
    BondBatch batch = new BondBatch(ownerId, ownerChest, amplifier, duration, getGlowTicks(), limit, preferenceEnabled(owner, TamingPreferences.SPEED), preferenceEnabled(owner, TamingPreferences.VISUALS));
    int candidates = 0;
    for (EntityRef entity : owner.nearbyEntities(radius, radius, radius)) {
      if (candidates >= HARD_MAX_CANDIDATES) {
        break;
      }
      if (entity instanceof LivingRef living && entity.has(EntityTrait.TAMEABLE)) {
        candidates++;
        Tasks.runEntity(living, () -> applyPetBond(living, batch));
      }
    }

    if (preferenceEnabled(owner, TamingPreferences.VISUALS) && fxCd.isReady(ownerId, 800L)) {
      fxCd.mark(ownerId);
      fx(owner.location().add(0, 1, 0), FxPriority.COMBAT)
          .dustRing(Rgb.of(0xFF5B4A), 1.6D, 10, 1.0F)
          .chord(Sounds.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.25F, Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.5F, 1.5F);
    }

    addStat(owner, "taming.battle-bond.kills", 1);
    xp(owner, getConfig().xpPerKill);
  }

  static EntityRef resolveKillingPet(LastDamage damageCause) {
    if (damageCause == null || !damageCause.byEntity()
        || !damageCause.damager().has(EntityTrait.TAMEABLE)) {
      return null;
    }
    return damageCause.damager();
  }

  static void applyBuffs(LivingRef entity, int amplifier, int duration, boolean speed, boolean visuals) {
    if (!entity.isValid() || entity.isDead()) {
      return;
    }

    for (BondBuff buff : buffTypes(true)) {
      if (buff == BondBuff.SPEED && !speed) { continue; }
      Id effectType = switch (buff) {
        case SPEED -> SPEED;
        case REGENERATION -> REGENERATION;
        case ATTACK_STRENGTH -> STRENGTH;
      };
      entity.addEffect(new EffectInstance(effectType, duration, amplifier, false, visuals, true));
    }
  }

  static List<BondBuff> buffTypes(boolean attackStrengthAvailable) {
    if (!attackStrengthAvailable) {
      return List.of(BondBuff.SPEED, BondBuff.REGENERATION);
    }
    return List.of(BondBuff.SPEED, BondBuff.REGENERATION, BondBuff.ATTACK_STRENGTH);
  }

  private void applyPetBond(LivingRef pet, BondBatch batch) {
    if (lifecycleCleanupStarted.get() || !AdaptCore.platform().host().animals().isTamed(pet) || !pet.isValid() || pet.isDead()
        || !isOwnedBy(pet, batch.ownerId())
        || !batch.claim()) {
      return;
    }
    applyBuffs(pet, batch.amplifier(), batch.duration(), batch.speed(), batch.visuals());
    if (!batch.visuals()) { return; }
    applyGlow(pet, batch.glowTicks());
    fx(pet, FxPriority.COMBAT)
        .burst(Particles.ELECTRIC_SPARK, 5, 0.25D)
        .line(Particles.END_ROD, batch.ownerChest().x(), batch.ownerChest().y(), batch.ownerChest().z(), 10)
        .sound(Sounds.BLOCK_BEACON_ACTIVATE, 0.45F, 1.6F);
  }

  private void applyGlow(LivingRef pet, int glowTicks) {
    EffectInstance current = pet.effect(GLOWING);
    if (current != null && current.durationTicks() >= glowTicks) {
      return;
    }
    pet.addEffect(new EffectInstance(GLOWING, glowTicks, 0, false, false, false));
  }

  private boolean isOwnedBy(LivingRef tameable, UUID ownerId) {
    UUID owner = tameable.tameOwner();
    return owner != null && ownerId.equals(owner);
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

  private record BondBatch(UUID ownerId, Loc ownerChest, int amplifier, int duration, int glowTicks,
                           int limit, boolean speed, boolean visuals, AtomicInteger claimed) {
    private BondBatch(UUID ownerId, Loc ownerChest, int amplifier, int duration, int glowTicks, int limit, boolean speed, boolean visuals) {
      this(ownerId, ownerChest, amplifier, duration, glowTicks, limit, speed, visuals, new AtomicInteger());
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
