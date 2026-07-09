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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.inventorygui.Element;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ExcavationSpelunker extends SimpleAdaptation<ExcavationSpelunker.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public ExcavationSpelunker() {
    super("excavation-spelunker");
    registerConfiguration(ExcavationSpelunker.Config.class);
    setIcon(Material.GOLDEN_HELMET);
    setInterval(20388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_excavation_spelunker_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_ORE)
            .key("challenge_excavation_spelunker_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_excavation_spelunker_1k", "excavation.spelunker.ores-revealed", 1000, 400);
    registerMilestone("challenge_excavation_spelunker_25k", "excavation.spelunker.ores-revealed", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("excavation.spelunker.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore2") + getConfig().rangeMultiplier * level);
    v.addLore(C.YELLOW + Localizer.dLocalize("excavation.spelunker.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || !hasGlowberries(p) || !hasOreInOffhand(p)) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), (long) (1000 * getConfig().cooldown))) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .particle(Particle.SMOKE, 2, 0, 0, 0, 0.1D, 0.01D)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
      return;
    }

    int radius = getConfig().rangeMultiplier * level;
    consumeGlowberry(p);
    searchForOres(p, radius);
    cooldowns.mark(p.getUniqueId());
  }

  private boolean hasGlowberries(Player player) {
    return player.getInventory().getItemInMainHand().getType() == Material.GLOW_BERRIES;
  }

  private void consumeGlowberry(Player player) {
    ItemStack berries = player.getInventory().getItemInMainHand();
    berries.setAmount(berries.getAmount() - 1);
    player.getInventory().setItemInMainHand(berries);
  }

  private boolean hasOreInOffhand(Player player) {
    Material offhandType = player.getInventory().getItemInOffHand().getType();
    return ItemListings.ores.contains(offhandType);
  }

  private void searchForOres(Player p, int radius) {
    Location playerLocation = p.getLocation();
    World world = p.getWorld();
    Material targetOre = p.getInventory().getItemInOffHand().getType();
    ChatColor c = ItemListings.oreColorsChatColor.get(targetOre);
    GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();

    timeline(playerLocation)
        .duration(5)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.END_ROD, 0.5D + (2.0D * progress), 16, 1.0D);
          if (tick == 0) {
            fx.chord(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.6f, 1.0f, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
          }
        })
        .start();

    for (int x = -radius; x <= radius; x++) {
      for (int y = -radius; y <= radius; y++) {
        for (int z = -radius; z <= radius; z++) {
          if (x * x + y * y + z * z > radius * radius) {
            continue;
          }

          Block block = world.getBlockAt(playerLocation.getBlockX() + x, playerLocation.getBlockY() + y, playerLocation.getBlockZ() + z);
          if (block.getType() != targetOre) {
            continue;
          }

          addStat(p, "excavation.spelunker.ores-revealed", 1);
          fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
              .particle(Particle.WAX_ON, 3, 0, 0, 0, 0.2D, 0.02D);

          if (glowingEntities == null) {
            continue;
          }

          Slime slime = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), Slime.class, (s) -> {
            StackExclusion.exclude(s);
            s.setPersistent(false);
            s.setRotation(0, 0);
            s.setInvulnerable(true);
            s.setCollidable(false);
            s.setGravity(false);
            s.setSilent(true);
            s.setAI(false);
            s.setSize(2);
            s.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            s.setMetadata("preventSuffocation", new FixedMetadataValue(Adapt.instance, true));
          });

          try {
            glowingEntities.setGlowing(slime, p, c);
          } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
          }

          J.runEntity(slime, () -> {
            try {
              glowingEntities.unsetGlowing(slime, p);
            } catch (ReflectiveOperationException ex) {
              throw new RuntimeException(ex);
            }

            fx(slime.getLocation().add(0, 0.5, 0), FxPriority.AMBIENT)
                .particle(Particle.SMOKE, 3, 0, 0, 0, 0.15D, 0.01D)
                .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.6f);
            slime.remove();
          }, 5 * 20);
        }
      }
    }
  }


  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof Slime && e.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
      Slime slime = (Slime) e.getEntity();
      if (slime.hasMetadata("preventSuffocation")) {
        e.setCancelled(true);
      } else {
        e.setCancelled(true);
        slime.remove();
      }
    }
  }

  @Override
  public void onTick() {
  }

  @ConfigDescription("See ores through the ground using Glowberries in your main hand.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Multiplier for the Excavation Spelunker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeMultiplier = 5;

    public Config() {
      baseCost = 5;
      costFactor = 1;
      initialCost = 10;
    }
  }
}
