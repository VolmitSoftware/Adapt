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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;


public class EnchantingCurseCleansing extends SimpleAdaptation<EnchantingCurseCleansing.Config> {
  public static final Id ID = Id.adapt("enchanting/curse_cleansing");
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", Text.key("adapt.enchanting.preferences.enchantingcursecleansing.confirmation"), CommonPreferences.Toggle.OFF);

  private static final Id AIR = Id.minecraft("air");

  public EnchantingCurseCleansing() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
    setInterval(1900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("grindstone"))
        .key("challenge_enchanting_cleanse_10")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("shears"))
            .key("challenge_enchanting_cleanse_100")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_cleanse_10", "enchanting.curse-cleansing.curses-removed", 10, 300);
    registerMilestone("challenge_enchanting_cleanse_100", "enchanting.curse-cleansing.curses-removed", 100, 1000);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.GREEN, "+ ", Format.f(getConfig().skillXpPerCurse, 1), 1);
  }

  static double cleanseReward(double xpPerCurse, int curseCount) {
    return Math.max(0.0D, xpPerCurse) * Math.max(0, curseCount);
  }


  @Handle(priority = EventPriority.LOWEST)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    if (e.rawSlot() != 2 || e.viewKind() != InventoryKind.GRINDSTONE) {
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    InventoryRef top = p.openMenuInventory();
    if (top == null) {
      return;
    }
    CleansePlan plan = planCleanse(top.item(0), top.item(1));
    if (plan == null) {
      return;
    }

    if (preferenceEnabled(p, CONFIRM) && !confirm(p, "cleanse", top.item(0), top.item(1))) {
      e.setCancelled(true);
      return;
    }
    performCleanse(p, plan, e, top);
  }

  private void performCleanse(PlayerRef p, CleansePlan plan, InventoryClickEvent e, InventoryRef top) {
    e.setCancelled(true);
    if (!consumeOne(p, top, plan.sourceSlot())) {
      return;
    }

    e.setCurrentItem(AdaptApi.platform().host().items().empty());

    List<ItemRef> overflow = p.inventory().addItem(plan.cleanedItem());
    overflow.forEach(item -> p.world().dropItemNaturally(p.location().position(), item));
    addStat(p, "enchanting.curse-cleansing.curses-removed", plan.cursesRemoved());
    xp(p, cleanseReward(getConfig().skillXpPerCurse, plan.cursesRemoved()));
    HudStatus.show(p, TextFormat.GREEN + Text.key("adapt.enchanting.curse_cleansing.message").withMarkup("count", String.valueOf(plan.cursesRemoved())).resolve());
    cleanseFx(p);
    AdaptApi.scheduler().runEntity(p, p::updateInventory, 1);
  }

  static CleansePlan planCleanse(ItemRef slotA, ItemRef slotB) {
    int sourceSlot = selectSourceSlot(hasStrippableCurse(slotA), hasStrippableCurse(slotB));
    if (sourceSlot < 0) {
      return null;
    }

    ItemRef source = sourceSlot == 0 ? slotA : slotB;
    return new CleansePlan(sourceSlot, stripCurses(source), countCurses(source));
  }

  static int selectSourceSlot(boolean firstCursed, boolean secondCursed) {
    if (firstCursed) {
      return 0;
    }
    return secondCursed ? 1 : -1;
  }

  public static boolean isCurse(Id enchant) {
    return CurseSet.VALUES.contains(enchant);
  }

  public static boolean hasStrippableCurse(ItemRef item) {
    if (item == null || item.isEmpty() || item.type().equals(AIR)) {
      return false;
    }

    for (Id curse : CurseSet.VALUES) {
      if (item.enchantments().containsKey(curse)) {
        return true;
      }
    }

    for (Id curse : CurseSet.VALUES) {
      if (item.storedEnchantments().containsKey(curse)) {
        return true;
      }
    }

    return false;
  }

  public static int countCurses(ItemRef item) {
    int count = 0;
    for (Id curse : CurseSet.VALUES) {
      if (item.enchantments().containsKey(curse)) {
        count++;
      }
    }

    for (Id curse : CurseSet.VALUES) {
      if (item.storedEnchantments().containsKey(curse)) {
        count++;
      }
    }

    return count;
  }

  public static ItemRef stripCurses(ItemRef item) {
    ItemRef cleaned = item.copy();
    cleaned.setAmount(1);
    Map<Id, Integer> directEnchantments = cleaned.enchantments();
    removePresent(CurseSet.VALUES, directEnchantments::containsKey, cleaned::removeEnchantment);

    Map<Id, Integer> storedEnchantments = cleaned.storedEnchantments();
    removePresent(CurseSet.VALUES, storedEnchantments::containsKey, cleaned::removeStoredEnchantment);

    return cleaned;
  }

  static <T> void removePresent(Iterable<T> candidates, Predicate<T> present, Consumer<T> remove) {
    for (T candidate : candidates) {
      if (present.test(candidate)) {
        remove.accept(candidate);
      }
    }
  }

  private boolean consumeOne(PlayerRef p, InventoryRef inventory, int slot) {
    ItemRef source = inventory.item(slot);
    return payItemCost(p, "catalyst",
        source == null || source.isEmpty() ? null : AdaptApi.platform().host().items().create(source.type()), 1, () -> {
          if (!ItemKinds.isItem(source) || source.amount() <= 1) {
            inventory.setItem(slot, null);
            return true;
          }

          ItemRef remainder = source.copy();
          remainder.setAmount(source.amount() - 1);
          inventory.setItem(slot, remainder);
          return true;
        });
  }

  private void cleanseFx(PlayerRef p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.END_ROD, (0.7D * (1.0D - progress)) + 0.15D, 1.5D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.burst(Particles.SMOKE, 8, 0.35D)
                .chord(Sounds.BLOCK_GRINDSTONE_USE, 0.8F, 0.7F, Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.5F, 1.4F);
          }
          if (tick == 4) {
            f.column(Particles.VILLAGER_HAPPY, 5, 1.4D)
                .chord(Sounds.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.3F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.6F);
          }
        })
        .start();
  }


  private static final class CurseSet {
    private static final Set<Id> VALUES = Set.of(Id.minecraft("binding_curse"), Id.minecraft("vanishing_curse"));
  }

  record CleansePlan(int sourceSlot, ItemRef cleanedItem, int cursesRemoved) {
  }

  @ConfigDescription("While sneaking, taking a grindstone result removes curses from the original item first, preserves its other properties, and grants Enchanting XP.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Enchanting skill XP granted for each curse removed.", impact = "Higher values reward curse cleansing with more Enchanting XP.")
    double skillXpPerCurse = 30;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }
}
