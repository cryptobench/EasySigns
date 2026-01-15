# EasySigns

> **Built for the European Hytale survival server at `play.hyfyve.net`**

Floating text displays for Hytale servers!

As of the first Hytale release, the game does not support writing text directly on sign blocks. This plugin provides a workaround by spawning invisible entities with nameplates, allowing you to place readable text anywhere in the world that other players can see.

---

## Quick Start

1. Download the latest `EasySigns.jar` from [Releases](../../releases)
2. Put it in your server's `mods` folder
3. Restart your server
4. Use `/sign Hello World!` to create your first floating text

---

## Commands

### Player Commands

| Command | What it does |
|---------|--------------|
| `/sign <text>` | Create floating text at your position |
| `/sign list` | List your signs with their unique IDs |
| `/sign edit <id> <text>` | Edit a sign's text (keeps same location) |
| `/sign delete <id>` | Delete your sign by its ID |
| `/sign remove` | Remove the nearest sign you own (within 5 blocks) |
| `/sign status` | Show plugin status |
| `/sign help` | Show all commands |

### Admin Commands (requires `signs.admin`)

| Command | What it does |
|---------|--------------|
| `/sign listall` | List ALL signs from all players with owner info |
| `/sign deleteany <id>` | Delete any sign by ID (bypass ownership) |
| `/sign purge <player>` | Delete ALL signs from a specific player |
| `/sign reload` | Reload config (banned words list) |

---

## How to Use

### Creating Signs

Stand where you want the text and type:
```
/sign Welcome to my base!
```

Use `|` to manually split lines:
```
/sign Line 1|Line 2|Line 3
```

Long text is automatically wrapped across multiple lines (max 32 characters per line, up to 4 lines).

### Editing Signs

List your signs to get the ID, then edit:
```
/sign list
/sign edit a1b2c3d4 New text here
```

The sign stays at the same location - only the text is replaced.

### Removing Signs

**By proximity:** Stand near your sign and use:
```
/sign remove
```

**By ID:** List your signs, then delete by ID:
```
/sign list
/sign delete a1b2c3d4
```

> Note: Sign IDs are stable 8-character strings that don't change when other signs are deleted.

### Sign Ownership

- Each sign tracks who created it
- You can only edit/delete your own signs
- Admins with `signs.admin` can manage anyone's signs

---

## Permissions

| Permission | What it does |
|------------|--------------|
| `signs.use` | Create, edit, and delete your own signs |
| `signs.admin` | List/delete anyone's signs, purge players, reload config |

Grant permissions via server console:
```
perm group add Adventure signs.use
perm group add Moderator signs.admin
```

---

## Configuration

Config file: `mods/cryptobench_EasySigns/config.json`

```json
{
  "filterEnabled": true,
  "bannedWords": ["badword1", "badword2"],
  "filterMessage": "Your sign contains inappropriate content.",
  "notifyAdmins": true
}
```

- **filterEnabled** - Enable/disable bad word filtering
- **bannedWords** - List of words to block (case insensitive)
- **filterMessage** - Message shown when sign is rejected
- **notifyAdmins** - Log attempts to use banned words

Use `/sign reload` to apply config changes without restart.

---

## How It Works

Since Hytale doesn't have native sign text support, this plugin:
1. Spawns invisible entities at your location
2. Attaches nameplate components to display text
3. Creates one entity per line, stacked vertically
4. Persists sign data to `mods/cryptobench_EasySigns/signs.json`

---

## License

MIT - Do whatever you want with it!
