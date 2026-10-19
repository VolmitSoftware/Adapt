import json
from dataclasses import asdict, dataclass, field
from pathlib import Path

PLAYER_EYE_HEIGHT: float = 1.62
START_TICK: int = 10
TICKS_PER_SECOND: float = 20.0


@dataclass(frozen=True)
class Take:
    id: str
    skill: str
    set: str
    level: int
    replay: str
    start_tick: int
    stop_tick: int
    evidence_ok: bool
    evidence_detail: str
    takes: int
    camera: dict
    actor_uuid: str
    status: str
    export_error: str = ''
    visual: str = ''
    pov_capture: bool = False
    stills: list[dict] = field(default_factory=list)
    min_golem_distance: float | None = None
    crf: dict = field(default_factory=dict)


def clip_seconds(take: Take) -> float:
    return max(0, take.stop_tick - take.start_tick - START_TICK) / TICKS_PER_SECOND


def live_pov_path(output: Path, identifier: str) -> Path:
    return output / 'intermediate' / (identifier + '-pov-live.mp4')


def still_path(output: Path, identifier: str, name: str) -> Path:
    return output / 'stills' / (identifier + '-' + name + '.png')


def load(path: Path) -> dict[str, Take]:
    if not path.is_file():
        return {}
    raw: dict = json.loads(path.read_text())
    return {identifier: Take(**body) for identifier, body in raw.items()}


def save(path: Path, takes: dict[str, Take]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({identifier: asdict(take) for identifier, take in takes.items()}, indent=2, sort_keys=True) + '\n')
