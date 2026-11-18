package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Block state value. Ores and obsidian need a pickaxe; everything else drops by hand. */
public record FakeBlockState(Id type, Map<String, String> properties) implements BlockStateRef {
  private static final Set<String> PICKAXES = Set.of("wooden_pickaxe", "stone_pickaxe", "copper_pickaxe",
      "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe", "netherite_pickaxe");

  public FakeBlockState {
    Objects.requireNonNull(type, "type");
    properties = Map.copyOf(properties);
  }

  public FakeBlockState(Id type) {
    this(type, Map.of());
  }

  public static FakeBlockState of(String vanillaPath) {
    return new FakeBlockState(Id.minecraft(vanillaPath));
  }

  @Override
  public boolean isAir() {
    return type.isAir();
  }

  @Override
  public boolean isSolid() {
    return !type.isAir();
  }

  @Override
  public String property(String name) {
    return properties.get(name);
  }

  @Override
  public BlockStateRef with(String name, String value) {
    Map<String, String> next = new LinkedHashMap<>(properties);
    next.put(name, value);
    return new FakeBlockState(type, next);
  }

  @Override
  public int age() {
    String age = properties.get("age");
    return age == null ? -1 : Integer.parseInt(age);
  }

  @Override
  public int maxAge() {
    return properties.containsKey("age") ? 7 : -1;
  }

  @Override
  public Id placementItem() {
    return switch (type.path()) {
      case "wheat" -> Id.minecraft("wheat_seeds");
      case "carrots" -> Id.minecraft("carrot");
      case "potatoes" -> Id.minecraft("potato");
      case "beetroots" -> Id.minecraft("beetroot_seeds");
      default -> type;
    };
  }

  @Override
  public Id breakSound() {
    return Id.minecraft("block.stone.break");
  }

  @Override
  public boolean isPreferredTool(ItemRef tool) {
    String path = type.path();
    boolean needsPickaxe = path.endsWith("_ore") || path.contains("obsidian") || path.equals("spawner")
        || path.equals("ancient_debris");
    return !needsPickaxe || (tool != null && PICKAXES.contains(tool.type().path()));
  }

  @Override
  public String asString() {
    return type.toString();
  }

  @Override
  public Object unwrap() {
    return this;
  }
}
