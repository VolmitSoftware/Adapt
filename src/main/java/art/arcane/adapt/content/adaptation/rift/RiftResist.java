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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;


public class RiftResist extends SimpleAdaptation<RiftResist.Config> {
  private final Cooldowns activationThrottle = cooldowns();

  public RiftResist() {
    super("rift-resist");
    registerConfiguration(Config.class);
    setIcon(Material.SCULK_VEIN);
    setInterval(10288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_resist_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_rift_resist_200", "rift.resist.activations", 200, 300);
  }

  static void riftResistStackAdd(Adaptation<?> source, Player p, int duration, int amplifier) {
    Location location = p.getLocation();
    if (location.getWorld() == null) {
      return;
    }

    boolean refresh = p.getPotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE) != null;
    p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE, duration, amplifier, true, false, false));

    if (refresh) {
      Fx.now(source, location, FxPriority.TRANSITION)
          .particle(Particle.REVERSE_PORTAL, 4, 0, 1.1, 0, 0.2, 0.02)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
      return;
    }

    Fx.now(source, location, FxPriority.TRANSITION)
        .dome(Particles.ENCHANTMENT_TABLE, 0.9, 14)
        .particle(Particle.WITCH, 8, 0, 1.0, 0, 0.4, 0.01)
        .chord(Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 0.8f);
  }

  public static boolean hasRiftResistPerk(AdaptPlayer p) {
    PlayerSkillLine line = p.getData().getSkillLineNullable("rift");
    PlayerAdaptation adaptation = line == null ? null : line.getAdaptation("rift-resist");
    return adaptation != null && adaptation.getLevel() > 0;
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.resist.lore1"));
    v.addLore(C.UNDERLINE + Localizer.dLocalize("rift.resist.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    ItemStack hand = p.getInventory().getItemInMainHand();

    if (e.getAction() == Action.RIGHT_CLICK_AIR) {
      switch (hand.getType()) {
        case ENDER_EYE, ENDER_PEARL -> {
          if (!activationThrottle.isReady(p.getUniqueId(), Math.max(0L, getConfig().activationCooldownMillis))) {
            return;
          }
          activationThrottle.mark(p.getUniqueId());
          xp(p, 3);
          riftResistStackAdd(this, p, getConfig().duration, getConfig().amplitude);
          addStat(p, "rift.resist.activations", 1);
        }
      }
    }

  }


  @ConfigDescription("Gain resistance when using Ender items and abilities.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplitude for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int amplitude = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration for the Rift Resist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int duration = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between manual right-click-air activations in milliseconds.", impact = "Higher values slow repeated activations and the XP they grant; lower values allow faster reuse.")
    long activationCooldownMillis = 4000;

    public Config() {
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 5;
    }
  }
}