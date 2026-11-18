package art.arcane.adapt.platform.menu;

@FunctionalInterface
public interface MenuAction {
  void click(MenuClick click);
}
