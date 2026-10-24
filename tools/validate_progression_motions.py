"""Sampled motion checks for the progression animations (tools/animation_authoring/progressions.py).

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
from preview_animations import Figure, Template  # noqa: E402

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
}
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
    facing = motion(name).keyframes[0][1].facing
    r = figure.head_radius
    for i, t, j in frames(name):
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
    general(name, check_elbows=name != "prone_ytw")  # Y-T-W arm shapes come from shoulder rotation

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
pinned("archer_pushup", "hand_near", (96, HAND_FLOOR))
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

# 10 sliding leg curl: shoulders down, heels on the floor, hips lifted, legs nearly straight when extended.
pinned("sliding_leg_curl", "neck", (70, 100))
on_level("sliding_leg_curl", "foot_near", 1, ANKLE)
on_level("sliding_leg_curl", "foot_far", 1, ANKLE)
for i, t, j in frames("sliding_leg_curl"):
    if j["hip"][1] > 95:
        fail("sliding_leg_curl", f"the hips drop at sample {i}")
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

if failures:
    print(f"Progression motion checks failed with {len(failures)} error(s):")
    print("\n".join(f" - {f}" for f in failures))
    sys.exit(1)
print(f"Progression motion checks passed: {len(EXPECTED)} movements, {len(set(EXPECTED.values()))} templates x {SAMPLES} samples "
      "(pinned contacts, straight-arm supports, joint bend direction, head clearance).")
