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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingInfusionTransfer extends SimpleAdaptation<EnchantingInfusionTransfer.Config> {
  public EnchantingInfusionTransfer() {
    super("enchanting-infusion-transfer");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    setInterval(2300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ANVIL)
        .key("challenge_enchanting_infusion_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_enchanting_infusion_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_infusion_25", "enchanting.infusion-transfer.transfers", 25, 400);
    registerMilestone("challenge_enchanting_infusion_250", "enchanting.infusion-transfer.transfers", 250, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSourceSurvival(level), 0), 1);
    statLore(v, C.YELLOW, "* ", getXpCost(level), 2);
  }

  static double infusionSourceSurvival(double base, double factor, double max, double levelPercent) {
    return Math.min(max, base + (levelPercent * factor));
  }

  static int infusionXpCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  private double getSourceSurvival(int level) {
    return infusionSourceSurvival(getConfig().survivalBase, getConfig().survivalFactor, getConfig().maxSurvival, getLevelPercent(level));
  }

  private int getXpCost(int level) {
    return infusionXpCost(getConfig().xpCostBase, getConfig().xpCostFactor, getConfig().minXpCost, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (e.getRawSlot() != 0
        || e.getClick() != ClickType.RIGHT
        || e.getView().getTopInventory().getType() != InventoryType.ANVIL
        || isItem(e.getCursor())) {
      return;
    }

    Inventory top = e.getView().getTopInventory();
    ItemStack base = top.getItem(0);
    ItemStack source = top.getItem(1);
    if (!isTransferBase(base) || !isItem(source)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Enchantment transfer = pickTransferEnchant(base, source);
    if (transfer == null) {
      return;
    }

    e.setCancelled(true);
    int cost = getXpCost(level);
    if (p.getLevel() < cost) {
      FxPresets.failFizzle(this, p);
      return;
    }

    int enchantLevel = Math.max(1, Math.min(sourceEnchantLevel(source, transfer), transfer.getMaxLevel()));
    ItemStack newBase = base.clone();
    ItemMeta baseMeta = newBase.getItemMeta();
    baseMeta.addEnchant(transfer, enchantLevel, true);
    newBase.setItemMeta(baseMeta);
    top.setItem(0, newBase);

    boolean survives = ThreadLocalRandom.current().nextDouble() < getSourceSurvival(level);
    top.setItem(1, survives ? reduceSource(source, transfer) : null);

    p.setLevel(Math.max(0, p.getLevel() - cost));
    addStat(p, "enchanting.infusion-transfer.transfers", 1);
    xp(p, getConfig().skillXpOnTransfer);
    Adapt.actionbar(p, C.LIGHT_PURPLE + AdaptLanguage.text(
        EnchantingMessages.INFUSION_TRANSFER_MESSAGE,
        trusted("enchantment", transfer.getKey().getKey().replace('_', ' ')),
        trusted("level", enchantLevel)
    ));
    transferFx(p);
    J.runEntity(p, p::updateInventory, 1);
  }

  private boolean isTransferBase(ItemStack item) {
    return isItem(item)
        && item.getType() != Material.ENCHANTED_BOOK
        && item.getType() != Material.BOOK;
  }

  private Enchantment pickTransferEnchant(ItemStack base, ItemStack source) {
    Map<Enchantment, Integer> sourceEnchants = readEnchants(source);
    Enchantment best = null;
    int bestLevel = 0;
    for (Map.Entry<Enchantment, Integer> entry : sourceEnchants.entrySet()) {
      Enchantment enchant = entry.getKey();
      if (base.containsEnchantment(enchant) || !enchant.canEnchantItem(base)) {
        continue;
      }

      if (entry.getValue() > bestLevel) {
        best = enchant;
        bestLevel = entry.getValue();
      }
    }

    return best;
  }

  private Map<Enchantment, Integer> readEnchants(ItemStack item) {
    if (item.getItemMeta() instanceof EnchantmentStorageMeta stored) {
      return stored.getStoredEnchants();
    }

    return item.getEnchantments();
  }

  private int sourceEnchantLevel(ItemStack source, Enchantment enchant) {
    return readEnchants(source).getOrDefault(enchant, 1);
  }

  private ItemStack reduceSource(ItemStack source, Enchantment transfer) {
    ItemStack reduced = source.clone();
    ItemMeta meta = reduced.getItemMeta();
    if (meta instanceof EnchantmentStorageMeta stored) {
      stored.removeStoredEnchant(transfer);
      if (stored.getStoredEnchants().isEmpty()) {
        ItemStack plain = new ItemStack(Material.BOOK);
        plain.setAmount(reduced.getAmount());
        return plain;
      }
      reduced.setItemMeta(stored);
      return reduced;
    }

    meta.removeEnchant(transfer);
    reduced.setItemMeta(meta);
    return reduced;
  }

  private void transferFx(Player p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.7D * (1.0D - progress)) + 0.15D, 1.5D, 3, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.CRIT_MAGIC, 4, 0, 0.5D, 0, 0.3D, 0.05D)
                .sound(Sound.BLOCK_ANVIL_USE, 0.6F, 1.3F);
          }
          if (tick == 4) {
            f.column(Particles.END_ROD, 4, 1.3D)
                .chord(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
          }
        })
        .start();
  }


  @ConfigDescription("Right-click the base item in an anvil to move one enchantment onto it from the sacrifice item.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Survival Base for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Survival Factor for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double survivalFactor = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Survival for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxSurvival = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Base for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Cost Factor for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpCostFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Xp Cost for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minXpCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Transfer for the Enchanting Infusion Transfer adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnTransfer = 20;

    public Config() {
      baseCost = 6;
      costFactor = 0.8;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
