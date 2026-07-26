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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
  public RiftGate() {
    super("rift-gate");
    registerConfiguration(Config.class);
    setIcon(Material.RESPAWN_ANCHOR);
    setInterval(1322);
    if (getConfig().requireCraftedEye) {
      registerRecipe(AdaptRecipe.shapeless()
          .key("rift-recall-gate")
          .ingredient(Material.ENDER_PEARL)
          .ingredient(Material.AMETHYST_SHARD)
          .ingredient(Material.EMERALD)
          .result(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)))
          .build());
    }
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_gate_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_gate_50k_dist")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_gate_100", "rift.gate.teleports", 100, 400);
    registerMilestone("challenge_rift_gate_50k_dist", "rift.gate.total-distance", 50000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    if (getConfig().requireCraftedEye) {
      v.addLore(C.YELLOW + AdaptLanguage.text(RiftMessages.GATE_LORE1));
    }
    v.addLore(C.RED + AdaptLanguage.text(RiftMessages.GATE_LORE2));
    v.addLore(C.ITALIC + AdaptLanguage.text(
        RiftMessages.GATE_WARNING,
        trusted("danger", C.UNDERLINE + "" + C.RED + AdaptLanguage.text(RiftMessages.GATE_LORE4))
    ));
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.text(
        getConfig().requireCraftedEye ? RiftMessages.GATE_DESCRIPTION : RiftMessages.GATE_DESCRIPTION_FREEHAND
    );
  }


  @ReceiveCancelledEvents
  @EventHandler
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    ItemStack offHand = p.getInventory().getItemInOffHand();
    Location location = e.getClickedBlock() == null ? p.getLocation() : e.getClickedBlock().getLocation();

    // Deny usage if the offhand contains a bindable item
    if (isProtectedEye(offHand) && e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND)) {
      e.setCancelled(true);
      return;
    }

    if (!hand.getType().equals(Material.ENDER_EYE) || !isGateEye(hand)) {
      return;
    }

    if (!hasActiveAdaptation(p) || p.hasCooldown(Material.ENDER_EYE)) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (e.getClickedBlock() != null && e.useInteractedBlock() == Event.Result.DENY) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (e.useItemInHand() == Event.Result.DENY) {
      return;
    }

    Adapt.verbose(" - Player Main hand: " + hand.getType());
    Action action = e.getAction();
    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
      if (p.isSneaking()) {
        e.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR && isBound(hand)) {
          Adapt.verbose("Unlinking eye");
          unlinkEye(p);
        } else {
          Adapt.verbose("Linking eye");
          linkEye(p, location);
        }
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
      if (isBound(hand)) {
        e.setCancelled(true);
        openEye(p);
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    }
  }

  private boolean isGateEye(ItemStack stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return stack.getType().equals(Material.ENDER_EYE);
  }

  private boolean isProtectedEye(ItemStack stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return isBound(stack);
  }


  private boolean isBound(ItemStack stack) {
    return stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null;
  }

  private void unlinkEye(Player p) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!payItemCost(p, "unlink", new ItemStack(hand.getType()), 1, () -> {
      decrementItemstack(hand, p);
      return true;
    })) {
      return;
    }

    ItemStack eye = getConfig().requireCraftedEye
        ? BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null))
        : new ItemStack(Material.ENDER_EYE);
    p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2)
        .chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.5f, 1.4f, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.2f);
  }

  private void linkEye(Player p, Location location) {
    Location center = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 2.0 - (2.0 * progress), 0, 0.02, 0);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.5f, 0.6f, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.2f);
          }
        })
        .start();
    ItemStack hand = p.getInventory().getItemInMainHand();

    payItemCost(p, "bind", new ItemStack(hand.getType()), 1, () -> {
      if (hand.getAmount() == 1) {
        BoundEyeOfEnder.setData(hand, location);
      } else {
        hand.setAmount(hand.getAmount() - 1);
        ItemStack eye = BoundEyeOfEnder.withData(location);
        p.getInventory().addItem(eye).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
      }

      return true;
    });
  }


  private void openEye(Player p) {
    Adapt.verbose("Using eye");
    Location l = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());

    if (!getConfig().consumeOnUse && p.getCooldown(Material.ENDER_EYE) > 0) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            if (tick == 0) {
              fx.burst(Particles.SMOKE, 3, 0.2).sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
            }
            if (tick == 2) {
              fx.sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.5f);
            }
          })
          .start();
      return;
    }
    p.setCooldown(Material.ENDER_EYE, 150);


    if (RiftResist.hasRiftResistPerk(getPlayer(p))) {
      RiftResist.riftResistStackAdd(this, p, 150, 3);
    }

    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 10, true, false, false));
    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 85, 0, true, false, false));
    fx(l, FxPriority.TRANSITION)
        .chord(Sound.BLOCK_LODESTONE_PLACE, 1f, 0.8f, Sound.BLOCK_BELL_RESONATE, 0.7f, 0.9f);

    timeline(p)
        .duration(80)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.dustRing(2.4 - (2.25 * progress), 16, 1.0f);
          fx.helix(Particles.END_ROD, 0.6, 1.6 * progress, 8, -progress * Math.PI * 2.0);
          if (tick % 16 == 0) {
            fx.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, (float) (0.6 + (progress * 0.8)));
          }
        })
        .start();

    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }

      Location from = p.getLocation().clone();
      AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(), getPlayer(p), this, from, l);
      Bukkit.getPluginManager().callEvent(event);
      if (event.isCancelled()) {
        return;
      }

      if (getConfig().consumeOnUse) {
        ItemStack current = p.getInventory().getItemInMainHand();
        if (!isBound(current) || !isSameBlock(BoundEyeOfEnder.getLocation(current), l)) {
          fx(p.getEyeLocation(), FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 3, 0.2)
              .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
          return;
        }
        if (!payItemCost(p, "teleport", new ItemStack(current.getType()), 1, () -> {
          decrementItemstack(current, p);
          return true;
        })) {
          return;
        }

        xp(p, 75);
      }

      addStat(p, "rift.teleports", 1);
      addStat(p, "rift.gate.teleports", 1);
      if (from.getWorld() != null && from.getWorld().equals(l.getWorld())) {
        addStat(p, "rift.gate.total-distance", (int) from.distance(l));
      }
      fx(from, FxPriority.TRANSITION)
          .particle(Particle.REVERSE_PORTAL, 16, 0, 1.0, 0, 0.25, 0.05)
          .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
      J.teleport(p, l, PlayerTeleportEvent.TeleportCause.PLUGIN);
      timeline(l)
          .duration(5)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            fx.ring(Particles.END_ROD, 0.3 + (1.3 * progress), 16, 0.1);
            if (tick == 0) {
              fx.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
            }
          })
          .start();
    }, 85);
  }

  private boolean isSameBlock(Location a, Location b) {
    return a != null
        && b != null
        && a.getWorld() != null
        && a.getWorld().equals(b.getWorld())
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }



  @ConfigDescription("Craft a gate item to teleport to a marked location.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consume On Use for the Rift Gate adaptation.", impact = "True enables this behavior and false disables it.")
    boolean consumeOnUse = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires crafting the recall gate eye before it can be bound.", impact = "False lets any plain Eye of Ender bind with sneak-left-click and removes the crafting recipe; recipe availability changes apply after a restart.")
    boolean requireCraftedEye = true;

    public Config() {
      baseCost = 0;
      costFactor = 0.0;
      maxLevel = 1;
      initialCost = 30;
    }
  }
}
