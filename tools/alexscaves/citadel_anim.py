"""Evaluates the animation code of Alex's Caves Citadel models (decompiled Java) in Python.

The mod animates its mobs with code, not with keyframe files: every frame
``setupAnim`` resets the pose and then adds

* keyframed animations through Citadel's ``ModelAnimator`` (``animate(entity)``:
  setAnimation/startKeyframe/rotate/move/endKeyframe/setStaticKeyframe/resetKeyframe,
  eased with ``sin(progress * PI / 2)`` between keyframes, tied to the entity's
  ``Animation`` constants and their tick counter), and
* procedural math (walk/swing/flap/bob/chain*/progressRotationPrev/...,
  plus raw ``Math.sin``/``cos`` expressions) driven by ``limbSwing``,
  ``limbSwingAmount``, ``ageInTicks`` and the entity's progress getters
  (``getSitProgress(partialTick)`` and similar).

This module runs that code with a small interpreter for the Java subset the
models use, so the converter can sample the exact pose of every part at any
time for any entity state. Values flow as :class:`Dual` numbers (value and
derivative over time), which lets the sampler find the real periods of the
procedural loops. Anything the interpreter cannot know (world queries, other
entities, vectors) evaluates to a neutral :class:`Unknown` (0 / false / null) and
is reported once as a warning.
"""
import math
import os
import re

# ---------------------------------------------------------------------------------------------------------------------
# Numbers


class Dual:
    """A float with its derivative over the sampling time (forward-mode autodiff)."""

    __slots__ = ("v", "d")

    def __init__(self, v, d=0.0):
        self.v = float(v)
        self.d = float(d)

    def __repr__(self):
        return f"Dual({self.v}, {self.d})"


def is_num(x):
    return isinstance(x, (int, float, Dual)) and not isinstance(x, bool)


def val(x):
    if isinstance(x, Dual):
        return x.v
    if isinstance(x, bool):
        return 1.0 if x else 0.0
    if isinstance(x, (int, float)):
        return x
    return 0


def der(x):
    return x.d if isinstance(x, Dual) else 0.0


def lift(x):
    return x if isinstance(x, Dual) else Dual(val(x), 0.0)


def d_add(a, b):
    if isinstance(a, Dual) or isinstance(b, Dual):
        return Dual(val(a) + val(b), der(a) + der(b))
    return val(a) + val(b)


def d_sub(a, b):
    if isinstance(a, Dual) or isinstance(b, Dual):
        return Dual(val(a) - val(b), der(a) - der(b))
    return val(a) - val(b)


def d_mul(a, b):
    if isinstance(a, Dual) or isinstance(b, Dual):
        return Dual(val(a) * val(b), der(a) * val(b) + val(a) * der(b))
    return val(a) * val(b)


def d_div(a, b):
    bv = val(b)
    if isinstance(a, int) and isinstance(b, int) and not isinstance(a, bool):
        if b == 0:
            return 0
        q = abs(a) // abs(b)
        return q if (a >= 0) == (b >= 0) else -q
    if bv == 0:
        return math.copysign(math.inf, val(a)) if val(a) != 0 else 0.0
    if isinstance(a, Dual) or isinstance(b, Dual):
        return Dual(val(a) / bv, (der(a) * bv - val(a) * der(b)) / (bv * bv))
    return val(a) / bv


def d_neg(a):
    return Dual(-a.v, -a.d) if isinstance(a, Dual) else -val(a)


class Unknown:
    """Something the interpreter cannot evaluate: numeric 0, boolean false, equal to null."""

    def __init__(self, what):
        self.what = what

    def __repr__(self):
        return f"Unknown({self.what})"


# ---------------------------------------------------------------------------------------------------------------------
# Tokenizer and parser (Java subset)

TOKEN = re.compile(r"""
    (?P<ws>\s+|//[^\n]*|/\*.*?\*/)
  | (?P<num>(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?[fFdDlL]?)
  | (?P<str>"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')
  | (?P<id>[A-Za-z_$][A-Za-z0-9_$]*)
  | (?P<op>>>>=|<<=|>>=|>>>|->|::|\+\+|--|&&|\|\||[=!<>+\-*/%&|^]=|<<|>>|[{}()\[\];,.?:=<>+\-*/%!~&|^@])
""", re.S | re.X)


def tokenize(text):
    out = []
    pos = 0
    while pos < len(text):
        m = TOKEN.match(text, pos)
        if not m:
            raise SyntaxError("unexpected character %r at %d" % (text[pos], pos))
        pos = m.end()
        kind = m.lastgroup
        if kind == "ws":
            continue
        out.append((kind, m.group()))
    out.append(("eof", ""))
    return out


PRIMITIVES = {"float", "double", "int", "long", "boolean", "short", "byte", "char", "var"}


class Parser:
    def __init__(self, tokens):
        self.t = tokens
        self.i = 0

    def peek(self, k=0):
        return self.t[min(self.i + k, len(self.t) - 1)]

    def at(self, value, k=0):
        tok = self.peek(k)
        return tok[0] in ("op", "id") and tok[1] == value

    def next(self):
        tok = self.t[self.i]
        self.i += 1
        return tok

    def expect(self, value):
        tok = self.next()
        if tok[1] != value:
            raise SyntaxError(f"expected {value!r}, got {tok[1]!r} near token {self.i}")
        return tok

    # --- types ---------------------------------------------------------------------------------------------------

    def try_type(self):
        """Parses a type at the cursor if one is there followed by an identifier; returns the type or None."""
        start = self.i
        if self.peek()[0] != "id":
            return None
        name = self.next()[1]
        while self.at(".") and self.peek(1)[0] == "id":
            self.next()
            name += "." + self.next()[1]
        if self.at("<"):
            depth = 0
            while True:
                tok = self.next()
                if tok[1] == "<":
                    depth += 1
                elif tok[1] == ">":
                    depth -= 1
                elif tok[1] == ">>":
                    depth -= 2
                elif tok[0] == "eof" or tok[1] in (";", "{", "(", ")", "="):
                    self.i = start
                    return None
                if depth <= 0:
                    break
        while self.at("[") and self.at("]", 1):
            self.next()
            self.next()
            name += "[]"
        if self.peek()[0] == "id" and self.peek()[1] not in ("instanceof",):
            return name
        self.i = start
        return None

    # --- statements -------------------------------------------------------------------------------------------------

    def block(self):
        self.expect("{")
        body = []
        while not self.at("}"):
            body.append(self.statement())
        self.expect("}")
        return ("block", body)

    def statement(self):
        tok = self.peek()
        if tok[1] == "{":
            return self.block()
        if tok[1] == ";":
            self.next()
            return ("block", [])
        if tok[0] == "id":
            word = tok[1]
            if word == "if":
                self.next()
                self.expect("(")
                cond = self.expr()
                self.expect(")")
                then = self.statement()
                other = None
                if self.at("else"):
                    self.next()
                    other = self.statement()
                return ("if", cond, then, other)
            if word == "return":
                self.next()
                value = None if self.at(";") else self.expr()
                self.expect(";")
                return ("return", value)
            if word == "break":
                self.next()
                self.expect(";")
                return ("break",)
            if word == "continue":
                self.next()
                self.expect(";")
                return ("continue",)
            if word == "switch":
                return self.switch()
            if word == "for":
                return self.for_loop()
            if word == "while":
                self.next()
                self.expect("(")
                cond = self.expr()
                self.expect(")")
                return ("while", cond, self.statement())
            if word == "throw":
                while not self.at(";"):
                    self.next()
                self.next()
                return ("block", [])
            if word == "final":
                self.next()
                return self.statement()
            decl = self.local_decl()
            if decl is not None:
                self.expect(";")
                return decl
        value = self.expr()
        self.expect(";")
        return ("expr", value)

    def local_decl(self):
        start = self.i
        type_name = self.try_type()
        if type_name is None:
            return None
        if not (self.peek()[0] == "id" and self.peek(1)[1] in ("=", ";", ",")):
            self.i = start
            return None
        decls = []
        while True:
            name = self.next()[1]
            init = None
            if self.at("="):
                self.next()
                init = self.array_init() if self.at("{") else self.expr()
            decls.append((name, init))
            if self.at(","):
                self.next()
                continue
            break
        return ("decl", type_name, decls)

    def array_init(self):
        self.expect("{")
        items = []
        while not self.at("}"):
            items.append(self.array_init() if self.at("{") else self.expr())
            if self.at(","):
                self.next()
        self.expect("}")
        return ("array", items)

    def switch(self):
        self.next()
        self.expect("(")
        subject = self.expr()
        self.expect(")")
        self.expect("{")
        cases = []  # (labels or None for default, [statements])
        while not self.at("}"):
            labels = []
            while self.at("case") or self.at("default"):
                if self.next()[1] == "default":
                    labels.append(None)
                else:
                    labels.append(self.expr())
                    while self.at(","):
                        self.next()
                        labels.append(self.expr())
                if self.at("->"):
                    self.next()
                    body = [self.statement(), ("break",)]
                    cases.append((labels, body))
                    labels = []
                    break
                self.expect(":")
            if not labels:
                continue
            body = []
            while not (self.at("case") or self.at("default") or self.at("}")):
                body.append(self.statement())
            cases.append((labels, body))
        self.expect("}")
        return ("switch", subject, cases)

    def for_loop(self):
        self.next()
        self.expect("(")
        start = self.i
        type_name = self.try_type()
        if type_name is not None and self.peek()[0] == "id" and self.at(":", 1):
            var = self.next()[1]
            self.expect(":")
            iterable = self.expr()
            self.expect(")")
            return ("foreach", var, iterable, self.statement())
        self.i = start
        init = None
        if not self.at(";"):
            init = self.local_decl() or ("expr", self.expr())
        self.expect(";")
        cond = None if self.at(";") else self.expr()
        self.expect(";")
        updates = []
        while not self.at(")"):
            updates.append(self.expr())
            if self.at(","):
                self.next()
        self.expect(")")
        return ("for", init, cond, updates, self.statement())

    # --- expressions --------------------------------------------------------------------------------------------------

    ASSIGN_OPS = {"=", "+=", "-=", "*=", "/=", "%="}

    def expr(self):
        left = self.ternary()
        if self.peek()[0] == "op" and self.peek()[1] in self.ASSIGN_OPS:
            op = self.next()[1]
            right = self.expr()
            return ("assign", op, left, right)
        return left

    def ternary(self):
        cond = self.binary(0)
        if self.at("?"):
            self.next()
            a = self.expr()
            self.expect(":")
            b = self.ternary()
            return ("cond", cond, a, b)
        return cond

    LEVELS = [["||"], ["&&"], ["|"], ["^"], ["&"], ["==", "!="], ["<", ">", "<=", ">=", "instanceof"],
              ["<<", ">>", ">>>"], ["+", "-"], ["*", "/", "%"]]

    def binary(self, level):
        if level == len(self.LEVELS):
            return self.unary()
        left = self.binary(level + 1)
        while self.peek()[1] in self.LEVELS[level] and self.peek()[0] in ("op", "id"):
            op = self.next()[1]
            if op == "instanceof":
                self.try_type() or self.next()
                if self.peek()[0] == "id":
                    self.next()
                left = ("const", Unknown("instanceof"))
                continue
            right = self.binary(level + 1)
            left = ("bin", op, left, right)
        return left

    def unary(self):
        tok = self.peek()
        if tok[0] == "op":
            if tok[1] in ("-", "+", "!", "~"):
                self.next()
                return ("unary", tok[1], self.unary())
            if tok[1] in ("++", "--"):
                self.next()
                target = self.unary()
                return ("assign", "+=" if tok[1] == "++" else "-=", target, ("const", 1))
            if tok[1] == "(":
                cast = self.try_cast()
                if cast is not None:
                    return ("cast", cast, self.unary())
        return self.postfix(self.primary())

    def try_cast(self):
        start = self.i
        self.next()
        if self.peek()[0] == "id":
            name = self.next()[1]
            while self.at(".") and self.peek(1)[0] == "id":
                self.next()
                name += "." + self.next()[1]
            if self.at("<"):
                depth = 0
                while True:
                    tok = self.next()
                    depth += tok[1].count("<") - tok[1].count(">")
                    if depth <= 0 or tok[0] == "eof":
                        break
            while self.at("[") and self.at("]", 1):
                self.next()
                self.next()
            if self.at(")"):
                after = self.peek(1)
                primitive = name in PRIMITIVES
                if primitive or (name[:1].isupper() and (after[0] in ("id", "num", "str") or after[1] in ("(", "!"))):
                    self.next()
                    return name
        self.i = start
        return None

    def primary(self):
        tok = self.next()
        kind, text = tok
        if kind == "num":
            if re.search(r"[fFdD]$", text) or "." in text or "e" in text.lower():
                return ("const", float(text.rstrip("fFdD")))
            return ("const", int(text.rstrip("lL")))
        if kind == "str":
            return ("const", text[1:-1])
        if text == "(":
            inner = self.expr()
            self.expect(")")
            return inner
        if kind == "id":
            if text == "true":
                return ("const", True)
            if text == "false":
                return ("const", False)
            if text == "null":
                return ("const", None)
            if text == "new":
                return self.new_expr()
            if self.at("->"):
                self.next()
                self.lambda_body()
                return ("const", Unknown("lambda"))
            return ("name", text)
        raise SyntaxError(f"unexpected token {text!r}")

    def lambda_body(self):
        if self.at("{"):
            self.block()
        else:
            self.expr()

    def new_expr(self):
        name = self.next()[1]
        while self.at("."):
            self.next()
            name += "." + self.next()[1]
        if self.at("<"):
            depth = 0
            while True:
                tok = self.next()
                depth += tok[1].count("<") - tok[1].count(">")
                if depth <= 0:
                    break
        if self.at("["):
            sizes = []
            while self.at("["):
                self.next()
                sizes.append(None if self.at("]") else self.expr())
                self.expect("]")
            if self.at("{"):
                return self.array_init()
            return ("newarray", name, sizes)
        self.expect("(")
        args = self.args_until(")")
        if self.at("{"):  # anonymous class
            depth = 0
            while True:
                tok = self.next()
                depth += (tok[1] == "{") - (tok[1] == "}")
                if depth == 0:
                    break
        return ("new", name, args)

    def args_until(self, close):
        args = []
        while not self.at(close):
            args.append(self.expr())
            if self.at(","):
                self.next()
        self.expect(close)
        return args

    def postfix(self, node):
        while True:
            if self.at("."):
                self.next()
                if self.at("<"):
                    while not self.at(">"):
                        self.next()
                    self.next()
                name = self.next()[1]
                if self.at("("):
                    self.next()
                    node = ("call", node, name, self.args_until(")"))
                else:
                    node = ("field", node, name)
            elif self.at("(") and node[0] == "name":
                self.next()
                node = ("call", None, node[1], self.args_until(")"))
            elif self.at("["):
                self.next()
                index = self.expr()
                self.expect("]")
                node = ("index", node, index)
            elif self.at("++") or self.at("--"):
                op = self.next()[1]
                node = ("postinc", op, node)
            elif self.at("::"):
                self.next()
                self.next()
                node = ("const", Unknown("method reference"))
            else:
                return node


# ---------------------------------------------------------------------------------------------------------------------
# Class model

METHOD_HEADER = re.compile(
    r"(?:(?:public|private|protected|static|final|synchronized|abstract)\s+)*(?:<[^>]*>\s+)?"
    r"[\w.<>\[\], ?]+?\s+(\w+)\s*\(([^)]*)\)\s*(?:throws [\w., ]+)?\{")


def split_class_body(source):
    """Returns (class_name, superclass, methods {name: [(params, body_text)]}, field_declarations_text)."""
    m = re.search(r"public (?:abstract )?class (\w+)(?:<[^{]*?>)?(?: extends (\w+))?", source)
    class_name, superclass = m.group(1), m.group(2)
    start = source.index("{", m.end()) + 1
    methods = {}
    fields = []
    i = start
    depth = 0
    member_start = i
    while i < len(source):
        c = source[i]
        if c == '"':
            j = i + 1
            while source[j] != '"':
                j += 2 if source[j] == "\\" else 1
            i = j + 1
            continue
        if c == "{":
            if depth == 0:
                header = source[member_start:i + 1]
                hm = METHOD_HEADER.search(header.strip() if header.strip().endswith("{") else header)
                body_start = i
                k = i
                d = 0
                while True:
                    if source[k] == "{":
                        d += 1
                    elif source[k] == "}":
                        d -= 1
                        if d == 0:
                            break
                    k += 1
                body = source[body_start:k + 1]
                if hm and "=" not in header.split("(")[0]:
                    params = [p.strip().split()[-1] for p in hm.group(2).split(",") if p.strip()]
                    name = hm.group(1)
                    if name == class_name:
                        name = "<init>"
                    methods.setdefault(name, []).append((params, body))
                else:
                    fields.append(header + body[1:])
                i = k + 1
                member_start = i
                continue
        elif c == ";" and depth == 0:
            fields.append(source[member_start:i + 1])
            member_start = i + 1
        elif c == "}" and depth == 0:
            break
        i += 1
    return class_name, superclass, methods, fields


# ---------------------------------------------------------------------------------------------------------------------
# Runtime objects


class PartState:
    """Mutable pose of one AdvancedModelBox (radians and model pixels, Java model space)."""

    __slots__ = ("name", "rx", "ry", "rz", "px", "py", "pz", "sx", "sy", "sz", "default", "show", "hideable")

    def __init__(self, name, pivot, rotation):
        self.name = name
        self.hideable = False
        self.default = (rotation[0], rotation[1], rotation[2], pivot[0], pivot[1], pivot[2], 1.0, 1.0, 1.0)
        self.reset()

    def reset(self):
        (self.rx, self.ry, self.rz, self.px, self.py, self.pz, self.sx, self.sy, self.sz) = self.default
        self.show = True

    def snapshot(self):
        """The pose; a hidden part (showModel = false, which also hides its children) becomes scale 0. Parts that
        hide only their own cubes (HideableModelBoxWithChildren) cannot be expressed per bone and stay visible."""
        pose = tuple(val(v) for v in (self.rx, self.ry, self.rz, self.px, self.py, self.pz, self.sx, self.sy, self.sz))
        if not self.show and not self.hideable:
            return pose[:6] + (0.0, 0.0, 0.0)
        return pose


PART_FIELDS = {"rotateAngleX": "rx", "rotateAngleY": "ry", "rotateAngleZ": "rz", "rotationPointX": "px",
               "rotationPointY": "py", "rotationPointZ": "pz", "scaleX": "sx", "scaleY": "sy", "scaleZ": "sz",
               "f_104203_": "rx", "f_104204_": "ry", "f_104205_": "rz", "f_104200_": "px", "f_104201_": "py",
               "f_104202_": "pz", "xRot": "rx", "yRot": "ry", "zRot": "rz", "x": "px", "y": "py", "z": "pz"}
PART_DEFAULTS = {"defaultRotationX": 0, "defaultRotationY": 1, "defaultRotationZ": 2, "defaultPositionX": 3,
                 "defaultPositionY": 4, "defaultPositionZ": 5}


class Animator:
    """Citadel ModelAnimator, verbatim semantics (citadel 2.6.3)."""

    def __init__(self, runtime):
        self.rt = runtime
        self.temp = 0
        self.prev_temp = 0
        self.correct = False
        self.transforms = {}
        self.prev_transforms = {}

    def update(self):
        self.temp = self.prev_temp = 0
        self.correct = False
        self.transforms = {}
        self.prev_transforms = {}

    def set_animation(self, anim):
        self.temp = self.prev_temp = 0
        self.rt.seen_animations.add(anim)
        self.correct = self.rt.ctx.animation == anim
        return self.correct

    def start(self, duration):
        if self.correct:
            self.prev_temp = self.temp
            self.temp += int(val(duration))

    def add(self, part, kind, x, y, z):
        if not self.correct:
            return
        t = self.transforms.setdefault(part, [0.0] * 6)
        o = 0 if kind == "rot" else 3
        t[o] += val(x)
        t[o + 1] += val(y)
        t[o + 2] += val(z)

    def end(self, stationary=False):
        if not self.correct:
            return
        tick = self.rt.ctx.animation_tick
        if self.prev_temp <= tick < self.temp:
            if stationary:
                for part, t in self.prev_transforms.items():
                    apply_transform(part, t, 1.0)
            else:
                progress = (tick - self.prev_temp + self.rt.ctx.partial) / (self.temp - self.prev_temp)
                inc = math.sin(progress * math.pi / 2.0)
                dec = 1.0 - inc
                for part, t in self.prev_transforms.items():
                    apply_transform(part, t, dec)
                for part, t in self.transforms.items():
                    apply_transform(part, t, inc)
        if not stationary:
            self.prev_transforms = dict(self.transforms)
            self.transforms = {}
        self.prev_temp = self.temp


def apply_transform(part, t, k):
    part.rx = d_add(part.rx, k * t[0])
    part.ry = d_add(part.ry, k * t[1])
    part.rz = d_add(part.rz, k * t[2])
    part.px = d_add(part.px, k * t[3])
    part.py = d_add(part.py, k * t[4])
    part.pz = d_add(part.pz, k * t[5])


class AnimRef:
    """An entity Animation constant (Citadel Animation.create(duration))."""

    def __init__(self, owner, name):
        self.owner = owner
        self.name = name

    def __eq__(self, other):
        return isinstance(other, AnimRef) and other.name == self.name

    def __hash__(self):
        return hash(self.name)

    def __repr__(self):
        return self.name


NO_ANIMATION = AnimRef("IAnimatedEntity", "NO_ANIMATION")


class Context:
    """The inputs of one setupAnim call."""

    def __init__(self, time, limb_rate=0.0, limb_amount=0.0, animation=NO_ANIMATION, animation_tick=0,
                 states=None):
        self.time = time
        self.age = Dual(time, 1.0)
        self.limb_swing = Dual(time * limb_rate, limb_rate)
        self.limb_amount = limb_amount
        self.animation = animation
        self.animation_tick = animation_tick
        self.partial = time - math.floor(time) if animation == NO_ANIMATION else 0.0
        self.states = states or {}


class EntityProxy:
    pass


class StaticRef:
    def __init__(self, name):
        self.name = name


class ReturnSignal(Exception):
    def __init__(self, value):
        self.value = value


class BreakSignal(Exception):
    pass


class ContinueSignal(Exception):
    pass


# Entity getters that are known booleans with a sensible default for a mob standing in its habitat.
ENTITY_DEFAULTS = {
    "m_6084_": True,      # isAlive
    "m_20096_": True,     # onGround
    "hasBothLegs": True,
    "isFormed": True,
    "m_6134_": 1.0,       # getScale
}
# Obfuscated vanilla getters that are animation states worth exposing under a readable name.
ENTITY_ALIASES = {"m_20998_": "getSwimAmount", "m_20160_": "isVehicle", "m_6254_": "isSaddled",
                  "m_20069_": "isInWater", "m_20072_": "isInWaterOrBubble", "m_6162_": "isBaby",
                  "m_21825_": "isOrderedToSit", "m_20159_": "isPassenger", "m_21224_": "isDeadOrDying"}


class Runtime:
    """Interprets one model class (and its Citadel model superclass)."""

    def __init__(self, source_dir, class_name, parts):
        self.source_dir = source_dir
        self.class_name = class_name
        self.methods = {}
        self.super_methods = {}
        self.fields = {}
        self.animator_fields = set()
        self.parts = {name: PartState(name, p.pivot, p.rotation) for name, p in parts.items()}
        self.warnings = []
        self._warned = set()
        self.seen_animations = set()
        self.compared_animations = set()
        self.state_calls = {}
        self.frequencies = []
        self.entity_class = None
        self.load_class(class_name)
        with open(self.model_path(class_name), encoding="utf-8") as handle:
            generic = re.search(r"class \w+ extends \w+<(\w+Entity)>", handle.read())
        if generic:
            self.entity_class = generic.group(1)
        self.animator = Animator(self)
        self.ctx = None
        self.movement_scale = 1.0

    # --- loading ----------------------------------------------------------------------------------------------------

    def model_path(self, name):
        return os.path.join(self.source_dir, "com", "github", "alexmodguy", "alexscaves", "client", "model",
                            name + ".java")

    def load_class(self, name):
        with open(self.model_path(name), encoding="utf-8") as handle:
            source = handle.read()
        cls, superclass, methods, fields = split_class_body(source)
        for hm in re.finditer(r"this\.(\w+) = new HideableModelBoxWithChildren\(", source):
            if hm.group(1) in self.parts:
                self.parts[hm.group(1)].hideable = True
        if superclass and os.path.exists(self.model_path(superclass)):
            self.load_class(superclass)
        m = re.search(r"setupAnim\((\w+) entity", source)
        if m and self.entity_class is None and m.group(1) not in ("T", "Entity", "LivingEntity"):
            self.entity_class = m.group(1)
        for method, overloads in methods.items():
            for params, body in overloads:
                parsed = Parser(tokenize(body)).block()
                existing = self.methods.setdefault(method, [])
                overridden = [o for o in existing if len(o[0]) == len(params)]
                if overridden:
                    self.super_methods.setdefault(method, []).extend(overridden)
                existing[:] = [o for o in existing if len(o[0]) != len(params)]
                existing.append((params, parsed))
        for decl in fields:
            for fm in re.finditer(r"ModelAnimator (\w+)", decl):
                self.animator_fields.add(fm.group(1))
            fm = re.search(r"(?:boolean|float|int|double) (\w+) = ([^;]+);", decl)
            if fm:
                try:
                    node = Parser(tokenize(fm.group(2))).expr()
                    self.fields[fm.group(1)] = self.eval(node, {}) if node[0] == "const" else 0.0
                except SyntaxError:
                    pass

    def warn(self, message):
        if message not in self._warned:
            self._warned.add(message)
            self.warnings.append(message)

    # --- running ----------------------------------------------------------------------------------------------------

    def run(self, ctx):
        """Runs setupAnim for one context and returns {part: (rx, ry, rz, px, py, pz, sx, sy, sz)}."""
        self.ctx = ctx
        for part in self.parts.values():
            part.reset()
        self.movement_scale = 1.0
        entity = EntityProxy()
        args = [entity, ctx.limb_swing, ctx.limb_amount, ctx.age, 0.0, 0.0]
        overloads = self.methods.get("setupAnim") or self.methods.get("m_6973_")
        if not overloads:
            raise ValueError(self.class_name + ": no setupAnim")
        self.call_user(overloads, args)
        return {name: part.snapshot() for name, part in self.parts.items()}

    def call_user(self, overloads, args):
        for params, body in overloads:
            if len(params) == len(args):
                scope = dict(zip(params, args))
                try:
                    self.exec(body, scope)
                except ReturnSignal as signal:
                    return signal.value
                return None
        self.warn(f"no overload with {len(args)} arguments")
        return Unknown("call")

    # --- statements -------------------------------------------------------------------------------------------------

    def exec(self, node, scope):
        kind = node[0]
        if kind == "block":
            for statement in node[1]:
                self.exec(statement, scope)
        elif kind == "expr":
            self.eval(node[1], scope)
        elif kind == "decl":
            for name, init in node[2]:
                value = self.eval(init, scope) if init is not None else 0
                if node[1] in ("int", "long", "short") and is_num(value):
                    value = int(val(value))
                scope[name] = value
        elif kind == "if":
            if self.truth(self.eval(node[1], scope)):
                self.exec(node[2], scope)
            elif node[3] is not None:
                self.exec(node[3], scope)
        elif kind == "return":
            raise ReturnSignal(self.eval(node[1], scope) if node[1] is not None else None)
        elif kind == "break":
            raise BreakSignal()
        elif kind == "continue":
            raise ContinueSignal()
        elif kind == "switch":
            subject = self.eval(node[1], scope)
            matched = False
            try:
                for labels, body in node[2]:
                    if not matched:
                        for label in labels:
                            if label is None or self.equals(self.eval(label, scope), subject):
                                matched = True
                    if matched:
                        for statement in body:
                            self.exec(statement, scope)
                if not matched:
                    for labels, body in node[2]:
                        if None in labels:
                            for statement in body:
                                self.exec(statement, scope)
            except BreakSignal:
                pass
        elif kind == "for":
            if node[1] is not None:
                self.exec(node[1], scope)
            guard = 0
            while node[2] is None or self.truth(self.eval(node[2], scope)):
                guard += 1
                if guard > 1000:
                    self.warn("loop cut after 1000 iterations")
                    break
                try:
                    self.exec(node[4], scope)
                except BreakSignal:
                    break
                except ContinueSignal:
                    pass
                for update in node[3]:
                    self.eval(update, scope)
        elif kind == "foreach":
            iterable = self.eval(node[2], scope)
            if isinstance(iterable, list):
                for item in iterable:
                    scope[node[1]] = item
                    try:
                        self.exec(node[3], scope)
                    except BreakSignal:
                        break
                    except ContinueSignal:
                        pass
            else:
                self.warn("for-each over something unknown skipped")
        elif kind == "while":
            self.warn("while loop skipped")
        else:
            raise ValueError("unknown statement " + kind)

    def truth(self, value):
        if isinstance(value, Unknown) or value is None:
            return False
        if isinstance(value, bool):
            return value
        return val(value) != 0

    def equals(self, a, b):
        if isinstance(a, Unknown) or isinstance(b, Unknown):
            return (a is None or isinstance(a, Unknown)) and (b is None or isinstance(b, Unknown))
        if isinstance(a, AnimRef) or isinstance(b, AnimRef):
            for x in (a, b):
                if isinstance(x, AnimRef) and x != NO_ANIMATION:
                    self.compared_animations.add(x)
            return a == b
        if is_num(a) and is_num(b):
            return val(a) == val(b)
        return a is b or a == b

    # --- expressions --------------------------------------------------------------------------------------------------

    def num(self, value):
        if isinstance(value, Unknown) or value is None:
            return 0.0
        if isinstance(value, bool):
            return 1.0 if value else 0.0
        if is_num(value):
            return value
        if isinstance(value, StaticRef):
            self.warn("unknown value " + value.name)
        return 0.0

    def eval(self, node, scope):
        kind = node[0]
        if kind == "const":
            return node[1]
        if kind == "name":
            return self.lookup(node[1], scope)
        if kind == "bin":
            op = node[1]
            if op == "&&":
                return self.truth(self.eval(node[2], scope)) and self.truth(self.eval(node[3], scope))
            if op == "||":
                return self.truth(self.eval(node[2], scope)) or self.truth(self.eval(node[3], scope))
            a = self.eval(node[2], scope)
            b = self.eval(node[3], scope)
            if op == "==":
                return self.equals(a, b)
            if op == "!=":
                return not self.equals(a, b)
            if op in ("&", "|", "^") and (isinstance(a, bool) or isinstance(b, bool)):
                a, b = self.truth(a), self.truth(b)
                return {"&": a and b, "|": a or b, "^": a != b}[op]
            a, b = self.num(a), self.num(b)
            if op == "+":
                return d_add(a, b)
            if op == "-":
                return d_sub(a, b)
            if op == "*":
                return d_mul(a, b)
            if op == "/":
                return d_div(a, b)
            if op == "%":
                if val(b) == 0:
                    return 0.0
                r = math.fmod(val(a), val(b))
                return Dual(r, der(a)) if isinstance(a, Dual) else (int(r) if isinstance(a, int) and isinstance(b, int) else r)
            if op == "<":
                return val(a) < val(b)
            if op == ">":
                return val(a) > val(b)
            if op == "<=":
                return val(a) <= val(b)
            if op == ">=":
                return val(a) >= val(b)
            if op in ("&", "|", "^", "<<", ">>", ">>>"):
                x, y = int(val(a)), int(val(b))
                return {"&": x & y, "|": x | y, "^": x ^ y, "<<": x << y, ">>": x >> y, ">>>": x >> y}[op]
            raise ValueError(op)
        if kind == "unary":
            value = self.eval(node[2], scope)
            if node[1] == "!":
                return not self.truth(value)
            if node[1] == "-":
                return d_neg(self.num(value))
            if node[1] == "~":
                return ~int(val(value))
            return value
        if kind == "cond":
            return self.eval(node[2] if self.truth(self.eval(node[1], scope)) else node[3], scope)
        if kind == "cast":
            value = self.eval(node[2], scope)
            if node[1] in ("int", "long", "short", "byte"):
                return int(val(self.num(value)))
            if node[1] in ("float", "double"):
                value = self.num(value)
                return value if isinstance(value, Dual) else float(val(value))
            return value
        if kind == "assign":
            return self.assign(node, scope)
        if kind == "postinc":
            old = self.eval(node[2], scope)
            self.assign(("assign", "+=" if node[1] == "++" else "-=", node[2], ("const", 1)), scope)
            return old
        if kind == "field":
            return self.get_field(self.eval(node[1], scope), node[2], node)
        if kind == "index":
            target = self.eval(node[1], scope)
            index = int(val(self.num(self.eval(node[2], scope))))
            if isinstance(target, list) and 0 <= index < len(target):
                return target[index]
            return Unknown("index")
        if kind == "array":
            return [self.eval(item, scope) for item in node[1]]
        if kind == "newarray":
            size = node[2][0]
            n = int(val(self.num(self.eval(size, scope)))) if size is not None else 0
            return [0.0 if node[1] in PRIMITIVES else None for _ in range(max(0, min(n, 1000)))]
        if kind == "new":
            for arg in node[2]:
                self.eval(arg, scope)
            return Unknown("new " + node[1])
        if kind == "call":
            return self.call(node, scope)
        raise ValueError("unknown expression " + kind)

    def lookup(self, name, scope):
        if name in scope:
            return scope[name]
        if name == "this":
            return self
        if name in self.parts:
            return self.parts[name]
        if name in self.animator_fields:
            return self.animator
        if name in self.fields:
            return self.fields[name]
        return StaticRef(name)

    def get_field(self, target, name, node):
        if target is self:
            return self.lookup(name, {})
        if isinstance(target, PartState):
            if name in PART_FIELDS:
                return getattr(target, PART_FIELDS[name])
            if name in PART_DEFAULTS:
                return target.default[PART_DEFAULTS[name]]
            if name == "showModel":
                return target.show
            if name == "scaleChildren":
                return False
            self.warn("unknown part field " + name)
            return Unknown(name)
        if isinstance(target, StaticRef):
            if target.name == "Math" and name == "PI":
                return math.pi
            if target.name == "Mth" and name in ("f_13994_", "PI"):
                return math.pi
            if name == "NO_ANIMATION":
                return NO_ANIMATION
            if name.startswith("ANIMATION_") or (name.isupper() and target.name.endswith("Entity")):
                return AnimRef(target.name, name)
            if target.name in ("Float", "Double") and name in ("MAX_VALUE",):
                return 3.4028235e38
            return StaticRef(target.name + "." + name)
        if isinstance(target, EntityProxy):
            if name == "f_19797_":  # tickCount
                return Dual(math.floor(self.ctx.time), 1.0)
            if name in ("f_20884_", "f_20883_", "f_19859_", "f_19857_", "f_20885_", "f_20886_"):
                return 0.0  # body / head yaw
            if name in self.ctx.states:
                return self.ctx.states[name]
            self.state_calls.setdefault(name, "field")
            self.warn("entity field " + name + " -> 0")
            return Unknown("entity." + name)
        if isinstance(target, list) and name == "length":
            return len(target)
        return Unknown(name)

    def assign(self, node, scope):
        op, target, value_node = node[1], node[2], node[3]
        value = self.eval(value_node, scope)
        if op != "=":
            current = self.num(self.eval(target, scope))
            value = self.num(value)
            value = {"+=": d_add, "-=": d_sub, "*=": d_mul, "/=": d_div}.get(op, lambda a, b: a)(current, value)
        if target[0] == "name":
            if target[1] in scope:
                if isinstance(scope[target[1]], int) and not isinstance(scope[target[1]], bool) and is_num(value):
                    value = int(val(value))
                scope[target[1]] = value
            else:
                self.fields[target[1]] = value
        elif target[0] == "field":
            owner = self.eval(target[1], scope)
            if isinstance(owner, PartState):
                if target[2] in PART_FIELDS:
                    setattr(owner, PART_FIELDS[target[2]], self.num(value))
                elif target[2] == "showModel":
                    owner.show = self.truth(value)
                elif target[2] != "scaleChildren":
                    self.warn("unknown part field " + target[2])
            elif owner is self:
                self.fields[target[2]] = value
        elif target[0] == "index":
            owner = self.eval(target[1], scope)
            index = int(val(self.num(self.eval(target[2], scope))))
            if isinstance(owner, list) and 0 <= index < len(owner):
                owner[index] = value
        return value

    # --- calls ------------------------------------------------------------------------------------------------------

    def call(self, node, scope):
        target_node, name, arg_nodes = node[1], node[2], node[3]
        if target_node is None or (target_node[0] == "name" and target_node[1] == "this"):
            target = self
        elif target_node[0] == "name" and target_node[1] == "super":
            args = [self.eval(a, scope) for a in arg_nodes]
            if name in self.super_methods:
                return self.call_user(self.super_methods[name], args)
            if name in self.methods and name != "setupAnim":
                return self.call_model(name, args)
            return None
        else:
            target = self.eval(target_node, scope)
        args = [self.eval(a, scope) for a in arg_nodes]
        if target is self:
            return self.call_model(name, args)
        if isinstance(target, PartState):
            return self.call_part(target, name, args)
        if isinstance(target, Animator):
            return self.call_animator(name, args)
        if isinstance(target, EntityProxy):
            return self.call_entity(name, args)
        if isinstance(target, StaticRef):
            return self.call_static(target.name, name, args)
        if isinstance(target, AnimRef) and name == "getDuration":
            return int(self.animation_duration(target))
        if isinstance(target, list) and name in ("length", "size"):
            return len(target)
        return Unknown(name)

    def call_model(self, name, args):
        if name in self.methods:
            return self.call_user(self.methods[name], args)
        a = args
        if name in ("walk", "swing", "flap"):
            part = a[0]
            if isinstance(part, PartState):
                self.oscillate(part, name, *a[1:])
            return None
        if name == "bob":
            if isinstance(a[0], PartState):
                self.bob(a[0], *a[1:])
            return None
        if name in ("chainWave", "chainSwing", "chainFlap"):
            boxes = [b for b in a[0] if isinstance(b, PartState)] if isinstance(a[0], list) else []
            speed, degree, root_offset, swing, amount = (self.num(x) for x in a[1:6])
            if boxes:
                offset = val(root_offset) * math.pi / (2 * len(boxes))
                attr = {"chainWave": "rx", "chainSwing": "ry", "chainFlap": "rz"}[name]
                for index, box in enumerate(boxes):
                    arg = d_add(d_mul(swing, val(speed) * self.movement_scale), offset * index)
                    value = d_mul(d_mul(self.cos(arg, val(amount) != 0 and val(degree) != 0), amount),
                                  val(degree) * self.movement_scale)
                    setattr(box, attr, d_add(getattr(box, attr), value))
            return None
        if name in ("progressRotationPrev", "progressRotation", "progressPositionPrev", "progressPosition"):
            part = a[0]
            if not isinstance(part, PartState):
                return None
            progress = self.num(a[1])
            divisor = val(self.num(a[5])) or 1.0
            rotation = name.startswith("progressRotation")
            attrs = ("rx", "ry", "rz") if rotation else ("px", "py", "pz")
            for k, attr in enumerate(attrs):
                target = self.num(a[2 + k])
                if not name.endswith("Prev"):
                    target = d_sub(target, part.default[k if rotation else 3 + k])
                setattr(part, attr, d_add(getattr(part, attr), d_div(d_mul(progress, target), divisor)))
            return None
        if name == "setRotateAngle" or name == "setRotationAngle":
            part = a[0]
            if isinstance(part, PartState):
                part.rx, part.ry, part.rz = (self.num(x) for x in a[1:4])
            return None
        if name == "resetToDefaultPose":
            for part in self.parts.values():
                part.reset()
            return None
        if name == "faceTarget":
            yaw, pitch, divisor = (val(self.num(x)) for x in a[:3])
            boxes = [b for b in a[3:] if isinstance(b, PartState)]
            if len(a) == 4 and isinstance(a[3], list):
                boxes = [b for b in a[3] if isinstance(b, PartState)]
            if boxes:
                actual = divisor * len(boxes)
                for box in boxes:
                    box.ry = d_add(box.ry, yaw / 57.295776 / actual)
                    box.rx = d_add(box.rx, pitch / 57.295776 / actual)
            return None
        if name == "setMovementScale":
            self.movement_scale = val(self.num(a[0]))
            return None
        if name == "getMovementScale":
            return self.movement_scale
        if name in ("updateDefaultPose",):
            return None
        if name == "animate" and len(a) == 1:  # base implementation (no animate override)
            return None
        if name == "moveBox":
            speed, degree, invert, offset, weight = a[:5]
            return 0.0
        self.warn("unknown model method " + name)
        return Unknown(name)

    def oscillate(self, part, kind, speed, degree, invert, offset, weight, time, amount):
        speed, degree, offset, weight, time, amount = (self.num(x) for x in (speed, degree, offset, weight, time, amount))
        scale = self.movement_scale
        arg = d_add(d_mul(time, d_mul(speed, scale)), offset)
        audible = val(degree) != 0 and val(amount) != 0
        value = d_add(d_mul(d_mul(self.cos(arg, audible), d_mul(degree, scale)), amount), d_mul(weight, amount))
        if self.truth(invert):
            value = d_neg(value)
        attr = {"walk": "rx", "swing": "ry", "flap": "rz"}[kind]
        setattr(part, attr, d_add(getattr(part, attr), value))

    def bob(self, part, speed, degree, bounce, time, amount):
        speed, degree, time, amount = (self.num(x) for x in (speed, degree, time, amount))
        speed = d_mul(speed, self.movement_scale)
        degree = d_mul(degree, self.movement_scale)
        s = self.sin(d_mul(time, speed), val(degree) != 0 and val(amount) != 0)
        value = d_sub(d_mul(d_mul(s, amount), degree), d_mul(amount, degree))
        if self.truth(bounce):
            value = d_neg(self.abs(d_mul(d_mul(s, amount), degree)))
        part.py = d_add(part.py, value)

    def call_part(self, part, name, a):
        if name in ("walk", "swing", "flap"):
            self.oscillate(part, name, *a)
        elif name == "bob":
            self.bob(part, *a)
        elif name == "setScale":
            part.sx, part.sy, part.sz = (self.num(x) for x in a[:3])
        elif name in ("setScaleX", "setScaleY", "setScaleZ"):
            setattr(part, "s" + name[-1].lower(), self.num(a[0]))
        elif name in ("setPos", "setRotationPoint"):
            part.px, part.py, part.pz = (self.num(x) for x in a[:3])
        elif name == "resetToDefaultPose":
            part.reset()
        elif name in ("setShouldScaleChildren", "updateDefaultPose", "translateAndRotate", "render"):
            pass
        else:
            self.warn("unknown part method " + name)
        return None

    def call_animator(self, name, a):
        if name == "update":
            self.animator.update()
        elif name == "setAnimation":
            return self.animator.set_animation(a[0] if isinstance(a[0], AnimRef) else NO_ANIMATION)
        elif name == "startKeyframe":
            self.animator.start(self.num(a[0]))
        elif name == "endKeyframe":
            self.animator.end(bool(a and self.truth(a[0])))
        elif name == "setStaticKeyframe":
            self.animator.start(self.num(a[0]))
            self.animator.end(True)
        elif name == "resetKeyframe":
            self.animator.start(self.num(a[0]))
            self.animator.end()
        elif name in ("rotate", "move"):
            if isinstance(a[0], PartState):
                self.animator.add(a[0], "rot" if name == "rotate" else "move", *(self.num(x) for x in a[1:4]))
        else:
            self.warn("unknown animator method " + name)
        return None

    def call_entity(self, name, a):
        if name == "getAnimation":
            return self.ctx.animation
        if name == "getAnimationTick":
            return self.ctx.animation_tick
        if name in ("m_20185_", "m_20186_", "m_20189_"):
            return 0.0
        readable = ENTITY_ALIASES.get(name, name)
        if readable in self.ctx.states:
            self.state_calls.setdefault(readable, "method")
            return self.ctx.states[readable]
        if name in ENTITY_DEFAULTS:
            return ENTITY_DEFAULTS[name]
        if readable.startswith(("get", "is", "has")) or readable in ENTITY_ALIASES.values():
            self.state_calls.setdefault(readable, "method")
        return Unknown("entity." + name)

    def call_static(self, owner, name, a):
        nums = [self.num(x) for x in a]
        if owner in ("Math", "Mth", "StrictMath"):
            fn = {"sin": self.sin, "m_14031_": self.sin, "cos": self.cos, "m_14089_": self.cos}.get(name)
            if fn:
                return fn(nums[0])
            if name in ("abs", "m_14154_"):
                return self.abs(nums[0])
            if name in ("toRadians",):
                return d_mul(nums[0], math.pi / 180.0)
            if name in ("toDegrees",):
                return d_mul(nums[0], 180.0 / math.pi)
            if name in ("min", "max"):
                pick = min if name == "min" else max
                return pick(nums, key=val)
            if name in ("m_14036_", "m_14008_", "clamp"):
                return max(nums[1], min(nums[2], nums[0], key=val), key=val)
            if name in ("sqrt", "m_14116_"):
                v = max(val(nums[0]), 0.0)
                r = math.sqrt(v)
                return Dual(r, der(nums[0]) / (2 * r)) if isinstance(nums[0], Dual) and r > 0 else r
            if name == "pow":
                return val(nums[0]) ** val(nums[1]) if val(nums[0]) >= 0 else 0.0
            if name == "signum":
                return math.copysign(1.0, val(nums[0])) if val(nums[0]) != 0 else 0.0
            if name in ("floor", "m_14143_", "m_14107_"):
                return math.floor(val(nums[0]))
            if name in ("ceil",):
                return math.ceil(val(nums[0]))
            if name in ("m_14177_", "m_14175_", "wrapDegrees"):
                v = math.fmod(val(nums[0]), 360.0)
                v = v - 360 if v >= 180 else (v + 360 if v < -180 else v)
                return Dual(v, der(nums[0])) if isinstance(nums[0], Dual) else v
            if name in ("m_14136_", "atan2"):
                return math.atan2(val(nums[0]), val(nums[1]))
            if name in ("m_144920_", "clampedLerp"):
                t = max(0.0, min(1.0, val(nums[2])))
                return d_add(nums[0], d_mul(t, d_sub(nums[1], nums[0])))
            if name in ("m_14179_", "m_14139_", "lerp"):
                return d_add(nums[1], d_mul(nums[0], d_sub(nums[2], nums[1])))
            if name in ("m_14148_", "approachDegrees"):
                cur, target, step = (val(x) for x in nums[:3])
                diff = math.fmod(target - cur + 540.0, 360.0) - 180.0
                return cur + max(-step, min(step, diff))
        if owner == "ACMath":
            if name == "walkValue":
                limb, amount, speed, offset, degree = nums[:5]
                value = d_mul(d_mul(self.cos(d_add(d_mul(limb, speed), offset), val(degree) * val(amount) != 0), degree),
                              amount)
                return d_neg(value) if self.truth(a[5]) else value
            if name == "cullAnimationTick":
                tick, amplitude = nums[0], nums[1]
                anim = a[2]
                partial = self.num(a[3])
                start = val(nums[4])
                duration = self.animation_duration(anim)
                end_at = val(nums[5]) if len(nums) > 5 else duration - start
                if end_at <= 0:
                    return 0.0
                i = max(0.0, min(end_at, val(tick) + val(partial) - start))
                f = math.sin(i / end_at * math.pi) * val(amplitude)
                return smin(f, 1.0, 0.1)
            if name == "smin":
                return smin(*(val(x) for x in nums[:3]))
        if owner in ("Minecraft",):
            return Unknown("Minecraft")
        self.warn(f"unknown static call {owner}.{name}")
        return Unknown(name)

    def animation_duration(self, anim):
        if isinstance(anim, AnimRef):
            return self.durations.get(anim.name, 0) if hasattr(self, "durations") else 0
        return 0

    def sin(self, x, record=True):
        if isinstance(x, Dual):
            if x.d and record:
                self.frequencies.append(abs(x.d))
            return Dual(math.sin(x.v), math.cos(x.v) * x.d)
        return math.sin(val(x))

    def cos(self, x, record=True):
        if isinstance(x, Dual):
            if x.d and record:
                self.frequencies.append(abs(x.d))
            return Dual(math.cos(x.v), -math.sin(x.v) * x.d)
        return math.cos(val(x))

    @staticmethod
    def abs(x):
        if isinstance(x, Dual):
            return Dual(abs(x.v), x.d if x.v >= 0 else -x.d)
        return abs(val(x))


def smin(a, b, k):
    h = max(k - abs(a - b), 0.0) / k
    return min(a, b) - h * h * k * 0.25


# ---------------------------------------------------------------------------------------------------------------------
# Animation constants of the entity


def entity_animations(source_dir, entity_class):
    """Returns {CONSTANT: duration_ticks} declared with Animation.create(n) in the entity class (and parents)."""
    found = {}
    root = os.path.join(source_dir, "com", "github", "alexmodguy", "alexscaves", "server", "entity")
    seen = set()
    name = entity_class
    while name and name not in seen:
        seen.add(name)
        path = None
        for folder, _, files in os.walk(root):
            if name + ".java" in files:
                path = os.path.join(folder, name + ".java")
                break
        if path is None:
            break
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        for m in re.finditer(r"static final Animation (\w+) = Animation\.create\((\d+)\)", text):
            found.setdefault(m.group(1), int(m.group(2)))
        sm = re.search(r"class " + name + r"\b[^{]*? extends (\w+)", text)
        name = sm.group(1) if sm else None
    return found


# ---------------------------------------------------------------------------------------------------------------------
# Where the mod triggers each animation (for the mapping handed to the behaviour code)

_SERVER_CACHE = {}


def _server_sources(source_dir):
    if source_dir not in _SERVER_CACHE:
        root = os.path.join(source_dir, "com", "github", "alexmodguy", "alexscaves", "server")
        files = {}
        for folder, _, names in os.walk(root):
            for name in sorted(names):
                if name.endswith(".java"):
                    with open(os.path.join(folder, name), encoding="utf-8") as handle:
                        files[name[:-5]] = handle.read().splitlines()
        _SERVER_CACHE[source_dir] = files
    return _SERVER_CACHE[source_dir]


_METHOD_LINE = re.compile(r"^\s*(?:public|private|protected)[^=;(]*?\b(\w+)\s*\([^;]*$")


def _enclosing_method(lines, index):
    for i in range(index, -1, -1):
        m = _METHOD_LINE.match(lines[i])
        if m:
            return m.group(1)
        if re.match(r"^\s*(?:public |private |protected )?(?:static )?(?:final )?class \w+", lines[i]):
            return "<class>"
    return "?"




def entity_chain(source_dir, entity_class):
    """The entity class and its superclasses that exist in the mod's server sources."""
    files = _server_sources(source_dir)
    chain = []
    name = entity_class
    while name in files and name not in chain:
        chain.append(name)
        sm = re.search(r"class " + name + r"\b[^{]*? extends (\w+)", "\n".join(files[name]))
        name = sm.group(1) if sm else None
    return chain


def animation_triggers(source_dir, entity_class, constant, limit=4):
    """'Class.method: code' for each place of the mod's server code that uses the entity's Animation constant
    (outside its declaration and the getAnimations() list)."""
    chain = entity_chain(source_dir, entity_class)
    if not chain:
        return []
    qualified = re.compile(r"\b(?:" + "|".join(map(re.escape, chain)) + r")\." + re.escape(constant) + r"\b")
    bare = re.compile(r"(?<![.\w])" + re.escape(constant) + r"\b")
    listing = re.compile(r"(?:\w+\.)?" + re.escape(constant) + r",?")
    sites = []
    for cls, lines in _server_sources(source_dir).items():
        for i, line in enumerate(lines):
            text = line.strip()
            if "Animation.create" in text or listing.fullmatch(text):
                continue
            if not (qualified.search(text) or (cls in chain and bare.search(text))):
                continue
            starts = 0 if re.search(r"setAnimation|tryAnimation|[?:] *(?:\w+\.)?" + re.escape(constant), text) else 1
            sites.append((starts, f"{cls}.{_enclosing_method(lines, i)}: {text[:160]}"))
    return [site for _, site in sorted(sites, key=lambda s: s[0])[:limit]]


def state_triggers(source_dir, entity_class, getter, limit=3):
    """How the entity drives a progress getter or boolean: the conditions that raise the progress value."""
    files = _server_sources(source_dir)
    for cls in entity_chain(source_dir, entity_class):
        text = "\n".join(files[cls])
        m = re.search(r"public \w+ " + getter + r"\([^)]*\)\s*\{(.*?)\n   \}", text, re.S)
        if not m:
            continue
        body = " ".join(m.group(1).split())
        fields = [f for f in re.findall(r"this\.(\w+)\b(?!\s*\()", body) if not f.startswith("prev")]
        if not fields or getter.startswith(("is", "has")):
            return [f"{cls}.{getter}: {body[:160]}"]
        field = fields[-1]
        out = []
        for owner in entity_chain(source_dir, entity_class):
            lines = files[owner]
            added = set()
            for i, line in enumerate(lines):
                if re.search(r"this\." + field + r"\s*(<|\+\+|\+=)", line) and i - 1 not in added:
                    added.add(i)
                    cond = line.strip()
                    if not cond.startswith("if") and i > 0 and lines[i - 1].strip().startswith("if"):
                        cond = lines[i - 1].strip() + " " + cond
                    out.append(f"{owner}.{_enclosing_method(lines, i)}: {cond[:160]}")
                    if len(out) >= limit:
                        return out
        return out or [f"{cls}.{getter}: {body[:160]}"]
    return []
