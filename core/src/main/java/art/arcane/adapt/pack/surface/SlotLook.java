package art.arcane.adapt.pack.surface;

import art.arcane.adapt.platform.value.Id;

import java.util.List;

/**
 * How a pack window draws the item in one slot, beyond its {@code MenuIcon} (which keeps the name, lore and count):
 * the pack item model ({@code itemModel}, null for the icon's own), the custom model data lists a composite model
 * reads ({@code strings}, {@code floats}, {@code colours}), the tooltip frame ({@code tooltipStyle}, null for vanilla), whether
 * the tooltip is hidden, the text shadow of every tooltip line ({@code loreShadow} as ARGB, {@link #VANILLA_SHADOW} for
 * the client's own; 0 for dark text on a parchment frame), the animated sprite the model shows ({@code sprite}, null
 * for a still one, animated by the pack on the client) and the cooldown group that one-shot effects run on
 * ({@code cooldownGroup}, null for none; see {@link PackSession#oneShot}). The {@code MenuIcon} type of every slot with
 * a pack model is the manifest's carrier item; {@code itemModel} decides the picture.
 */
public record SlotLook(Id itemModel, List<String> strings, List<Float> floats, List<Integer> colours, String tooltipStyle,
                       boolean hideTooltip, int loreShadow, String sprite, Id cooldownGroup) {
  public static final int VANILLA_SHADOW = -1;

  public SlotLook {
    strings = strings == null ? List.of() : List.copyOf(strings);
    floats = floats == null ? List.of() : List.copyOf(floats);
    colours = colours == null ? List.of() : List.copyOf(colours);
  }

  /** A model with the kit's tooltip frame and shadow. */
  public static SlotLook of(Id itemModel, String tooltipStyle, int loreShadow) {
    return new SlotLook(itemModel, List.of(), List.of(), List.of(), tooltipStyle, false, loreShadow, null, null);
  }

  /** An item that draws nothing and shows no tooltip. */
  public static SlotLook hidden(Id blankModel) {
    return new SlotLook(blankModel, List.of(), List.of(), List.of(), null, true, VANILLA_SHADOW, null, null);
  }

  public SlotLook withSprite(String animatedSprite) {
    return new SlotLook(itemModel, strings, floats, colours, tooltipStyle, hideTooltip, loreShadow, animatedSprite, cooldownGroup);
  }

  public SlotLook withCooldownGroup(Id group) {
    return new SlotLook(itemModel, strings, floats, colours, tooltipStyle, hideTooltip, loreShadow, sprite, group);
  }

  public SlotLook withModelData(List<String> newStrings, List<Float> newFloats, List<Integer> newColours) {
    return new SlotLook(itemModel, newStrings, newFloats, newColours, tooltipStyle, hideTooltip, loreShadow, sprite, cooldownGroup);
  }
}
