package com.example.adaptaddon;

import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class AddonListener implements Listener {
  private ItemStack lastBrewResult;
  private UUID lastBrewer;
  private int brewedPotions;
  private Player lastUser;
  private String lastAdaptation;
  private Location lastDestination;

  @EventHandler
  public void onBrew(AdaptBrewCompleteEvent event) {
    lastBrewResult = event.getRecipe().getResult();
    lastBrewer = event.getBrewerId();
    brewedPotions = event.getBrewedPotions();
  }

  @EventHandler(ignoreCancelled = true)
  public void onUse(AdaptAdaptationUseEvent event) {
    lastUser = event.getPlayer().getPlayer();
    lastAdaptation = event.getAdaptation().getName();
    if ("addon-blocked".equals(lastAdaptation)) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onTeleport(AdaptAdaptationTeleportEvent event) {
    lastDestination = event.getToLocation();
    if (event.getFromLocation() != null && event.getFromLocation().equals(lastDestination)) {
      event.setCancelled(true);
    }
  }

  public ItemStack lastBrewResult() {
    return lastBrewResult;
  }

  public UUID lastBrewer() {
    return lastBrewer;
  }

  public int brewedPotions() {
    return brewedPotions;
  }

  public Player lastUser() {
    return lastUser;
  }

  public String lastAdaptation() {
    return lastAdaptation;
  }

  public Location lastDestination() {
    return lastDestination;
  }
}
