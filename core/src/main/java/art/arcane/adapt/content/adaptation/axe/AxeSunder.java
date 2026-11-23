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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.attribute.LegacyModifierKey;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AxeSunder extends SimpleAdaptation<AxeSunder.Config> {
  private static final LegacyModifierKey MODIFIER_KEY = LegacyModifierKey.of(Id.of("adapt", "axe-sunder"));
  private static final Rgb SUNDER_ORANGE = new Rgb(200, 120, 40);
  private static final int MAX_TRACKED = 8_192;
  private final Map<UUID, SunderState> targets = new ConcurrentHashMap<>();

  public AxeSunder() {
    super("axe-sunder");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_axe"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_sunder_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_axe"))
            .key("challenge_axe_sunder_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_axe_sunder_500", "axe.sunder.stacks-applied", 500, 500);
    registerMilestone("challenge_axe_sunder_5k", "axe.sunder.stacks-applied", 5000, 1800);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.RED, "- ", Form.f(getShredPerStack(level), 1), 1);
    statLore(v, C.RED, "x ", getMaxStacks(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getConfig().durationTicks * 50D, 1), 3);
    double toughnessShred = getShredPerStack(level) * getConfig().toughnessShredPerStackRatio;
    if (toughnessShred > 0D) {
      statLore(v, C.RED, "- ", Form.f(toughnessShred, 1), 4);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    if (target.uuid().equals(attacker.uuid()) || !canDamageTarget(attacker, target)) {
      return;
    }

    int level = combat.level();
    int maxStacks = getMaxStacks(level);
    double shred = getShredPerStack(level);
    UUID targetId = target.uuid();

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

    if (!target.hasAttribute(Attributes.ARMOR)) {
      targets.remove(targetId, state);
      return;
    }

    MODIFIER_KEY.setTransient(target, Attributes.ARMOR, -(stacks * shred), AttributeOperation.ADD_VALUE);

    boolean toughness = target.hasAttribute(Attributes.ARMOR_TOUGHNESS);
    double toughnessShred = shred * getConfig().toughnessShredPerStackRatio;
    if (toughnessShred > 0D && toughness) {
      MODIFIER_KEY.setTransient(target, Attributes.ARMOR_TOUGHNESS, -(stacks * toughnessShred), AttributeOperation.ADD_VALUE);
    }

    synchronized (state) {
      state.target = target;
      state.armor = true;
      state.toughness = toughness;
    }

    SunderState tracked = state;
    long capturedGeneration = generation;
    boolean scheduled = Tasks.runEntity(
        target,
        () -> expireOwned(targetId, tracked, capturedGeneration),
        getConfig().durationTicks,
        () -> targets.remove(targetId, tracked)
    );
    if (!scheduled) {
      expireOwned(targetId, tracked, capturedGeneration);
      return;
    }

    fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
        .dustRing(SUNDER_ORANGE, 0.7D, 10, 1.0F)
        .particle(Particles.CRIT_MAGIC, 6, 0D, 0D, 0D, 0.25D, 0.05D)
        .sound(Sounds.ITEM_SHIELD_BREAK, 0.5F, 1.2F);

    Tasks.runEntity(attacker, () -> rewardApplication(attacker));
  }

  private void expireOwned(UUID targetId, SunderState state, long capturedGeneration) {
    LivingRef target;
    boolean armor;
    boolean toughness;
    synchronized (state) {
      if (state.generation != capturedGeneration) {
        return;
      }
      target = state.target;
      armor = state.armor;
      toughness = state.toughness;
    }

    removeModifiers(target, armor, toughness);
    targets.remove(targetId, state);
  }

  private static void removeModifiers(LivingRef target, boolean armor, boolean toughness) {
    if (target == null) {
      return;
    }
    if (armor) {
      MODIFIER_KEY.remove(target, Attributes.ARMOR);
    }
    if (toughness) {
      MODIFIER_KEY.remove(target, Attributes.ARMOR_TOUGHNESS);
    }
  }

  private void rewardApplication(PlayerRef attacker) {
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
    for (SunderState state : targets.values()) {
      LivingRef target;
      boolean armor;
      boolean toughness;
      synchronized (state) {
        target = state.target;
        armor = state.armor;
        toughness = state.toughness;
      }
      if (target == null || !target.isValid()) {
        continue;
      }
      removeModifiers(target, armor, toughness);
    }
    targets.clear();
    super.unregister();
  }

  private static final class SunderState {
    private int stacks;
    private long generation;
    private LivingRef target;
    private boolean armor;
    private boolean toughness;
  }

  @ConfigDescription("Axe hits shred a target's armor in stacking layers that fade over time.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Armor points removed per Sunder stack before level scaling.", impact = "Higher values make each stack strip more armor.")
    double shredPerStackBase = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra armor points per stack granted by leveling.", impact = "Higher values reward leveling with deeper armor shred.")
    double shredPerStackFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of each stack's armor shred also removed from armor toughness.", impact = "Higher values make Sunder bite harder against heavily armored targets; 0 disables toughness shredding.")
    double toughnessShredPerStackRatio = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum Sunder stacks before level scaling.", impact = "Higher values allow more stacks at low levels.")
    int maxStacksBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional maximum stacks granted at maximum level.", impact = "Higher values let higher levels reach more stacks.")
    int maxStacksFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a Sunder application lasts before that layer decays.", impact = "Higher values keep shredded armor down longer between hits.")
    int durationTicks = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per Sunder stack applied.", impact = "Higher values accelerate skill progression from shredding armor.")
    double xpPerStack = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
