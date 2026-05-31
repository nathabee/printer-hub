package spaghettichef.central.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CentralJson {
    private CentralJson() {
    }

    static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isDone()) {
            throw new IllegalArgumentException("invalid json");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("json body must be an object");
        }
        return (Map<String, Object>) value;
    }

    static String stringField(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return string;
    }

    static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        writeJson(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeJson(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            builder.append('"').append(escape(string)).append('"');
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof BigDecimal) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("json object keys must be strings");
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escape(key)).append('"').append(':');
                writeJson(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value instanceof List<?> list) {
            builder.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                writeJson(builder, list.get(index));
            }
            builder.append(']');
        } else {
            throw new IllegalArgumentException("unsupported json value");
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json == null ? "" : json;
        }

        private Object parseValue() {
            skipWhitespace();
            if (isDone()) {
                throw new IllegalArgumentException("invalid json");
            }
            char ch = json.charAt(index);
            return switch (ch) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return values;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!isDone()) {
                char ch = json.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch == '\\') {
                    if (isDone()) {
                        throw new IllegalArgumentException("invalid json string");
                    }
                    char escaped = json.charAt(index++);
                    switch (escaped) {
                        case '"' -> builder.append('"');
                        case '\\' -> builder.append('\\');
                        case '/' -> builder.append('/');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(parseUnicode());
                        default -> throw new IllegalArgumentException("invalid json string escape");
                    }
                } else {
                    builder.append(ch);
                }
            }
            throw new IllegalArgumentException("unterminated json string");
        }

        private char parseUnicode() {
            if (index + 4 > json.length()) {
                throw new IllegalArgumentException("invalid unicode escape");
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("invalid unicode escape", exception);
            }
        }

        private Object parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            consumeDigits();
            if (peek('.')) {
                index++;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                consumeDigits();
            }
            if (start == index) {
                throw new IllegalArgumentException("invalid json value");
            }
            return new BigDecimal(json.substring(start, index));
        }

        private void consumeDigits() {
            int start = index;
            while (!isDone() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("invalid json number");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!json.startsWith(literal, index)) {
                throw new IllegalArgumentException("invalid json value");
            }
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (isDone() || json.charAt(index) != expected) {
                throw new IllegalArgumentException("invalid json");
            }
            index++;
        }

        private boolean peek(char expected) {
            return !isDone() && json.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (!isDone() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private boolean isDone() {
            return index >= json.length();
        }
    }
}
