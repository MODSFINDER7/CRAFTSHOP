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

The workspace includes a separate **2D Image Editor** alongside the 3D Studio. The editor provides a white canvas, brush and eraser strokes, text placement, a colour palette, adjustable brush size, paginated layer selection, visibility, opacity and reorder controls, crop bounds, ink brightness adjustment, snapshot-based undo/redo, screenshot import from a selectable gallery image picker, grayscale/invert/sepia image filters, project-relative imported image assets, named JSON-based `.craftshop2d` projects with a project browser, and PNG export. It is a lightweight drawing workspace rather than a full Photoshop replacement: imported images are used as a canvas background and are not yet directly retouched with pixel-level filters, masks, selection transforms, or advanced typography.

## Milestone 3: Core 3D editor functionality

The current editor foundation includes:
- Editable scene objects with position, rotation, scale, visibility and lock state
- Wireframe cube primitives in the viewport
- Viewport selection and Outliner selection
- Move, rotate and uniform scale tools using viewport dragging
- Perspective camera positioned in the saved scene, with free-fly movement (select **Fly**, then use W/A/S/D and Q/E; hold Shift to move faster)
- Right-mouse drag to look around; Orbit, Pan and Zoom tools remain available
- Axis-constrained transforms: press X, Y or Z while using Move, Rotate or Scale; press the same axis again to return to free transform
- Add, duplicate, delete, hide/show and lock/unlock scene objects
- Undo/redo history (Ctrl+Z / Ctrl+Y)
- Keyboard tool shortcuts (G = Move, R = Rotate, S = Scale)
- Project save/load using editable `.craftshop` project files

## Milestone 4: Vanilla blocks and entities

The editor includes a searchable vanilla asset picker for registered blocks and entities. Blocks and primitive cubes are previewed through isolated Block Display entities; living entities use Minecraft's entity render-state pipeline. Scene objects are projected into a perspective viewport, depth ordered, and scaled by camera distance. Each scene object stores its Minecraft registry ID and transform, so projects remain editable and portable across sessions. Preview entities are never added to the live world entity list, and the editor does not place or modify world blocks.

**Rendering scope:** this milestone is a perspective scene editor with vanilla model previews, not yet a full replacement Minecraft world renderer with a dedicated off-screen 3D world, lighting pipeline, shadows, and physically correct occlusion between every object.

## Milestone 5: PNG viewport export

The Studio has an **Export PNG** action. It captures the current Studio viewport, crops away the surrounding editor panels, and saves the PNG to the operating system Pictures folder under `Pictures/CraftShop`. The editable `.craftshop` project remains separate from the exported image.

Export uses Minecraft's screenshot readback and NativeImage pipeline; it does not use raw OpenGL, keeping compatibility with the 26.2 rendering backend abstraction.

## Build and test policy

A GitHub Actions workflow now runs a Java 25 / Gradle build on pushes and pull requests to `main`. The first automated build is the next verification gate; Minecraft client runtime testing still requires launching the mod in the target game version.
