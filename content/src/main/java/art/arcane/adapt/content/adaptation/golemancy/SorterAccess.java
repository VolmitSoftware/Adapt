package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.entity.PlayerRef;

interface SorterAccess {
  boolean owns(BlockRef block);

  PlayerRef owner();

  static SorterAccess of(NodeContext node) {
    return new SorterAccess() {
      @Override
      public boolean owns(BlockRef block) {
        return node.owns(block);
      }

      @Override
      public PlayerRef owner() {
        return node.onlineOwner().orElse(null);
      }
    };
  }

  static SorterAccess of(GolemContext golem) {
    return new SorterAccess() {
      @Override
      public boolean owns(BlockRef block) {
        return golem.owns(block.location());
      }

      @Override
      public PlayerRef owner() {
        return golem.owner();
      }
    };
  }
}
