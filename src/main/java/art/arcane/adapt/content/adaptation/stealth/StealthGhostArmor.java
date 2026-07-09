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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;

public class StealthGhostArmor extends SimpleAdaptation<StealthGhostArmor.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-ghost-armor".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:ghost-armor");

  private final Map<UUID, Double> armorAmount = playerState();
  private final Map<UUID, Boolean> charged = playerState();

  public StealthGhostArmor() {
    super("stealth-ghost-armor");
    registerConfiguration(Config.class);
    setIcon(Material.CHAINMAIL_HELMET);
    setInterval(5353);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_CHESTPLATE)
        .key("challenge_stealth_ghost_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CHAINMAIL_CHESTPLATE)
            .key("challenge_stealth_ghost_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_ghost_100", "stealth.ghost-armor.armor-consumed", 100, 300);
    registerMilestone("challenge_stealth_ghost_500", "stealth.ghost-armor.armor-consumed", 500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getMaxArmorPoints(getLevelPercent(level)), 0), 1);
    statLore(v, Form.f(getMaxArmorPerTick(getLevelPercent(level)), 1), 2);
  }

  public double getMaxArmorPoints(double factor) {
    return M.lerp(getConfig().minArmor, getConfig().maxArmor, factor);
  }

  public double getMaxArmorPerTick(double factor) {
    return M.lerp(getConfig().minArmorPerTick, getConfig().maxArmorPerTick, factor);
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      UUID id = p.getUniqueId();
      IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
      if (attribute == null) {
        continue;
      }

      if (!hasActiveAdaptation(p)) {
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);
        armorAmount.remove(id);
        charged.remove(id);
        continue;
      }
      double oldArmor = 0;
      for (IAttribute.Modifier modifier : attribute.getModifier(MODIFIER, MODIFIER_KEY)) {
        double amount = modifier.getAmount();
        if (!Double.isNaN(amount) && amount > oldArmor) {
          oldArmor = amount;
        }
      }
      double armor = getMaxArmorPoints(getLevelPercent(p));
      armor = Double.isNaN(armor) ? 0 : armor;

      double newAmount = oldArmor;
      if (oldArmor < armor) {
        newAmount = Math.min(armor, oldArmor + getMaxArmorPerTick(getLevelPercent(p)));
        attribute.setModifier(MODIFIER, MODIFIER_KEY, newAmount, AttributeModifier.Operation.ADD_NUMBER);
      } else if (oldArmor > armor) {
        newAmount = armor;
        attribute.setModifier(MODIFIER, MODIFIER_KEY, armor, AttributeModifier.Operation.ADD_NUMBER);
      }
      armorAmount.put(id, newAmount);

      boolean nowCharged = armor > 0 && newAmount >= armor - 1.0E-4D;
      boolean wasCharged = Boolean.TRUE.equals(charged.get(id));
      if (nowCharged && !wasCharged) {
        charged.put(id, Boolean.TRUE);
        timeline(p).duration(6).priority(FxPriority.TRANSITION).cullRadius(24)
            .frame((emit, tick, progress) -> {
              emit.ring(Particles.END_ROD, 0.6D, 8, 1.4D + (progress * 0.3D));
              if (tick == 0) {
                emit.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);
              }
            }).start();
      } else if (!nowCharged) {
        if (wasCharged) {
          charged.put(id, Boolean.FALSE);
        }
        fx(p.getLocation().add(0, 1.0D, 0), FxPriority.AMBIENT).particle(Particle.WAX_ON, 1, 0, 0.3D, 0, 0.1D, 0.01D);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player p && hasActiveAdaptation(p) && e.getDamage() > 0) {
      int damageXP = (int) Math.min(10, 2.5 * e.getDamage());
      xp(p, damageXP);
      addStat(p, "stealth.ghost-armor.armor-consumed", 1);
      UUID id = p.getUniqueId();
      double stored = armorAmount.getOrDefault(id, 0.0D);
      if (stored > 0.1D) {
        armorAmount.put(id, 0.0D);
        charged.put(id, Boolean.FALSE);
        double radius = Math.min(1.6D, 0.8D + (stored * 0.05D));
        fx(p.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
            .ring(Particle.CRIT, radius, 12, 0)
            .burst(Particle.CRIT, 4, 0.25D)
            .dustBurst(4, 0.4D, 1.0F)
            .chord(Sound.ITEM_SHIELD_BREAK, 0.7F, 1.2F, Sound.BLOCK_GLASS_BREAK, 0.5F, 0.8F, Sound.BLOCK_ANVIL_LAND, 0.2F, 1.8F);
      }
      J.runEntity(p, () -> {
        IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_ARMOR);
        if (attribute == null) return;
        attribute.removeModifier(MODIFIER, MODIFIER_KEY);
      });
    }
  }

  @ConfigDescription("Slowly build armor when not taking damage, consumed on the next hit.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmor = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Armor for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxArmorPerTick = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Armor Per Tick for the Stealth Ghost Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minArmorPerTick = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.335;
      maxLevel = 7;
      initialCost = 1;
    }
  }
}
