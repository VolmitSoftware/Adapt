package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.node.NodeContext;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

interface SorterAccess {
  boolean owns(Block block);

  Player owner();

  static SorterAccess of(NodeContext node) {
    return new SorterAccess() {
      @Override
      public boolean owns(Block block) {
        return node.owns(block);
      }

      @Override
      public Player owner() {
        return node.onlineOwner().orElse(null);
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
      public Player owner() {
        return golem.owner();
      }
    };
  }
}
