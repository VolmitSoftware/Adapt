package art.arcane.adapt.util.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TomlCodecKeyQuotingTest {
  @Test
  void quotedKeysParseWithoutTheirQuotes() throws IOException {
    Holder holder = TomlCodec.fromToml("[values]\n\"adapt.xpmultiplier.vip\" = 1.5\n\"group.vip\" = 2.0\nplain = 3.0\n", Holder.class);

    assertThat(holder.values)
        .containsEntry("adapt.xpmultiplier.vip", 1.5D)
        .containsEntry("group.vip", 2.0D)
        .containsEntry("plain", 3.0D);
  }

  @Test
  void quotedKeysStayStableAcrossRepeatedRewrites() throws IOException {
    Holder holder = new Holder();
    holder.values.put("adapt.xpmultiplier.vip", 1.5D);

    String first = TomlCodec.toToml(holder, "test");
    String second = TomlCodec.toToml(TomlCodec.fromToml(first, Holder.class), "test");
    String third = TomlCodec.toToml(TomlCodec.fromToml(second, Holder.class), "test");

    assertThat(first).contains("\"adapt.xpmultiplier.vip\" = 1.5");
    assertThat(second).isEqualTo(first);
    assertThat(third).isEqualTo(first);
  }

  public static class Holder {
    private Map<String, Double> values = new LinkedHashMap<>();
  }
}
