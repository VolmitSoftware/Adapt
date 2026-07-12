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
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TamingAlphasCommand extends SimpleAdaptation<TamingAlphasCommand.Config> {
  private static final int HARD_MAX_PETS = 24;
  private final Cooldowns commandCd = cooldowns();

  public TamingAlphasCommand() {
    super("tame-alphas-command");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.alphas_command");
    setIcon(Material.BONE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_taming_command_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WOLF_ARMOR)
            .key("challenge_taming_command_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_taming_command_250", "taming.alphas-command.commands", 250, 400);
    registerMilestone("challenge_taming_command_2500", "taming.alphas-command.commands", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getCommandRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getFocusTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !p.isSneaking()) {
      return;
    }

    if (p.getInventory().getItemInMainHand().getType() != Material.BONE) {
      return;
    }

    if (!(e.getEntity() instanceof LivingEntity target)) {
      return;
    }

    if (isOwnPet(p, target)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !canDamageTarget(p, target)) {
      return;
    }

    double range = getCommandRange(level);
    List<Mob> pets = collectPets(p, range, target);
    if (pets.isEmpty()) {
      return;
    }

    UUID playerId = p.getUniqueId();
    if (!commandCd.isReady(playerId, getConfig().commandCooldownMillis)) {
      return;
    }

    commandCd.mark(playerId);
    e.setCancelled(true);

    int focusTicks = getFocusTicks(level);
    int amplifier = Math.max(0, getConfig().focusSpeedAmplifier);
    for (Mob pet : pets) {
      J.runEntity(pet, () -> focusPet(pet, playerId, target, focusTicks, amplifier));
    }

    Location origin = p.getLocation().add(0, 1, 0);
    fx(origin, FxPriority.COMBAT)
        .ring(Particles.VILLAGER_HAPPY, 1.2D, 12, 0.3D)
        .chord(Sound.ENTITY_WOLF_GROWL, 0.8F, 0.9F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.7F);
    addStat(p, "taming.alphas-command.commands", 1);
    xp(p, getConfig().xpPerCommand);
  }

  private List<Mob> collectPets(Player owner, double range, LivingEntity target) {
    UUID ownerId = owner.getUniqueId();
    int limit = getPetLimit();
    List<Mob> pets = new ArrayList<>(limit);
    for (Entity entity : owner.getNearbyEntities(range, range, range)) {
      if (entity == target) {
        continue;
      }
      if (entity instanceof Mob mob && mob instanceof Tameable tameable
          && tameable.isTamed() && mob.isValid() && !mob.isDead() && isOwnedBy(tameable, ownerId)) {
        pets.add(mob);
        if (pets.size() >= limit) {
          break;
        }
      }
    }
    return pets;
  }

  private void focusPet(Mob pet, UUID ownerId, LivingEntity target, int focusTicks, int amplifier) {
    if (!pet.isValid() || pet.isDead() || !(pet instanceof Tameable tameable) || !tameable.isTamed()
        || !isOwnedBy(tameable, ownerId)) {
      return;
    }

    pet.setTarget(target);
    if (PotionEffectTypes.INCREASE_DAMAGE != null) {
      pet.addPotionEffect(new PotionEffect(PotionEffectTypes.INCREASE_DAMAGE, focusTicks, amplifier, false, false));
    }
    pet.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, focusTicks, amplifier, false, false));
    fx(pet, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.25D)
        .sound(Sound.ENTITY_WOLF_GROWL, 0.6F, 1.2F);
  }

  private boolean isOwnPet(Player p, LivingEntity target) {
    return target instanceof Tameable tameable && tameable.isTamed() && isOwnedBy(tameable, p.getUniqueId());
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
  }

  private double getCommandRange(int level) {
    return getConfig().commandRangeBase + (getLevelPercent(level) * getConfig().commandRangeFactor);
  }

  private int getFocusTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().focusTicksBase + (getLevelPercent(level) * getConfig().focusTicksFactor)));
  }

  private int getPetLimit() {
    return Math.max(1, Math.min(getConfig().maxPets, HARD_MAX_PETS));
  }


  @ConfigDescription("Sneak-left-click a target while holding a bone to make your nearby pets focus it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Command Range Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeBase = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Command Range Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeFactor = 12.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Focus Ticks Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Focus Ticks Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the speed and strength focus buff applied to commanded pets.", impact = "Higher values give commanded pets a stronger pursuit buff.")
    int focusSpeedAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between commands.", impact = "Higher values reduce how often the pack can be re-commanded.")
    long commandCooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time the pack is commanded.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCommand = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pets commanded per activation, capped internally at 24.", impact = "Lower values reduce entity scheduling in very large packs.")
    int maxPets = 12;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      initialCost = 4;
    }
  }
}
