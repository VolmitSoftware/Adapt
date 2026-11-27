package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.pack.surface.PackSession;

import java.util.List;

/** What one viewer's pack screen shows: which screen, its page and tab, and the pending choices on it. */
final class PackViewer {
  Screen screen = Screen.SKILLS;
  String skillId = "";
  String adaptationId = "";
  int page;
  int tab;
  int pickedLevel = -1;
  long armedUntil;
  int recipe;
  String search = "";
  boolean partners;
  String notice = "";
  MutationSpreadView spread;
  /** The skills offered as a mutation's lead while the viewer picks one; empty once a lead is chosen. */
  List<String> leads = List.of();
  long fingerprint;
  long catalogRevision;
  boolean refreshDue;
  PackSession session;
  String armedControl = "";

  void go(Screen next) {
    screen = next;
    page = 0;
    tab = 0;
    pickedLevel = -1;
    armedUntil = 0L;
    armedControl = "";
    recipe = 0;
    partners = false;
    leads = List.of();
    notice = "";
  }

  /** True while {@code control} waits for its confirming click. */
  boolean armed(String control, long now) {
    return armedUntil > now && armedControl.equals(control);
  }

  enum Screen {
    SKILLS,
    SKILL,
    ADAPTATION,
    MUTATE
  }
}
