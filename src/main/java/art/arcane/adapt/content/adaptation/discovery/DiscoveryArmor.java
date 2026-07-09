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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.math.Sphere;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-discovery-armor".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:discovery-armor");
  private static final long UPDATE_COOLDOWN = TimeUnit.SECONDS.toMillis(3);
  private static final Sphere SPHERE = new Sphere(5);

  private final Cooldowns updateThrottle = cooldowns();

  public DiscoveryArmor() {
    super("discovery-world-armor");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.armor");
    setIcon(Material.TURTLE_HELMET);
    setInterval(305);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_discovery_armor_1hr")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_CHESTPLATE)
            .key("challenge_discovery_armor_24hr")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_armor_1hr", "discovery.armor.ticks-with-bonus", 72000, 400);
    registerMilestone("challenge_discovery_armor_24hr", "discovery.armor.ticks-with-bonus", 1728000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("discovery.armor.lore1") + C.GRAY + ", " + Localizer.dLocalize("discovery.armor.lore2"));
    v.addLore(C.YELLOW + "~ " + Localizer.dLocalize("discovery.armor.lore3") + C.BLUE + " +" + level * 0.25);
  }

  public double getArmorPoints(Material m) {
    return Math.log(Math.min(2000, m.getBlastResistance() * m.getBlastResistance())) + Math.log((m.getHardness() < 0 ? 50 : Math.min(50, m.getHardness() + 25)) * 0.33);
  }

  public double getArmor(Location l, int level) {
    Block center = l.getBlock();
    double armorValue = 0.0;
    double count = 0;

    art.arcane.adapt.util.common.math.Sphere sphere = SPHERE.clone();

    while (sphere.hasNext()) {
      art.arcane.volmlib.util.math.BlockPosition r = sphere.next();
      Block b = center.getRelative(r.getX(), r.getY(), r.getZ());
      if (b.isEmpty() || b.isLiquid())
        continue;

      count++;
      double a = getArmorPoints(b.getType());
      if (Double.isNaN(a) || a < 0) {
        a = 0;
      }
      armorValue += a;
    }

    return Math.min((armorValue / count) * (level / 2D) * 0.65, 10);
  }


  private double getRadius(double factor) {
    return factor * getConfig().radiusFactor;
  }

  private double getStrength(double factor) {
    return Math.pow(factor, getConfig().strengthExponent);
  }


  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) continue;

      if (!updateThrottle.isReady(p.getUniqueId(), UPDATE_COOLDOWN)) continue;
      updateThrottle.mark(p.getUniqueId());

      IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
      if (attribute == null) continue;

      if (!hasActiveAdaptation(p)) {
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);
      } else {
        double oldArmor = 0;
        for (IAttribute.Modifier modifier : attribute.getModifier(MODIFIER, MODIFIER_KEY)) {
          double amount = modifier.getAmount();
          if (!Double.isNaN(amount) && amount > oldArmor) {
            oldArmor = amount;
          }
        }

        double armor = getArmor(p.getLocation(), getLevel(p));
        armor = Double.isNaN(armor) ? 0 : armor;

        double lArmor = M.lerp(oldArmor, armor, 0.3);
        lArmor = Double.isNaN(lArmor) ? 0 : lArmor;
        attribute.setModifier(MODIFIER, MODIFIER_KEY, lArmor, AttributeModifier.Operation.ADD_NUMBER);
        if (lArmor > 0) {
          adaptPlayer.getData().addStat("discovery.armor.ticks-with-bonus", 1);
        }

        if (Math.round(lArmor) != Math.round(oldArmor) && Math.round(lArmor) > 0) {
          fx(p.getLocation(), FxPriority.TRANSITION)
              .dustRing(Color.fromRGB(150, 170, 190), 0.8D, 16, 1.1F)
              .chord(Sound.BLOCK_STONE_PLACE, 0.5F, 0.7F, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.3F, 1.2F);
        } else if (lArmor > 6D && ThreadLocalRandom.current().nextInt(20) == 0) {
          fx(p.getLocation(), FxPriority.AMBIENT)
              .particle(Particles.CRIT_MAGIC, 2, 0, 0, 0, 0.05D, 0.01D);
        }
      }
    }
  }

  @ConfigDescription("Gain passive armor based on nearby block hardness.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Discovery Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int radiusFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strength Exponent for the Discovery Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public double strengthExponent = 1.25;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
