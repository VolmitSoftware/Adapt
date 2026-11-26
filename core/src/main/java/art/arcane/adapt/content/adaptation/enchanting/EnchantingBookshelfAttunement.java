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
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class EnchantingBookshelfAttunement extends SimpleAdaptation<EnchantingBookshelfAttunement.Config> {
  public static final PlayerPreference<CommonPreferences.Scale> POWER = CommonPreferences.scale("power", EnchantingMessages.PREFERENCE_ENCHANTINGBOOKSHELFATTUNEMENT_POWER);

  private final Cooldowns shimmerCooldown = cooldowns();

  public EnchantingBookshelfAttunement() {
    super("enchanting-bookshelf-attunement");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bookshelf"));
    setInterval(1400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bookshelf"))
        .key("challenge_enchanting_bookshelf_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_enchanting_bookshelf_100", "enchanting.bookshelf-attunement.enchants-boosted", 100, 400);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, POWER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getVirtualPower(level), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    int count = Math.min(24, 6 + (getVirtualPower(getLevel(p)) * 2));
    fx(e.table().location().add(0.5D, 1.0D, 0.5D), FxPriority.TRANSITION)
        .dome(Particles.ENCHANTMENT_TABLE, 1.4D, count)
        .sound(Sounds.BLOCK_BEACON_ACTIVATE, 0.5F, 1.3F);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareItemEnchantEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    int power = (int) Math.floor(getVirtualPower(getLevel(p)) * preference(p, POWER).multiplier());
    boolean boosted = EnchantingOffers.boost(e.offers(), power);
    if (boosted) {
      addStat(p, "enchanting.bookshelf-attunement.enchants-boosted", 1);
      if (shimmerCooldown.isReady(p.uuid(), 1000L)) {
        shimmerCooldown.mark(p.uuid());
        fx(e.table().location().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
            .particle(Particles.ENCHANTMENT_TABLE, 6, 0, 0, 0, 0.4D, 0.05D)
            .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.5F);
      }
    }
  }

  private int getVirtualPower(int level) {
    return Math.max(1, (int) Math.round(getConfig().powerBase + (getLevelPercent(level) * getConfig().powerFactor)));
  }


  @ConfigDescription("Gain virtual bookshelf power to improve enchanting table offer quality.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Power Base for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double powerBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Power Factor for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double powerFactor = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }
}
