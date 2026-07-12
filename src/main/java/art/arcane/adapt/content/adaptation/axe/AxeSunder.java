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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeSunder extends SimpleAdaptation<AxeSunder.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-axe-sunder".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:axe-sunder");
  private static final Color SUNDER_ORANGE = Color.fromRGB(200, 120, 40);
  private static final int MAX_TRACKED = 8_192;
  private final Map<UUID, SunderState> targets = new ConcurrentHashMap<>();

  public AxeSunder() {
    super("axe-sunder");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_AXE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_sunder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_AXE)
            .key("challenge_axe_sunder_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_axe_sunder_500", "axe.sunder.stacks-applied", 500, 500);
    registerMilestone("challenge_axe_sunder_5k", "axe.sunder.stacks-applied", 5000, 1800);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.RED, "- ", Form.f(getShredPerStack(level), 1), 1);
    statLore(v, C.RED, "x ", getMaxStacks(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getConfig().durationTicks * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    Player attacker = combat.attacker();
    LivingEntity target = combat.target();
    if (target == attacker || !canDamageTarget(attacker, target)) {
      return;
    }

    int level = combat.level();
    int maxStacks = getMaxStacks(level);
    double shred = getShredPerStack(level);
    UUID targetId = target.getUniqueId();

    SunderState state = targets.get(targetId);
    if (state == null) {
      if (targets.size() >= MAX_TRACKED) {
        return;
      }
      state = new SunderState();
      SunderState existing = targets.putIfAbsent(targetId, state);
      if (existing != null) {
        state = existing;
      }
    }

    int stacks;
    long generation;
    synchronized (state) {
      state.stacks = Math.min(state.stacks + 1, maxStacks);
      state.generation++;
      stacks = state.stacks;
      generation = state.generation;
    }

    IAttribute attribute = Version.get().getAttribute(target, Attributes.GENERIC_ARMOR);
    if (attribute == null) {
      targets.remove(targetId, state);
      return;
    }

    attribute.setTransientModifier(MODIFIER, MODIFIER_KEY, -(stacks * shred), AttributeModifier.Operation.ADD_NUMBER);

    SunderState tracked = state;
    long capturedGeneration = generation;
    J.runEntity(target, () -> expireOwned(target, targetId, tracked, capturedGeneration), getConfig().durationTicks);

    fx(target.getLocation().add(0D, target.getHeight() * 0.6D, 0D), FxPriority.COMBAT)
        .dustRing(SUNDER_ORANGE, 0.7D, 10, 1.0F)
        .particle(Particles.CRIT_MAGIC, 6, 0D, 0D, 0D, 0.25D, 0.05D)
        .sound(Sound.ITEM_SHIELD_BREAK, 0.5F, 1.2F);

    J.runEntity(attacker, () -> rewardApplication(attacker));
  }

  private void expireOwned(LivingEntity target, UUID targetId, SunderState state, long capturedGeneration) {
    synchronized (state) {
      if (state.generation != capturedGeneration) {
        return;
      }
    }

    IAttribute attribute = Version.get().getAttribute(target, Attributes.GENERIC_ARMOR);
    if (attribute != null) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    }
    targets.remove(targetId, state);
  }

  private void rewardApplication(Player attacker) {
    if (!attacker.isOnline()) {
      return;
    }
    addStat(attacker, "axe.sunder.stacks-applied", 1);
    xp(attacker, getConfig().xpPerStack);
  }

  private double getShredPerStack(int level) {
    return getConfig().shredPerStackBase + (getLevelPercent(level) * getConfig().shredPerStackFactor);
  }

  private int getMaxStacks(int level) {
    return Math.max(1, getConfig().maxStacksBase + (int) Math.round(getLevelPercent(level) * getConfig().maxStacksFactor));
  }

  @Override
  public void unregister() {
    targets.clear();
    super.unregister();
  }

  private static final class SunderState {
    private int stacks;
    private long generation;
  }

  @ConfigDescription("Axe hits shred a target's armor in stacking layers that fade over time.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Armor points removed per Sunder stack before level scaling.", impact = "Higher values make each stack strip more armor.")
    double shredPerStackBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra armor points per stack granted by leveling.", impact = "Higher values reward leveling with deeper armor shred.")
    double shredPerStackFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum Sunder stacks before level scaling.", impact = "Higher values allow more stacks at low levels.")
    int maxStacksBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional maximum stacks granted at maximum level.", impact = "Higher values let higher levels reach more stacks.")
    int maxStacksFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a Sunder application lasts before that layer decays.", impact = "Higher values keep shredded armor down longer between hits.")
    int durationTicks = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per Sunder stack applied.", impact = "Higher values accelerate skill progression from shredding armor.")
    double xpPerStack = 3;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
