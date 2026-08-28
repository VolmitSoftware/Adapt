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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public class CraftingSignature extends SimpleAdaptation<CraftingSignature.Config> {
  private final NamespacedKey signatureKey = new NamespacedKey(Adapt.instance, "crafting_signature_owner");

  public CraftingSignature() {
    super("crafting-signature");
    registerConfiguration(Config.class);
    setIcon(Material.WRITABLE_BOOK);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EMERALD)
        .key("challenge_crafting_signature_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.EMERALD_BLOCK)
            .key("challenge_crafting_signature_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_signature_100", "crafting.signature.signed-trades", 100, 400);
    registerMilestone("challenge_crafting_signature_1k", "crafting.signature.signed-trades", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.SIGNATURE_LORE1));
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.SIGNATURE_LORE2));
  }

  static int tradeAmplifier(int base, double factor, int max, double levelPercent) {
    return Math.max(0, Math.min(max, base + (int) Math.floor(levelPercent * factor)));
  }

  private int getTradeAmplifier(int level) {
    return tradeAmplifier(getConfig().amplifierBase, getConfig().amplifierFactor, getConfig().amplifierMax, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    Recipe recipe = e.getRecipe();
    if (!(recipe instanceof ShapedRecipe) && !(recipe instanceof ShapelessRecipe)) {
      return;
    }

    ItemStack result = e.getCurrentItem();
    if (result == null || result.getType().isAir()) {
      return;
    }

    ItemMeta meta = result.getItemMeta();
    if (meta == null || meta.getPersistentDataContainer().has(signatureKey, PersistentDataType.STRING)) {
      return;
    }

    ItemStack signed = result.clone();
    ItemMeta signedMeta = signed.getItemMeta();
    signedMeta.getPersistentDataContainer().set(signatureKey, PersistentDataType.STRING, p.getUniqueId().toString());
    List<String> lore = signedMeta.hasLore() ? new ArrayList<>(signedMeta.getLore()) : new ArrayList<>();
    lore.add(C.DARK_PURPLE + "" + C.ITALIC + AdaptLanguage.text(
        CraftingMessages.SIGNATURE_ITEM_LORE,
        untrusted("player", p.getName())
    ));
    signedMeta.setLore(lore);
    signed.setItemMeta(signedMeta);
    e.setCurrentItem(signed);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Villager villager)) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0 || p.hasPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE) || !carriesSignedGoods(p)) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
        getConfig().tradeDurationTicks, getTradeAmplifier(level), true, true, true), true);
    addStat(p, "crafting.signature.signed-trades", 1);
    fx(villager.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.4D, 0, 0.3D, 0.05D)
        .chord(Sound.ENTITY_VILLAGER_YES, 0.6F, 1.1F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
  }

  private boolean carriesSignedGoods(Player p) {
    String signature = p.getUniqueId().toString();
    for (ItemStack slot : p.getInventory().getStorageContents()) {
      if (slot == null || slot.getType().isAir() || !slot.hasItemMeta()) {
        continue;
      }
      String owner = slot.getItemMeta().getPersistentDataContainer().get(signatureKey, PersistentDataType.STRING);
      if (signature.equals(owner)) {
        return true;
      }
    }
    return false;
  }

  @ConfigDescription("Items you craft carry your signature, and villagers offer better trades while you carry your signed goods.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Trade discount amplifier at level 1.", impact = "Higher values give a larger baseline trade discount.")
    int amplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional trade discount amplifier gained across levels.", impact = "Higher values scale the discount up faster with level.")
    double amplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum trade discount amplifier.", impact = "Higher values raise the ceiling on trade discounts.")
    int amplifierMax = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the trade discount applied on interacting with a villager.", impact = "Higher values keep the discount active longer per interaction.")
    int tradeDurationTicks = 200;

    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
