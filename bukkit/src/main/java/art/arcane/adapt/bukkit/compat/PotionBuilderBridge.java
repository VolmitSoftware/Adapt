package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import de.crazydev22.platformutils.ItemEditor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class PotionBuilderBridge {
  private static final Map<PotionBuilder, Extras> EXTRAS = Collections.synchronizedMap(new WeakHashMap<>());

  private PotionBuilderBridge() {
  }

  @ApiBridge(value = PotionBuilder.class, instance = false)
  public static ItemStack vanilla(PotionBuilder.Type type, PotionType potion) {
    return build(setBaseType(PotionBuilder.of(type), potion));
  }

  @ApiBridge(value = PotionBuilder.class, instance = false)
  public static PotionBuilder of(ItemStack stack) {
    PotionBuilder.Type type = null;
    for (PotionBuilder.Type value : PotionBuilder.Type.values()) {
      if (material(value) == stack.getType()) {
        type = value;
        break;
      }
    }

    if (type == null) {
      throw new IllegalArgumentException("Invalid potion type!");
    }

    ItemEditor editor = Adapt.platform.editItem(stack);
    PotionBuilder builder = setBaseItem(PotionBuilder.of(type), stack);
    PotionMeta meta = (PotionMeta) stack.getItemMeta();
    builder.setBaseType(PotionBuilderVersion.basePotion(meta));
    setLore(builder, editor.lore());
    setColor(builder, meta.getColor());
    setName(builder, editor.customName());
    for (PotionEffect effect : meta.getCustomEffects()) {
      addEffect(builder, effect);
    }

    return builder;
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder setColor(PotionBuilder builder, Color color) {
    return builder.setColor(color == null ? null : new Rgb(color.getRed(), color.getGreen(), color.getBlue()));
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder addEffect(PotionBuilder builder, PotionEffect effect) {
    Objects.requireNonNull(effect, "effect");
    return builder.addEffect(new EffectInstance(BukkitRefs.id(effect.getType()), effect.getDuration(),
        effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder addEffect(PotionBuilder builder, PotionEffectType type, int duration, int amplifier,
                                        boolean ambient, boolean particles, boolean icon) {
    return addEffect(builder, new PotionEffect(type, duration, amplifier, ambient, particles, icon));
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder setName(PotionBuilder builder, Component name) {
    String plain = name == null ? null : PlainTextComponentSerializer.plainText().serialize(name);
    builder.setName(plain);
    Extras extras = extras(builder);
    extras.name = name;
    extras.plainName = plain;
    return builder;
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder addLore(PotionBuilder builder, Component lore) {
    Extras extras = extras(builder);
    if (extras.lore == null) {
      extras.lore = new ArrayList<>();
    }

    extras.lore.add(lore);
    return builder;
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder setLore(PotionBuilder builder, List<Component> lore) {
    extras(builder).lore = lore;
    return builder;
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder setBaseItem(PotionBuilder builder, ItemStack item) {
    extras(builder).baseItem = item;
    return builder;
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionBuilder setBaseType(PotionBuilder builder, PotionType type) {
    return builder.setBaseType(type == null ? null : BukkitResolve.id(type));
  }

  @ApiBridge(PotionBuilder.class)
  public static ItemStack build(PotionBuilder builder) {
    Material material = material(builder.getType());
    ItemStack stack = getBaseItem(builder);
    if (stack == null) {
      stack = new ItemStack(material);
    } else if (stack.getType() != material) {
      stack.setType(material);
    }

    PotionMeta meta = (PotionMeta) stack.getItemMeta();
    meta.clearCustomEffects();
    for (EffectInstance effect : builder.getEffects()) {
      meta.addCustomEffect(new PotionEffect(BukkitRefs.effect(effect.type()), effect.durationTicks(),
          effect.amplifier(), effect.ambient(), effect.particles(), effect.icon()), true);
    }
    Color color = getColor(builder);
    if (color != null) {
      meta.setColor(color);
    }
    stack.setItemMeta(meta);

    Adapt.platform.editItem(stack)
        .lore(getLore(builder))
        .customName(getName(builder))
        .build();

    Id baseType = builder.getBaseType();
    if (baseType != null && getBaseType(builder) != null) {
      PotionMeta based = (PotionMeta) stack.getItemMeta();
      if (based != null) {
        PotionBuilderVersion.setBasePotion(based, baseType);
        stack.setItemMeta(based);
      }
    }
    return stack;
  }

  @ApiBridge(PotionBuilder.class)
  public static Component getName(PotionBuilder builder) {
    String name = builder.getName();
    if (name == null) {
      return null;
    }

    Extras extras = EXTRAS.get(builder);
    if (extras != null && extras.name != null && name.equals(extras.plainName)) {
      return extras.name;
    }
    return Component.text(name).decoration(TextDecoration.ITALIC, false);
  }

  @ApiBridge(PotionBuilder.class)
  public static List<Component> getLore(PotionBuilder builder) {
    Extras extras = EXTRAS.get(builder);
    return extras == null ? null : extras.lore;
  }

  @ApiBridge(PotionBuilder.class)
  public static Color getColor(PotionBuilder builder) {
    Rgb color = builder.getColor();
    return color == null ? null : Color.fromRGB(color.red(), color.green(), color.blue());
  }

  @ApiBridge(PotionBuilder.class)
  public static PotionType getBaseType(PotionBuilder builder) {
    Id type = builder.getBaseType();
    return type == null ? null : BukkitResolve.potion(type);
  }

  @ApiBridge(PotionBuilder.class)
  public static ItemStack getBaseItem(PotionBuilder builder) {
    Extras extras = EXTRAS.get(builder);
    return extras == null ? null : extras.baseItem;
  }

  @ApiBridge(value = PotionBuilder.Type.class, name = "getMaterial")
  public static Material material(PotionBuilder.Type type) {
    return BukkitRefs.material(type.getMaterial());
  }

  private static Extras extras(PotionBuilder builder) {
    return EXTRAS.computeIfAbsent(builder, (PotionBuilder ignored) -> new Extras());
  }

  private static final class Extras {
    private Component name;
    private String plainName;
    private List<Component> lore;
    private ItemStack baseItem;
  }
}
