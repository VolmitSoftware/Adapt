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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SwordMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.google.common.collect.Multimap;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SwordsHeirloomEdge extends SimpleAdaptation<SwordsHeirloomEdge.Config> {
  private static final Color HEIRLOOM = Color.fromRGB(0xE8C46A);
  private final NamespacedKey flagKey;
  private final NamespacedKey killsKey;
  private final NamespacedKey bonusKey;
  private final NamespacedKey modifierKey;
  private final NamespacedKey loreKey;

  public SwordsHeirloomEdge() {
    super("sword-heirloom-edge");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_SWORD);
    flagKey = new NamespacedKey(Adapt.instance, "heirloom_edge");
    killsKey = new NamespacedKey(Adapt.instance, "heirloom_edge_kills");
    bonusKey = new NamespacedKey(Adapt.instance, "heirloom_edge_bonus");
    modifierKey = new NamespacedKey(Adapt.instance, "heirloom_edge_damage");
    loreKey = new NamespacedKey(Adapt.instance, "heirloom_edge_lore");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_heirloom_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_heirloom_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_heirloom_10", "swords.heirloom-edge.banks", 10, 400);
    registerMilestone("challenge_swords_heirloom_100", "swords.heirloom-edge.banks", 100, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getGrowthPerBank(level), 2), 1);
    statLore(v, getConfig().killsPerBank, 2);
    statLore(v, Form.f(getBonusCap(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PrepareAnvilEvent e) {
    if (!(e.getView().getPlayer() instanceof Player p) || getActiveLevel(p) <= 0) {
      return;
    }

    if (!(e.getView() instanceof AnvilView view)) {
      return;
    }

    String rename = view.getRenameText();
    if (rename == null || rename.isBlank()) {
      return;
    }

    ItemStack result = e.getResult();
    if (result == null || !isSword(result)) {
      return;
    }

    ItemMeta meta = result.getItemMeta();
    if (meta == null) {
      return;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (!pdc.has(flagKey, PersistentDataType.BYTE)) {
      pdc.set(flagKey, PersistentDataType.BYTE, (byte) 1);
      pdc.set(killsKey, PersistentDataType.INTEGER, 0);
      pdc.set(bonusKey, PersistentDataType.DOUBLE, 0D);
    }

    applyHeirloomModifier(result.getType(), meta, pdc.getOrDefault(bonusKey, PersistentDataType.DOUBLE, 0D));
    applyHeirloomLore(meta);
    result.setItemMeta(meta);
    e.setResult(result);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    Player p = e.getEntity().getKiller();
    if (p == null) {
      return;
    }

    J.runEntity(p, () -> bankKill(p));
  }

  private void bankKill(Player p) {
    if (!p.isOnline()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isSword(hand)) {
      return;
    }

    ItemMeta meta = hand.getItemMeta();
    if (meta == null) {
      return;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    if (!pdc.has(flagKey, PersistentDataType.BYTE)) {
      return;
    }

    int perBank = Math.max(1, getConfig().killsPerBank);
    double cap = getBonusCap(level);
    int kills = pdc.getOrDefault(killsKey, PersistentDataType.INTEGER, 0) + 1;
    double bonus = pdc.getOrDefault(bonusKey, PersistentDataType.DOUBLE, 0D);
    boolean banked = false;

    if (kills >= perBank) {
      if (bonus < cap) {
        kills -= perBank;
        bonus = Math.min(cap, bonus + getGrowthPerBank(level));
        banked = true;
      } else {
        kills = perBank;
      }
    }

    pdc.set(killsKey, PersistentDataType.INTEGER, kills);
    pdc.set(bonusKey, PersistentDataType.DOUBLE, bonus);
    applyHeirloomModifier(hand.getType(), meta, bonus);
    applyHeirloomLore(meta);
    hand.setItemMeta(meta);
    p.getInventory().setItemInMainHand(hand);

    if (banked) {
      addStat(p, "swords.heirloom-edge.banks", 1);
      xp(p, getConfig().xpPerBank);
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .dustBurst(HEIRLOOM, 6, 0.3D, 1.0F)
          .burst(Particles.TOTEM, 4, 0.3D)
          .chord(Sound.BLOCK_ANVIL_USE, 0.3F, 1.7F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.3F);
    }
  }

  private void applyHeirloomModifier(Material material, ItemMeta meta, double bonus) {
    Attribute attribute = Attributes.ATTACK_DAMAGE;
    if (attribute == null) {
      return;
    }

    Collection<AttributeModifier> existing = meta.getAttributeModifiers(attribute);
    if (existing != null) {
      for (AttributeModifier modifier : new ArrayList<>(existing)) {
        if (modifierKey.equals(modifier.getKey())) {
          meta.removeAttributeModifier(attribute, modifier);
        }
      }
    }

    if (bonus <= 0) {
      return;
    }

    if (!meta.hasAttributeModifiers()) {
      Multimap<Attribute, AttributeModifier> defaults = material.getDefaultAttributeModifiers();
      for (Map.Entry<Attribute, AttributeModifier> entry : defaults.entries()) {
        meta.addAttributeModifier(entry.getKey(), entry.getValue());
      }
    }

    meta.addAttributeModifier(attribute, new AttributeModifier(modifierKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
  }

  private void applyHeirloomLore(ItemMeta meta) {
    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    PersistentDataContainer data = meta.getPersistentDataContainer();
    String previous = data.get(loreKey, PersistentDataType.STRING);
    String english = SwordMessages.HEIRLOOM_EDGE_NAME.english();
    lore.removeIf(line -> line != null && (line.equals(previous) || line.contains(english)));
    String rendered = C.GOLD + AdaptLanguage.text(SwordMessages.HEIRLOOM_EDGE_NAME);
    lore.add(rendered);
    data.set(loreKey, PersistentDataType.STRING, rendered);
    meta.setLore(lore);
  }

  private double getGrowthPerBank(int level) {
    return getConfig().growthBase + (getLevelPercent(level) * getConfig().growthFactor);
  }

  private double getBonusCap(int level) {
    return getConfig().capBase + (getLevelPercent(level) * getConfig().capFactor);
  }


  @ConfigDescription("Name a sword at an anvil to make it an heirloom that banks a tiny permanent damage bonus every few kills.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Base for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Factor for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cap Base for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double capBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cap Factor for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double capFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Kills required to bank one growth step on the blade.", impact = "Lower values bank power faster; higher values require more kills per step.")
    int killsPerBank = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bank for the Swords Heirloom Edge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBank = 12;

    public Config() {
      baseCost = 6;
      costFactor = 0.72;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
