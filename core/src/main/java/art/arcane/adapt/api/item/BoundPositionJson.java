package art.arcane.adapt.api.item;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.format.Form;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

/**
 * JSON for blocks and locations stored in bound items, in the array shape the Bukkit item data always used:
 * blocks as {@code [world key, x, y, z]}, locations as {@code [world key, x, y, z, yaw, pitch]} with coordinates
 * rounded to one decimal. A world that is not loaded reads back as null.
 */
public final class BoundPositionJson {
  private BoundPositionJson() {
  }

  public static final class Block extends TypeAdapter<BlockRef> {
    @Override
    public void write(JsonWriter out, BlockRef block) throws IOException {
      if (block == null) {
        out.nullValue();
        return;
      }
      out.beginArray();
      out.value(block.world().key().toString());
      out.value(block.x());
      out.value(block.y());
      out.value(block.z());
      out.endArray();
    }

    @Override
    public BlockRef read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      in.beginArray();
      WorldRef world = world(in.nextString());
      int x = in.nextInt();
      int y = in.nextInt();
      int z = in.nextInt();
      in.endArray();
      return world == null ? null : world.blockAt(x, y, z);
    }
  }

  public static final class Location extends TypeAdapter<Loc> {
    @Override
    public void write(JsonWriter out, Loc location) throws IOException {
      if (location == null) {
        out.nullValue();
        return;
      }
      out.beginArray();
      out.value(location.world().key().toString());
      out.value(truncate(location.x()));
      out.value(truncate(location.y()));
      out.value(truncate(location.z()));
      out.value((int) location.yaw());
      out.value((int) location.pitch());
      out.endArray();
    }

    @Override
    public Loc read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      in.beginArray();
      WorldRef world = world(in.nextString());
      double x = in.nextDouble();
      double y = in.nextDouble();
      double z = in.nextDouble();
      float yaw = (float) in.nextDouble();
      float pitch = (float) in.nextDouble();
      in.endArray();
      return world == null ? null : new Loc(world, x, y, z, yaw, pitch);
    }
  }

  private static WorldRef world(String key) {
    Id id = Id.tryParse(key);
    return id == null ? null : AdaptCore.platform().host().world(id);
  }

  private static double truncate(double value) {
    if ((int) value == value) {
      return value;
    }
    return Double.parseDouble(Form.f(value, 1));
  }
}
