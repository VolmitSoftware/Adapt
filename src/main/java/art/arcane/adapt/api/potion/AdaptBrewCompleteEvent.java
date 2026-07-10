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

package art.arcane.adapt.api.potion;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class AdaptBrewCompleteEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();

  private final Block block;
  private final BrewingRecipe recipe;
  private final UUID brewerId;
  private final int brewedPotions;

  public AdaptBrewCompleteEvent(Block block, BrewingRecipe recipe, UUID brewerId, int brewedPotions) {
    this.block = block;
    this.recipe = recipe;
    this.brewerId = brewerId;
    this.brewedPotions = brewedPotions;
  }

  public Block getBlock() {
    return block;
  }

  public BrewingRecipe getRecipe() {
    return recipe;
  }

  public UUID getBrewerId() {
    return brewerId;
  }

  public int getBrewedPotions() {
    return brewedPotions;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static @NotNull HandlerList getHandlerList() {
    return HANDLERS;
  }
}
