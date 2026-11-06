"""Group J: yoga-style chest openers (the owner, 2026-11-06: "include chest opener ... support this kind of movement
yoga style") - the bench prayer stretch, the lying chest stretch, the child's pose arm lift, the hands-off cobra, the
lunge overhead reach and the behind-back chest opener, for the free Chest Opener workout.

Most are long, breathing holds: the clip eases into the stretch and stays there, like the other stretches. The arm
lift pulses five times and then holds the top, as its note says. The lying chest stretch rolls away from an arm that
points at the viewer, so the side clip shows the arm long on the floor, the other hand pressing by the face and the
top knee bending to roll.
"""
from author import *

# ---------- bench prayer stretch (kneeling, facing the bench on the left) ----------
# Knees under the hips, straight arms with the hands on the bench, the back flat; breathing out, the chest sinks
# below the hands.
PS_KNEE = {"joint": "knee_near", "at": [136.0, 102.0]}
PS_HAND = (92.0, 88.0)


def _prayer(name, torso):
    pose(name, facing=-1, torso=torso, head=torso, legs=[182, 90], leg_far=[180, 90], feet=90, pin=PS_KNEE)
    reach_arm(name, PS_HAND, pref=UP)
    return name


_prayer("ps_flat", 270)
_prayer("ps_sink", 255)
template("bench_prayer_stretch", 3600, [(0, "ps_flat"), (0.5, "ps_sink"), (1, "ps_sink")], apparatus=("low_bench",))

# ---------- lying chest stretch (face down, head on the left) ----------
# The near arm lies long on the floor; the far hand presses down by the face and the far knee bends and plants the
# foot behind, which rolls the body away from the arm.
pose("pcs_flat", base="arch_flat", head=262, arms=[270, 270], arm_far=[270, 270])
pose("pcs_roll", base="arch_flat", head=258, arms=[270, 270], foot_far=90, hip=[104, 99.5])
reach_arm("pcs_roll", (72.0, 104.5), pref=UP, which=("arm_far",))
reach_leg("pcs_roll", (121.0, ANKLE), pref=UP, which=("leg_far",))
template("prone_chest_stretch", 3800, [(0, "pcs_flat"), (0.45, "pcs_roll"), (1, "pcs_roll")], apparatus=("mat",))

# ---------- child's pose arm lift ----------
# From child's pose one straight arm lifts, pulses five times and holds the top.
pose("cpl_down", base="child")
pose("cpl_up", base="child", arm_near=[291, 291])
pose("cpl_pulse", base="child", arm_near=[284, 284])
_lift = [(0, "cpl_down"), (0.1, "cpl_up")]
for _i in range(4):
    _lift += [(0.15 + 0.1 * _i, "cpl_pulse"), (0.2 + 0.1 * _i, "cpl_up")]
_lift += [(0.88, "cpl_up"), (1, "cpl_down")]
template("child_pose_arm_lift", 6400, _lift, mode="loop", apparatus=("mat",), reduced=0.7)

# ---------- hands-off cobra (face down, head on the left) ----------
# The chest lifts from the upper back with the hands just off the floor, elbows drawn back toward the hips and the
# chin tucked; the hips and legs stay down.
pose("hc_down", base="arch_flat", head=272, arms=[100, 270])
pose("hc_up", base="arch_flat", torso=290, head=292, arms=[118, 274], spine=2.5)
template("hover_cobra", 3200, [(0, "hc_down"), (0.45, "hc_up"), (1, "hc_up")], apparatus=("mat",))

# ---------- lunge overhead reach (low lunge, facing right) ----------
# Back knee down, a towel pulled apart overhead; the arms press back and the chest lifts, the bend from the upper back.
pose("lar_up", base="hip_flexor", torso=0, head=0, arms=[2, 2])
reach_leg("lar_up", (109, ANKLE), pref=RIGHT, which=("leg_near",))
pose("lar_back", base="hip_flexor_fwd", torso=354, head=346, arms=[338, 338], spine=2)
reach_leg("lar_back", (109, ANKLE), pref=RIGHT, which=("leg_near",))
template("lunge_arm_reach", 3600, [(0, "lar_up"), (0.5, "lar_back"), (1, "lar_back")], apparatus=("mat",))

# ---------- behind-back chest opener (standing, facing right) ----------
# A towel held behind the back with straight arms; the shoulder blades squeeze and the arms lift away from the back.
pose("bbo_low", base="stand", arms=[194, 194])
pose("bbo_lift", base="stand", torso=358, head=356, arms=[222, 222])
template("behind_back_chest_opener", 3400, [(0, "bbo_low"), (0.5, "bbo_lift"), (1, "bbo_lift")])
