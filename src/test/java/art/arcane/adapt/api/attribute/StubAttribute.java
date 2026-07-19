package art.arcane.adapt.api.attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

final class StubAttribute implements Attribute {
  private final String id;

  StubAttribute(String id) {
    this.id = id;
  }

  @Override
  public Attribute.Sentiment getSentiment() {
    return null;
  }

  @Override
  public double getDefaultValue() {
    return 0;
  }

  @Override
  public NamespacedKey getKey() {
    return new NamespacedKey("test", id);
  }

  @Override
  public String getTranslationKey() {
    return id;
  }

  @Override
  public String translationKey() {
    return id;
  }

  @Override
  public String name() {
    return id;
  }

  @Override
  public int ordinal() {
    return 0;
  }

  @Override
  public int compareTo(Attribute other) {
    return 0;
  }
}
