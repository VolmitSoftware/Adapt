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

package art.arcane.adapt.content.adaptation.hunter;

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
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;
import java.util.UUID;

public class HunterDropToInventory extends SimpleAdaptation<HunterDropToInventory.Config> {
  private final Cooldowns catchFx = cooldowns();

  public HunterDropToInventory() {
    super("hunter-drop-to-inventory");
    registerConfiguration(HunterDropToInventory.Config.class);
    setIcon(Material.TRAPPED_CHEST);
    setInterval(18440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_hunter_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_hunter_dti_10k", "hunter.drop-to-inv.items-caught", 10000, 500);
  }

  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("hunter.drop_to_inventory.lore1"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {

    Player p = e.getPlayer();
    if (resolveInteractContext(p, e.getBlock().getLocation(), null, true) == null
        || !canPVP(p, e.getBlock().getLocation())) {
      return;
    }

    if (ItemListings.toolSwords.contains(p.getInventory().getItemInMainHand().getType())) {
      List<Item> items = new KList<>(e.getItems());
      e.getItems().clear();
      for (Item i : items) {
        if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
          p.getWorld().dropItem(p.getLocation(), i.getItemStack());
        }
      }
      addStat(p, "hunter.drop-to-inv.items-caught", items.size());
      emitCatch(p, e.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), items.size());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    LivingEntity k = e.getEntity();
    if (k.getKiller() == null || k.getKiller().getType() != EntityType.PLAYER) {
      return;
    }
    Player p = k.getKiller();
    if (e.getEntity() instanceof Player || getActiveDamageLevel(p, e.getEntity()) <= 0) {
      return;
    }
    if (e.getEntity().getKiller() != null && e.getEntity().getKiller().getClass().getSimpleName().equals("CraftPlayer")) {
      int itemCount = e.getDrops().size();
      e.getDrops().forEach(i -> {
        if (!p.getInventory().addItem(i).isEmpty()) {
          p.getWorld().dropItem(p.getLocation(), i);
        }
      });
      e.getDrops().clear();
      addStat(p, "hunter.drop-to-inv.items-caught", itemCount);
      emitCatch(p, e.getEntity().getLocation(), itemCount);
    }
  }

  private void emitCatch(Player p, Location origin, int itemCount) {
    UUID id = p.getUniqueId();
    if (!catchFx.isReady(id, 150L)) {
      return;
    }
    catchFx.mark(id);

    Location to = p.getLocation().add(0, 1.0D, 0);
    fx(origin, FxPriority.AMBIENT)
        .particle(Particle.WAX_ON, 4, 0, 0.2D, 0, 0.15D, 0)
        .line(Particle.GLOW, to.getX(), to.getY(), to.getZ(), 3)
        .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);

    if (itemCount >= 6) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.AMBIENT)
          .cullRadius(20)
          .frame((f, tick, progress) -> {
            f.helix(Particle.GLOW, 0.5D, 1.6D, 12, progress * Math.PI * 2.0D);
            if (tick == 0) {
              f.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.05F);
            }
          })
          .start();
    }
  }

  @Override
  public void onTick() {
  }

  @ConfigDescription("Mob and block drops teleport directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
