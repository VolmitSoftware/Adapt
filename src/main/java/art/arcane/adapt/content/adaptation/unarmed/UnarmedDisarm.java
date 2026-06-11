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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class UnarmedDisarm extends SimpleAdaptation<UnarmedDisarm.Config> {
  private final Map<UUID, Long> targetLockUntil = new java.util.concurrent.ConcurrentHashMap<>();

  public UnarmedDisarm() {
    super("unarmed-disarm");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.disarm.description"));
    setDisplayName(Localizer.dLocalize("unarmed.disarm.name"));
    setIcon(Material.STICK);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(5125);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_disarm_100")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_disarm_100.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_disarm_100.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_disarm_1k")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_disarm_1k.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_disarm_1k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_disarm_100", "unarmed.disarm.disarms", 100, 400);
    registerMilestone("challenge_unarmed_disarm_1k", "unarmed.disarm.disarms", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("unarmed.disarm.lore1"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getConfig().targetCooldownMillis, 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.disarm.lore2"));
    v.addLore(C.GREEN + "+ " + Form.pc(getConfig().mobArmorDropChance, 0) + C.GRAY + " " + Localizer.dLocalize("unarmed.disarm.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
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
    }

    if (armor != null) {
      Item droppedArmor = victim.getWorld().dropItemNaturally(victim.getLocation().add(0, 0.4, 0), armor);
      droppedArmor.setPickupDelay(getConfig().pickupDelayTicks);
    }

    targetLockUntil.put(victim.getUniqueId(), now + getConfig().targetCooldownMillis);

    SoundPlayer sp = SoundPlayer.of(victim.getWorld());
    sp.play(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.35f);
    sp.play(victim.getLocation(), Sound.ITEM_LEAD_BREAK, 0.9f, 0.8f);
    if (areParticlesEnabled()) {
      victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.1, 0), 10, 0.25, 0.3, 0.25, 0.08);
    }
    xp(p, getConfig().xpPerDisarm, "disarm");
    getPlayer(p).getData().addStat("unarmed.disarm.disarms", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    targetLockUntil.remove(e.getPlayer().getUniqueId());
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

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Bare-hand hits can knock the target's held item to the ground.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.55;
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
  }
}
