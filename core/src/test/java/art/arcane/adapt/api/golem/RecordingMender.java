package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;

final class RecordingMender implements GolemMend.Mender {
  final List<GolemMend.RegenSenses> senses = new ArrayList<>();
  final List<GolemHandle> asked = new ArrayList<>();
  final List<GolemHandle> shattered = new ArrayList<>();
  final List<Loc> shatteredAt = new ArrayList<>();
  long intervalMillis = 5_000L;
  double regenAmount;
  boolean shatters;

  @Override
  public long regenIntervalMillis() {
    return intervalMillis;
  }

  @Override
  public double regen(GolemHandle handle, GolemMend.RegenSenses next) {
    senses.add(next);
    return regenAmount;
  }

  @Override
  public boolean shatters(GolemHandle handle) {
    asked.add(handle);
    return shatters;
  }

  @Override
  public void shattered(GolemHandle handle, Loc at) {
    shattered.add(handle);
    shatteredAt.add(at);
  }
}
