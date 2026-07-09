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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;


public class CraftingXP extends SimpleAdaptation<CraftingXP.Config> {
  private final Cooldowns xpCooldown = cooldowns();


  public CraftingXP() {
    super("crafting-xp");
    registerConfiguration(CraftingXP.Config.class);
    setIcon(Material.ENCHANTED_BOOK);
    setInterval(5580);
    AdvancementSpec xp25k = AdvancementSpec.challenge(
        "challenge_crafting_xp_25k",
        Material.EXPERIENCE_BOTTLE,
        Localizer.dLocalize("advancement.challenge_crafting_xp_25k.title"),
        Localizer.dLocalize("advancement.challenge_crafting_xp_25k.description")
    );
    AdvancementSpec xp1k = AdvancementSpec.challenge(
        "challenge_crafting_xp_1k",
        Material.CRAFTING_TABLE,
        Localizer.dLocalize("advancement.challenge_crafting_xp_1k.title"),
        Localizer.dLocalize("advancement.challenge_crafting_xp_1k.description")
    ).withChild(xp25k);
    registerAdvancementSpec(xp1k);
    registerStatTracker(xp1k.statTracker("crafting.xp.items-crafted", 1000, 300));
    registerStatTracker(xp25k.statTracker("crafting.xp.items-crafted", 25000, 1500));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("crafting.xp.lore1"));
  }

  @EventHandler(priority = EventPriority.LOW)
  public void on(CraftItemEvent e) {
    Player p = (Player) e.getWhoClicked();
    if (e.getInventory().getResult() != null && hasActiveAdaptation(p) && e.getInventory().getResult().getAmount() > 0) {
      if (e.getInventory().getResult() != null && e.getCursor() != null && e.getCursor().getAmount() < 64) {
        if (p.getInventory().addItem(e.getCurrentItem()).isEmpty()) {
          p.getInventory().removeItem(e.getCurrentItem());
          if (!xpCooldown.isReady(p.getUniqueId(), 20000)) {
            return;
          }
          xpCooldown.mark(p.getUniqueId());
          int level = getLevel(p);
          p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(level * 2);
          addStat(p, "crafting.xp.items-crafted", 1);
          xpShimmer(p.getLocation().add(0, 1, 0), level);
        }
      }
    }
  }

  private void xpShimmer(Location center, int level) {
    float pickupPitch = (float) Math.min(2.0D, 0.8D + (level * 0.05D));
    FxEmitter fx = fx(center, FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, Math.min(14, 6 + level), 0, 0.2D, 0, 0.4D, 0.4D)
        .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, pickupPitch, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.3F);
    if (level >= 5) {
      fx.burst(Particle.GLOW, 3, 0.2D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.8F);
    }
  }


  @Override
  public void onTick() {
  }

  @ConfigDescription("Gain passive XP when crafting items.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
