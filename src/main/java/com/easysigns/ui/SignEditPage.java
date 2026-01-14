package com.easysigns.ui;

import com.easysigns.EasySigns;
import com.easysigns.data.SignData;
import com.easysigns.data.SignStorage;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;

import java.util.logging.Logger;

/**
 * Custom UI page for editing sign text.
 * Uses the existing NameRespawnPointPage.ui layout with text input.
 * Users enter all lines separated by | (e.g., "Line1|Line2|Line3|Line4")
 */
public class SignEditPage extends InteractiveCustomUIPage<SignEditPage.SignEditEventData> {

    private static final String PAGE_LAYOUT = "Pages/NameRespawnPointPage.ui";

    private final EasySigns plugin;
    private final SignStorage signStorage;
    private final String worldName;
    private final Vector3i position;
    private final SignData signData;
    private final World world;
    private final Logger logger;

    private String currentText = "";
    private boolean alreadySaved = false;

    public SignEditPage(EasySigns plugin, SignStorage signStorage,
                        String worldName, Vector3i position, SignData signData,
                        PlayerRef playerRef, World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, SignEditEventData.CODEC);
        this.plugin = plugin;
        this.signStorage = signStorage;
        this.worldName = worldName;
        this.position = position;
        this.signData = signData;
        this.world = world;
        this.logger = plugin.getPluginLogger();

        // Build initial text from existing sign data (only non-empty lines)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SignData.MAX_LINES; i++) {
            String line = signData.getLine(i);
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) sb.append("|");
                sb.append(line);
            }
        }
        this.currentText = sb.toString();
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder ui,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Append the base page layout
        ui.append(PAGE_LAYOUT);

        // Set the input field value (use .Value property)
        ui.set("#NameInput.Value", currentText);

        // Save button - @ prefix on key means resolve #NameInput.Value at event time
        EventData saveData = EventData.of("@Value", "#NameInput.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SetButton", saveData);

        // Cancel button
        EventData cancelData = EventData.of("Action", "Cancel");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", cancelData);
    }

    /**
     * Raw string handler - logs the actual JSON received from client.
     */
    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, String rawJson) {
        logger.info("Sign edit RAW JSON: " + rawJson);
        super.handleDataEvent(playerRef, store, rawJson);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, SignEditEventData eventData) {
        if (eventData == null) {
            logger.info("Sign edit event: eventData is null");
            close();
            return;
        }

        String action = eventData.action;
        String value = eventData.value;

        logger.info("Sign edit event: action=[" + action + "], value=[" + value + "]");

        // Cancel button sends Action="Cancel"
        if ("Cancel".equals(action)) {
            close();
            return;
        }

        // Save button clicked - save the value if we got one, otherwise save current/empty
        alreadySaved = true;
        if (value != null && !value.isEmpty()) {
            parseAndSaveText(value);
        } else {
            // No value received from binding, try saving currentText
            logger.info("No value from binding, saving currentText: " + currentText);
            parseAndSaveText(currentText);
        }
        close();
    }

    private void parseAndSaveText(String text) {
        if (text == null) text = "";

        // Split by | to get individual lines
        String[] parts = text.split("\\|", SignData.MAX_LINES + 1);

        for (int i = 0; i < SignData.MAX_LINES; i++) {
            if (i < parts.length) {
                signData.setLine(i, parts[i].trim());
            } else {
                signData.setLine(i, "");
            }
        }

        signStorage.updateSign(worldName, position, signData);

        // Update the floating text display
        if (world != null && plugin.getDisplayManager() != null) {
            plugin.getDisplayManager().createDisplay(world, position, signData.getLines());
        }

        logger.info("Sign saved at " + worldName + ":" + position);
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        // Only save on dismiss (ESC) if we haven't already saved via save button
        if (!alreadySaved) {
            parseAndSaveText(currentText);
        }
        super.onDismiss(playerRef, store);
    }

    /**
     * Event data class for sign edit events.
     * Note: KeyedCodec requires keys to start with uppercase.
     */
    public static class SignEditEventData {
        public String action = "";
        public String value = "";

        public SignEditEventData() {}

        public static final BuilderCodec<SignEditEventData> CODEC = BuilderCodec
            .builder(SignEditEventData.class, SignEditEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (d, v) -> d.action = v != null ? v : "",
                    d -> d.action)
            .add()
            .append(new KeyedCodec<>("@Value", Codec.STRING),
                    (d, v) -> d.value = v != null ? v : "",
                    d -> d.value)
            .add()
            .build();
    }
}
