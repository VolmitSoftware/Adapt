package art.arcane.adapt.content.item;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.data.Json;
import art.arcane.adapt.platform.data.DataCodec;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The JSON object a bound item keeps in one STRING data entry, in the shape the Bukkit item data always used: blocks
 * as {@code [world key, x, y, z]}, locations as {@code [world key, x, y, z, yaw, pitch]} with coordinates rounded to
 * one decimal, written by {@link Json}. Values read back as {@link String}, {@link Double}, {@link Boolean},
 * {@link List} and {@link Map}.
 */
final class StoredJson {
  private StoredJson() {
  }

  /** A STRING codec over a JSON object; stored text that is not a JSON object reads back as null. */
  static <T> DataCodec<T> codec(Function<T, Map<String, Object>> encode, Function<Map<String, Object>, T> decode) {
    return DataCodecs.map(DataCodecs.STRING, (T value) -> Json.write(encode.apply(value)), (String raw) -> {
      Object parsed = Json.parse(raw);
      if (!(parsed instanceof Map<?, ?>)) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> object = (Map<String, Object>) parsed;
      return decode.apply(object);
    });
  }

  /** A new object for {@link #codec}'s encoder, keeping field order. */
  static Map<String, Object> object() {
    return new LinkedHashMap<>();
  }

  static @Nullable String string(Map<String, Object> object, String field) {
    return object.get(field) instanceof String value ? value : null;
  }

  static double number(Map<String, Object> object, String field) {
    return object.get(field) instanceof Number value ? value.doubleValue() : 0D;
  }

  static @Nullable List<Object> blockJson(@Nullable BlockRef block) {
    if (block == null) {
      return null;
    }
    List<Object> out = new ArrayList<>(4);
    out.add(block.world().key().toString());
    out.add(block.x());
    out.add(block.y());
    out.add(block.z());
    return out;
  }

  /** The block a stored array names; null when it is malformed or its world is not loaded. */
  static @Nullable BlockRef block(@Nullable Object stored) {
    if (!(stored instanceof List<?> parts) || parts.size() < 4) {
      return null;
    }
    WorldRef world = world(parts.get(0));
    if (world == null || !(parts.get(1) instanceof Number x) || !(parts.get(2) instanceof Number y)
        || !(parts.get(3) instanceof Number z)) {
      return null;
    }
    return world.blockAt(x.intValue(), y.intValue(), z.intValue());
  }

  static @Nullable List<Object> locationJson(@Nullable Loc location) {
    if (location == null) {
      return null;
    }
    List<Object> out = new ArrayList<>(6);
    out.add(location.world().key().toString());
    out.add(truncate(location.x()));
    out.add(truncate(location.y()));
    out.add(truncate(location.z()));
    out.add((int) location.yaw());
    out.add((int) location.pitch());
    return out;
  }

  /** The location a stored array names; null when it is malformed or its world is not loaded. */
  static @Nullable Loc location(@Nullable Object stored) {
    if (!(stored instanceof List<?> parts) || parts.size() < 6) {
      return null;
    }
    WorldRef world = world(parts.get(0));
    if (world == null) {
      return null;
    }
    double[] values = new double[5];
    for (int index = 0; index < values.length; index++) {
      if (!(parts.get(index + 1) instanceof Number value)) {
        return null;
      }
      values[index] = value.doubleValue();
    }
    return new Loc(world, values[0], values[1], values[2], (float) values[3], (float) values[4]);
  }

  private static @Nullable WorldRef world(Object key) {
    Id id = key instanceof String text ? Id.tryParse(text) : null;
    return id == null ? null : AdaptApi.platform().host().world(id);
  }

  private static double truncate(double value) {
    if ((int) value == value) {
      return value;
    }
    return Double.parseDouble(Format.f(value, 1));
  }
}
