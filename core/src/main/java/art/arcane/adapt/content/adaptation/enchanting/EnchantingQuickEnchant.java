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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingQuickEnchant extends SimpleAdaptation<EnchantingQuickEnchant.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", EnchantingMessages.PREFERENCE_ENCHANTINGQUICKENCHANT_CONFIRM, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> MODIFIED = CommonPreferences.toggle("modified-click", EnchantingMessages.PREFERENCE_ENCHANTINGQUICKENCHANT_MODIFIED, CommonPreferences.Toggle.OFF);

  public EnchantingQuickEnchant() {
    super("enchanting-quick-enchant");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("writable_book"));
    setInterval(15100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("enchanted_book"))
        .key("challenge_enchanting_quick_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bookshelf"))
            .key("challenge_enchanting_quick_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_quick_100", "enchanting.quick-enchant.books-applied", 100, 300);
    registerMilestone("challenge_enchanting_quick_1k", "enchanting.quick-enchant.books-applied", 1000, 1000);
  }

  private int getTotalLevelCount(int level) {
    return totalLevelCount(level, getConfig().maxPowerBonusLimit, getConfig().maxPowerBonus1PerLevels);
  }

  static int totalLevelCount(int level, int bonusLimit, int levelsPerBonus) {
    return level + (level > bonusLimit ? level / Math.max(1, levelsPerBonus) : 0);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, MODIFIED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getTotalLevelCount(level), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (e.clickedKind() == null) {
      return;
    }

    PlayerRef p = e.player();
    if (e.action() != InventoryAction.SWAP_WITH_CURSOR || e.click() != (preferenceEnabled(p, MODIFIED) ? ClickKind.RIGHT : ClickKind.LEFT)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef current = e.currentItem();
    ItemRef cursor = e.cursor();
    if (e.action() == InventoryAction.SWAP_WITH_CURSOR
        && e.click() == (preferenceEnabled(p, MODIFIED) ? ClickKind.RIGHT : ClickKind.LEFT)
        && (e.slotKind() == SlotKind.CONTAINER
        || e.slotKind() == SlotKind.ARMOR
        || e.slotKind() == SlotKind.QUICKBAR)
        && EnchantingBooks.isBookApplication(current, cursor)) {
      e.setCancelled(true);
      ItemRef item = current.copy();
      ItemRef book = cursor.copy();
      if (preferenceEnabled(p, CONFIRM) && !PreferenceConfirmation.confirm(this, p, "apply", item, book)) {
        return;
      }
      if (book.storedEnchantments().isEmpty()) {
        return;
      }

      EnchantingBooks.Merge merge = EnchantingBooks.merge(item, book.storedEnchantments());
      Map<Id, Integer> addEnchants = merge.added();

      if (merge.power() > getTotalLevelCount(level)) {
        AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
            EnchantingMessages.QUICK_ENCHANT_LIMIT,
            trusted("power", getTotalLevelCount(level))
        ));
        fx(p.eyeLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 6, 0.2D)
            .particle(Particles.CRIT_MAGIC, 2, 0, 0.2D, 0, 0.15D, 0.02D)
            .chord(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 1.7F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
        return;
      }

      if (merge.changed()) {
        EnchantingBooks.writeEnchantments(item, merge.merged());

        xp(p, 50);
        e.setCurrentItem(item);
        addStat(p, "enchanting.quick-enchant.books-applied", 1);
        EnchantingBooks.showAdded(p, addEnchants);
        timeline(p)
            .duration(8)
            .priority(FxPriority.TRANSITION)
            .cullRadius(16.0D)
            .frame((f, tick, progress) -> {
              f.helix(Particles.ENCHANTMENT_TABLE, (0.8D * (1.0D - progress)) + 0.1D, 1.4D, 3, progress * Math.PI * 2.0D);
              if (tick == 0) {
                f.sound(Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.7F);
              }
              if ((tick & 1) == 0) {
                f.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, (float) (1.2D + (0.4D * progress)));
              }
            })
            .start();
        xp(p, 320 * addEnchants.values().stream().mapToInt((i) -> i).sum(), "quick-apply");

        if (EnchantingBooks.settleCursor(e, book, merge.leftover())) {
          fx(p.eyeLocation(), FxPriority.TRANSITION)
              .particle(Particles.CRIT_MAGIC, 4, 0, 0, 0, 0.2D, 0.05D)
              .particle(Particles.END_ROD, 2, 0, 0.3D, 0, 0.1D, 0.02D)
              .sound(Sounds.ITEM_BOOK_PAGE_TURN, 0.8F, 1.4F);
        }
      }
    }
  }

  @ConfigDescription("Enchant items by clicking enchant books directly on them.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Power Bonus Limit for the Enchanting Quick Enchant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxPowerBonusLimit = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Power Bonus1Per Levels for the Enchanting Quick Enchant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxPowerBonus1PerLevels = 3;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
