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

package art.arcane.adapt.content.adaptation.pickaxe;

import java.util.UUID;
import java.util.Map;
import art.arcane.adapt.api.notification.AdaptHud;
import java.util.List;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class PickaxeUnbreakablePact extends SimpleAdaptation<PickaxeUnbreakablePact.Config> {
  private final Map<UUID, Long> noticeTimes = playerState();
  public PickaxeUnbreakablePact() {
    super("pickaxe-unbreakable-pact");
    registerConfiguration(PickaxeUnbreakablePact.Config.class);
    setIcon(Material.ANVIL);
    setInterval(9122);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_PICKAXE)
        .key("challenge_pickaxe_pact_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_pact_100", "pickaxe.unbreakable-pact.saves", 100, 400);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.NOTICE);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.UNBREAKABLE_PACT_LORE1));
    statLore(v, Form.pc(getIgnoreChance(level), 0), 2);
  }

  private double getIgnoreChance(int level) {
    return Math.min(getConfig().maxIgnoreChance, level * getConfig().ignoreChancePerLevel);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerItemDamageEvent e) {
    ItemStack item = e.getItem();
    if (!isPickaxe(item)) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (preferenceEnabled(p, PickaxePreferences.NOTICE) && item.getItemMeta() instanceof Damageable current) {
      int maximum = current.hasMaxDamage() ? current.getMaxDamage() : item.getType().getMaxDurability();
      long now = System.currentTimeMillis();
      if (maximum - current.getDamage() - e.getDamage() <= maximum / 10
          && now - noticeTimes.getOrDefault(p.getUniqueId(), 0L) >= 5_000L) {
        noticeTimes.put(p.getUniqueId(), now);
        AdaptHud.actionBar(p, AdaptLanguage.text(PickaxePreferences.LOW_DURABILITY));
      }
    }

    if (M.r(getIgnoreChance(level))) {
      e.setCancelled(true);
      addStat(p, "pickaxe.unbreakable-pact.damage-ignored", e.getDamage());
      if (M.r(0.2)) {
        fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
            .particle(Particle.WAX_ON, 2, 0, 0.2, 0, 0.15, 0.01);
      }
      return;
    }

    if (!(item.getItemMeta() instanceof Damageable damageable)) {
      return;
    }

    int maxDurability = item.getType().getMaxDurability();
    if (damageable.getDamage() + e.getDamage() < maxDurability) {
      return;
    }

    e.setCancelled(true);
    damageable.setDamage(maxDurability - 1);
    item.setItemMeta(damageable);
    addStat(p, "pickaxe.unbreakable-pact.saves", 1);
    fx(p.getLocation().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particle.CRIT, 10, 0, 0.2, 0, 0.3, 0.1)
        .particle(Particles.CRIT_MAGIC, 6, 0, 0.2, 0, 0.3, 0.05)
        .ring(Particle.WAX_ON, 0.5D, 12, 0.1D)
        .chord(Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.8f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 0.8f, Sound.ITEM_TRIDENT_RETURN, 0.3f, 1.0f);
  }


  @ConfigDescription("Your pickaxe refuses to break, surviving at 1 durability.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance per level to ignore pickaxe durability loss entirely.", impact = "Higher values make the pickaxe lose durability less often at higher levels.")
    double ignoreChancePerLevel = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total chance to ignore durability loss.", impact = "Higher values allow more durability loss to be ignored at max level.")
    double maxIgnoreChance = 0.25;

    public Config() {
      baseCost = 6;
      costFactor = 0.65;
      initialCost = 5;
    }
  }
}
