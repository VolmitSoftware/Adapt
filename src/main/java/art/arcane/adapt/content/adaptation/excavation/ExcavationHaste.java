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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.Map;
import java.util.UUID;

public class ExcavationHaste extends SimpleAdaptation<ExcavationHaste.Config> {
  private final Map<UUID, Long> hasteUntil = playerState();
  private final Map<UUID, Integer> hasteLevel = playerState();

  public ExcavationHaste() {
    super("excavation-haste");
    registerConfiguration(ExcavationHaste.Config.class);
    setIcon(Material.GOLDEN_PICKAXE);
    setInterval(4388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SHOVEL)
        .key("challenge_excavation_haste_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SHOVEL)
            .key("challenge_excavation_haste_50k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_excavation_haste_5k", "excavation.haste.blocks-while-hasted", 5000, 400);
    registerMilestone("challenge_excavation_haste_50k", "excavation.haste.blocks-while-hasted", 50000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("excavation.haste.lore1"));
    v.addLore(C.GREEN + "" + (level) + C.GRAY + Localizer.dLocalize("excavation.haste.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    int durationTicks = getHasteDurationTicks();
    AdaptAttributeService.get().applyTimed(p, getName(), "haste", Attributes.BLOCK_BREAK_SPEED,
        hasteAmount(level), AttributeModifier.Operation.ADD_SCALAR, durationTicks);
    addStat(p, "excavation.haste.blocks-while-hasted", 1);

    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    Long until = hasteUntil.get(id);
    boolean onset = until == null || now >= until;
    hasteUntil.put(id, now + (durationTicks * 50L));
    if (onset) {
      hasteLevel.put(id, level);
      fx(p.getEyeLocation(), FxPriority.AMBIENT)
          .helix(Particles.ENCHANTMENT_TABLE, 0.5D, 1.4D, 6, 0)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
      return;
    }

    Integer seen = hasteLevel.get(id);
    if (seen == null || level > seen) {
      hasteLevel.put(id, level);
      fx(e.getBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
          .ring(Particle.WAX_ON, 0.6D, 8, 0.2D)
          .chord(Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.2f, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.5f);
    }
  }

  private int getHasteDurationTicks() {
    return Math.max(40, Math.min(20 * 30, getConfig().hasteDurationTicks));
  }

  static double hasteAmount(int level) {
    return 0.20D * Math.max(1, level);
  }

  @ConfigDescription("Gain Haste while excavating blocks.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks that Hasty Excavator remains active after mining begins.", impact = "Longer durations prevent mining progress from repeatedly changing speed during slow block breaks and are clamped between 40 and 600 ticks.")
    int hasteDurationTicks = 100;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
