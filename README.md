# CraftShop

CraftShop is a client-side creative studio for Minecraft Java Edition. The long-term goal is to combine a Photoshop-style 2D editor with a Blender-inspired 3D scene editor inside Minecraft.

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
- Image thumbnails and a larger selected-image preview
- Pagination
- Rename and delete controls
- Open-folder and refresh controls

The original Minecraft screenshot is left untouched when a gallery copy is renamed or deleted.

## Build status

The project is under active development. Automated build workflows and formal testing will be added after Milestone 1's feature implementation is complete, as planned.
