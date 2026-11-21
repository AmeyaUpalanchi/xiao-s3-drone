# Frame v2 Strong (recommended)

A compact TPU 95A box frame for PCB1 and 8520 coreless motors, with reinforced motor cups.
This is the frame the drone has been flight-tested on, with a **GNB 550 mAh 1S** LiPo.

![Frame v2 Strong](frame_v2_strong.png)

## Files

| File | What it is |
|---|---|
| `frame_v2_strong.stl` | Ready to print |
| `frame_v2_strong.scad` | Parametric OpenSCAD source (includes `frame_outline.scad`) |
| `frame_outline.scad` | Outline of the base: arms, centre ring and window |
| `BambuStudio_A1_TPU_0.20mm.json` | Bambu Studio process profile used for the test prints (Bambu Lab A1) |

## Key dimensions

| Item | Value |
|---|---|
| Overall size | 94.4 × 94.2 × 27.5 mm |
| Printed mass | About 22 g (TPU at 1.21 g/cm³, from the STL volume) |
| Motor spacing | 80.0 × 79.8 mm between cup centres, 113 mm diagonal |
| Motor cups | 8.6 mm bore, hold 10 mm of the motor, 2 mm closed floor, ribs beside the wire slot |
| Board mount | 4 × M3 standoffs on a 42.27 mm square, board underside 17.5 mm above the base bottom |
| Base | 8 mm deep, hollow with a 0.8 mm skin, chamfered edges |
| Battery | Held under the board by two TPU strap loops, with grips for 22 AWG battery leads |
| Motor leads | Two clips along each arm |

## Printing

| Setting | Value |
|---|---|
| Material | TPU 95A |
| Layer height | 0.2 mm |
| Walls | 2 |
| Infill | 100 %, zig-zag |
| Outer wall speed | 25 mm/s |
| Supports | None. Print flat on its base |

In Bambu Studio, import `BambuStudio_A1_TPU_0.20mm.json` as a process preset. On other
printers, use the settings in the table and print slowly: TPU strings and under-extrudes
when pushed fast.

## Assembly

1. Press each motor into its cup from the top until it seats, with the wire running out
   through the slot. M1 front right and M3 rear left are the clockwise motors.
2. Route each motor lead along the arm clips to its JST connector on the board.
3. Slide the battery through the two strap loops under the board and press its leads into
   the grips.
4. Screw PCB1 onto the four standoffs, USB-C end facing forward. **M3 rubber vibration-damping
   mounts (soft-mount grommets) are recommended** between the board and the standoffs: they keep
   motor vibration away from the IMU, which gives a steadier hover and less drift. Plain M3
   screws also work.

## Editing

Open `frame_v2_strong.scad` in [OpenSCAD](https://openscad.org). The main sizes are
parameters at the top of the file: motor cup bore and height, standoff height, strap loop
size and wire grip size. Render with F6 and export a new STL.
