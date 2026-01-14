package com.easysigns.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonInt32;
import org.bson.BsonValue;

/**
 * Event data sent from the sign edit UI to the server.
 * Contains the action type and associated data (line index, text content).
 */
public class SignEventData {

    /**
     * Codec for serializing/deserializing SignEventData from UI events.
     */
    public static final Codec<SignEventData> CODEC = new Codec<SignEventData>() {
        @Override
        public SignEventData decode(BsonValue value, ExtraInfo info) {
            if (value == null || !value.isDocument()) {
                return new SignEventData();
            }

            BsonDocument document = value.asDocument();
            SignEventData data = new SignEventData();

            if (document.containsKey("action")) {
                data.action = document.getString("action").getValue();
            }
            if (document.containsKey("lineIndex")) {
                data.lineIndex = document.getInt32("lineIndex").getValue();
            }
            if (document.containsKey("text")) {
                data.text = document.getString("text").getValue();
            }
            if (document.containsKey("colorHex")) {
                data.colorHex = document.getString("colorHex").getValue();
            }

            return data;
        }

        @Override
        public BsonValue encode(SignEventData data, ExtraInfo info) {
            BsonDocument document = new BsonDocument();

            if (data.action != null) {
                document.put("action", new BsonString(data.action));
            }
            document.put("lineIndex", new BsonInt32(data.lineIndex));
            if (data.text != null) {
                document.put("text", new BsonString(data.text));
            }
            if (data.colorHex != null) {
                document.put("colorHex", new BsonString(data.colorHex));
            }

            return document;
        }

        @Override
        public Schema toSchema(SchemaContext context) {
            // Return null for now - schema generation not required for basic functionality
            return null;
        }
    };

    // Event action types
    public static final String ACTION_UPDATE_LINE = "updateLine";
    public static final String ACTION_SAVE = "save";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_CLEAR = "clear";
    public static final String ACTION_SET_COLOR = "setColor";

    // Event data fields
    private String action;
    private int lineIndex;
    private String text;
    private String colorHex;

    public SignEventData() {
        this.action = "";
        this.lineIndex = 0;
        this.text = "";
        this.colorHex = "#000000";
    }

    /**
     * Create event data for a line update.
     */
    public static SignEventData updateLine(int lineIndex, String text) {
        SignEventData data = new SignEventData();
        data.action = ACTION_UPDATE_LINE;
        data.lineIndex = lineIndex;
        data.text = text;
        return data;
    }

    /**
     * Create event data for save action.
     */
    public static SignEventData save() {
        SignEventData data = new SignEventData();
        data.action = ACTION_SAVE;
        return data;
    }

    /**
     * Create event data for cancel action.
     */
    public static SignEventData cancel() {
        SignEventData data = new SignEventData();
        data.action = ACTION_CANCEL;
        return data;
    }

    /**
     * Create event data for clear action.
     */
    public static SignEventData clear() {
        SignEventData data = new SignEventData();
        data.action = ACTION_CLEAR;
        return data;
    }

    /**
     * Create event data for color change.
     */
    public static SignEventData setColor(String hexColor) {
        SignEventData data = new SignEventData();
        data.action = ACTION_SET_COLOR;
        data.colorHex = hexColor;
        return data;
    }

    // Getters and setters

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    @Override
    public String toString() {
        return "SignEventData{" +
                "action='" + action + '\'' +
                ", lineIndex=" + lineIndex +
                ", text='" + text + '\'' +
                ", colorHex='" + colorHex + '\'' +
                '}';
    }
}
