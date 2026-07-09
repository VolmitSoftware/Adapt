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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class UnarmedFlurry extends SimpleAdaptation<UnarmedFlurry.Config> {
  private final Map<UUID, FlurryState> flurries = playerState();

  public UnarmedFlurry() {
    super("unarmed-flurry");
    registerConfiguration(Config.class);
    setIcon(Material.FEATHER);
    setInterval(4811);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_flurry_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_flurry_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_flurry_1k", "unarmed.flurry.flurry-hits", 1000, 400);
    registerMilestone("challenge_unarmed_flurry_10k", "unarmed.flurry.flurry-hits", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Form.f(getDamagePerStack(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().windowMillis, 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      flurries.remove(p.getUniqueId());
      return;
    }

    long now = System.currentTimeMillis();
    int level = attack.level();
    FlurryState state = flurries.computeIfAbsent(p.getUniqueId(), id -> new FlurryState());
    if (now - state.lastHitMillis > getConfig().windowMillis) {
      state.stacks = 0;
    }

    state.lastHitMillis = now;
    int max = getMaxStacks(level);
    state.stacks = Math.min(max, state.stacks + 1);

    double bonus = state.stacks * getDamagePerStack(level);
    e.setDamage(e.getDamage() + bonus);
    if (state.stacks >= max) {
      fx(e.getEntity().getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
          .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
          .particle(Particle.CRIT, 6, 0, 0, 0, 0.3D, 0.1D)
          .chord(Sound.BLOCK_NOTE_BLOCK_BIT, 0.5F, 1.8F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.7F);
    } else {
      fx(e.getEntity().getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
          .particle(Particle.CRIT, 2, 0, 0, 0, 0.15D, 0.05D)
          .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3F, Math.min(2.0F, 1.2F + (state.stacks * 0.08F)));
    }
    xp(p, bonus * getConfig().xpPerBonusDamage);
    addStat(p, "unarmed.flurry.flurry-hits", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    FlurryState state = flurries.get(e.getPlayer().getUniqueId());
    if (state == null) {
      return;
    }

    ItemStack next = e.getPlayer().getInventory().getItem(e.getNewSlot());
    if (next != null && isTool(next)) {
      flurries.remove(e.getPlayer().getUniqueId());
    }
  }

  private int getMaxStacks(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxStacksBase + (getLevelPercent(level) * getConfig().maxStacksFactor)));
  }

  private double getDamagePerStack(int level) {
    return getConfig().damagePerStackBase + (getLevelPercent(level) * getConfig().damagePerStackFactor);
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Rapid consecutive bare-hand hits build flurry stacks that add bonus damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum flurry stacks at level 1.", impact = "Higher values allow more stacked bonus damage early.")
    double maxStacksBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional maximum flurry stacks granted at max level.", impact = "Higher values raise the stack cap as levels increase.")
    double maxStacksFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus damage added per flurry stack.", impact = "Higher values make each stack hit harder.")
    double damagePerStackBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional per-stack bonus damage granted at max level.", impact = "Higher values make stacks hit harder as levels increase.")
    double damagePerStackFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds between hits before stacks reset.", impact = "Higher values make the flurry easier to maintain.")
    long windowMillis = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per point of flurry bonus damage dealt.", impact = "Higher values speed up unarmed skill progression from flurries.")
    double xpPerBonusDamage = 3.7;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 4;
    }
  }

  private static class FlurryState {
    private int stacks = 0;
    private long lastHitMillis = 0L;
  }
}
