package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BrewerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Brewing stand over a Bukkit block. Slots go through the live brewer inventory; fuel level and brew time are
 * written through a fresh state snapshot followed by a forced update.
 */
public final class BukkitBrewingStandRef implements BrewingStandRef {
  private final Block block;

  public BukkitBrewingStandRef(Block block) {
    this.block = Objects.requireNonNull(block, "block");
  }

  @Override
  public BlockRef block() {
    return BukkitRefs.block(block);
  }

  @Override
  public ItemRef bottle(int slot) {
    return BukkitRefs.item(inventory().getItem(slot));
  }

  @Override
  public void setBottle(int slot, ItemRef item) {
    inventory().setItem(slot, BukkitRefs.item(item));
  }

  @Override
  public ItemRef ingredient() {
    return BukkitRefs.item(inventory().getIngredient());
  }

  @Override
  public void setIngredient(ItemRef item) {
    inventory().setIngredient(BukkitRefs.item(item));
  }

  @Override
  public ItemRef fuel() {
    return BukkitRefs.item(inventory().getFuel());
  }

  @Override
  public void setFuel(ItemRef item) {
    inventory().setFuel(BukkitRefs.item(item));
  }

  @Override
  public int fuelLevel() {
    return live().getFuelLevel();
  }

  @Override
  public void setFuelLevel(int level) {
    write((BrewingStand stand) -> stand.setFuelLevel(level));
  }

  @Override
  public int brewingTime() {
    return live().getBrewingTime();
  }

  @Override
  public void setBrewingTime(int ticks) {
    write((BrewingStand stand) -> stand.setBrewingTime(ticks));
  }

  @Override
  public List<PlayerRef> viewers() {
    List<HumanEntity> viewers = inventory().getViewers();
    List<PlayerRef> players = new ArrayList<>(viewers.size());
    for (HumanEntity viewer : viewers) {
      if (viewer instanceof Player player) {
        players.add(BukkitRefs.player(player));
      }
    }
    return players;
  }

  private BrewingStand live() {
    BlockState state = PaperCompat.liveState(block);
    if (!(state instanceof BrewingStand stand)) {
      throw new IllegalStateException("No brewing stand at " + block);
    }
    return stand;
  }

  private BrewerInventory inventory() {
    return live().getInventory();
  }

  private void write(Consumer<BrewingStand> change) {
    if (!(block.getState() instanceof BrewingStand stand)) {
      return;
    }
    change.accept(stand);
    stand.update(true);
  }
}
