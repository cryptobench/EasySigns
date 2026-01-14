package com.easysigns.sign;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.awt.Color;
import java.util.Arrays;

/**
 * BlockState implementation for signs.
 * Stores the text lines and styling information for a sign.
 *
 * Data is automatically persisted when chunks are saved via BSON serialization.
 */
@SuppressWarnings("deprecation")
public class SignBlockState extends BlockState {

    public static final int MAX_LINES = 4;
    public static final int MAX_LINE_LENGTH = 32;

    /**
     * Codec for serialization/deserialization of SignBlockState.
     * Used by Hytale's block state registry for persistence.
     */
    public static final Codec<SignBlockState> CODEC = new Codec<SignBlockState>() {
        @Override
        public SignBlockState decode(BsonValue value, ExtraInfo info) {
            SignBlockState state = new SignBlockState();

            if (value == null || !value.isDocument()) {
                return state;
            }

            BsonDocument document = value.asDocument();

            // Decode text lines
            if (document.containsKey("lines")) {
                BsonArray linesArray = document.getArray("lines");
                for (int i = 0; i < Math.min(linesArray.size(), MAX_LINES); i++) {
                    state.lines[i] = linesArray.get(i).asString().getValue();
                }
            }

            // Decode text color
            if (document.containsKey("colorR")) {
                int r = document.getInt32("colorR").getValue();
                int g = document.getInt32("colorG").getValue();
                int b = document.getInt32("colorB").getValue();
                state.textColor = new Color(r, g, b);
            }

            // Decode glow effect
            if (document.containsKey("glowing")) {
                state.glowing = document.getBoolean("glowing").getValue();
            }

            return state;
        }

        @Override
        public BsonValue encode(SignBlockState state, ExtraInfo info) {
            BsonDocument document = new BsonDocument();

            // Encode text lines
            BsonArray linesArray = new BsonArray();
            for (String line : state.lines) {
                linesArray.add(new BsonString(line != null ? line : ""));
            }
            document.put("lines", linesArray);

            // Encode text color
            document.put("colorR", new BsonInt32(state.textColor.getRed()));
            document.put("colorG", new BsonInt32(state.textColor.getGreen()));
            document.put("colorB", new BsonInt32(state.textColor.getBlue()));

            // Encode glow effect
            document.put("glowing", new BsonBoolean(state.glowing));

            return document;
        }

        @Override
        public Schema toSchema(SchemaContext context) {
            // Return null for now - schema generation not required for basic functionality
            return null;
        }
    };

    // Sign data fields
    private final String[] lines;
    private Color textColor;
    private boolean glowing;

    /**
     * Create a new empty sign state.
     */
    public SignBlockState() {
        this.lines = new String[MAX_LINES];
        Arrays.fill(this.lines, "");
        this.textColor = new Color(0, 0, 0); // Black text by default
        this.glowing = false;
    }

    /**
     * Create a sign state with initial text.
     */
    public SignBlockState(String[] initialLines) {
        this();
        if (initialLines != null) {
            for (int i = 0; i < Math.min(initialLines.length, MAX_LINES); i++) {
                setLine(i, initialLines[i]);
            }
        }
    }

    /**
     * Get a specific line of text.
     * @param index Line index (0-3)
     * @return The text on that line, or empty string if invalid index
     */
    public String getLine(int index) {
        if (index < 0 || index >= MAX_LINES) {
            return "";
        }
        return lines[index] != null ? lines[index] : "";
    }

    /**
     * Set a specific line of text.
     * @param index Line index (0-3)
     * @param text The text to set (truncated to MAX_LINE_LENGTH)
     */
    public void setLine(int index, String text) {
        if (index < 0 || index >= MAX_LINES) {
            return;
        }

        if (text == null) {
            lines[index] = "";
        } else if (text.length() > MAX_LINE_LENGTH) {
            lines[index] = text.substring(0, MAX_LINE_LENGTH);
        } else {
            lines[index] = text;
        }

        markNeedsSave();
    }

    /**
     * Get all lines as an array.
     */
    public String[] getLines() {
        return Arrays.copyOf(lines, lines.length);
    }

    /**
     * Set all lines at once.
     */
    public void setLines(String[] newLines) {
        if (newLines == null) {
            Arrays.fill(lines, "");
        } else {
            for (int i = 0; i < MAX_LINES; i++) {
                setLine(i, i < newLines.length ? newLines[i] : "");
            }
        }
    }

    /**
     * Get all text as a single string (for display).
     */
    public String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_LINES; i++) {
            if (lines[i] != null && !lines[i].isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(lines[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Check if the sign has any text.
     */
    public boolean hasText() {
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clear all text from the sign.
     */
    public void clear() {
        Arrays.fill(lines, "");
        markNeedsSave();
    }

    /**
     * Get the text color.
     */
    public Color getTextColor() {
        return textColor;
    }

    /**
     * Set the text color.
     */
    public void setTextColor(Color color) {
        this.textColor = color != null ? color : new Color(0, 0, 0);
        markNeedsSave();
    }

    /**
     * Check if the sign has a glow effect.
     */
    public boolean isGlowing() {
        return glowing;
    }

    /**
     * Set the glow effect.
     */
    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
        markNeedsSave();
    }

    @Override
    public BsonDocument saveToDocument() {
        return (BsonDocument) CODEC.encode(this, null);
    }

    @Override
    public String toString() {
        return "SignBlockState{" +
                "lines=" + Arrays.toString(lines) +
                ", color=" + textColor +
                ", glowing=" + glowing +
                ", pos=" + getPosition() +
                '}';
    }
}
