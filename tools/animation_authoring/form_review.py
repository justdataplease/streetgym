"""Round 5 of the pro-athlete reviews (27 October 2026): a coach's pass over every guide.

The reviewer solved every keyframe and measured joint angles and contacts against the written coaching; this module
replaces the guides that taught the wrong shape, as contact_corrections.py did before it. Closed chains follow
progressions.py: dense keyframes, each solved with two-bone IK that keeps the anatomical bend and pinned on its
loaded contact. Range lines and phase labels that the app cannot work out from a clip are set per template at the end
("marker" and "phases").
"""
from author import *
from progressions import _ease, _lerp, arm_to, dense, leg_to, repin


def _body_angles(foot, bar, lo=250.0):
    """A straight body pivoting on its feet: the angle where the arms just lock at the bottom, and the angle where the
    bar meets the sternum (two units off the body line, near the shoulders) at the top."""
    reach = TORSO + THIGH + SHIN

    def shoulder(a):
        return foot[0] + reach * d(a)[0], foot[1] + reach * d(a)[1]

    def off_line(a):
        u = d(a)
        return (bar[0] - foot[0]) * u[1] - (bar[1] - foot[1]) * u[0]

    bottom = top = None
    a = lo
    while a < 360 and top is None:
        s = shoulder(a)
        if bottom is None and math.hypot(s[0] - bar[0], s[1] - bar[1]) <= UPPER + FORE - 0.03:
            bottom = a
        if off_line(a) >= -2.0:
            top = a
        a += 0.1
    return round(bottom, 1), round(top, 1)


# ---------- rows: straight arms at the bottom, the bar at the sternum at the top ----------
# The inverted row's feet move in under the bar; the high row gets a chest-height bar (the head-height bar can't be
# reached with the feet down); the feet-elevated row hangs level from the low bar with the heels on a low box.
APPARATUS["chest_bar"] = [
    {"type": "line", "values": [60, 60, 140, 60]},
    {"type": "line", "role": "floor", "values": [64, 60, 64, FLOOR]},
    {"type": "line", "role": "floor", "values": [136, 60, 136, FLOOR]},
]
APPARATUS["row_box"] = [{"type": "round_rect", "values": [112, 92, 148, FLOOR], "filled": True}]
ROWS = {
    "row": ((106.0, ANKLE), (80.0, 66.0), ("low_bar",)),
    "high_row": ((113.0, ANKLE), (100.0, 60.0), ("chest_bar",)),
    "feet_elevated_row": ((120.0, 90.0), (80.0, 66.0), ("low_bar", "row_box")),
}
def _row(name, foot, bar, apparatus):
    """Hands on the bar, feet planted, a straight body pivoting on the feet; the elbows bend the anatomical way."""
    start, end = _body_angles(foot, bar)
    frames = []
    for i in range(41):
        t = i / 40
        a = start + (end - start) * t
        key = f"{name}_contact_{i}"
        pose(key, facing=1, torso=a, legs=[a + 180, a + 180], feet=90, pin={"joint": "foot_near", "at": list(foot)})
        arm_to(key, bar, ("arm_near", "arm_far"))
        frames.append((t, key))
    template(name, 2400, frames, apparatus=apparatus, easing="linear")


for _name, (_foot, _bar, _apparatus) in ROWS.items():
    _row(_name, _foot, _bar, _apparatus)


# ---------- tuck front-lever row (low bar) ----------
# Hips stay folded (the thighs at least 100 degrees from the trunk line) throughout. It starts as a tuck hang under the
# bar with straight arms, the hips low and the toes touching the floor lightly, and rows until the shoulders reach the
# bar with the trunk level and the hips at shoulder height; the feet leave the floor on the way up.
TFR_HAND = (80.0, 66.0)
TFR_BOTTOM = (TFR_HAND[0] - 26 * math.sin(math.radians(15)) + 0.05, TFR_HAND[1] + 26 * math.cos(math.radians(15)) - 0.05)
TFR_TOP = (75.5, 70.0)


def _tuck_row(key, s):
    shoulder = _lerp(list(TFR_BOTTOM), list(TFR_TOP), s)
    torso = 300.0 + (270.0 - 300.0) * s
    hip_flex, knee_flex = 105.0 + 10 * s, 150.0 - 10 * s
    thigh = (torso + 180 - hip_flex) % 360
    pose(key, facing=1, torso=round(torso, 2), head=round(torso + 8, 2), shrug=-1,
         legs=[round(thigh, 1), round((thigh + knee_flex) % 360, 1)], feet=round((thigh + knee_flex + 20) % 360, 1),
         pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_to(key, TFR_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


template("tuck_front_row", 2600, dense("tfr", _tuck_row, 12), apparatus=("low_bar",), easing="linear")


# ---------- toes to bar: the lats pull the shoulders back behind the bar while the straight legs pike up ----------
def _hang_reach(name, shoulder_dx, torso, shrug=-1.0, hand=(100.0, 12.0)):
    """A straight-arm hang with the shoulders shoulder_dx behind (negative) or in front of the hands."""
    sy = hand[1] + math.sqrt((UPPER + FORE - 0.02) ** 2 - shoulder_dx ** 2)
    pose(name, facing=1, torso=torso, shrug=shrug, pin={"joint": "shoulder", "at": [hand[0] + shoulder_dx, sy]})
    arm_to(name, hand, ("arm_near", "arm_far"))
    repin(name, "hand_near")
    return name


_hang_reach("t2b_swing", -6.0, 345.0)
POSES["t2b_swing"].update(legs=[100, 100], leg_far=[102, 102], feet=110)
_hang_reach("t2b_top", -10.0, 292.0)
_hip = solved("t2b_top")["hip"]
_legs = ang(101.5 - _hip[0], 13.5 - _hip[1])
POSES["t2b_top"].update(legs=[round(_legs, 1)] * 2, leg_far=[round(_legs + 2, 1)] * 2, feet=round(_legs + 20, 1))
template("toes_to_bar", 2200, [(0, "hang_active"), (0.5, "t2b_swing"), (1, "t2b_top")], apparatus=("high_bar",))


# ---------- relaxed hang: a still hang, not scapular pulls ----------
template("relaxed_hang", 3000, [(0, "hang_relaxed"), (1, "hang_relaxed")], mode="loop", apparatus=("high_bar",))


# ---------- levers balance under the bar ----------
# The hands sit over the body's centre of mass, not over the shoulders: the arms lean about 15 degrees toward the hips
# in the tucks and 30-35 in the long shapes (both levers have the hips to the right), so the shoulders sit toward the
# head from the bar.
for _shape, _lean in {"fl_tuck": 15, "fl_tuck_low": 12, "fl_adv": 22, "fl_one_leg": 27, "fl_straddle": 31, "fl_full": 33,
                      "bl_tuck": 15, "bl_tuck_high": 12, "bl_adv": 22, "bl_one_leg": 27, "bl_straddle": 31, "bl_full": 33}.items():
    POSES[_shape]["arms"] = [float(_lean)] * 2


# ---------- push-ups: locked and protracted at the top, hands under the lower chest at the bottom ----------
# The hands move back under the shoulders, the top body angle is the one where the arms are straight, and the elbows
# point back toward the hips (forearms near vertical) at the bottom.
from group_c import prone_pose

PUSH_FOOT, PUSH_HAND = (146.0, 101.0), (101.0, 105.0)


def _lock_angle(foot, hand, lo, hi):
    """The body angle (pivoting on the feet) at which the shoulder is exactly an arm's length from the hand."""
    reach = TORSO + THIGH + SHIN
    for _ in range(50):
        a = (lo + hi) / 2
        s = (foot[0] + reach * d(a)[0], foot[1] + reach * d(a)[1])
        if math.hypot(s[0] - hand[0], s[1] - hand[1]) > UPPER + FORE - 0.02:
            hi = a
        else:
            lo = a
    return round(lo, 2)


def push_chain(name, foot, hand, start, end, apparatus=(), pause=False, duration=2400, feet=180, pref=RIGHT):
    frames = []
    for i in range(25):
        t = i / 24
        a = start + (end - start) * t
        key = f"{name}_lock_{i}"
        pose(key, facing=-1, torso=a, legs=[a - 180, a - 180], feet=feet, pin={"joint": "foot_near", "at": list(foot)})
        reach_arm(key, hand, pref=pref)
        frames.append((t * (0.7 if pause else 1), key))
    if pause:
        frames.append((1, frames[-1][1]))
    template(name, duration + (400 if pause else 0), frames, apparatus=apparatus, easing="linear")


_top = _lock_angle(PUSH_FOOT, PUSH_HAND, 280, 300)
push_chain("pushup", PUSH_FOOT, PUSH_HAND, _top, 276)
push_chain("pause_pushup", PUSH_FOOT, PUSH_HAND, _top, 276, pause=True)
push_chain("close_pushup", PUSH_FOOT, PUSH_HAND, _top, 277)
INCLINE_FOOT, INCLINE_HAND = (105.0, 101.0), (60.0, 82.0)
push_chain("incline_pushup", INCLINE_FOOT, INCLINE_HAND, _lock_angle(INCLINE_FOOT, INCLINE_HAND, 300, 340), 306,
           apparatus=("box_left",))

# Pseudo-planche push-up: the hands sit back by the waist, so at the locked top the shoulders lean ten units ahead of
# them and at the bottom fifteen.
push_chain("pseudo_planche_pushup", (148.0, 102.0), (112.0, 105.0), _lock_angle((148.0, 102.0), (112.0, 105.0), 280, 300),
           276, duration=2000)


def lock_chain(name, foot, hand, protract, duration, apparatus=()):
    """Straight arms between planted hands and toes: only the shoulder blades move (protract from one value to the
    other), so the trunk rises and sinks a little over the locked arms."""
    frames = []
    for i in range(13):
        t = i / 12
        p = protract[0] + (protract[1] - protract[0]) * _ease(t)
        key = f"{name}_blades_{i}"
        lo, hi = 260.0, 340.0
        for _ in range(50):
            a = (lo + hi) / 2
            pose(key, facing=-1, torso=a, legs=[a - 180, a - 180], feet=180, protract=p,
                 pin={"joint": "foot_near", "at": list(foot)})
            sx, sy = solved(key)["shoulder"]
            if math.hypot(sx - hand[0], sy - hand[1]) > UPPER + FORE - 0.02:
                hi = a
            else:
                lo = a
        reach_arm(key, hand, pref=RIGHT)
        frames.append((t, key))
    template(name, duration, frames, apparatus=apparatus, easing="linear")


lock_chain("scapular_pushup", PUSH_FOOT, PUSH_HAND, (-2.5, 3.0), 1800)
lock_chain("scapular_pushup_incline", INCLINE_FOOT, INCLINE_HAND, (-2.5, 3.0), 1800, ("box_left",))
lock_chain("incline_plank", INCLINE_FOOT, INCLINE_HAND, (0.0, 2.5), 3000, ("box_left",))
# The forearm plank spreads the shoulder blades without sinking the forearms into the floor.
prone_pose("plank_forearm_spread", 92, extra={"arms": [180, 270]}, protract=2)
template("plank_forearm", 3000, [(0, "plank_forearm"), (1, "plank_forearm_spread")])


# ---------- pike push-up (feet on the floor), the elevated version's geometry ----------
# Straight arms at the top with the hips high over the hands; at the bottom the elbows bend to about 110 degrees and the
# head lowers ahead of the hands toward the floor.
FPIKE_HAND, FPIKE_TOE, FPIKE_FOOT = (84.0, 105.0), (113.0, FLOOR), 205.0
FPIKE_ANKLE = (FPIKE_TOE[0] - FOOT * d(FPIKE_FOOT)[0], FPIKE_TOE[1] - FOOT * d(FPIKE_FOOT)[1])


def _upper_meet(p, rp, q, rq):
    dx, dy = q[0] - p[0], q[1] - p[1]
    dist = math.hypot(dx, dy)
    a = (rp * rp - rq * rq + dist * dist) / (2 * dist)
    h = math.sqrt(max(rp * rp - a * a, 0.0))
    mx, my = p[0] + a * dx / dist, p[1] + a * dy / dist
    return min(((mx - h * dy / dist, my + h * dx / dist), (mx + h * dy / dist, my - h * dx / dist)), key=lambda o: o[1])


def _floor_pike(key, s):
    neck = _lerp([86.6, 79.08], [83.0, 90.0], s)
    hip = _upper_meet(neck, TORSO, FPIKE_ANKLE, THIGH + SHIN)
    torso = ang(neck[0] - hip[0], neck[1] - hip[1])
    legs = ang(FPIKE_ANKLE[0] - hip[0], FPIKE_ANKLE[1] - hip[1])
    pose(key, facing=-1, torso=round(torso, 2), head=round(torso + (209 - torso) * s, 2), legs=[legs, legs],
         feet=FPIKE_FOOT, pin={"joint": "toe_near", "at": list(FPIKE_TOE)})
    arm_to(key, FPIKE_HAND, ("arm_near", "arm_far"))
    return key


template("pike_pushup", 2200, dense("floor_pike", _floor_pike, 12), easing="linear")


# ---------- wall push-up: the feet stand back so the arms are straight at the top ----------
WALL_HAND = (149.0, 50.0)
def _wall_push(key, s):
    a = 22.8 + (45.0 - 22.8) * s
    pose(key, facing=1, torso=a, legs=[a + 180, a + 180], feet=90, pin={"joint": "foot_near", "at": [104.2, ANKLE]})
    reach_arm(key, WALL_HAND, pref=DOWN)
    return key


template("wall_pushup", 2200, dense("wall_push", _wall_push, 12, span=1.0, hold=False), apparatus=("wall_right",),
         easing="linear")


# ---------- bent-leg lowering: knees held at 90 degrees, heels lowered toward the floor ----------
pose("tabletop", base="lying_feet_down", legs=[0, 90], leg_far=[2, 92], feet=90)
pose("tabletop_low", base="lying_feet_down", legs=[55, 145], leg_far=[57, 147], feet=145)
template("bent_leg_lower", 2600, [(0, "tabletop"), (1, "tabletop_low")], apparatus=("mat",))


# ---------- reverse lunge and split squat: shin vertical over the front foot, back knee just off the floor ----------
# The front foot stays planted where the athlete stood; the back knee lowers to about three units above the floor with
# the back foot on the ball of the foot and the trunk tall.
LUNGE_FRONT = (100.0, ANKLE)
BACK_ANKLE_Y, BACK_FOOT = 101.5, 155.0   # heel up: the foot points down and forward to the ball of the foot


def _back_ankle(hip, knee_y=103.0):
    """Where the back ankle sits so that the back knee hovers at knee_y below this hip (knee behind the hip)."""
    knee = (hip[0] - math.sqrt(THIGH ** 2 - (knee_y - hip[1]) ** 2), knee_y)
    return knee[0] - math.sqrt(SHIN ** 2 - (BACK_ANKLE_Y - knee_y) ** 2), BACK_ANKLE_Y


def _lunge_bottom(name, front, hip):
    pose(name, facing=1, torso=5, arms=[178, 178], foot_near=90, foot_far=BACK_FOOT, hip=list(hip))
    leg_to(name, front, ("leg_near",))
    leg_to(name, _back_ankle(hip), ("leg_far",))
    repin(name, "foot_near")
    return name


_lunge_bottom("lunge_back", LUNGE_FRONT, (LUNGE_FRONT[0] - 15.5, 89.0))
template("reverse_lunge", 2000, [(0, "stand"), (0.85, "lunge_back"), (1, "lunge_back")])
# Split squat: both feet stay where they are; the hips lower straight down between them, a hand on the post.
SPLIT_FRONT = (128.0, ANKLE)
SPLIT_BACK = _back_ankle((SPLIT_FRONT[0] - 15.5, 89.0))


def _split(key, s):
    hip = _lerp([SPLIT_FRONT[0] - 14.0, 81.5], [SPLIT_FRONT[0] - 15.5, 89.0], s)
    pose(key, facing=1, torso=3 + 2 * s, arm_far=[180, 180], foot_near=90, foot_far=BACK_FOOT, hip=hip)
    leg_to(key, SPLIT_FRONT, ("leg_near",))
    leg_to(key, SPLIT_BACK, ("leg_far",))
    reach_arm(key, (138, 58), pref=DOWN, which=("arm_near",))
    repin(key, "foot_near")
    return key


template("split_squat", 2200, dense("split", _split, 10), apparatus=("post_right",), easing="linear")


# ---------- glute bridge: shoulders, hips and knees in one line at the top, not an arched back ----------
pose("bridge_down", facing=1, torso=270, arm_near=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
reach_leg("bridge_down", (110, ANKLE), pref=UP)
pose("bridge_up", facing=1, torso=254, arm_near=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
reach_leg("bridge_up", (110, ANKLE), pref=UP)
pose("sl_bridge_down", base="bridge_down", leg_far=[55, 55], foot_far=60)
pose("sl_bridge_up", base="bridge_up", leg_far=[74, 74], foot_far=80)


# ---------- straight-leg side plank's knee version: the lower knee bent on the floor, the top leg long ----------
# The guide faces the viewer, so the bent lower leg's shin lies along the floor behind the knee.
pose("knee_side_plank", base="side_plank_grounded")
_hip = solved("knee_side_plank")["hip"]
_knee = (_hip[0] + math.sqrt(THIGH ** 2 - (ANKLE - _hip[1]) ** 2), ANKLE)
POSES["knee_side_plank"]["leg_near"] = [round(ang(_knee[0] - _hip[0], _knee[1] - _hip[1]), 1), 90.0]
POSES["knee_side_plank"]["foot_near"] = 90
repin("knee_side_plank", "foot_far")
template("side_plank", 3000, [(0, "knee_side_plank"), (1, "knee_side_plank")])


# ---------- tucked hollow hold: shoulder blades just off the floor, knees over the hips ----------
pose("hollow", facing=1, torso=290, head=296, arms=[80, 80], legs=[12, 92], leg_far=[14, 94], feet=92, hip=[100, 100])
pose("hollow_press", base="hollow", torso=292, legs=[8, 96], leg_far=[10, 98], feet=96)

# ---------- tuck L-sit: the feet start on the floor, then lift ----------
pose("lsit_floor", facing=1, torso=0, arms=[180, 180], shrug=-2, feet=90, pin={"joint": "hand_near", "at": [100, 96]})
leg_to("lsit_floor", (122, ANKLE), ("leg_near", "leg_far"))


# ---------- step-up onto a low step: shin vertical, chest over the knee, then stand tall ----------
APPARATUS["low_step"] = [{"type": "round_rect", "values": [116, 90, 150, FLOOR], "filled": True}]
STEP_FOOT = (126.0, 88.0)


def _step(key, s):
    hip = _lerp([111.0, 81.0], [STEP_FOOT[0], STEP_FOOT[1] - THIGH - SHIN + 0.4], _ease(s))
    torso = 28 * (1 - _ease(s))
    pose(key, facing=1, torso=round(torso, 2), arms=[175, 175], foot_near=90, hip=hip)
    leg_to(key, STEP_FOOT, ("leg_near",))
    # The trailing foot pushes off the floor, rises clear of the step's edge, then comes up beside the step foot.
    if s <= 0.3:
        trail = (104.0, ANKLE)
    elif s <= 0.6:
        trail = _lerp([104.0, ANKLE], [109.0, 86.0], (s - 0.3) / 0.3)
    else:
        trail = _lerp([109.0, 86.0], [120.0, 80.0], (s - 0.6) / 0.4)
    leg_to(key, trail, ("leg_far",))
    POSES[key]["foot_far"] = round(90 + 30 * min(s / 0.3, 1.0), 1)
    repin(key, "foot_near")
    return key


template("step_up", 2400, dense("step", _step, 12), apparatus=("low_step",), easing="linear")


# ---------- bear crawl: opposite hand and foot travel together, knees hovering just off the floor ----------
CRAWL_HIP, CRAWL_SHOULDER_DX = (116.0, 83.0), 0.0
CRAWL_HANDS = (93.0, 104.0)      # front and back hand x
CRAWL_FEET = (128.0, 136.0)      # front and back ankle x


def _crawl(key, u):
    # u 0 -> 0.5: the near hand and far foot step forward (lifted); the other pair slides back on the floor.
    half = u < 0.5
    f = (u % 0.5) / 0.5
    lift = math.sin(math.pi * f) * 3.0
    fwd = lambda pair: pair[1] + (pair[0] - pair[1]) * f
    back = lambda pair: pair[0] + (pair[1] - pair[0]) * f
    near_hand = (fwd(CRAWL_HANDS), 105 - lift) if half else (back(CRAWL_HANDS), 105)
    far_hand = (back(CRAWL_HANDS), 105) if half else (fwd(CRAWL_HANDS), 105 - lift)
    far_foot = (fwd(CRAWL_FEET), 102 - lift) if half else (back(CRAWL_FEET), 102)
    near_foot = (back(CRAWL_FEET), 102) if half else (fwd(CRAWL_FEET), 102 - lift)
    pose(key, facing=-1, torso=284, head=278, feet=160, hip=list(CRAWL_HIP))
    arm_to(key, near_hand, ("arm_near",))
    arm_to(key, far_hand, ("arm_far",))
    leg_to(key, near_foot, ("leg_near",))
    leg_to(key, far_foot, ("leg_far",))
    return key


template("bear_crawl", 1800, [(round(i / 16, 4), _crawl(f"crawl_{i}", i / 16)) for i in range(17)], mode="loop",
         easing="linear")


# ---------- planches balance over the hands ----------
# The shoulders lean ahead of the hands until the hands sit under the centre of mass: about 12 units for the tuck,
# 14 for the advanced tuck and 17 for the straddle and full planche.
for _shape, _ahead in {"tp_hold": 12.0, "tp_adv": 14.0, "tp_straddle": 17.0, "tp_full": 17.0}.items():
    POSES[_shape]["arms"] = [round(180 - math.degrees(math.asin(_ahead / (UPPER + FORE))), 1)] * 2


# ---------- strict muscle-up (head-height bar) ----------
# The body stays behind the bar until the shoulders reach it, the shoulders pass over the bar close to it, and the rep
# ends in a locked straight-bar support with the bar at the hips. The hang holds the straight legs together a little in
# front (a hollow pike), which keeps them off the floor under this bar.
MU_HAND = (100.0, 40.0)
MU_U = [0.0, 0.35, 0.55, 0.7, 0.85, 1.0]
MU_SX = [100.0, 95.5, 96.0, 102.5, 102.0, 101.5]
MU_SY = [MU_HAND[1] + UPPER + FORE - 0.02, 47.0, 36.0, 31.0, 22.0, MU_HAND[1] - UPPER - FORE + 0.02]
MU_TORSO = [0.0, -10.0, 18.0, 30.0, 12.0, 6.0]
MU_THIGH = [120.0, 150.0, 185.0, 195.0, 185.0, 178.0]
MU_SHIN = [120.0, 150.0, 185.0, 190.0, 182.0, 178.0]


def _track(us, values, u):
    for u0, u1, v0, v1 in zip(us, us[1:], values, values[1:]):
        if u <= u1:
            return v0 + (v1 - v0) * (0.0 if u1 == u0 else (u - u0) / (u1 - u0))
    return values[-1]


def _muscleup(key, s):
    shoulder = [_track(MU_U, MU_SX, s), _track(MU_U, MU_SY, s)]
    thigh, shin = _track(MU_U, MU_THIGH, s), _track(MU_U, MU_SHIN, s)
    pose(key, facing=1, torso=round(_track(MU_U, MU_TORSO, s) % 360, 2), shrug=-1, legs=[round(thigh, 1), round(shin, 1)],
         leg_far=[round(thigh + 2, 1), round(shin + 2, 1)], feet=round(shin, 1),
         pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_to(key, MU_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


_mu = dense("mu_path", _muscleup, 20, span=1.0, hold=False)
for _alias, _s in (("mu_hang", 0.0), ("mu_pull", 0.35), ("mu_transition", 0.55), ("mu_press", 0.85), ("mu_support", 1.0)):
    _muscleup(_alias, _s)


def _out_and_back(path, start, top, hold, end):
    """The path up over [start, top], held to hold, then back down the same way to end."""
    up = [(round(start + (top - start) * t, 4), k) for t, k in path]
    down = [(round(hold + (end - hold) * (1 - t), 4), k) for t, k in reversed(path)]
    return up + down


template("muscleup", 4400, [(0, "mu_hang")] + _out_and_back(_mu, 0.05, 0.5, 0.6, 0.95) + [(1, "mu_hang")],
         mode="loop", apparatus=("mid_bar",), easing="linear")
# Negative: from the support, lower slowly back through the transition to the hang, then step down; the guide cuts back
# to the support instead of pulling up again.
template("negative_muscleup", 5600,
         [(0, "mu_support")] + [(round(0.06 + 0.7 * (1 - t), 4), k) for t, k in reversed(_mu)] +
         [(1, "mu_hang")], mode="loop", apparatus=("mid_bar",), easing="linear")
template("two_thirds_muscleup", 2600, [(0.0, "mu_hang")] + [(round(0.85 * t / 0.55, 4), k) for t, k in _mu if 0 < t <= 0.55] +
         [(1, "mu_transition")], apparatus=("mid_bar",), easing="linear")


# ---------- jump muscle-up on the low bar ----------
# Stand behind the low bar holding it, dip, jump and drive the hips to the bar while the shoulders pass over it, lock
# out in support, then step back down to the floor.
JMU_HAND, JMU_FOOT = (100.0, 66.0), (79.0, ANKLE)
JMU_U = [0.0, 0.15, 0.28, 0.42, 0.55, 0.68, 0.8, 0.92, 1.0]
JMU_SX = [75.5, 73.5, 80.0, 95.5, 103.0, 101.5, 101.5, 80.0, 75.5]
JMU_SY = [63.0, 69.0, 60.0, 47.0, 44.0, JMU_HAND[1] - UPPER - FORE + 0.02, JMU_HAND[1] - UPPER - FORE + 0.02, 60.0, 63.0]
JMU_TORSO = [0.0, 10.0, 18.0, 30.0, 22.0, 6.0, 6.0, 4.0, 0.0]


def _jump_mu(key, s):
    shoulder = [_track(JMU_U, JMU_SX, s), _track(JMU_U, JMU_SY, s)]
    torso = _track(JMU_U, JMU_TORSO, s)
    pose(key, facing=1, torso=round(torso, 2), shrug=-0.5, feet=90, pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    hip = solved(key)["hip"]
    hang = (hip[0] + 2.0, min(hip[1] + 29.0, 100.0))
    grounded = s <= 0.24 or s >= 0.96
    if grounded:
        foot = JMU_FOOT
    elif s < 0.34:
        foot = _lerp(list(JMU_FOOT), list(hang), (s - 0.24) / 0.1)
    elif s <= 0.86:
        foot = hang
    else:
        foot = _lerp(list(hang), list(JMU_FOOT), (s - 0.86) / 0.1)
    leg_to(key, foot, ("leg_near", "leg_far"))
    POSES[key]["leg_far"] = [POSES[key]["leg_far"][0] + 2, POSES[key]["leg_far"][1] + 2]
    if not grounded:
        POSES[key]["feet"] = round(POSES[key]["leg_near"][1], 1)
    arm_to(key, JMU_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


template("jump_muscleup", 4000, [(round(i / 40, 4), _jump_mu(f"jmu_{i}", i / 40)) for i in range(41)], mode="loop",
         apparatus=("low_bar",), easing="linear")


# ---------- pullover: finish in a front support (straight arms, bar at the hips), then cut back to the start ----------
POSES["po_top"] = dict(POSES["mu_support"])
template("pullover_lowbar", 3800,
         [(0, "lbt_under"), (0.18, "po_knees"), (0.38, "po_tuck"), (0.55, "po_over"), (0.75, "po_top"), (1, "po_top")],
         mode="loop", apparatus=("mid_bar", "mat"))
template("pullover", 3800,
         [(0, "mu_hang"), (0.18, "po_knees"), (0.38, "po_tuck"), (0.55, "po_over"), (0.75, "po_top"), (1, "po_top")],
         mode="loop", apparatus=("mid_bar",))


# ---------- low-bar transition: crouched on a step under the bar, the legs help the chest over it to support ----------
APPARATUS["transition_box"] = [{"type": "round_rect", "values": [86, 88, 118, FLOOR], "filled": True}]
LBT_HAND, LBT_FOOT = (100.0, 66.0), (104.0, 86.0)
LBT_U = [0.0, 0.5, 1.0]
LBT_SX, LBT_SY = [97.5, 103.0, 101.5], [66.5, 52.0, LBT_HAND[1] - UPPER - FORE + 0.02]
LBT_TORSO = [15.0, 25.0, 6.0]


def _lbt(key, s):
    shoulder = [_track(LBT_U, LBT_SX, s), _track(LBT_U, LBT_SY, s)]
    pose(key, facing=1, torso=round(_track(LBT_U, LBT_TORSO, s), 2), shrug=-1, feet=90,
         pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    leg_to(key, LBT_FOOT, ("leg_near", "leg_far"))
    arm_to(key, LBT_HAND, ("arm_near", "arm_far"))
    repin(key, "foot_near")
    return key


template("low_bar_transition", 3400, dense("lbt_path", _lbt, 16), apparatus=("low_bar", "transition_box"), easing="linear")


# ---------- straight-bar support on the waist-high bar; straight-bar dip with the bar at the lower chest ----------
pose("sb_low_support", facing=1, torso=8, shrug=-2, legs=[176, 180], leg_far=[178, 182], feet=180,
     pin={"joint": "shoulder", "at": [101.5, 66.0 - UPPER - FORE + 0.02]})
arm_to("sb_low_support", (100.0, 66.0), ("arm_near", "arm_far"))
repin("sb_low_support", "hand_near")
template("straight_bar_support", 2600, [(0, {"pose": "sb_low_support", "shrug": 1}), (1, "sb_low_support")], apparatus=("low_bar",))
pose("sb_dip_bottom", facing=1, torso=20, shrug=0, legs=[176, 215], leg_far=[178, 217], feet=200,
     pin={"joint": "shoulder", "at": [98.0, 35.0]})
arm_to("sb_dip_bottom", (100.0, 40.0), ("arm_near", "arm_far"))
repin("sb_dip_bottom", "hand_near")
template("straight_bar_dip", 2000, [(0, "sb_support"), (0.85, "sb_dip_bottom"), (1, "sb_dip_bottom")], apparatus=("mid_bar",))


# ---------- negatives: a slow lowering, then step down; no pulling or pressing back up ----------
# Each guide lowers over three to four seconds, steps down, and cuts back to the start for the next repetition.
pose("ecc_box_stand", facing=1, torso=0, arms=[0, 0], shrug=2, feet=90, pin={"joint": "hand_near", "at": [100, 12]})
leg_to("ecc_box_stand", (100.0, 90.0), ("leg_near", "leg_far"))
template("eccentric_pullup", 5600,
         [(0, "pull_top"), (0.06, "pull_top"), (0.4, "pull_mid"), (0.74, "hang_active"), (0.8, "hang_active"),
          (0.9, "ecc_box_stand"), (1, "ecc_box_stand")], mode="loop", apparatus=("high_bar", "box_under"))
# Dips: the knees bend so the feet stay clear of the step until the bottom, where they step onto it and the legs push the
# body back up to support (step back up, as the coaching says).
pose("ecc_dip_top", base="support_up", legs=[176, 255], leg_far=[178, 257], feet=245)
pose("ecc_dip_low", base="dip_bottom", legs=[168, 292], leg_far=[170, 294], feet=280)
# Before stepping down the feet come forward over the step, clear of its edge.
pose("ecc_dip_reach", base="dip_bottom", feet=90)
leg_to("ecc_dip_reach", (96.0, 68.0), ("leg_near", "leg_far"))
pose("ecc_dip_step", facing=1, torso=14, shrug=0, feet=90, pin={"joint": "hand_near", "at": [100, 50]})
POSES["ecc_dip_step"]["arms"] = POSES["dip_bottom"]["arms"]
leg_to("ecc_dip_step", (102.0, 72.0), ("leg_near", "leg_far"))
pose("ecc_dip_stand", facing=1, torso=2, arms=[180, 180], shrug=-1, feet=90, pin={"joint": "hand_near", "at": [100, 50]})
leg_to("ecc_dip_stand", (102.0, 72.0), ("leg_near", "leg_far"))
template("eccentric_dip", 6000,
         [(0, "ecc_dip_top"), (0.06, "ecc_dip_top"),
          (0.4, {"pose": "dip_half", "legs": [172, 280], "leg_far": [174, 282], "feet": 268}),
          (0.7, "ecc_dip_low"), (0.74, "ecc_dip_reach"), (0.79, "ecc_dip_step"), (0.9, "ecc_dip_stand"), (1, "ecc_dip_top")],
         mode="loop", apparatus=("parallel_bars", "box_tall"))


# ---------- wall slide: from a "W" (elbows down at the wall, hands at about head height) up to the overhead "Y" ----------
# Seen from the side the arms move in the wall's plane, so the elbows stay on the wall line; the front view starts from
# the same W.
pose("wall_slide_low", facing=-1, torso=0, legs=[180, 180], arms=[124, 0], pin={"joint": "foot_near", "at": [139, ANKLE]})
pose("wall_slide_high", base="wall_slide_low", arms=[23, 23])


# ---------- range lines and phase labels the app can't work out from a clip ----------
# "marker": the joint to watch, where the line sits and its label (the app lights the line when the joint reaches its
# own end of the range); false for none. "phases": one label for a hold or stretch, or [at, label] pairs along a loop.
def _end(name, joint, at=1.0):
    """Where a joint is at a moment of a template's timeline (the end of the range by default)."""
    clip = pv.Template(name, TEMPLATES[name], POSES, {})
    return clip.pose_at(at / 2 if clip.mode == "ping_pong" else at, pv.Figure({"segments": {
        "head_radius": HEAD_R, "neck": NECK, "torso": TORSO, "upper_arm": UPPER, "forearm": FORE, "thigh": THIGH,
        "shin": SHIN, "foot": FOOT}}))[joint]


def marker(names, joint=None, y=None, label=None, at=None):
    for name in names:
        spec = {}
        if joint:
            spec["joint"] = joint
        if y is not None:
            spec["y"] = round(y(name) if callable(y) else y, 2)
        if label:
            spec["label"] = label
        if at is not None:
            spec["at"] = at
        TEMPLATES[name]["marker"] = spec


for _name, _bar in (("row", 66.0), ("high_row", 60.0), ("feet_elevated_row", 66.0), ("chest_to_bar", 12.0),
                    ("straight_bar_dip", 40.0)):
    marker([_name], "shoulder", _bar, "chest_to_bar")
marker(["pushup", "pause_pushup", "close_pushup", "archer_pushup", "pseudo_planche_pushup"], "shoulder", FLOOR - 4,
       "chest_to_fist")
marker(["incline_pushup"], "shoulder", 82.0, "chest_to_edge")
marker(["dip", "assisted_dip", "half_dip"], label="shoulders_to_elbows")
marker(["squat", "tempo_squat", "pause_squat", "pistol", "pistol_assisted"], "hip", lambda n: _end(n, "knee_near")[1],
       "hips_to_knees")
marker(["reverse_lunge", "split_squat", "bulgarian_split_squat", "front_lunge"], "knee_far", 103.0, "back_knee_down")
marker(["hanging_knee_raise"], "knee_near", lambda n: _end(n, "hip")[1], "knees_to_hips")
marker(["hanging_leg_raise"], "foot_near", lambda n: _end(n, "hip")[1], "feet_to_hips")
marker(["toes_to_bar"], "toe_near", 12.0, "toes_to_bar")
marker(["glute_bridge", "single_leg_bridge"], "hip", label="hips_in_line")
marker(["pike_pushup", "elevated_pike_pushup"], "head", label="head_ahead")
marker(["wall_hspu", "deficit_wall_hspu"], "head", label="head_to_mat")
marker(["wall_hspu_negative"], "head", label="head_to_mat", at=0.72)
marker(["scapular_pull", "active_hang", "support", "support_shrug"], "neck", label="shoulders_down")
marker(["calf_raise"], "foot_near", label="heels_high")
marker(["front_lever_tuck", "front_lever_adv", "back_lever_tuck", "back_lever_adv", "tuck_front_row", "tuck_planche",
        "adv_tuck_planche"], "hip", lambda n: _end(n, "shoulder")[1], "hips_level")
marker(["lsit", "one_leg_lsit"], "foot_near", lambda n: _end(n, "hip")[1], "legs_level")
marker(["tuck_lsit", "frog_stand"], "foot_near", label="feet_off")
marker(["step_up"], "hip", label="stand_tall")

# Negatives: a slow lowering, then the way off the bar, bars or wall.
TEMPLATES["eccentric_pullup"]["phases"] = [[0, "lower_slow"], [0.74, "hang"], [0.8, "step_down"]]
TEMPLATES["eccentric_dip"]["phases"] = [[0, "lower_slow"], [0.7, "step_up"]]
TEMPLATES["negative_muscleup"]["phases"] = [[0, "lower_slow"], [0.76, "step_down"]]
TEMPLATES["one_arm_pullup_negative"]["phases"] = [[0, "lower_slow"], [0.78, "step_down"]]
TEMPLATES["wall_hspu_negative"]["phases"] = [[0, "lower_slow"], [0.72, "come_down"]]
TEMPLATES["freestanding_hspu_negative"]["phases"] = [[0, "lower_slow"], [0.74, "come_down"]]

# Static stretches and holds read "Hold · breathe"; the stretches lose the range line, which read as reps.
STRETCHES = ["hamstring_stretch", "ninety_ninety_stretch", "hip_flexor_stretch", "quad_stretch", "calf_stretch",
             "chest_stretch", "lat_stretch", "child_pose", "forearm_stretch", "cossack_hold", "cossack_hold_side",
             "deep_squat_hold", "supine_twist"]
HOLDS = ["plank_forearm", "incline_plank", "side_plank", "straight_side_plank", "kneeling_side_plank", "hollow_hold",
         "hollow_body_hold", "front_lever_tuck", "front_lever_adv", "front_lever_one_leg", "front_lever_straddle",
         "front_lever_full", "back_lever_tuck", "back_lever_adv", "back_lever_one_leg", "back_lever_straddle",
         "back_lever_full", "tuck_planche", "adv_tuck_planche", "straddle_planche", "full_planche", "lsit", "one_leg_lsit",
         "support", "foot_assisted_support", "straight_bar_support", "wall_handstand", "pike_handstand_hold",
         "overhead_line", "handstand_balance", "flag_ground_support", "assisted_tuck_flag", "tuck_flag", "straddle_flag",
         "full_flag", "relaxed_hang"]
for _name in STRETCHES + HOLDS:
    TEMPLATES[_name]["phases"] = "hold"
for _name in STRETCHES:
    TEMPLATES[_name]["marker"] = False


# ---------- joint-prep movements added by the round-5 curriculum review ----------
# Wrist push-up: on all fours with the hands under the shoulders, the heels of the hands press off the floor (the wrist
# rises onto the fingers and knuckles) and lower slowly; the arms stay straight, so the trunk rises with them.
def _quad_at(name, hand_y, shoulder_dx=0.0):
    hand = (QUAD_HAND_X, hand_y)
    sx = hand[0] + shoulder_dx
    sy = hand[1] - math.sqrt((UPPER + FORE) ** 2 - shoulder_dx ** 2)
    kx, ky = 118.0, 102.0
    dx, dy = sx - kx, sy - ky
    dist = math.hypot(dx, dy)
    a = (THIGH ** 2 - TORSO ** 2 + dist ** 2) / (2 * dist)
    h = math.sqrt(THIGH ** 2 - a * a)
    mx, my = kx + a * dx / dist, ky + a * dy / dist
    hip = min(((mx - h * dy / dist, my + h * dx / dist), (mx + h * dy / dist, my - h * dx / dist)), key=lambda o: o[1])
    torso = ang(sx - hip[0], sy - hip[1])
    thigh = ang(kx - hip[0], ky - hip[1])
    pose(name, facing=-1, torso=torso, legs=[thigh, 90], leg_far=[thigh - 2, 90], feet=90,
         pin={"joint": "knee_near", "at": [kx, ky]})
    reach_arm(name, hand, pref=LEFT)
    return name


QUAD_HAND_X = 99.0
_quad_at("wrist_flat", 105.0)
_quad_at("wrist_lift", 102.0, -1.5)
template("wrist_pushup", 2400, [(0, "wrist_flat"), (0.8, "wrist_lift"), (1, "wrist_lift")])


# Wall shoulder opener: hands on the wall at about hip height with straight arms, the hips push back over the feet and the
# chest sinks between the arms until the arms line up past the trunk.
SHOULDER_WALL_HAND, SHOULDER_WALL_FOOT = (150.0, 70.0), (104.0, ANKLE)


def _wall_opener(key, s):
    torso = 78.0 + 18.0 * s
    arm = 78.0 - 2.0 * s
    shoulder = (SHOULDER_WALL_HAND[0] - (UPPER + FORE - 0.02) * d(arm)[0], SHOULDER_WALL_HAND[1] - (UPPER + FORE - 0.02) * d(arm)[1])
    pose(key, facing=1, torso=round(torso, 2), head=round(torso - 6, 2), arms=[round(arm, 1)] * 2, feet=90,
         pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    leg_to(key, SHOULDER_WALL_FOOT, ("leg_near", "leg_far"))
    POSES[key]["leg_far"] = [POSES[key]["leg_far"][0] + 2, POSES[key]["leg_far"][1] + 2]
    repin(key, "foot_near")
    return key


template("wall_shoulder_flexion", 3600, dense("wall_opener", _wall_opener, 10), apparatus=("wall_right",), easing="linear")


# Pancake compression: a wide straddle can only be seen from the front, so the main clip faces the viewer (seated, legs
# wide along the floor, hands beside the knees, the heels lifting a little) and its side clip shows the forward lean.
pose("pancake_front", facing=1, torso=0, head=0, shrug=-1, legs=[262, 262], leg_far=[98, 98], foot_near=355, foot_far=5,
     hip=[100, 99])
reach_arm("pancake_front", (100 - 14.0, 105.0), pref=LEFT, which=("arm_near",))
reach_arm("pancake_front", (100 + 14.0, 105.0), pref=RIGHT, which=("arm_far",))
pose("pancake_front_lift", base="pancake_front", legs=[258, 258], leg_far=[102, 102], head=8)
reach_arm("pancake_front_lift", (100 - 14.0, 105.0), pref=LEFT, which=("arm_near",))
reach_arm("pancake_front_lift", (100 + 14.0, 105.0), pref=RIGHT, which=("arm_far",))
template("pancake_compression", 2600, [(0, "pancake_front"), (0.7, "pancake_front_lift"), (1, "pancake_front_lift")])
pose("pancake_side", facing=1, torso=22, head=30, shrug=-1, legs=[97.4, 97.4], leg_far=[97.8, 97.8], feet=10, hip=[96, 100])
arm_to("pancake_side", (116.0, 105.0), ("arm_near", "arm_far"))
pose("pancake_side_lift", base="pancake_side", torso=28, head=36, legs=[91.5, 91.5], leg_far=[91.9, 91.9], feet=4)
arm_to("pancake_side_lift", (116.0, 105.0), ("arm_near", "arm_far"))
template("pancake_side", 2600, [(0, "pancake_side"), (0.7, "pancake_side_lift"), (1, "pancake_side_lift")])


# Bench thoracic extension: seated on the ground with the upper back on the bench's front edge and the hands behind the
# head, the upper back arches back over the edge (the trunk bows, chest up) while the hips stay down.
APPARATUS["low_bench"] = [{"type": "round_rect", "values": [60, 88, 97, FLOOR], "filled": True}]


def _bench_t(key, s):
    torso = 330.0 - 18.0 * s
    pose(key, facing=1, torso=round(torso, 2), head=round(torso - 10 * s, 2), spine=round(4.0 * s, 2),
         arm_near=[round(torso + 35, 1), round(torso + 200, 1)], arm_far=[round(torso + 38, 1), round(torso + 203, 1)],
         feet=90, hip=[104.0, 100.0])
    leg_to(key, (128.0, ANKLE), ("leg_near", "leg_far"))
    return key


template("thoracic_extension", 3200, dense("bench_t", _bench_t, 10), apparatus=("low_bench",), easing="linear")
for _name in ("wall_shoulder_flexion", "pancake_compression", "thoracic_extension"):
    TEMPLATES[_name]["phases"] = "hold" if _name == "wall_shoulder_flexion" else TEMPLATES[_name].get("phases")
    if TEMPLATES[_name]["phases"] is None:
        del TEMPLATES[_name]["phases"]
TEMPLATES["wall_shoulder_flexion"]["marker"] = False


# ---------- box squat: sit back to a box behind the heels (the old box stood under the feet) ----------
APPARATUS["squat_box"] = [{"type": "round_rect", "values": [62, 92, 90, FLOOR], "filled": True}]
pose("box_squat_bottom", base="stand", torso=30, head=18, arms=[95, 95], foot_near=90, foot_far=90, hip=[86.0, 89.5])
leg_to("box_squat_bottom", (100.0, ANKLE), ("leg_near",))
leg_to("box_squat_bottom", (99.0, ANKLE), ("leg_far",))
repin("box_squat_bottom", "foot_near")
template("box_squat", 2200, [(0, "stand"), (0.5, "squat_mid"), (1, "box_squat_bottom")], apparatus=("squat_box",))
