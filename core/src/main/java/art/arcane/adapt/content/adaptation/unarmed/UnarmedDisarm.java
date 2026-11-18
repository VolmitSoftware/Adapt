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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class UnarmedDisarm extends SimpleAdaptation<UnarmedDisarm.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
  private final Map<UUID, Long> targetLockUntil = playerState();

  public UnarmedDisarm() {
    super("unarmed-disarm");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stick"));
    setInterval(5125);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_disarm_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_disarm_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_disarm_100", "unarmed.disarm.disarms", 100, 400);
    registerMilestone("challenge_unarmed_disarm_1k", "unarmed.disarm.disarms", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.PLAYERS, UnarmedPreferences.ARMOR);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getChance(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().targetCooldownMillis, 1), 2);
    statLore(v, Form.pc(getConfig().mobArmorDropChance, 0), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled()) {
      return;
    }

    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())) {
      return;
    }

    if (!(attack.target() instanceof LivingRef victim)) {
      return;
    }

    boolean victimIsPlayer = victim instanceof PlayerRef;
    if (victimIsPlayer && (!getConfig().allowDisarmPlayers || !preferenceEnabled(p, UnarmedPreferences.PLAYERS))) {
      return;
    }

    long now = System.currentTimeMillis();
    Long lock = targetLockUntil.get(victim.uuid());
    if (lock != null && now < lock) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getChance(attack.level())) {
      return;
    }

    if (MinionIdentity.isMinion(victim)) {
      return;
    }

    ItemRef empty = AdaptCore.platform().host().items().empty();
    ItemRef main = victim.equipment(EquipmentSlot.MAIN_HAND);
    ItemRef off = victim.equipment(EquipmentSlot.OFF_HAND);
    ItemRef knocked = null;
    if (isItem(main)) {
      knocked = main.copy();
      victim.setEquipment(EquipmentSlot.MAIN_HAND, empty);
    } else if (isItem(off) && off.type().equals(SHIELD)) {
      knocked = off.copy();
      victim.setEquipment(EquipmentSlot.OFF_HAND, empty);
    }

    ItemRef armor = null;
    if (!victimIsPlayer && preferenceEnabled(p, UnarmedPreferences.ARMOR) && ThreadLocalRandom.current().nextDouble() < getConfig().mobArmorDropChance) {
      armor = takeRandomArmorPiece(victim, empty);
    }

    if (knocked == null && armor == null) {
      return;
    }

    if (knocked != null) {
      ItemEntityRef dropped = victim.world().dropItemNaturally(victim.location().add(0, 0.4, 0).position(), knocked);
      dropped.setPickupDelay(getConfig().pickupDelayTicks);
      spinTrail(dropped);
    }

    if (armor != null) {
      ItemEntityRef droppedArmor = victim.world().dropItemNaturally(victim.location().add(0, 0.4, 0).position(), armor);
      droppedArmor.setPickupDelay(getConfig().pickupDelayTicks);
      spinTrail(droppedArmor);
    }

    targetLockUntil.put(victim.uuid(), now + getConfig().targetCooldownMillis);

    playDisarm(victim, knocked, armor);
    xp(p, getConfig().xpPerDisarm, "disarm");
    addStat(p, "unarmed.disarm.disarms", 1);
  }

  private void spinTrail(ItemEntityRef item) {
    timeline(item)
        .duration(6)
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particles.WAX_ON, 1, 0, 0.1D, 0, 0.05D, 0.0D))
        .start();
  }

  private void playDisarm(LivingRef victim, ItemRef knocked, ItemRef armor) {
    Loc head = victim.location().add(0, 1.1D, 0);
    FxEmitter fx = fx(head, FxPriority.COMBAT)
        .particle(Particles.CRIT, 6, 0, 0, 0, 0.3D, 0.08D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.35F, Sounds.ITEM_LEAD_BREAK, 0.9F, 0.8F);
    ItemRef sprayItem = knocked != null ? knocked : armor;
    if (sprayItem != null) {
      fx.particle(Particles.itemCrack(sprayItem), 8, 0, 0, 0, 0.15D, 0.05D);
    }
    if (knocked != null && knocked.type().equals(SHIELD)) {
      fx.sound(Sounds.ITEM_SHIELD_BREAK, 0.4F, 1.4F);
    }
    if (armor != null) {
      fx.sound(Sounds.ITEM_ARMOR_EQUIP_IRON, 0.6F, 0.7F);
    }
  }

  private double getChance(int level) {
    return Math.min(1, getConfig().chanceBase + (getLevelPercent(level) * getConfig().chanceFactor));
  }

  private ItemRef takeRandomArmorPiece(LivingRef victim, ItemRef empty) {
    int worn = 0;
    for (EquipmentSlot slot : ARMOR_SLOTS) {
      if (isItem(victim.equipment(slot))) {
        worn++;
      }
    }

    if (worn == 0) {
      return null;
    }

    int pick = ThreadLocalRandom.current().nextInt(worn);
    for (EquipmentSlot slot : ARMOR_SLOTS) {
      ItemRef piece = victim.equipment(slot);
      if (!isItem(piece)) {
        continue;
      }

      if (pick == 0) {
        ItemRef taken = piece.copy();
        victim.setEquipment(slot, empty);
        return taken;
      }
      pick--;
    }

    return null;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    targetLockUntil.values().removeIf(until -> until <= now);
  }

  @ConfigDescription("Bare-hand hits can knock the target's held item to the ground.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows disarming other players, not just mobs.", impact = "True lets bare-hand hits knock items out of player hands in PVP.")
    boolean allowDisarmPlayers = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance that a successful disarm against a mob also knocks loose a worn armor piece.", impact = "Higher values strip mob armor faster; players never lose armor.")
    double mobArmorDropChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for a bare-hand hit to disarm the target.", impact = "Higher values make disarms proc more often at level 1.")
    double chanceBase = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional disarm chance granted at max level.", impact = "Higher values make disarms proc more often as levels increase.")
    double chanceFactor = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Pickup delay ticks applied to the knocked item.", impact = "Higher values keep the victim from re-grabbing the item for longer.")
    int pickupDelayTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Per-target cooldown in milliseconds between disarms.", impact = "Higher values prevent chain-disarming the same target.")
    long targetCooldownMillis = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per successful disarm.", impact = "Higher values speed up unarmed skill progression from disarms.")
    double xpPerDisarm = 28;

    public Config() {
      costFactor = 0.55;
      initialCost = 5;
    }
  }
}
