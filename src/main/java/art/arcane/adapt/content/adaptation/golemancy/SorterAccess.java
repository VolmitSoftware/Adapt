package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.node.NodeContext;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

import java.util.Optional;

interface SorterAccess {
  boolean owns(Block block);

  Optional<Inventory> container(Block block);

  static SorterAccess of(NodeContext node) {
    return new SorterAccess() {
      @Override
      public boolean owns(Block block) {
        return node.owns(block);
      }

      @Override
      public Optional<Inventory> container(Block block) {
        return node.container(block);
      }
    };
  }

  static SorterAccess of(GolemContext golem) {
    return new SorterAccess() {
      @Override
      public boolean owns(Block block) {
        return golem.owns(block.getLocation());
      }

      @Override
      public Optional<Inventory> container(Block block) {
        return golem.container(block);
      }
    };
  }
}
