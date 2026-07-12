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

package art.arcane.adapt.content.adaptation.discovery;

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
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscoveryRelicAppraiser extends SimpleAdaptation<DiscoveryRelicAppraiser.Config> {
  private static final long COOLDOWN_MILLIS = 400L;

  private final Cooldowns cooldowns = cooldowns();
  private final NamespacedKey appraisedKey = new NamespacedKey(Adapt.instance, "relic_appraised");

  public DiscoveryRelicAppraiser() {
    super("discovery-relic-appraiser");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(3300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_discovery_appraiser_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DECORATED_POT)
            .key("challenge_discovery_appraiser_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_appraiser_50", "discovery.relic-appraiser.appraised", 50, 300);
    registerMilestone("challenge_discovery_appraiser_500", "discovery.relic-appraiser.appraised", 500, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor), 0), 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isAppraisable(hand.getType())) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldowns.isReady(id, COOLDOWN_MILLIS)) {
      return;
    }

    ItemMeta meta = hand.getItemMeta();
    if (meta == null) {
      return;
    }

    if (meta.getPersistentDataContainer().has(appraisedKey, PersistentDataType.BYTE)) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.7F);
      return;
    }

    cooldowns.mark(id);
    if (action == Action.RIGHT_CLICK_BLOCK) {
      e.setCancelled(true);
    }

    double reward = appraiseXp(getLevelPercent(level), getConfig().appraiseXpBase, getConfig().appraiseXpFactor)
        * rarityWeight(hand.getType());
    meta.getPersistentDataContainer().set(appraisedKey, PersistentDataType.BYTE, (byte) 1);
    List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
    lore.add(C.LIGHT_PURPLE + "" + C.ITALIC + Localizer.dLocalize("discovery.relic_appraiser.lore_tag"));
    meta.setLore(lore);
    hand.setItemMeta(meta);
    p.getInventory().setItemInMainHand(hand);

    xp(p, reward);
    getPlayer(p).getData().addStat("discovery.relic-appraiser.appraised", 1);

    timeline(p)
        .duration(12)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((f, tick, progress) -> {
          f.helix(Particle.WAX_ON, 0.4D, 1.5D, 5, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F, Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.4F, 1.6F);
          }
        })
        .onComplete(() -> fx(p.getEyeLocation(), FxPriority.TRANSITION).particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.4, 0, 0.3, 0.02))
        .start();
  }

  private boolean isAppraisable(Material type) {
    String n = type.name();
    return n.startsWith("MUSIC_DISC_")
        || n.endsWith("_POTTERY_SHERD")
        || n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")
        || n.endsWith("_HEAD")
        || n.endsWith("_SKULL");
  }

  private double rarityWeight(Material type) {
    String n = type.name();
    if (n.startsWith("MUSIC_DISC_")) {
      return getConfig().discRarityWeight;
    }

    if (n.endsWith("_HEAD") || n.endsWith("_SKULL")) {
      return getConfig().headRarityWeight;
    }

    if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) {
      return getConfig().trimRarityWeight;
    }

    return getConfig().sherdRarityWeight;
  }

  static double appraiseXp(double levelPercent, double base, double factor) {
    return Math.max(0D, base + (levelPercent * factor));
  }


  @ConfigDescription("Sneak-right-click rare drops to appraise them for Discovery XP; appraised items gain a lore tag.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Appraise Xp Base for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Appraise Xp Factor for the Discovery Relic Appraiser adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double appraiseXpFactor = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to music disc appraisals.", impact = "Higher values pay more Discovery XP for discs.")
    double discRarityWeight = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to head and skull appraisals.", impact = "Higher values pay more Discovery XP for mob heads.")
    double headRarityWeight = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to armor trim template appraisals.", impact = "Higher values pay more Discovery XP for trims.")
    double trimRarityWeight = 1.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Rarity multiplier applied to pottery sherd appraisals.", impact = "Higher values pay more Discovery XP for sherds.")
    double sherdRarityWeight = 1.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 5;
      initialCost = 3;
    }
  }
}
