"""Round 11 guides: the one-arm handstand line, the shrimp squat line and the Hefesto line (18 movements).

Ported from the sg-placement-points worktree and redrawn after review:
- The one-arm handstand line happens in the frontal plane (straddle, weight shift, free arm out to the side), which a
  profile can't show. Like the flags and the pancake, these clips are drawn facing the viewer, and the app shows each
  beside a side clip (FACING_CLIPS in ExerciseAnimationView.kt): the freestanding handstand, or the chest-to-wall
  handstand for the supported shift. The support hand stays planted; the shoulder is the rig's single point, so the free
  hand reaches the floor only close in.
- The shrimp squats are side-on: the front foot planted, the rear knee folded behind and lowered to a pad (a raised
  block for the partial version). Assisted versions hold the right-hand post, so the front view draws the post too.
- The Hefesto line is side-on with the bar behind the athlete, on bars the front view knows: the feet-supported German
  hang's bar for the hang, the partial and the assisted Hefesto (feet on the floor in front, lifting off once the hips
  rise past their reach), the waist-high bar with the feet on a box for the behind-bar support and Korean dip, and the
  muscle-up's bar for the strict Hefesto.
Closed chains are dense keyframes solved with progressions.arm_to/leg_to, as in progressions.py.
"""
from author import *
from progressions import arm_to, leg_to, dense, repin
from group_e import _track
from form_review import marker, _end
import mappings


def _blend(a, b, w):
    """Angle pair a toward b by w, the short way round."""
    return [round((x + (((y - x + 180) % 360) - 180) * w) % 360, 1) for x, y in zip(a, b)]


def _lerp(a, b, t):
    return [a[i] + (b[i] - a[i]) * t for i in range(len(a))]


def _out_and_back(path, start, top, hold, end):
    """The path over [start, top], held to hold, then back the same way to end."""
    up = [(round(start + (top - start) * t, 4), k) for t, k in path]
    down = [(round(hold + (end - hold) * (1 - t), 4), k) for t, k in reversed(path)]
    return up + down


# ---------- one-arm handstand line (drawn facing the viewer) ----------
# The support hand (the near arm) stays on the floor at OAH_HAND. Two-arm shapes put the shoulder over the centre with
# the other hand 14 to the right; one-arm shapes move the shoulder 3 toward the support hand and tilt the trunk so the
# hips stack over it, the straddle opening evenly either side of that vertical.
OAH_HAND = (93.0, 105.0)
OAH_REACH = 25.95


def _oah_shoulder(offset):
    """The shoulder [offset] right of the support hand, the support arm straight."""
    return (OAH_HAND[0] + offset, OAH_HAND[1] - math.sqrt(OAH_REACH ** 2 - offset ** 2))


def _oah(key, offset, torso, legs, far_hand=None, far_angle=None):
    near_leg, far_leg = legs
    pose(key, facing=1, torso=torso, head=torso, legs=[near_leg, near_leg], leg_far=[far_leg, far_leg],
         foot_near=near_leg, foot_far=far_leg, pin={"joint": "shoulder", "at": list(_oah_shoulder(offset))})
    arm_to(key, OAH_HAND, ("arm_near",))
    if far_hand is not None:
        arm_to(key, far_hand, ("arm_far",))
    else:
        POSES[key]["arm_far"] = [far_angle, far_angle]
    repin(key, "hand_near")
    return key


def _stack_torso(offset):
    """The trunk angle that puts the hips straight over the support hand."""
    return round(180 - math.degrees(math.asin(offset / TORSO)), 2)


STRADDLE, TOGETHER = (330.0, 30.0), (357.0, 3.0)
OAH_TWO = 7.0                         # two hands: the shoulder over the centre between them
OAH_ONE = 4.0                         # one hand: the shoulder moved toward it
OAH_FAR_FLOOR = (OAH_HAND[0] + 2 * OAH_TWO, 105.0)
OAH_FINGERTIP = (OAH_HAND[0] + 10.0, 104.5)

_oah("oah_two_together", OAH_TWO, 180, TOGETHER, far_hand=OAH_FAR_FLOOR)
_oah("oah_two_straddle", OAH_TWO, 180, STRADDLE, far_hand=OAH_FAR_FLOOR)
# Weight shifts: the hands and shoulder stay; the hips move over the support hand. Against the wall the feet stay put;
# freestanding, the straddle leans with the trunk.
_oah("oah_shift_wall", OAH_TWO, 166, (7.0, 10.0), far_hand=OAH_FAR_FLOOR)
_oah("oah_shift_free", OAH_TWO, 166, (316.0, 16.0), far_hand=OAH_FAR_FLOOR)
# Stacked over one hand: fingertips down, hovering just off the floor, then out to the side.
_ONE = _stack_torso(OAH_ONE)
_oah("oah_fingertip", OAH_ONE, _ONE, STRADDLE, far_hand=OAH_FINGERTIP)
_hover_y = 100.0
_sx, _sy = _oah_shoulder(OAH_ONE)
_oah("oah_hover", OAH_ONE, _ONE, STRADDLE, far_angle=round(ang(math.sqrt((UPPER + FORE - 0.05) ** 2 - (_hover_y - _sy) ** 2),
                                                                _hover_y - _sy), 1))
_oah("oah_one_straddle", OAH_ONE, _ONE, STRADDLE, far_angle=115.0)
_oah("oah_one_closing", OAH_ONE, _ONE, (345.0, 15.0), far_angle=115.0)
_oah("oah_one", OAH_ONE, _ONE, TOGETHER, far_angle=115.0)

template("straddle_handstand", 3200, [(0, "oah_two_together"), (0.6, "oah_two_straddle"), (1, "oah_two_straddle")])
template("supported_handstand_shift", 3600, [(0, "oah_two_together"), (0.6, "oah_shift_wall"), (1, "oah_shift_wall")])
template("handstand_weight_shift", 3600, [(0, "oah_two_straddle"), (0.6, "oah_shift_free"), (1, "oah_shift_free")])
template("fingertip_one_arm_handstand", 3600, [(0, "oah_two_straddle"), (0.6, "oah_fingertip"), (1, "oah_fingertip")])
template("one_arm_handstand_hover", 3200, [(0, "oah_fingertip"), (0.5, "oah_hover"), (1, "oah_hover")])
template("straddle_one_arm_handstand", 3200, [(0, "oah_hover"), (0.6, "oah_one_straddle"), (1, "oah_one_straddle")])
template("one_arm_handstand", 3200, [(0, "oah_one_closing"), (0.6, "oah_one"), (1, "oah_one")])


# ---------- shrimp squat line (side-on, facing right) ----------
# The front ankle stays at SHRIMP_FRONT; the hips sit back and down while the trunk leans forward and the rear knee,
# folded behind, lowers to the pad. The assisted versions hold the right-hand post.
SHRIMP_FRONT = (122.0, ANKLE)
SHRIMP_GRIP = (138.0, 64.0)
APPARATUS["shrimp_pad"] = [{"type": "round_rect", "values": [86, 103, 114, FLOOR], "filled": True}]
# The partial version's raised target: a block the rear knee reaches about halfway down.
SHRIMP_PARTIAL = 0.52
APPARATUS["shrimp_block"] = [{"type": "round_rect", "values": [86, 96.5, 112, FLOOR], "filled": True}]


def _shrimp(prefix, assisted=False, held=False, partial=False):
    def build(key, t):
        depth = t * (SHRIMP_PARTIAL if partial else 1.0)
        thigh = 210 - 15 * depth
        # Held: the heel pulled to the seat; floating: the shin trails behind and up, clear of the floor.
        shin = thigh + (172 if held else 110)
        torso = 12 + depth * 20
        pose(key, facing=1, torso=round(torso, 2), head=round(torso - 6, 2), hip=[117 - 12 * depth, 74 + 14 * depth],
             arms=[75, 90], arm_far=[78, 93], foot_near=90, foot_far=round(shin % 360, 1),
             leg_far=[round(thigh, 1), round(shin % 360, 1)])
        leg_to(key, SHRIMP_FRONT, ("leg_near",))
        if assisted:
            arm_to(key, SHRIMP_GRIP, ("arm_near",) if held else ("arm_near", "arm_far"))
        if held:
            arm_to(key, solved(key)["foot_far"], ("arm_far",))
        repin(key, "foot_near")
        return key
    return dense(prefix, build, 16)


_SHRIMPS = {
    "assisted_partial_shrimp": dict(assisted=True, partial=True),
    "assisted_shrimp": dict(assisted=True),
    "floating_shrimp": dict(),
    "assisted_held_shrimp": dict(assisted=True, held=True),
    "shrimp_squat": dict(held=True),
}
for _name, _kw in _SHRIMPS.items():
    _scene = ("shrimp_block" if _kw.get("partial") else "shrimp_pad",) + (("post_right",) if _kw.get("assisted") else ())
    template(_name, 3000, _shrimp("shrimp_" + _name, **_kw), apparatus=_scene, easing="linear")
    marker([_name], "knee_far", lambda n: _end(n, "knee_far")[1], "back_knee_down")


# ---------- Hefesto line (side-on, facing right, the bar behind) ----------
def _hef(key, hand, shoulder, torso, legs=None, foot=None, reach=30.4):
    """Hands on the bar behind; the shoulder at [shoulder]; legs as given, or the feet on [foot] while they reach it."""
    pose(key, facing=1, torso=round(torso % 360, 2), head=round(torso % 360, 2), feet=180,
         pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    if legs is not None:
        POSES[key]["legs"] = [round(v % 360, 1) for v in legs]
        POSES[key]["leg_far"] = [round((v + 2) % 360, 1) for v in legs]
        POSES[key]["feet"] = round(legs[1] % 360, 1)
    if foot is not None:
        hip = solved(key)["hip"]
        gap = math.hypot(foot[0] - hip[0], foot[1] - hip[1])
        planted = leg_to(key, foot, ("leg_near", "leg_far"))
        POSES[key]["feet"] = 90
        # Past the legs' reach the feet lift off and the legs hang (blended over a few units, so nothing jumps).
        w = min(1.0, max(0.0, (gap - reach) / 6.0))
        if w > 0 and legs is not None:
            POSES[key]["leg_near"] = _blend(list(planted), legs, w)
            POSES[key]["leg_far"] = _blend(list(planted), [legs[0] + 2, legs[1] + 2], w)
            # The feet stay flexed (at right angles to the shin) as they leave the floor; pointing them would dip the
            # toes through it.
            flexed = legs[1] - 90
            POSES[key]["feet"] = round((90 + ((flexed - 90 + 180) % 360 - 180) * w) % 360, 1)
    arm_to(key, hand, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


def _path(us, xs, ys, torsos, thighs=None, shins=None):
    def at(u):
        out = ([_track(us, xs, u), _track(us, ys, u)], _track(us, torsos, u))
        legs = [_track(us, thighs, u), _track(us, shins, u)] if thighs else None
        return out + (legs,)
    return at


# Strict Hefesto on the muscle-up's bar: from a still German hang (trunk pitched forward, arms straight up behind),
# pull with the elbows going back, pass the bar behind the hips and press to straight-arm support above it.
HEF_BAR = (95.0, 40.0)
_hef_path = _path([0, 0.25, 0.45, 0.6, 0.8, 1.0],
                  [5, 11, 14, 14, 12, 9], [25.4, 16, 6, -2, -13, -24.3],
                  [58, 40, 25, 18, 12, 10],
                  [225, 205, 185, 178, 174, 174], [240, 220, 205, 192, 184, 180])


def _hefesto(key, u):
    rel, torso, legs = _hef_path(u)
    return _hef(key, HEF_BAR, (HEF_BAR[0] + rel[0], HEF_BAR[1] + rel[1]), torso, legs=legs)


_hef_up = dense("hef_strict", _hefesto, 24, span=1.0, hold=False)
template("hefesto", 6000, [(0, _hef_up[0][1])] + _out_and_back(_hef_up, 0.06, 0.5, 0.6, 0.94) + [(1, _hef_up[0][1])],
         mode="loop", apparatus=("mid_bar",), easing="linear")

# Foot-assisted work on the feet-supported German hang's bar: the feet on the floor in front carry most of the weight.
GH_HAND = (80.0, 46.0)
GH_FOOT = (100.0, ANKLE)


def _gh_shoulder(phi):
    """The shoulder on the straight-arm circle round the grip, [phi] degrees round from straight up."""
    return (GH_HAND[0] + OAH_REACH * math.sin(math.radians(phi)), GH_HAND[1] - OAH_REACH * math.cos(math.radians(phi)))


# The supinated German hang: standing tall with the arms reaching back to the bar, the hips sink back and down while the
# shoulders open into the hang, elbows straight; then out the same way.
_gh_sink = dense("hef_gh", lambda key, s: _hef(key, GH_HAND, _gh_shoulder(112 + 32 * s), 5 + 20 * s, foot=GH_FOOT), 12,
                 span=0.7)
template("assisted_supinated_german_hang", 4800, _gh_sink, apparatus=("gh_bar",), easing="linear")
GH_HANG = _gh_shoulder(144)

# Partial pull: from the hang the elbows bend and the shoulders rise as far as the planted feet allow, then lower.
_gh_partial = dense("hef_partial", lambda key, s: _hef(key, GH_HAND, _lerp(GH_HANG, (97.0, 57.5), s), 25 - 3 * s,
                                                        foot=GH_FOOT), 14)
template("assisted_partial_hefesto", 4400, _gh_partial, apparatus=("gh_bar",), easing="linear")

# The whole Hefesto with the feet helping from the floor; once the hips rise past the legs' reach the feet lift and the
# arms finish the press.
_assist_path = _path([0, 0.2, 0.4, 0.55, 0.75, 1.0],
                     [GH_HANG[0] - GH_HAND[0], 17, 16.5, 15, 12.5, 9], [GH_HANG[1] - GH_HAND[1], 11.5, 3, -4, -13, -24.3],
                     [25, 22, 18, 15, 12, 10],
                     [150, 160, 168, 172, 172, 172], [210, 205, 195, 186, 182, 182])


def _assisted(key, u):
    rel, torso, legs = _assist_path(u)
    return _hef(key, GH_HAND, (GH_HAND[0] + rel[0], GH_HAND[1] + rel[1]), torso, legs=legs, foot=GH_FOOT)


_assist_up = dense("hef_assisted", _assisted, 24, span=1.0, hold=False)
template("assisted_hefesto", 6000,
         [(0, _assist_up[0][1])] + _out_and_back(_assist_up, 0.06, 0.5, 0.6, 0.94) + [(1, _assist_up[0][1])],
         mode="loop", apparatus=("gh_bar",), easing="linear")

# Behind-bar support and the Korean dip on the waist-high bar, the feet up on a box in front at about hip height, so
# the legs reach forward to it from the top of the support and through the dip's comfortable range (the shoulders drop
# about halfway to the bar). The box ends at the toes, so the side view's bar frame, which stands its post just past the
# movement, doesn't cross it.
LOW_HAND = (70.0, 66.0)
LOW_FOOT = (101.0, 74.0)
APPARATUS["hefesto_box"] = [{"type": "round_rect", "values": [97, 76, 106, FLOOR], "filled": True}]
LOW_TOP = (LOW_HAND[0] + 9.0, LOW_HAND[1] - 24.3)


def _korean(key, s):
    shoulder = (LOW_TOP[0] + 4.0 * s, LOW_TOP[1] + 12.3 * s)
    return _hef(key, LOW_HAND, shoulder, 10 + 6 * s, foot=LOW_FOOT)


# The support settles from slightly soft elbows to locked ones and holds.
template("assisted_behind_bar_support", 3000,
         dense("hef_support", lambda key, s: _hef(key, LOW_HAND, (LOW_TOP[0] + 0.5 * (1 - s), LOW_TOP[1] + 2.0 * (1 - s)),
                                                  11 - s, foot=LOW_FOOT), 6, span=0.6),
         apparatus=("low_bar", "hefesto_box"), easing="linear")
template("assisted_korean_dip", 3200, dense("hef_korean", _korean, 14), apparatus=("low_bar", "hefesto_box"),
         easing="linear")

# Holds read "Hold · breathe".
for _name in ("straddle_handstand", "fingertip_one_arm_handstand", "straddle_one_arm_handstand", "one_arm_handstand",
              "assisted_supinated_german_hang", "assisted_behind_bar_support"):
    TEMPLATES[_name]["phases"] = "hold"

FOCUSED = ("straddle_handstand", "supported_handstand_shift", "handstand_weight_shift", "fingertip_one_arm_handstand",
           "one_arm_handstand_hover", "straddle_one_arm_handstand", "one_arm_handstand", "assisted_partial_shrimp",
           "assisted_shrimp", "floating_shrimp", "assisted_held_shrimp", "shrimp_squat", "assisted_supinated_german_hang",
           "assisted_behind_bar_support", "assisted_korean_dip", "assisted_partial_hefesto", "assisted_hefesto", "hefesto")
for _name in FOCUSED:
    mappings.MOVEMENTS[_name] = _name
