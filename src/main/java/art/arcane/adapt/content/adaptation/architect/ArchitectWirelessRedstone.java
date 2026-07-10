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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class ArchitectWirelessRedstone extends SimpleAdaptation<ArchitectWirelessRedstone.Config> {
  private final Cooldowns pulseCd = cooldowns();

  public ArchitectWirelessRedstone() {
    super("architect-wireless-redstone");
    registerConfiguration(ArchitectWirelessRedstone.Config.class);
    setIcon(Material.REDSTONE_TORCH);
    registerRecipe(AdaptRecipe.shapeless()
        .key("remote-redstone-torch")
        .ingredient(Material.REDSTONE_TORCH)
        .ingredient(Material.TARGET)
        .ingredient(Material.ENDER_PEARL)
        .result(BoundRedstoneTorch.io.withData(new BoundRedstoneTorch.Data(null)))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.REDSTONE)
        .key("challenge_architect_wireless_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.REDSTONE)
            .key("challenge_architect_wireless_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_wireless_100", "architect.wireless-redstone.pulses", 100, 300);
    registerMilestone("challenge_architect_wireless_5k", "architect.wireless-redstone.pulses", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.wireless_redstone.lore1"));
  }


  @EventHandler
  public void onPlaceBlock(BlockPlaceEvent event) {
    ItemStack item = event.getItemInHand();
    if (BoundRedstoneTorch.hasItemData(item) && isRedstoneTorch(item)) {
      event.setBuild(false);
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
      return;
    }

    ItemStack itemInHand = event.getItem();

    if (itemInHand == null) {
      return;
    }

    boolean specialItem =
        isRedstoneTorch(itemInHand) && BoundRedstoneTorch.hasItemData(itemInHand);
    if (!specialItem) {
      return;
    }

    Player player = event.getPlayer();
    withPlayerThread(player, event, () -> {
      if (resolveInteractContext(player, player.getLocation()) == null) {
        return;
      }

      boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
      boolean inCreative = player.getGameMode() == GameMode.CREATIVE;
      if (inCreative && !canUseInCreative) {
        return;
      }

      switch (event.getAction()) {
        case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> handleLeftClick(event, player);
        case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK ->
            handleRightClick(event, player);
      }
    });
  }


  private boolean isRedstoneTorch(ItemStack item) {
    return item.getType().equals(Material.REDSTONE_TORCH);
  }


  private void handleLeftClick(PlayerInteractEvent event, Player player) {
    if (!player.isSneaking()) {
      return;
    }

    if (event.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Block target = event.getClickedBlock();
    if (target == null) {
      target = player.getTargetBlockExact(5);
    }

    if (target == null) {
      return;
    }

    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
      event.setUseItemInHand(Result.DENY);
    }

    Location location = new Location(target.getWorld(), target.getX(), target.getY(), target.getZ());
    linkTorch(player, location);
  }

  private void handleRightClick(PlayerInteractEvent event, Player player) {
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      event.setUseItemInHand(Result.DENY);
      event.setUseInteractedBlock(Result.DENY);
    }

    if (hasCooldown(player)) {
      fx(player.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 1, 0.05D)
          .sound(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
    } else {
      pulseCd.mark(player.getUniqueId());
      showPlayerCooldown(player);
      triggerPulse(player, event.getItem());
    }
  }

  private void showPlayerCooldown(Player player) {
    int cooldownTicks = Math.max(1, (int) Math.ceil(getConfig().cooldown / 50D));
    player.setCooldown(Material.REDSTONE_TORCH, cooldownTicks);
  }


  private boolean hasCooldown(Player i) {
    return !pulseCd.isReady(i.getUniqueId(), getConfig().cooldown);
  }


  private void linkTorch(Player p, Location l) {
    if (!l.getBlock().getType().equals(Material.TARGET)) {
      return;
    }
    Location targetCenter = l.clone().add(0.5, 0.5, 0.5);
    fx(p.getEyeLocation(), FxPriority.GAMEPLAY)
        .line(Particle.ELECTRIC_SPARK, targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(), 16);
    fx(targetCenter, FxPriority.GAMEPLAY)
        .dustRing(Color.fromRGB(255, 40, 40), 0.6D, 16, 1.0F)
        .chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.2f, 0.48f, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.6f);
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (hand.getAmount() == 1) {
      BoundRedstoneTorch.setData(hand, l);
    } else {
      hand.setAmount(hand.getAmount() - 1);
      ItemStack torch = BoundRedstoneTorch.withData(l);
      p.getInventory().addItem(torch).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }
  }


  private void triggerPulse(Player p, ItemStack item) {
    Location l = BoundRedstoneTorch.getLocation(item);
    if (!isBound(item) || l == null) {
      return;
    }

    Location targetCenter = l.clone().add(0.5, 0.5, 0.5);
    loadChunkAsync(l, chunk -> J.runAt(l, () -> {
      Block b = l.getBlock();
      BlockData data = b.getBlockData();
      if (data instanceof AnaloguePowerable redBlock && b.getType().equals(Material.TARGET)) {
        redBlock.setPower(15);
        b.setBlockData(redBlock);
        fx(targetCenter, FxPriority.GAMEPLAY)
            .dustRing(Color.fromRGB(255, 40, 40), 0.5D, 12, 1.0F)
            .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.4f);
        J.runEntity(p, () -> fx(p.getEyeLocation(), FxPriority.GAMEPLAY)
            .burst(Particle.ELECTRIC_SPARK, 4, 0.15D)
            .sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f));
        addStat(p, "architect.wireless-redstone.pulses", 1);
        J.runAt(l, () -> {
          redBlock.setPower(0);
          b.setBlockData(redBlock);
        }, 2);
      } else {
        J.runEntity(p, () -> fx(p.getLocation(), FxPriority.TRANSITION)
            .sound(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f));
      }
    }));
  }

  private boolean isBound(ItemStack stack) {
    return (stack.getType().equals(Material.REDSTONE_TORCH) && BoundRedstoneTorch.getLocation(stack) != null);
  }

  @ConfigDescription("Use a crafted redstone remote to toggle redstone at a distance.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Architect Wireless Redstone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 125;

    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 0;
    }
  }
}
