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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
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
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

public class EnchantingEchoOfKnowledge extends SimpleAdaptation<EnchantingEchoOfKnowledge.Config> {
  private static final NamespacedKey CHARGE_KEY = new NamespacedKey("adapt", "echo-charge");
  private final Cooldowns chargeThrottle = cooldowns();

  public EnchantingEchoOfKnowledge() {
    super("enchanting-echo-of-knowledge");
    registerConfiguration(Config.class);
    setIcon(Material.KNOWLEDGE_BOOK);
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.KNOWLEDGE_BOOK)
        .key("challenge_enchanting_echo_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTED_BOOK)
            .key("challenge_enchanting_echo_250")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_echo_25", "enchanting.echo-of-knowledge.levels-charged", 25, 400);
    registerMilestone("challenge_enchanting_echo_250", "enchanting.echo-of-knowledge.levels-charged", 250, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getChargeRate(level), 1), 1);
  }

  static double echoChargeRate(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  private double getChargeRate(int level) {
    return echoChargeRate(getConfig().chargeRateBase, getConfig().chargeRateFactor, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    Player p = e.getPlayer();
    if (e.getAmount() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack book = p.getInventory().getItemInMainHand();
    if (!isChargeableBook(book)) {
      return;
    }

    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
    int gain = Math.max(1, (int) Math.round(e.getAmount() * getChargeRate(level)));
    int threshold = Math.max(1, getConfig().chargeThreshold);
    int charge = meta.getPersistentDataContainer().getOrDefault(CHARGE_KEY, PersistentDataType.INTEGER, 0) + gain;

    Enchantment upgradeable = findUpgradeable(meta.getStoredEnchants());
    if (charge >= threshold && upgradeable != null) {
      int current = meta.getStoredEnchants().get(upgradeable);
      meta.removeStoredEnchant(upgradeable);
      meta.addStoredEnchant(upgradeable, Math.min(upgradeable.getMaxLevel(), current + 1), true);
      meta.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.INTEGER, charge - threshold);
      book.setItemMeta(meta);
      p.getInventory().setItemInMainHand(book);
      addStat(p, "enchanting.echo-of-knowledge.levels-charged", 1);
      xp(p, getConfig().skillXpOnUpgrade);
      Adapt.actionbar(p, C.LIGHT_PURPLE + "+1 " + upgradeable.getKey().getKey().replace('_', ' ')
          + C.GRAY + " " + Localizer.dLocalize("enchanting.echo_of_knowledge.upgraded"));
      upgradeFx(p);
      return;
    }

    if (upgradeable == null) {
      return;
    }

    meta.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.INTEGER, Math.min(charge, threshold));
    book.setItemMeta(meta);
    p.getInventory().setItemInMainHand(book);
    UUID id = p.getUniqueId();
    if (chargeThrottle.isReady(id, 400L)) {
      chargeThrottle.mark(id);
      fx(p.getEyeLocation(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0, 0, 0.3D, 0.03D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F);
    }
  }

  private boolean isChargeableBook(ItemStack item) {
    return isItem(item)
        && item.getType() == Material.ENCHANTED_BOOK
        && item.getItemMeta() instanceof EnchantmentStorageMeta meta
        && !meta.getStoredEnchants().isEmpty();
  }

  private Enchantment findUpgradeable(Map<Enchantment, Integer> stored) {
    for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
      if (entry.getValue() < entry.getKey().getMaxLevel()) {
        return entry.getKey();
      }
    }

    return null;
  }

  private void upgradeFx(Player p) {
    timeline(p)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(18.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.8D * (1.0D - progress)) + 0.15D, 1.6D, 4, progress * Math.PI * 3.0D);
          if (tick == 0) {
            f.dome(Particles.END_ROD, 1.3D, 12)
                .chord(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8F, 1.5F, Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
          }
          if (tick == 5) {
            f.column(Particles.CRIT_MAGIC, 5, 1.5D)
                .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
          }
        })
        .start();
  }


  @ConfigDescription("Hold an enchanted book while collecting XP to charge it and upgrade an enchantment within vanilla caps.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Rate Base for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeRateBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Rate Factor for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargeRateFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Charge Threshold for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int chargeThreshold = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Upgrade for the Enchanting Echo Of Knowledge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnUpgrade = 35;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
