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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class EnchantingOfferReroll extends SimpleAdaptation<EnchantingOfferReroll.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", EnchantingMessages.PREFERENCE_ENCHANTINGOFFERREROLL_CONFIRM, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<EnchantingPreferences.Reserve> XP_RESERVE = EnchantingPreferences.reserve("xp-reserve", EnchantingMessages.PREFERENCE_ENCHANTINGOFFERREROLL_XP_RESERVE);
  public static final PlayerPreference<EnchantingPreferences.Reserve> LAPIS_RESERVE = EnchantingPreferences.reserve("lapis-reserve", EnchantingMessages.PREFERENCE_ENCHANTINGOFFERREROLL_LAPIS_RESERVE);

  public EnchantingOfferReroll() {
    super("enchanting-offer-reroll");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("enchanting_table"));
    setInterval(1800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("enchanting_table"))
        .key("challenge_enchanting_reroll_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanting_table"))
            .key("challenge_enchanting_reroll_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_reroll_100", "enchanting.offer-reroll.rerolls", 100, 300);
    registerMilestone("challenge_enchanting_reroll_1k", "enchanting.offer-reroll.rerolls", 1000, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, XP_RESERVE, LAPIS_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getCooldownTicks(level) * 50D, 1), 1);
    statLore(v, C.YELLOW, "* ", getLapisCost(level), 2);
    if (getConfig().xpLevelCost > 0) {
      statLore(v, C.YELLOW, "* ", getConfig().xpLevelCost, EnchantingMessages.OFFER_REROLL_LORE_COST_XP);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || p.hasCooldown(EnchantingRerolls.ENCHANTING_TABLE)) {
      return;
    }

    BlockRef table = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (table == null || !table.type().equals(EnchantingRerolls.ENCHANTING_TABLE)) {
      return;
    }

    Loc tableTop = table.location().add(0.5D, 1.0D, 0.5D);
    if (p.level() - getConfig().xpLevelCost < preference(p, XP_RESERVE).amount()) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }

    int lapisCost = getLapisCost(level);
    if (!EnchantingRerolls.hasLapis(p, lapisCost + preference(p, LAPIS_RESERVE).amount())) {
      return;
    }
    if (preferenceEnabled(p, CONFIRM) && !PreferenceConfirmation.confirm(this, p, "reroll:" + blockKey(table), p.inventory().mainHand())) {
      e.setCancelled(true);
      return;
    }
    EnchantingRerolls.Outcome outcome = EnchantingRerolls.reroll(this, p, lapisCost, getConfig().xpLevelCost);
    if (outcome == EnchantingRerolls.Outcome.UNPAID) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }
    if (outcome == EnchantingRerolls.Outcome.FAILED) {
      return;
    }

    int cooldownTicks = getCooldownTicks(level);
    p.setCooldown(EnchantingRerolls.ENCHANTING_TABLE, cooldownTicks);
    AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
    e.setCancelled(true);

    rerollSurgeFx(tableTop);
    xp(p, getConfig().xpGainOnReroll);
    addStat(p, "enchanting.offer-reroll.rerolls", 1);
  }

  private static String blockKey(BlockRef block) {
    return block.world().name() + ":" + block.x() + "," + block.y() + "," + block.z();
  }

  private void rerollSurgeFx(Loc tableTop) {
    timeline(tableTop)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20.0D)
        .frame((f, tick, progress) -> {
          if (tick < 4) {
            f.dustRing(1.5D - (0.3D * tick), 12, 0.9F);
          }
          if (tick < 6) {
            f.particle(Particles.ENCHANTMENT_TABLE, 4, 0, 0.4D, 0, 0.6D, 0.1D);
          } else {
            f.helix(Particles.PORTAL, (1.2D * (1.0D - ((progress - 0.6D) / 0.4D))) + 0.15D, 1.4D, 4, progress * Math.PI * 4.0D);
          }
          if (tick == 0) {
            f.particle(Particles.FLASH, 1, 0, 0.5D, 0, 0, 0)
                .sound(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
          }
          if ((tick & 1) == 0 && tick <= 8) {
            f.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, (float) (0.85D + (0.45D * progress)));
          }
          if (tick == 8) {
            f.sound(Sounds.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.0F);
          }
        })
        .start();
  }

  private int getLapisCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().lapisCostBase - (getLevelPercent(level) * getConfig().lapisCostFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  @ConfigDescription("Sneak-right-click an enchanting table to reroll offers for lapis and XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisCostBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 220;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Level Cost for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int xpLevelCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Gain On Reroll for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpGainOnReroll = 15;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }
}
