package art.arcane.adapt.util.common.io;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import lombok.NonNull;

import java.io.IOException;
import java.lang.reflect.Type;

public class Json {
  public static final Gson NORMAL = create(false);
  public static final Gson PRETTY = create(true);

  @NonNull
  public static String toJson(@NonNull Object src, boolean pretty) {
    return toJson(src, src.getClass(), pretty);
  }

  @NonNull
  public static String toJson(@NonNull Object src, @NonNull Type type, boolean pretty) {
    return (pretty ? PRETTY : NORMAL).toJson(src, type);
  }

  public static <T> T fromJson(@NonNull String json, @NonNull Class<T> type) {
    return NORMAL.fromJson(json, type);
  }

  public static <T> T fromJson(@NonNull String json, @NonNull Type type) {
    return NORMAL.fromJson(json, type);
  }

  private static Gson create(boolean pretty) {
    com.google.gson.GsonBuilder builder = new GsonBuilder()
        .setLenient()
        .disableHtmlEscaping()
        .registerTypeAdapter(SkillPair.class, new MutationAdapter().nullSafe());
    if (pretty) builder.setPrettyPrinting();
    return builder.create();
  }

  /** A mutation as its line key, such as {@code adapt:swords+adapt:stealth}. */
  private static final class MutationAdapter extends TypeAdapter<SkillPair> {
    @Override
    public void write(JsonWriter out, SkillPair value) throws IOException {
      out.value(value.key());
    }

    @Override
    public SkillPair read(JsonReader in) throws IOException {
      if (in.peek() != JsonToken.STRING) {
        throw new JsonParseException("A mutation is stored as its line key");
      }
      String raw = in.nextString();
      SkillPair mutation = PairKeys.parse(raw);
      if (mutation == null) {
        throw new JsonParseException("Invalid mutation: " + raw);
      }
      return mutation;
    }
  }
}
