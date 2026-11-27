package art.arcane.adapt.pack;

import art.arcane.adapt.pack.manifest.PackManifest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveredResourcesTest {
  @Test
  void buildsResourcesOncePerPublishedManifest() {
    AtomicReference<Optional<PackManifest>> published = new AtomicReference<>(Optional.empty());
    DeliveredResources resources = new DeliveredResources(published::get);

    assertThat(resources.get()).isNull();

    PackManifest manifest = PackSamples.manifest();
    published.set(Optional.of(manifest));
    PackResources first = resources.get();
    assertThat(first).isNotNull();
    assertThat(first.manifest()).isSameAs(manifest);
    assertThat(resources.get()).isSameAs(first);

    PackManifest next = PackSamples.manifest();
    published.set(Optional.of(next));
    assertThat(resources.get()).isNotSameAs(first);
    assertThat(resources.get().manifest()).isSameAs(next);

    published.set(Optional.empty());
    assertThat(resources.get()).isNull();
  }
}
