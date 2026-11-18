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
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

public class AxeShieldSplitter extends SimpleAdaptation<AxeShieldSplitter.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private final Map<UUID, PendingDisable> pendingDisables = playerState();

  public AxeShieldSplitter() {
    super("axe-shield-splitter");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("shield"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_axe_shield_splitter_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_shield_splitter_250", "axe.shield-splitter.shields-broken", 250, 700);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "+ ", Form.duration(getDisableTicks(level) * 50D, 1), 1);
    statLore(v, C.RED, "+ ", Form.pc(getBonusDamagePct(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.MeleeContext combat = resolveMeleeContext(e, this::isAxe);
    if (combat == null) {
      return;
    }

    LivingRef target = combat.target();
    if (!isActivelyBlocking(target)) {
      return;
    }

    PlayerRef p = combat.attacker();
    int level = combat.level();
    e.setDamage(e.damage() * (1D + getBonusDamagePct(level)));

    if (target instanceof PlayerRef blocker) {
      int ticks = getDisableTicks(level);
      PendingDisable pending = new PendingDisable(p.uuid());
      pendingDisables.put(blocker.uuid(), pending);
      if (!Tasks.runEntity(blocker, () -> finishShieldDisable(blocker, ticks, pending), 1)) {
        pendingDisables.remove(blocker.uuid(), pending);
      }
    }

    fx(target.location().add(0D, target.height() * 0.6D, 0D), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 8, 0.3D)
        .chord(Sounds.ITEM_SHIELD_BREAK, 0.8F, 0.9F, Sounds.ITEM_AXE_STRIP, 0.5F, 0.7F);

    Tasks.runEntity(p, () -> rewardBreak(p));
  }

  private void rewardBreak(PlayerRef p) {
    if (!p.isOnline()) {
      return;
    }
    addStat(p, "axe.shield-splitter.shields-broken", 1);
    xp(p, getConfig().xpPerBreak);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  @ReceiveCancelledEvents
  public void onOutcome(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef blocker)) {
      return;
    }
    PendingDisable pending = pendingDisables.get(blocker.uuid());
    if (pending != null && e.damager() != null && pending.attackerId().equals(e.damager().uuid())) {
      pending.cancelled = e.isCancelled();
    }
  }

  private void finishShieldDisable(PlayerRef blocker, int ticks, PendingDisable pending) {
    pendingDisables.remove(blocker.uuid(), pending);
    if (!isRuntimeRegistered() || pending.cancelled || !blocker.isOnline() || !blocker.isValid() || blocker.isDead()) {
      return;
    }
    blocker.setCooldown(SHIELD, Math.max(blocker.cooldownTicks(SHIELD), ticks - 1));
  }

  private boolean isActivelyBlocking(LivingRef target) {
    if (target instanceof PlayerRef blocker) {
      return blocker.isBlocking();
    }
    return target.isHandRaised() && hasShield(target);
  }

  private boolean hasShield(LivingRef target) {
    return target.equipment(EquipmentSlot.OFF_HAND).type().equals(SHIELD)
        || target.equipment(EquipmentSlot.MAIN_HAND).type().equals(SHIELD);
  }

  private int getDisableTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().disableTicksBase + (getLevelPercent(level) * getConfig().disableTicksFactor)));
  }

  private double getBonusDamagePct(int level) {
    return getConfig().bonusDamagePctBase + (getLevelPercent(level) * getConfig().bonusDamagePctFactor);
  }

  private static final class PendingDisable {
    private final UUID attackerId;
    private volatile boolean cancelled;

    private PendingDisable(UUID attackerId) {
      this.attackerId = attackerId;
    }

    private UUID attackerId() {
      return attackerId;
    }
  }

  @ConfigDescription("Axe hits on blocking foes disable their shield longer and deal bonus damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base shield-disable duration in ticks before level scaling.", impact = "Higher values keep struck shields disabled longer.")
    double disableTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra shield-disable ticks granted by leveling.", impact = "Higher values extend shield-disable duration with level.")
    double disableTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus damage fraction against a blocking target.", impact = "Higher values punish blocking foes harder at low levels.")
    double bonusDamagePctBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra bonus damage fraction granted by leveling.", impact = "Higher values reward leveling with more damage against blockers.")
    double bonusDamagePctFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per shield broken open.", impact = "Higher values accelerate skill progression from splitting shields.")
    double xpPerBreak = 5;

    public Config() {
      baseCost = 4;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
