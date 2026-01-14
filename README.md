# EasySigns

Floating text displays for Hytale servers! Create hovering text anywhere in your world.

---

## Quick Start

1. Download the latest `EasySigns.jar` from [Releases](../../releases)
2. Put it in your server's `mods` folder
3. Restart your server
4. Use `/sign Hello World!` to create your first sign

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

Stand where you want the sign and type:
```
/sign Welcome to my base!
```

For multiple lines, use `|` as a separator:
```
/sign Line 1|Line 2|Line 3|Line 4
```

### Editing Signs

Right-click an existing sign to open the editor UI. You can also edit via chat:
- Type each line and press enter
- Type `skip` to leave a line blank
- Type `done` to finish editing
- Type `cancel` to cancel without saving

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

---

## Permissions

| Permission | What it does |
|------------|--------------|
| `signs.use` | Create, edit, and remove signs |
| `signs.admin` | Admin access |

---

## Data Storage

Sign data is saved in `mods/cryptobench_EasySigns/signs.json` and persists across server restarts.

---

## License

MIT - Do whatever you want with it!
