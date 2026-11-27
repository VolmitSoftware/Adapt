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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerItemBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

public class UnarmedPower extends SimpleAdaptation<UnarmedPower.Config> {
  public static final Id ID = Id.adapt("unarmed/power");
  private static final String SLOT_POWER = "power";
  private static final long REFRESH_DURATION_TICKS = 180L;

  public UnarmedPower() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_ingot"));
    setInterval(4444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_power_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_power_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_power_500", "unarmed.power.unarmed-kills", 500, 400);
    registerMilestone("challenge_unarmed_power_5k", "unarmed.power.unarmed-kills", 5000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    reconcile(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getUnarmedDamage(level), 0), 1);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (PlayerProfile adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.player();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef p = e.player();
    reconcileWith(p, p.inventory().item(e.newSlot()), p.inventory().offHand());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent e) {
    reconcileWith(e.player(), e.mainHand(), e.offHand());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryCloseEvent e) {
    reconcile(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDropItemEvent e) {
    reconcileNextTick(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityPickupItemEvent e) {
    if (e.entity() instanceof PlayerRef p) {
      reconcileNextTick(p);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerItemBreakEvent e) {
    reconcileNextTick(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerRespawnEvent e) {
    reconcileNextTick(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerChangedWorldEvent e) {
    reconcile(e.player());
  }

  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    ItemRef mainHand = p.inventory().mainHand();
    ItemRef offHand = p.inventory().offHand();
    if (ItemKinds.isTool(mainHand) || ItemKinds.isTool(offHand)) {
      return;
    }
    double factor = getLevelPercent(attack.level());

    if (factor <= 0) {
      return;
    }
    reconcileWith(p, mainHand, offHand);
    xp(p, 0.321 * factor * e.damage(), "unarmed-hit");
    if (factor >= 0.5) {
      fx(e.entity(), FxPriority.COMBAT)
          .particle(Particles.CRIT, 3, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.35F, (float) (0.9D + (factor * 0.3D)));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    LastDamage last = victim.lastDamage();
    if (last != null
        && last.damager() instanceof PlayerRef p
        && hasActiveAdaptation(p)
        && !ItemKinds.isTool(p.inventory().mainHand())
        && !ItemKinds.isTool(p.inventory().offHand())) {
      addStat(p, "unarmed.power.unarmed-kills", 1);
      fx(victim, FxPriority.COMBAT)
          .particle(Particles.SOUL, 5, 0, 0.4D, 0, 0.2D, 0.03D)
          .sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
    }
  }

  private void reconcile(PlayerRef p) {
    if (p == null || !p.isOnline()) {
      return;
    }
    reconcileWith(p, p.inventory().mainHand(), p.inventory().offHand());
  }

  private void reconcileWith(PlayerRef p, ItemRef mainHand, ItemRef offHand) {
    if (p == null || !p.isOnline()) {
      return;
    }

    int level = getActiveLevel(p);
    double scalar = powerScalar(getLevelPercent(level), getConfig().damageFactor);
    boolean bareHanded = !ItemKinds.isTool(mainHand) && !ItemKinds.isTool(offHand);
    if (shouldApplyPower(level, bareHanded, scalar)) {
      AdaptAttributeService.get().applyTimed(p, getId().toString(), SLOT_POWER, Attributes.ATTACK_DAMAGE, scalar, AttributeOperation.ADD_MULTIPLIED_TOTAL, REFRESH_DURATION_TICKS);
    } else {
      AdaptAttributeService.get().remove(p, getId().toString(), SLOT_POWER, Attributes.ATTACK_DAMAGE);
    }
  }

  private void reconcileNextTick(PlayerRef p) {
    AdaptApi.scheduler().runEntity(p, () -> reconcile(p), 1);
  }

  private double getUnarmedDamage(int level) {
    return powerScalar(getLevelPercent(level), getConfig().damageFactor);
  }

  static double powerScalar(double levelPercent, double damageFactor) {
    return levelPercent * damageFactor;
  }

  static boolean shouldApplyPower(int activeLevel, boolean bareHanded, double scalar) {
    return activeLevel > 0 && bareHanded && scalar != 0D;
  }


  @ConfigDescription("Improved base unarmed damage.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Damage Factor for the Unarmed Power adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 2.57;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 7;
      initialCost = 2;
    }
  }
}
