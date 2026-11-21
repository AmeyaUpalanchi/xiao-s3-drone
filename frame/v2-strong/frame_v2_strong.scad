// frame_box_c  v2 STRONG MOUNTS  -  same as frame_v2 but with reinforced motor mounts
// frame_box_c  v2  -  parametric rebuild of the drone frame (TPU 95A)
//
// Same working dimensions as the patched frame_box_c_tpu95-2: screw positions, board height,
// battery slot, motor cups (original foot and floor; 10 mm of motor held), wire grips, 8 mm base.
// What is new is the shape: the arms blend into the centre ring with fillets, every edge of the
// base is chamfered, the cups and standoffs flare into the base, the strap loops have rounded
// corners, and the wire grips are carried on swept buttresses that grow out of the loop pillars.
//
// x right, y toward the USB-C end of PCB1, z up. Prints flat on its base, no supports.

include <frame_outline.scad>

// ---- base ----
base_h   = 8.0;      // height of the hollow base (deck level)
skin     = 0.8;      // wall of the hollow base
root_r   = 3.5;      // fillet where the arms meet the centre ring
edge_c   = 0.6;      // chamfer on the top and bottom edges of the base
roof     = 1.0;      // thickness of the deck over the void (5 layers at 0.2)
roof_steps = 5;      // 0.4 mm steps of 45 deg shoulder under the roof; more = narrower bridges inside
hollow   = true;

// ---- board standoffs ----
hole_xy  = 21.135;   // M3 holes of PCB1
so_top   = 17.5;     // underside of PCB1
so_r0    = 3.0;  so_r1 = 2.5;  so_hole = 2.7;

// ---- battery strap loops ----
loop_hw  = 10.2;  loop_wall = 1.6;       // half width, pillar thickness
loop_y   = [14.1, 21.1];                 // each loop spans this in y (mirrored front/rear)
loop_in_h= 5.4;                          // opening height (battery is squeezed)
loop_bar = 1.6;
loop_r   = 1.6;                          // rounding of the loop's top corners

// ---- motor cups ----
cup_xy   = [40, 39.88];
cup_r    = 5.8;   bore_r = 4.3;          // 8.6 mm bore
cup_top  = 27.5;                         // 19.5 mm above the deck, as on the original frame
seat_z   = 17.5;                         // motor bottom (10 mm of the motor inside the cup, as original)
wire_hole= 2.0;   slot_w = 3.2;
low_bore = 3.54;                         // pocket under the cup floor
cup_floor= 2.0;                          // floor closing the cup (original: 0.6)
flare    = 0.8;                          // extra wall thickness at the deck, tapering to nothing at the motor seat
collar_h = 1.6;                          // 45 deg collar where the mount meets the arm
rib_t    = 1.4;  rib_h = 6.5;  rib_x = 8.2;   // two ribs beside the wire slot, running down onto the arm
pocket_in= 1.5;                          // the pocket sits this far toward the frame centre (original)

// ---- motor-lead clips on the arms ----
clip_c   = [[26.2, 27.45], [32.1, 32.86]];   // centres on the +x +y arm (mirrored to the others)
clip_gap = 1.3;  clip_h = 3.2;

// ---- battery-lead grips behind each strap loop ----
grip_d   = 6.0;      // grip length
grip_z0  = 14.4;     // underside of the grip bridge (clears the battery top)
grip_t   = 1.0;
wire_d   = 1.7;      // 22 AWG silicone wire (outer diameter)
grip_bore= wire_d + 0.2;   // bore the wire lies in: wire size plus allowance, because printed TPU holes come out small
grip_neck= wire_d - 0.2;   // short pinch in the middle of each bore that holds the wire
grip_slit= 1.1;            // slit the wire is pressed down through
grip_x   = 4.4;            // each wire sits this far from the centre line (was 1.5; wider so the slack U fits between)

// ---- slack shelf: hides the spare length of a 40 mm pigtail under the board ----
hook_on  = true;
hook_side= -1;             // which end: -1 = the end with the BT+ / BT- pads (camera end)
hook_d   = 6.0;            // shelf length, inward from the strap loop
hook_z0  = 14.4;  hook_t = 0.8;     // shelf underside (clears the battery) and thickness
peg_x    = 2.65;  peg_w = 1.6;      // one peg per wire; the wire goes in along the centre, round the peg, and back out
peg_top  = so_top - 0.2;            // pegs stop just under the board, which closes the top
grips_on = true;

eps = 0.01;  $fn = 48;

// ------------------------------------------------------------------ base
module base2d() difference() {
    union() {
        offset(r = -root_r) offset(r = root_r) difference() {              // fillet the arm roots
            union() {
                polygon(frame_outer);
                for (sx = [-1, 1], sy = [-1, 1]) translate([sx*cup_xy[0], sy*cup_xy[1]]) circle(r = cup_r + flare + edge_c, $fn = 72);   // arm end fully under the mount
                // a boss under each standoff, so the standoff and its foot sit fully on the base
                for (sx = [-1, 1], sy = [-1, 1]) translate([sx*hole_xy, sy*hole_xy]) circle(r = so_r0 + 1.0 + edge_c);
            }
            // the traced outline still carries the old grip supports behind the loops: trim them off
            for (sy = [-1, 1]) translate([-loop_hw - 0.5, sy > 0 ? 21.16 : -21.16 - 10]) square([2*loop_hw + 1, 10]);
        }
    }
    polygon(frame_window);
}
module chamfered(h, c) {          // extrude a 2D shape with chamfered top and bottom edges
    n = 3;
    for (i = [0 : n - 1]) {
        translate([0, 0, i*c/n]) linear_extrude(c/n + eps) offset(delta = -c + (i + 1)*c/n) children();
        translate([0, 0, h - (i + 1)*c/n]) linear_extrude(c/n) offset(delta = -c + (i + 1)*c/n) children();
    }
    translate([0, 0, c - eps]) linear_extrude(h - 2*c + 2*eps) children();
}
module cavity2d() difference() {
    base2d();
    for (sx = [-1, 1], sy = [-1, 1]) {
        translate([sx*hole_xy, sy*hole_xy]) circle(r = so_hole/2 + 1.3);             // solid round the screw holes
        translate([sx*(loop_hw - loop_wall/2) - loop_wall/2, sy > 0 ? loop_y[0] - 0.5 : -loop_y[1] - 0.5]) square([loop_wall, loop_y[1] - loop_y[0] + 1]);   // web under each loop pillar
        translate([sx*(cup_xy[0] - pocket_in/sqrt(2)), sy*(cup_xy[1] - pocket_in/sqrt(2))]) circle(r = low_bore + skin, $fn = 72);   // tube under the cups
    }
}
module cavity() {                 // closed void: straight sides, 45 deg shoulders, short flat roof (bridges like the original)
    z1 = base_h - roof - 0.4*roof_steps;
    translate([0, 0, skin]) linear_extrude(z1 - skin) offset(delta = -skin) cavity2d();
    for (i = [1 : roof_steps]) translate([0, 0, z1 + 0.4*(i - 1) - eps]) linear_extrude(0.4 + eps) offset(delta = -skin - 0.4*i) cavity2d();
}

// ------------------------------------------------------------------ parts on the deck
module standoffs() for (sx = [-1, 1], sy = [-1, 1]) translate([sx*hole_xy, sy*hole_xy, 0]) {
    translate([0, 0, base_h - eps]) cylinder(r1 = so_r0, r2 = so_r1, h = so_top - base_h + eps);
    intersection() {                                                                            // flared foot, trimmed to the base
        translate([0, 0, base_h - eps]) cylinder(r1 = so_r0 + 1.0, r2 = so_r0 - 0.1, h = 1.4);  // so it never overhangs the edge
        translate([-sx*hole_xy, -sy*hole_xy, base_h - 1]) linear_extrude(4) offset(delta = -edge_c) base2d();
    }
}

module loop_profile() difference() {      // in (x, z), z measured from the deck
    H = loop_in_h + loop_bar;
    hull() { translate([-loop_hw, 0]) square([2*loop_hw, eps]); for (s = [-1, 1]) translate([s*(loop_hw - loop_r), H - loop_r]) circle(loop_r, $fn = 32); }
    hull() {
        translate([-(loop_hw - loop_wall), -1]) square([2*(loop_hw - loop_wall), 1 + eps]);
        for (s = [-1, 1]) translate([s*(loop_hw - loop_wall - 1.2), loop_in_h - 1.2]) circle(1.2, $fn = 32);
    }
}
module loops() for (sy = [-1, 1]) mirror([0, sy < 0 ? 1 : 0, 0])
    translate([0, loop_y[1], base_h - eps]) rotate([90, 0, 0]) linear_extrude(loop_y[1] - loop_y[0]) loop_profile();

module each_cup() for (sx = [-1, 1], sy = [-1, 1])
    translate([sx*cup_xy[0], sy*cup_xy[1], 0]) rotate([0, 0, atan2(-sy, -sx)]) children();   // +x = toward the frame centre

module cups() each_cup() {
    // full-width wall and floor right down into the arm, exactly like the original frame (no taper, no neck)
    translate([0, 0, base_h - cup_floor]) cylinder(r = cup_r, h = cup_top - base_h + cup_floor, $fn = 72);
    translate([0, 0, base_h - eps]) cylinder(r1 = cup_r + flare, r2 = cup_r, h = seat_z - base_h, $fn = 72);        // thicker wall toward the root
    for (s = [-1, 1]) translate([0, s > 0 ? slot_w/2 : -slot_w/2 - rib_t, base_h - eps]) rotate([-90, 0, 0]) linear_extrude(rib_t)
        polygon([[2, 0], [rib_x, 0], [4.6, -rib_h], [2, -rib_h]]);                                                 // ribs beside the wire slot

}
module cup_collars() for (sx = [-1, 1], sy = [-1, 1]) intersection() {                                          // 45 deg collar, trimmed to the arm
    translate([sx*cup_xy[0], sy*cup_xy[1], base_h - eps]) cylinder(r1 = cup_r + flare + collar_h, r2 = cup_r + flare - 0.2, h = collar_h + 0.2, $fn = 72);
    translate([0, 0, base_h - 1]) linear_extrude(4) offset(delta = -edge_c) base2d();
}
module cup_cuts() each_cup() {
    translate([0, 0, base_h]) cylinder(r = bore_r, h = cup_top, $fn = 72);                                         // motor bore
    translate([pocket_in, 0, -1]) cylinder(r = low_bore, h = base_h - cup_floor + 1, $fn = 72);                    // pocket under the floor
    translate([0, -slot_w/2, base_h]) cube([cup_r + 2, slot_w, cup_top]);                                          // wire slot
}
module cup_ledge() each_cup() difference() {                                                                       // motor seat
    rotate_extrude($fn = 72) polygon([[wire_hole, seat_z], [bore_r + 0.1, seat_z], [bore_r + 0.1, seat_z - 3.3], [bore_r, seat_z - 3.3], [wire_hole, seat_z - 0.6]]);
    translate([0, -slot_w/2, base_h]) cube([bore_r + 2, slot_w, seat_z]);
}

module arm_clips() for (sx = [-1, 1], sy = [-1, 1], c = clip_c) {
    a = atan2(sy*(cup_xy[1] - clip_c[0][1]), sx*(cup_xy[0] - clip_c[0][0]));       // arm direction
    translate([sx*c[0], sy*c[1], base_h - eps]) rotate([0, 0, a]) for (s = [-1, 1]) mirror([0, s < 0 ? 1 : 0, 0]) {
        hull() {                                                                    // post, rounded in plan
            for (x = [-0.8, 0.8]) translate([x, clip_gap/2 + 0.8, 0]) cylinder(d = 1.6, h = clip_h, $fn = 20);
        }
        hull() {                                                                    // lip over the wires
            translate([-1.6, clip_gap/2 - 0.25, clip_h - 0.15]) cube([3.2, 0.4, 0.15]);
            translate([-1.6, clip_gap/2, clip_h - 0.9]) cube([3.2, 0.2, 0.1]);
        }
        hull() {                                                                    // small foot
            translate([-1.6, clip_gap/2, 0]) cube([3.2, 1.6, 0.1]);
            translate([-2.2, clip_gap/2, 0]) cube([4.4, 2.2, eps]);
        }
    }
}

// battery-lead grip: bridge behind the loop, on buttresses that grow out of the loop pillars
module grip() {
    yb = loop_y[1];
    // 45 deg buttresses, flush with the loop pillars
    for (s = [-1, 1]) translate([s > 0 ? loop_hw - loop_wall : -loop_hw, 0, 0]) rotate([90, 0, 90]) linear_extrude(loop_wall)
        polygon([[yb - 0.3, 0], [yb + 1.4, 0], [yb + 1.4, grip_z0 - (grip_d - 1.4)],      // rib, then a 45 deg rise
                 [yb + grip_d, grip_z0], [yb + grip_d, grip_z0 + grip_t], [yb - 0.3, grip_z0 + grip_t]]);
    // bridge
    translate([-loop_hw, yb - 0.3, grip_z0]) cube([2*loop_hw, grip_d + 0.3, grip_t]);
    // grip block with chamfered shoulders
    z0 = grip_z0 + grip_t;  bore = grip_bore;  zc = z0 + 0.3 + bore/2;  zt = zc + bore/2 + 1.0;  xo = grip_x + bore/2 + 1.2;
    difference() {
        hull() {
            translate([-xo - 0.8, yb, z0 - eps]) cube([2*xo + 1.6, grip_d, eps]);
            translate([-xo, yb, zt - 0.6]) cube([2*xo, grip_d, 0.1]);
            translate([-xo + 0.5, yb + 0.4, zt - eps]) cube([2*xo - 1.0, grip_d - 0.8, eps]);
        }
        for (s = [-1, 1]) translate([s*grip_x, 0, zc]) {
            for (y = [[yb - 1, grip_d/2 + 0.6], [yb + grip_d/2 + 0.4, grip_d/2 + 0.6]]) translate([0, y[0], 0]) rotate([-90, 0, 0]) cylinder(d = bore, h = y[1], $fn = 32);
            translate([0, yb - 1, 0]) rotate([-90, 0, 0]) cylinder(d = grip_neck, h = grip_d + 2, $fn = 32);
            translate([-grip_slit/2, yb - 1, 0]) cube([grip_slit, grip_d + 2, zt - zc + 1]);
            hull() { translate([-grip_slit/2, yb - 1, zt - zc - 0.5]) cube([grip_slit, grip_d + 2, eps]); translate([-grip_slit/2 - 0.5, yb - 1, zt - zc]) cube([grip_slit + 1.0, grip_d + 2, eps]); }
        }
    }
}
// slack shelf on the inner side of one strap loop (drawn for the +y loop, mirrored into place)
module hook() {
    yi = loop_y[0];  zs = hook_z0 + hook_t;
    for (s = [-1, 1]) translate([s > 0 ? loop_hw - loop_wall : -loop_hw, 0, 0]) rotate([90, 0, 90]) linear_extrude(loop_wall)
        polygon([[yi + 0.3, 0], [yi - 1.4, 0], [yi - 1.4, hook_z0 - (hook_d - 1.4)],     // rib, then a 45 deg rise (same as the grip buttress)
                 [yi - hook_d, hook_z0], [yi - hook_d, zs], [yi + 0.3, zs]]);
    translate([-loop_hw, yi - hook_d, hook_z0]) cube([2*loop_hw, hook_d + 0.3, hook_t]);                 // shelf
    for (s = [-1, 1]) {
        hull() for (y = [yi - 0.6, yi - hook_d + 2.9]) translate([s*peg_x, y, zs - eps]) cylinder(d = peg_w, h = peg_top - zs, $fn = 24);   // peg
        translate([s*(grip_x + wire_d/2 + 0.5), yi - hook_d + 0.4, zs - eps]) cube([0.8, hook_d - 0.4, 1.2], center = false);           // low fence outside the return leg
    }
}
module grips() for (sy = [-1, 1]) mirror([0, sy < 0 ? 1 : 0, 0]) grip();

// ------------------------------------------------------------------ assembly
module frame() {
    difference() {
        union() {
            difference() { chamfered(base_h, edge_c) base2d(); if (hollow) cavity(); }
            standoffs();
            loops();
            cups();
            cup_collars();
            arm_clips();
            if (grips_on) grips();
            if (hook_on) mirror([0, hook_side < 0 ? 1 : 0, 0]) hook();
        }
        cup_cuts();
        for (sx = [-1, 1], sy = [-1, 1]) translate([sx*hole_xy, sy*hole_xy, -1]) cylinder(d = so_hole, h = so_top + 2, $fn = 32);
    }
    cup_ledge();
}
frame();
