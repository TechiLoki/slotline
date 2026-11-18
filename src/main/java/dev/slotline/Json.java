package dev.slotline;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static {
        MAPPER.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(30).maxStringLength(5000).maxNumberLength(30).build());
    }
    private Json() {}
    public static JsonNode read(String value) {
        try { JsonNode result = MAPPER.readTree(value); if (result == null || result.isMissingNode()) throw Problem.invalid("Expected valid JSON."); return result; }
        catch (Exception e) { throw Problem.invalid("Expected valid JSON."); }
    }
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not encode response", e); }
    }
    public static String text(JsonNode node, String key, int max, boolean required) {
        JsonNode field = node.path(key);
        if (!field.isMissingNode() && !field.isTextual()) throw Problem.invalid(key + " must be text.");
        String value = field.asText("").strip();
        if (value.length() > max || required && value.isEmpty()) throw Problem.invalid("Invalid " + key + ".");
        if (value.indexOf('\0') >= 0) throw Problem.invalid("Unsupported control character.");
        return value;
    }
    public static int integer(JsonNode node, String key, int min, int max) {
        JsonNode value = node.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max)
            throw Problem.invalid(key + " must be an integer between " + min + " and " + max + ".");
        return value.intValue();
    }
}
