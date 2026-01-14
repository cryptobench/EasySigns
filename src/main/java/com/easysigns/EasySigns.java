package com.easysigns;

import com.easysigns.command.SignCommand;
import com.easysigns.data.SignStorage;
import com.easysigns.listener.SignChatListener;
import com.easysigns.listener.SignInteractionListener;
import com.easysigns.sign.SignDisplayEntity;
import com.easysigns.sign.SignDisplayManager;
import com.easysigns.systems.SignPlaceSystem;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.logging.Logger;

/**
 * EasySigns - Typeable signs for Hytale servers
 *
 * Place a sign block to open the editor, or right-click existing signs to edit.
 * Enter text with lines separated by | (e.g., "Line1|Line2|Line3|Line4")
 */
public class EasySigns extends JavaPlugin {

    private static EasySigns instance;
    private Logger logger;
    private SignStorage signStorage;
    private SignDisplayManager displayManager;
    private SignInteractionListener interactionListener;
    private SignChatListener chatListener;
    private SignPlaceSystem signPlaceSystem;

    public EasySigns(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    public void setup() {
        this.logger = Logger.getLogger("EasySigns");
        logger.info("========== EASYSIGNS STARTING ==========");

        // Register custom entity type for floating text displays
        getEntityRegistry().registerEntity(
            "easysigns:sign_display",
            SignDisplayEntity.class,
            SignDisplayEntity::new,
            SignDisplayEntity.CODEC
        );
        logger.info("Registered sign display entity type");

        // Initialize sign storage
        this.signStorage = new SignStorage(getDataDirectory(), logger);

        // Initialize display manager for floating text
        this.displayManager = new SignDisplayManager(this);

        // Register commands
        getCommandRegistry().registerCommand(new SignCommand(this, signStorage));

        // Register interaction listener for editing existing signs
        this.interactionListener = new SignInteractionListener(this, signStorage);
        interactionListener.register(getEventRegistry());

        // Register chat listener for sign text input
        this.chatListener = new SignChatListener(this, signStorage);
        chatListener.register(getEventRegistry());

        // Register block place system to detect sign placements
        this.signPlaceSystem = new SignPlaceSystem(this, signStorage);
        getEntityStoreRegistry().registerSystem(signPlaceSystem);

        logger.info("EasySigns setup complete!");
    }

    @Override
    public void start() {
        logger.info("========== EASYSIGNS STARTED ==========");
        logger.info("Signs loaded: " + signStorage.getSignCount());
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down EasySigns...");
        if (displayManager != null) {
            displayManager.cleanup();
        }
        if (signStorage != null) {
            signStorage.save();
        }
        logger.info("EasySigns shut down.");
    }

    public static EasySigns getInstance() {
        return instance;
    }

    public SignStorage getSignStorage() {
        return signStorage;
    }

    public SignDisplayManager getDisplayManager() {
        return displayManager;
    }

    public Logger getPluginLogger() {
        return logger;
    }
}
