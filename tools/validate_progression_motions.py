"""Sampled motion checks for the progression animations (tools/animation_authoring/progressions.py
and group_e.py).

Every template is sampled at 201 phases, so the frames between keyframes are checked too:
loaded contacts stay pinned, straight-arm supports stay straight, elbows and knees bend the
anatomical way, and the head stays inside the canvas and clear of the floor.
Run: python -I tools/validate_progression_motions.py
"""
import json
import math
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
from preview_animations import Figure, Template, _blend_meta  # noqa: E402

ROOT = TOOLS.parent
asset = json.loads((ROOT / "app/src/main/assets/exercise_animations.json").read_text(encoding="utf-8"))
figure = Figure(asset["figure"])
WIDTH, HEIGHT = float(asset["canvas"]["width"]), float(asset["canvas"]["height"])
FLOOR, HAND_FLOOR, ANKLE = 106.0, 105.0, 104.0
TOL, SAMPLES, STRAIGHT = 0.2, 201, 25.9

EXPECTED = {
    "archer_pullup": "archer_pullup", "archer_pushup": "archer_pushup", "elevated_pike_pushup": "elevated_pike_pushup",
    "wall_hspu_negative": "wall_hspu_negative", "wall_hspu": "wall_hspu", "one_leg_lsit": "one_leg_lsit", "lsit": "lsit",
    "seated_pike_lift": "seated_pike_lift", "bulgarian_split_squat": "bulgarian_split_squat",
    "sliding_leg_curl": "sliding_leg_curl", "nordic_negative": "nordic_negative", "single_leg_rdl": "single_leg_rdl",
    "prone_ytw": "prone_ytw", "squat_jump": "half_squat_jump",
    # group_e.py
    "straight_side_plank": "straight_side_plank", "hollow_body_hold": "hollow_body_hold", "german_hang": "german_hang",
    "handstand_exit": "handstand_exit", "straddle_flag": "straddle_flag", "full_flag": "full_flag",
    "nordic_curl": "nordic_curl",
    # group_f.py (Level 7 "Mastery")
    "typewriter_pullup": "typewriter_pullup", "assisted_one_arm_pullup": "assisted_one_arm_pullup",
    "one_arm_pullup_negative": "one_arm_pullup_negative", "deficit_wall_hspu": "deficit_wall_hspu",
    "freestanding_hspu_negative": "freestanding_hspu_negative",
    # flag_hang_skills.py (round 11)
    "vertical_flag": "vertical_flag", "flag_negative": "flag_negative", "skin_the_cat": "skin_the_cat",
    "supinated_german_hang": "supinated_german_hang",
}
# The typewriter guide faces the viewer (its slide is sideways), so its elbows point out to the sides;
# group_f checks them against the bar and the outer side instead of the side-view bend direction.
FRONT_VIEW = {"typewriter_pullup"}
failures = []
cache = {}


def fail(name, message):
    failures.append(f"{name}: {message}")


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def angle(a, b):
    """Absolute angle of a->b (0 up, 90 right)."""
    return math.degrees(math.atan2(b[0] - a[0], -(b[1] - a[1]))) % 360


def signed(a):
    return (a + 180.0) % 360.0 - 180.0


def motion(name):
    return Template(name, asset["templates"][name], asset["poses"], cache)


def frames(name):
    """(phase index, keyframe time, joints) for every sample of one cycle."""
    m = motion(name)
    for i in range(SAMPLES):
        phase = i / (SAMPLES - 1)
        yield i, m.timeline(phase), m.pose_at(phase, figure)


def at(name, t):
    m = motion(name)
    return m.pose_at(t / 2 if m.mode == "ping_pong" else t, figure)


def pinned(name, joint, target, window=(0.0, 1.0)):
    for i, t, j in frames(name):
        if window[0] <= t <= window[1] and dist(j[joint], target) >= TOL:
            fail(name, f"{joint} leaves {target} at sample {i} (t={t:.3f}): {tuple(round(v, 2) for v in j[joint])}")
            return


def on_level(name, joint, axis, value, window=(0.0, 1.0)):
    """Sliding contact: the joint keeps one coordinate (floor height or wall line)."""
    for i, t, j in frames(name):
        if window[0] <= t <= window[1] and abs(j[joint][axis] - value) >= TOL:
            fail(name, f"{joint} loses contact with {'xy'[axis]}={value} at sample {i} (t={t:.3f}): {j[joint][axis]:.2f}")
            return


def straight(name, side, minimum=STRAIGHT, window=(0.0, 1.0)):
    for i, t, j in frames(name):
        reach = dist(j["shoulder"], j[f"hand_{side}"])
        if window[0] <= t <= window[1] and reach < minimum:
            fail(name, f"arm_{side} bends ({reach:.2f} < {minimum}) at sample {i} (t={t:.3f})")
            return


def general(name, check_elbows=True, head_floor=FLOOR - 0.5):
    """Canvas, head clearance and anatomical bend direction on every sample."""
    m = motion(name)
    r = figure.head_radius
    for i, t, j in frames(name):
        # A figure that turns (the handstand exit) is judged by its facing at each sample.
        facing = _blend_meta(m, i / (SAMPLES - 1))[1]
        head = j["head"]
        if not (r <= head[0] <= WIDTH - r and r <= head[1] and head[1] + r <= head_floor):
            fail(name, f"head outside the canvas or on the floor at sample {i}: {tuple(round(v, 2) for v in head)}")
            return
        for joint, (x, y) in j.items():
            if not (0 <= x <= WIDTH and 0 <= y <= HEIGHT):
                fail(name, f"{joint} leaves the canvas at sample {i}")
                return
        for side in ("near", "far"):
            knee = signed(angle(j[f"knee_{side}"], j[f"foot_{side}"]) - angle(j["hip"], j[f"knee_{side}"])) * facing
            if knee < -2:
                fail(name, f"knee_{side} bends backward ({knee:.1f} deg) at sample {i}")
                return
            if check_elbows:
                elbow = signed(angle(j["shoulder"], j[f"elbow_{side}"]) - angle(j[f"elbow_{side}"], j[f"hand_{side}"])) * facing
                if elbow < -2:
                    fail(name, f"elbow_{side} bends the wrong way ({elbow:.1f} deg) at sample {i}")
                    return


# ---- mapping ----
for movement, name in EXPECTED.items():
    if asset["movements"].get(movement) != name:
        fail(movement, f"must map to template {name}, found {asset['movements'].get(movement)!r}")
    if name not in asset["templates"]:
        fail(movement, f"template {name} is missing")
if failures:
    print("\n".join(failures))
    sys.exit(1)

for name in EXPECTED.values():
    # Y-T-W arm shapes come from shoulder rotation; front-view guides are checked in their own section.
    general(name, check_elbows=name != "prone_ytw" and name not in FRONT_VIEW)

# 1 archer pull-up: both hands stay on the bar, the far arm stays nearly straight.
pinned("archer_pullup", "hand_near", (92, 12))
pinned("archer_pullup", "hand_far", (110, 12))
straight("archer_pullup", "far", 25.5)
top = at("archer_pullup", 1.0)
if dist(top["shoulder"], top["hand_far"]) - dist(top["shoulder"], top["hand_near"]) < 12:
    fail("archer_pullup", "the far arm is not clearly straighter than the pulling arm at the top")
if top["head"][1] > 12:
    fail("archer_pullup", "the head does not reach the bar at the top")

# 2 archer push-up: toes and near hand planted, far hand slides on the floor with a straight arm.
pinned("archer_pushup", "foot_near", (146, 101))
pinned("archer_pushup", "hand_near", (101, HAND_FLOOR))
on_level("archer_pushup", "hand_far", 1, HAND_FLOOR)
straight("archer_pushup", "far", 25.5)
low = at("archer_pushup", 1.0)
if dist(low["shoulder"], low["hand_near"]) > 20:
    fail("archer_pushup", "the near arm does not bend at the bottom")

# 3 elevated pike push-up: toes on the box, hands on the floor, head forward of the hands, never on the floor.
pinned("elevated_pike_pushup", "toe_near", (124, 80))
pinned("elevated_pike_pushup", "hand_near", (89, HAND_FLOOR))
pinned("elevated_pike_pushup", "hand_far", (89, HAND_FLOOR))
straight("elevated_pike_pushup", "near", 25.5, window=(0.0, 0.0))
general("elevated_pike_pushup", head_floor=HAND_FLOOR - 1)
low = at("elevated_pike_pushup", 1.0)
if not low["head"][0] < 89 - 2:
    fail("elevated_pike_pushup", "the head does not lower forward of the hands")
if at("elevated_pike_pushup", 0.0)["hip"][1] > 65:
    fail("elevated_pike_pushup", "the hips are not high over the hands at the top")

# 4-5 wall handstand push-ups: hands planted, toes on the wall, nothing through the wall, head above the mat.
MAT_TOP = 102.5
for name in ("wall_hspu_negative", "wall_hspu"):
    pinned(name, "hand_near", (137, HAND_FLOOR))
    pinned(name, "hand_far", (137, HAND_FLOOR))
    on_level(name, "toe_near", 0, 150)
    straight(name, "near", window=(0.0, 0.0))
    general(name, head_floor=MAT_TOP)
    lowest = max((j["head"][1] for _, _, j in frames(name)))
    if lowest + figure.head_radius < MAT_TOP - 1.5:
        fail(name, "the head does not lower to the mat")
    for i, t, j in frames(name):
        if any(x > 150.05 for k, (x, _) in j.items() if k != "toe_near"):
            fail(name, f"a limb passes through the wall at sample {i}")
            break

# 6-7 L-sits: straight arms on the parallettes; legs at hip height in the hold.
for name in ("one_leg_lsit", "lsit"):
    pinned(name, "hand_near", (100, 96))
    pinned(name, "hand_far", (100, 96))
    straight(name, "near")
    straight(name, "far")
    hold = at(name, 1.0)
    if abs(hold["foot_near"][1] - hold["hip"][1]) > 1.5:
        fail(name, "the straight leg is not at hip height in the hold")
    if name == "lsit" and abs(hold["foot_far"][1] - hold["hip"][1]) > 1.5:
        fail(name, "both legs must be at hip height")
    if name == "one_leg_lsit" and not hold["knee_far"][1] < hold["hip"][1]:
        fail(name, "the bent knee is not tucked")

# 8 seated pike lift: hips and hands stay down, heels lift a little.
pinned("seated_pike_lift", "hip", (100, 100))
pinned("seated_pike_lift", "hand_near", (113, HAND_FLOOR))
if abs(at("seated_pike_lift", 0.0)["foot_near"][1] - ANKLE) >= TOL:
    fail("seated_pike_lift", "the heels do not start on the floor")
if at("seated_pike_lift", 1.0)["foot_near"][1] > ANKLE - 3:
    fail("seated_pike_lift", "the heels do not lift off the floor")

# 9 Bulgarian split squat: front foot planted, rear foot on the bench, back knee lowers toward the floor.
pinned("bulgarian_split_squat", "foot_near", (112, ANKLE))
pinned("bulgarian_split_squat", "foot_far", (79, 88))
knees = [j["knee_far"][1] for _, _, j in frames("bulgarian_split_squat")]
if max(knees) > ANKLE or max(knees) < 98:
    fail("bulgarian_split_squat", f"the back knee must lower toward but not through the floor (lowest {max(knees):.1f})")

# 10 sliding leg curl: shoulders down, heels on the floor, hips lifted, legs nearly straight when extended. Since
#    round 5 the hips stay off the floor without arching: shoulders, hips and knees in one line in the curl, and the
#    hips never more than 8 degrees past it.
pinned("sliding_leg_curl", "neck", (70, 100))
on_level("sliding_leg_curl", "foot_near", 1, ANKLE)
on_level("sliding_leg_curl", "foot_far", 1, ANKLE)
for i, t, j in frames("sliding_leg_curl"):
    arch = signed(angle(j["hip"], j["knee_near"]) - angle(j["neck"], j["hip"]))
    if j["hip"][1] > 98:
        fail("sliding_leg_curl", f"the hips drop at sample {i}")
        break
    if arch < -8:
        fail("sliding_leg_curl", f"the hips arch past the shoulder-knee line ({arch:.1f} deg) at sample {i}")
        break
out = at("sliding_leg_curl", 1.0)
if dist(out["hip"], out["foot_near"]) < 30.5:
    fail("sliding_leg_curl", "the legs do not extend nearly straight")

# 11 nordic negative: knees and anchored ankles fixed, one body line from the knees, hands catch the floor.
pinned("nordic_negative", "knee_near", (124, 102))
pinned("nordic_negative", "foot_near", (139, 102))
for i, t, j in frames("nordic_negative"):
    bend = abs(signed(angle(j["knee_near"], j["hip"]) - angle(j["hip"], j["neck"])))
    if bend > 1:
        fail("nordic_negative", f"the hips break the body line ({bend:.1f} deg) at sample {i}")
        break
    if max(j["hand_near"][1], j["hand_far"][1]) > HAND_FLOOR + TOL:
        fail("nordic_negative", f"a hand passes through the floor at sample {i}")
        break
pinned("nordic_negative", "hand_near", (80, HAND_FLOOR), window=(0.78, 0.88))

# 12 single-leg RDL: stance foot planted, free foot off the floor, torso and free leg level together.
pinned("single_leg_rdl", "foot_far", (96, ANKLE))
for i, t, j in frames("single_leg_rdl"):
    if max(j["foot_near"][1], j["toe_near"][1]) > ANKLE - 2:
        fail("single_leg_rdl", f"the free foot touches down at sample {i}")
        break
hinge = at("single_leg_rdl", 1.0)
if abs(hinge["neck"][1] - hinge["hip"][1]) > 4 or abs(signed(angle(hinge["hip"], hinge["neck"]) - angle(hinge["foot_near"], hinge["hip"]))) > 10:
    fail("single_leg_rdl", "the torso and free leg do not reach a level line together")

# 13 prone Y-T-W: the body stays on the floor, the arms lift just off it.
pinned("prone_ytw", "hip", (104, 101))
on_level("prone_ytw", "neck", 1, 101)
on_level("prone_ytw", "toe_near", 1, FLOOR)
for t in (0.0, 0.33, 0.66):
    hold = at("prone_ytw", t)
    if max(hold["hand_near"][1], hold["elbow_near"][1]) > 101:
        fail("prone_ytw", f"the arms are not lifted off the floor at t={t}")

# 14 squat jump: feet planted outside the flight phase, clearly above the floor at the peak, brief flight.
GROUND = [(0.0, 0.32), (0.52, 1.0)]
for window in GROUND:
    pinned("half_squat_jump", "foot_near", (100, ANKLE), window=window)
    on_level("half_squat_jump", "foot_far", 1, ANKLE, window=window)
peak = at("half_squat_jump", 0.42)
if peak["foot_near"][1] > ANKLE - 8 or max(peak["toe_near"][1], peak["toe_far"][1]) > ANKLE - 6:
    fail("half_squat_jump", "the feet do not clearly leave the floor at the peak")
airborne = sum(1 for _, _, j in frames("half_squat_jump") if j["foot_near"][1] < ANKLE - 1)
if airborne > SAMPLES * 0.25:
    fail("half_squat_jump", f"the flight phase is too long ({airborne} of {SAMPLES} samples)")


# ---- group_e.py ----
def above_floor(name):
    """No limb sinks into the floor between keyframes (feet and elbows at ankle height, hands and toes lower)."""
    limits = {"hand_near": HAND_FLOOR, "hand_far": HAND_FLOOR, "toe_near": FLOOR, "toe_far": FLOOR}
    for i, t, j in frames(name):
        for joint, (_, y) in j.items():
            if joint in ("hands", "feet"):
                continue
            limit = limits.get(joint, ANKLE)
            if y > limit + TOL:
                fail(name, f"{joint} sinks into the floor at sample {i} (t={t:.3f}): y={y:.2f} > {limit}")
                return


def straight_legs(name, minimum=30.8, window=(0.0, 1.0), sides=("near", "far")):
    for i, t, j in frames(name):
        for side in sides:
            if window[0] <= t <= window[1] and dist(j["hip"], j[f"foot_{side}"]) < minimum:
                fail(name, f"leg_{side} bends ({dist(j['hip'], j[f'foot_{side}']):.2f} < {minimum}) at sample {i} (t={t:.3f})")
                return


def extension(j, facing=1):
    """Shoulder extension in degrees: 0 with the arms along the sides, positive as they go behind the back."""
    return ((angle(j["shoulder"], j["hand_near"]) - angle(j["hip"], j["neck"])) * facing) % 360 - 180


for name in ("straight_side_plank", "hollow_body_hold", "german_hang", "handstand_exit", "straddle_flag", "full_flag", "nordic_curl"):
    above_floor(name)

# 15 straight-leg side plank: forearm and feet planted, straight legs, one line from head to heels.
SSP_ANKLE = (74 + math.sqrt(51 ** 2 - 13.5 ** 2), ANKLE)
pinned("straight_side_plank", "elbow_near", (74, ANKLE))
pinned("straight_side_plank", "hand_near", (86.5, ANKLE))
pinned("straight_side_plank", "foot_near", SSP_ANKLE)
straight_legs("straight_side_plank")
hold = at("straight_side_plank", 1.0)
if abs(signed(angle(hold["foot_near"], hold["hip"]) - angle(hold["hip"], hold["neck"]))) > 1:
    fail("straight_side_plank", "the hold is not one line from head to heels")
if abs(hold["shoulder"][0] - hold["elbow_near"][0]) > 0.1:
    fail("straight_side_plank", "the elbow is not stacked under the shoulder in the hold")
if not at("straight_side_plank", 0.0)["hip"][1] > hold["hip"][1] + 1.5:
    fail("straight_side_plank", "the hips do not lift into the line")

# 16 straight hollow body: lower back down, straight legs low, shoulders up, arms by the ears.
pinned("hollow_body_hold", "hip", (106, 100))
straight_legs("hollow_body_hold")
straight("hollow_body_hold", "near")
for t in (0.0, 1.0):
    hold = at("hollow_body_hold", t)
    lift = -signed(angle(hold["hip"], hold["foot_near"]) - 90)
    if not 8 <= lift <= 25:
        fail("hollow_body_hold", f"the straight legs are not held low at t={t} ({lift:.1f} deg)")
    if not hold["neck"][1] < hold["hip"][1] - 4:
        fail("hollow_body_hold", f"the shoulders are not lifted at t={t}")
    if not (hold["hand_near"][0] < hold["head"][0] - 4 and hold["hand_near"][1] < hold["shoulder"][1]):
        fail("hollow_body_hold", f"the arms are not reaching past the head at t={t}")

# 17 feet-supported German hang: hands on the bar with straight arms, feet planted in front at the
# start and behind in the hang, the hips pass over the shoulders, shoulders open in the hold.
GH_HAND, GH_SIT, GH_BACK = (100, 46), (124, ANKLE), (60, 101)
pinned("german_hang", "hand_near", GH_HAND)
pinned("german_hang", "hand_far", GH_HAND)
straight("german_hang", "near")
straight("german_hang", "far")
for window in ((0.0, 0.06), (0.94, 1.0)):
    pinned("german_hang", "foot_near", GH_SIT, window=window)
    pinned("german_hang", "foot_far", GH_SIT, window=window)
pinned("german_hang", "foot_near", GH_BACK, window=(0.36, 0.64))
pinned("german_hang", "foot_far", GH_BACK, window=(0.36, 0.64))
on_level("german_hang", "toe_near", 1, FLOOR, window=(0.36, 0.64))
if min(j["hip"][1] - j["shoulder"][1] for _, _, j in frames("german_hang")) > -15:
    fail("german_hang", "the hips never rotate over the shoulders (no skin-the-cat)")
if extension(at("german_hang", 0.5)) < 100:
    fail("german_hang", f"the hands are not behind the body in the hang ({extension(at('german_hang', 0.5)):.0f} deg)")

# 18 handstand cartwheel exit: support hand planted and straight until the first foot lands, toes on
# the wall at the start, the far hand off the floor, one foot at a time, lunge finish, nothing in the wall.
HS_HAND, HS_STEP, HS_BACK, WALL = (145, HAND_FLOOR), (114, ANKLE), (86, 101.5), 150.0
for window in ((0.0, 0.34), (0.7295, 1.0)):
    pinned("handstand_exit", "hand_near", HS_HAND, window=window)
    straight("handstand_exit", "near", window=window)
on_level("handstand_exit", "toe_near", 0, WALL, window=(0.0, 0.08))
pinned("handstand_exit", "foot_near", HS_STEP, window=(0.34, 0.7295))
pinned("handstand_exit", "foot_far", HS_BACK, window=(0.47, 0.62))
for i, t, j in frames("handstand_exit"):
    if any(x > WALL + 0.05 for _, (x, _) in j.items()) or j["head"][0] + figure.head_radius > WALL:
        fail("handstand_exit", f"a limb or the head passes through the wall at sample {i}")
        break
    if 0.2 <= t <= 0.34 and j["hand_far"][1] > HAND_FLOOR - 4:
        fail("handstand_exit", f"the weight is not on one hand (far hand down) at sample {i}")
        break
    if abs(t - 0.34) < 0.004 and j["foot_far"][1] > ANKLE - 15:
        fail("handstand_exit", "both feet come down together")
        break
finish = at("handstand_exit", 0.55)
if not (abs(signed(angle(finish["hip"], finish["neck"]))) < 5 and finish["hand_near"][1] < finish["head"][1]):
    fail("handstand_exit", "the exit does not finish upright with the arms overhead")

# 19-20 human flags: split grip with both arms straight, trunk level; wide straddle, then one straight line.
FLAG_DY = math.sqrt(26 ** 2 - 18 ** 2)
for name in ("straddle_flag", "full_flag"):
    pinned(name, "hand_near", (62, 60 + FLAG_DY))
    pinned(name, "hand_far", (62, 60 - FLAG_DY))
    straight(name, "near")
    straight(name, "far")
    on_level(name, "hip", 1, 60)
straddle = at("straddle_flag", 1.0)
if abs(signed(angle(straddle["hip"], straddle["foot_near"]) - angle(straddle["hip"], straddle["foot_far"]))) < 60:
    fail("straddle_flag", "the legs are not in a wide straddle")
straight_legs("straddle_flag", window=(0.55, 1.0))
full = at("full_flag", 1.0)
for side in ("near", "far"):
    if abs(signed(angle(full["hip"], full[f"foot_{side}"]) - angle(full["neck"], full["hip"]))) > 2:
        fail("full_flag", f"leg_{side} is not in one line with the trunk")
straight_legs("full_flag")

# 21 full Nordic curl: knees and anchored ankles fixed, one line from the knees through the whole
# range, the chest close to the floor at the bottom, the hands never catch, back up to kneeling.
pinned("nordic_curl", "knee_near", (124, 102))
pinned("nordic_curl", "foot_near", (139, 102))
for i, t, j in frames("nordic_curl"):
    if abs(signed(angle(j["knee_near"], j["hip"]) - angle(j["hip"], j["neck"]))) > 1:
        fail("nordic_curl", f"the hips break the body line at sample {i}")
        break
    if max(j["hand_near"][1], j["hand_far"][1]) > HAND_FLOOR - 2:
        fail("nordic_curl", f"the hands catch the floor at sample {i}")
        break
if max(j["neck"][1] for _, _, j in frames("nordic_curl")) < 92.5:
    fail("nordic_curl", "the curl does not lower through the full range")
if abs(signed(angle(at("nordic_curl", 1.0)["hip"], at("nordic_curl", 1.0)["neck"]))) > 1:
    fail("nordic_curl", "the curl does not return to upright kneeling")

# ---- group_f.py (Level 7 "Mastery") ----
BAR = 12.0

# 22 typewriter pull-up (front view): both hands stay on the bar, each elbow below the bar on its own
# outer side, the chin clears the bar across the slide, and the straight arm swaps sides.
TW_LEFT, TW_RIGHT = (86, BAR), (114, BAR)
pinned("typewriter_pullup", "hand_near", TW_LEFT)
pinned("typewriter_pullup", "hand_far", TW_RIGHT)
for i, t, j in frames("typewriter_pullup"):
    sh = j["shoulder"]
    outer = [((j[f"hand_{s}"][0] - sh[0]) * (j[f"elbow_{s}"][1] - sh[1]) - (j[f"hand_{s}"][1] - sh[1]) * (j[f"elbow_{s}"][0] - sh[0])) * sign
             for s, sign in (("near", -1), ("far", 1))]
    if min(j["elbow_near"][1], j["elbow_far"][1]) < BAR + 2:
        fail("typewriter_pullup", f"an elbow rises over the bar at sample {i}")
        break
    if min(outer) < -TOL:
        fail("typewriter_pullup", f"an elbow points inward at sample {i}")
        break
for t in (0.4, 0.49, 0.58):
    if at("typewriter_pullup", t)["head"][1] > BAR:
        fail("typewriter_pullup", f"the chin drops below the bar during the slide (t={t})")
left_top, right_top = at("typewriter_pullup", 0.36), at("typewriter_pullup", 0.62)
if not (left_top["shoulder"][0] < 92 < 108 < right_top["shoulder"][0]):
    fail("typewriter_pullup", "the shoulders do not travel from one hand to the other at the top")
if dist(left_top["shoulder"], left_top["hand_far"]) < 25.5 or dist(right_top["shoulder"], right_top["hand_near"]) < 25.5:
    fail("typewriter_pullup", "the long arm is not straight at either end of the slide")

# 23 towel-assisted one-arm pull-up: the bar hand and the towel hand stay put, the working arm is
# straight in the hang, the chin clears the bar at the top and the towel hand ends beside the shoulder.
pinned("assisted_one_arm_pullup", "hand_near", (100, BAR))
pinned("assisted_one_arm_pullup", "hand_far", (109, 26))
straight("assisted_one_arm_pullup", "near", 25.5, window=(0.0, 0.0))
oap_top = at("assisted_one_arm_pullup", 1.0)
if oap_top["head"][1] > BAR:
    fail("assisted_one_arm_pullup", "the chin does not clear the bar at the top")
if not oap_top["hand_far"][1] > oap_top["shoulder"][1]:
    fail("assisted_one_arm_pullup", "the towel hand is not pressing down beside the shoulder at the top")

# 24 one-arm pull-up negative: one hand on the bar, the free hand off it, a long steady lowering to a
# straight arm, and the feet clear of the box in the hang.
pinned("one_arm_pullup_negative", "hand_near", (100, BAR))
straight("one_arm_pullup_negative", "near", 25.5, window=(0.78, 0.88))
heights = []
for i, t, j in frames("one_arm_pullup_negative"):
    if j["hand_far"][1] < BAR + 3:
        fail("one_arm_pullup_negative", f"the free hand reaches the bar at sample {i}")
        break
    if max(j["foot_near"][1], j["toe_near"][1], j["foot_far"][1], j["toe_far"][1]) > 97 - TOL:
        fail("one_arm_pullup_negative", f"the feet touch the box at sample {i}")
        break
    if 0.12 <= t <= 0.78:
        heights.append(j["shoulder"][1])
if any(b < a - 1e-6 for a, b in zip(heights, heights[1:])) or heights[-1] - heights[0] < 15:
    fail("one_arm_pullup_negative", "the lowering is not one steady descent to the hang")
if at("one_arm_pullup_negative", 0.0)["head"][1] > BAR:
    fail("one_arm_pullup_negative", "the negative does not start with the chin over the bar")

# 25 deficit wall handstand push-up: hands planted on the blocks, toes on the wall, straight arms at the
# top, the head lowering below hand level beside the blocks onto the mat, nothing through the wall.
DEF_HAND, DEF_MAT, DEF_BLOCK = (137, 96), 104.0, (134.0, 97.0)
pinned("deficit_wall_hspu", "hand_near", DEF_HAND)
pinned("deficit_wall_hspu", "hand_far", DEF_HAND)
on_level("deficit_wall_hspu", "toe_near", 0, 150)
straight("deficit_wall_hspu", "near", window=(0.0, 0.0))
general("deficit_wall_hspu", head_floor=DEF_MAT)
deepest = max((j["head"][1] for _, _, j in frames("deficit_wall_hspu")))
if deepest < DEF_HAND[1] + 2 or deepest + figure.head_radius < DEF_MAT - 1.5:
    fail("deficit_wall_hspu", "the head does not lower below the hands to the mat")
for i, t, j in frames("deficit_wall_hspu"):
    if any(x > 150.05 for k, (x, _) in j.items() if k != "toe_near"):
        fail("deficit_wall_hspu", f"a limb passes through the wall at sample {i}")
        break
    if j["head"][1] + figure.head_radius > DEF_BLOCK[1] and j["head"][0] + figure.head_radius > DEF_BLOCK[0]:
        fail("deficit_wall_hspu", f"the head enters the blocks at sample {i}")
        break

# 26 freestanding handstand push-up negative: hands planted, stacked over them at the top with straight
# arms, hips kept over the hands, the head lowering to the mat, and nothing touching the backstop wall.
FS_HAND, FS_MAT, FS_WALL = (110, HAND_FLOOR), 102.5, 140.0
pinned("freestanding_hspu_negative", "hand_near", FS_HAND)
pinned("freestanding_hspu_negative", "hand_far", FS_HAND)
straight("freestanding_hspu_negative", "near", window=(0.0, 0.0))
general("freestanding_hspu_negative", head_floor=FS_MAT)
if abs(at("freestanding_hspu_negative", 0.0)["hip"][0] - FS_HAND[0]) > 0.5:
    fail("freestanding_hspu_negative", "the start is not a stacked handstand over the hands")
lowest = max((j["head"][1] for _, _, j in frames("freestanding_hspu_negative")))
if lowest + figure.head_radius < FS_MAT - 1.5:
    fail("freestanding_hspu_negative", "the head does not lower to the mat")
for i, t, j in frames("freestanding_hspu_negative"):
    if abs(j["hip"][0] - FS_HAND[0]) > 4:
        fail("freestanding_hspu_negative", f"the hips leave the hands at sample {i}")
        break
    if max(x for k, (x, _) in j.items()) > FS_WALL - 2:
        fail("freestanding_hspu_negative", f"the body reaches the backstop wall at sample {i}")
        break
    if max(j["foot_near"][1], j["foot_far"][1]) > 60:
        fail("freestanding_hspu_negative", f"the feet come down during the negative at sample {i}")
        break
straight_legs("freestanding_hspu_negative")

# ---- flag_hang_skills.py (round 11) ----
for name in ("vertical_flag", "flag_negative", "skin_the_cat", "supinated_german_hang"):
    above_floor(name)

# 27-28 vertical flag and flag negative: the flags' split grip and shoulder with both arms locked and the legs straight.
# The vertical flag holds one line straight up over the shoulders, legs together; the negative lowers that line away
# from the pole, slowly and steadily, through the level flag line until the toes reach the floor. Nothing but the hands
# touches the pole.
for name in ("vertical_flag", "flag_negative"):
    pinned(name, "neck", (80, 60))
    pinned(name, "hand_near", (62, 60 + FLAG_DY))
    pinned(name, "hand_far", (62, 60 - FLAG_DY))
    straight(name, "near")
    straight(name, "far")
    straight_legs(name)
    for i, t, j in frames(name):
        if min(x for k, (x, _) in j.items() if k not in ("hand_near", "hand_far", "hands")) < 62 + 2 or \
                j["head"][0] - figure.head_radius < 62:
            fail(name, f"the body touches the pole at sample {i}")
            break
vertical = at("vertical_flag", 1.0)
if abs(vertical["hip"][0] - vertical["neck"][0]) > 0.1 or vertical["hip"][1] > vertical["neck"][1] - 19.9:
    fail("vertical_flag", "the trunk does not stand straight up over the shoulders in the hold")
for side in ("near", "far"):
    if abs(signed(angle(vertical["hip"], vertical[f"foot_{side}"]))) > 1:
        fail("vertical_flag", f"leg_{side} does not point straight up in the hold")
if abs(signed(angle(vertical["hip"], vertical["foot_near"]) - angle(vertical["hip"], vertical["foot_far"]))) > 2:
    fail("vertical_flag", "the legs are not together in the hold")
for i, t, j in frames("flag_negative"):
    for side in ("near", "far"):
        if abs(signed(angle(j["hip"], j[f"foot_{side}"]) - angle(j["neck"], j["hip"]))) > 1:
            fail("flag_negative", f"leg_{side} breaks the body line at sample {i}")
            break
drops = [j["foot_near"][1] for _, t, j in frames("flag_negative") if 0.08 <= t <= 0.78]
if any(b < a - 1e-6 for a, b in zip(drops, drops[1:])):
    fail("flag_negative", "the lowering is not one steady descent")
if abs(signed(angle(at("flag_negative", 0.0)["hip"], at("flag_negative", 0.0)["neck"]) - 180)) > 0.5:
    fail("flag_negative", "the negative does not start from the vertical flag")
if not any(abs(j["hip"][1] - j["neck"][1]) < 1 and 0.3 <= t <= 0.6 for _, t, j in frames("flag_negative")):
    fail("flag_negative", "the lowering does not pass the level flag line in its middle")
for t in (0.0, 0.5):
    if at("flag_negative", t)["toe_near"][1] > ANKLE - 5:
        fail("flag_negative", f"the feet are down at t={t}")
landed = at("flag_negative", 0.9)
if landed["toe_near"][1] < FLOOR - 0.5:
    fail("flag_negative", "the toes do not reach the floor at the end")

# 29 skin-the-cat on the high bar: both hands stay on the bar with straight arms, the feet never touch the floor, the
# hips roll over the shoulders, and the hang behind opens the shoulders with straight legs; a still hang at either end.
STC_HAND = (100, BAR)
pinned("skin_the_cat", "hand_near", STC_HAND)
pinned("skin_the_cat", "hand_far", STC_HAND)
straight("skin_the_cat", "near")
straight("skin_the_cat", "far")
for i, t, j in frames("skin_the_cat"):
    if max(j["foot_near"][1], j["toe_near"][1], j["foot_far"][1], j["toe_far"][1]) > ANKLE - 5:
        fail("skin_the_cat", f"the feet come down to the floor at sample {i}")
        break
if min(j["hip"][1] - j["shoulder"][1] for _, _, j in frames("skin_the_cat")) > -15:
    fail("skin_the_cat", "the hips never roll over the shoulders")
if extension(at("skin_the_cat", 0.5)) < 100:
    fail("skin_the_cat", f"the hands are not behind the body in the German hang ({extension(at('skin_the_cat', 0.5)):.0f} deg)")
straight_legs("skin_the_cat", window=(0.44, 0.56))
for t in (0.0, 1.0):
    hang = at("skin_the_cat", t)
    if abs(hang["hip"][0] - hang["shoulder"][0]) > 0.1 or dist(hang["hip"], hang["foot_near"]) < 30.8:
        fail("skin_the_cat", f"no still, straight hang at t={t}")

# 30 supinated German hang: the Hefesto's bar and start position, hands on the bar, straight arms, the shoulders open
# behind the body and the feet clear of the floor.
pinned("supinated_german_hang", "hand_near", (95, 40))
pinned("supinated_german_hang", "hand_far", (95, 40))
straight("supinated_german_hang", "near")
straight("supinated_german_hang", "far")
for i, t, j in frames("supinated_german_hang"):
    if max(j["foot_near"][1], j["toe_near"][1], j["foot_far"][1], j["toe_far"][1]) > ANKLE - 3:
        fail("supinated_german_hang", f"the feet come down to the floor at sample {i}")
        break
held, start = at("supinated_german_hang", 1.0), at("hefesto", 0.0)
if extension(held) < 100:
    fail("supinated_german_hang", f"the shoulders are not open in the hold ({extension(held):.0f} deg)")
if max(dist(held[k], start[k]) for k in held if k not in ("hands", "feet")) > 0.5:
    fail("supinated_german_hang", "the hold is not the Hefesto's start position")

if failures:
    print(f"Progression motion checks failed with {len(failures)} error(s):")
    print("\n".join(f" - {f}" for f in failures))
    sys.exit(1)
print(f"Progression motion checks passed: {len(EXPECTED)} movements, {len(set(EXPECTED.values()))} templates x {SAMPLES} samples "
      "(pinned contacts, straight-arm supports, joint bend direction, head clearance).")
