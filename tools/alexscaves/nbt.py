"""Lector y escritor mínimo de NBT (formato de las plantillas de estructuras de Minecraft, gzip)."""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """Valor NBT con su tipo, para poder volver a escribirlo igual."""

    def __init__(self, kind, value, item_kind=None):
        self.kind = kind
        self.value = value
        self.item_kind = item_kind

    def __repr__(self):
        return "Tag(%d, %r)" % (self.kind, self.value)


def _read(stream, kind):
    if kind == BYTE:
        return struct.unpack(">b", stream.read(1))[0]
    if kind == SHORT:
        return struct.unpack(">h", stream.read(2))[0]
    if kind == INT:
        return struct.unpack(">i", stream.read(4))[0]
    if kind == LONG:
        return struct.unpack(">q", stream.read(8))[0]
    if kind == FLOAT:
        return struct.unpack(">f", stream.read(4))[0]
    if kind == DOUBLE:
        return struct.unpack(">d", stream.read(8))[0]
    if kind == BYTE_ARRAY:
        n = struct.unpack(">i", stream.read(4))[0]
        return stream.read(n)
    if kind == STRING:
        n = struct.unpack(">H", stream.read(2))[0]
        return stream.read(n).decode("utf-8")
    if kind == LIST:
        item = stream.read(1)[0]
        n = struct.unpack(">i", stream.read(4))[0]
        return Tag(LIST, [Tag(item, _read(stream, item)) for _ in range(n)], item)
    if kind == COMPOUND:
        out = {}
        while True:
            child = stream.read(1)[0]
            if child == END:
                return out
            name = _read(stream, STRING)
            out[name] = Tag(child, _read(stream, child))
    if kind == INT_ARRAY:
        n = struct.unpack(">i", stream.read(4))[0]
        return list(struct.unpack(">%di" % n, stream.read(4 * n)))
    if kind == LONG_ARRAY:
        n = struct.unpack(">i", stream.read(4))[0]
        return list(struct.unpack(">%dq" % n, stream.read(8 * n)))
    raise ValueError("tipo NBT desconocido %d" % kind)


def _write(stream, tag):
    kind, value = tag.kind, tag.value
    if kind == BYTE:
        stream.write(struct.pack(">b", value))
    elif kind == SHORT:
        stream.write(struct.pack(">h", value))
    elif kind == INT:
        stream.write(struct.pack(">i", value))
    elif kind == LONG:
        stream.write(struct.pack(">q", value))
    elif kind == FLOAT:
        stream.write(struct.pack(">f", value))
    elif kind == DOUBLE:
        stream.write(struct.pack(">d", value))
    elif kind == BYTE_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        stream.write(value)
    elif kind == STRING:
        data = value.encode("utf-8")
        stream.write(struct.pack(">H", len(data)))
        stream.write(data)
    elif kind == LIST:
        items = value.value if isinstance(value, Tag) else value
        item_kind = value.item_kind if isinstance(value, Tag) else tag.item_kind
        if item_kind is None:
            item_kind = items[0].kind if items else END
        stream.write(bytes([item_kind]))
        stream.write(struct.pack(">i", len(items)))
        for item in items:
            _write(stream, Tag(item_kind, item.value, getattr(item, "item_kind", None)))
    elif kind == COMPOUND:
        for name, child in value.items():
            stream.write(bytes([child.kind]))
            _write(stream, Tag(STRING, name))
            _write(stream, child)
        stream.write(bytes([END]))
    elif kind == INT_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        stream.write(struct.pack(">%di" % len(value), *value))
    elif kind == LONG_ARRAY:
        stream.write(struct.pack(">i", len(value)))
        stream.write(struct.pack(">%dq" % len(value), *value))
    else:
        raise ValueError("tipo NBT desconocido %d" % kind)


def load(data):
    """Raíz (nombre, Tag compuesto) de un archivo NBT comprimido con gzip."""
    stream = io.BytesIO(gzip.decompress(data))
    kind = stream.read(1)[0]
    name = _read(stream, STRING)
    return name, Tag(kind, _read(stream, kind))


def dump(name, root):
    stream = io.BytesIO()
    stream.write(bytes([root.kind]))
    _write(stream, Tag(STRING, name))
    _write(stream, root)
    return gzip.compress(stream.getvalue(), mtime=0)
