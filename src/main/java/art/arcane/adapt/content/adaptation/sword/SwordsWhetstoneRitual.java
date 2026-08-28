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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class SwordsWhetstoneRitual extends SimpleAdaptation<SwordsWhetstoneRitual.Config> {
  private static final Color SPARK = Color.fromRGB(0xFFE08A);
  private static final String SHARP_SLOT = "sharp";
  private static final double STRENGTH_DAMAGE_PER_TIER = 3.0D;
  private final Cooldowns ritualCooldown = cooldowns();

  public SwordsWhetstoneRitual() {
    super("sword-whetstone-ritual");
    registerConfiguration(Config.class);
    setIcon(Material.GRINDSTONE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GRINDSTONE)
        .key("challenge_swords_whetstone_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_SWORD)
            .key("challenge_swords_whetstone_1000")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_whetstone_100", "swords.whetstone-ritual.rituals", 100, 400);
    registerMilestone("challenge_swords_whetstone_1000", "swords.whetstone-ritual.rituals", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getBuffStrength(level) + 1D, 0), 1);
    statLore(v, Form.duration(getBuffDurationTicks(level) * 50D, 1), 2);
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND
        || e.getAction() != Action.RIGHT_CLICK_BLOCK
        || e.getClickedBlock() == null
        || e.getClickedBlock().getType() != Material.GRINDSTONE) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isSword(hand)) {
      return;
    }

    if (e.useInteractedBlock() == Event.Result.DENY) {
      if (!ritualCooldown.isReady(p.getUniqueId(), (long) getConfig().cooldownMillis)) {
        e.setCancelled(true);
      }
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int duration = getBuffDurationTicks(level);
    if (duration <= 0) {
      return;
    }

    if (!ritualCooldown.isReady(p.getUniqueId(), (long) getConfig().cooldownMillis)) {
      return;
    }

    int xpCost = getConfig().xpCost;
    if (p.getLevel() < xpCost) {
      e.setCancelled(true);
      fx(p.getLocation().add(0, 1, 0), FxPriority.GAMEPLAY)
          .sound(Sound.BLOCK_GRINDSTONE_USE, 0.4F, 0.6F);
      return;
    }

    if (!applyDurabilityCost(hand, getConfig().durabilityCost)) {
      return;
    }

    e.setCancelled(true);
    p.getInventory().setItemInMainHand(hand);
    p.giveExpLevels(-xpCost);
    ritualCooldown.mark(p.getUniqueId());

    AdaptAttributeService.get().applyTimed(p, getName(), SHARP_SLOT, Attributes.ATTACK_DAMAGE, sharpnessDamage(getBuffStrength(level)), AttributeModifier.Operation.ADD_NUMBER, duration);

    ritualFx(p, level);
    xp(p, getConfig().skillXpOnRitual);
    addStat(p, "swords.whetstone-ritual.rituals", 1);
  }

  private void ritualFx(Player p, int level) {
    int sparks = 4 + (int) Math.round(getLevelPercent(level) * 6D);
    timeline(p)
        .duration(9)
        .priority(FxPriority.TRANSITION)
        .cullRadius(18D)
        .frame((f, tick, progress) -> {
          f.helix(Particle.CRIT, (0.5D * (1D - progress)) + 0.2D, 1.5D, 3, progress * Math.PI * 2D);
          if (tick == 0) {
            f.chord(Sound.BLOCK_GRINDSTONE_USE, 0.9F, 1.2F, Sound.ITEM_AXE_STRIP, 0.5F, 1.5F);
          }
          if (tick == 4) {
            f.dustBurst(SPARK, sparks, 0.3D, 1.0F)
                .chord(Sound.BLOCK_ANVIL_LAND, 0.3F, 1.8F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.4F);
          }
        })
        .start();
  }

  private boolean applyDurabilityCost(ItemStack hand, int durabilityCost) {
    if (!(hand.getItemMeta() instanceof Damageable damageable) || hand.getType().getMaxDurability() <= 0) {
      return true;
    }

    int max = hand.getType().getMaxDurability();
    int next = damageable.getDamage() + durabilityCost;
    if (next >= max) {
      return false;
    }

    damageable.setDamage(next);
    hand.setItemMeta(damageable);
    return true;
  }

  static int buffAmplifier(double strengthBase, double strengthFactor, double levelPercent) {
    return Math.max(0, (int) Math.round(strengthBase + (levelPercent * strengthFactor)));
  }

  static int buffDurationTicks(double durationTicksBase, double durationTicksFactor, double levelPercent) {
    return Math.max(40, (int) Math.round(durationTicksBase + (levelPercent * durationTicksFactor)));
  }

  static double sharpnessDamage(int amplifier) {
    return STRENGTH_DAMAGE_PER_TIER * (amplifier + 1);
  }

  private int getBuffStrength(int level) {
    return buffAmplifier(getConfig().strengthBase, getConfig().strengthFactor, getLevelPercent(level));
  }

  private int getBuffDurationTicks(int level) {
    return buffDurationTicks(getConfig().durationTicksBase, getConfig().durationTicksFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sneak-right-click a grindstone with a sword to grind a temporary sharpness buff for durability and XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strength Base for the Swords Whetstone Ritual adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strengthBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strength Factor for the Swords Whetstone Ritual adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strengthFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Swords Whetstone Ritual adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Swords Whetstone Ritual adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Durability consumed from the sword per ritual.", impact = "Higher values wear the blade faster for each sharpening.")
    int durabilityCost = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience levels consumed per ritual.", impact = "Higher values make each sharpening cost more experience levels.")
    int xpCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum delay between rituals in milliseconds.", impact = "Higher values force more downtime between sharpenings.")
    double cooldownMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Ritual for the Swords Whetstone Ritual adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnRitual = 14;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      maxLevel = 5;
      initialCost = 5;
    }
  }
}
