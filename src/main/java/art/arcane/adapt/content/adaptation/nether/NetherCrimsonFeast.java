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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class NetherCrimsonFeast extends SimpleAdaptation<NetherCrimsonFeast.Config> {
  private final Cooldowns eatCooldowns = cooldowns();

  public NetherCrimsonFeast() {
    super("nether-crimson-feast");
    registerConfiguration(Config.class);
    setIcon(Material.CRIMSON_FUNGUS);
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CRIMSON_FUNGUS)
        .key("challenge_nether_feast_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WARPED_FUNGUS)
            .key("challenge_nether_feast_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_feast_100", "nether.crimson-feast.fungi-eaten", 100, 300);
    registerMilestone("challenge_nether_feast_2500", "nether.crimson-feast.fungi-eaten", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getFloraFood(level), 1);
    statLore(v, C.YELLOW, "+ ", Form.f(getFloraSaturation(level), 1), 2);
    statLore(v, Form.duration(getResistTicks(level) * 50D, 1), 3);
  }

  @ReceiveCancelledEvents
  @EventHandler
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    ItemStack item = e.getItem();
    if (item == null || !isEdibleFlora(item.getType())) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      boolean vetoed = e.getClickedBlock() != null
          ? e.useInteractedBlock() == Event.Result.DENY
          : e.useItemInHand() == Event.Result.DENY;
      if (vetoed) {
        if (!eatCooldowns.isReady(p.getUniqueId(), getConfig().eatCooldownMillis)) {
          e.setCancelled(true);
        }
        return;
      }

      if (p.getFoodLevel() >= 20 && !p.isSneaking()) {
        return;
      }
      if (!eatCooldowns.isReady(p.getUniqueId(), getConfig().eatCooldownMillis)) {
        return;
      }

      if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
        e.setCancelled(true);
      }

      eatCooldowns.mark(p.getUniqueId());
      if (p.getGameMode() != GameMode.CREATIVE) {
        item.setAmount(item.getAmount() - 1);
      }

      int newFood = Math.min(20, p.getFoodLevel() + getFloraFood(level));
      p.setFoodLevel(newFood);
      float newSat = Math.min((float) newFood, p.getSaturation() + (float) getFloraSaturation(level));
      p.setSaturation(newSat);
      grantNetherFireResist(p, level);

      addStat(p, "nether.crimson-feast.fungi-eaten", 1);
      xp(p, getConfig().xpPerFungus);
      fx(p.getEyeLocation(), FxPriority.GAMEPLAY)
          .particle(Particle.HAPPY_VILLAGER, 5, 0.2D, 0.1D, 0.2D, 0.1D, 0.0D)
          .particle(Particle.CRIMSON_SPORE, 6, 0.3D, 0.2D, 0.3D, 0.1D, 0.0D)
          .sound(Sound.ENTITY_GENERIC_EAT, 0.6F, 1.1F);
    });
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerItemConsumeEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      grantNetherFireResist(p, level);
      xp(p, getConfig().xpPerNetherMeal);
    });
  }

  private void grantNetherFireResist(Player p, int level) {
    if (!isNether(p)) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, getResistTicks(level), 0, false, false, true), true);
    fx(p.getLocation(), FxPriority.TRANSITION)
        .particle(Particle.FLAME, 3, 0.2D, 0.1D, 0.2D, 0.05D, 0.0D)
        .sound(Sound.BLOCK_FIRE_AMBIENT, 0.25F, 1.2F);
  }

  private boolean isNether(Player p) {
    return p.getWorld().getEnvironment().name().contains("NETHER");
  }

  private boolean isEdibleFlora(Material m) {
    return switch (m) {
      case CRIMSON_FUNGUS, WARPED_FUNGUS, CRIMSON_ROOTS, WARPED_ROOTS, NETHER_SPROUTS,
          WEEPING_VINES, TWISTING_VINES -> true;
      default -> false;
    };
  }

  private int getFloraFood(int level) {
    return floraFood(getLevelPercent(level), getConfig().floraFoodBase, getConfig().floraFoodFactor);
  }

  private double getFloraSaturation(int level) {
    return getConfig().floraSaturationBase + (getLevelPercent(level) * getConfig().floraSaturationFactor);
  }

  private int getResistTicks(int level) {
    return resistTicks(getLevelPercent(level), getConfig().resistTicksBase, getConfig().resistTicksFactor);
  }

  static int floraFood(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }

  static int resistTicks(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }


  @ConfigDescription("Eat nether fungi and warped flora for food, and gain fire resistance from any meal in the Nether.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Flora Food Base for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double floraFoodBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Flora Food Factor for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double floraFoodFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Flora Saturation Base for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double floraSaturationBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Flora Saturation Factor for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double floraSaturationFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Resist Ticks Base for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double resistTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Resist Ticks Factor for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double resistTicksFactor = 140;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Eat Cooldown Millis for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long eatCooldownMillis = 350;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Fungus for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerFungus = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Nether Meal for the Nether Crimson Feast adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerNetherMeal = 3;

    public Config() {
      baseCost = 2;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
