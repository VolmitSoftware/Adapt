package art.arcane.adapt.api.minion;

import org.bukkit.entity.Skeleton;

import java.util.List;
import java.util.UUID;

public interface ServantPack {
  List<Skeleton> servants(UUID owner);

  long expiresAt(Skeleton servant);

  ServantOrder order(Skeleton servant);

  boolean order(Skeleton servant, ServantOrder order);

  boolean surrender(Skeleton servant);

  void crumble(Skeleton servant);
}
