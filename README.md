# CraftShop

CraftShop is a client-side creative studio for Minecraft Java Edition. The goal is to combine a Photoshop-style 2D editor with a Blender-inspired 3D scene editor inside Minecraft.

## Development target

- Minecraft Java Edition 26.2
- Fabric
- Java 25
- Client-side only

## Milestone 1: Screenshot system and gallery

### Controls

| Key | Action |
| --- | --- |
| Z | Capture a screenshot while in a world |
| O | Open the CraftShop screenshot gallery while in a world |

Both key mappings can be changed in **Options → Controls → Key Binds → CraftShop**.

### Storage

Minecraft's screenshot capture is used, then new PNG files are copied into:

`.minecraft/craftshop/screenshots`

The gallery includes:
- Cached, downscaled thumbnails and a full-size selected-image preview
- Pagination
- Rename and delete controls
- Open-folder and refresh controls

The original Minecraft screenshot is left untouched when a gallery copy is renamed or deleted.

## Milestone 2: 3D Studio interface

The gallery includes an entry point to a Blender-inspired Studio interface. The current interface shell contains:
- A left-side tool shelf
- A central viewport area
- Scene outliner and properties panels
- A timeline area
- Select, move, rotate, scale, orbit, pan and zoom tool selectors

The viewport and transform tools are interface placeholders at this stage. Actual scene rendering and manipulation are planned for Milestone 3. No real Minecraft world blocks or entities are modified by the Studio.

## Build and test policy

The project is under active development. Automated build workflows and formal testing will be added after all planned milestones have been implemented. Until then, changes are committed without running the build or tests.
