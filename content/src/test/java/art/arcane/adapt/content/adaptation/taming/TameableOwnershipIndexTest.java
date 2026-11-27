package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TameableOwnershipIndexTest {
  @Test
  void claimingSameScopeInvalidatesPreviousGeneration() {
    TameableOwnershipIndex index = new TameableOwnershipIndex();
    TameableOwnershipIndex.Generation first = index.claimGeneration("damage");
    TameableOwnershipIndex.Generation replacement = index.claimGeneration("damage");

    assertThat(index.isGenerationCurrent(first)).isFalse();
    assertThat(index.isGenerationCurrent(replacement)).isTrue();
  }

  @Test
  void generationScopesRemainIndependent() {
    TameableOwnershipIndex index = new TameableOwnershipIndex();
    TameableOwnershipIndex.Generation damage = index.claimGeneration("damage");
    TameableOwnershipIndex.Generation health = index.claimGeneration("health");

    index.claimGeneration("damage");

    assertThat(index.isGenerationCurrent(damage)).isFalse();
    assertThat(index.isGenerationCurrent(health)).isTrue();
  }

  @Test
  void staleGenerationCannotRunGuardedWork() {
    TameableOwnershipIndex index = new TameableOwnershipIndex();
    TameableOwnershipIndex.Generation stale = index.claimGeneration("damage");
    index.claimGeneration("damage");
    AtomicBoolean executed = new AtomicBoolean();

    boolean ran = index.runIfGenerationCurrent(stale, () -> executed.set(true));

    assertThat(ran).isFalse();
    assertThat(executed).isFalse();
  }
}
