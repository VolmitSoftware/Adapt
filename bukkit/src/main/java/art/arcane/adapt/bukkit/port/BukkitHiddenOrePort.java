package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.integration.hiddenore.HiddenOreLink;
import art.arcane.adapt.engine.integration.HiddenOrePort;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/** HiddenOre veins through the Bukkit-only link; empty while HiddenOre is absent. */
final class BukkitHiddenOrePort implements HiddenOrePort {
  @Override
  public List<VeinTarget> veins(Loc origin, int radius) {
    Location location = BukkitRefs.location(origin);
    if (location == null) {
      return List.of();
    }
    List<VeinTarget> targets = new ArrayList<>();
    for (HiddenOreLink.VeinTarget vein : HiddenOreLink.veins(location, radius)) {
      targets.add(new VeinTarget(BukkitRefs.loc(vein.location()), BukkitRefs.id(vein.display())));
    }
    return targets;
  }

  @Override
  public List<BlockRef> veinSiblings(BlockRef block) {
    Block bukkitBlock = BukkitRefs.block(block);
    if (bukkitBlock == null) {
      return List.of();
    }
    List<BlockRef> siblings = new ArrayList<>();
    for (Block sibling : HiddenOreLink.veinSiblings(bukkitBlock)) {
      siblings.add(BukkitRefs.block(sibling));
    }
    return siblings;
  }
}
