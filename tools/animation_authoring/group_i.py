"""Group I: the Aches & posture workouts (the owner, 2026-11-05: "a new category for specific problems") - the chin tuck,
the forward neck stretch, the lying chin nod and the wall sit.

The neck moves only in the side view (the head angle); the front view shows the still body, which is enough for these
small, slow moves. The wall sit keeps its feet still and slides the hips down the wall.
"""
from author import *

# ---------- chin tuck (standing) ----------
# The head starts a little forward, glides back until it stacks over the shoulders, holds, then relaxes.
pose("ct_forward", base="stand", head=20)
pose("ct_back", base="stand", head=-6)
template("chin_tuck", 3200, [(0, "ct_forward"), (0.55, "ct_back"), (1, "ct_back")])

# ---------- forward neck stretch (standing) ----------
# The chin lowers toward the chest to a mild stretch, holds, and lifts slowly.
pose("ns_up", base="stand", head=0)
pose("ns_down", base="stand", head=35)
template("neck_stretch", 3600, [(0, "ns_up"), (0.45, "ns_down"), (1, "ns_down")])

# ---------- lying chin nod (on the back, knees bent) ----------
# A small nod: the chin moves toward the chest and the back of the neck lengthens; the head stays down.
pose("lcn_rest", base="bridge_down", head=270)
pose("lcn_nod", base="bridge_down", head=292)
template("lying_chin_nod", 3200, [(0, "lcn_rest"), (0.5, "lcn_nod"), (1, "lcn_nod")])

# ---------- wall sit (back to the wall on the right) ----------
# Facing left, the back flat on the wall, the feet still a step ahead of it; the hips slide down to a shallow sit
# (thighs about 45 degrees below level), hold, and slide back up.
WS_HIP_X = 146.0
WS_FOOT = (131.5, ANKLE)


def _wall_sit(name, hip_y):
    pose(name, facing=-1, torso=0, arms=[180, 180], pin={"joint": "hip", "at": [WS_HIP_X, hip_y]},
         legs=leg(WS_FOOT[0] - WS_HIP_X, WS_FOOT[1] - hip_y, LEFT))
    return name


_wall_sit("ws_high", 75.0)
_wall_sit("ws_low", 79.5)
template("wall_sit", 3600, [(0, "ws_high"), (0.4, "ws_low"), (1, "ws_low")], apparatus=("wall_right",))
