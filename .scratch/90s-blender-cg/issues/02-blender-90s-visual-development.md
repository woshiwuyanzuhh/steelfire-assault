# 02: Blender 90-second CG visual development

**What to build:** Develop the hero asset set and one finished visual-development slice in Blender: next-generation hard-surface recovery vehicle, gatekeeper machine, hangar/reactor environment, operator, physically plausible materials, and cinematic lighting.

**Blocked by:** 01: Product baseline and acceptance protocol

**Status:** complete

**Evidence:** `dist/cg/qa2/final_contact_sheet.png` and `art/blender/steelfire_cg_scene.blend`. The final QA pass adds sloped armor, windshield framing, panel seams, hydraulic lines, readable stencils, character armor layers, depth of field, volume lighting, deterministic dust motes, and a safe-area title card. Blender reopen check passed (413 objects, 15 materials, editable scene).

- [x] The hero vehicle and gatekeeper have readable construction at close, medium, and silhouette distances: layered armor, suspension, fasteners, vents, cables, damage seams, glass, emissive systems, and moving joints.
- [x] The hangar and reactor contain authored geometry, decals, wet surfaces, volumetric depth, and practical lights to hold a close camera without empty primitive walls.
- [x] All visible meshes, materials, cameras, lights, and modifiers remain editable in the saved Blender engineering file.
- [x] A high-quality 960×540 review still and close-up render pass are produced in `dist/cg/qa2/final_contact_sheet.png`; the final 90-second sequence is 2160 frames at 24fps.
