"""Lector/escritor NBT minimo (big endian, como los archivos de Minecraft Java)."""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """Valor con tipo explicito para escribir (lectura devuelve tipos de Python)."""

    def __init__(self, kind, value, list_kind=None):
        self.kind, self.value, self.list_kind = kind, value, list_kind


def _read_string(f):
    (n,) = struct.unpack(">H", f.read(2))
    return f.read(n).decode("utf-8", "replace")


def _read_payload(f, kind):
    if kind == BYTE:
        return struct.unpack(">b", f.read(1))[0]
    if kind == SHORT:
        return struct.unpack(">h", f.read(2))[0]
    if kind == INT:
        return struct.unpack(">i", f.read(4))[0]
    if kind == LONG:
        return struct.unpack(">q", f.read(8))[0]
    if kind == FLOAT:
        return struct.unpack(">f", f.read(4))[0]
    if kind == DOUBLE:
        return struct.unpack(">d", f.read(8))[0]
    if kind == BYTE_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return f.read(n)
    if kind == STRING:
        return _read_string(f)
    if kind == LIST:
        sub = f.read(1)[0]
        (n,) = struct.unpack(">i", f.read(4))
        return [_read_payload(f, sub) for _ in range(n)]
    if kind == COMPOUND:
        out = {}
        while True:
            t = f.read(1)[0]
            if t == END:
                return out
            name = _read_string(f)
            out[name] = _read_payload(f, t)
    if kind == INT_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if kind == LONG_ARRAY:
        (n,) = struct.unpack(">i", f.read(4))
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"tipo NBT desconocido {kind}")


def read(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    f = io.BytesIO(raw)
    kind = f.read(1)[0]
    name = _read_string(f)
    return name, _read_payload(f, kind)


def _write_payload(f, tag):
    k, v = tag.kind, tag.value
    if k == BYTE:
        f.write(struct.pack(">b", v))
    elif k == SHORT:
        f.write(struct.pack(">h", v))
    elif k == INT:
        f.write(struct.pack(">i", v))
    elif k == LONG:
        f.write(struct.pack(">q", v))
    elif k == FLOAT:
        f.write(struct.pack(">f", v))
    elif k == DOUBLE:
        f.write(struct.pack(">d", v))
    elif k == STRING:
        b = v.encode("utf-8")
        f.write(struct.pack(">H", len(b)) + b)
    elif k == LIST:
        f.write(bytes([tag.list_kind if v else (tag.list_kind or END)]))
        f.write(struct.pack(">i", len(v)))
        for item in v:
            _write_payload(f, item)
    elif k == COMPOUND:
        for name, item in v.items():
            f.write(bytes([item.kind]))
            b = name.encode("utf-8")
            f.write(struct.pack(">H", len(b)) + b)
            _write_payload(f, item)
        f.write(bytes([END]))
    elif k == INT_ARRAY:
        f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}i", *v))
    else:
        raise ValueError(f"no se puede escribir el tipo {k}")


def write(path, root, name=""):
    f = io.BytesIO()
    f.write(bytes([COMPOUND]))
    b = name.encode("utf-8")
    f.write(struct.pack(">H", len(b)) + b)
    _write_payload(f, root)
    open(path, "wb").write(gzip.compress(f.getvalue()))


def compound(**items):
    return Tag(COMPOUND, items)


def ints(*values):
    return Tag(LIST, [Tag(INT, v) for v in values], INT)
