# CraftShop

CraftShop is a client-side creative studio for Minecraft Java Edition. It combines a 2D PNG editor with a separate editable 3D scene project.

## Development target

- Minecraft Java Edition 26.2
- Fabric
- Java 25
- Client-side only

## Screenshot system and gallery

### Controls

| Key | Action |
| --- | --- |
| Z | Capture a screenshot while in a world |
| O | Open the CraftShop screenshot gallery while in a world |

Both key mappings can be changed in **Options → Controls → Key Binds → CraftShop**.

Screenshots are copied into `.minecraft/craftshop/screenshots`. The gallery supports cached thumbnails, full-size preview, pagination, rename, delete, open-folder and refresh controls. Original Minecraft screenshots are left untouched when a gallery copy is renamed or deleted.

Arbitrary PNG files can be placed in `.minecraft/craftshop/canvas/imports` and are available in the 2D image picker.

## 2D PNG editor

The 2D editor works with an imported PNG canvas and supports brush and eraser strokes, text placement, line/rectangle/ellipse tools, a rectangular Select tool with move and resize handles for strokes, shapes and text on the active layer, colour and brush-size controls, layers, visibility, opacity, layer reordering, crop bounds, ink brightness, undo/redo, grayscale/invert/sepia filters, project-relative image assets, named `.craftshop2d` projects, a project browser and full-resolution PNG export.

To transform content, choose **Select** and drag a rectangle over the active layer. Drag inside the rectangle to move the selected points, shapes and text; drag the lower-right handle to resize them. The selection is an editing aid and is not included in exported PNGs.

It is a lightweight drawing workspace, not a full Photoshop replacement. Advanced masks, non-destructive pixel selections and advanced typography controls are not included yet.

## Button-based 3D scene editor

The 3D Studio is intentionally **not** a Blender-style viewport layout. There is no large central 3D viewport, no scene grid, and no timeline in this interface. Editing is controlled through dedicated buttons and an object list.

- **Place Block** opens a searchable picker of registered Minecraft blocks.
- **Spawn Mob** opens a searchable picker of registered Minecraft entities.
- **Add Cube** creates a primitive scene object.
- Scene objects are selected from the Scene Objects list.
- Move buttons adjust X/Y/Z position in 0.25-block increments.
- Rotate buttons adjust X/Y/Z rotation in 15-degree increments.
- Scale buttons adjust all axes uniformly.
- Duplicate, Delete, Hide/Show and Lock/Unlock operate on the selected object.
- Undo/Redo supports up to 80 recorded changes; Ctrl+Z, Ctrl+Y and Ctrl+D are also supported.
- Spectator-camera position controls are available as buttons.
- **AO: ON/OFF** toggles screen-space contact shading. **Light-/Light+** rotates the key light horizontally, while **Amb-/Amb+** adjusts ambient intensity; these settings are stored in the project.
- **Preview** opens the fixed PNG composition without changing the editing layout. Drag to look around, use WASD to move, Space/C to move vertically, and the mouse wheel to zoom. Escape returns to the Studio; the preview camera changes remain in the current project.
- Projects are saved as editable `.craftshop` files in `.minecraft/craftshop/projects`.

The scene project data is separate from the live Minecraft world: editing objects never places or modifies live world blocks. The preview uses a standalone CPU rasterizer with its own colour and depth buffers; it does not borrow or modify the loaded level. This is an isolated 3D preview, not yet a second Minecraft world backed by `ClientLevel`, chunk storage, `LevelExtractor`/`LevelRenderer`, or Minecraft's off-screen render pipeline.

Each `.craftshop` project stores its editable base PNG, scene objects, spectator camera and last rendered-output reference. The Render button creates a temporary render-only composition screen: the original PNG remains the fixed background, while Minecraft block/entity preview models are projected using the saved camera and object transforms. The result is captured at the source PNG dimensions and written into `.minecraft/craftshop/screenshots`, where it appears in the gallery. The original base PNG is preserved so repeated renders do not stack duplicate objects.

Opening a selected gallery screenshot in the 2D editor or 3D Studio uses that selected image. Standalone 2D exports are added to the gallery; 2D exports from the 3D Studio replace the project's base PNG and clear the stale render reference.

### Rendering scope

The standalone CPU preview has its own colour and depth buffers, perspective projection, camera controls, object transforms, hit-testing and selection outlines. Its frame resolution scales with the canvas up to 1024 pixels on the longest edge and is cached until the camera, selection or scene changes. For block assets it reads the registered default BlockState and baked BlockStateModel parts/quads from Minecraft's model manager, transforms their actual geometry, decodes per-vertex UV coordinates and samples the baked texture-atlas sprite pixels. Cutout alpha is respected. The studio has an adjustable directional key light (horizontal direction and ambient intensity), and the optional **AO: ON/OFF** control applies screen-space contact shading from the completed depth buffer. These settings are saved with each .craftshop project. It does not yet apply biome/block tint providers, animated texture frames, physically based materials, or true cast shadows. Entity objects have CPU proxy geometry for scene depth and selection; when a live Minecraft client level is available, their vanilla entity render states are also drawn as GUI previews. These entity previews are not yet part of the CPU depth buffer, so complex block/entity intersections can still differ from a unified world renderer. This is an isolated scene compositor, not a second simulated Minecraft world: it does not yet own a ClientLevel, chunk/section storage, LevelExtractor/LevelRenderer pipeline, physics, or entity simulation. Fabric 26.2 uses extraction/render-state APIs and Blaze3D-compatible rendering rather than raw OpenGL. The 3D Studio editing interface remains button-based, with no central viewport, grid or timeline.

## Build and test policy

GitHub Actions uses Java 25 and Gradle 9.5.1. It runs `gradle --no-daemon clean test build` before uploading the mod JAR.

The JUnit suite currently covers the project-owned scene index: 16-block chunk and vertical-section addressing, negative coordinates, block/entity classification, null-entry handling, hidden-object filtering, camera-facing culling, cache reuse, and cache invalidation when objects cross chunk boundaries or the project collection changes.

Automated tests and a successful build do not replace launching the mod in Minecraft Java 26.2 with Fabric. Runtime interaction, resource reloads, large-scene performance, and visual correctness still require in-game verification.
