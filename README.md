# AreaScanner

A powerful Minecraft mod for scanning and locating blocks within defined areas - perfect for preparation before running World Eaters or large-scale terrain modification projects.

## ✨ Features

- **Flexible Area Selection**: Define scan zones of any size, including unloaded chunks
- **Advanced Block Filtering**: Use whitelist conditions to find specific blocks based on:
    - Block type
    - Blast resistance
    - Piston behavior
    - Waterlogged state
- **Pre-built Templates**: Includes ready-to-use templates for World Eater setups and other common scenarios
- **Dual Mode Operation**:
    - **Client-side**: Scan loaded chunks (visualized with yellow rectangles)
    - **Server-side**: Scan generated chunks
- **Performance Optimized**: Efficient scanning algorithm minimizes lag

## 🎯 Use Cases

- Clear terrain before deploying World Eaters
- Locate specific block types in large areas
- Identify problematic blocks in build areas

## 📦 Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib](https://modrinth.com/mod/malilib)

## 📖 Documentation

Comprehensive documentation is available in our [Wiki](https://github.com/Alexresh/AreaScanner/wiki).

> ⚠️ **Note**: The wiki is currently under development. Check back regularly for updates!

## 💡 How It Works

"alt + ]" = open config screen

### Client-Side Scanning
- Scan your area using command or ui
- Yellow rectangles indicate chunks that need to be loaded (colors are customizable)
- All selected chunks must be loaded for complete scanning
- Results are displayed in real-time (3 LOD levels and position changes for better visibility)

### Server-Side Scanning
- Only requires chunks to be generated
- Scans are published for all players. Players can subscribe to them to see all extra blocks in real time.

## 🛠️ Whitelist Configuration

Create custom filters by specifying:
- Specific block IDs
- Block properties (blast resistance, piston behavior(normal, immovable, destroy), waterlogged)
- Use predefined templates for quick setup


---

Made with 🥀 for the Minecraft community
