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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

public class UnarmedSecondWind extends SimpleAdaptation<UnarmedSecondWind.Config> {
  private final Map<UUID, Long> lastTriggerMillis = new java.util.concurrent.ConcurrentHashMap<>();

  public UnarmedSecondWind() {
    super("unarmed-second-wind");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.second_wind.description"));
    setDisplayName(Localizer.dLocalize("unarmed.second_wind.name"));
    setIcon(Material.COOKED_BEEF);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4960);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_second_wind_100")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_second_wind_100.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_second_wind_100.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_second_wind_1k")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_second_wind_1k.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_second_wind_1k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_second_wind_100", "unarmed.second-wind.second-winds", 100, 400);
    registerMilestone("challenge_unarmed_second_wind_1k", "unarmed.second-wind.second-winds", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + getFoodRestore(level) + C.GRAY + " " + Localizer.dLocalize("unarmed.second_wind.lore1"));
    v.addLore(C.GREEN + "+ " + Form.duration(getRegenDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.second_wind.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
      return;
    }

    if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg) || !(dmg.getDamager() instanceof Player p)) {
      return;
    }

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    long now = System.currentTimeMillis();
    Long last = lastTriggerMillis.get(p.getUniqueId());
    if (last != null && now - last < getConfig().cooldownMillis) {
      return;
    }

    lastTriggerMillis.put(p.getUniqueId(), now);
    int foodRestore = getFoodRestore(level);
    int regenTicks = getRegenDurationTicks(level);
    J.runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }
      p.setFoodLevel(Math.min(20, p.getFoodLevel() + foodRestore));
      p.setSaturation((float) Math.min(p.getFoodLevel(), p.getSaturation() + getConfig().saturationRestore));
      p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, getConfig().regenAmplifier, false, true, true), true);
    });

    SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.7f);
    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.6, 0), 3, 0.3, 0.2, 0.3, 0);
    }
    xp(p, getConfig().xpPerSecondWind, "second-wind");
    getPlayer(p).getData().addStat("unarmed.second-wind.second-winds", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    lastTriggerMillis.remove(e.getPlayer().getUniqueId());
  }

  private int getFoodRestore(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodRestoreBase + (getLevelPercent(level) * getConfig().foodRestoreFactor)));
  }

  private int getRegenDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().regenDurationTicksBase + (getLevelPercent(level) * getConfig().regenDurationTicksFactor)));
  }

  @Override
  public void onTick() {

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
  @ConfigDescription("Bare-hand kills restore hunger and grant a short regeneration burst.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hunger points restored per bare-hand kill.", impact = "Higher values restore more food per kill.")
    double foodRestoreBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional hunger points restored at max level.", impact = "Higher values restore more food per kill as levels increase.")
    double foodRestoreFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Saturation restored per bare-hand kill.", impact = "Higher values keep the hunger bar full for longer.")
    double saturationRestore = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base regeneration duration in ticks per bare-hand kill.", impact = "Higher values keep the regen burst active longer.")
    double regenDurationTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional regeneration duration ticks granted at max level.", impact = "Higher values keep the regen burst active longer as levels increase.")
    double regenDurationTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Regeneration amplifier applied by the burst.", impact = "Higher values heal faster during the burst.")
    int regenAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between second wind triggers.", impact = "Higher values prevent rapid kill-chains from spamming the heal.")
    long cooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per second wind trigger.", impact = "Higher values speed up unarmed skill progression from kills.")
    double xpPerSecondWind = 18;
  }
}
