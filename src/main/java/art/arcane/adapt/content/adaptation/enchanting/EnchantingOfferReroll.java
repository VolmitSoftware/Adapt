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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingOfferReroll extends SimpleAdaptation<EnchantingOfferReroll.Config> {
  private static volatile Method setSeedMethod;

  public EnchantingOfferReroll() {
    super("enchanting-offer-reroll");
    registerConfiguration(Config.class);
    setIcon(Material.ENCHANTING_TABLE);
    setInterval(1800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENCHANTING_TABLE)
        .key("challenge_enchanting_reroll_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTING_TABLE)
            .key("challenge_enchanting_reroll_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_reroll_100", "enchanting.offer-reroll.rerolls", 100, 300);
    registerMilestone("challenge_enchanting_reroll_1k", "enchanting.offer-reroll.rerolls", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getCooldownTicks(level) * 50D, 1), 1);
    statLore(v, C.YELLOW, "* ", getLapisCost(level), 2);
    if (getConfig().xpLevelCost > 0) {
      statLore(v, C.YELLOW, "* ", getConfig().xpLevelCost, EnchantingMessages.OFFER_REROLL_LORE_COST_XP);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0 || p.hasCooldown(Material.ENCHANTING_TABLE)) {
      return;
    }

    Block table = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (table == null || table.getType() != Material.ENCHANTING_TABLE) {
      return;
    }

    Location tableTop = table.getLocation().add(0.5D, 1.0D, 0.5D);
    if (p.getLevel() < getConfig().xpLevelCost) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }

    int lapisCost = getLapisCost(level);
    RerollReservation reservation = reserveRerollCost(p, lapisCost);
    if (reservation == null) {
      FxPresets.failFizzle(this, tableTop);
      return;
    }

    p.setLevel(Math.max(0, p.getLevel() - getConfig().xpLevelCost));
    if (!setSeed(p, ThreadLocalRandom.current().nextInt())) {
      refundFailedReroll(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      return;
    }

    if (!settleRerollCost(reservation)) {
      if (!setSeed(p, reservation.previousSeed())) {
        Adapt.warn("Offer Reroll could not restore the previous enchantment seed for " + p.getName() + ".");
      }
      refundFailedReroll(p, reservation, AbilityRefundReason.CHARGE_ROLLBACK);
      return;
    }

    p.setCooldown(Material.ENCHANTING_TABLE, getCooldownTicks(level));
    e.setCancelled(true);

    rerollSurgeFx(tableTop);
    xp(p, getConfig().xpGainOnReroll);
    addStat(p, "enchanting.offer-reroll.rerolls", 1);
  }

  private void rerollSurgeFx(Location tableTop) {
    timeline(tableTop)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20.0D)
        .frame((f, tick, progress) -> {
          if (tick < 4) {
            f.dustRing(1.5D - (0.3D * tick), 12, 0.9F);
          }
          if (tick < 6) {
            f.particle(Particles.ENCHANTMENT_TABLE, 4, 0, 0.4D, 0, 0.6D, 0.1D);
          } else {
            f.helix(Particle.PORTAL, (1.2D * (1.0D - ((progress - 0.6D) / 0.4D))) + 0.15D, 1.4D, 4, progress * Math.PI * 4.0D);
          }
          if (tick == 0) {
            f.particle(Particle.FLASH, 1, 0, 0.5D, 0, 0, 0)
                .sound(Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
          }
          if ((tick & 1) == 0 && tick <= 8) {
            f.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, (float) (0.85D + (0.45D * progress)));
          }
          if (tick == 8) {
            f.sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.4F, 1.0F);
          }
        })
        .start();
  }

  private boolean hasLapis(Player p, int amount) {
    int found = 0;
    for (ItemStack stack : p.getInventory().getContents()) {
      if (stack == null || stack.getType() != Material.LAPIS_LAZULI) {
        continue;
      }

      found += stack.getAmount();
      if (found >= amount) {
        return true;
      }
    }
    return false;
  }

  private RerollReservation reserveRerollCost(Player p, int amount) {
    int previousXpLevel = p.getLevel();
    int previousSeed = p.getEnchantmentSeed();
    AtomicBoolean defaultLapisConsumed = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(
        p,
        "lapis",
        new ItemStack(Material.LAPIS_LAZULI),
        amount,
        () -> {
          boolean consumed = takeLapis(p, amount);
          defaultLapisConsumed.set(consumed);
          return consumed;
        }
    );
    if (!charge.allowed()) {
      return null;
    }

    return new RerollReservation(
        charge,
        amount,
        defaultLapisConsumed.get(),
        previousXpLevel,
        previousSeed
    );
  }

  private boolean takeLapis(Player p, int amount) {
    if (!hasLapis(p, amount)) {
      return false;
    }

    int need = amount;
    for (ItemStack stack : p.getInventory().getContents()) {
      if (stack == null || stack.getType() != Material.LAPIS_LAZULI || need <= 0) {
        continue;
      }

      int used = Math.min(stack.getAmount(), need);
      stack.setAmount(stack.getAmount() - used);
      need -= used;
    }
    return need == 0;
  }

  private boolean settleRerollCost(RerollReservation reservation) {
    boolean settled = settleCost(reservation.charge().activationId());
    return acceptsDeferredSettlement(reservation.defaultLapisConsumed(), settled);
  }

  static boolean acceptsDeferredSettlement(boolean defaultLapisConsumed, boolean settled) {
    return defaultLapisConsumed || settled;
  }

  private void refundFailedReroll(Player p, RerollReservation reservation, AbilityRefundReason reason) {
    boolean refunded = refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      Adapt.warn("Offer Reroll could not resolve the deferred provider cost for " + p.getName() + ".");
    }
    if (reservation.defaultLapisConsumed()) {
      refundDefaultLapis(p, reservation.lapisAmount());
    }
    p.setLevel(reservation.previousXpLevel());
  }

  private void refundDefaultLapis(Player p, int amount) {
    try {
      HashMap<Integer, ItemStack> leftovers =
          p.getInventory().addItem(new ItemStack(Material.LAPIS_LAZULI, amount));
      leftovers.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
    } catch (Throwable t) {
      Adapt.warn("Offer Reroll could not return reserved lapis to " + p.getName() + ".");
      t.printStackTrace();
    }
  }

  private boolean setSeed(Player p, int seed) {
    try {
      Method method = setSeedMethod;
      if (method == null) {
        method = p.getClass().getMethod("setEnchantmentSeed", int.class);
        setSeedMethod = method;
      }

      method.invoke(p, seed);
      return true;
    } catch (Throwable t) {
      Adapt.warn("Offer Reroll could not update the enchantment seed for " + p.getName() + ".");
      t.printStackTrace();
      return false;
    }
  }

  private int getLapisCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().lapisCostBase - (getLevelPercent(level) * getConfig().lapisCostFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private record RerollReservation(
      AbilityCharge charge,
      int lapisAmount,
      boolean defaultLapisConsumed,
      int previousXpLevel,
      int previousSeed
  ) {
  }

  @ConfigDescription("Sneak-right-click an enchanting table to reroll offers for lapis and XP.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisCostBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double lapisCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 320;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 220;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Level Cost for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int xpLevelCost = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Gain On Reroll for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpGainOnReroll = 15;

    public Config() {
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
