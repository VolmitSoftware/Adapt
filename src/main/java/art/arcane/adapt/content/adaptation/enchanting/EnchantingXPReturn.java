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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.UUID;

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {
  private final Cooldowns cooldown = cooldowns();

  public EnchantingXPReturn() {
    super("enchanting-xp-return");
    registerConfiguration(Config.class);
    setLocalizationKey("enchanting.return");
    setIcon(Material.EXPERIENCE_BOTTLE);
    setInterval(13001);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_enchanting_xp_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_enchanting_xp_100", "enchanting.xp-return.levels-saved", 100, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(EnchantingMessages.RETURN_LORE1));
    statLore(v, C.GREEN, "", rewardXp(level, getConfig().vanillaXpAtLevelOne, getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerEnchant), 2);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EnchantItemEvent e) {
    Player p = e.getEnchanter();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldown.isReady(id, getConfig().cooldownMillis)) {
      return;
    }

    int xpAmount = rewardXp(level, getConfig().vanillaXpAtLevelOne, getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerEnchant);
    if (xpAmount <= 0) {
      return;
    }

    cooldown.mark(id);
    p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(xpAmount);
    addStat(p, "enchanting.xp-return.levels-saved", xpAmount);
    xpRefundFx(p, level);
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  private void xpRefundFx(Player p, int level) {
    int rods = Math.min(level, 5);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.7D * (1.0D - progress)) + 0.1D, 1.4D, 2, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.VILLAGER_HAPPY, 4, 0, 1.0D, 0, 0.3D, 0)
                .column(Particles.END_ROD, rods, 1.2D)
                .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
          }
        })
        .start();
  }


  @ConfigDescription("Committed enchants periodically return a bounded vanilla XP orb.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points returned by a level-one activation.", impact = "Higher values increase the base enchanting refund.")
    int vanillaXpAtLevelOne = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the refund scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum vanilla XP points returned by one enchant.", impact = "Caps oversized or heavily scaled refunds.")
    int maximumXpPerEnchant = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum milliseconds between vanilla XP refunds for the same player.", impact = "Higher values reduce repeated enchanting refunds.")
    long cooldownMillis = 30000L;

    public Config() {
      baseCost = 1;
      costFactor = 0.9;
      maxLevel = 7;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      vanillaXpAtLevelOne = Math.max(0, Math.min(vanillaXpAtLevelOne, 100000));
      vanillaXpPerAdditionalLevel = Math.max(0, Math.min(vanillaXpPerAdditionalLevel, 100000));
      maximumXpPerEnchant = Math.max(0, Math.min(maximumXpPerEnchant, 100000));
      cooldownMillis = Math.max(0L, Math.min(cooldownMillis, 3600000L));
    }
  }
}
