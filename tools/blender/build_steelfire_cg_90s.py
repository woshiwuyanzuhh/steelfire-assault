"""Build the 90 second SteelFire Assault Blender cinematic.

The scene is authored from Blender primitives and procedural materials so the
.blend remains editable. It is an original industrial science-fiction setting:
a six-wheel recovery rover, a flooded maintenance hangar, an operator and a
reactor. No third-party meshes or game assets are loaded by this file.

Run headless with Blender 5.x:
    blender --background --python tools/blender/build_steelfire_cg_90s.py

This produces an engineering file plus a PNG frame sequence. The frame
sequence is encoded to H.264 by the separate offline encode step.
"""

import bpy
import json
import math
import os
from mathutils import Vector

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_BLEND = os.path.join(ROOT, "art", "blender", "steelfire_cg_scene.blend")
FRAME_DIR = os.path.join(ROOT, "dist", "cg", "blender_frames")
SHOT_MANIFEST = os.path.join(ROOT, "dist", "cg", "steelfire_cg_shots.json")
os.makedirs(FRAME_DIR, exist_ok=True)

FPS = 24
DURATION_SECONDS = 90
TOTAL_FRAMES = FPS * DURATION_SECONDS


def material(name, color, metallic=0.0, roughness=0.5, emission=None, emission_strength=0.0, noise=False):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nodes = m.node_tree.nodes
    links = m.node_tree.links
    bsdf = nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = roughness
    if emission:
        bsdf.inputs["Emission Color"].default_value = (*emission, 1.0)
        bsdf.inputs["Emission Strength"].default_value = emission_strength
    if noise:
        tex = nodes.new("ShaderNodeTexNoise")
        tex.inputs["Scale"].default_value = 8.0
        tex.inputs["Detail"].default_value = 4.0
        tex.inputs["Roughness"].default_value = 0.72
        bump = nodes.new("ShaderNodeBump")
        bump.inputs["Strength"].default_value = 0.16
        bump.inputs["Distance"].default_value = 0.08
        links.new(tex.outputs["Fac"], bump.inputs["Height"])
        links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return m


def box(name, location, scale, mat, bevel=0.04, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if mat:
        obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new("machined_edge_bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 3
    return obj


def cylinder(name, location, radius, depth, mat, rotation=(0, 0, 0), vertices=32, bevel=0.02):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    if mat:
        obj.data.materials.append(mat)
    if bevel:
        mod = obj.modifiers.new("edge_radius", "BEVEL")
        mod.width = bevel
        mod.segments = 2
    return obj


def sphere(name, location, radius, mat, segments=32, rings=16):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, radius=radius, location=location)
    obj = bpy.context.object
    obj.name = name
    if mat:
        obj.data.materials.append(mat)
    return obj


def torus(name, location, major, minor, mat, rotation=(0, 0, 0)):
    bpy.ops.mesh.primitive_torus_add(major_radius=major, minor_radius=minor, major_segments=48, minor_segments=16, location=location, rotation=rotation)
    obj = bpy.context.object
    obj.name = name
    if mat:
        obj.data.materials.append(mat)
    return obj


def wedge(name, location, length, width, height, mat, slope=0.35, rotation=(0, 0, 0)):
    """A beveled trapezoid plate used for sloped armor and cockpit shells."""
    x0, x1 = -length * 0.5, length * 0.5
    z0, z1 = -height * 0.5, height * 0.5
    verts = [
        (x0, -width * 0.5, z0), (x1, -width * 0.5, z0),
        (x1, width * 0.5, z0), (x0, width * 0.5, z0),
        (x0 + slope * length, -width * 0.5, z1), (x1, -width * 0.5, z1),
        (x1, width * 0.5, z1), (x0 + slope * length, width * 0.5, z1),
    ]
    faces = [(0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (4, 0, 3, 7)]
    mesh = bpy.data.meshes.new(f"{name}_Mesh")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.location = location
    obj.rotation_euler = rotation
    if mat:
        obj.data.materials.append(mat)
    bevel = obj.modifiers.new("wedge_edge_bevel", "BEVEL")
    bevel.width = min(0.16, height * 0.16)
    bevel.segments = 4
    return obj


def look_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def area_light(name, location, energy, color, size, target=(0, 0, 2)):
    bpy.ops.object.light_add(type="AREA", location=location)
    light = bpy.context.object
    light.name = name
    light.data.energy = energy
    light.data.color = color
    light.data.shape = "DISK"
    light.data.size = size
    look_at(light, target)
    return light


# Reset and render settings.
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = "BLENDER_EEVEE"
scene.render.resolution_x = 960
scene.render.resolution_y = 540
scene.render.resolution_percentage = 100
scene.render.fps = FPS
scene.frame_start = 1
scene.frame_end = TOTAL_FRAMES
scene.render.filepath = os.path.join(FRAME_DIR, "frame_")
scene.render.image_settings.file_format = "PNG"
scene.render.film_transparent = False
scene.render.use_file_extension = True
scene.render.image_settings.color_mode = "RGB"
scene.view_settings.look = "AgX - Medium High Contrast"

world = bpy.data.worlds.new("Steelfire Industrial World")
scene.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.0015, 0.004, 0.008, 1.0)
world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.5
volume = world.node_tree.nodes.new("ShaderNodeVolumePrincipled")
volume.inputs["Density"].default_value = 0.004
volume.inputs["Color"].default_value = (0.04, 0.08, 0.10, 1.0)
volume.inputs["Anisotropy"].default_value = 0.32
world.node_tree.links.new(volume.outputs["Volume"], world.node_tree.nodes["World Output"].inputs["Volume"])
scene.view_settings.exposure = 1.15

# Palette.
armor = material("Armor teal ceramic", (0.075, 0.19, 0.21), 0.86, 0.27, noise=True)
armor_dark = material("Armor graphite", (0.022, 0.052, 0.065), 0.91, 0.22, noise=True)
armor_edge = material("Armor edge oxide", (0.34, 0.105, 0.032), 0.62, 0.4, noise=True)
rubber = material("Tire rubber", (0.003, 0.004, 0.005), 0.05, 0.76, noise=True)
glass = material("Smart glass", (0.008, 0.065, 0.075), 0.42, 0.12, (0.015, 0.35, 0.42), 3.0)
amber = material("Amber warning emissive", (0.32, 0.06, 0.008), 0.28, 0.25, (1.0, 0.12, 0.008), 8.0)
cyan = material("Reactor cyan emissive", (0.01, 0.15, 0.22), 0.14, 0.18, (0.04, 0.62, 1.0), 10.0)
cold = material("Cold white worklight", (0.35, 0.53, 0.60), 0.12, 0.18, (0.55, 0.86, 1.0), 6.0)
floor_mat = material("Wet basalt hangar floor", (0.012, 0.025, 0.031), 0.82, 0.14, noise=True)
human_suit = material("Operator composite suit", (0.02, 0.032, 0.044), 0.65, 0.35, noise=True)
decal = material("Stencilled safety marking", (0.54, 0.22, 0.028), 0.35, 0.33, (1.0, 0.18, 0.01), 2.2)
rubber_highlight = material("Tire sidewall highlight", (0.025, 0.035, 0.04), 0.18, 0.5, noise=True)
dust_material = material("Suspended dust", (0.18, 0.22, 0.22), 0.05, 0.95, (0.12, 0.20, 0.22), 0.35)

# Hangar shell, floor, bays, rails and service detail.
box("Hangar_Floor", (0, 0, -0.28), (40, 28, 0.5), floor_mat, 0.05)
for x in (-17, -11, -5, 5, 11, 17):
    box(f"Pillar_{x}", (x, 5.5, 6.4), (1.0, 1.5, 12.5), armor_dark, 0.12)
    for z in (1.8, 4.2, 6.6, 9.0, 11.3):
        box(f"Pillar_Signal_{x}_{z}", (x, 4.68, z), (0.14, 0.08, 0.58), amber, 0.01)
for z in (3.3, 6.8, 10.2):
    box(f"Crossbeam_{z}", (0, 5.5, z), (38, 1.0, 0.62), armor_dark, 0.12)
    for x in range(-16, 17, 4):
        box(f"Ceiling_Lamp_{x}_{z}", (x, 4.85, z - 0.35), (1.2, 0.18, 0.08), cold, 0.01)
for y in (-5.5, 5.5):
    box(f"Rail_{y}", (0, y, 0.03), (35, 0.2, 0.08), armor_edge, 0.02)
for x in range(-18, 19, 2):
    box(f"Floor_Seam_{x}", (x, 0, 0.015), (0.035, 25, 0.02), armor_dark, 0.005)
for x in (-14, -9, 9, 14):
    box(f"CableTray_{x}", (x, 3.95, 4.0), (0.42, 0.22, 7.5), armor_dark, 0.04)
    for z in [1.3 + i * 0.85 for i in range(9)]:
        cylinder(f"Cable_{x}_{z}", (x + 0.28, 3.78, z), 0.035, 1.15, amber, rotation=(math.radians(90), 0, 0), vertices=12, bevel=0.005)

# Large reactor assembly in the far bay.
box("Reactor_Pedestal", (0, 9.5, 1.25), (8.8, 3.8, 2.5), armor_dark, 0.18)
for radius, reactor_mat in ((3.6, armor_edge), (2.8, cyan), (1.9, armor_edge)):
    torus(f"Reactor_Ring_{radius}", (0, 9.0, 5.0), radius, 0.18 if radius > 2 else 0.12, reactor_mat, rotation=(math.radians(90), 0, 0))
core = sphere("Reactor_Core", (0, 9.0, 5.0), 1.35, cyan, segments=48, rings=24)
core.scale = (1.0, 0.35, 1.0)
for x in (-3.7, 3.7):
    box(f"Reactor_Support_{x}", (x, 9.0, 4.7), (0.5, 2.8, 5.2), armor_dark, 0.1)
    for z in (3.1, 4.3, 5.5, 6.7):
        box(f"Reactor_Support_Light_{x}_{z}", (x * 0.98, 7.55, z), (0.12, 0.06, 0.45), cyan, 0.01)

# Rover body and engineering detail.
box("Rover_Undercarriage", (0, 0, 1.05), (8.6, 3.8, 0.9), armor_dark, 0.18)
box("Rover_Main_Hull", (0, 0, 1.72), (8.0, 3.45, 1.65), armor, 0.24, rotation=(0, 0, math.radians(-2)))
box("Rover_Nose_Armor", (-4.0, -0.02, 1.95), (1.25, 3.55, 1.3), armor_edge, 0.18, rotation=(0, math.radians(-12), 0))
box("Rover_Cabin", (0.8, 0, 3.12), (4.0, 2.82, 2.1), armor, 0.2, rotation=(0, 0, math.radians(-2)))
for y in (-1.43, 1.43):
    box(f"Cabin_Glass_{y}", (0.55, y, 3.25), (2.45, 0.08, 0.9), glass, 0.035, rotation=(0, math.radians(6), math.radians(-2)))
    box(f"Cabin_Seal_{y}", (0.55, y * 1.01, 2.7), (2.55, 0.07, 0.12), armor_dark, 0.02)
wedge("Rover_Cabin_Roof_Slope", (0.85, 0, 4.12), 3.9, 2.88, 0.84, armor_dark, slope=0.12, rotation=(0, 0, math.radians(-2)))
for y in (-1.41, 1.41):
    box(f"Rover_Front_Windshield_{y}", (-0.72, y, 3.48), (1.05, 0.09, 0.76), glass, 0.04, rotation=(0, math.radians(-19), math.radians(-2)))
    box(f"Rover_Windshield_Frame_{y}", (-0.72, y * 1.02, 3.03), (1.18, 0.07, 0.08), armor_edge, 0.015, rotation=(0, math.radians(-19), 0))
for y in (-1.9, 1.9):
    wedge(f"Rover_Fender_{y}", (-2.45, y, 1.72), 2.2, 0.42, 0.48, armor_edge, slope=0.18, rotation=(0, 0, math.radians(-5)))
box("Rover_Nose_Bumper", (-4.55, 0, 1.42), (0.34, 3.35, 0.42), armor_dark, 0.1, rotation=(0, math.radians(-5), 0))
for y in (-1.15, 1.15):
    cylinder(f"Rover_Exhaust_{y}", (3.7, y, 2.35), 0.19, 1.1, armor_dark, rotation=(0, math.radians(90), 0), vertices=28, bevel=0.04)

# Six wheels with layered hubs, tread blocks and suspension arms.
for ix, x in enumerate((-3.0, 0.0, 3.0)):
    for side, y in enumerate((-1.95, 1.95)):
        cylinder(f"Rover_Wheel_{ix}_{side}", (x, y, 0.92), 0.92, 0.62, rubber, rotation=(math.radians(90), 0, 0), vertices=48, bevel=0.08)
        cylinder(f"Rover_Hub_{ix}_{side}", (x, y * 1.01, 0.92), 0.38, 0.68, armor_edge, rotation=(math.radians(90), 0, 0), vertices=32, bevel=0.04)
        torus(f"Rover_Tire_Ring_{ix}_{side}", (x, y * 1.03, 0.92), 0.76, 0.075, armor_dark, rotation=(math.radians(90), 0, 0))
        for a in range(8):
            angle = a * math.tau / 8.0
            tx = x + math.cos(angle) * 0.75
            tz = 0.92 + math.sin(angle) * 0.75
            box(f"Tread_{ix}_{side}_{a}", (tx, y, tz), (0.14, 0.67, 0.11), armor_dark, 0.015, rotation=(0, angle, 0))
        box(f"Suspension_{ix}_{side}", (x, y * 0.90, 1.55), (0.22, 1.8, 0.22), armor_edge, 0.03, rotation=(0, math.radians(18), 0))

# Armor ribs, fasteners, vents and service modules.
for x in (-3.2, -1.6, 0, 1.6, 3.2):
    for y in (-1.78, 1.78):
        box(f"Armor_Rib_{x}_{y}", (x, y, 1.93), (0.14, 0.13, 0.92), armor_edge, 0.02)
        cylinder(f"Armor_Bolt_{x}_{y}", (x, y * 1.01, 2.48), 0.075, 0.055, amber, rotation=(math.radians(90), 0, 0), vertices=16, bevel=0.01)
for i, x in enumerate((-1.6, -1.0, -0.4, 0.2, 0.8)):
    box(f"Rover_Vent_{i}", (x, -1.82, 3.3), (0.38, 0.08, 0.85), armor_dark, 0.015, rotation=(0, math.radians(10), 0))
for y in (-1.35, 1.35):
    box(f"Storage_Case_{y}", (-0.55, y, 2.7), (2.0, 0.42, 0.72), armor_edge, 0.08)
    for i in range(3):
        cylinder(f"Storage_Buckle_{y}_{i}", (-1.2 + i * 0.62, y * 1.02, 2.7), 0.075, 0.07, amber, rotation=(math.radians(90), 0, 0), vertices=12, bevel=0.01)
box("Rover_RoofRack", (0.7, 0, 4.35), (4.4, 3.0, 0.18), armor_dark, 0.04)
for x in (-1.0, 1.6):
    cylinder(f"Rack_Antenna_{x}", (x, 0, 4.9), 0.055, 1.05, armor, vertices=20, bevel=0.01)
    sphere(f"Rack_Antenna_Tip_{x}", (x, 0, 5.44), 0.09, cyan, segments=20, rings=10)
box("Rover_Turret_Base", (1.65, 0, 4.58), (1.8, 1.95, 0.55), armor_edge, 0.12)
cylinder("Rover_Turret", (1.75, 0, 5.05), 0.84, 0.48, armor_dark, vertices=40, bevel=0.07)
barrel = box("Rover_Cannon", (3.0, 0, 5.08), (2.8, 0.34, 0.34), armor, 0.09)
cylinder("Cannon_Muzzle", (4.42, 0, 5.08), 0.28, 0.42, armor_dark, rotation=(0, math.radians(90), 0), vertices=36, bevel=0.04)
for y in (-1.08, 1.08):
    box(f"Headlight_{y}", (-4.62, y, 2.36), (0.12, 0.52, 0.32), amber, 0.03)

# Close-up readable surface pass: removable plates, seams, cable glands,
# hydraulic rams and machine-readable stencils. These small layers are what
# keep the vehicle from reading as a single primitive in macro shots.
for x in (-3.65, -2.9, -1.95, -0.85, 0.35, 1.55, 2.6, 3.45):
    box(f"Hull_Panel_Seam_{x}", (x, -1.785, 2.05), (0.035, 0.028, 0.92), armor_dark, 0.006)
    box(f"Hull_Panel_Seam_R_{x}", (x, 1.785, 2.05), (0.035, 0.028, 0.92), armor_dark, 0.006)
for x in (-2.9, -1.1, 0.8, 2.6):
    box(f"Side_Armor_Plate_{x}", (x, -1.82, 2.52), (1.3, 0.05, 0.32), armor_dark, 0.025, rotation=(0, math.radians(-4), 0))
    for dz in (-0.1, 0.1):
        cylinder(f"Side_Plate_Bolt_{x}_{dz}", (x - 0.49, -1.87, 2.52 + dz), 0.045, 0.04, amber, rotation=(math.radians(90), 0, 0), vertices=12, bevel=0.006)
for x in (-3.2, -2.8, -2.4, -2.0, -1.6):
    box(f"Nose_Grille_{x}", (-4.66, x * 0.38, 1.96), (0.05, 0.12, 0.58), armor_dark, 0.012, rotation=(0, math.radians(-12), 0))

def beam_between(name, start, end, radius, mat):
    start_v, end_v = Vector(start), Vector(end)
    delta = end_v - start_v
    obj = cylinder(name, (start_v + end_v) * 0.5, radius, delta.length, mat, vertices=16, bevel=0.008)
    obj.rotation_euler = delta.to_track_quat("Z", "Y").to_euler()
    return obj

for i, (start, end) in enumerate([
    ((-2.8, -1.72, 1.35), (-2.2, -1.72, 2.15)),
    ((0.0, -1.72, 1.35), (0.6, -1.72, 2.18)),
    ((2.8, -1.72, 1.35), (2.2, -1.72, 2.12)),
    ((-0.2, 1.48, 3.65), (0.8, 1.48, 4.22)),
    ((0.9, -1.48, 3.65), (1.8, -1.48, 4.22)),
]):
    beam_between(f"Hydraulic_Line_{i}", start, end, 0.055, rubber_highlight)

for i, (body, loc, rot) in enumerate((
    ("RUST HARBOR", (-1.1, -1.88, 2.72), math.radians(90)),
    ("ARK-09 // RECOVERY", (-0.1, -1.89, 2.42), math.radians(90)),
)):
    bpy.ops.object.text_add(location=loc, rotation=(math.radians(90), 0, rot))
    text_obj = bpy.context.object
    text_obj.name = f"Stencil_{i}"
    text_obj.data.body = body
    text_obj.data.align_x = "CENTER"
    text_obj.data.size = 0.28 if i == 0 else 0.18
    text_obj.data.extrude = 0.012
    text_obj.data.bevel_depth = 0.006
    text_obj.data.materials.append(decal)

# Maintenance drones establish scale and provide motion in wide shots.
drones = []
for i, (x, y, z) in enumerate(((-9, 1.5, 6.0), (10, 0.8, 7.2), (12, 3.2, 4.7))):
    body = sphere(f"Service_Drone_{i}", (x, y, z), 0.42, armor_dark, segments=24, rings=12)
    torus(f"Service_Drone_Ring_{i}", (x, y, z), 0.62, 0.06, cyan, rotation=(math.radians(90), 0, 0))
    sphere(f"Service_Drone_Lamp_{i}", (x, y - 0.35, z), 0.09, amber, segments=16, rings=8)
    drones.append(body)

# Sparse dust motes catch the rim lights and sell the scale of the bay without
# relying on a render-engine particle system (which keeps headless renders
# deterministic across Blender versions).
for i in range(38):
    x = -12.0 + ((i * 7) % 240) / 10.0
    y = -5.0 + ((i * 11) % 170) / 10.0
    z = 0.8 + ((i * 13) % 92) / 10.0
    mote = sphere(f"Dust_Mote_{i:02d}", (x, y, z), 0.018 + (i % 4) * 0.006, dust_material, segments=8, rings=6)
    mote.rotation_euler = (i * 0.31, i * 0.17, i * 0.23)

# Original operator, assembled from primitives.
operator_root = bpy.data.objects.new("Operator_Root", None)
bpy.context.collection.objects.link(operator_root)
def parent(obj):
    obj.parent = operator_root
    return obj
parent(box("Operator_Torso", (-8, -1.0, 2.0), (0.95, 0.58, 1.45), armor_dark, 0.14, rotation=(0, math.radians(-8), math.radians(-5))))
parent(sphere("Operator_Helmet", (-8.0, -1.0, 3.25), 0.5, armor_dark, segments=32, rings=16))
parent(box("Operator_Visor", (-8.0, -1.45, 3.28), (0.58, 0.08, 0.2), glass, 0.02, rotation=(0, math.radians(-5), 0)))
for x, y, z, rot in ((-8.5, -1.0, 1.15, math.radians(8)), (-7.55, -1.0, 1.15, math.radians(-8))):
    parent(box(f"Operator_Leg_{x}", (x, y, z), (0.35, 0.45, 1.35), armor_dark, 0.08, rotation=(0, rot, 0)))
for x, y, z, rot in ((-8.65, -1.0, 2.25, math.radians(-20)), (-7.35, -1.0, 2.25, math.radians(20))):
    parent(box(f"Operator_Arm_{x}", (x, y, z), (0.28, 0.42, 1.15), armor_dark, 0.07, rotation=(0, rot, 0)))
parent(box("Operator_Rifle", (-7.1, -1.5, 2.25), (1.5, 0.14, 0.14), armor_edge, 0.03, rotation=(0, 0, math.radians(-12))))
parent(box("Operator_Chest_Plate", (-8.0, -1.34, 2.18), (0.68, 0.12, 0.62), armor, 0.07, rotation=(0, math.radians(-6), 0)))
parent(box("Operator_Backpack", (-8.0, -0.72, 2.24), (0.72, 0.22, 0.9), armor_dark, 0.06))
parent(box("Operator_Shoulder_L", (-8.62, -1.0, 2.55), (0.40, 0.58, 0.34), armor_edge, 0.08, rotation=(0, 0, math.radians(-12))))
parent(box("Operator_Shoulder_R", (-7.38, -1.0, 2.55), (0.40, 0.58, 0.34), armor_edge, 0.08, rotation=(0, 0, math.radians(12))))
for x in (-8.5, -7.55):
    parent(box(f"Operator_Knee_{x}", (x, -1.04, 1.28), (0.38, 0.52, 0.22), armor_edge, 0.05))
    parent(box(f"Operator_Boot_{x}", (x, -1.18, 0.50), (0.44, 0.72, 0.28), armor_dark, 0.06))
parent(cylinder("Operator_Rifle_Scope", (-7.72, -1.53, 2.42), 0.09, 0.28, cyan, rotation=(0, math.radians(90), 0), vertices=20, bevel=0.02))
parent(sphere("Operator_Visor_Lamp", (-8.0, -1.5, 3.33), 0.045, amber, segments=16, rings=8))
# Rounded shell overlays soften the silhouette in the operator close-up while
# retaining the editable hard-surface plates and rig root above.
torso_shell = parent(sphere("Operator_Torso_Shell", (-8.0, -1.16, 2.08), 0.72, armor_dark, segments=40, rings=24))
torso_shell.scale = (0.70, 0.43, 1.10)
parent(sphere("Operator_Pelvis_Shell", (-8.0, -1.12, 1.35), 0.46, armor_dark, segments=32, rings=16))
for i, (x, y, z, rot) in enumerate(((-8.52, -1.12, 1.75, math.radians(7)), (-7.48, -1.12, 1.75, math.radians(-7)), (-8.58, -1.15, 2.55, math.radians(-18)), (-7.42, -1.15, 2.55, math.radians(18)))):
    limb = parent(cylinder(f"Operator_Rounded_Limb_{i}", (x, y, z), 0.19 if i < 2 else 0.16, 0.72 if i < 2 else 0.63, human_suit, rotation=(0, rot, 0), vertices=28, bevel=0.04))
    limb.data.materials.clear()
    limb.data.materials.append(human_suit)
for i, loc in enumerate(((-8.52, -1.15, 1.38), (-7.48, -1.15, 1.38), (-8.72, -1.18, 2.20), (-7.28, -1.18, 2.20))):
    parent(sphere(f"Operator_Joint_{i}", loc, 0.23 if i < 2 else 0.18, armor_edge, segments=24, rings=12))

# Lighting: warm practicals, cyan reactor fill and a moving search light.
for i, (x, y, z) in enumerate(((-14, 0, 9), (-5, 1, 10), (6, 2, 9), (15, 4, 8))):
    area_light(f"Warm_Area_{i}", (x, y, z), 1100, (1.0, 0.20, 0.045), 4.5, (0, 0, 2))
area_light("Cool_Fill", (0, -8, 10), 2600, (0.08, 0.30, 0.55), 10.0, (0, 1, 2.5))
area_light("Front_Softbox", (0, -14, 5.0), 5200, (0.30, 0.48, 0.56), 12.0, (0, 0, 2.0))
area_light("Hero_Rim", (-9, 2, 8.5), 4200, (0.05, 0.35, 0.60), 6.0, (0, 0, 2.5))
bpy.ops.object.light_add(type="SUN", location=(0, -5, 12))
sun = bpy.context.object
sun.name = "Hangar_Sun_Rim"
sun.data.energy = 1.8
sun.data.angle = math.radians(12)
sun.rotation_euler = (math.radians(28), math.radians(-18), math.radians(-32))
reactor_light = area_light("Reactor_Cyan_Key", (0, 5.0, 7.0), 900, (0.04, 0.55, 1.0), 6.0, (0, 9, 5))
bpy.ops.object.light_add(type="SPOT", location=(-12, -6, 8))
search = bpy.context.object
search.name = "Searchlight_Sweep"
search.data.energy = 1600
search.data.color = (1.0, 0.30, 0.08)
search.data.spot_size = math.radians(18)
search.data.spot_blend = 0.64
look_at(search, (0, 0, 2))

# Camera and storyboard: the cut points line up with the 90-second narration
# cue sheet (8/18/28/38/49/59/69/78/86 seconds), leaving a four-second title
# hold at the end.
bpy.ops.object.camera_add(location=(17, -19, 8.5))
camera = bpy.context.object
camera.name = "Cinematic_Camera_90s"
camera.data.lens = 42
camera.data.sensor_width = 36
scene.camera = camera
focus = bpy.data.objects.new("Cinematic_DOF_Focus", None)
bpy.context.collection.objects.link(focus)
camera.data.dof.use_dof = True
camera.data.dof.focus_object = focus
camera.data.dof.aperture_fstop = 2.8

# Camera-space title card. Keeping the title parented to the camera makes it
# stable through the final push-in; visibility is keyed only for the final
# 78–90 second title hold so the earlier action remains unobstructed.
title_root = bpy.data.objects.new("Title_Card_Root", None)
bpy.context.collection.objects.link(title_root)
title_root.parent = camera
title_root.location = (0.0, 0.0, -7.0)
title_plate = box("Title_Plate", (0, 0, 0), (5.8, 2.05, 0.08), armor_dark, 0.06)
title_plate.parent = title_root
title_plate.location = (0, 0, 0)
title_plate.hide_render = True
title_plate.keyframe_insert("hide_render", frame=1)
title_plate.hide_render = False
title_plate.keyframe_insert("hide_render", frame=1873)
title_orange = material("Title orange glow", (0.5, 0.12, 0.018), 0.22, 0.28, (1.0, 0.16, 0.012), 8.0)
title_cyan = material("Title cyan scanline", (0.02, 0.24, 0.34), 0.18, 0.24, (0.04, 0.65, 1.0), 8.0)

def title_text(name, body, location, size, mat, extrude=0.015):
    bpy.ops.object.text_add(location=(0, 0, 0))
    obj = bpy.context.object
    obj.name = name
    obj.parent = title_root
    obj.location = location
    obj.data.body = body
    obj.data.align_x = "CENTER"
    obj.data.align_y = "CENTER"
    obj.data.size = size
    obj.data.extrude = extrude
    obj.data.bevel_depth = 0.008
    obj.data.materials.append(mat)
    obj.hide_render = True
    obj.keyframe_insert("hide_render", frame=1)
    obj.hide_render = False
    obj.keyframe_insert("hide_render", frame=1873)
    return obj

# Camera-space title card sizing is deliberately conservative so the full
# English lockup stays inside a 16:9 safe area on both the 960x540 render and
# down-scaled mobile previews.  Blender's Bfont glyph widths vary by locale,
# so the previous 0.72 size clipped the first/last letters in QA renders.
title_text("Title_English", "STEELFIRE ASSAULT", (0, 0.30, 0.07), 0.44, title_orange, 0.02)
title_text("Title_Chinese", "钢火突袭", (0, -0.46, 0.07), 0.40, title_cyan, 0.018)
title_text("Title_Subtitle", "RUST HARBOR  //  72 HOURS AFTER THE FALL", (0, -0.94, 0.07), 0.14, cold, 0.006)
for i, y in enumerate((-1.12, -0.08, 1.08)):
    scan = box(f"Title_Scanline_{i}", (0, y, 0.12), (5.2, 0.018, 0.018), title_cyan, 0.002)
    scan.parent = title_root
    scan.location = (0, y, 0.12)
    scan.hide_render = True
    scan.keyframe_insert("hide_render", frame=1)
    scan.hide_render = False
    scan.keyframe_insert("hide_render", frame=1873)
shots = [
    (1, 192, "01 PORT ARRIVAL", (15.5, -17.5, 8.0), (0, 0, 2.5), 45),
    (193, 432, "02 EVACUATION LINE", (-9.5, -11.5, 4.4), (-7.7, -1.0, 2.45), 54),
    (433, 672, "03 ARMOR SUIT", (-9.7, -10.5, 3.8), (-7.8, -1.0, 2.35), 58),
    (673, 912, "04 SIX-WHEEL GATE", (13.2, -12.0, 5.6), (0.0, 1.4, 2.2), 48),
    (913, 1176, "05 DRONES APPROACH", (11.0, -6.5, 6.4), (1.5, 0, 3.1), 50),
    (1177, 1416, "06 FIRELINE MEMORY", (6.2, -7.6, 5.0), (1.4, 0, 4.0), 66),
    (1417, 1656, "07 CORE COUNTDOWN", (8.7, -9.0, 5.8), (0, 8.6, 5.0), 54),
    (1657, 1872, "08 MELTDOWN ESCAPE", (13.2, -12.5, 5.6), (0.0, 2.0, 2.1), 48),
    (1873, 2064, "09 STEELFIRE TITLE", (9.4, -14.5, 5.4), (0.6, 2.5, 2.65), 52),
    (2065, 2160, "10 TITLE HOLD", (9.4, -14.5, 5.4), (0.6, 2.5, 2.65), 52),
]
for start, end, _label, loc, target, lens in shots:
    camera.location = loc
    look_at(camera, target)
    focus.location = target
    focus.keyframe_insert("location", frame=start)
    camera.data.lens = lens
    camera.keyframe_insert("location", frame=start)
    camera.keyframe_insert("rotation_euler", frame=start)
    camera.data.keyframe_insert("lens", frame=start)
    end_loc = Vector(loc) * 0.94 + Vector(target) * 0.06
    camera.location = end_loc
    look_at(camera, target)
    focus.location = target
    focus.keyframe_insert("location", frame=end)
    camera.data.lens = lens + 4
    camera.keyframe_insert("location", frame=end)
    camera.keyframe_insert("rotation_euler", frame=end)
    camera.data.keyframe_insert("lens", frame=end)

# Action keyframes: operator enters, reactor pulses, drones sweep and turret tracks.
operator_root.location = (-2.0, 0.0, 0.0)
operator_root.keyframe_insert("location", frame=680)
operator_root.location = (0.0, 0.0, 0.0)
operator_root.keyframe_insert("location", frame=920)
operator_root.location = (0.8, 0.0, 0.0)
operator_root.keyframe_insert("location", frame=1120)
for frame, scale in ((940, 0.85), (1040, 1.18), (1140, 0.95), (1260, 1.08), (1360, 0.9)):
    core.scale = (scale, 0.35 * scale, scale)
    core.keyframe_insert("scale", frame=frame)
for i, drone in enumerate(drones):
    base = drone.location.copy()
    drone.keyframe_insert("location", frame=1)
    drone.location = (base.x + (-5 if i % 2 == 0 else 5), base.y - 2.5, base.z + 1.5)
    drone.keyframe_insert("location", frame=1220 + i * 25)
    drone.location = (base.x, base.y, base.z)
    drone.keyframe_insert("location", frame=1560 + i * 25)
barrel.rotation_euler = (0, 0, 0)
barrel.keyframe_insert("rotation_euler", frame=1430)
barrel.rotation_euler = (0, 0, math.radians(10))
barrel.keyframe_insert("rotation_euler", frame=1610)
barrel.rotation_euler = (0, 0, math.radians(-4))
barrel.keyframe_insert("rotation_euler", frame=1760)
search.data.energy = 500
search.data.keyframe_insert("energy", frame=1)
search.data.energy = 1900
search.data.keyframe_insert("energy", frame=1180)
search.data.energy = 600
search.data.keyframe_insert("energy", frame=1540)
reactor_light.data.energy = 250
reactor_light.data.keyframe_insert("energy", frame=1)
reactor_light.data.energy = 1800
reactor_light.data.keyframe_insert("energy", frame=1030)
reactor_light.data.energy = 950
reactor_light.data.keyframe_insert("energy", frame=1500)

# Blender 5.x stores f-curves through layered Actions. Default BEZIER
# interpolation is sufficient here and keeping the action untouched preserves
# compatibility with both Blender 4.x and 5.x headless builds.

manifest = {
    "title": "钢火突袭 / Steelfire Assault — Rust Harbor",
    "fps": FPS,
    "duration_seconds": DURATION_SECONDS,
    "frames": TOTAL_FRAMES,
    "resolution": [scene.render.resolution_x, scene.render.resolution_y],
    "render_engine": scene.render.engine,
    "shots": [
        {"start_frame": s, "end_frame": e, "label": label, "start_seconds": round((s - 1) / FPS, 3), "end_seconds": round(e / FPS, 3)}
        for s, e, label, *_ in shots
    ],
    "audio_sync": {
        "voiceover_track": "audio/cg/intro_voiceover_90s.wav",
        "mix_policy": "voice and score are encoded after frame render; no network assets",
    },
}
with open(SHOT_MANIFEST, "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, ensure_ascii=False, indent=2)

bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
print(f"Saved Blender scene: {OUT_BLEND}")
print(f"90 second render: {TOTAL_FRAMES} frames at {FPS} fps -> {FRAME_DIR}")

# Rendering is optional in CI; pass STEELFIRE_RENDER=1 to render all frames.
if os.environ.get("STEELFIRE_RENDER", "0") == "1":
    bpy.ops.render.render(animation=True)
    print("Completed 90 second frame render")
else:
    print("Scene build complete; set STEELFIRE_RENDER=1 to render the 90 second sequence")
