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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class UnarmedDisarm extends SimpleAdaptation<UnarmedDisarm.Config> {
  private final Map<UUID, Long> targetLockUntil = playerState();

  public UnarmedDisarm() {
    super("unarmed-disarm");
    registerConfiguration(Config.class);
    setIcon(Material.STICK);
    setInterval(5125);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_disarm_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_disarm_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_disarm_100", "unarmed.disarm.disarms", 100, 400);
    registerMilestone("challenge_unarmed_disarm_1k", "unarmed.disarm.disarms", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getChance(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().targetCooldownMillis, 1), 2);
    statLore(v, Form.pc(getConfig().mobArmorDropChance, 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled()) {
      return;
    }

    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    if (!(attack.target() instanceof LivingEntity victim)) {
      return;
    }

    boolean victimIsPlayer = victim instanceof Player;
    if (victimIsPlayer && !getConfig().allowDisarmPlayers) {
      return;
    }

    long now = System.currentTimeMillis();
    Long lock = targetLockUntil.get(victim.getUniqueId());
    if (lock != null && now < lock) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getChance(attack.level())) {
      return;
    }

    if (TragoulSkeletalServant.isServant(victim)) {
      return;
    }

    EntityEquipment equipment = victim.getEquipment();
    if (equipment == null) {
      return;
    }

    ItemStack main = equipment.getItemInMainHand();
    ItemStack off = equipment.getItemInOffHand();
    ItemStack knocked = null;
    if (isItem(main)) {
      knocked = main.clone();
      equipment.setItemInMainHand(null);
    } else if (isItem(off) && off.getType() == Material.SHIELD) {
      knocked = off.clone();
      equipment.setItemInOffHand(null);
    }

    ItemStack armor = null;
    if (!victimIsPlayer && ThreadLocalRandom.current().nextDouble() < getConfig().mobArmorDropChance) {
      armor = takeRandomArmorPiece(equipment);
    }

    if (knocked == null && armor == null) {
      return;
    }

    if (knocked != null) {
      Item dropped = victim.getWorld().dropItemNaturally(victim.getLocation().add(0, 0.4, 0), knocked);
      dropped.setPickupDelay(getConfig().pickupDelayTicks);
      spinTrail(dropped);
    }

    if (armor != null) {
      Item droppedArmor = victim.getWorld().dropItemNaturally(victim.getLocation().add(0, 0.4, 0), armor);
      droppedArmor.setPickupDelay(getConfig().pickupDelayTicks);
      spinTrail(droppedArmor);
    }

    targetLockUntil.put(victim.getUniqueId(), now + getConfig().targetCooldownMillis);

    playDisarm(victim, knocked, armor);
    xp(p, getConfig().xpPerDisarm, "disarm");
    addStat(p, "unarmed.disarm.disarms", 1);
  }

  private void spinTrail(Item item) {
    timeline(item)
        .duration(6)
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particle.WAX_ON, 1, 0, 0.1D, 0, 0.05D, 0.0D))
        .start();
  }

  private void playDisarm(LivingEntity victim, ItemStack knocked, ItemStack armor) {
    Location head = victim.getLocation().add(0, 1.1D, 0);
    FxEmitter fx = fx(head, FxPriority.COMBAT)
        .particle(Particle.CRIT, 6, 0, 0, 0, 0.3D, 0.08D)
        .chord(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.35F, Sound.ITEM_LEAD_BREAK, 0.9F, 0.8F);
    ItemStack sprayItem = knocked != null ? knocked : armor;
    if (sprayItem != null) {
      fx.particle(Particles.ITEM_CRACK, 8, 0, 0, 0, 0.15D, 0.05D, sprayItem);
    }
    if (knocked != null && knocked.getType() == Material.SHIELD) {
      fx.sound(Sound.ITEM_SHIELD_BREAK, 0.4F, 1.4F);
    }
    if (armor != null) {
      fx.sound(Sound.ITEM_ARMOR_EQUIP_IRON, 0.6F, 0.7F);
    }
  }

  private double getChance(int level) {
    return Math.min(1, getConfig().chanceBase + (getLevelPercent(level) * getConfig().chanceFactor));
  }

  private ItemStack takeRandomArmorPiece(EntityEquipment equipment) {
    ItemStack[] armor = equipment.getArmorContents();
    int worn = 0;
    for (ItemStack piece : armor) {
      if (isItem(piece)) {
        worn++;
      }
    }

    if (worn == 0) {
      return null;
    }

    int pick = ThreadLocalRandom.current().nextInt(worn);
    for (int i = 0; i < armor.length; i++) {
      if (!isItem(armor[i])) {
        continue;
      }

      if (pick == 0) {
        ItemStack taken = armor[i].clone();
        armor[i] = null;
        equipment.setArmorContents(armor);
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
