package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptMessages;
import art.arcane.volmlib.util.localization.TextValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectWirelessRedstoneTargetTest {
  private static final Path REMOTE_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/architect/ArchitectWirelessRedstone.java"
  );
  @Test
  void bindingOutputAndSuccessEffectsRequireAnAllowedPayment() throws IOException {
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(true, false))
        .isTrue();
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(true, true))
        .isFalse();
    assertThat(ArchitectWirelessRedstone.shouldCreateProviderBoundOutput(false, false))
        .isFalse();

    String source = Files.readString(REMOTE_SOURCE);
    String linkTorch = method(source, "private void linkTorch", "private void handleRightClick");
    int payment = linkTorch.indexOf("boolean paymentAllowed = payItemCost");
    int denied = linkTorch.indexOf("if (!paymentAllowed)");
    int providerOutput = linkTorch.indexOf("shouldCreateProviderBoundOutput");
    int successFx = linkTorch.indexOf("Location targetCenter");

    assertThat(payment).isGreaterThanOrEqualTo(0);
    assertThat(denied).isGreaterThan(payment);
    assertThat(providerOutput).isGreaterThan(denied);
    assertThat(successFx).isGreaterThan(providerOutput);
  }

  @Test
  void itemLoreExplainsThatAnyBlockCanBeBound() {
    TextValue usage1 = (TextValue) AdaptMessages.require("items.bound_redstone_torch.usage1").englishValue();
    TextValue usage2 = (TextValue) AdaptMessages.require("items.bound_redstone_torch.usage2").englishValue();
    String item = usage1.template() + " " + usage2.template();

    assertThat(item).contains("any block", "bound face", "4 ticks");
    assertThat(item).doesNotContain("'Target' Block", "1-Tick Redstone pulse", "2-Tick Redstone pulse");
  }

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException("Missing method markers: " + startMarker + ", " + endMarker);
    }
    return source.substring(start, end);
  }
}
