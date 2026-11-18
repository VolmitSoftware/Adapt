#!/usr/bin/env python3
"""Checks every mod mixin against the Minecraft jar of each loader and version target.

Usage: python3 adapters/tools/verify-mixins.py [-v] [--jar loader:version=path ...] [loader[:version] ...]

--jar also checks a deployed game jar (a NeoForge minecraft-server-patched jar, a Forge server jar, a vanilla
server jar) for that loader and version, so builds newer than the compile target are covered. Join several jars
with ':' when patched classes ship apart from the vanilla jar (Forge: forge-<v>-server.jar:server-<mc>-unpacked.jar).
"""
import concurrent.futures
import glob
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADAPTERS = ROOT / 'adapters'
COMMON = ADAPTERS / 'mod-common'
HOME = Path.home()
LOADERS = ('fabric', 'neoforge', 'forge')

PRIMITIVES = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S',
              'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}
REF_WRAPPERS = {'LocalBooleanRef': 'Z', 'LocalByteRef': 'B', 'LocalCharRef': 'C', 'LocalShortRef': 'S',
                'LocalIntRef': 'I', 'LocalLongRef': 'J', 'LocalFloatRef': 'F', 'LocalDoubleRef': 'D'}
JAVA_LANG = {'Object', 'String', 'Integer', 'Float', 'Double', 'Long', 'Short', 'Byte', 'Boolean',
             'Character', 'Number', 'Iterable', 'Runnable', 'Void', 'Class', 'Enum', 'Record',
             'CharSequence', 'Comparable', 'Throwable', 'Exception', 'RuntimeException'}
MIXIN_PACKAGES = ('org.spongepowered.', 'com.llamalad7.mixinextras.')
POINT_INJECTORS = {'Inject', 'WrapOperation', 'ModifyExpressionValue', 'ModifyReturnValue', 'ModifyArg',
                   'Redirect', 'WrapWithCondition', 'ModifyVariable'}
PASSIVE = {'Mixin', 'Shadow', 'Final', 'Mutable', 'Unique', 'Override', 'At', 'Local', 'Accessor', 'Invoker'}
INVOKES = {'invokevirtual', 'invokespecial', 'invokestatic', 'invokeinterface'}
FIELDS = {'getfield', 'putfield', 'getstatic', 'putstatic'}
RETURNS = {'return', 'ireturn', 'lreturn', 'freturn', 'dreturn', 'areturn'}



def strip_comments(text):
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c in '"\'':
            j = i + 1
            while j < n and text[j] != c:
                j += 2 if text[j] == '\\' else 1
            out.append(text[i:j + 1])
            i = j + 1
        elif text.startswith('//', i):
            while i < n and text[i] != '\n':
                i += 1
        elif text.startswith('/*', i):
            end = text.find('*/', i + 2)
            i = n if end < 0 else end + 2
            out.append(' ')
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def skip_string(text, i):
    quote = text[i]
    j = i + 1
    while j < len(text) and text[j] != quote:
        j += 2 if text[j] == '\\' else 1
    return j + 1


def balanced(text, i, open_ch='(', close_ch=')'):
    depth = 0
    j = i
    while j < len(text):
        c = text[j]
        if c in '"\'':
            j = skip_string(text, j)
            continue
        if c == open_ch:
            depth += 1
        elif c == close_ch:
            depth -= 1
            if depth == 0:
                return j
        j += 1
    raise ValueError('unbalanced ' + open_ch)


def split_top(text, sep=','):
    parts, depth, cur, i = [], 0, [], 0
    while i < len(text):
        c = text[i]
        if c in '"\'':
            j = skip_string(text, i)
            cur.append(text[i:j])
            i = j
            continue
        if c in '({<[':
            depth += 1
        elif c in ')}>]':
            depth -= 1
        if c == sep and depth == 0:
            parts.append(''.join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    if ''.join(cur).strip():
        parts.append(''.join(cur).strip())
    return parts


class Annotation:
    def __init__(self, name, args):
        self.name = name
        self.simple = name.rsplit('.', 1)[-1]
        self.raw = args
        self.attrs = {}
        if args is None or not args.strip():
            return
        for part in split_top(args):
            m = re.match(r'^(\w+)\s*=\s*(.*)$', part, re.S)
            if m and not part.startswith('"'):
                self.attrs[m.group(1)] = m.group(2).strip()
            else:
                self.attrs['value'] = part.strip()

    def strings(self, key):
        value = self.attrs.get(key)
        return [] if value is None else re.findall(r'"((?:[^"\\]|\\.)*)"', value)

    def string(self, key):
        found = self.strings(key)
        return found[0] if found else None

    def integer(self, key):
        value = self.attrs.get(key)
        return int(value) if value is not None and re.fullmatch(r'-?\d+', value) else None

    def boolean(self, key):
        return self.attrs.get(key) == 'true'

    def nested(self, key):
        value = self.attrs.get(key)
        return [] if value is None else parse_annotations(value)


def parse_annotation_at(text, i):
    m = re.compile(r'@([\w.]+)\s*').match(text, i)
    j = m.end()
    args = None
    if j < len(text) and text[j] == '(':
        end = balanced(text, j)
        args = text[j + 1:end]
        j = end + 1
    return Annotation(m.group(1), args), j


def parse_annotations(text):
    found, i = [], 0
    while True:
        i = text.find('@', i)
        if i < 0:
            return found
        ann, i = parse_annotation_at(text, i)
        found.append(ann)


def strip_generics(type_text):
    out, depth = [], 0
    for c in type_text:
        if c == '<':
            depth += 1
        elif c == '>':
            depth -= 1
        elif depth == 0:
            out.append(c)
    return ''.join(out).strip()


def generic_args(type_text):
    start = type_text.find('<')
    if start < 0:
        return []
    return split_top(type_text[start + 1:type_text.rfind('>')])


class Param:
    def __init__(self, text):
        self.annotations = []
        i = 0
        text = text.strip()
        while text.startswith('@', i):
            ann, i = parse_annotation_at(text, i)
            self.annotations.append(ann)
            while i < len(text) and text[i].isspace():
                i += 1
        rest = re.sub(r'^\s*final\s+', '', text[i:])
        m = re.match(r'^(.*?)\s*([\w$]+)$', rest, re.S)
        self.type = m.group(1).strip()
        self.name = m.group(2)


class Member:
    def __init__(self, annotations, decl):
        self.annotations = annotations
        self.decl = decl
        paren = decl.find('(')
        eq = decl.find('=')
        self.is_method = paren >= 0 and (eq < 0 or paren < eq)
        head = decl[:paren] if self.is_method else (decl[:eq] if eq >= 0 else decl)
        head = re.sub(r'\b(public|private|protected|static|abstract|final|default|synchronized|native|transient|volatile)\b', ' ', head)
        head = re.sub(r'^\s*<[^()]*?>\s*', ' ', head.strip()).strip()
        m = re.match(r'^(.*?)\s*([\w$]+)\s*$', head, re.S)
        self.type = m.group(1).strip() if m else ''
        self.name = m.group(2) if m else head
        self.is_static = bool(re.search(r'\bstatic\b', decl[:paren] if self.is_method else decl))
        self.params = []
        if self.is_method:
            inner = decl[paren + 1:balanced(decl, paren)]
            self.params = [Param(p) for p in split_top(inner)]


class MixinSource:
    def __init__(self, path, label):
        self.path = path
        self.label = label
        self.text = strip_comments(path.read_text(encoding='utf-8'))
        pkg = re.search(r'^\s*package\s+([\w.]+)\s*;', self.text, re.M)
        self.package = pkg.group(1) if pkg else ''
        self.imports = {}
        for imp in re.findall(r'^\s*import\s+(?!static)([\w.]+)\s*;', self.text, re.M):
            self.imports[imp.rsplit('.', 1)[-1]] = imp
        self.targets = []
        self.members = []
        self.class_annotations = []
        start = self.text.find('@Mixin')
        if start < 0:
            return
        i = start
        while True:
            while i < len(self.text) and self.text[i].isspace():
                i += 1
            if not self.text.startswith('@', i):
                break
            ann, i = parse_annotation_at(self.text, i)
            self.class_annotations.append(ann)
        mixin = next(a for a in self.class_annotations if a.simple == 'Mixin')
        for key in ('value', 'targets'):
            raw = mixin.attrs.get(key, '')
            for ref in re.findall(r'([\w.]+)\.class', raw):
                self.targets.append(self.resolve(ref))
            if key == 'targets':
                self.targets.extend(s.replace('/', '.') for s in mixin.strings('targets'))
        body = self.text.find('{', i)
        self._scan_members(body + 1, balanced(self.text, body, '{', '}'))

    def _scan_members(self, i, end):
        text = self.text
        depth = 0
        while i < end:
            c = text[i]
            if c in '"\'':
                i = skip_string(text, i)
                continue
            if c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
            elif c == '@' and depth == 0 and not text.startswith('@interface', i):
                annotations = []
                while text.startswith('@', i):
                    ann, i = parse_annotation_at(text, i)
                    annotations.append(ann)
                    while text[i].isspace():
                        i += 1
                j, paren = i, 0
                while j < end:
                    ch = text[j]
                    if ch in '"\'':
                        j = skip_string(text, j)
                        continue
                    if ch == '(':
                        paren += 1
                    elif ch == ')':
                        paren -= 1
                    elif ch in ';{' and paren == 0:
                        break
                    j += 1
                self.members.append(Member(annotations, text[i:j]))
                if j < end and text[j] == '{':
                    j = balanced(text, j, '{', '}')
                i = j + 1
                continue
            i += 1

    def resolve(self, name):
        name = name.strip()
        parts = name.split('.')
        if parts[0] in self.imports:
            base = self.imports[parts[0]]
            return base + ''.join('$' + p for p in parts[1:])
        if parts[0][:1].islower():
            pkg = []
            while parts and parts[0][:1].islower():
                pkg.append(parts.pop(0))
            return '.'.join(pkg + parts[:1]) + ''.join('$' + p for p in parts[1:])
        if parts[0] in JAVA_LANG:
            return 'java.lang.' + name
        return (self.package + '.' if self.package else '') + parts[0] + ''.join('$' + p for p in parts[1:])

    def annotation_fqcn(self, ann):
        return ann.name if '.' in ann.name else self.imports.get(ann.simple)

    def descriptor(self, type_text, unwrap_refs=False):
        type_text = type_text.strip()
        dims = 0
        if type_text.endswith('...'):
            dims += 1
            type_text = type_text[:-3].strip()
        raw = strip_generics(type_text)
        while raw.endswith('[]'):
            dims += 1
            raw = raw[:-2].strip()
        simple = raw.rsplit('.', 1)[-1]
        if unwrap_refs and dims == 0:
            if simple in REF_WRAPPERS:
                return REF_WRAPPERS[simple]
            if simple == 'LocalRef':
                args = generic_args(type_text)
                return self.descriptor(args[0]) if args else None
        if raw in PRIMITIVES:
            return '[' * dims + PRIMITIVES[raw]
        return '[' * dims + 'L' + self.resolve(raw).replace('.', '/') + ';'



class Insn:
    __slots__ = ('offset', 'op', 'ref')

    def __init__(self, offset, op, ref):
        self.offset, self.op, self.ref = offset, op, ref


class MethodInfo:
    def __init__(self, name, is_static):
        self.name = name
        self.is_static = is_static
        self.desc = None
        self.insns = []
        self.lvt = None
        self.has_code = False

    def params(self):
        args = re.match(r'^\((.*)\)', self.desc).group(1)
        out, slot = [], 0 if self.is_static else 1
        for d in re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)', args):
            out.append((slot, d))
            slot += 2 if d in ('J', 'D') else 1
        return out


class ClassInfo:
    def __init__(self, name):
        self.name = name
        self.superclass = None
        self.methods = {}
        self.fields = {}

    def all_methods(self):
        for group in self.methods.values():
            yield from group


HEADER = re.compile(r'^(?:[\w-]+ )*(class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(?: extends ([\w.$]+))?')
REF = re.compile(r'^(Method|InterfaceMethod|Field) (?:(.+?)\.)?"?([\w$<>]+)"?:(.+)$')


def parse_javap(output):
    classes = {}
    cur = None
    member = None
    section = None
    for line in output.split('\n'):
        if not line.startswith(' '):
            m = HEADER.match(line)
            if m and line.rstrip().endswith('{'):
                cur = ClassInfo(m.group(2))
                classes[cur.name] = cur
                if m.group(1) == 'class' and m.group(3):
                    cur.superclass = m.group(3)
                member = None
            elif line.startswith('}'):
                cur = None
            continue
        if cur is None:
            continue
        if line.startswith('  ') and not line.startswith('   '):
            text = line.strip()
            section = None
            if text == 'static {};':
                member = MethodInfo('<clinit>', True)
                cur.methods.setdefault('<clinit>', []).append(member)
            elif '(' in text and text.endswith(';'):
                tokens = text[:text.index('(')].split()
                name = tokens[-1]
                if '.' in name or name == cur.name:
                    name = '<init>'
                member = MethodInfo(name, 'static' in tokens[:-1])
                cur.methods.setdefault(name, []).append(member)
            elif text.endswith(';'):
                tokens = text[:-1].split()
                member = None
                cur.fields[tokens[-1]] = 'static' in tokens[:-1]
            else:
                member = None
            continue
        if member is None:
            continue
        stripped = line.strip()
        if line.startswith('    ') and not line.startswith('     '):
            section = None
            if stripped.startswith('descriptor: ') and member.desc is None:
                member.desc = stripped[len('descriptor: '):]
            elif stripped == 'Code:':
                section = 'code'
                member.has_code = True
            continue
        if stripped.endswith(':') and re.fullmatch(r'[A-Za-z]+:', stripped):
            section = 'lvt' if stripped == 'LocalVariableTable:' else 'other'
            if section == 'lvt':
                member.lvt = []
            continue
        if section == 'code':
            m = re.match(r'^\s+(\d+): (\w+)(.*)$', line)
            if m:
                ref = m.group(3).split('//', 1)[1].strip() if '//' in m.group(3) else None
                member.insns.append(Insn(int(m.group(1)), m.group(2), ref))
        elif section == 'lvt':
            m = re.match(r'^\s+(\d+)\s+(\d+)\s+(\d+)\s+(\S+)\s+(\S+)\s*$', line)
            if m:
                member.lvt.append((int(m.group(1)), int(m.group(2)), int(m.group(3)), m.group(4), m.group(5)))
    return classes


class Jar:
    def __init__(self, path):
        self.path = path
        self.cache = {}

    def preload(self, names):
        names = [n for n in dict.fromkeys(names) if n not in self.cache]
        if not names:
            return
        result = subprocess.run(['javap', '-c', '-l', '-p', '-s', '-cp', str(self.path)] + names,
                                capture_output=True, text=True)
        parsed = parse_javap(result.stdout)
        for name in names:
            self.cache[name] = parsed.get(name)

    def get(self, name):
        if name not in self.cache:
            self.preload([name])
        return self.cache[name]



def load_targets():
    with open(ROOT / 'gradle' / 'targets.json', encoding='utf-8') as handle:
        return json.load(handle)


def first_glob(patterns):
    for pattern in patterns:
        hits = sorted(glob.glob(str(pattern)))
        if hits:
            return Path(hits[0])
    return None


def locate_jar(loader, version, props):
    if loader == 'fabric':
        return first_glob([
            HOME / f'.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/{version}/minecraft-merged-deobf-{version}.jar',
            ADAPTERS / f'fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-deobf/{version}/minecraft-merged-deobf-{version}.jar',
        ])
    if loader == 'neoforge':
        neo = props.get('neoforge')
        if not neo:
            return None
        return first_glob([ADAPTERS / f'neoforge/build/{version}/moddev/artifacts/minecraft-patched-{neo}.jar',
                           ADAPTERS / f'neoforge/build/*/moddev/artifacts/minecraft-patched-{neo}.jar'])
    forge = props.get('forge')
    if not forge:
        return None
    mappings = props.get('forgeRuntimeMappings', 'official')
    return first_glob([HOME / f'.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/{forge}/{mappings}/{version}/recompiled.jar',
                       HOME / f'.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/{forge}/*/{version}/recompiled.jar'])


def source_roots(loader, version, props, kind):
    base = [COMMON / 'src/main' / kind, ADAPTERS / loader / 'src/main' / kind]
    overlays = []
    family = props.get('sourceFamily')
    if family:
        overlays.append(COMMON / 'src/families' / family / kind)
    overlays += [COMMON / 'src/versions' / version / kind, ADAPTERS / loader / 'src/overlay' / kind]
    if family:
        overlays.append(ADAPTERS / loader / 'src/families' / family / kind)
    overlays.append(ADAPTERS / loader / 'src/versions' / version / kind)
    return base, overlays


def resolve_overlay(rel, base, overlays):
    for root in reversed(overlays):
        if (root / rel).is_file():
            return root / rel
    for root in base:
        if (root / rel).is_file():
            return root / rel
    return None


def label_for(path):
    rel = path.relative_to(ADAPTERS)
    parts = rel.parts
    origin = '/'.join(parts[:parts.index('java')]) if 'java' in parts else str(rel.parent)
    origin = origin.replace('mod-common/src/', '').replace('/src/', ':')
    return f'{path.name}' + ('' if origin in ('main',) else f' [{origin}]')


def mixin_sources(loader, version, props):
    rbase, roverlays = source_roots(loader, version, props, 'resources')
    jbase, joverlays = source_roots(loader, version, props, 'java')
    configs = sorted(p.name for p in (COMMON / 'src/main/resources').glob('*.mixins.json'))
    out, problems = [], []
    for config_name in configs:
        config_path = resolve_overlay(config_name, rbase, roverlays)
        with open(config_path, encoding='utf-8') as handle:
            config = json.load(handle)
        package = config.get('package', '')
        for key in ('mixins', 'server', 'client'):
            for name in config.get(key, []):
                rel = (package + '.' + name).replace('.', '/') + '.java'
                path = resolve_overlay(rel, jbase, joverlays)
                if path is None:
                    problems.append(f'{config_name} {name}: source missing ({rel})')
                else:
                    out.append(path)
    return out, problems



def parse_target(target):
    m = re.match(r'^(?:L([^;]+);)?([\w$<>]+)(.*)$', target)
    owner, name, desc = m.group(1), m.group(2), m.group(3)
    if desc.startswith(':'):
        desc = desc[1:]
    return owner, name, desc or None


SELECTOR = re.compile(r'^(?:L[^;]+;)?([\w$<>]+|\*)(\*|\+|\{\d*(?:,\d*)?\})?(\(.*)?$')


def parse_selector(selector):
    m = SELECTOR.match(selector)
    return (m.group(1), m.group(2), m.group(3)) if m else (selector, None, None)


def quantifier_max(quantifier):
    if quantifier in ('*', '+'):
        return None
    m = re.fullmatch(r'\{(\d*)(?:(,)(\d*))?\}', quantifier)
    if not m.group(2):
        return int(m.group(1)) if m.group(1) else None
    return int(m.group(3)) if m.group(3) else None


def select_methods(info, selector):
    """Mirrors Mixin target selection: a selector without a quantifier stops at the first declared match."""
    name, quantifier, desc = parse_selector(selector)
    if name == '*':
        candidates = [m for m in info.all_methods() if m.name not in ('<init>', '<clinit>')]
        return [m for m in candidates if desc is None or m.desc == desc]
    hits = [m for m in info.methods.get(name, []) if desc is None or m.desc == desc]
    limit = 1 if quantifier is None else quantifier_max(quantifier)
    return hits if limit is None else hits[:limit]


OPCODE_NAMES = {'GETFIELD': 'getfield', 'PUTFIELD': 'putfield', 'GETSTATIC': 'getstatic', 'PUTSTATIC': 'putstatic'}


def point_indexes(method, at, owner_class):
    """Returns (indexes of instructions the @At selects, None) or (None, reason) when unsupported."""
    value = at.string('value')
    insns = method.insns
    if value == 'HEAD':
        return ([0] if insns else []), None
    if value in ('RETURN', 'TAIL'):
        hits = [i for i, x in enumerate(insns) if x.op in RETURNS]
        if value == 'TAIL':
            hits = hits[-1:]
        ordinal = at.integer('ordinal')
        if ordinal is not None:
            hits = hits[ordinal:ordinal + 1]
        return hits, None
    if value not in ('INVOKE', 'INVOKE_ASSIGN', 'FIELD'):
        return None, f'@At("{value}")'
    target = at.string('target')
    if not target:
        return None, f'@At("{value}") without target'
    owner, name, desc = parse_target(target)
    opcode = at.attrs.get('opcode')
    opname = OPCODE_NAMES.get(opcode.rsplit('.', 1)[-1]) if opcode else None
    hits = []
    for i, insn in enumerate(insns):
        if value == 'FIELD':
            if insn.op not in FIELDS or (opname and insn.op != opname):
                continue
        elif insn.op not in INVOKES:
            continue
        if not insn.ref:
            continue
        rm = REF.match(insn.ref)
        if not rm:
            continue
        ref_owner = (rm.group(2) or owner_class.replace('.', '/')).strip('"')
        if owner and ref_owner != owner:
            continue
        if rm.group(3) != name or (desc and rm.group(4) != desc):
            continue
        hits.append(i)
    ordinal = at.integer('ordinal')
    if ordinal is not None:
        hits = hits[ordinal:ordinal + 1]
    return hits, None


def shifted_offset(method, index, at):
    shift = at.attrs.get('shift', '')
    delta = 0
    if shift.endswith('AFTER'):
        delta = 1
    elif shift.endswith('BEFORE'):
        delta = -1
    elif shift.endswith('BY'):
        delta = at.integer('by') or 0
    if at.string('value') == 'INVOKE_ASSIGN':
        delta += 1
        nxt = index + delta
        if nxt < len(method.insns) and re.match(r'^[ilfda]store', method.insns[nxt].op):
            delta += 1
    j = max(0, min(len(method.insns) - 1, index + delta))
    return method.insns[j].offset


def check_local(report, method, offsets, local_desc, opts, what):
    args_only = opts.get('argsOnly')
    ordinal, index, name = opts.get('ordinal'), opts.get('index'), opts.get('name')
    if args_only:
        names = {}
        if method.lvt is not None:
            for start, _length, slot, lname, _sig in method.lvt:
                if start == 0:
                    names[slot] = lname
        groups = [('args', [(slot, names.get(slot, '?')) for slot, d in method.params() if d == local_desc])]
    else:
        if method.lvt is None:
            report.warn(f'{what}: no LVT in {method.name}{method.desc}, @Local {local_desc} not checked')
            return
        groups = []
        for off in offsets:
            cover = sorted((slot, lname) for start, length, slot, lname, sig in method.lvt
                           if sig == local_desc and start <= off < start + length)
            groups.append((f'@{off}', cover))
    for where, matches in groups:
        listing = ', '.join(f'{n}#{s}' for s, n in matches) or 'none'
        count = len(matches)
        problem = None
        if count == 0:
            problem = 'no candidate'
        elif name is not None:
            if args_only and method.lvt is None:
                report.warn(f'{what}: no LVT in {method.name}{method.desc}, name="{name}" not checked')
            elif not any(n == name for _s, n in matches):
                problem = f'name "{name}" not among candidates'
        elif index is not None:
            if not any(s == index for s, _n in matches):
                problem = f'index {index} not among candidates'
        elif ordinal is not None:
            if count <= ordinal:
                problem = f'ordinal {ordinal} out of range'
        elif count != 1:
            problem = 'ambiguous without ordinal/index/name'
        detail = f'{local_desc} in {method.name}{method.desc} {where}: {count} match ({listing})'
        if problem:
            report.error(f'{what}: {problem}; {detail}')
        else:
            report.info(f'{what}: ok; {detail}')


class Report:
    def __init__(self, verbose):
        self.lines = []
        self.errors = 0
        self.unchecked = {}
        self.verbose = verbose
        self.prefix = ''
        self.lvt_methods = [0, 0]
        self.lvt_seen = set()

    def error(self, text):
        self.errors += 1
        self.lines.append(f'{self.prefix}{text}')

    def warn(self, text):
        self.lines.append(f'{self.prefix}warning: {text}')

    def info(self, text):
        if self.verbose:
            self.lines.append(f'{self.prefix}info: {text}')

    def skip(self, key, where):
        self.unchecked.setdefault(key, []).append(where)


def handler_ref_ok(src, member, ann, info, report, cls):
    selectors = ann.strings('method')
    if not selectors:
        report.error(f'{member.name}: @{ann.simple} without method selector')
        return
    ats = ann.nested('at')
    if not ats:
        report.error(f'{member.name}: @{ann.simple} without @At')
        return
    total = 0
    points = []
    mismatched = []
    for selector in selectors:
        name, quantifier, desc = parse_selector(selector)
        overloads = info.methods.get(name, []) if name != '*' else []
        if quantifier is None and desc is None and len(overloads) > 1:
            listing = ', '.join(m.desc for m in overloads)
            report.warn(f'{member.name}: method="{selector}" binds only the first of {len(overloads)} overloads '
                        f'({overloads[0].desc}); others: {listing}')
    for at in ats:
        for selector in selectors:
            for method in select_methods(info, selector):
                if ann.simple == 'Inject':
                    void = method.desc.endswith(')V')
                    cir = any(strip_generics(p.type).endswith('CallbackInfoReturnable') for p in member.params)
                    if void == cir and method not in mismatched:
                        mismatched.append(method)
                if not method.has_code:
                    continue
                hits, reason = point_indexes(method, at, cls)
                if hits is None:
                    report.skip(reason, f'{src.label} {member.name}')
                    total += 1
                    continue
                total += len(hits)
                if hits:
                    points.append((method, [shifted_offset(method, i, at) for i in hits], hits))
                if ann.simple in ('WrapOperation', 'Redirect') and hits and at.string('value') == 'INVOKE':
                    check_wrapped_call(src, member, ann, method, hits, report)
                if ann.simple == 'ModifyArg' and hits:
                    arg_index = ann.integer('index')
                    rm = REF.match(method.insns[hits[0]].ref or '')
                    if arg_index is not None and rm:
                        count = len(re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)', rm.group(4)[1:rm.group(4).index(')')]))
                        if arg_index >= count:
                            report.error(f'{member.name}: @ModifyArg index {arg_index} >= {count} args of {rm.group(3)}')
    if mismatched:
        listing = ', '.join(f'{m.name}{m.desc}' for m in mismatched[:3]) + (' ...' if len(mismatched) > 3 else '')
        report.error(f'{member.name}: callback type mismatch in {len(mismatched)} method(s): {listing}')
    if total < 1 and ann.integer('require') == 0:
        return
    if total < 1:
        tgts = [at.string('target') for at in ats]
        ords = [at.integer('ordinal') for at in ats]
        values = [at.string('value') for at in ats]
        report.error(f'{member.name}: NO MATCH method={selectors} at={values} target={tgts} ordinal={ords}')
        return
    what_base = f'{member.name} -> {cls}'
    for method, offsets, _hits in points:
        for param in member.params:
            local = next((a for a in param.annotations if a.simple == 'Local'), None)
            if local is None:
                continue
            desc = src.descriptor(param.type, unwrap_refs=True)
            opts = {'argsOnly': local.boolean('argsOnly'), 'ordinal': local.integer('ordinal'),
                    'index': local.integer('index'), 'name': local.string('name')}
            check_local(report, method, offsets, desc, opts, f'{what_base} @Local {param.type} {param.name}')
        if ann.simple == 'ModifyVariable':
            desc = src.descriptor(member.type)
            opts = {'argsOnly': ann.boolean('argsOnly'), 'ordinal': ann.integer('ordinal'),
                    'index': ann.integer('index'), 'name': ann.string('name')}
            check_local(report, method, offsets, desc, opts, f'{what_base} @ModifyVariable {member.type}')


def check_wrapped_call(src, member, ann, method, hits, report):
    """The handler takes the receiver only when the patched call is not static, then the call's arguments."""
    params = [p for p in member.params if not any(a.simple in ('Local', 'Share', 'Cancellable') for a in p.annotations)]
    if ann.simple == 'WrapOperation' and params and strip_generics(params[-1].type).rsplit('.', 1)[-1] == 'Operation':
        params = params[:-1]
    for index in hits:
        insn = method.insns[index]
        rm = REF.match(insn.ref or '')
        if not rm:
            continue
        args = re.findall(r'\[*(?:[ZBCSIJFD]|L[^;]+;)', rm.group(4)[1:rm.group(4).index(')')])
        expected = ([] if insn.op == 'invokestatic' else ['L' + (rm.group(2) or '').strip('"') + ';']) + args
        found = [src.descriptor(p.type) for p in params]
        if len(found) != len(expected):
            kind = 'static' if insn.op == 'invokestatic' else 'instance'
            report.error(f'{member.name}: @{ann.simple} handler takes {len(found)} call parameter(s), the {kind} call '
                         f'{rm.group(3)} in {method.name}{method.desc} needs {len(expected)} ({"".join(expected)})')
            return
        for position, (have, need) in enumerate(zip(found, expected)):
            if have != need and not (have.startswith('L') and need.startswith('L')):
                report.error(f'{member.name}: @{ann.simple} parameter {position} is {have}, the call in {method.name} passes {need}')
                return


def accessor_name(member, prefixes):
    for prefix in prefixes:
        base = member.name.split('$')[-1]
        if base.startswith(prefix) and len(base) > len(prefix):
            rest = base[len(prefix):]
            return rest[0].lower() + rest[1:]
    return member.name.split('$')[-1]


def check_source(src, jar, report):
    report.prefix = f'  {src.label} '
    if not src.targets:
        report.error('no @Mixin target')
        return
    for ann in src.class_annotations:
        if ann.simple not in PASSIVE and (src.annotation_fqcn(ann) or '').startswith(MIXIN_PACKAGES):
            report.skip(f'@{ann.simple}', src.label)
    for cls in src.targets:
        info = jar.get(cls)
        if info is None:
            report.error(f'CLASS MISSING {cls}')
            continue
        if cls not in report.lvt_seen:
            report.lvt_seen.add(cls)
            for method in info.all_methods():
                if method.has_code:
                    report.lvt_methods[0] += 1
                    report.lvt_methods[1] += method.lvt is not None
        for member in src.members:
            names = {a.simple for a in member.annotations}
            for ann in member.annotations:
                fq = src.annotation_fqcn(ann) or ''
                if ann.simple in POINT_INJECTORS:
                    handler_ref_ok(src, member, ann, info, report, cls)
                elif ann.simple == 'Accessor':
                    field = ann.string('value') or accessor_name(member, ('get', 'set', 'is'))
                    if field not in info.fields:
                        report.error(f'{member.name}: accessor field missing {field}')
                elif ann.simple == 'Invoker':
                    target = ann.string('value') or accessor_name(member, ('call', 'invoke', 'new', 'create'))
                    if target not in info.methods:
                        report.error(f'{member.name}: invoker method missing {target}')
                    elif target == '<init>':
                        desc = '(' + ''.join(src.descriptor(p.type) for p in member.params) + ')V'
                        if not any(m.desc == desc for m in info.methods['<init>']):
                            report.error(f'{member.name}: invoker constructor missing {desc}')
                elif ann.simple == 'Shadow':
                    if member.is_method and member.name not in info.methods:
                        report.error(f'{member.name}: shadow method missing {member.name}')
                    elif not member.is_method and member.name not in info.fields:
                        report.error(f'{member.name}: shadow field missing {member.name}')
                elif ann.simple not in PASSIVE and fq.startswith(MIXIN_PACKAGES):
                    report.skip(f'@{ann.simple}', f'{src.label} {member.name}')
            for param in member.params:
                for ann in param.annotations:
                    fq = src.annotation_fqcn(ann) or ''
                    if ann.simple not in PASSIVE and fq.startswith(MIXIN_PACKAGES):
                        report.skip(f'@{ann.simple}', f'{src.label} {member.name}')
                    elif ann.simple == 'Local' and not (names & POINT_INJECTORS):
                        report.skip('@Local outside an injector', f'{src.label} {member.name}')


def run_target(loader, version, props, verbose, jar_override=None):
    report = Report(verbose)
    header = f'## {loader} {version}' + (' runtime' if jar_override else '')
    jar_path = jar_override or locate_jar(loader, version, props)
    if jar_path is None:
        report.error(f'  jar missing for {loader} {version}')
        return header, report
    header += f'  ({jar_path})'
    paths, problems = mixin_sources(loader, version, props)
    for problem in problems:
        report.error('  ' + problem)
    sources = []
    for path in paths:
        sources.append(MixinSource(path, label_for(path)))
    jar = Jar(jar_path)
    jar.preload([t for s in sources for t in s.targets])
    for src in sources:
        check_source(src, jar, report)
    report.prefix = '  '
    return header, report


def parse_args(argv):
    verbose, filters, extra = False, [], []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == '-v':
            verbose = True
        elif arg == '--jar' or arg.startswith('--jar='):
            spec = arg[len('--jar='):] if arg.startswith('--jar=') else argv[i + 1]
            i += 0 if arg.startswith('--jar=') else 1
            m = re.fullmatch(r'(\w+):([\w.\-]+)=(.+)', spec)
            if not m or m.group(1) not in LOADERS:
                raise SystemExit(f'bad --jar {spec}: expected loader:version=path')
            extra.append((m.group(1), m.group(2), [Path(part).expanduser().resolve() for part in m.group(3).split(':')]))
        else:
            filters.append(arg)
        i += 1
    return verbose, filters, extra


def main(argv):
    verbose, filters, extra = parse_args(argv)
    targets = load_targets()
    versions = sorted(targets, key=lambda v: [int(x) for x in re.findall(r'\d+', v)], reverse=True)
    jobs = [(loader, v, None) for loader in LOADERS for v in versions
            if (not filters and not extra) or any(f in (loader, f'{loader}:{v}', v) for f in filters)]
    for loader, version, path in extra:
        if version not in targets:
            raise SystemExit(f'--jar {loader}:{version}: version not in gradle/targets.json')
        missing = [str(part) for part in path if not part.is_file()]
        if missing:
            raise SystemExit(f'--jar {loader}:{version}: not found {", ".join(missing)}')
        jobs.append((loader, version, ':'.join(str(part) for part in path)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=min(9, len(jobs) or 1)) as pool:
        results = list(pool.map(lambda job: run_target(job[0], job[1], targets[job[1]], verbose, job[2]), jobs))
    total = 0
    summary = []
    for (loader, version, path), (header, report) in zip(jobs, results):
        print(header)
        for line in report.lines:
            print(line)
        for key, where in sorted(report.unchecked.items()):
            print(f'  unchecked {key}: {", ".join(sorted(set(where)))}')
        with_lvt, methods = report.lvt_methods[1], report.lvt_methods[0]
        if methods:
            print(f'  lvt {with_lvt}/{methods} target methods')
        print(f'  errors {report.errors}')
        total += report.errors
        summary.append(f'{loader} {version}' + (' runtime' if path else '') + f': {report.errors}')
    print('## total')
    for line in summary:
        print('  ' + line)
    print(f'errors {total}')
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
