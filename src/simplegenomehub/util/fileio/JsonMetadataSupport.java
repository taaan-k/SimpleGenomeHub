package simplegenomehub.util.fileio;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** UTF-8 complete-file metadata writes that retain fields from newer producers. */
public final class JsonMetadataSupport {
    private static final Logger LOGGER = Logger.getLogger(JsonMetadataSupport.class.getName());
    private JsonMetadataSupport() { }

    public enum FailureKind { ENCODING, JSON }

    /** Keep decoding failures distinguishable from invalid JSON in the UI. */
    public static final class MetadataReadException extends IOException {
        private final FailureKind kind;
        MetadataReadException(FailureKind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }
        public FailureKind getKind() { return kind; }
    }

    public static JsonObject read(File file) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file.toPath());
             InputStream decoded = file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".gz")
                 ? new java.util.zip.GZIPInputStream(input) : input) {
            bytes = decoded.readAllBytes();
        }
        Set<Charset> candidates = new LinkedHashSet<>();
        candidates.add(StandardCharsets.UTF_8);
        addCharset(candidates, System.getProperty("native.encoding"));
        candidates.add(Charset.defaultCharset());
        addCharset(candidates, "GB18030");
        CharacterCodingException decodingFailure = null;
        for (Charset charset : candidates) {
            final String text;
            try {
                text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException failure) {
                decodingFailure = failure;
                continue;
            }
            // Once text decoded successfully, a syntax error is not an encoding fallback.
            JsonObject result = parseObject(text, file, charset);
            if (!StandardCharsets.UTF_8.equals(charset)) {
                LOGGER.info("Read legacy metadata using " + charset.name() + ": " + file.getAbsolutePath());
            }
            return result;
        }
        throw new MetadataReadException(FailureKind.ENCODING,
            "Cannot decode metadata " + file + " using " + candidates, decodingFailure);
    }

    private static void addCharset(Set<Charset> candidates, String name) {
        if (name == null || name.trim().isEmpty()) return;
        try { candidates.add(Charset.forName(name.trim())); }
        catch (IllegalArgumentException unavailable) { /* Optional native encoding is not always available. */ }
    }

    private static JsonObject parseObject(String text, File file, Charset charset) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = readValue(reader, 0);
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Expected a single JSON object");
            return value.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException failure) {
            throw new MetadataReadException(FailureKind.JSON,
                "Invalid JSON metadata (" + charset.name() + "): " + file + ": " + failure.getMessage(), failure);
        }
    }

    // JsonParser.parse() enables lenient mode in the bundled Gson. Read tokens
    // directly so broken JSON is not silently accepted as a usable experiment.
    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 256) throw new IOException("JSON nesting exceeds 256 levels");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) object.add(reader.nextName(), readValue(reader, depth + 1));
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default: throw new IOException("Expected a JSON value at " + reader.getPath());
        }
    }
    public static JsonObject copy(JsonObject object) {
        return object == null ? new JsonObject() : new JsonParser().parse(object.toString()).getAsJsonObject();
    }
    public static JsonElement merge(JsonElement old, JsonElement update) {
        if (old != null && old.isJsonObject() && update.isJsonObject()) {
            JsonObject out = copy(old.getAsJsonObject());
            for (Map.Entry<String, JsonElement> field : update.getAsJsonObject().entrySet())
                out.add(field.getKey(), merge(out.get(field.getKey()), field.getValue()));
            return out;
        }
        if (old != null && old.isJsonArray() && update.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement entry : update.getAsJsonArray()) {
                JsonElement previous = null;
                if (entry.isJsonObject()) {
                    for (String key : new String[]{"sample_id", "vcfName"}) {
                        JsonElement id = entry.getAsJsonObject().get(key);
                        if (id == null) continue;
                        for (JsonElement candidate : old.getAsJsonArray())
                            if (candidate.isJsonObject() && id.equals(candidate.getAsJsonObject().get(key))) previous = candidate;
                        break;
                    }
                }
                out.add(merge(previous, entry));
            }
            return out;
        }
        return update;
    }
    public static void write(File target, JsonObject value) throws IOException {
        AtomicFiles.write(target.toPath(), writer -> new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(value, writer));
    }
    public static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
