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

| Command | What it does |
|---------|--------------|
| `/sign <text>` | Create floating text at your position |
| `/sign list` | List all signs with IDs |
| `/sign delete <id>` | Delete a sign by its ID |
| `/sign remove` | Remove the nearest sign (within 5 blocks) |
| `/sign status` | Show plugin status |
| `/sign help` | Show all commands |

---

## How to Use

### Creating Signs

Stand where you want the text and type:
```
/sign Welcome to my base!
```

Long text is automatically wrapped across multiple lines (max 32 characters per line, up to 4 lines).

### Removing Signs

**By proximity:** Stand near a sign and use:
```
/sign remove
```

**By ID:** List all signs, then delete by number:
```
/sign list
/sign delete 3
```

### Editing Signs

To edit a sign, remove it and create a new one:
```
/sign remove
/sign New text here
```

---

## Permissions

| Permission | What it does |
|------------|--------------|
| `signs.use` | Create, edit, and remove signs |
| `signs.admin` | Admin access |

---

## How It Works

NSince Hytale doesn't have native sign text support, this plugin:
1. Spawns invisible entities at your location
2. Attaches nameplate components to display text
3. Creates one entity per line, stacked vertically
4. Persists sign data to `mods/cryptobench_EasySigns/signs.json`

---

## License

MIT - Do whatever you want with it!
