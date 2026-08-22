package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.util.director.DirectorSystem;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.director.handlers.base.BlockVectorHandlerBase;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.List;

public class BlockVectorHandler extends BlockVectorHandlerBase implements DirectorParameterHandler<BlockVector> {
  @Override
  protected boolean isSenderPlayer() {
    return BukkitDirectorContext.isPlayer();
  }

  @Override
  protected BlockVector getSenderBlockVector() {
    return BukkitDirectorContext.player().getLocation().toVector().toBlockVector();
  }

  @Override
  protected BlockVector getLookBlockVector() {
    return BukkitDirectorContext.player().getTargetBlockExact(256, FluidCollisionMode.NEVER).getLocation().toVector().toBlockVector();
  }

  @Override
  protected List<?> playerPossibilities(String query) {
    return DirectorSystem.getHandler(Player.class).getPossibilities(query);
  }

  @Override
  protected String format(double value) {
    return Form.f(value, 2);
  }
}
