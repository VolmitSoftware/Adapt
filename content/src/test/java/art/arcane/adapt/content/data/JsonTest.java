package art.arcane.adapt.content.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JsonTest {
  private static final Gson GSON = new Gson();

  @Test
  void writesObjectsExactlyAsGsonDid() {
    for (Sample sample : samples()) {
      assertThat(Json.write(sample)).isEqualTo(GSON.toJson(sample));
    }
  }

  @Test
  void readsBackWhatGsonWrote() {
    for (Sample sample : samples()) {
      Sample read = Json.read(GSON.toJson(sample), Sample.class);
      assertThat(Json.write(read)).isEqualTo(GSON.toJson(GSON.fromJson(GSON.toJson(sample), Sample.class)));
    }
  }

  @Test
  void componentsMatchGsonTreeText() {
    JsonObject tree = new JsonObject();
    tree.addProperty("text", "a <b> & 'c' = d");
    tree.addProperty("bold", true);
    JsonArray with = new JsonArray();
    JsonObject inner = new JsonObject();
    inner.addProperty("translate", "potion.withAmplifier");
    with.add(inner);
    tree.add("with", with);

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("text", "a <b> & 'c' = d");
    map.put("bold", true);
    Map<String, Object> innerMap = new LinkedHashMap<>();
    innerMap.put("translate", "potion.withAmplifier");
    map.put("with", List.of(innerMap));

    assertThat(Json.writeComponent(map)).isEqualTo(tree.toString());
  }

  @Test
  void keepsDefaultsForMissingFieldsAndRefusesMalformedText() {
    Sample read = Json.read("{\"count\":3}", Sample.class);

    assertThat(read.count).isEqualTo(3);
    assertThat(read.names).containsExactly("default");
    assertThat(Json.read("", Sample.class)).isNull();
    assertThat(Json.read("null", Sample.class)).isNull();
    assertThatIllegalArgumentException().isThrownBy(() -> Json.read("{\"count\":", Sample.class));
    assertThatIllegalArgumentException().isThrownBy(() -> Json.read("{\"count\":\"many\"}", Sample.class));
    assertThat(Json.parse("{oops")).isNull();
  }

  private static List<Sample> samples() {
    Sample plain = new Sample();
    Sample full = new Sample();
    full.count = -7;
    full.big = 1_700_000_000_123L;
    full.share = 0.1D + 0.2D;
    full.boxed = 2D;
    full.flag = true;
    full.text = "quote \" slash \\ tab \t line \n html <a href='x'>&amp;</a> =   \u0001";
    full.id = UUID.fromString("00000000-0000-0000-0000-00000000002a");
    full.kind = Kind.SECOND;
    full.cells = new int[] {1, 2, 3};
    full.names = new ArrayList<>(List.of("a", "b"));
    full.counts = new LinkedHashMap<>(Map.of("x", 1));
    full.counts.put("gone", null);
    full.child = new Child();
    full.child.label = "inner";
    full.children = new ArrayList<>(List.of(new Child()));
    return List.of(plain, full);
  }

  enum Kind {
    FIRST,
    SECOND
  }

  static class Base {
    String inherited = "base";
  }

  static final class Sample extends Base {
    int count;
    long big;
    double share = 1D;
    Double boxed;
    boolean flag;
    String text;
    UUID id;
    Kind kind;
    int[] cells = new int[0];
    List<String> names = new ArrayList<>(List.of("default"));
    Map<String, Integer> counts = new LinkedHashMap<>();
    Child child;
    List<Child> children = new ArrayList<>();
    static int ignored = 5;
    transient int skipped = 9;
  }

  static final class Child {
    String label;
    float weight = 1.5F;
  }
}
