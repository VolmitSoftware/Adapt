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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
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
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulMarrowArmor extends SimpleAdaptation<TragoulMarrowArmor.Config> {
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

  public TragoulMarrowArmor() {
    super("tragoul-marrow-armor");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("tragoul.marrow_armor.description"));
    setDisplayName(Localizer.dLocalize("tragoul.marrow_armor.name"));
    setIcon(Material.BONE_MEAL);
    setInterval(25000);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE_MEAL)
        .key("challenge_tragoul_marrow_500")
        .title(Localizer.dLocalize("advancement.challenge_tragoul_marrow_500.title"))
        .description(Localizer.dLocalize("advancement.challenge_tragoul_marrow_500.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BONE_BLOCK)
            .key("challenge_tragoul_marrow_5k")
            .title(Localizer.dLocalize("advancement.challenge_tragoul_marrow_5k.title"))
            .description(Localizer.dLocalize("advancement.challenge_tragoul_marrow_5k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_marrow_500", "tragoul.marrow-armor.damage-absorbed", 500, 400);
    registerMilestone("challenge_tragoul_marrow_5k", "tragoul.marrow-armor.damage-absorbed", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.marrow_armor.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getAbsorbPercent(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.marrow_armor.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getInternalCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.marrow_armor.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (e.getFinalDamage() < getConfig().minDamageToTrigger) {
      return;
    }

    long now = System.currentTimeMillis();
    Long until = cooldowns.get(p.getUniqueId());
    if (until != null && until > now) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (!p.getInventory().containsAtLeast(new ItemStack(Material.BONE), 1)) {
        return;
      }

      cooldowns.put(p.getUniqueId(), now + getInternalCooldownMillis(level));
      p.getInventory().removeItem(new ItemStack(Material.BONE, 1));
      double absorbed = e.getDamage() * getAbsorbPercent(level);
      e.setDamage(Math.max(0, e.getDamage() - absorbed));

      if (areParticlesEnabled()) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(235, 230, 210), 1.1f);
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.0, 0), 18, 0.3, 0.45, 0.3, 0.01, dust);
      }
      SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 0.8f, 1.1f);
      getPlayer(p).getData().addStat("tragoul.marrow-armor.damage-absorbed", absorbed);
      xp(p, getConfig().xpPerAbsorb);
    });
  }

  private double getAbsorbPercent(int level) {
    return Math.min(getConfig().maxAbsorbPercent,
        Math.max(0, getConfig().absorbPercentBase + (getLevelPercent(level) * getConfig().absorbPercentFactor)));
  }

  private long getInternalCooldownMillis(int level) {
    return Math.max(500L, (long) Math.round(getConfig().internalCooldownMillisBase - (getLevelPercent(level) * getConfig().internalCooldownMillisFactor)));
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Consume a bone from your inventory to absorb part of incoming damage.")
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
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.72;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum final damage required before a bone is consumed.", impact = "Higher values ignore chip damage and save bones for real hits.")
    double minDamageToTrigger = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of the hit absorbed before level scaling.", impact = "Higher values absorb more damage per bone.")
    double absorbPercentBase = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional absorbed fraction granted at max level.", impact = "Higher values increase level-scaled absorption growth.")
    double absorbPercentFactor = 0.30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the absorbed fraction of a hit.", impact = "Prevents full damage immunity at high levels.")
    double maxAbsorbPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Internal cooldown in milliseconds before level scaling.", impact = "Higher values stop damage bursts from draining the bone supply.")
    double internalCooldownMillisBase = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels absorb hits more often.")
    double internalCooldownMillisFactor = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per absorbed hit.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerAbsorb = 8;
  }
}
