"""Group F: Level 7 "Mastery" rungs - typewriter pull-up, towel-assisted one-arm pull-up, one-arm
pull-up negative, deficit wall handstand push-up and freestanding handstand push-up negative.

Closed chains follow progressions.py: dense keyframes, each solved with two-bone IK that keeps the
anatomical bend and pinned on its loaded contact, so interpolation stays inside the 0.2-unit
tolerance checked by tools/validate_progression_motions.py.
"""
from author import *
from progressions import _both, _lerp, _pose, _template, arm_to, dense, repin


def arm_out(name, target, which, side):
    """Hand on target with the elbow on the outer side of the shoulder-to-hand line (side -1 left, 1 right)."""
    sx, sy = solved(name)["shoulder"]
    best = max(_both(target[0] - sx, target[1] - sy, UPPER, FORE), key=lambda s: d(s[0])[0] * side)
    for w in which:
        POSES[name][w] = list(best)


# ---------- typewriter pull-up (high bar, wide grip, seen from the front) ----------
# The slide is sideways, so this guide faces the viewer: hands wide on the bar, legs together, each
# elbow pointing out to its own side. Pull toward the left hand with the right arm long, slide the
# chin along the bar to the right hand while staying at the top, then lower from that side back to
# the centred hang. The shoulders never pass outside either hand, so neither elbow changes side.
TW_LEFT, TW_RIGHT, TW_REACH, TW_TOP_Y = (86.0, 12.0), (114.0, 12.0), 25.85, 21.0
TW_SIDE_X = math.sqrt(TW_REACH ** 2 - (TW_TOP_Y - TW_LEFT[1]) ** 2)
TW_HANG = (100.0, TW_LEFT[1] + math.sqrt(TW_REACH ** 2 - (100.0 - TW_LEFT[0]) ** 2))


def _tw_body(key, shoulder, s):
    """s: 0 in the hang, 1 at the top (shoulders drawn down so the chin clears the bar)."""
    _pose(key, facing=1, torso=0, shrug=-1.0 - 1.5 * s, legs=[181, 181], leg_far=[179, 179], feet=180,
          pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_out(key, TW_LEFT, ("arm_near",), -1)
    arm_out(key, TW_RIGHT, ("arm_far",), 1)
    repin(key, "hand_near")
    return key


def _around(hand, start, end, s):
    """Shoulder on the circle of one straight arm, from start to end (both on the circle)."""
    a0 = math.atan2(start[0] - hand[0], start[1] - hand[1])
    a1 = math.atan2(end[0] - hand[0], end[1] - hand[1])
    a = a0 + (a1 - a0) * s
    return (hand[0] + TW_REACH * math.sin(a), hand[1] + TW_REACH * math.cos(a))


TW_TOP_LEFT = (TW_RIGHT[0] - TW_SIDE_X, TW_TOP_Y)
TW_TOP_RIGHT = (TW_LEFT[0] + TW_SIDE_X, TW_TOP_Y)
_tw_up = dense("tw_up", lambda k, s: _tw_body(k, _around(TW_RIGHT, TW_HANG, TW_TOP_LEFT, s), s), 12, span=1.0, hold=False)
_tw_across = dense("tw_across", lambda k, s: _tw_body(k, _lerp(list(TW_TOP_LEFT), list(TW_TOP_RIGHT), s), 1.0),
                   12, span=1.0, hold=False)[1:]
_tw_down = dense("tw_down", lambda k, s: _tw_body(k, _around(TW_LEFT, TW_TOP_RIGHT, TW_HANG, s), 1 - s), 12, span=1.0, hold=False)[1:]
_tw = [(round(0.06 + 0.30 * t, 4), k) for t, k in _tw_up] + \
      [(round(0.36 + 0.26 * (i + 1) / len(_tw_across), 4), k) for i, (_, k) in enumerate(_tw_across)] + \
      [(round(0.62 + 0.32 * (i + 1) / len(_tw_down), 4), k) for i, (_, k) in enumerate(_tw_down)]
_template("typewriter_pullup", 4200, [(0, _tw_up[0][1])] + _tw + [(1, _tw_down[-1][1])],
          mode="loop", apparatus=("high_bar",), easing="linear")


# ---------- towel-assisted one-arm pull-up ----------
# The near hand holds the bar; the far hand grips a towel looped over the bar a little lower and to
# the side, helping only as much as needed. Both hands stay pinned; the far elbow bends more as the
# body rises, ending with the towel hand beside the shoulder.
OAP_HAND, OAP_TOWEL = (100.0, 12.0), (109.0, 26.0)
APPARATUS["towel_bar"] = [{"type": "line", "values": [107.5, 12, 107.5, 38]}, {"type": "line", "values": [110.5, 12, 110.5, 38]}]
OAP_HANG = (OAP_HAND[0], OAP_HAND[1] + 25.9)
OAP_TOP = (96.5, 18.0)


def _oap(key, s, far=True):
    shoulder = _lerp(list(OAP_HANG), list(OAP_TOP), s)
    _pose(key, facing=1, torso=(-8 * s) % 360, shrug=-1.0 * (1 - s), legs=[178 - 6 * s, 184 + 10 * s],
          leg_far=[180 - 6 * s, 186 + 12 * s], feet=180, arm_far=[165, 20],
          pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_to(key, OAP_HAND, ("arm_near",))
    if far:
        arm_to(key, OAP_TOWEL, ("arm_far",))
    repin(key, "hand_near")
    return key


_template("assisted_one_arm_pullup", 2600, dense("oap_assist", _oap, 14), apparatus=("high_bar", "towel_bar"), easing="linear")


# ---------- one-arm pull-up negative (box under the bar) ----------
# Start at the top on one arm with the free hand on the chest, lower slowly to a straight arm,
# pause in the hang, then come back to the top (the athlete steps up from the box to reset).
def _oap_neg(key, s):
    return _oap(key, 1 - s, far=False)


_neg = dense("oap_neg", _oap_neg, 14, span=1.0, hold=False)
# A low box under the bar: the feet clear it in the hang and step back up from it to reset.
APPARATUS["step_box"] = [{"type": "round_rect", "values": [84, 97, 114, FLOOR], "filled": True}]
_template("one_arm_pullup_negative", 4200,
          [(0, _neg[0][1])] + [(round(0.12 + 0.66 * t, 4), k) for t, k in _neg] + [(0.88, _neg[-1][1])] +
          [(round(0.88 + 0.12 * (1 - t), 4), k) for t, k in reversed(_neg[:-1])],
          mode="loop", apparatus=("high_bar", "step_box"), easing="linear")


# ---------- deficit wall handstand push-up (hands on low blocks, chest to wall) ----------
# The wall_hspu geometry with the hands raised on blocks: the toes slide on the wall, and the head
# lowers below hand level, just behind the blocks, to a thin mat on the floor.
DEF_HAND = (137.0, 96.0)
APPARATUS["deficit_blocks"] = [{"type": "round_rect", "values": [134, 97, 140, FLOOR], "filled": True}]
APPARATUS["deficit_mat"] = [{"type": "round_rect", "values": [120, 104, 132, FLOOR], "filled": True}]
_DEF_REACH = UPPER + FORE + TORSO + THIGH + SHIN
_DEF_LEAN = (145.0 - DEF_HAND[0]) / _DEF_REACH
DEF_TOP = (DEF_HAND[0] + (UPPER + FORE) * _DEF_LEAN, DEF_HAND[1] - (UPPER + FORE) * math.sqrt(1 - _DEF_LEAN ** 2))
DEF_BOTTOM = (128.4, 91.8)


def _deficit(key, s):
    # The shoulders travel back from the wall early, so the head passes behind the blocks before it
    # drops below hand level.
    sx = DEF_TOP[0] + (DEF_BOTTOM[0] - DEF_TOP[0]) * (1 - (1 - s) ** 3)
    sy = DEF_TOP[1] + (DEF_BOTTOM[1] - DEF_TOP[1]) * s
    lean = math.degrees(math.asin((145.0 - sx) / (TORSO + THIGH + SHIN)))
    _pose(key, base="handstand_wall", torso=round(180 + lean, 2), legs=[lean, lean], head=180 + 6 * s,
          pin={"joint": "shoulder", "at": [round(sx, 3), round(sy, 3)]})
    arm_to(key, DEF_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


_def = dense("deficit_down", _deficit, 14, span=1.0, hold=False)
_template("deficit_wall_hspu", 3000, [(0, _def[0][1])] + [(round(0.1 + 0.75 * t, 4), k) for t, k in _def] + [(1, _def[-1][1])],
          apparatus=("wall_right", "deficit_blocks", "deficit_mat"), easing="linear")


# ---------- freestanding handstand push-up negative (a step from the wall) ----------
# The wall_hspu shape without wall support: a stacked handstand over the hands with a wall a short
# step away on the chest side as a backstop. Lowering, the head travels just behind the hands to a
# folded mat while the elbows bend toward the chest side, the hips stay over the hands and the
# straight legs tip slightly the other way to balance, never reaching the wall. The athlete then
# tucks and steps down; the guide returns to the top for the next repetition.
FS_HAND, FS_WALL = (110.0, 105.0), 140.0
APPARATUS["backstop_wall"] = [{"type": "line", "values": [FS_WALL, 6, FS_WALL, FLOOR]}]
APPARATUS["freestanding_mat"] = [{"type": "round_rect", "values": [95, 102.5, 106.5, FLOOR], "filled": True}]
FS_TOP, FS_BOTTOM = (FS_HAND[0], FS_HAND[1] - UPPER - FORE), (106.0, 90.5)
FS_TORSO, FS_LEGS, FS_HEAD = (180.0, 197.4), (0.0, 15.0), (180.0, 185.0)


def _freestanding(key, s):
    sx, sy = _lerp(list(FS_TOP), list(FS_BOTTOM), s)
    torso = FS_TORSO[0] + (FS_TORSO[1] - FS_TORSO[0]) * s
    legs = FS_LEGS[0] + (FS_LEGS[1] - FS_LEGS[0]) * s
    head = FS_HEAD[0] + (FS_HEAD[1] - FS_HEAD[0]) * s
    _pose(key, facing=-1, torso=round(torso, 2), head=round(head, 2), legs=[legs, legs], leg_far=[legs - 1.5, legs - 1.5],
          foot_near=round(legs % 360, 2), foot_far=round((legs - 1.5) % 360, 2),
          pin={"joint": "shoulder", "at": [round(sx, 3), round(sy, 3)]})
    arm_to(key, FS_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


_fs = dense("freestanding_down", _freestanding, 16, span=1.0, hold=False)
_template("freestanding_hspu_negative", 4400,
          [(0, _fs[0][1])] + [(round(0.1 + 0.68 * t, 4), k) for t, k in _fs] + [(0.86, _fs[-1][1])] +
          [(round(0.86 + 0.14 * (1 - t), 4), k) for t, k in reversed(_fs[:-1])],
          mode="loop", apparatus=("backstop_wall", "freestanding_mat"), easing="linear")
