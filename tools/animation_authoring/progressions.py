"""Progression movements: archer pull/push, pike and wall handstand push-ups, L-sits,
single-leg strength, nordic negatives, Y-T-W and the half-squat jump.

Closed chains (two hands on a bar, hands plus toes, a foot plus a bench) are written as dense
keyframes. Every frame is solved with two-bone IK that keeps the anatomical bend (elbows flex
toward the front of the upper arm, knees bend forward) and is then re-pinned on its loaded
contact, so interpolation stays well inside the 0.2-unit tolerance checked by
tools/validate_progression_motions.py.
"""
from author import *


def _pose(name, **kw):
    assert name not in POSES, f"pose {name} already exists"
    return pose(name, **kw)


def _template(name, *args, **kw):
    assert name not in TEMPLATES, f"template {name} already exists"
    return template(name, *args, **kw)


def _ease(t):
    return t * t * (3 - 2 * t)


def _lerp(a, b, t):
    return [a[i] + (b[i] - a[i]) * t for i in range(len(a))]


def _signed(a):
    return (a + 180.0) % 360.0 - 180.0


def _both(dx, dy, a, b):
    """Both two-bone IK solutions (same clamping as author.ik)."""
    dist = math.hypot(dx, dy)
    dist = max(min(dist, a + b - 0.01), abs(a - b) + 0.01)
    cos_r = max(-1.0, min(1.0, (a * a + dist * dist - b * b) / (2 * a * dist)))
    r = math.degrees(math.acos(cos_r))
    base = ang(dx, dy)
    out = []
    for side in (1, -1):
        a1 = base + side * r
        jx, jy = d(a1)[0] * a, d(a1)[1] * a
        out.append((round(a1 % 360, 1), round(ang(dx - jx, dy - jy) % 360, 1)))
    return out


def _facing(name):
    return pv.Pose.from_json(name, POSES, {}).facing


def arm_to(name, target, which=("arm_near",)):
    """Hand on target with the elbow bending the anatomical way for the pose's facing."""
    sx, sy = solved(name)["shoulder"]
    f = _facing(name)
    best = max(_both(target[0] - sx, target[1] - sy, UPPER, FORE), key=lambda s: _signed(s[0] - s[1]) * f)
    for w in which:
        POSES[name][w] = list(best)
    return best


def leg_to(name, target, which=("leg_near",)):
    """Ankle on target with the knee bending forward for the pose's facing."""
    hx, hy = solved(name)["hip"]
    f = _facing(name)
    best = max(_both(target[0] - hx, target[1] - hy, THIGH, SHIN), key=lambda s: _signed(s[1] - s[0]) * f)
    for w in which:
        POSES[name][w] = list(best)
    return best


def repin(name, joint):
    """Anchor the solved configuration on one of its contacts."""
    j = solved(name)[joint]
    POSES[name].pop("hip", None)
    POSES[name]["pin"] = {"joint": joint, "at": [round(j[0], 3), round(j[1], 3)]}


def dense(prefix, build, count, span=0.85, hold=True):
    """Keyframes 0..span over an eased parameter, optionally holding the last frame to 1."""
    frames = [(round(span * i / count, 4), build(f"{prefix}_{i}", _ease(i / count))) for i in range(count + 1)]
    if hold and span < 1:
        frames.append((1.0, frames[-1][1]))
    return frames


# ---------- archer pull-up (high bar) ----------
# The side view cannot show grip width, so the far hand sits further along the bar and the far
# arm stays nearly straight while the shoulder circles toward the bent near arm.
ARCHER_NEAR, ARCHER_FAR, ARCHER_REACH = (92.0, 12.0), (110.0, 12.0), 25.85


def _archer_pull(key, s):
    # At the top the shoulders reach bar height, so the chin clears the bar (round 5).
    tilt = math.radians(20.6 + (87.0 - 20.6) * s)
    shoulder = [ARCHER_FAR[0] - ARCHER_REACH * math.sin(tilt), ARCHER_FAR[1] + ARCHER_REACH * math.cos(tilt)]
    _pose(key, facing=1, torso=(-6 * s) % 360, shrug=-1.5 * (1 - s), legs=[180 - 4 * s, 181 + 11 * s],
          leg_far=[181 - 4 * s, 182 + 13 * s], feet=180, pin={"joint": "shoulder", "at": shoulder})
    arm_to(key, ARCHER_NEAR, ("arm_near",))
    arm_to(key, ARCHER_FAR, ("arm_far",))
    repin(key, "hand_near")
    return key


_template("archer_pullup", 2600, dense("archer_pull", _archer_pull, 16), apparatus=("high_bar",), easing="linear")


# ---------- archer push-up (floor) ----------
# Toes and the near hand stay planted; the far arm stays nearly straight and slides forward.
def _archer_push(key, s):
    # Round 5: the near hand sits under the shoulder and the top starts with both arms locked.
    a = 295.6 - 15.6 * s
    _pose(key, facing=-1, torso=a, head=a + 10 * s, legs=[a - 180, a - 180], feet=180,
          pin={"joint": "foot_near", "at": [146, 101]})
    arm_to(key, (101, 105), ("arm_near",))
    sx, sy = solved(key)["shoulder"]
    arm_to(key, (sx - math.sqrt(max(0.0, 25.98 ** 2 - (105 - sy) ** 2)), 105), ("arm_far",))
    return key


_template("archer_pushup", 2600, dense("archer_push", _archer_push, 12), easing="linear")


# ---------- elevated pike push-up (toes on box_right) ----------
# Straight legs pivot on the toes; the head travels down and forward of the hands.
PIKE_HAND, PIKE_TOE, PIKE_FOOT = (89.0, 105.0), (124.0, 80.0), 190.0
PIKE_ANKLE = (PIKE_TOE[0] - FOOT * d(PIKE_FOOT)[0], PIKE_TOE[1] - FOOT * d(PIKE_FOOT)[1])


def _upper_meet(p, rp, q, rq):
    """Higher intersection of circle(p, rp) and circle(q, rq)."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    dist = math.hypot(dx, dy)
    a = (rp * rp - rq * rq + dist * dist) / (2 * dist)
    h = math.sqrt(max(rp * rp - a * a, 0.0))
    mx, my = p[0] + a * dx / dist, p[1] + a * dy / dist
    options = [(mx - h * dy / dist, my + h * dx / dist), (mx + h * dy / dist, my - h * dx / dist)]
    return min(options, key=lambda o: o[1])


def _pike(key, s):
    neck = _lerp([89.3, 79.15], [85.0, 92.5], s)
    hip = _upper_meet(neck, TORSO, PIKE_ANKLE, THIGH + SHIN)
    torso = ang(neck[0] - hip[0], neck[1] - hip[1])
    legs = ang(PIKE_ANKLE[0] - hip[0], PIKE_ANKLE[1] - hip[1])
    _pose(key, facing=-1, torso=round(torso, 2), head=round(torso + (225 - torso) * s, 2), legs=[legs, legs],
          feet=PIKE_FOOT, pin={"joint": "toe_near", "at": list(PIKE_TOE)})
    arm_to(key, PIKE_HAND, ("arm_near", "arm_far"))
    return key


_template("elevated_pike_pushup", 2400, dense("elevated_pike", _pike, 12), apparatus=("box_right",), easing="linear")


# ---------- wall handstand push-up (chest to wall) ----------
# The handstand_wall shape with the hands a little further from the wall, so the elbows can
# bend toward it without touching it. The toes slide down the wall and the head stops just
# above a folded mat, slightly behind the hands so the forearms never cross the mat.
APPARATUS["hspu_mat"] = [{"type": "round_rect", "values": [124, 102.5, 135, FLOOR], "filled": True}]
HSPU_HAND = (137.0, 105.0)
# Straight line from the hands to the ankle (x 145), so the toes (5 long) touch the wall at x 150.
_HSPU_LEAN = (145.0 - HSPU_HAND[0]) / (UPPER + FORE + TORSO + THIGH + SHIN)
HSPU_TOP = (HSPU_HAND[0] + (UPPER + FORE) * _HSPU_LEAN, HSPU_HAND[1] - (UPPER + FORE) * math.sqrt(1 - _HSPU_LEAN ** 2))
HSPU_BOTTOM = (133.0, 89.9)


def _hspu(key, s):
    sx, sy = _lerp(HSPU_TOP, HSPU_BOTTOM, s)
    lean = math.degrees(math.asin((145.0 - sx) / (TORSO + THIGH + SHIN)))
    _pose(key, base="handstand_wall", torso=round(180 + lean, 2), legs=[lean, lean], head=180 + 5 * s,
          pin={"joint": "shoulder", "at": [sx, sy]})
    arm_to(key, HSPU_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


down = dense("hspu_down", _hspu, 14, span=1.0, hold=False)
# Round 5: a slow lowering (about 3.5 s) and a pause on the mat; the athlete comes down and the guide cuts back to the
# top instead of pressing up.
wall_hspu_negative = [(0, down[0][1])] + [(round(0.08 + 0.64 * t, 4), k) for t, k in down] + [(1.0, down[-1][1])]
_template("wall_hspu_negative", 5600, wall_hspu_negative, mode="loop", apparatus=("wall_right", "hspu_mat"), easing="linear")
_template("wall_hspu", 2800, [(0, down[0][1])] + [(round(0.1 + 0.75 * t, 4), k) for t, k in down] + [(1, down[-1][1])],
          apparatus=("wall_right", "hspu_mat"), easing="linear")


# ---------- L-sits on parallettes (straight arms) ----------
_pose("one_leg_lsit_hold", base="lsit_floor", shrug=-2.5, leg_near=[90, 90], foot_near=95, leg_far=[50, 150], foot_far=160)
_template("one_leg_lsit", 2600, [(0, {"pose": "one_leg_lsit_hold", "shrug": -1, "leg_near": [96, 96], "leg_far": [54, 154]}),
                                 (0.6, "one_leg_lsit_hold"), (1, "one_leg_lsit_hold")], apparatus=("parallettes",))
_pose("lsit_hold", base="lsit_floor", shrug=-2.5, legs=[90, 90], leg_far=[91, 91], feet=95)
_template("lsit", 2600, [(0, {"pose": "lsit_hold", "shrug": -1, "legs": [96, 96], "leg_far": [97, 97]}),
                         (0.6, "lsit_hold"), (1, "lsit_hold")], apparatus=("parallettes",))


# ---------- seated pike lift ----------
_pose("pike_sit_down", facing=1, torso=10, head=8, shrug=-1.5, legs=[97.4, 97.4], leg_far=[97.8, 97.8], feet=102, hip=[100, 100])
arm_to("pike_sit_down", (113, 105), ("arm_near", "arm_far"))
_pose("pike_sit_lift", base="pike_sit_down", legs=[90.5, 90.5], leg_far=[90.9, 90.9], feet=96)
_template("seated_pike_lift", 2400, [(0, "pike_sit_down"), (0.7, "pike_sit_lift"), (1, "pike_sit_lift")])


# ---------- Bulgarian split squat (rear foot on a knee-high bench) ----------
APPARATUS["split_bench"] = [{"type": "round_rect", "values": [46, 90, 78, FLOOR], "filled": True}]
BSS_FRONT, BSS_REAR = (112.0, ANKLE), (79.0, 88.0)


def _bss(key, s):
    torso = 6 + 8 * s
    _pose(key, facing=1, torso=torso, head=torso - 4, arm_near=[178, 168], arm_far=[182, 172], foot_near=90, foot_far=270,
          pin={"joint": "hip", "at": _lerp([100.0, 76.0], [97.5, 88.5], s)})
    leg_to(key, BSS_FRONT, ("leg_near",))
    leg_to(key, BSS_REAR, ("leg_far",))
    repin(key, "foot_near")
    return key


_template("bulgarian_split_squat", 2400, dense("bulgarian", _bss, 12), apparatus=("split_bench",), easing="linear")


# ---------- sliding leg curl (shoulders down, hips lifted, heels slide) ----------
def _leg_curl(key, s):
    # Round 5: shoulders, hips and knees stay in one line in the curl (no arched back), and as the heels slide out the
    # hips stay just off the floor, never more than a few degrees past that line.
    foot = (101 + 18.7 * s, ANKLE)
    best = None
    for tenth in range(2500, 2701):
        torso = tenth / 10
        POSES.pop(key, None)
        _pose(key, facing=1, torso=torso, head=270, arms=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
        leg_to(key, foot, ("leg_near", "leg_far"))
        j = solved(key)
        line = math.degrees(math.atan2(j["hip"][1] - j["neck"][1], j["hip"][0] - j["neck"][0]))
        thigh = math.degrees(math.atan2(j["knee_near"][1] - j["hip"][1], j["knee_near"][0] - j["hip"][0]))
        miss = abs((thigh - line) - 5 * s) + (100 if j["hip"][1] > 97.5 else 0)
        if best is None or miss < best[0]:
            best = (miss, torso)
    del POSES[key]
    _pose(key, facing=1, torso=best[1], head=270, arms=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
    leg_to(key, foot, ("leg_near", "leg_far"))
    return key


_template("sliding_leg_curl", 2800, dense("leg_curl", _leg_curl, 12), easing="linear")


# ---------- nordic negative (knees down, ankles under a low anchor) ----------
NORDIC_KNEE = (124.0, 102.0)
APPARATUS["nordic_anchor"] = [
    {"type": "circle", "values": [139, 97.4, 2.6]},
    {"type": "line", "values": [141.6, 97.4, 150, 97.4]},
    {"type": "line", "values": [150, 97.4, 150, FLOOR]},
]


def _nordic(name, theta, head_drop=0.0):
    _pose(name, facing=-1, torso=(360 - theta) % 360, head=(360 - theta - head_drop) % 360, legs=[180 - theta, 90], feet=90,
          arms=[(210 - theta) % 360, (320 - theta) % 360], pin={"joint": "knee_near", "at": list(NORDIC_KNEE)})
    return name


_nordic("nordic_up", 0)
_nordic("nordic_low", 64, head_drop=20)
_nordic("nordic_catch", 72, head_drop=33)
arm_to("nordic_catch", (80, 105), ("arm_near", "arm_far"))
_template("nordic_negative", 3600, [(0, "nordic_up"), (0.1, "nordic_up"), (0.7, "nordic_low"), (0.78, "nordic_catch"),
                                    (0.88, "nordic_catch"), (1, "nordic_up")],
          mode="loop", apparatus=("nordic_anchor",), easing="linear")


# ---------- single-leg Romanian deadlift (stance foot planted) ----------
RDL_FOOT = {"joint": "foot_far", "at": [96, ANKLE]}
_pose("sl_rdl_stand", facing=1, torso=0, arms=[180, 180], leg_far=[177, 183], foot_far=90,
      leg_near=[182, 225], foot_near=100, pin=RDL_FOOT)
_pose("sl_rdl_hinge", facing=1, torso=82, head=88, arms=[180, 180], leg_far=[163, 178], foot_far=90,
      leg_near=[262, 262], foot_near=172, pin=RDL_FOOT)
_template("single_leg_rdl", 2800, [(0, "sl_rdl_stand"), (0.8, "sl_rdl_hinge"), (1, "sl_rdl_hinge")])


# ---------- prone Y-T-W (body stays on the floor) ----------
# Y: straight arms overhead, just lifted. T: the arm points at the viewer, so only a short
# raised stub shows above the shoulder. W: elbows drawn back toward the ribs, hands by the head.
_pose("ytw_y", base="arch_flat", leg_far=[89.6, 89.6], head=273, arm_near=[283, 283], arm_far=[286, 286])
# Round 5: the T points the arms out of the picture, toward the viewer, so they fold to a short stub by the shoulder
# instead of a vertical spike.
_pose("ytw_t", base="ytw_y", arm_near=[278, 98], arm_far=[281, 101])
_pose("ytw_w", base="ytw_y", head=274, arm_near=[80, 292], arm_far=[84, 296])
_template("prone_ytw", 3600, [(0, "ytw_y"), (0.18, "ytw_y"), (0.33, "ytw_t"), (0.5, "ytw_t"), (0.66, "ytw_w"), (0.84, "ytw_w"), (1, "ytw_y")],
          mode="loop", apparatus=("mat",))


# ---------- squat jump from a half squat (loop) ----------
_pose("hsj_half", base="stand", torso=20, head=10, arm_near=[215, 205], arm_far=[219, 209], feet=90,
      leg_near=leg(4, 23.5, RIGHT), leg_far=leg(5, 23.5, RIGHT))
_pose("hsj_extend", base="stand", torso=4, head=2, arm_near=[15, 10], arm_far=[19, 14], legs=[180, 180], leg_far=[179, 181], feet=90)
_pose("hsj_air", base="hsj_extend", torso=0, head=0, arm_near=[10, 6], arm_far=[14, 10], leg_far=[182, 183], feet=150,
      pin={"joint": "foot_near", "at": [100, ANKLE - 13]})
_pose("hsj_land", base="hsj_half", arm_near=[110, 95], arm_far=[114, 99])
# The drive and touchdown frames route the arm swing forward (shortest-angle interpolation
# would otherwise circle the arms backward over the head).
_pose("hsj_drive", base="stand", torso=12, head=6, arm_near=[110, 100], arm_far=[114, 104], feet=90,
      leg_near=leg(2, 28, RIGHT), leg_far=leg(3, 28, RIGHT))
_pose("hsj_touch", base="hsj_extend", arm_near=[60, 50], arm_far=[64, 54])
_template("half_squat_jump", 2200, [(0, "hsj_half"), (0.16, "hsj_half"), (0.24, "hsj_drive"), (0.32, "hsj_extend"),
                                    (0.42, "hsj_air"), (0.52, "hsj_touch"), (0.66, "hsj_land"), (0.84, "hsj_land"),
                                    (1, "hsj_half")], mode="loop", easing="linear")
