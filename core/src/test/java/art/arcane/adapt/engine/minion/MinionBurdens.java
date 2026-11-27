package art.arcane.adapt.engine.minion;

import art.arcane.adapt.api.attribute.Attributes;

import java.lang.reflect.Field;

public final class MinionBurdens {
  private MinionBurdens() {
  }

  public static MinionBurden install() throws ReflectiveOperationException {
    MinionBurden burden = new MinionBurden((player, action) -> {
      action.run();
      return true;
    }, Attributes.MAX_HEALTH);
    instanceField().set(null, burden);
    return burden;
  }

  public static void uninstall() throws ReflectiveOperationException {
    instanceField().set(null, null);
  }

  public static double healthPerMinion(MinionBurden burden) throws ReflectiveOperationException {
    Field field = MinionBurden.class.getDeclaredField("healthPerMinion");
    field.setAccessible(true);
    return field.getDouble(burden);
  }

  private static Field instanceField() throws ReflectiveOperationException {
    Field field = MinionBurden.class.getDeclaredField("instance");
    field.setAccessible(true);
    return field;
  }
}
