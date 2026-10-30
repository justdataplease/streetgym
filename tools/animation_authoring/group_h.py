"""Group H: review 9's dynamic strength (the 10-year class coach) - the tuck front-lever row on the high bar with the
feet free, and the tuck planche push-up.

Closed chains follow progressions.py: dense keyframes, each solved with two-bone IK that keeps the anatomical bend and
re-pinned on its loaded contact (the near hand), so interpolation stays inside the tolerance the validators check.
"""
from author import *
from progressions import _lerp, _pose, _template, arm_to, dense, repin

# ---------- tuck front-lever row (high bar, feet free) ----------
# The trunk stays level and the hips folded as in the tuck front lever. From the lever on straight arms, row until the
# elbows reach about a right angle with the trunk still level, then lower back to the lever. The thighs open a little on
# the way up so the tucked knees stay under the bar.
TLR_HAND = tuple(solved("fl_tuck")["hand_near"])
TLR_BOTTOM = tuple(solved("fl_tuck")["shoulder"])
TLR_TOP = (TLR_HAND[0] - 4.5, TLR_HAND[1] + 14.0)


def _lever_row(key, s):
    shoulder = _lerp(list(TLR_BOTTOM), list(TLR_TOP), s)
    _pose(key, facing=1, torso=270, head=280, shrug=-1, legs=[335 - 10 * s, 145 - 10 * s], leg_far=[337 - 10 * s, 147 - 10 * s],
          feet=150 - 10 * s,
          pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_to(key, TLR_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


_template("tuck_front_lever_row", 2600, dense("tlr", _lever_row, 12), apparatus=("high_bar",), easing="linear")


# ---------- tuck planche push-up (floor) ----------
# From the tuck planche the elbows bend straight back along the body while the shoulders travel further ahead of the
# hands and dip; the trunk stays nearly level and the knees fold in to the chest so the feet stay off the floor, then
# press back to locked arms.
TPP_HAND = tuple(solved("tp_hold")["hand_near"])
TPP_TOP = tuple(solved("tp_hold")["shoulder"])
TPP_DROP, TPP_AHEAD = 6.0, 4.0


def _tp_push(key, s):
    ahead = -1.0 if TPP_TOP[0] < TPP_HAND[0] else 1.0  # the side of the hands the shoulders lean to
    shoulder = (TPP_TOP[0] + ahead * TPP_AHEAD * s, TPP_TOP[1] + TPP_DROP * s)
    # Facing left: the hips stay level with the shoulders, the thighs fold toward the chest and the feet tuck up.
    _pose(key, facing=-1, torso=275 - 4 * s, head=280 - 4 * s, protract=3 * (1 - s), legs=[200 + 50 * s, 100 - 25 * s],
          leg_far=[202 + 50 * s, 102 - 25 * s], feet=190 - 25 * s, pin={"joint": "shoulder", "at": [round(v, 3) for v in shoulder]})
    arm_to(key, TPP_HAND, ("arm_near", "arm_far"))
    repin(key, "hand_near")
    return key


_template("tuck_planche_pushup", 2600, dense("tpp", _tp_push, 12), apparatus=("mat",), easing="linear")
