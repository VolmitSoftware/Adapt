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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationMudlark extends SimpleAdaptation<ExcavationMudlark.Config> {
  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> wetHasteUntil = playerState();

  public ExcavationMudlark() {
    super("excavation-mudlark");
    registerConfiguration(Config.class);
    setIcon(Material.MUD);
    setInterval(4530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.MUD)
        .key("challenge_excavation_mudlark_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_mudlark_1k", "excavation.mudlark.bonus-drops", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusChance(level), 1), 1);
    statLore(v, getHasteAmplifier(level) + 1, 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    if (!isWet(p)) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int hasteDurationTicks = getConfig().hasteDurationTicks;
    if (hasteDurationTicks <= 0) {
      return;
    }

    AdaptAttributeService.get().applyTimed(p, getName(), "haste", Attributes.BLOCK_BREAK_SPEED, hasteScalar(getHasteAmplifier(context.level())), AttributeModifier.Operation.ADD_SCALAR, hasteDurationTicks);

    UUID id = p.getUniqueId();
    long now = M.ms();
    Long until = wetHasteUntil.get(id);
    boolean onset = until == null || now >= until;
    wetHasteUntil.put(id, now + (hasteDurationTicks * MILLIS_PER_TICK));
    if (onset) {
      fx(p.getEyeLocation(), FxPriority.AMBIENT)
          .particle(Particle.SPLASH, 5, 0, 0.2D, 0, 0.3D, 0.02D)
          .sound(Sound.ENTITY_PLAYER_SPLASH, 0.3f, 1.4f);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Material type = e.getBlock().getType();
    if (!isMudlarkBlock(type)) {
      return;
    }

    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    Material bonus = getBonusDrop(type);
    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    e.getBlock().getWorld().dropItemNaturally(drop, new ItemStack(bonus, 1));
    fx(drop, FxPriority.TRANSITION)
        .particle(Particle.SPLASH, 8, 0, 0, 0, 0.25D, 0.01D)
        .particle(Particles.ITEM_CRACK, 3, 0, 0.1D, 0, 0.15D, 0.02D, new ItemStack(bonus))
        .chord(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 1.2f, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.5f);
    addStat(p, "excavation.mudlark.bonus-drops", 1);
    xp(p, getConfig().xpPerBonusDrop);
  }

  private boolean isWet(Player p) {
    if (p.isInWater()) {
      return true;
    }

    if (!p.getWorld().hasStorm()) {
      return false;
    }

    return p.getLocation().getBlock().getLightFromSky() >= 14;
  }

  private boolean isMudlarkBlock(Material type) {
    return switch (type) {
      case CLAY, MUD, MUDDY_MANGROVE_ROOTS, SOUL_SAND, SOUL_SOIL -> true;
      default -> false;
    };
  }

  private Material getBonusDrop(Material type) {
    return switch (type) {
      case CLAY -> Material.CLAY_BALL;
      case MUD, MUDDY_MANGROVE_ROOTS -> Material.MUD;
      case SOUL_SAND -> Material.SOUL_SAND;
      default -> Material.SOUL_SOIL;
    };
  }

  private double getBonusChance(int level) {
    return Math.min(getConfig().maxBonusChance, getConfig().bonusChanceBase + (getLevelPercent(level) * getConfig().bonusChanceFactor));
  }

  private int getHasteAmplifier(int level) {
    return hasteAmplifier(getLevelPercent(level), getConfig().maxHasteLevel);
  }

  static int hasteAmplifier(double levelPercent, double maxHasteLevel) {
    return Math.max(0, (int) Math.round(levelPercent * (maxHasteLevel - 1)));
  }

  static double hasteScalar(int amplifier) {
    return 0.20D * (amplifier + 1);
  }

  @ConfigDescription("Bonus drops from muddy blocks, plus haste while digging in water or rain.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Base for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Factor for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Chance for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Haste Level for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHasteLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Duration Ticks for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Drop for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDrop = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
