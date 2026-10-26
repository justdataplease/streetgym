"""Group E: straight-leg side plank, straight hollow body, feet-supported German hang,
handstand cartwheel exit, straddle and full human flag, and the full Nordic curl.

Closed chains follow progressions.py: dense keyframes, each solved with two-bone IK that
keeps the anatomical bend and pinned on its loaded contact, so interpolation stays inside
the 0.2-unit tolerance checked by tools/validate_progression_motions.py.
"""
from author import *
from progressions import NORDIC_KNEE, _pose, _template, arm_to, dense, leg_to, repin


def _meet(p, rp, q, rq, lower=False):
    """Upper (or lower) intersection of circle(p, rp) and circle(q, rq)."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    dist = math.hypot(dx, dy)
    a = (rp * rp - rq * rq + dist * dist) / (2 * dist)
    h = math.sqrt(max(rp * rp - a * a, 0.0))
    mx, my = p[0] + a * dx / dist, p[1] + a * dy / dist
    options = [(mx - h * dy / dist, my + h * dx / dist), (mx + h * dy / dist, my - h * dx / dist)]
    return max(options, key=lambda o: o[1]) if lower else min(options, key=lambda o: o[1])


def _track(us, values, u):
    """Piecewise-linear value through the knots (us[i], values[i])."""
    for u0, u1, v0, v1 in zip(us, us[1:], values, values[1:]):
        if u <= u1:
            return v0 + (v1 - v0) * (0.0 if u1 == u0 else (u - u0) / (u1 - u0))
    return values[-1]


def _flex(name, side, torso):
    """(hip flexion, knee flexion) of an authored leg, facing 1: thigh = torso + 180 - hip, shin = thigh + knee."""
    thigh, shin = POSES[name][f"leg_{side}"]
    return ((torso - thigh) % 360 - 180, (shin - thigh + 180) % 360 - 180)


def _mirror(frames, start, end):
    """The same keyframes played backwards over [start, end] (out the way it went in)."""
    t0, t1 = frames[0][0], frames[-1][0]
    return [(round(start + (end - start) * (t1 - t) / (t1 - t0), 4), k) for t, k in reversed(frames)]


# ---------- straight-leg side plank (forearm and feet on the floor) ----------
# The kneeling_side_plank convention: elbow stacked under the shoulder, forearm on the floor,
# top arm up. Here the legs are straight and the hips settle from a small sag into one line
# from head to heels; elbow, forearm and feet stay planted.
SSP_ELBOW = (74.0, ANKLE)
SSP_ANKLE = (SSP_ELBOW[0] + math.sqrt((TORSO + THIGH + SHIN) ** 2 - UPPER ** 2), ANKLE)


def _side_plank(key, s):
    sag = math.radians(11.0 * (1 - s))
    reach = math.sqrt(TORSO ** 2 + (THIGH + SHIN) ** 2 + 2 * TORSO * (THIGH + SHIN) * math.cos(sag))
    neck = _meet(SSP_ELBOW, UPPER, SSP_ANKLE, reach)
    hip = _meet(neck, TORSO, SSP_ANKLE, THIGH + SHIN, lower=True)
    torso = ang(neck[0] - hip[0], neck[1] - hip[1])
    legs = ang(SSP_ANKLE[0] - hip[0], SSP_ANKLE[1] - hip[1])
    _pose(key, facing=1, torso=round(torso, 2), legs=[legs, legs], feet=90,
          arm_near=[ang(SSP_ELBOW[0] - neck[0], SSP_ELBOW[1] - neck[1]), 90], arm_far=[0, 0],
          pin={"joint": "foot_near", "at": list(SSP_ANKLE)})
    return key


_template("straight_side_plank", 3000, dense("ssp", _side_plank, 10, span=0.6), easing="linear")


# ---------- straight hollow body hold (lying, legs straight and low, arms by the ears) ----------
# The low back stays down (hip pinned) while the rounded trunk and straight legs hold a
# shallow banana; the legs settle from about 20 to 13 degrees above the floor.
HOLLOW_HIP = [106.0, 100.0]


def _hollow(key, s):
    torso, legs = 288.0, 70 + 7 * s
    _pose(key, facing=1, torso=torso, head=torso + 12, spine=-2 - s, arms=[torso + 9, torso + 9],
          arm_far=[torso + 11, torso + 11], legs=[legs, legs], leg_far=[legs - 1.5, legs - 1.5], feet=legs + 6,
          hip=HOLLOW_HIP)
    return key


_template("hollow_body_hold", 3000, [(0, _hollow("hollow_body_set", 0)), (0.6, _hollow("hollow_body_hold", 1)),
                                     (1, "hollow_body_hold")], apparatus=("mat",))


# ---------- straddle and full human flag (split grip on a post, both arms straight) ----------
# Same post and split grip as tuck_flag, with both arms locked straight: the top hand pulls,
# the bottom hand pushes and the trunk stays level. Tuck -> straddle, straddle -> full line.
FLAG_NECK = (80.0, 60.0)
_FLAG_DY = math.sqrt((UPPER + FORE) ** 2 - (FLAG_NECK[0] - 62.0) ** 2)
FLAG_TOP, FLAG_LOW = (62.0, FLAG_NECK[1] - _FLAG_DY), (62.0, FLAG_NECK[1] + _FLAG_DY)


def _flag(name, near, far, foot_near, foot_far):
    top = ang(FLAG_TOP[0] - FLAG_NECK[0], FLAG_TOP[1] - FLAG_NECK[1])
    low = ang(FLAG_LOW[0] - FLAG_NECK[0], FLAG_LOW[1] - FLAG_NECK[1])
    return _pose(name, facing=1, torso=270, arm_far=[top, top], arm_near=[low, low], leg_near=near, leg_far=far,
                 foot_near=foot_near, foot_far=foot_far, pin={"joint": "neck", "at": list(FLAG_NECK)})


_flag("flag_e_tuck", [35, 155], [37, 157], 90, 92)
_flag("flag_e_straddle", [128, 128], [52, 52], 130, 50)
_flag("flag_e_full", [90.5, 90.5], [89.5, 89.5], 92, 88)
_template("straddle_flag", 3000, [(0, "flag_e_tuck"), (0.55, "flag_e_straddle"), (1, "flag_e_straddle")], apparatus=("post_left",))
_template("full_flag", 3000, [(0, "flag_e_straddle"), (0.55, "flag_e_full"), (1, "flag_e_full")], apparatus=("post_left",))


# ---------- full Nordic curl (knees down, ankles under the low anchor) ----------
# One straight line from the knees lowers through the whole range, pauses just above the
# floor and pulls back up. The hands stay ready in front of the chest and never catch.
NC_DEPTH, NC_HOVER = 76.0, 100.0


def _nordic_curl(key, s):
    theta = NC_DEPTH * s
    torso = (360 - theta) % 360
    _pose(key, facing=-1, torso=torso, head=(torso - 26 * s) % 360, legs=[180 - theta, 90], feet=90,
          arms=[(210 - theta) % 360, (320 - theta) % 360], pin={"joint": "knee_near", "at": list(NORDIC_KNEE)})
    hand = solved(key)["hand_near"]
    if hand[1] > NC_HOVER:
        arm_to(key, (hand[0], NC_HOVER), ("arm_near", "arm_far"))
    return key


_nc = dense("nordic_curl", _nordic_curl, 14, span=1.0, hold=False)
_template("nordic_curl", 4200, [(0, _nc[0][1])] + [(round(0.08 + 0.47 * t, 4), k) for t, k in _nc] +
          _mirror(_nc, 0.62, 0.92) + [(1, _nc[0][1])], mode="loop", apparatus=("nordic_anchor",), easing="linear")


# ---------- feet-supported German hang (low bar) ----------
# Sit under the bar, roll back through a tucked skin-the-cat with the arms straight above the
# shoulders, let the legs pass the arms and put the toes down behind, then let the shoulders
# open a little further with the feet planted. Out the same way.
GH_BAR = 46.0
APPARATUS["gh_bar"] = [
    {"type": "line", "values": [40, GH_BAR, 160, GH_BAR]},
    {"type": "line", "role": "floor", "values": [44, GH_BAR, 44, FLOOR]},
    {"type": "line", "role": "floor", "values": [156, GH_BAR, 156, FLOOR]},
]
GH_HAND = (100.0, GH_BAR)
# Flat feet in front for the seated start; toes tucked behind (ankle above the toe) in the hang.
GH_SIT, GH_BACK = (124.0, ANKLE), (60.0, FLOOR - FOOT)
GH_START, GH_TOUCH, GH_HOLD = 345.0, 68.0, 56.0


def _gh_base(key, torso, head):
    return _pose(key, facing=1, torso=round(torso % 360, 2), head=round(head % 360, 2), arms=[0, 0],
                 pin={"joint": "hand_near", "at": list(GH_HAND)})


_gh_base("gh_ik_sit", GH_START, GH_START)
leg_to("gh_ik_sit", GH_SIT, ("leg_near", "leg_far"))
_gh_base("gh_ik_touch", GH_TOUCH, GH_TOUCH + 45)
leg_to("gh_ik_touch", GH_BACK, ("leg_near", "leg_far"))
# Torso, hip flexion and knee flexion along the roll (the torso angle decreases as it rolls back);
# the end knots are the IK solutions on the floor, so contact starts and ends exactly.
_GH_U = [(t - GH_START) / (GH_TOUCH - GH_START) for t in (GH_START, 300.0, 270.0, 180.0, 130.0, GH_TOUCH)]
_GH_HIP = [_flex("gh_ik_sit", "near", GH_START)[0], 120.0, 135.0, 135.0, 95.0, _flex("gh_ik_touch", "near", GH_TOUCH)[0]]
_GH_KNEE = [_flex("gh_ik_sit", "near", GH_START)[1], 145.0, 150.0, 150.0, 80.0, _flex("gh_ik_touch", "near", GH_TOUCH)[1]]
# Foot angle relative to the shin: flat on the floor, pointed in the air, toes tucked at touch-down.
_GH_FOOT = [90 - POSES["gh_ik_sit"]["leg_near"][1], 0.0, 0.0, 0.0, 0.0, 180 - POSES["gh_ik_touch"]["leg_near"][1]]
del POSES["gh_ik_sit"], POSES["gh_ik_touch"]


def _gh_roll(key, s):
    torso = GH_START + (GH_TOUCH - GH_START) * s
    _gh_base(key, torso, torso + 45 * s)
    if s in (0, 1):
        leg_to(key, GH_SIT if s == 0 else GH_BACK, ("leg_near", "leg_far"))
        shin = POSES[key]["leg_near"][1]
    else:
        thigh = torso + 180 - _track(_GH_U, _GH_HIP, s)
        shin = thigh + _track(_GH_U, _GH_KNEE, s)
        POSES[key]["legs"] = [round(thigh % 360, 1), round(shin % 360, 1)]
    POSES[key]["feet"] = round((shin + _track(_GH_U, _GH_FOOT, s)) % 360, 1)
    return key


def _gh_settle(key, s):
    torso = GH_TOUCH + (GH_HOLD - GH_TOUCH) * s
    _gh_base(key, torso, torso + 45 + 10 * s)
    leg_to(key, GH_BACK, ("leg_near", "leg_far"))
    POSES[key]["feet"] = 180
    return key


_roll = dense("gh_roll", _gh_roll, 24, span=1.0, hold=False)
_settle = dense("gh_settle", _gh_settle, 6, span=1.0, hold=False)[1:]
_gh_in = [(round(0.06 + 0.30 * t, 4), k) for t, k in _roll] + \
    [(round(0.36 + 0.08 * (i + 1) / len(_settle), 4), k) for i, (_, k) in enumerate(_settle)]
_template("german_hang", 5600, [(0, _roll[0][1])] + _gh_in + _mirror(_gh_in, 0.56, 0.94) + [(1, _roll[0][1])],
          mode="loop", apparatus=("gh_bar", "mat"), easing="linear")


# ---------- handstand cartwheel exit (from the chest-to-wall handstand) ----------
# Side view: the weight shifts onto the near hand while the far hand lifts and reaches out, the
# hips turn (the figure's facing flips while every limb is straight), the near leg steps down
# in front onto the floor, the hand leaves the floor and the far leg follows into a lunge
# finish with the arms overhead. Back up the same way.
HS_HAND = tuple(POSES["handstand_wall"]["pin"]["at"])
HS_STEP, HS_BACK = (114.0, ANKLE), (86.0, 101.5)
_HS_U = [0.0, 0.3, 0.65, 1.0]
_HS_ARM = [180.0, 182.0, 176.0, 168.0]        # near arm (straight): shoulder -> hand
_HS_TORSO = [180.0, 175.0, 145.0, 100.0]
_HS_HEAD = [0.0, 10.0, 35.0, 60.0]            # head turned toward the floor between the hands
_HS_HIP = [0.0, 0.0, 75.0, None]              # near leg hip and knee flexion (facing 1); IK at touch-down
_HS_KNEE = [0.0, 0.0, 35.0, None]
_HS_FAR_LEG = [360.0, 355.0, 340.0, 300.0]    # far leg stays straight and high
_HS_FAR_ARM = [180.0, 222.0, 225.0, 215.0]    # far hand off the floor, reaching out
_HS_NEAR_FOOT = [90.0, 0.0, 0.0, None]        # relative to the shin; flat on the floor at touch-down
_HS_FAR_FOOT = [90.0, 0.0, 0.0, 0.0]


def _hs_base(key, u, facing):
    torso = _track(_HS_U, _HS_TORSO, u)
    arm, far_arm, far_leg = (_track(_HS_U, k, u) for k in (_HS_ARM, _HS_FAR_ARM, _HS_FAR_LEG))
    _pose(key, facing=facing, torso=round(torso, 2), head=round(torso + _track(_HS_U, _HS_HEAD, u), 2),
          arm_near=[arm, arm], arm_far=[far_arm, far_arm], leg_far=[far_leg, far_leg],
          pin={"joint": "hand_near", "at": list(HS_HAND)})
    return key


_hs_base("hs_ik_touch", 1.0, 1)
leg_to("hs_ik_touch", HS_STEP, ("leg_near",))
_HS_HIP[3], _HS_KNEE[3] = _flex("hs_ik_touch", "near", _HS_TORSO[3])
_HS_NEAR_FOOT[3] = 90 - POSES["hs_ik_touch"]["leg_near"][1]
del POSES["hs_ik_touch"]


def _hs_down(key, u):
    _hs_base(key, u, -1 if u == 0 else 1)
    thigh = POSES[key]["torso"] + 180 - _track(_HS_U, _HS_HIP, u)
    POSES[key]["leg_near"] = [round(thigh % 360, 1), round((thigh + _track(_HS_U, _HS_KNEE, u)) % 360, 1)]
    if u == 1:
        leg_to(key, HS_STEP, ("leg_near",))
    POSES[key]["foot_near"] = round((POSES[key]["leg_near"][1] + _track(_HS_U, _HS_NEAR_FOOT, u)) % 360, 1)
    POSES[key]["foot_far"] = round((POSES[key]["leg_far"][1] + _track(_HS_U, _HS_FAR_FOOT, u)) % 360, 1)
    return key


_down = dense("hs_down", _hs_down, 18, span=1.0, hold=False)
# After touch-down the near foot carries the weight: the hip path, torso and arms are authored,
# the legs are solved onto their floor points, and every frame is pinned on the near foot. The
# far leg lowers from high behind onto the ball of the foot.
_HS_V = [0.0, 0.4, 0.75, 1.0]
_HS_RISE_HIP = [tuple(solved(_down[-1][1])["hip"]), (113.0, 77.5), (106.0, 78.5), (101.0, 80.0)]
_HS_RISE_TORSO = [_HS_TORSO[-1], 55.0, 18.0, 0.0]
_HS_RISE_HEAD = [_HS_HEAD[-1], 15.0, 5.0, 0.0]
_HS_RISE_ARM = [_HS_ARM[-1], 150.0, 60.0, 0.0]
_HS_RISE_FAR_ARM = [_HS_FAR_ARM[-1], 165.0, 62.0, 3.0]
_pose("hs_ik_back", facing=1, torso=_HS_RISE_TORSO[2], hip=list(_HS_RISE_HIP[2]))
_HS_BACK_LEG = leg_to("hs_ik_back", HS_BACK, ("leg_far",))
del POSES["hs_ik_back"]


def _hs_rise(key, v):
    torso = _track(_HS_V, _HS_RISE_TORSO, v)
    arm, far_arm = _track(_HS_V, _HS_RISE_ARM, v), _track(_HS_V, _HS_RISE_FAR_ARM, v)
    hip = [_track(_HS_V, [p[i] for p in _HS_RISE_HIP], v) for i in (0, 1)]
    _pose(key, facing=1, torso=round(torso, 2), head=round(torso + _track(_HS_V, _HS_RISE_HEAD, v), 2),
          arm_near=[arm, arm], arm_far=[far_arm, far_arm], foot_near=90, hip=hip)
    leg_to(key, HS_STEP, ("leg_near",))
    if v >= _HS_V[2]:
        leg_to(key, HS_BACK, ("leg_far",))
        POSES[key]["foot_far"] = 150
    else:
        f = v / _HS_V[2]
        thigh, shin = (_HS_FAR_LEG[-1] + (b - _HS_FAR_LEG[-1]) * f for b in _HS_BACK_LEG)
        POSES[key]["leg_far"] = [round(thigh % 360, 1), round(shin % 360, 1)]
        POSES[key]["foot_far"] = round((shin + (150 - _HS_BACK_LEG[1]) * f) % 360, 1)
    repin(key, "foot_near")
    return key


_rise = dense("hs_rise", _hs_rise, 14, span=1.0, hold=False)[1:]
_hs_out = [(round(0.08 + 0.26 * t, 4), k) for t, k in _down] + \
    [(round(0.34 + 0.16 * (i + 1) / len(_rise), 4), k) for i, (_, k) in enumerate(_rise)]
_template("handstand_exit", 6000, [(0, "handstand_wall")] + _hs_out + _mirror(_hs_out, 0.60, 0.94) + [(1, "handstand_wall")],
          mode="loop", apparatus=("wall_right", "mat"), easing="linear", reduced=0.34)
