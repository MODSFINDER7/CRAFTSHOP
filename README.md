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
- Pagination for larger screenshot collections
- Rename and delete controls
- Open-folder and refresh controls
- Collision-safe filenames when importing screenshots
- A bounded texture cache so browsing many screenshots does not retain every image texture in GPU memory

The original Minecraft screenshot is left untouched when a gallery copy is renamed or deleted.

## Milestone 2: Blender-inspired Studio interface

The gallery opens a dedicated Studio workspace containing:
- Top application bar and project controls
- Left-side tool shelf
- Central 3D viewport area with grid and axis indicators
- Scene Outliner
- Properties panel
- Timeline and frame ruler
- Status bar and project information

The workspace layout is in place. The viewport renderer and editor interactions are being implemented in Milestone 3.

## Milestone 3: Core 3D editor functionality

The current editor foundation includes:
- Editable scene objects with position, rotation, scale, visibility and lock state
- Wireframe cube primitives in the viewport
- Viewport selection and Outliner selection
- Move, rotate and uniform scale tools using viewport dragging
- Camera orbit, pan and zoom controls
- Add, duplicate and delete scene objects
- Undo/redo history (Ctrl+Z / Ctrl+Y)
- Keyboard tool shortcuts (G = Move, R = Rotate, S = Scale)
- Project save/load using editable `.craftshop` project files

The editor now includes a searchable vanilla asset picker for registered blocks and entities. Blocks are previewed through isolated Block Display entities and living entities through the current entity render-state pipeline. Each scene object stores its Minecraft registry ID, so projects remain editable and portable across sessions. The preview entities are never added to the live world entity list, and the editor does not place or modify world blocks. Player mannequin assets are available when registered by the target game version.

## Milestone 5: PNG viewport export

The Studio has an **Export PNG** action. It captures the current Studio viewport, crops away the surrounding editor panels, and saves the PNG to the operating system Pictures folder under `Pictures/CraftShop`. The editable `.craftshop` project remains separate from the exported image.

Export uses Minecraft's screenshot readback and NativeImage pipeline; it does not use raw OpenGL, keeping compatibility with the 26.2 rendering backend abstraction.

## Build and test policy

The project is under active development. Automated build workflows and formal testing will be added after all planned milestones have been implemented. Until then, changes are committed without running the build or tests.
