package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitRefsTest {
  @AfterEach
  void clearCaches() {
    BukkitRefs.clear();
  }

  @Test
  void playerHandleIsCachedPerUuidAndRebindsToTheCurrentObject() {
    UUID id = UUID.randomUUID();
    Player first = player(id, "Steve");
    Player second = player(id, "Steve");

    PlayerRef ref = BukkitRefs.player(first);
    assertThat(BukkitRefs.player(first)).isSameAs(ref);
    assertThat(BukkitRefs.player(second)).isSameAs(ref);
    assertThat(BukkitRefs.player(ref)).isSameAs(second);
    assertThat(BukkitRefs.entity((Entity) second)).isSameAs(ref);
    assertThat(ref.name()).isEqualTo("Steve");
  }

  @Test
  void quitPlayersAreForgottenOnlyOnceOffline() {
    UUID id = UUID.randomUUID();
    Player player = player(id, "Alex");
    when(player.isOnline()).thenReturn(true);
    PlayerRef ref = BukkitRefs.player(player);

    BukkitRefs.forget(id);
    assertThat(BukkitRefs.cachedPlayer(id)).isSameAs(ref);

    when(player.isOnline()).thenReturn(false);
    BukkitRefs.forget(id);
    assertThat(BukkitRefs.cachedPlayer(id)).isNull();
  }

  @Test
  void entityHandlesUseTheMostSpecificTypeAndAreCached() {
    Zombie zombie = entity(Zombie.class, EntityType.ZOMBIE);
    ArmorStand stand = entity(ArmorStand.class, EntityType.ARMOR_STAND);
    Arrow arrow = entity(Arrow.class, EntityType.ARROW);
    Item item = entity(Item.class, EntityType.ITEM);
    Entity plain = entity(Entity.class, EntityType.MARKER);

    EntityRef mob = BukkitRefs.entity(zombie);
    assertThat(mob).isInstanceOf(MobRef.class);
    assertThat(BukkitRefs.entity(zombie)).isSameAs(mob);
    assertThat(BukkitRefs.entity(stand)).isInstanceOf(LivingRef.class).isNotInstanceOf(MobRef.class);
    assertThat(BukkitRefs.entity(arrow)).isInstanceOf(ProjectileRef.class);
    assertThat(BukkitRefs.entity(item)).isInstanceOf(ItemEntityRef.class);
    assertThat(BukkitRefs.entity(plain)).isNotInstanceOf(LivingRef.class);
    assertThat(mob.type()).isEqualTo(Id.minecraft("zombie"));
    assertThat(BukkitRefs.entity(mob)).isSameAs(zombie);
  }

  @Test
  void forgottenEntitiesGetAFreshHandleThatIsStillEqual() {
    Zombie zombie = entity(Zombie.class, EntityType.ZOMBIE);
    EntityRef first = BukkitRefs.entity(zombie);

    BukkitRefs.forget(zombie);
    EntityRef second = BukkitRefs.entity(zombie);

    assertThat(second).isNotSameAs(first).isEqualTo(first).hasSameHashCodeAs(first);
  }

  @Test
  void entityEqualityIsByUuid() {
    UUID id = UUID.randomUUID();
    Zombie a = entity(Zombie.class, EntityType.ZOMBIE);
    Zombie b = entity(Zombie.class, EntityType.ZOMBIE);
    when(a.getUniqueId()).thenReturn(id);
    when(b.getUniqueId()).thenReturn(id);

    assertThat(BukkitRefs.entity(a)).isEqualTo(BukkitRefs.entity(b));
    assertThat(BukkitRefs.entity(a)).isNotEqualTo(BukkitRefs.entity(entity(Zombie.class, EntityType.ZOMBIE)));
  }

  @Test
  void worldHandlesAreCachedAndEqualByUid() {
    UUID id = UUID.randomUUID();
    World world = world(id, "world");
    World reloaded = world(id, "world");

    WorldRef ref = BukkitRefs.world(world);
    assertThat(BukkitRefs.world(world)).isSameAs(ref);
    assertThat(BukkitRefs.world(reloaded)).isNotSameAs(ref).isEqualTo(ref);
    assertThat(ref.key()).isEqualTo(Id.minecraft("overworld"));
    assertThat(ref.name()).isEqualTo("world");
  }

  @Test
  void blockHandlesAreEqualByWorldAndPosition() {
    World world = world(UUID.randomUUID(), "world");
    BlockRef a = BukkitRefs.block(block(world, 1, 64, -3));
    BlockRef b = BukkitRefs.block(block(world, 1, 64, -3));
    BlockRef other = BukkitRefs.block(block(world, 1, 65, -3));

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(other);
    assertThat(a.pos()).isEqualTo(new BlockPos(1, 64, -3));
  }

  @Test
  void locationsConvertWithRotation() {
    World world = world(UUID.randomUUID(), "world");
    Loc loc = BukkitRefs.loc(new Location(world, 1.5D, 2.0D, -3.25D, 90.0F, 10.0F));

    assertThat(loc.world()).isEqualTo(BukkitRefs.world(world));
    assertThat(loc.x()).isEqualTo(1.5D);
    assertThat(loc.z()).isEqualTo(-3.25D);
    assertThat(loc.yaw()).isEqualTo(90.0F);
    assertThat(loc.pitch()).isEqualTo(10.0F);
    assertThat(BukkitRefs.loc(new Location(null, 0.0D, 0.0D, 0.0D))).isNull();
  }

  @Test
  void idsMatchBukkitKeys() {
    assertThat(BukkitRefs.id(Material.DIAMOND_SWORD)).isEqualTo(Id.minecraft("diamond_sword"));
    assertThat(BukkitRefs.key(Id.adapt("elevator"))).isEqualTo(new NamespacedKey("adapt", "elevator"));
    assertThat(BukkitRefs.id(new NamespacedKey("adapt", "elevator"))).isEqualTo(Id.adapt("elevator"));
  }

  private static Player player(UUID id, String name) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getName()).thenReturn(name);
    when(player.getType()).thenReturn(EntityType.PLAYER);
    return player;
  }

  private static <T extends Entity> T entity(Class<T> type, EntityType entityType) {
    T entity = mock(type);
    when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    when(entity.getType()).thenReturn(entityType);
    return entity;
  }

  private static World world(UUID id, String name) {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(id);
    when(world.getName()).thenReturn(name);
    when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
    return world;
  }

  private static Block block(World world, int x, int y, int z) {
    Block block = mock(Block.class);
    when(block.getWorld()).thenReturn(world);
    when(block.getX()).thenReturn(x);
    when(block.getY()).thenReturn(y);
    when(block.getZ()).thenReturn(z);
    return block;
  }
}
