package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.client.protocol.ClientProtocolException;
import art.arcane.adapt.client.protocol.FrameCodec;
import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.ItemDescriptor;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Adapt items a native client adds to its recipe viewer: a Knowledge Orb and an Experience Orb without contents,
 * then the output of every enabled Adapt recipe that is not a plain vanilla stack, in recipe key order, each exact
 * stack once. An item carries {@link ItemOps#encodeNbt} of its one-item stack, so the client rebuilds the stack the
 * recipe sync shows. A list is sent as {@link ServerFrame.CustomItems} chunks of at most {@link #CHUNK_BYTES} encoded
 * bytes followed by one empty {@code CustomItems}; the client collects chunks and replaces its whole list when the
 * empty frame arrives, so an empty list is just the empty frame.
 */
public final class CustomItemCatalog {
  /** Encoded size a chunk stays under, well inside {@link FrameLimits#MAX_S2C_BYTES} and every transport's limit. */
  public static final int CHUNK_BYTES = 262144;
  public static final String KNOWLEDGE_ORB = "adapt:knowledge_orb";
  public static final String EXPERIENCE_ORB = "adapt:experience_orb";

  private CustomItemCatalog() {
  }

  /** One candidate stack under the id the client indexes it by. */
  public record Source(String id, ItemRef stack) {
    public Source {
      Objects.requireNonNull(id, "id");
    }
  }

  /** What a built list depends on; a viewer whose last list had another key gets the list again. */
  public record Key(long catalogRevision, String locale, LocalizationSnapshot texts, long modelGeneration, List<String> recipes) {
    public Key {
      recipes = List.copyOf(recipes);
    }
  }

  public static Key key(SkillRegistry registry, String locale, LocalizationSnapshot texts) {
    List<String> recipes = new ArrayList<>();
    for (RecipeSpec recipe : enabledRecipes(registry)) {
      recipes.add(recipe.key().toString());
    }
    return new Key(registry.getCatalogRevision(), locale, texts, CustomModel.generation(), recipes);
  }

  /** Builds the list in the caller's language scope with the running platform's items. */
  public static List<ItemDescriptor> build(SkillRegistry registry) {
    return describe(sources(registry), AdaptCore.platform().host().items());
  }

  static List<Source> sources(SkillRegistry registry) {
    List<Source> sources = new ArrayList<>();
    sources.add(new Source(KNOWLEDGE_ORB, KnowledgeOrb.with(Map.of())));
    sources.add(new Source(EXPERIENCE_ORB, ExperienceOrb.with(Map.of())));
    for (RecipeSpec recipe : enabledRecipes(registry)) {
      sources.add(new Source(recipe.key().toString(), recipe.result()));
    }
    return sources;
  }

  private static List<RecipeSpec> enabledRecipes(SkillRegistry registry) {
    List<RecipeSpec> recipes = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      if (!skill.isEnabled()) {
        continue;
      }
      recipes.addAll(skill.getRecipes());
      for (Adaptation<?> adaptation : Skill.ownedAdaptations(skill)) {
        if (adaptation.isEnabled()) {
          recipes.addAll(adaptation.getRecipes());
        }
      }
    }
    recipes.sort(Comparator.comparing((RecipeSpec recipe) -> recipe.key().toString()));
    return recipes;
  }

  /**
   * Turns sources into descriptors in order. Skips empty stacks, stacks equal to their plain item, a stack already
   * listed, an id already used and stacks that cannot be encoded or exceed the frame limits.
   */
  public static List<ItemDescriptor> describe(List<Source> sources, ItemOps items) {
    List<ItemDescriptor> described = new ArrayList<>();
    Set<ByteBuffer> stacks = new HashSet<>();
    Set<String> ids = new HashSet<>();
    for (Source source : sources) {
      ItemRef stack = source.stack();
      if (stack == null || stack.isEmpty() || ids.contains(source.id())) {
        continue;
      }
      ItemRef single = stack.copyWithAmount(1);
      byte[] encoded;
      try {
        encoded = items.encodeNbt(single);
        if (Arrays.equals(encoded, items.encodeNbt(items.create(single.type(), 1)))) {
          continue;
        }
      } catch (RuntimeException failed) {
        AdaptLog.warn("Recipe viewer item " + source.id() + " could not be encoded: " + failed.getMessage());
        continue;
      }
      if (!stacks.add(ByteBuffer.wrap(encoded))) {
        continue;
      }
      ItemDescriptor descriptor = descriptor(source.id(), single, encoded);
      if (descriptor == null) {
        continue;
      }
      ids.add(source.id());
      described.add(descriptor);
    }
    return List.copyOf(described);
  }

  private static ItemDescriptor descriptor(String id, ItemRef stack, byte[] encoded) {
    if (encoded.length > FrameLimits.MAX_ITEM_PAYLOAD_BYTES) {
      AdaptLog.warn("Recipe viewer item " + id + " is " + encoded.length + " bytes, over the frame limit");
      return null;
    }
    List<String> lore = stack.lore();
    Id model = stack.itemModel();
    ItemDescriptor descriptor = new ItemDescriptor(id, stack.type().toString(),
        stack.hasDisplayName() ? stack.displayName() : stack.type().toString(),
        lore.size() > FrameLimits.MAX_LINES ? lore.subList(0, FrameLimits.MAX_LINES) : lore,
        model == null ? null : model.toString(), 0, encoded);
    try {
      encodedSize(descriptor);
      return descriptor;
    } catch (ClientProtocolException tooLarge) {
      AdaptLog.warn("Recipe viewer item " + id + " does not fit a frame: " + tooLarge.getMessage());
      return null;
    }
  }

  /** The list as chunks of at most {@link #CHUNK_BYTES} encoded bytes, always ending with one empty frame. */
  public static List<ServerFrame.CustomItems> frames(List<ItemDescriptor> items) {
    List<ServerFrame.CustomItems> frames = new ArrayList<>();
    List<ItemDescriptor> chunk = new ArrayList<>();
    int chunkBytes = emptySize();
    for (ItemDescriptor item : items) {
      int size;
      try {
        size = encodedSize(item);
      } catch (ClientProtocolException tooLarge) {
        continue;
      }
      if (!chunk.isEmpty() && (chunkBytes + size > CHUNK_BYTES || chunk.size() >= FrameLimits.MAX_ITEMS)) {
        frames.add(new ServerFrame.CustomItems(chunk));
        chunk = new ArrayList<>();
        chunkBytes = emptySize();
      }
      chunk.add(item);
      chunkBytes += size;
    }
    if (!chunk.isEmpty()) {
      frames.add(new ServerFrame.CustomItems(chunk));
    }
    frames.add(new ServerFrame.CustomItems(List.of()));
    return List.copyOf(frames);
  }

  /** Bytes one item adds to a frame, plus two for a longer count prefix. */
  private static int encodedSize(ItemDescriptor item) throws ClientProtocolException {
    return FrameCodec.encodeServer(new ServerFrame.CustomItems(List.of(item))).length - emptySize() + 2;
  }

  private static int emptySize() {
    try {
      return FrameCodec.encodeServer(new ServerFrame.CustomItems(List.of())).length;
    } catch (ClientProtocolException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
