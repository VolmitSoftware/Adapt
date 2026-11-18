package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetherSkullYeetTargetingTest extends AdaptTestBase {
  private static final DataKey<String> SERVANT_OWNER = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);

  private NetherSkullYeet adaptation;
  private FakeWorld world;
  private FakeProjectile skull;
  private PlayerRef owner;
  private DamageCause cause;

  @BeforeAll
  static void registerMinionKinds() {
    MinionIdentity.registerKind(TragoulSkeletalServant.MINION_KIND);
  }

  @BeforeEach
  void installEntityGuard() {
    AdaptPorts.install(EntityGuardPort.class, new EntityGuardPort() {
      @Override
      public boolean isShielded(EntityRef entity) {
        return MinionIdentity.isMinion(entity);
      }
    });
  }

  @AfterEach
  void uninstallEntityGuard() throws ReflectiveOperationException {
    AdaptPorts.uninstall(EntityGuardPort.class);
    thrownSkulls().clear();
  }

  @BeforeEach
  void prepare() throws ReflectiveOperationException {
    adaptation = new NetherSkullYeet();
    adaptation.getConfig().setIgnorePassiveMobs(true);
    world = new FakeWorld("world");
    skull = new FakeProjectile(Id.minecraft("wither_skull"), new Loc(world, 0D, 64D, 0D));
    owner = mock(PlayerRef.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    skull.setShooter(owner);
    thrownSkulls().add(skull.uuid());
    cause = DamageCause.ENTITY_EXPLOSION;
  }

  @Test
  void explosionProtectsCowWhenEnabled() {
    EntityDamageByEntityEvent event = hit(target("cow", EntityTrait.ANIMAL));

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void explosionStillDamagesHostileMob() {
    EntityDamageByEntityEvent event = hit(target("husk", EntityTrait.ENEMY));

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void disabledProtectionPreservesExplosionDamage() {
    adaptation.getConfig().setIgnorePassiveMobs(false);
    EntityDamageByEntityEvent event = hit(target("cow", EntityTrait.ANIMAL));

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void deliberateDirectImpactIsUnchanged() {
    cause = DamageCause.PROJECTILE;
    EntityDamageByEntityEvent event = hit(target("cow", EntityTrait.ANIMAL));

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void unrelatedSkullExplosionIsUnchanged() throws ReflectiveOperationException {
    thrownSkulls().remove(skull.uuid());
    EntityDamageByEntityEvent event = hit(target("cow", EntityTrait.ANIMAL));

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isFalse();
  }

  @Test
  void summonedMobIsProtectedFromDirectImpactWithToggleDisabled() {
    adaptation.getConfig().setIgnorePassiveMobs(false);
    FakeLiving servant = new FakeLiving(Id.minecraft("skeleton"), new Loc(world, 2D, 64D, 0D));
    servant.data().set(SERVANT_OWNER, UUID.randomUUID().toString());
    cause = DamageCause.PROJECTILE;
    EntityDamageByEntityEvent event = hit(servant);

    adaptation.protectPassiveMobs(event);

    assertThat(event.isCancelled()).isTrue();
  }

  private FakeLiving target(String type, EntityTrait trait) {
    FakeLiving target = new FakeLiving(Id.minecraft(type), new Loc(world, 2D, 64D, 0D));
    target.trait(trait);
    return target;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target) {
    return new EntityDamageByEntityEvent(target, skull, owner, cause, Id.minecraft("wither_skull"), 4D, false,
        FinalDamage.IDENTITY);
  }

  @SuppressWarnings("unchecked")
  private static Set<UUID> thrownSkulls() throws ReflectiveOperationException {
    Field field = NetherSkullYeet.class.getDeclaredField("THROWN_SKULLS");
    field.setAccessible(true);
    return (Set<UUID>) field.get(null);
  }
}
