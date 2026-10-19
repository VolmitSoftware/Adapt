import dataclasses
import re

import beats
import choreography
import manifest

LIMIT_BLOCKS: float = 2.5
SAMPLE_TICKS: int = 5
ALLOW_FLOORS: dict[str, float] = {'bind-ritual': 2.0, 'frozen-gui': 2.4}
NEAREST: re.Pattern[str] = re.compile(r'nearest=(none|\d+(?:\.\d+)?)')


def parse_nearest(reply: str) -> float | None:
    found: re.Match[str] | None = NEAREST.search(reply)
    if found is None:
        raise RuntimeError('Unexpected near reply: ' + reply)
    return None if found.group(1) == 'none' else float(found.group(1))


def clip_seconds(tick: int, start_tick: int) -> float:
    return (tick - start_tick - manifest.START_TICK) / manifest.TICKS_PER_SECOND


def message(violation: tuple[float, float], closest: tuple[float, float] | None = None) -> str:
    text: str = 'golem ' + format(violation[1], '.2f') + ' blocks from the camera at ' + format(violation[0], 'g') + ' s, closer than ' + format(LIMIT_BLOCKS, 'g')
    if closest is not None and closest != violation and closest[1] < violation[1]:
        text += ' (closest ' + format(closest[1], '.2f') + ' blocks at ' + format(closest[0], 'g') + ' s)'
    return text


@dataclasses.dataclass
class Proximity:
    rcon: beats.RconClient
    actor: str
    start_tick: int
    allow: tuple[choreography.AllowClose, ...] = ()
    samples: list[tuple[float, float]] = dataclasses.field(default_factory=list)
    next_tick: int = 0

    def sample(self, tick: int) -> None:
        if tick < self.next_tick:
            return
        self.next_tick = tick + SAMPLE_TICKS
        blocks: float | None = parse_nearest(self.rcon.command('adaptqa demo near ' + self.actor))
        seconds: float = clip_seconds(tick, self.start_tick)
        if blocks is not None and seconds >= 0:
            self.samples.append((seconds, blocks))

    def minimum(self) -> float | None:
        return min((blocks for _, blocks in self.samples), default=None)

    def series(self) -> str:
        return ' '.join(format(seconds, 'g') + 's:' + format(blocks, '.2f') for seconds, blocks in self.samples)

    def closest(self) -> tuple[float, float] | None:
        return min(self.samples, key=lambda sample: sample[1], default=None)

    def minimum_at(self) -> float | None:
        closest: tuple[float, float] | None = self.closest()
        return None if closest is None else closest[0]

    def excused(self, seconds: float, blocks: float) -> bool:
        return any(window.start <= seconds <= window.end and blocks >= ALLOW_FLOORS[window.reason] for window in self.allow)

    def violation(self) -> tuple[float, float] | None:
        for seconds, blocks in self.samples:
            if round(blocks, 2) < LIMIT_BLOCKS and not self.excused(seconds, round(blocks, 2)):
                return seconds, blocks
        return None
