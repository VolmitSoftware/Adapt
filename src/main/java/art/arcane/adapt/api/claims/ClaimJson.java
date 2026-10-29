package art.arcane.adapt.api.claims;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ClaimJson {
  private ClaimJson() {
  }

  static ClaimState read(JsonObject document) {
    List<ClaimCity> cities = new ArrayList<>();
    List<ClaimPlot> plots = new ArrayList<>();
    List<ClaimProgress> progress = new ArrayList<>();
    for (JsonElement element : array(document, "cities")) {
      JsonObject city = element.getAsJsonObject();
      Set<UUID> members = new HashSet<>();
      for (JsonElement member : array(city, "members")) {
        if (!members.add(UUID.fromString(string(member)))) {
          throw new IllegalArgumentException("Duplicate city member");
        }
      }
      cities.add(new ClaimCity(uuid(city, "id"), string(city.get("name")), uuid(city, "founder"),
          block(city.getAsJsonObject("anchor")), members, chunks(array(city, "chunks")),
          ranks(city.getAsJsonObject("ranks")), number(city, "offlineSince").getAsBigDecimal().longValueExact()));
    }
    for (JsonElement element : array(document, "plots")) {
      JsonObject plot = element.getAsJsonObject();
      plots.add(new ClaimPlot(uuid(plot, "owner"), uuid(plot, "cityId"), block(plot.getAsJsonObject("anchor")),
          chunks(array(plot, "chunks"))));
    }
    for (JsonElement element : array(document, "progress")) {
      JsonObject entry = element.getAsJsonObject();
      progress.add(new ClaimProgress(block(entry.getAsJsonObject("block")), string(entry.get("blockState")),
          number(entry, "breaking").getAsDouble(), number(entry, "placement").getAsDouble()));
    }
    return new ClaimState(cities, plots, progress);
  }

  static JsonObject write(ClaimState state) {
    JsonArray cities = new JsonArray();
    JsonArray plots = new JsonArray();
    JsonArray progress = new JsonArray();
    for (ClaimCity city : state.cities()) {
      JsonObject value = new JsonObject();
      value.addProperty("id", city.id().toString());
      value.addProperty("name", city.name());
      value.addProperty("founder", city.founder().toString());
      value.add("anchor", block(city.anchor()));
      JsonArray members = new JsonArray();
      for (UUID member : city.members()) {
        members.add(new JsonPrimitive(member.toString()));
      }
      value.add("members", members);
      value.add("chunks", chunks(city.chunks()));
      value.add("ranks", ranks(city.ranks()));
      value.addProperty("offlineSince", city.offlineSince());
      cities.add(value);
    }
    for (ClaimPlot plot : state.plots()) {
      JsonObject value = new JsonObject();
      value.addProperty("owner", plot.owner().toString());
      value.addProperty("cityId", plot.cityId().toString());
      value.add("anchor", block(plot.anchor()));
      value.add("chunks", chunks(plot.chunks()));
      plots.add(value);
    }
    for (ClaimProgress entry : state.progress()) {
      JsonObject value = new JsonObject();
      value.add("block", block(entry.block()));
      value.addProperty("blockState", entry.blockState());
      value.addProperty("breaking", entry.breaking());
      value.addProperty("placement", entry.placement());
      progress.add(value);
    }
    JsonObject document = new JsonObject();
    document.add("cities", cities);
    document.add("plots", plots);
    document.add("progress", progress);
    return document;
  }

  private static ClaimBlock block(JsonObject value) {
    return new ClaimBlock(uuid(value, "worldId"), integer(value, "x"), integer(value, "y"), integer(value, "z"));
  }

  private static ClaimRanks ranks(JsonObject value) {
    return new ClaimRanks(bool(value, "founding"), integer(value, "fortification"), integer(value, "citizenship"),
        integer(value, "expansion"), bool(value, "homestead"));
  }

  private static Set<ClaimChunk> chunks(JsonArray values) {
    Set<ClaimChunk> chunks = new HashSet<>();
    for (JsonElement element : values) {
      JsonObject value = element.getAsJsonObject();
      if (!chunks.add(new ClaimChunk(uuid(value, "worldId"), integer(value, "x"), integer(value, "z")))) {
        throw new IllegalArgumentException("Duplicate claim chunk");
      }
    }
    return chunks;
  }

  private static JsonObject block(ClaimBlock block) {
    JsonObject value = new JsonObject();
    value.addProperty("worldId", block.worldId().toString());
    value.addProperty("x", block.x());
    value.addProperty("y", block.y());
    value.addProperty("z", block.z());
    return value;
  }

  private static JsonObject ranks(ClaimRanks ranks) {
    JsonObject value = new JsonObject();
    value.addProperty("founding", ranks.founding());
    value.addProperty("fortification", ranks.fortification());
    value.addProperty("citizenship", ranks.citizenship());
    value.addProperty("expansion", ranks.expansion());
    value.addProperty("homestead", ranks.homestead());
    return value;
  }

  private static JsonArray chunks(Set<ClaimChunk> chunks) {
    JsonArray values = new JsonArray();
    for (ClaimChunk chunk : chunks) {
      JsonObject value = new JsonObject();
      value.addProperty("worldId", chunk.worldId().toString());
      value.addProperty("x", chunk.x());
      value.addProperty("z", chunk.z());
      values.add(value);
    }
    return values;
  }

  private static JsonArray array(JsonObject value, String name) {
    JsonElement element = value.get(name);
    if (element == null || !element.isJsonArray()) {
      throw new IllegalArgumentException("Claims field must be an array: " + name);
    }
    return element.getAsJsonArray();
  }

  private static UUID uuid(JsonObject value, String name) {
    return UUID.fromString(string(value.get(name)));
  }

  private static String string(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Claims field must be a string");
    }
    return value.getAsString();
  }

  private static int integer(JsonObject value, String name) {
    return number(value, name).getAsBigDecimal().intValueExact();
  }

  private static JsonPrimitive number(JsonObject value, String name) {
    JsonElement element = value.get(name);
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("Claims field must be numeric: " + name);
    }
    return element.getAsJsonPrimitive();
  }

  private static boolean bool(JsonObject value, String name) {
    JsonElement element = value.get(name);
    if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
      throw new IllegalArgumentException("Claims field must be boolean: " + name);
    }
    return element.getAsBoolean();
  }
}
