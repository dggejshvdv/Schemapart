package com.schemapart.core;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal big-endian NBT writer (gzip is applied by the caller). */
public final class Nbt {
    public static final byte END = 0;
    public static final byte BYTE = 1;
    public static final byte SHORT = 2;
    public static final byte INT = 3;
    public static final byte LONG = 4;
    public static final byte FLOAT = 5;
    public static final byte DOUBLE = 6;
    public static final byte BYTE_ARRAY = 7;
    public static final byte STRING = 8;
    public static final byte LIST = 9;
    public static final byte COMPOUND = 10;
    public static final byte INT_ARRAY = 11;
    public static final byte LONG_ARRAY = 12;

    private Nbt() {
    }

    public abstract static class Tag {
        public abstract byte type();
        public abstract void write(DataOutputStream out) throws IOException;
    }

    public static final class ByteTag extends Tag {
        public final byte value;
        public ByteTag(byte value) { this.value = value; }
        public ByteTag(boolean value) { this.value = (byte) (value ? 1 : 0); }
        @Override public byte type() { return BYTE; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeByte(value); }
    }

    public static final class ShortTag extends Tag {
        public final short value;
        public ShortTag(short value) { this.value = value; }
        @Override public byte type() { return SHORT; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeShort(value); }
    }

    public static final class IntTag extends Tag {
        public final int value;
        public IntTag(int value) { this.value = value; }
        @Override public byte type() { return INT; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeInt(value); }
    }

    public static final class LongTag extends Tag {
        public final long value;
        public LongTag(long value) { this.value = value; }
        @Override public byte type() { return LONG; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeLong(value); }
    }

    public static final class FloatTag extends Tag {
        public final float value;
        public FloatTag(float value) { this.value = value; }
        @Override public byte type() { return FLOAT; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeFloat(value); }
    }

    public static final class DoubleTag extends Tag {
        public final double value;
        public DoubleTag(double value) { this.value = value; }
        @Override public byte type() { return DOUBLE; }
        @Override public void write(DataOutputStream out) throws IOException { out.writeDouble(value); }
    }

    public static final class StringTag extends Tag {
        public final String value;
        public StringTag(String value) { this.value = value; }
        @Override public byte type() { return STRING; }
        @Override public void write(DataOutputStream out) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeShort(bytes.length);
            out.write(bytes);
        }
    }

    public static final class IntArrayTag extends Tag {
        public final int[] value;
        public IntArrayTag(int[] value) { this.value = value; }
        @Override public byte type() { return INT_ARRAY; }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeInt(value.length);
            for (int v : value) out.writeInt(v);
        }
    }

    public static final class LongArrayTag extends Tag {
        public final long[] value;
        public LongArrayTag(long[] value) { this.value = value; }
        @Override public byte type() { return LONG_ARRAY; }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeInt(value.length);
            for (long v : value) out.writeLong(v);
        }
    }

    public static final class ListTag extends Tag {
        public final byte elementType;
        public final List<Tag> values = new ArrayList<>();
        public ListTag(byte elementType) { this.elementType = elementType; }
        public ListTag add(Tag tag) { values.add(tag); return this; }
        @Override public byte type() { return LIST; }
        @Override public void write(DataOutputStream out) throws IOException {
            out.writeByte(elementType);
            out.writeInt(values.size());
            for (Tag tag : values) tag.write(out);
        }
    }

    public static final class CompoundTag extends Tag {
        public final Map<String, Tag> values = new LinkedHashMap<>();
        public CompoundTag put(String name, Tag tag) { values.put(name, tag); return this; }
        public CompoundTag putByte(String name, int value) { return put(name, new ByteTag((byte) value)); }
        public CompoundTag putShort(String name, int value) { return put(name, new ShortTag((short) value)); }
        public CompoundTag putInt(String name, int value) { return put(name, new IntTag(value)); }
        public CompoundTag putLong(String name, long value) { return put(name, new LongTag(value)); }
        public CompoundTag putString(String name, String value) { return put(name, new StringTag(value)); }
        @Override public byte type() { return COMPOUND; }
        @Override public void write(DataOutputStream out) throws IOException {
            for (Map.Entry<String, Tag> entry : values.entrySet()) {
                out.writeByte(entry.getValue().type());
                writeName(out, entry.getKey());
                entry.getValue().write(out);
            }
            out.writeByte(END);
        }
    }

    public static void writeRoot(CompoundTag root, DataOutputStream out) throws IOException {
        out.writeByte(COMPOUND);
        writeName(out, "");
        root.write(out);
    }

    private static void writeName(DataOutputStream out, String name) throws IOException {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }
}
