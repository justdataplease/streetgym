"""Round 11, second batch: the vertical flag and the flag negative, the full skin-the-cat and the free supinated German
hang (4 movements).

- The two flags face the viewer like the other flags (group_e.py): the same post, split grip and shoulder point, both
  arms locked, the top hand pulling and the bottom hand pushing. The vertical flag stands the straight body upside down
  over the shoulders, legs closing together from a small straddle; the negative lowers that line away from the pole,
  through the level full-flag line, until the toes reach the floor, then cuts back to the top. Upside down the feet
  rise past the post's top, so these clips draw the pole taller.
- The skin-the-cat hangs from the high bar with the feet clear of the floor all the way: from a still hang the knees
  tuck, the body rolls back through the inverted tuck and the straight legs lower behind into a German hang, then back
  the same way. Free of the floor, the body's centre of mass stays under the hands, so the straight arms swing a little
  as the shape changes.
- The supinated German hang holds the Hefesto's start position (focused_skills.py) on the same bar: the shoulders
  settle a little further open into it, elbows straight, the bent legs trailing clear of the floor.
Closed chains are pinned on the hands, as in progressions.py.
"""
from author import *
from group_e import FLAG_NECK, FLAG_TOP, FLAG_LOW, _track, _mirror
from focused_skills import HEF_BAR, OAH_REACH, _hef, _hef_path
import mappings


def _frames(prefix, build, count, start, end):
    """count + 1 keyframes from start to end over a linear parameter."""
    return [(round(start + (end - start) * i / count, 4), build(f"{prefix}_{i}", i / count)) for i in range(count + 1)]


# ---------- vertical flag and flag negative (facing the viewer, the post on the left) ----------
APPARATUS["flag_pole_top"] = [{"type": "line", "values": [62, 2, 62, 18]}]
_TOP_ARM = round(ang(FLAG_TOP[0] - FLAG_NECK[0], FLAG_TOP[1] - FLAG_NECK[1]), 2)
_LOW_ARM = round(ang(FLAG_LOW[0] - FLAG_NECK[0], FLAG_LOW[1] - FLAG_NECK[1]), 2)


def _vflag(key, torso, near, far, foot_near=None, foot_far=None):
    """The flag grip with the body line at [torso] (180: straight up over the shoulders, 270: the level flag)."""
    return pose(key, facing=1, torso=round(torso % 360, 2), arm_far=[_TOP_ARM, _TOP_ARM], arm_near=[_LOW_ARM, _LOW_ARM],
                leg_near=[near, near], leg_far=[far, far],
                foot_near=round((near if foot_near is None else foot_near) % 360, 2),
                foot_far=round((far if foot_far is None else foot_far) % 360, 2),
                pin={"joint": "neck", "at": list(FLAG_NECK)})


_vflag("vflag_straddle", 180, 15, 345)
_vflag("vflag_line", 180, 0.5, 359.5)
template("vertical_flag", 3000, [(0, "vflag_straddle"), (0.55, "vflag_line"), (1, "vflag_line")],
         apparatus=("post_left", "flag_pole_top"))

# The negative turns the straight line about the shoulders, legs together and toes pointed, until the toe tips reach
# the floor (the pointed foot carries the line on, so the toes land first).
_REACH = TORSO + THIGH + SHIN + FOOT
NEG_END = 360 - math.degrees(math.acos((FLOOR - 0.2 - FLAG_NECK[1]) / _REACH))


def _negative(key, s):
    torso = 180 + (NEG_END - 180) * s
    return _vflag(key, torso, torso - 180 + 0.5, torso - 180 - 0.5)


_neg = _frames("flag_neg", _negative, 24, 0.08, 0.78)
template("flag_negative", 6000, [(0, _neg[0][1])] + _neg + [(1, _neg[-1][1])], mode="loop",
         apparatus=("post_left", "flag_pole_top"), easing="linear")


# ---------- skin-the-cat (high bar, feet clear of the floor) ----------
# This replaces group_b.py's "skin_the_cat" template, four rough keyframes on the mid bar that no movement mapped.
STC_HAND = (100.0, 12.0)
# Torso angle (falling as the body rolls back), hip flexion and knee flexion along the roll; the German hang at the end
# keeps the legs straight with a small pike.
_STC_U = [0.0, 0.15, 0.3, 0.45, 0.6, 0.8, 1.0]
_STC_TORSO = [360.0, 350.0, 310.0, 270.0, 180.0, 110.0, 40.0]
_STC_HIP = [0.0, 80.0, 125.0, 135.0, 135.0, 95.0, 25.0]
_STC_KNEE = [0.0, 95.0, 145.0, 150.0, 150.0, 70.0, 0.0]
# Segment masses (fractions of the body) at their midpoints, for the centre of mass.
_MASS = [(("head", "head"), 0.08), (("hip", "neck"), 0.50), (("shoulder", "elbow_near"), 0.03),
         (("shoulder", "elbow_far"), 0.03), (("elbow_near", "hand_near"), 0.025), (("elbow_far", "hand_far"), 0.025),
         (("hip", "knee_near"), 0.10), (("hip", "knee_far"), 0.10), (("knee_near", "foot_near"), 0.055),
         (("knee_far", "foot_far"), 0.055)]


def _com_x(key):
    j = solved(key)
    return sum(w * (j[a][0] + j[b][0]) / 2 for (a, b), w in _MASS)


def _stc(key, u):
    torso = _track(_STC_U, _STC_TORSO, u)
    thigh = torso + 180 - _track(_STC_U, _STC_HIP, u)
    shin = thigh + _track(_STC_U, _STC_KNEE, u)
    arm = 0.0
    for _ in range(6):
        pose(key, facing=1, torso=round(torso % 360, 2), head=round(torso % 360, 2), arms=[arm, arm],
             legs=[thigh, shin], leg_far=[thigh + 1.5, shin + 1.5], feet=round(shin % 360, 1),
             pin={"joint": "hand_near", "at": list(STC_HAND)})
        # Straight arms: moving the shoulder moves the whole body, so the offset converts directly to an arm angle.
        off = _com_x(key) - STC_HAND[0]
        sx = solved(key)["shoulder"][0] - off
        arm = round(math.degrees(math.asin(max(-1.0, min(1.0, (STC_HAND[0] - sx) / (UPPER + FORE))))), 2)
    return key


_stc_in = _frames("stc", _stc, 30, 0.06, 0.44)
template("skin_the_cat", 6000, [(0, _stc_in[0][1])] + _stc_in + _mirror(_stc_in, 0.56, 0.94) + [(1, _stc_in[0][1])],
         mode="loop", apparatus=("high_bar", "mat"), easing="linear")


# ---------- supinated German hang (the Hefesto's bar, feet clear of the floor) ----------
# The Hefesto's start, on the same bar: the shoulder on the straight-arm circle round the grip and the bent legs trailing.
_HOLD_REL, _HOLD_TORSO, _HOLD_LEGS = _hef_path(0.0)
_HOLD_PHI = ang(*_HOLD_REL)


def _sgh(key, s):
    phi = _HOLD_PHI - 7.0 * (1 - s)
    shoulder = (HEF_BAR[0] + OAH_REACH * d(phi)[0], HEF_BAR[1] + OAH_REACH * d(phi)[1])
    legs = [v + 4.0 * (1 - s) for v in _HOLD_LEGS]
    return _hef(key, HEF_BAR, shoulder, _HOLD_TORSO + 4.0 * (1 - s), legs=legs)


template("supinated_german_hang", 3400, [(t, k) for t, k in _frames("sgh", _sgh, 8, 0.0, 0.6)] + [(1, "sgh_8")],
         apparatus=("mid_bar",), easing="linear")

# Holds read "Hold · breathe"; the negative lowers slowly, then comes down.
for _name in ("vertical_flag", "supinated_german_hang"):
    TEMPLATES[_name]["phases"] = "hold"
TEMPLATES["flag_negative"]["phases"] = [[0, "lower_slow"], [0.78, "come_down"]]

for _name in ("vertical_flag", "flag_negative", "skin_the_cat", "supinated_german_hang"):
    mappings.MOVEMENTS[_name] = _name
