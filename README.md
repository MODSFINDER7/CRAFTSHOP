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
- Projects are saved as editable `.craftshop` files in `.minecraft/craftshop/projects`.

The 3D scene is independent from the live Minecraft world. Preview entities are created only for rendering and are never added to the live level; the editor does not place or modify live world blocks.

### Rendering limitation

The current scene data and button-based editing controls are separate from the exported PNG. A complete off-screen Minecraft scene renderer that composites the 3D scene into the imported PNG is still a development task. The 3D scene project remains editable in its own `.craftshop` file.

## Build and test policy

GitHub Actions builds the mod with Java 25 and Gradle. A successful automated build verifies compilation only; Minecraft client runtime testing still requires launching the mod in Minecraft Java 26.2 with Fabric.
