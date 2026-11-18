package art.arcane.adapt.api.minion;

import art.arcane.adapt.platform.entity.MobRef;

import java.util.List;
import java.util.UUID;

public interface ServantPack {
  List<MobRef> servants(UUID owner);

  long expiresAt(MobRef servant);

  ServantOrder order(MobRef servant);

  boolean order(MobRef servant, ServantOrder order);

  boolean surrender(MobRef servant);

  void crumble(MobRef servant);
}
