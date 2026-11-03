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

The 2D editor works with an imported PNG canvas and supports brush and eraser strokes, text placement, line/rectangle/ellipse tools, colour and brush-size controls, layers, visibility, opacity, layer reordering, crop bounds, ink brightness, undo/redo, grayscale/invert/sepia filters, project-relative image assets, named `.craftshop2d` projects, a project browser and full-resolution PNG export.

It is a lightweight drawing workspace, not a full Photoshop replacement. Advanced masks, selection transforms and advanced typography are not included yet.

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
- **Preview** opens the fixed PNG composition without changing the editing layout. Drag to look around, use WASD to move, Space/C to move vertically, and the mouse wheel to zoom. Escape returns to the Studio; the preview camera changes remain in the current project.
- Projects are saved as editable `.craftshop` files in `.minecraft/craftshop/projects`.

The scene project data is separate from the live Minecraft world: editing objects never places or modifies live world blocks. The scene data is independent of the live Minecraft world. The preview uses a standalone CPU rasterizer with its own colour and depth buffers; it does not borrow or modify the loaded level. This is an isolated 3D preview, not yet a second Minecraft world backed by `ClientLevel`, chunk storage, `LevelExtractor`/`LevelRenderer`, or Minecraft's off-screen render pipeline.

Each `.craftshop` project stores its editable base PNG, scene objects, spectator camera and last rendered-output reference. The Render button creates a temporary render-only composition screen: the original PNG remains the fixed background, while Minecraft block/entity preview models are projected using the saved camera and object transforms. The result is captured at the source PNG dimensions and written into `.minecraft/craftshop/screenshots`, where it appears in the gallery. The original base PNG is preserved so repeated renders do not stack duplicate objects.

Opening a selected gallery screenshot in the 2D editor or 3D Studio uses that selected image. Standalone 2D exports are added to the gallery; 2D exports from the 3D Studio replace the project's base PNG and clear the stale render reference.

### Rendering scope

The current CPU preview rasterizes cube geometry and simplified low-poly entity placeholders with perspective projection, depth testing, object transforms and camera zoom. It does not yet load Minecraft blockstate model JSON, atlas textures, vanilla entity renderer models, or entity animations. It is not a full Minecraft world renderer: there is no chunk/section pipeline, terrain physics, dynamic scene lighting, block/entity interaction simulation, or cast-shadow pass. Fabric 26.2 separates level extraction from drawing and requires Blaze3D-compatible rendering rather than raw OpenGL; registering ordinary LevelRenderEvents only adds geometry to the currently rendered level and does not create a second world. The 3D Studio editing interface remains button-based, with no central viewport, grid or timeline.

## Build and test policy

GitHub Actions builds the mod with Java 25 and Gradle. A successful automated build verifies compilation only; Minecraft client runtime testing still requires launching the mod in Minecraft Java 26.2 with Fabric.
