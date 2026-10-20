import json
from dataclasses import dataclass
from pathlib import Path

from media_textures import CustomItem

GRID: int = 3


@dataclass(frozen=True)
class Recipe:
    id: str
    result: str
    yield_count: int
    pattern: tuple[str, ...]
    key: dict[str, str]


@dataclass(frozen=True)
class Infusion:
    id: str
    center: str
    pedestals: tuple[str, ...]
    result: str
    essence_levels: int
    duration_ticks: int


@dataclass(frozen=True)
class Requirement:
    id: str
    level: int


@dataclass(frozen=True)
class Adaptation:
    id: str
    name: str
    requires: tuple[Requirement, ...]


@dataclass(frozen=True)
class Catalog:
    items: dict[str, CustomItem]
    recipes: tuple[Recipe, ...]
    infusions: tuple[Infusion, ...]
    adaptations: tuple[Adaptation, ...]

    def name(self, item: str) -> str:
        custom: CustomItem | None = self.items.get(item)
        if custom is not None:
            return custom.name
        return item.split(':')[-1].split('/')[-1].lstrip('#').replace('_', ' ').title()


def pattern_problem(recipe: Recipe) -> str:
    if not 1 <= len(recipe.pattern) <= GRID or any(not 1 <= len(row) <= GRID for row in recipe.pattern):
        return 'pattern must be 1 to ' + str(GRID) + ' rows of 1 to ' + str(GRID) + ' symbols'
    for symbol in sorted({symbol for row in recipe.pattern for symbol in row if symbol != ' '} - set(recipe.key)):
        return 'pattern uses ' + symbol + ', which has no key'
    return ''


def parse(raw: dict) -> Catalog:
    try:
        items: dict[str, CustomItem] = {key: CustomItem(name=body['name'], base=body['base'], tint=body.get('tint'), tier=body.get('tier'))
                                        for key, body in raw['items'].items()}
        recipes: tuple[Recipe, ...] = tuple(Recipe(id=body['id'], result=body['result'], yield_count=int(body['yield']),
                                                   pattern=tuple(body['pattern']), key=dict(body['key'])) for body in raw['recipes'])
        infusions: tuple[Infusion, ...] = tuple(Infusion(id=body['id'], center=body['center'], pedestals=tuple(body['pedestals']), result=body['result'],
                                                         essence_levels=int(body['essenceLevels']), duration_ticks=int(body['durationTicks']))
                                                for body in raw['infusions'])
        adaptations: tuple[Adaptation, ...] = tuple(Adaptation(id=body['id'], name=body['name'],
                                                               requires=tuple(Requirement(id=entry['id'], level=int(entry['level'])) for entry in body['requires']))
                                                    for body in raw['adaptations'])
    except (KeyError, TypeError, ValueError, AttributeError) as failure:
        raise ValueError('Malformed recipe catalog: ' + repr(failure)) from failure
    for recipe in recipes:
        problem: str = pattern_problem(recipe)
        if problem:
            raise ValueError('Malformed recipe catalog: ' + recipe.id + ' ' + problem)
    return Catalog(items=items, recipes=recipes, infusions=infusions, adaptations=adaptations)


def load(path: Path) -> Catalog:
    return parse(json.loads(path.read_text(encoding='utf-8')))
