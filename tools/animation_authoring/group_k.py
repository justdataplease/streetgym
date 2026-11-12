"""Group K: the warm-up opener and the recovery day's squat (the owner, 2026-11-12: "remove the marching walking, have
something else like jumping jacks"). Three AI coach personas chose the low-impact jack for Level 1, recovery and legs
days, a full jumping jack for the other classes from Level 2, and a slow squat to stand in place of the walk.

The jumping jack is drawn like the low-impact jack (arms forward at shoulder height on the way up, a profile hides the
side sweep), with a small hop: the feet leave the floor halfway and land with soft knees. Its front view
(FrontView.Motion.JUMPING_JACK) shows both feet jumping out wide. The squat to stand folds to the toes, sinks into a
deep squat holding them with the chest up, lifts the hips back to the fold and rolls up tall with the arms overhead.
"""
from author import *

X = 100

# ---------- jumping jack (ping-pong: together, in the air, landed wide) ----------
pose("jj_air", base="stand", arms=[90, 90], arm_far=[94, 94], legs=[180, 180], leg_far=[183, 177], feet=150,
     pin={"joint": "foot_near", "at": [X, ANKLE - 7]})
pose("jj_land", base="stand", arm_near=[8, 5], arm_far=[12, 8], leg_near=[188, 172], leg_far=[172, 188])
template("jumping_jack", 1100, [(0, "stand"), (0.5, "jj_air"), (1, "jj_land")])

# ---------- squat to stand (loop) ----------
# Tall, reach forward and fold to the toes, sink into the squat holding them, hips up to the fold again, roll up tall.
# The reach keeps the arms moving in front of the body on the way down and up.
STS_PIN = {"joint": "foot_near", "at": [X, ANKLE]}
TOES = (X + 6, ANKLE - 1)
pose("sts_tall", base="stand", arm_near=[4, 4], arm_far=[8, 8])
pose("sts_reach", facing=1, torso=55, head=55, arms=[112, 112], arm_far=[116, 116], leg_near=leg(3, 30.5, RIGHT),
     leg_far=leg(2, 30.5, RIGHT), pin=STS_PIN)
pose("sts_fold", facing=1, torso=148, head=160, leg_near=leg(4, 30, RIGHT), leg_far=leg(3, 30, RIGHT), pin=STS_PIN)
reach_arm("sts_fold", TOES, pref=RIGHT)
pose("sts_squat", facing=1, torso=32, head=12, leg_near=leg(5, 9, RIGHT), leg_far=leg(4, 9, RIGHT), pin=STS_PIN)
reach_arm("sts_squat", TOES, pref=RIGHT)
template("squat_to_stand", 6000, [(0, "sts_tall"), (0.12, "sts_reach"), (0.24, "sts_fold"), (0.42, "sts_squat"),
                                  (0.56, "sts_squat"), (0.74, "sts_fold"), (0.87, "sts_reach"), (1, "sts_tall")],
         mode="loop", reduced=0.5)
