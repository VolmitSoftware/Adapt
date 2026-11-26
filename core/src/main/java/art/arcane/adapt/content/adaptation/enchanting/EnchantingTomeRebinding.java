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
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingTomeRebinding extends SimpleAdaptation<EnchantingTomeRebinding.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", EnchantingMessages.PREFERENCE_ENCHANTINGTOMEREBINDING_CONFIRM, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak", EnchantingMessages.PREFERENCE_ENCHANTINGTOMEREBINDING_SNEAK, CommonPreferences.Toggle.OFF);

  private static final Id ANVIL = Id.minecraft("anvil");
  private static final Id CHIPPED_ANVIL = Id.minecraft("chipped_anvil");
  private static final Id DAMAGED_ANVIL = Id.minecraft("damaged_anvil");

  public EnchantingTomeRebinding() {
    super("enchanting-tome-rebinding");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("writable_book"));
    setInterval(2100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("writable_book"))
        .key("challenge_enchanting_rebind_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("enchanted_book"))
            .key("challenge_enchanting_rebind_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_rebind_50", "enchanting.tome-rebinding.books-split", 50, 400);
    registerMilestone("challenge_enchanting_rebind_500", "enchanting.tome-rebinding.books-split", 500, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getLossChance(level), 0), 1);
    statLore(v, C.YELLOW, "* ", getXpCost(level), 2);
  }

  static double tomeLossChance(double base, double factor, double levelPercent) {
    return Math.max(0.0D, base - (levelPercent * factor));
  }

  static int tomeXpCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  private double getLossChance(int level) {
    return tomeLossChance(getConfig().lossChanceBase, getConfig().lossChanceFactor, getLevelPercent(level));
  }

  private int getXpCost(int level) {
    return tomeXpCost(getConfig().xpCostBase, getConfig().xpCostFactor, getConfig().minXpCost, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    ItemEntityRef droppedItem = e.itemDrop();
    ItemRef book = droppedItem.item();
    if (!EnchantingBooks.isMultiEnchantBook(book)) {
      return;
    }

    BlockRef target = p.targetBlock(5);
    if (target == null || !isAnvilTarget(target.type())) {
      return;
    }

    int level = getActiveInteractLevel(p, target.location());
    if (level <= 0 || preferenceEnabled(p, SNEAK) && !p.isSneaking()) {
      return;
    }

    int cost = getXpCost(level);
    if (p.level() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    if (preferenceEnabled(p, CONFIRM) && !PreferenceConfirmation.confirm(this, p, "split", book)) {
      e.setCancelled(true);
      return;
    }
    List<ItemRef> books = EnchantingBooks.splitBook(book, getLossChance(level));
    if (!payExperienceCost(p, "experience-levels", cost, () -> EnchantingBooks.spendLevels(p, cost))) {
      return;
    }

    EnchantingBooks.dropSplit(p, droppedItem, books);
    addStat(p, "enchanting.tome-rebinding.books-split", 1);
    xp(p, getConfig().skillXpOnSplit * books.size());
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(
        EnchantingMessages.TOME_REBINDING_MESSAGE,
        trusted("count", books.size())
    ));
    splitFx(p);
  }

  static boolean isAnvilTarget(Id material) {
    return material.equals(ANVIL)
        || material.equals(CHIPPED_ANVIL)
        || material.equals(DAMAGED_ANVIL);
  }

  private void splitFx(PlayerRef p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.ENCHANTMENT_TABLE, (0.4D + progress) * 0.9D, 10, 1.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT_MAGIC, 4, 0, 0.6D, 0, 0.3D, 0.05D)
                .sound(Sounds.ITEM_BOOK_PAGE_TURN, 0.9F, 1.2F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, 4, 1.3D)
                .chord(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }


  @ConfigDescription("Drop a multi-enchant book while aiming at an anvil to split it into single-enchant books.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loss Chance Base for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lossChanceBase = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Loss Chance Factor for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lossChanceFactor = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Base for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostBase = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Factor for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Xp Cost for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minXpCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Split for the Enchanting Tome Rebinding adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnSplit = 14;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
