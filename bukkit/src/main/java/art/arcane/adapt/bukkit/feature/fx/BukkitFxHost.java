package art.arcane.adapt.bukkit.feature.fx;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.BossBarColor;
import art.arcane.adapt.platform.fx.BossBarRef;
import art.arcane.adapt.platform.fx.BossBarStyle;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Particles and sounds through the Bukkit world and player APIs, as the plugin's FX emitter sends them.
 * Sounds play by key so resource-pack sounds work like vanilla ones.
 */
public final class BukkitFxHost implements FxHost {
  @Override
  public void particle(WorldRef world, ParticleSpec particle, double x, double y, double z,
                       int count, double spreadX, double spreadY, double spreadZ, double speed) {
    World target = BukkitRefs.world(world);
    Particle type = BukkitParticles.particle(particle.type());
    if (target == null || type == null) {
      return;
    }
    target.spawnParticle(type, x, y, z, count, spreadX, spreadY, spreadZ, speed, BukkitParticles.data(type, particle.data()));
  }

  @Override
  public void particle(PlayerRef viewer, ParticleSpec particle, double x, double y, double z,
                       int count, double spreadX, double spreadY, double spreadZ, double speed) {
    Player target = BukkitRefs.player(viewer);
    Particle type = BukkitParticles.particle(particle.type());
    if (target == null || type == null || !target.isOnline()) {
      return;
    }
    target.spawnParticle(type, x, y, z, count, spreadX, spreadY, spreadZ, speed, BukkitParticles.data(type, particle.data()));
  }

  @Override
  public void sound(WorldRef world, double x, double y, double z, Id sound, SoundCategory category, float volume, float pitch) {
    World target = BukkitRefs.world(world);
    if (target == null) {
      return;
    }
    target.playSound(new Location(target, x, y, z), sound.toString(), BukkitConversions.category(category), volume, pitch);
  }

  @Override
  public BossBarRef createBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
    return new BukkitBossBar(titleMarkup, color, style);
  }

  @Override
  public void boneMealParticles(WorldRef world, BlockPos block, int count) {
    World target = BukkitRefs.world(world);
    if (target != null) {
      target.playEffect(new Location(target, block.x(), block.y(), block.z()), Effect.BONE_MEAL_USE, count);
    }
  }

  @Override
  public void blockBreakEffect(WorldRef world, BlockPos block, BlockStateRef state) {
    World target = BukkitRefs.world(world);
    if (target != null) {
      target.playEffect(new Location(target, block.x(), block.y(), block.z()), Effect.STEP_SOUND, BukkitRefs.blockData(state));
    }
  }
}
