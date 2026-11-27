package art.arcane.adapt.api.asset;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How a skill, adaptation, advancement or preference choice is drawn. Start from {@link #item(Id)}.
 *
 * <p>{@link #item()} is always shown where nothing better is available. A native client draws {@link #sprite()} when it
 * has that asset; a vanilla client with the server pack draws the item with {@link #customModelData()} or
 * {@link #itemModel()}.
 */
@ApiStatus.Experimental
public final class Icon {
  private final Id item;
  private final OptionalInt customModelData;
  private final Optional<Id> itemModel;
  private final Optional<Id> sprite;

  private Icon(Id item, OptionalInt customModelData, Optional<Id> itemModel, Optional<Id> sprite) {
    this.item = Objects.requireNonNull(item, "item");
    this.customModelData = Objects.requireNonNull(customModelData, "customModelData");
    this.itemModel = Objects.requireNonNull(itemModel, "itemModel");
    this.sprite = Objects.requireNonNull(sprite, "sprite");
  }

  /** A plain item icon. */
  public static Icon item(Id item) {
    return new Icon(item, OptionalInt.empty(), Optional.empty(), Optional.empty());
  }

  /** A copy of this icon that native clients draw with the bundle texture {@code sprite}. */
  public Icon sprite(Id sprite) {
    return new Icon(item, customModelData, itemModel, Optional.of(sprite));
  }

  /** A copy of this icon whose item carries {@code value} as custom model data. */
  public Icon customModelData(int value) {
    return new Icon(item, OptionalInt.of(value), itemModel, sprite);
  }

  /** A copy of this icon whose item uses the item model {@code model}. */
  public Icon itemModel(Id model) {
    return new Icon(item, customModelData, Optional.of(model), sprite);
  }

  /** The item shown as a fallback everywhere. */
  public Id item() {
    return item;
  }

  /** Custom model data for packs on older clients. */
  public OptionalInt customModelData() {
    return customModelData;
  }

  /** The item model component for packs on current clients. */
  public Optional<Id> itemModel() {
    return itemModel;
  }

  /** A 16x16 texture from an {@link AssetBundle}, such as {@code alchemist:gui/skill/alchemy}. */
  public Optional<Id> sprite() {
    return sprite;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Icon icon && item.equals(icon.item) && customModelData.equals(icon.customModelData)
        && itemModel.equals(icon.itemModel) && sprite.equals(icon.sprite);
  }

  @Override
  public int hashCode() {
    return Objects.hash(item, customModelData, itemModel, sprite);
  }

  @Override
  public String toString() {
    return "Icon[item=" + item + ", customModelData=" + customModelData + ", itemModel=" + itemModel + ", sprite="
        + sprite + "]";
  }
}
