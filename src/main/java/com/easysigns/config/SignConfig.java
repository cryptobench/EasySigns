package com.easysigns.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Configuration for EasySigns plugin.
 * Handles banned words filtering and other settings.
 */
public class SignConfig {
    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config values
    private boolean filterEnabled = true;
    private List<String> bannedWords = new ArrayList<>();
    private String filterMessage = "Your sign contains inappropriate content.";
    private boolean notifyAdmins = true;

    // Transient (not saved)
    private transient Path dataDirectory;
    private transient Logger logger;
    private transient List<Pattern> bannedPatterns;

    public SignConfig() {
        // Default banned words - common profanity, slurs, and obfuscations
        // Server admins can customize this list in config.json

        // F-word variants
        bannedWords.add("fuck");
        bannedWords.add("f u c k");
        bannedWords.add("fck");
        bannedWords.add("fuk");
        bannedWords.add("fuck");
        bannedWords.add("fvck");
        bannedWords.add("f*ck");
        bannedWords.add("fu*k");
        bannedWords.add("f**k");
        bannedWords.add("phuck");
        bannedWords.add("phuk");
        bannedWords.add("fück");
        bannedWords.add("fucker");
        bannedWords.add("fucking");
        bannedWords.add("fuckin");
        bannedWords.add("fkin");
        bannedWords.add("fkn");

        // S-word variants
        bannedWords.add("shit");
        bannedWords.add("s h i t");
        bannedWords.add("sh1t");
        bannedWords.add("sht");
        bannedWords.add("sh!t");
        bannedWords.add("s#it");
        bannedWords.add("shlt");
        bannedWords.add("shiit");
        bannedWords.add("shitty");
        bannedWords.add("bullshit");

        // B-word variants
        bannedWords.add("bitch");
        bannedWords.add("b1tch");
        bannedWords.add("b!tch");
        bannedWords.add("biatch");
        bannedWords.add("bytch");
        bannedWords.add("b i t c h");

        // A-word variants
        bannedWords.add("ass");
        bannedWords.add("a55");
        bannedWords.add("a s s");
        bannedWords.add("asshole");
        bannedWords.add("a55hole");
        bannedWords.add("assh0le");
        bannedWords.add("azzhole");

        // C-word variants
        bannedWords.add("cunt");
        bannedWords.add("c u n t");
        bannedWords.add("cvnt");
        bannedWords.add("c*nt");

        // D-word variants
        bannedWords.add("dick");
        bannedWords.add("d1ck");
        bannedWords.add("d!ck");
        bannedWords.add("d i c k");
        bannedWords.add("cock");
        bannedWords.add("c0ck");
        bannedWords.add("c o c k");

        // P-word variants
        bannedWords.add("pussy");
        bannedWords.add("pu55y");
        bannedWords.add("pus5y");
        bannedWords.add("p u s s y");
        bannedWords.add("pussies");

        // Slurs and hate speech
        bannedWords.add("whore");
        bannedWords.add("wh0re");
        bannedWords.add("slut");
        bannedWords.add("s1ut");
        bannedWords.add("fag");
        bannedWords.add("f4g");
        bannedWords.add("faggot");
        bannedWords.add("f4ggot");
        bannedWords.add("fagg0t");
        bannedWords.add("nigger");
        bannedWords.add("n1gger");
        bannedWords.add("nigg3r");
        bannedWords.add("n i g g e r");
        bannedWords.add("nigga");
        bannedWords.add("n1gga");
        bannedWords.add("nigg4");
        bannedWords.add("niga");
        bannedWords.add("negro");
        bannedWords.add("retard");
        bannedWords.add("r3tard");
        bannedWords.add("retarded");
        bannedWords.add("tard");
        bannedWords.add("spic");
        bannedWords.add("sp1c");
        bannedWords.add("chink");
        bannedWords.add("ch1nk");
        bannedWords.add("kike");
        bannedWords.add("k1ke");
        bannedWords.add("gook");
        bannedWords.add("g00k");
        bannedWords.add("wetback");
        bannedWords.add("beaner");
        bannedWords.add("tranny");
        bannedWords.add("shemale");

        // Violence/harmful
        bannedWords.add("kys");
        bannedWords.add("k y s");
        bannedWords.add("kill yourself");
        bannedWords.add("killyourself");
        bannedWords.add("kms");
        bannedWords.add("kill myself");
        bannedWords.add("suicide");
        bannedWords.add("hang yourself");
        bannedWords.add("neck yourself");
        bannedWords.add("die");
        bannedWords.add("go die");
        bannedWords.add("hope you die");
        bannedWords.add("get cancer");
        bannedWords.add("get aids");

        // Other profanity
        bannedWords.add("bastard");
        bannedWords.add("b4stard");
        bannedWords.add("damn");
        bannedWords.add("d4mn");
        bannedWords.add("crap");
        bannedWords.add("piss");
        bannedWords.add("p1ss");
        bannedWords.add("pissed");
        bannedWords.add("wanker");
        bannedWords.add("w4nker");
        bannedWords.add("twat");
        bannedWords.add("tw4t");
        bannedWords.add("douche");
        bannedWords.add("d0uche");
        bannedWords.add("douchebag");

        // Sexual content
        bannedWords.add("porn");
        bannedWords.add("p0rn");
        bannedWords.add("sex");
        bannedWords.add("s3x");
        bannedWords.add("sexy");
        bannedWords.add("cum");
        bannedWords.add("c u m");
        bannedWords.add("jizz");
        bannedWords.add("j1zz");
        bannedWords.add("dildo");
        bannedWords.add("d1ldo");
        bannedWords.add("penis");
        bannedWords.add("pen1s");
        bannedWords.add("vagina");
        bannedWords.add("vag1na");
        bannedWords.add("boobs");
        bannedWords.add("b00bs");
        bannedWords.add("tits");
        bannedWords.add("t1ts");
        bannedWords.add("titties");
        bannedWords.add("nude");
        bannedWords.add("nudes");
        bannedWords.add("naked");
        bannedWords.add("hentai");
        bannedWords.add("h3ntai");

        // Nazi/extremist
        bannedWords.add("nazi");
        bannedWords.add("n4zi");
        bannedWords.add("hitler");
        bannedWords.add("h1tler");
        bannedWords.add("heil");
        bannedWords.add("h3il");
        bannedWords.add("swastika");
        bannedWords.add("kkk");
        bannedWords.add("white power");
        bannedWords.add("white pride");
        bannedWords.add("1488");
        bannedWords.add("88");
    }

    /**
     * Initialize the config with data directory and logger.
     */
    public void init(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        load();
        compilePatterns();
    }

    /**
     * Check if text contains banned words.
     * Returns the matched word if found, null otherwise.
     */
    public String checkForBannedWords(String text) {
        if (!filterEnabled || text == null || bannedPatterns == null) {
            return null;
        }

        // Patterns are compiled with CASE_INSENSITIVE flag, so toLowerCase() is redundant
        for (int i = 0; i < bannedPatterns.size(); i++) {
            if (bannedPatterns.get(i).matcher(text).find()) {
                return bannedWords.get(i);
            }
        }
        return null;
    }

    /**
     * Check multiple lines for banned words.
     * Returns the matched word if found, null otherwise.
     */
    public String checkLinesForBannedWords(String[] lines) {
        if (lines == null) return null;
        for (String line : lines) {
            String match = checkForBannedWords(line);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * Compile banned words into regex patterns for efficient matching.
     */
    private void compilePatterns() {
        bannedPatterns = new ArrayList<>();
        for (String word : bannedWords) {
            // Match word boundaries, case insensitive
            String pattern = "\\b" + Pattern.quote(word.toLowerCase()) + "\\b";
            bannedPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        logger.info("Compiled " + bannedPatterns.size() + " banned word patterns");
    }

    /**
     * Load config from file.
     */
    private void load() {
        Path file = dataDirectory.resolve(CONFIG_FILE);
        if (Files.exists(file)) {
            try {
                String json = Files.readString(file);
                SignConfig loaded = GSON.fromJson(json, SignConfig.class);
                if (loaded != null) {
                    this.filterEnabled = loaded.filterEnabled;
                    this.bannedWords = loaded.bannedWords != null ? loaded.bannedWords : new ArrayList<>();
                    this.filterMessage = loaded.filterMessage;
                    this.notifyAdmins = loaded.notifyAdmins;
                }
                logger.info("Loaded config with " + bannedWords.size() + " banned words");
            } catch (IOException e) {
                logger.warning("Failed to load config: " + e.getMessage());
            }
        } else {
            // Save default config
            save();
            logger.info("Created default config file");
        }
    }

    /**
     * Save config to file.
     */
    public void save() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve(CONFIG_FILE);
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            logger.warning("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Reload config from file.
     */
    public void reload() {
        load();
        compilePatterns();
    }

    // Getters
    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public String getFilterMessage() {
        return filterMessage;
    }

    public boolean shouldNotifyAdmins() {
        return notifyAdmins;
    }

    public List<String> getBannedWords() {
        return new ArrayList<>(bannedWords);
    }
}
