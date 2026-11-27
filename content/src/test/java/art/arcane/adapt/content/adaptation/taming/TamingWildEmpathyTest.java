package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeAnimals;
import art.arcane.adapt.testkit.FakeEntity;
import art.arcane.adapt.testkit.FakeWorld;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TamingWildEmpathyTest {
  private static final FakeAnimals ANIMALS = new FakeAnimals();
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void wildNeutralAggressorsRemainPacifiable() {
    FakeEntity wolf = entity("wolf").trait(EntityTrait.TAMEABLE);
    FakeEntity bee = entity("bee");

    assertThat(TamingWildEmpathy.isPacifiableNeutral(wolf, ANIMALS)).isTrue();
    assertThat(TamingWildEmpathy.isPacifiableNeutral(bee, ANIMALS)).isTrue();
  }

  @Test
  void tamedAggressorsAreNeverCancelledByWildEmpathy() {
    FakeEntity wolf = entity("wolf").trait(EntityTrait.TAMEABLE);
    wolf.setTameOwner(UUID.randomUUID());

    assertThat(TamingWildEmpathy.isPacifiableNeutral(wolf, ANIMALS)).isFalse();
  }

  @Test
  void unrelatedEntitiesAreNotPacified() {
    assertThat(TamingWildEmpathy.isPacifiableNeutral(entity("player"), ANIMALS)).isFalse();
  }

  private static FakeEntity entity(String type) {
    return new FakeEntity(Id.minecraft(type), new Loc(WORLD, 0, 64, 0, 0F, 0F));
  }
}
