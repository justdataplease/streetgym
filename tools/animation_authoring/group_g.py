"""Group G: kick-up to the wall (round 2 of the 2.4.0 pro-athlete reviews).

From a lunge facing a clear wall, the hands reach to the floor a hand's width from it, the back leg kicks and
the push leg follows into a back-to-wall handstand with the heels touching the wall softly; the athlete then
comes down the way they went up. Closed chains follow progressions.py: dense keyframes, pinned on the front
foot while it carries the weight and on the hands once they are down.
"""
from author import *
from group_e import _mirror, _track
from progressions import _pose, _signed, _template, dense, leg_to, repin

KU_HAND = (141.0, 105.0)          # hands about a hand's width from the wall at x 150 (round 5)
KU_FRONT = (119.0, ANKLE)         # front (push) foot
KU_REAR = (95.0, ANKLE)           # back (kicking) foot, on the ball of the foot
KU_HIP0 = (110.0, 77.0)           # upright lunge
KU_TOUCH_TORSO, KU_TOUCH_ARM = 100.0, 170.0
_neck = (KU_HAND[0] - (UPPER + FORE) * d(KU_TOUCH_ARM)[0], KU_HAND[1] - (UPPER + FORE) * d(KU_TOUCH_ARM)[1])
KU_HIP1 = (_neck[0] - TORSO * d(KU_TOUCH_TORSO)[0], _neck[1] - TORSO * d(KU_TOUCH_TORSO)[1])
# Top: stacked arms, hips leaning slightly toward the wall, legs reaching it with the heels on the wall.
KU_TOP_TORSO, KU_TOP_ARM, KU_TOP_FOOT = 190.0, 178.0, 3.0
_shoulder = (KU_HAND[0] - (UPPER + FORE) * d(KU_TOP_ARM)[0], KU_HAND[1] - (UPPER + FORE) * d(KU_TOP_ARM)[1])
_hip_top = (_shoulder[0] - TORSO * d(KU_TOP_TORSO)[0], _shoulder[1] - TORSO * d(KU_TOP_TORSO)[1])
KU_TOP_LEGS = math.degrees(math.asin((148.5 - _hip_top[0]) / (THIGH + SHIN)))
_REAR_ANGLE = ang(KU_REAR[0] - KU_HIP0[0], KU_REAR[1] - KU_HIP0[1])
FOOT_FLAT, FOOT_TOES = 90.0, 112.0


def _turn(a, b, f):
    """Angle from a toward b along the shorter way."""
    return round((a + _signed(b - a) * max(0.0, min(1.0, f))) % 360, 1)


def _reach(key, s):
    """Lunge to hands-down, the weight on the front foot: the hips travel forward as the torso pitches over."""
    torso = 8.0 + (KU_TOUCH_TORSO - 8.0) * s
    arms = _track([0.0, 0.4, 1.0], [6.0, 135.0, KU_TOUCH_ARM], s)  # overhead, then reaching down toward the floor
    hip = [KU_HIP0[i] + (KU_HIP1[i] - KU_HIP0[i]) * s for i in (0, 1)]
    rear = _REAR_ANGLE + (215.0 - _REAR_ANGLE) * s
    _pose(key, facing=1, torso=round(torso, 2), head=round(torso + 8 * s, 2), arms=[arms, arms],
          leg_far=[rear, rear], foot_far=FOOT_TOES, foot_near=FOOT_FLAT, hip=hip)
    leg_to(key, KU_FRONT, ("leg_near",))
    repin(key, "foot_near")
    return key


_reach("ku_ik_touch", 1.0)
_NEAR_TOUCH = list(POSES["ku_ik_touch"]["leg_near"])
del POSES["ku_ik_touch"]


def _kick(key, v):
    """Hands down: the back leg kicks up first, the push leg follows, both meet at the wall."""
    torso = _track([0.0, 0.5, 1.0], [KU_TOUCH_TORSO, 150.0, KU_TOP_TORSO], v)
    arm = _track([0.0, 1.0], [KU_TOUCH_ARM, KU_TOP_ARM], v)
    far = _track([0.0, 0.45, 0.8, 1.0], [215.0, 300.0, 370.0, 360.0 + KU_TOP_LEGS], v) % 360
    thigh = _track([0.0, 0.4, 0.75, 1.0], [_NEAR_TOUCH[0], 200.0, 330.0, 360.0 + KU_TOP_LEGS], v) % 360
    shin = _track([0.0, 0.4, 0.75, 1.0], [_NEAR_TOUCH[1], 250.0, 345.0, 360.0 + KU_TOP_LEGS], v) % 360
    _pose(key, facing=1, torso=round(torso, 2), head=round(torso + 8 - 3 * v, 2), arms=[arm, arm],
          leg_far=[far, far], leg_near=[thigh, shin],
          foot_far=_turn(FOOT_TOES, far if v < 1 else KU_TOP_FOOT, v / 0.6),
          foot_near=_turn(FOOT_FLAT, shin if v < 1 else KU_TOP_FOOT, v / 0.6),
          pin={"joint": "hand_near", "at": list(KU_HAND)})
    if v >= 0.6:
        POSES[key]["foot_far"] = _turn(far, KU_TOP_FOOT, (v - 0.6) / 0.4)
        POSES[key]["foot_near"] = _turn(shin, KU_TOP_FOOT, (v - 0.6) / 0.4)
    return key


_up1 = dense("ku_reach", _reach, 10, span=1.0, hold=False)
_up2 = dense("ku_kick", _kick, 12, span=1.0, hold=False)[1:]
_up = [(round(0.06 + 0.26 * t, 4), k) for t, k in _up1] + \
      [(round(0.32 + 0.22 * (i + 1) / len(_up2), 4), k) for i, (_, k) in enumerate(_up2)]
_template("wall_kickup", 5200, [(0, _up1[0][1])] + _up + _mirror(_up, 0.64, 0.94) + [(1, _up1[0][1])],
          mode="loop", apparatus=("wall_right", "mat"), easing="linear", reduced=0.54)
