// =====================================================================
//  XIAO ESP-Drone PCB1 : 8520 brushed TPU ducted frame for 75 mm props
// ---------------------------------------------------------------------
//  Structural ideas taken from the TinyFlex HD 75 TPU frame and re-sized
//  for 8.5 x 20 mm coreless motors, 75 mm props and the 50 x 50 mm PCB1:
//
//   * Duct rim = open box section, like a bicycle rim. Crash loads are
//     horizontal, so the rim is made deep in the horizontal direction:
//     a 45 deg flared inlet lip on top (upper chord) and a wide outer
//     flange on the bed (lower chord), joined by a Warren-truss lattice.
//     About 65x the in-plane stiffness of a plain 0.8 mm band, and the
//     flared lip doubles as a bell-mouth duct inlet.
//   * Concave "waist" fillets between ducts (TinyFlex outline) tie the
//     four rings into one closed hoop.
//   * Every member is a vertical web: bending stiffness grows with h^3,
//     so tall thin TPU webs are far stiffer than flat TPU plates.
//   * Diamond cut-outs (45 deg flanks) lighten webs and print without
//     support. Nothing on this frame needs support material.
//   * Five spokes per duct, spread over the arc that takes the impacts,
//     so no stretch of rim is left long and unsupported. With the stiff
//     rim they also triangulate each collar sideways; the arm and the
//     waist spokes tie everything to the PCB centre square.
//   * PCB1 bolts on top and the battery cage bolts underneath with the
//     same 4 x M3 screws, so the FR4 board works as a stressed skin.
//   * Motors push up into a 45 deg seat at the top of the collar, so
//     thrust and landing loads go into the frame in compression rather
//     than relying on friction. Crush ribs give the press fit.
//
//  Units: mm.  Print all parts in TPU 95A, flat as exported, no supports.
//  Frame origin = PCB centre = centre of the M3 hole square, z=0 = bed.
// =====================================================================

include <pcb1_outline.scad>

part = "assembly"; // [assembly, frame, battery_holder, motor_boot, prop_envelope]

/* [Propeller and duct] */
prop_d     = 75;    // propeller diameter
tip_clear  = 2.5;   // radial gap from prop tip to duct wall
wall       = 0.8;   // duct wall, 2 perimeters with a 0.4 nozzle
duct_h     = 15.5;  // duct height above the bed
lip_w      = 4.5;   // outward reach of the rolled duct lip
lip_t      = 0.6;   // lip skin thickness (prints as 2 lines, max 45 deg overhang)
flange_w   = 4.0;   // outward bottom flange on the bed
flange_t   = 0.8;
waist_r    = 30;    // concave fillet radius between neighbouring ducts
spoke_ang  = [-20, 25, 65, 110];     // spoke directions from each motor (deg)
vent_w     = 2.5;   // 45 deg chevron vents in the lower duct wall
vent_s     = 2.2;   // web between vents (horizontal)
flange_oc  = 1.2;   // outer chord width of the trussed bottom flange
flange_s   = 1.1;   // diagonal strut width in the bottom flange

/* [8520 motor] */
motor_d    = 8.5;
motor_len  = 20;
bore_d     = 8.8;   // collar bore
rib_id     = 8.15;  // crush-rib inner diameter (press fit on the 8.5 can)
ribs       = 6;
rib_w      = 1.0;
collar_wall= 1.2;
collar_h   = 9.5;   // collar top above the bed
seat_id    = 7.4;   // top seat bore: stops the can, prop hub drops into it

/* [PCB1 mounting] */
hole_sp    = 42.27; // M3 hole square on PCB1
screw_d    = 3.3;
boss_d     = 7.0;
boss_h     = 2.0;   // PCB underside height above the bed
beam_w     = 1.2;
beam_h     = 1.2;   // 0.8 mm below the PCB

/* [Webs] */
arm_t      = 1.6;
spoke_t    = 0.9;
waist_t    = 0.9;
waist_hgt  = 4.0;   // kept low so a USB-C plug clears it
prop_cap   = 8.5;   // max height of anything under a prop disc
spoke_h    = 6.0;   // rim spokes at the collar, tapering to spoke_h2 at the duct
spoke_h2   = 3.5;
arm_w      = 8.0;   // width of the X-arm bottom flange at the PCB boss
chord      = 1.0;   // solid edge left around web cut-outs
strut      = 1.6;   // horizontal strut width between cut-outs

/* [Battery 66 x 18 x 6.5] */
bat_l = 66;  bat_w = 18;  bat_h = 6.5;
bat_clear_l = 1.0;  bat_clear_w = 0.5;  bat_clear_h = 0.2;
cage_wall  = 1.0;
cage_plate = 1.0;
lip        = 1.5;

/* [Motor boot] */
boot_id    = 8.6;
boot_wall  = 0.9;
boot_floor = 1.2;
boot_h     = 6.0;

/* [Prop envelope used for clearance checks] */
blade_lo = 2.0;     // lowest blade point above the motor face
blade_hi = 6.0;     // highest blade point above the motor face

/* [Preview] */
show_pcb   = true;
pcb_stl    = "";    // optional: STL export of PCB1 (board origin at the top-left M3 hole)
show_props = true;

// ------------------------------------------------------------ derived
R_in    = prop_d/2 + tip_clear;
R_out   = R_in + wall;
A       = R_out + 0.25;                          // motor offset on each axis
yc      = A + sqrt(pow(R_out + waist_r, 2) - A*A); // waist fillet centre
waist_y = yc - waist_r;                          // inner face of the waist
hp      = hole_sp/2;
collar_od  = bore_d + 2*collar_wall;
cone_h     = (bore_d - seat_id)/2;
motor_top  = collar_h - (motor_d - seat_id)/2;   // can face seats here
theta_t = atan2(yc - A, -A);                     // lobe/waist tangent, seen from the motor
phi_t   = atan2(A - yc, A);                      // same point, seen from the fillet centre

// duct lattice geometry
// rolled lip: quadratic Bezier from the wall (vertical tangent) to the outer
// edge (45 deg tangent), then a 45 deg cap back to the wall top -> closed,
// hollow, rounded rim that prints without support.
lip_zm = duct_h - (lip_w - wall/2) - lip_t/2;   // outer edge height
lip_z0 = lip_zm - (lip_w + 0.5);                 // where the skin leaves the wall
lip_c  = [wall/2, lip_zm - (lip_w - wall/2)];    // Bezier control point
z_lo = flange_t + 0.6;
z_hi = lip_z0 - 0.3;                             // vents stop below the lip
v_h  = z_hi - z_lo;
d_p  = vent_w + vent_s;
d_hw = (vent_w + v_h)/2;                          // half footprint of one vent

W_i = bat_w + bat_clear_w;
H_i = bat_h + bat_clear_h;
L_i = bat_l + bat_clear_l;

// ------------------------------------------------------------ helpers
function arc_pos(L, m0, m1, p) =
    let(u = L - m0 - m1, n = u < 0 ? 0 : floor(u/p) + 1,
        st = m0 + (u - (n-1)*p)/2)
    [for (i = [0:1:n-1]) st + i*p];

// cut list entries: [cx, cy, r, angle, kind(0 diamond / 1 triangle), gap]
function seg_cuts(cx, cy, r, a0, a1, m0, m1) =
    let(L  = abs(a1 - a0)*PI/180*r,
        s  = sign(a1 - a0),
        ds = arc_pos(L, m0, m1, d_p),
        ang = function(d) a0 + s*d/r*180/PI)
    concat([for (d = ds) [cx, cy, r, ang(d), 0, 0]],
           [for (i = [0:1:len(ds)-2])
                [cx, cy, r, ang((ds[i] + ds[i+1])/2), 1, ds[i+1] - ds[i]]]);

m_j = d_hw + 1.4;            // keep-out at spoke junctions
r_l = R_out - wall/2;
r_f = waist_r + wall/2;

// One quadrant of the rim as a path: top waist spoke -> half waist fillet ->
// lobe (theta_t down to 90-theta_t) -> half waist fillet -> right waist spoke.
// Lattice cut-outs fill each stretch between junctions.
L_f = (phi_t + 90)*PI/180*r_f;
L_l = (2*theta_t - 90)*PI/180*r_l;
function rim_cut(d, kind, gap) =
    d < L_f       ? [0, yc, r_f, -90 + d/r_f*180/PI, kind, gap, -1] :
    d < L_f + L_l ? [A, A, r_l, theta_t - (d - L_f)/r_l*180/PI, kind, gap, 1] :
                    [yc, 0, r_f, 90 - phi_t + (d - L_f - L_l)/r_f*180/PI, kind, gap, -1];
junctions = concat([0], [for (a = [len(spoke_ang)-1:-1:0]) L_f + (theta_t - spoke_ang[a])*PI/180*r_l],
                   [2*L_f + L_l]);
// Bottom flange as an in-plane Warren truss: wall = inner chord, a thin
// outer chord, 60 deg diagonals. Keeps the flange's bending stiffness at
// about half the mass.
L_q   = 2*L_f + L_l;
fl_h  = flange_w - flange_oc;                  // free radial depth
fl_n  = round(L_q/(2*fl_h/tan(60)));
fl_p  = L_q/fl_n;                              // pitch fitted to the path
flange_cuts = [for (k = [0:1:2*fl_n-1]) rim_cut((k + 0.5)*fl_p/2, k % 2, fl_p)];

// vents lean away from the middle of each stretch -> chevrons between spokes
quad_cuts = [for (k = [0:1:len(junctions)-2])
                let(mid = (junctions[k] + junctions[k+1])/2,
                    ds = [for (d = arc_pos(junctions[k+1] - junctions[k], m_j, m_j, d_p)) junctions[k] + d])
                each [for (d = ds) rim_cut(d, d < mid ? 2 : 3, 0)]];

module quad4() for (mx = [0, 1], my = [0, 1]) mirror([mx, 0, 0]) mirror([0, my, 0]) children();

// ------------------------------------------------------------ duct
// Rim cross-section in (u, z), u measured outward from the inner wall face.
module rim_profile() {
    square([wall, duct_h]);                                   // wall
    square([wall + flange_w, flange_t]);                      // outer bottom flange
    p0 = [wall/2, lip_z0];  p1 = [lip_w, lip_zm];  p2 = [wall/2, duct_h - lip_t/2];
    pts = concat([for (t = [0:0.1:1.0001]) (1-t)*(1-t)*p0 + 2*(1-t)*t*lip_c + t*t*p1], [p2]);
    for (i = [0:len(pts)-2]) hull() {
        translate(pts[i]) circle(d = lip_t, $fn = 16);
        translate(pts[i+1]) circle(d = lip_t, $fn = 16);
    }
}
module lobe_rim() translate([A, A, 0]) rotate([0, 0, 90 - theta_t - 0.01])
    rotate_extrude(angle = 2*theta_t - 90 + 0.02, $fn = 360) translate([R_in, 0]) rim_profile();
module waist_rim_half() translate([0, yc, 0]) rotate([0, 0, -90])
    rotate_extrude(angle = phi_t + 90 + 0.01, $fn = 360)
        translate([waist_r + wall, 0]) mirror([1, 0]) rim_profile();

module lat_cut(c)
    translate([c[0], c[1], 0]) rotate([0, 0, c[3]]) translate([c[2], 0, 0])
        rotate([90, 0, 90]) linear_extrude(height = 2.2, center = true)
            mirror([c[4] == 3 ? 1 : 0, 0])
                polygon([[-d_hw, z_lo], [-d_hw + vent_w, z_lo], [d_hw, z_hi], [d_hw - vent_w, z_hi]]);

// triangle hole in the bottom flange; kind 0 points outward, 1 inward
module flange_cut(c)
    translate([c[0], c[1], -1]) rotate([0, 0, c[3]]) translate([c[2], 0, 0])
        mirror([c[6] < 0 ? 1 : 0, 0, 0]) linear_extrude(flange_t + 2)
            translate([wall/2, 0]) offset(delta = -flange_s/2)
                polygon(c[4] == 0 ? [[0, -c[5]/2], [fl_h, 0], [0, c[5]/2]]
                                  : [[fl_h, -c[5]/2], [fl_h, c[5]/2], [0, 0]]);

module duct() difference() {
    quad4() { lobe_rim(); waist_rim_half(); mirror([1, -1, 0]) waist_rim_half(); }
    quad4() {
        for (c = quad_cuts) lat_cut(c);
        for (c = flange_cuts) flange_cut(c);
    }
}

// ------------------------------------------------------------ webs
// Vertical plate from p0 to p1 (xy), top edge given by prof = [[d, z], ...]
// holes = [d0, d1, zlo, zhi] places diamond cut-outs inside that window.
module web(p0, p1, t, prof, holes = []) {
    L   = norm(p1 - p0);
    ang = atan2(p1[1] - p0[1], p1[0] - p0[0]);
    translate([p0[0], p0[1], 0]) rotate([0, 0, ang]) rotate([90, 0, 0])
        linear_extrude(height = t, center = true) difference() {
            polygon(concat([[0, 0], [L, 0]], [for (i = [len(prof)-1:-1:0]) prof[i]]));
            if (len(holes) == 4) {
                hh = (holes[3] - holes[2])/2;
                hw = hh;
                ds = hh >= 1.5 ? arc_pos(holes[1] - holes[0], hw, hw, 2*hw + strut) : [];
                for (d = ds)
                    translate([holes[0] + d, (holes[2] + holes[3])/2])
                        polygon([[0, -hh], [hw, 0], [0, hh], [-hw, 0]]);
                // up-pointing triangles between diamonds -> Warren truss
                for (i = [0:1:len(ds)-2]) let(b = (ds[i+1] - ds[i])/2 - strut, dm = holes[0] + (ds[i] + ds[i+1])/2)
                    if (b > 1) polygon([[dm - b, holes[2]], [dm + b, holes[2]], [dm, holes[2] + b]]);
            }
        }
}

module quadrant_webs() {
    m = [A, A];
    // arm: boss -> collar
    La = norm(m - [hp, hp]);
    rise = 5 + (prop_cap - boss_h);
    web([hp, hp], m, arm_t,
        [[0, boss_h], [5, boss_h], [rise, prop_cap], [La, prop_cap]]);
    // T-flange on the bed: gives the arm its width (and lateral stiffness)
    linear_extrude(0.8) hull() {
        translate([hp, hp]) circle(d = arm_w, $fn = 48);
        translate(m) circle(d = collar_od, $fn = 48);
    }
    // spokes: collar -> duct
    for (a = spoke_ang) let(dir = [cos(a), sin(a)]) {
        p0 = m + dir*(bore_d/2 + 0.3);
        p1 = m + dir*(R_in + wall/2);
        Ls = norm(p1 - p0);
        web(p0, p1, spoke_t, [[0, spoke_h], [Ls, spoke_h2]]);
    }
    // waist spokes: mid-node -> waist of the duct (half of each, mirrored)
    Lw = waist_y + wall/2 - hp;
    for (q = [[[0, hp], [0, waist_y + wall/2]], [[hp, 0], [waist_y + wall/2, 0]]])
        web(q[0], q[1], waist_t,
            [[0, beam_h], [3, waist_hgt], [Lw - (duct_h - waist_hgt) - 1, waist_hgt], [Lw - 1, duct_h], [Lw, duct_h]]);
}

module collar_body() translate([A, A, 0]) cylinder(d = collar_od, h = collar_h, $fn = 96);

module collar_void() translate([A, A, 0]) {
    translate([0, 0, -1]) cylinder(d = bore_d, h = collar_h - cone_h + 1.001, $fn = 96);
    translate([0, 0, collar_h - cone_h]) cylinder(d1 = bore_d, d2 = seat_id, h = cone_h + 0.001, $fn = 96);
    translate([0, 0, collar_h - 1]) cylinder(d = seat_id, h = 3, $fn = 96);
    translate([0, 0, -0.01]) cylinder(d1 = bore_d + 1, d2 = bore_d, h = 0.5, $fn = 96); // lead-in
}

module collar_ribs() translate([A, A, 0])
    for (i = [0:ribs-1]) rotate([0, 0, i*360/ribs + 30])
        translate([rib_id/2, -rib_w/2, 0.8])
            cube([(bore_d - rib_id)/2 + 0.3, rib_w, collar_h - cone_h - 0.8]);

module centre() {
    for (r = [0, 90, 180, 270]) rotate([0, 0, r])
        translate([-hp, hp - beam_w/2, 0]) cube([2*hp, beam_w, beam_h]);
    quad4() translate([hp, hp, 0]) cylinder(d = boss_d, h = boss_h, $fn = 64);
    // body rim that frames the PCB outline
    linear_extrude(1.4) difference() {
        offset(r = 1.9, $fn = 32) polygon(pcb_outline);
        offset(r = 0.4, $fn = 32) polygon(pcb_outline);
    }
}

module frame() {
    difference() {
        union() {
            duct();
            centre();
            quad4() { quadrant_webs(); collar_body(); }
        }
        quad4() {
            translate([hp, hp, -1]) cylinder(d = screw_d, h = 20, $fn = 32);
            collar_void();
        }
    }
    quad4() collar_ribs();
}

// ------------------------------------------------------------ battery cage
// Printed in this orientation: the plate that bolts under the frame is on
// the bed and the cage opens upwards. Flip it over to fit it.
hoop_w = 4;
hoop_y = [-L_i/2 + hoop_w/2, -hp, hp, L_i/2 - hoop_w/2];

module battery_holder() {
    xo = W_i/2 + cage_wall;
    ztop = cage_plate + H_i + lip + 0.8;
    difference() {
        union() {
            // plate: edge rails, end strips and the two bolt bars
            for (s = [-1, 1]) translate([s > 0 ? W_i/2 - 2.5 : -xo, -L_i/2, 0])
                cube([2.5 + cage_wall, L_i + cage_wall, cage_plate]);
            translate([-xo, -L_i/2, 0]) cube([2*xo, hoop_w, cage_plate]);
            translate([-xo, L_i/2 - hoop_w, 0]) cube([2*xo, hoop_w + cage_wall, cage_plate]);
            for (sy = [-1, 1]) translate([0, sy*hp, 0]) {
                translate([-hp, -2.5, 0]) cube([2*hp, 5, cage_plate]);
                for (sx = [-1, 1]) translate([sx*hp, 0, 0]) cylinder(d = boss_d, h = cage_plate, $fn = 48);
            }
            // four hoops: side posts with 45 deg lips (print without support)
            for (y = hoop_y, s = [-1, 1]) mirror([s < 0 ? 1 : 0, 0, 0])
                translate([0, y - hoop_w/2, 0]) rotate([90, 0, 0]) mirror([0, 0, 1])
                    linear_extrude(hoop_w)
                        polygon([[W_i/2, 0], [xo, 0], [xo, ztop], [W_i/2 - lip, ztop],
                                 [W_i/2 - lip, cage_plate + H_i + lip], [W_i/2, cage_plate + H_i]]);
            // end stop on the +Y end (battery lead exits at -Y, towards the PCB pads)
            translate([-xo, L_i/2, 0]) cube([2*xo, cage_wall, ztop]);
        }
        for (sx = [-1, 1], sy = [-1, 1]) translate([sx*hp, sy*hp, -1]) cylinder(d = screw_d, h = 5, $fn = 32);
        translate([0, L_i/2 + cage_wall/2, cage_plate + H_i/2 + 0.6]) rotate([90, 0, 0])
            linear_extrude(height = 4, center = true)
                polygon([[0, -3.6], [6, 0], [0, 3.6], [-6, 0]]);
    }
}

// ------------------------------------------------------------ motor boot
module motor_boot() difference() {
    cylinder(d = boot_id + 2*boot_wall, h = boot_floor + boot_h, $fn = 64);
    translate([0, 0, boot_floor]) cylinder(d = boot_id, h = boot_h + 1, $fn = 64);
    translate([0, 0, -1]) cylinder(d = 4, h = boot_floor + 2, $fn = 32);
    translate([0, -1.25, -1]) cube([10, 2.5, boot_floor + boot_h + 2]);
}

// ------------------------------------------------------------ preview
module xiao_proxy() translate([0, 0, boss_h])
    color("dimgray") translate([0, 7.6, 1.6]) {
        for (sx = [-1, 1]) translate([sx*7.62 - 1.2, -8, 0]) cube([2.4, 16, 5]);
        translate([-8.9, -10.5, 5]) cube([17.8, 21, 1.2]);
        translate([-4.5, 10.5 - 7.3, 6.2]) cube([9, 7.3, 3.2]);  // USB-C
    }
module pcb_proxy() translate([0, 0, boss_h]) {
    color("darkgreen") linear_extrude(1.6) difference() {
        polygon(pcb_outline);
        for (sx = [-1, 1], sy = [-1, 1]) translate([sx*hp, sy*hp]) circle(d = 3.2, $fn = 24);
    }
    color("white") for (sx = [-1, 1], sy = [-1, 1])
        translate([sx*15.25 - 2, sy*20.3 - 1.8, 1.6]) cube([4, 3.6, 4.3]);
}

prop_lo = 0.5;      // prop hub base above the motor face (preview)
module prop_blade() {
    n = 14;
    function sl(t) = [2.4 + t*(prop_d/2 - 2.4),
                      lookup(t, [[0, 4.5], [0.25, 9], [0.7, 8], [0.95, 4], [1, 2.5]]),
                      lookup(t, [[0, 30], [1, 11]])];
    for (i = [0:n-1]) hull() for (t = [i/n, (i+1)/n]) let(q = sl(t))
        translate([q[0], 0, 3]) rotate([q[2], 0, 0]) cube([0.4, q[1], 0.5], center = true);
}
module prop() {
    cylinder(d = 5.2, h = 5.5, $fn = 32);
    for (a = [0, 180]) rotate([0, 0, a]) prop_blade();
}

module assembly() {
    color([0.16, 0.17, 0.19]) frame();
    quad4() translate([A, A, 0]) {
        color("silver") translate([0, 0, motor_top - motor_len]) cylinder(d = motor_d, h = motor_len, $fn = 48);
        color("black") translate([0, 0, motor_top - motor_len - boot_floor]) motor_boot();
        if (show_props) color([0.85, 0.2, 0.15], 0.9)
            translate([0, 0, motor_top + prop_lo]) rotate([0, 0, 35]) prop();
    }
    color([0.16, 0.17, 0.19]) mirror([0, 0, 1]) battery_holder();
    color("gold") translate([-bat_w/2, -L_i/2 + 0.5, -cage_plate - bat_h]) cube([bat_w, bat_l, bat_h]);
    if (show_pcb) {
        if (pcb_stl == "") pcb_proxy();
        else color("darkgreen") translate([-hp, hp, boss_h]) import(pcb_stl);
        xiao_proxy();
    }
}

if (part == "frame") frame();
else if (part == "battery_holder") battery_holder();
else if (part == "motor_boot") motor_boot();
else if (part == "prop_envelope") quad4() translate([A, A, motor_top + blade_lo]) cylinder(d = prop_d, h = blade_hi - blade_lo, $fn = 96);
else assembly();
