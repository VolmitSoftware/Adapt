from dataclasses import dataclass


@dataclass(frozen=True)
class EvidenceResult:
    ok: bool
    sound_found: bool
    particle_found: bool
    detail: str


def resource_id(name: str) -> str:
    return name if ':' in name else 'minecraft:' + name


def named(event: dict, kind: str, expected: str) -> bool:
    return event.get('type') == kind and resource_id(str(event.get('name', ''))) == resource_id(expected)


def visual_detail(text: str) -> str:
    return 'visual-only: ' + text


def check(events: list[dict], start_tick: int, stop_tick: int, sound: str | None, particle: str | None, visual: str | None = None) -> EvidenceResult:
    if visual and sound is None and particle is None:
        return EvidenceResult(ok=True, sound_found=True, particle_found=True, detail=visual_detail(visual))
    sound_found: bool = sound is None
    particle_found: bool = particle is None
    for event in events:
        tick: int = int(event.get('tick', -1))
        if tick < start_tick or tick > stop_tick:
            continue
        if sound is not None and named(event, 'sound', sound):
            sound_found = True
        if particle is not None and named(event, 'particle', particle) and bool(event.get('rendered')):
            particle_found = True
    detail: str = 'sound=' + side(sound, sound_found) + ' particle=' + side(particle, particle_found)
    return EvidenceResult(ok=sound_found and particle_found, sound_found=sound_found, particle_found=particle_found, detail=detail)


def side(expected: str | None, found: bool) -> str:
    return 'n/a' if expected is None else str(found)
