"""Round 11: writes all 1,000 classes of the Street Gymnastics program from one plan (curriculum/program.json).

The plan is docs/CURRICULUM_PLAN.md: named circuit blocks in Level 1, the skill week from Level 2 with the elbow-heavy
days between arm-light ones, lean blocks between today's frozen exams, the full shapes in Levels 8-9 and records in
Level 10. Round 11 adds the one-arm handstand, the shrimp squat and the Hefesto. Everything here is deterministic: the
same plan gives the same classes. Run it, then tools/assemble_curriculum.ps1 and the validators.

    python tools/build_curriculum.py            # writes curriculum/program.json and prints the belt landings
    python tools/build_curriculum.py --report X # also writes the belt and dose report as JSON to X

Day ids are frozen (DayIdsTest): a class is identified by its level and number, so this rewrites classes in place.
"""
import itertools
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIB = {m['id']: m for m in json.loads((ROOT / 'curriculum/movement_library.json').read_text(encoding='utf-8'))['movements']}
LIMITS = {p['level']: p for p in json.loads((ROOT / 'curriculum/class_progression.json').read_text(encoding='utf-8'))['levels']}

EXAMS = {1: [20, 40, 60, 81, 100], 2: [20, 40, 62, 84, 100], 3: [20, 40, 64, 84, 100]}
for _L in range(4, 11):
    EXAMS[_L] = [13, 34, 55, 76, 100]
# The Full Battle (5 rounds of the basics) is a challenge class on L10:090, the day-6 slot (the owner, 2.6.8).
FULL_BATTLE = (10, 90)
BELT_SETS = [10, 15, 20, 25, 30]  # each belt five sets more (the owner, review 11)
REP_CAP = {'pushup': 50, 'squat': 40, 'parallel_dip': 30, 'pullup': 21, 'hanging_leg_raise': 15, 'toes_to_bar': 15}

# ----- skills: the 40 belted skills, their targets and their dose path -----
# unit: 'r' reps or 's' seconds; side: per side; start: (level, class) from which the line runs; d0: first dose;
# ends: the working dose at the end of each level (Normal toggle). Between levels the dose rises block by block.
SKILLS = {}


def skill(sid, targets, unit, start, d0, ends, side=False):
    SKILLS[sid] = dict(t=targets, unit=unit, start=start, d0=d0, ends=ends, side=side)


def flat(v, frm):
    return {L: v for L in range(frm, 11)}


skill('pushup', [6, 12, 20, 30, 40], 'r', (1, 1), 6, {1: 15, 2: 20, 3: 22, 4: 25, 5: 30, 6: 32, 7: 35, 8: 40, 9: 45, 10: 50})
skill('squat', [15, 20, 25, 30, 40], 'r', (1, 1), 15, {1: 20, 2: 25, 3: 30, 4: 35, **flat(40, 5)})
skill('hollow_body_hold', [20, 30, 40, 50, 60], 's', (1, 1), 20, {1: 30, 2: 45, 3: 50, **flat(60, 4)})
skill('dead_hang', [30, 45, 60, 75, 90], 's', (1, 1), 30, {1: 45, 2: 60, 3: 75, **flat(90, 4)})
skill('pullup', [3, 6, 10, 15, 20], 'r', (1, 61), 1, None)
skill('parallel_dip', [4, 10, 15, 20, 25], 'r', (1, 61), 3, {1: 6, 2: 10, 3: 12, 4: 14, 5: 16, 6: 18, 7: 20, 8: 23, 9: 26, 10: 28})
skill('pike_pushup', [6, 8, 10, 12, 15], 'r', (1, 41), 5, {1: 8, 2: 10, 3: 12, **flat(15, 4)})
skill('wall_handstand', [20, 30, 45, 60, 75], 's', (1, 21), 20, {1: 30, 2: 45, 3: 60, 4: 60, **flat(75, 5)})
skill('hanging_leg_raise', [5, 8, 10, 12, 15], 'r', (2, 22), 5, {2: 8, 3: 10, 4: 12, **flat(15, 5)})
skill('tuck_front_hold', [5, 8, 12, 16, 20], 's', (2, 21), 5, {2: 8, 3: 14, **flat(20, 4)})
skill('pseudo_planche_pushup', [5, 8, 10, 12, 15], 'r', (3, 2), 5, {3: 7, 4: 9, 5: 10, 6: 12, 7: 13, **flat(15, 8)})
skill('tuck_back_hold', [5, 8, 12, 16, 20], 's', (3, 11), 5, {**flat(20, 3)})
skill('lsit', [5, 10, 15, 20, 30], 's', (3, 23), 5, {3: 8, 4: 12, 5: 15, 6: 18, 7: 20, **flat(30, 8)})
skill('tuck_planche', [5, 8, 10, 12, 15], 's', (4, 2), 5, {4: 8, 5: 10, 6: 13, **flat(15, 7)})
skill('pistol_squat', [2, 4, 6, 8, 10], 'r', (4, 3), 1, {4: 3, 5: 5, 6: 6, 7: 8, 8: 9, 9: 10, 10: 10}, side=True)
skill('freestanding_handstand', [5, 15, 30, 45, 60], 's', (4, 5), 5, {4: 10, 5: 25, 6: 35, 7: 45, **flat(60, 8)})
skill('advanced_tuck_front', [5, 8, 10, 12, 15], 's', (4, 8), 5, {4: 6, 5: 10, **flat(15, 6)})
skill('toes_to_bar', [3, 6, 8, 12, 15], 'r', (4, 15), 2, {4: 3, 5: 5, 6: 7, 7: 9, 8: 12, 9: 15, 10: 15})
skill('strict_muscleup', [1, 2, 4, 6, 7], 'r', (4, 39), 1, {4: 1, 5: 2, 6: 3, 7: 4, 8: 5, 9: 6, 10: 7})
skill('advanced_tuck_back', [5, 8, 10, 15, 20], 's', (4, 60), 5, {4: 10, **flat(20, 5)})
skill('wall_hspu', [2, 4, 6, 8, 10], 'r', (4, 61), 1, {4: 2, 5: 4, 6: 5, 7: 7, 8: 8, 9: 10, 10: 10})
skill('tuck_flag', [5, 8, 10, 12, 15], 's', (5, 5), 5, {5: 8, 6: 15, **flat(15, 7)}, side=True)
skill('archer_pullup', [2, 3, 4, 6, 8], 'r', (5, 8), 1, {5: 2, 6: 3, 7: 4, 8: 6, 9: 8, 10: 8}, side=True)
skill('straddle_back', [3, 5, 8, 10, 12], 's', (5, 81), 3, {5: 4, 6: 7, 7: 10, **flat(12, 8)})
skill('advanced_tuck_planche', [5, 6, 8, 10, 12], 's', (6, 2), 3, {6: 6, 7: 9, **flat(12, 8)})
skill('dragon_flag', [1, 3, 5, 6, 8], 'r', (6, 17), 1, {6: 2, 7: 4, 8: 6, 9: 8, 10: 8})
skill('straddle_flag', [3, 4, 6, 8, 10], 's', (6, 81), 3, {6: 3, 7: 5, 8: 6, 9: 8, 10: 10}, side=True)
skill('one_arm_pushup', [1, 3, 4, 4, 5], 'r', (7, 2), 1, {7: 2, 8: 4, 9: 6, 10: 10}, side=True)
skill('full_back', [3, 5, 8, 12, 14], 's', (6, 46), 3, {6: 4, 7: 8, 8: 10, 9: 12, 10: 15})
skill('straddle_front', [3, 5, 7, 10, 12], 's', (7, 15), 3, {7: 5, 8: 8, 9: 10, 10: 12})
skill('v_sit', [3, 4, 5, 6, 7], 's', (8, 9), 3, {8: 4, 9: 6, 10: 8})
skill('deficit_wall_hspu', [2, 3, 5, 6, 7], 'r', (8, 5), 1, {8: 3, 9: 5, 10: 8})
skill('full_front', [3, 4, 5, 7, 10], 's', (8, 15), 3, {8: 4, 9: 6, 10: 10})
skill('straddle_planche', [3, 4, 5, 6, 8], 's', (8, 2), 3, {8: 5, 9: 6, 10: 8})
skill('full_flag', [3, 4, 5, 5, 6], 's', (7, 61), 3, {7: 3, 8: 4, 9: 5, 10: 8}, side=True)
skill('one_arm_pullup', [1, 1, 1, 1, 2], 'r', (9, 15), 1, {9: 1, 10: 2}, side=True)
skill('full_planche', [3, 3, 4, 5, 6], 's', (9, 58), 3, {9: 4, 10: 6})
skill('one_arm_handstand', [3, 4, 5, 6, 7], 's', (9, 68), 3, {9: 3, 10: 7}, side=True)
skill('shrimp_squat', [2, 3, 5, 7, 8], 'r', (7, 58), 2, {7: 3, 8: 5, 9: 7, 10: 9}, side=True)
skill('hefesto', [1, 1, 2, 2, 3], 'r', (9, 39), 1, {9: 2, 10: 3})
assert len(SKILLS) == 40, len(SKILLS)

# Review 11 (the owner): the classes take every belt to Master, and each ladder's top rung about 80% of the way to Elite
# (24 of its 30 sets); a repeated block or the athlete's own training does the rest. The top rung's working dose stops a
# step under its Elite target until the class named here, then holds it. The basics (push-up, squat, pull-up, dip, hang,
# hollow body), the core ladder (the owner: leg raise, toes-to-bar, L-sit and V-sit, the V-sit's Elite dose early enough to
# finish) and the easier rungs, whose Elite comes through the harder rung's sets, finish inside the program.
ELITE_FROM = {
    'one_arm_pullup': (9, 83), 'strict_muscleup': (10, 28), 'full_front': (10, 38), 'full_back': (10, 15),
    'one_arm_pushup': (8, 69), 'deficit_wall_hspu': (10, 44), 'full_planche': (10, 56), 'one_arm_handstand': (10, 58),
    'pistol_squat': (10, 9), 'v_sit': (9, 54), 'dragon_flag': (10, 32), 'full_flag': (9, 68),
    'shrimp_squat': (10, 15), 'hefesto': (9, 88),
}
ELITE_SHARE = (21, 27)  # the top rungs' Elite sets inside the program: 70-90% of 30
ELITE_FULL = {'v_sit'}  # a top rung whose Elite finishes inside the program

# Progression movements (no belt) that the classes also dose: start (level, class), first dose and level ends.
PROG = {}


def prog(mid, unit, start, d0, ends, side=False):
    PROG[mid] = dict(unit=unit, start=start, d0=d0, ends=ends, side=side)


prog('incline_pushup', 'r', (1, 1), 6, {1: 10})
prog('inverted_row', 'r', (1, 1), 8, {1: 12, **flat(12, 2)})
prog('eccentric_pullup', 'r', (1, 22), 2, {1: 3, 2: 4})
prog('assisted_pullup', 'r', (1, 41), 5, {1: 6})
prog('support_hold', 's', (1, 1), 20, {1: 30, 2: 30})
prog('assisted_dip', 'r', (1, 22), 5, {1: 8})
prog('eccentric_dip', 'r', (1, 41), 3, {1: 4})
prog('glute_bridge', 'r', (1, 1), 12, {1: 15, **flat(15, 2)})
prog('single_leg_bridge', 'r', (2, 1), 8, {2: 10, **flat(12, 3)}, side=True)
prog('single_leg_rdl', 'r', (3, 1), 6, {3: 8, 4: 10, 5: 10, **flat(12, 6)}, side=True)
prog('split_squat', 'r', (1, 1), 6, {1: 10, 2: 10, 3: 10}, side=True)
prog('reverse_lunge', 'r', (1, 21), 8, {1: 10}, side=True)
prog('bulgarian_split_squat', 'r', (2, 1), 8, {2: 10, **flat(10, 3)}, side=True)
prog('assisted_pistol_squat', 'r', (2, 66), 3, {2: 4, 3: 6}, side=True)
prog('side_plank', 's', (1, 1), 20, {1: 30, 2: 45}, side=True)
prog('straight_side_plank', 's', (3, 1), 20, {3: 30, 4: 40, **flat(45, 5)}, side=True)
prog('knee_raise', 'r', (1, 1), 6, {1: 8, 2: 10})
prog('plank', 's', (1, 1), 30, {1: 45, 2: 45})
prog('pike_handstand_hold', 's', (1, 1), 20, {1: 30, 2: 30})
prog('arch_hold', 's', (1, 1), 15, {1: 20, **flat(30, 2)})
prog('tuck_lsit', 's', (1, 61), 10, {1: 10, 2: 15})
prog('one_leg_lsit', 's', (2, 81), 5, {2: 6, 3: 8}, side=True)
prog('tuck_front_row', 'r', (2, 1), 5, {2: 8})
# Review 9 (the 5- and 20-year coaches): the safe way down first, from L1:023, before any kick-up.
prog('handstand_exit', 'r', (1, 22), 2, {1: 2, 2: 3}, side=True)  # both directions, as its text says
prog('handstand_toe_pull', 's', (2, 50), 15, {2: 20, 3: 20})
prog('handstand_balance', 's', (3, 26), 8, {3: 12, 4: 15})
prog('planche_lean', 's', (2, 50), 10, {2: 15, 3: 20, **flat(25, 4)})
prog('frog_stand', 's', (1, 41), 10, {1: 15, 2: 20, **flat(30, 3)})
prog('elevated_plank', 's', (1, 1), 20, {1: 30, 2: 30, 3: 30})
prog('skin_cat_partial', 'r', (2, 60), 2, {2: 3, **flat(3, 3)})
# The full skin-the-cat (batch 2): through the German hang and back, the handstand-press day's shoulder extension.
prog('skin_the_cat', 'r', (3, 61), 2, {3: 3, 4: 4, **flat(5, 5)})
prog('german_hang', 's', (2, 21), 10, {2: 20, 3: 20, 4: 25, **flat(30, 5)})
prog('straight_bar_support', 's', (2, 1), 20, {2: 30, 3: 30})
prog('straight_bar_dip', 'r', (3, 25), 3, {3: 5, 4: 8, 5: 10, 6: 12, **flat(15, 7)})
prog('low_bar_transition', 'r', (2, 40), 3, {2: 5, 3: 5})
prog('chest_to_bar', 'r', (3, 46), 2, {3: 3, 4: 5, 5: 6})
prog('band_muscleup', 'r', (3, 46), 2, {3: 3, 4: 3})
prog('negative_muscleup', 'r', (3, 67), 2, {3: 3, 4: 3})
prog('candlestick', 'r', (2, 1), 5, {2: 8, 3: 8})
prog('bent_dragon_negative', 'r', (3, 46), 2, {3: 4, 4: 4})
prog('one_leg_dragon_negative', 'r', (4, 59), 2, {4: 3, 5: 3}, side=True)
prog('full_dragon_negative', 'r', (5, 38), 2, {5: 3, 6: 4})
prog('wall_hspu_negative', 'r', (3, 47), 2, {3: 3, 4: 4})
prog('elevated_pike_pushup', 'r', (3, 12), 5, {3: 8, 4: 10, **flat(12, 5)})
prog('flag_ground_support', 's', (3, 5), 10, {3: 15}, side=True)
prog('assisted_tuck_flag', 's', (3, 47), 5, {3: 8, 4: 10}, side=True)
# Batch 2: the vertical flag, hips over the shoulders on the pole, between the assisted tuck and the tuck flag; the flag
# negative, lowered from the vertical through the line, in the blocks that lead into the full flag.
prog('vertical_flag', 's', (4, 21), 5, {4: 10, 5: 10}, side=True)
prog('flag_negative', 'r', (7, 14), 1, {7: 2, 8: 2}, side=True)
prog('nordic_negative', 'r', (4, 17), 3, {4: 4, **flat(5, 5)})
prog('nordic_curl', 'r', (7, 17), 1, {7: 1, 8: 2, 9: 3, 10: 4})
prog('sliding_leg_curl', 'r', (3, 1), 6, {3: 8, **flat(10, 4)})
prog('feet_elevated_row', 'r', (1, 82), 8, {1: 8, 2: 10, 3: 10, **flat(12, 4)})
prog('archer_pushup', 'r', (4, 37), 3, {4: 4, 5: 6, 6: 8}, side=True)
prog('assisted_one_arm_pullup', 'r', (7, 36), 1, {7: 2, 8: 3}, side=True)
prog('one_arm_pullup_negative', 'r', (8, 36), 1, {8: 2, 9: 3}, side=True)
prog('freestanding_hspu_negative', 'r', (10, 26), 1, {10: 3})
# Review 7 (the owner): the full freestanding handstand push-up after five weeks of its negatives.
prog('freestanding_hspu', 'r', (10, 60), 1, {10: 2})
# The owner's "go fix" (the 10-year coach's review 9): dynamic lever and planche strength.
prog('tuck_front_lever_row', 'r', (5, 1), 3, {5: 4, 6: 5, 7: 6})
prog('tuck_planche_pushup', 'r', (7, 1), 2, {7: 3, 8: 3, 9: 3})
prog('pancake_compression', 'r', (3, 1), 8, {3: 10, 4: 12, **flat(12, 5)})
prog('seated_pike_lift', 'r', (2, 1), 6, {2: 10, 3: 12, **flat(15, 4)})
prog('calf_raise', 'r', (1, 1), 12, {1: 15, **flat(20, 2)})
prog('dead_bug', 'r', (1, 1), 6, {1: 10, **flat(10, 2)}, side=True)
prog('one_leg_front', 's', (5, 36), 5, {5: 5, 6: 8, **flat(10, 7)}, side=True)
# The one-arm handstand line (round 11): two-arm straddle, weight shift, fingertips, straddle one-arm, one-arm.
prog('straddle_handstand', 's', (6, 19), 10, {6: 20, **flat(30, 7)})
prog('handstand_weight_shift', 's', (7, 40), 3, {7: 5, **flat(10, 8)}, side=True)
prog('fingertip_one_arm_handstand', 's', (8, 40), 3, {8: 5, **flat(10, 9)}, side=True)
prog('straddle_one_arm_handstand', 's', (9, 5), 3, {9: 5, 10: 8}, side=True)
# The shrimp line: assisted partial, assisted, floating, assisted rear-foot held, then the shrimp squat.
prog('assisted_partial_shrimp', 'r', (3, 2), 3, {3: 6, 4: 8, **flat(8, 5)}, side=True)
prog('assisted_shrimp', 'r', (5, 16), 3, {5: 5, **flat(8, 6)}, side=True)
prog('floating_shrimp', 'r', (6, 37), 3, {6: 4, 7: 6, **flat(8, 8)}, side=True)
prog('assisted_held_shrimp', 'r', (7, 2), 3, {7: 5, **flat(6, 8)}, side=True)
# The Hefesto line: supinated German hang (feet assisted, then free), Korean dip, partial and assisted Hefesto, the Hefesto.
prog('assisted_supinated_german_hang', 's', (6, 39), 10, {6: 15, 7: 20, 8: 25, **flat(30, 9)})
# Batch 2: the free supinated German hang is the Korean dip's back-off (`hefesto_below`), not a rung of its own.
prog('supinated_german_hang', 's', (7, 22), 10, {7: 20, **flat(25, 8)})
prog('assisted_korean_dip', 'r', (7, 22), 3, {7: 5, **flat(8, 8)})
prog('assisted_partial_hefesto', 'r', (8, 1), 2, {8: 4, **flat(4, 9)})
prog('assisted_hefesto', 'r', (8, 61), 2, {8: 3, **flat(5, 9)})
DOSED = {**SKILLS, **PROG}
for _mid in DOSED:
    assert _mid in LIB, _mid

# The ladders: rungs in order. A class trains the hardest rung it has reached as its top sets and keeps back-off sets
# on the rung below, so the lower rungs keep earning.
LADDERS = {
    'front': ['tuck_front_hold', 'advanced_tuck_front', 'one_leg_front', 'straddle_front', 'full_front'],
    'back': ['tuck_back_hold', 'advanced_tuck_back', 'straddle_back', 'full_back'],
    'planche': ['tuck_planche', 'advanced_tuck_planche', 'straddle_planche', 'full_planche'],
    'flag': ['tuck_flag', 'straddle_flag', 'full_flag'],
    'oah': ['straddle_handstand', 'handstand_weight_shift', 'fingertip_one_arm_handstand', 'straddle_one_arm_handstand', 'one_arm_handstand'],
    'shrimp': ['assisted_partial_shrimp', 'assisted_shrimp', 'floating_shrimp', 'assisted_held_shrimp', 'shrimp_squat'],
    'hefesto': ['assisted_supinated_german_hang', 'assisted_korean_dip', 'assisted_partial_hefesto', 'assisted_hefesto', 'hefesto'],
}
STATIC_LADDERS = ('front', 'back', 'planche', 'flag')

# The app's belt ladders (SkillBook.DEFAULT_FAMILIES), easiest rung first. A level held on a harder rung is held on
# every easier rung of its ladder too (round 10): a full back lever at Elite makes the tuck back lever Elite.
BELT_LADDERS = [
    ['dead_hang', 'pullup', 'archer_pullup', 'one_arm_pullup'], ['strict_muscleup'],
    ['tuck_front_hold', 'advanced_tuck_front', 'straddle_front', 'full_front'],
    ['tuck_back_hold', 'advanced_tuck_back', 'straddle_back', 'full_back'], ['parallel_dip'],
    ['pushup', 'one_arm_pushup'], ['pike_pushup', 'wall_hspu', 'deficit_wall_hspu'],
    ['pseudo_planche_pushup', 'tuck_planche', 'advanced_tuck_planche', 'straddle_planche', 'full_planche'],
    ['wall_handstand', 'freestanding_handstand', 'one_arm_handstand'], ['squat', 'pistol_squat'], ['hanging_leg_raise', 'toes_to_bar'],
    ['lsit', 'v_sit'], ['hollow_body_hold', 'dragon_flag'], ['tuck_flag', 'straddle_flag', 'full_flag'], ['shrimp_squat'], ['hefesto'],
]
assert sorted(r for rungs in BELT_LADDERS for r in rungs) == sorted(SKILLS)

# Elbow-load sets (the S&C review's list): straight-arm pushing and back-lever-type pulling, the flag, the muscle-up,
# the Hefesto line, one-arm and archer pulling, free shoulder extension and the hard push-up variants. The front lever
# is straight-arm pulling, counted on its own.
ELBOW = (set(LADDERS['planche']) | set(LADDERS['back']) | set(LADDERS['flag']) | set(LADDERS['hefesto']) |
         {'strict_muscleup', 'band_muscleup', 'negative_muscleup', 'archer_pullup', 'assisted_one_arm_pullup', 'one_arm_pullup_negative',
          'one_arm_pullup', 'german_hang', 'skin_cat_partial', 'skin_the_cat', 'flag_negative', 'supinated_german_hang', 'pseudo_planche_pushup',
          'one_arm_pushup', 'straight_bar_dip', 'jump_muscleup', 'tuck_planche_pushup'})
ELBOW_CAP = 6

# Minimum holds: no set is a touch. 3 s for the full and straddle shapes, the V-sit, the advanced-tuck planche and the
# one-arm balance line; 5 s for every other hold, the one-leg front lever too (the S&C review).
MIN3 = {'full_front', 'full_back', 'full_planche', 'full_flag', 'straddle_front', 'straddle_back', 'straddle_planche', 'straddle_flag',
        'v_sit', 'advanced_tuck_planche', 'handstand_weight_shift', 'fingertip_one_arm_handstand',
        'straddle_one_arm_handstand', 'one_arm_handstand'}

# Levels 1-2 use round numbers (the teacher's palette, with the 8, 12, 25, 40 and 50 s belt targets, so the hang and the
# wall handstand climb every block); later, reps over 20 and holds over 20 s go in steps of 5.
R_PAL = [1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 35, 40]
S_PAL = [5, 8, 10, 12, 15, 20, 25, 30, 40, 45, 50, 60, 75, 90, 120]


def pos(L, n):
    return (L - 1) * 100 + n


def block_of(L, n):
    for b, e in enumerate(EXAMS[L], 1):
        if n <= e:
            return b
    raise ValueError((L, n))


def started(mid, L, n):
    sL, sn = DOSED[mid]['start']
    return pos(L, n) >= pos(sL, sn)


def unit_of(mid):
    return DOSED[mid]['unit'] if mid in DOSED else 's' if mid in SECONDS else 'r'


def snap(v, pal):
    return min(pal, key=lambda x: (abs(x - v), x))


def tidy(mid, v, L):
    """Round a dose the way a coach writes it, and never under the minimum hold."""
    u = unit_of(mid)
    if L <= 2:
        v = snap(v, R_PAL if u == 'r' else S_PAL)
    elif v > 20:
        v = 5 * int(v / 5 + 0.5)
    if u == 's':
        v = max(v, 3 if mid in MIN3 else 5)
    return max(1, v)


def step_up(mid, d, L):
    """Week B: one rep (+2 over 12, +5 over 20), or one second to 10 s, two to 30 s, five above; Levels 1-2 move one
    palette step."""
    if L <= 2:
        pal = R_PAL if unit_of(mid) == 'r' else S_PAL
        bigger = [x for x in pal if x > d]
        return bigger[0] if bigger else d + 1
    if unit_of(mid) == 'r':
        return d + (1 if d <= 12 else 2 if d <= 20 else 5)
    return d + (1 if d <= 10 else 2 if d <= 30 else 5)


def base_dose(mid, L, n):
    s = DOSED[mid]
    b = block_of(L, n)
    if mid == 'pullup':
        if L == 1:
            return 1 if b <= 4 else 2
        return max(1, 2 * L - 1 + (b - 1) // 2)
    ends = s['ends']
    sL, sn = s['start']
    if not any(k <= L for k in ends):
        return None
    last = max(k for k in ends if k <= L)
    if L not in ends:
        return ends[last]
    if L == sL:
        sb = block_of(sL, sn)
        return ends[L] if sb == 5 else s['d0'] + (ends[L] - s['d0']) * (b - sb) / (5 - sb)
    prev = ends.get(L - 1, s['d0'])
    return prev + (ends[L] - prev) * (b - 1) / 4


def dose(mid, L, n, step=0):
    """The working dose of a movement at class (L, n); week B (`step`) steps it up, never past the next block's dose."""
    d = base_dose(mid, L, n)
    if d is None:
        return None
    d = tidy(mid, int(round(d)), L)
    if step:
        nxt = next_block_dose(mid, L, n)
        d = min(step_up(mid, d, L), max(d, nxt if nxt is not None else 10 ** 6))
    cap = REP_CAP.get(mid, 10 ** 6)
    if mid in SKILLS and SKILLS[mid]['unit'] == 's':
        cap = SKILLS[mid]['t'][4]  # a working hold stops at its Elite target (the statics review)
    if mid in ELITE_FROM:
        # A top rung: a step under its Elite target until ELITE_FROM, then at it (review 11).
        elite = SKILLS[mid]['t'][4]
        if pos(L, n) >= pos(*ELITE_FROM[mid]):
            cap, d = min(cap, elite), max(d, elite)
        else:
            cap = min(cap, elite - 1)
    return tidy(mid, max(1, min(d, cap)), L)


def next_block_dose(mid, L, n):
    b = block_of(L, n)
    if b < 5:
        return dose(mid, L, EXAMS[L][b - 1] + 1)
    if L < 10:
        return dose(mid, L + 1, 1)
    return None


def rung(ladder, L, n):
    """(top, below): the hardest rung of a ladder reached by class (L, n), and the rung below it."""
    rungs = [r for r in LADDERS[ladder] if started(r, L, n)]
    if not rungs:
        return None, None
    return rungs[-1], (hefesto_below(rungs[-2], L, n) if len(rungs) > 1 else None)


def hefesto_below(mid, L, n):
    """Beside the Korean dip the supinated German hang is held free once it starts (batch 2)."""
    return 'supinated_german_hang' if mid == 'assisted_supinated_german_hang' and started('supinated_german_hang', L, n) else mid


# ----- step building -----
REHEARSAL = {'skill_foundation', 'skill', 'skill_strength', 'advanced_skill', 'advanced_strength', 'mastery_skill'}
rehearsed = set()


def regression_of(mid):
    """The nearest easier movement in the library chain that the sequence has already practised."""
    r = LIB[mid].get('regression_id')
    while r:
        if LIB[r]['category'] not in REHEARSAL or r in rehearsed:
            return r
        r = LIB[r].get('regression_id')
    return None


SECONDS = {'easy_walk', 'deep_squat_hold', 'wall_shoulder_flexion', 'hip_flexor_stretch', 'child_pose', 'supine_twist', 'slow_breathing',
           'quad_stretch', 'hamstring_stretch', 'calf_stretch', 'active_hang', 'bear_crawl', 'one_arm_handstand_hover',
           'supported_handstand_shift'}
PER_SIDE = {'ankle_rocks', 'bird_dog', 'cossack_squat', 'dead_bug', 'leg_swings', 'reverse_lunge', 'single_leg_bridge', 'split_squat',
            'bulgarian_split_squat', 'single_leg_rdl', 'archer_pullup', 'archer_pushup', 'typewriter_pullup', 'assisted_one_arm_pullup',
            'one_arm_pullup_negative', 'thoracic_rotation', 'side_plank', 'straight_side_plank', 'one_leg_dragon_negative', 'shoulder_cars',
            'hip_flexor_stretch', 'calf_stretch', 'wall_kickup', 'one_arm_handstand_hover', 'supported_handstand_shift'}
# Review 7 (the owner: "repeat easy stuff but interesting"): new easy movements at fixed doses, one each in Level 1's
# middle blocks and in the Level 3 stretch that had nothing new, each named on the class it arrives.
FIXED = {'wall_kickup': 3, 'bear_crawl': 20, 'hollow_arch_swing': 6, 'parallel_bar_walk': 6, 'cossack_squat': 4, 'jump_muscleup': 2,
         'pullover_lowbar': 3, 'squat_jump': 5,
         # Review 8 (stale periods): paused basics and the skill lines' missing drills, each a swap within its own line.
         'pause_pushup': 8, 'pause_squat': 10, 'chinup': 2, 'one_arm_handstand_hover': 20, 'supported_handstand_shift': 8}


def amount(mid, value):
    side = DOSED[mid]['side'] if mid in DOSED else mid in PER_SIDE
    if isinstance(value, str):
        return value
    return f'{value}/side' if side else value


def st(mid, sets, value=None, rest=90, tempo=None, frac=1.0, ctx=None, step=0):
    """One step tuple. value None = the movement's working dose at ctx (scaled by frac, then tidied)."""
    if value is None and mid not in DOSED and mid in FIXED:
        value = FIXED[mid]
    if value is None:
        L, n = ctx
        d = dose(mid, L, n, step)
        assert d is not None, (mid, L, n)
        value = d if frac == 1.0 else tidy(mid, max(1, int(math.floor(d * frac + 0.5))), L)
    unit = unit_of(mid) if mid in DOSED else ('s' if mid in SECONDS else 'r')
    if tempo is None:
        tempo = 'isometric' if unit == 's' else '2111'
    v = amount(mid, value)
    reps, secs = (None, v) if unit == 's' else (v, None)
    return [mid, sets, reps, secs, rest, tempo, None]


def finish(steps, rehearse=True):
    """Fill each step's easier option from the library chain and record what the sequence has practised (warm-up and
    main count as practice of a movement; the validator does not count the practice part)."""
    out = []
    for s in steps:
        s = list(s)
        if s[6] is None and LIB[s[0]].get('regression_id'):
            s[6] = regression_of(s[0])
        out.append(s)
        if rehearse:
            rehearsed.add(s[0])
    return out


# ----- warm-ups and cool-downs: one per kind of day (the teacher's templates); a ramp-up set closes the warm-up -----
WARM = {
    'push': [['march', 1, None, 60, 0, 'brisk', None], ['arm_swings', 1, 10, None, 10, 'controlled', 'shoulder_cars'],
             ['wrist_circles', 1, 10, None, 10, 'controlled', None], ['wrist_rocks', 1, 10, None, 10, 'controlled', None],
             ['wrist_pushup', 1, 10, None, 20, 'slow', 'wrist_rocks'], ['scapular_pushup', 2, 8, None, 25, '2111', None]],
    'pull': [['march', 1, None, 60, 0, 'brisk', None], ['shoulder_cars', 1, '5/side', None, 10, 'controlled', None],
             ['scapular_pull', 2, 6, None, 30, '2111', None], ['active_hang', 1, None, 15, 30, 'controlled', None],
             ['prone_ytw', 1, 6, None, 20, 'slow', 'wall_slide'], ['arch_hold', 1, None, 15, 20, 'isometric', None]],
    'legs': [['march', 1, None, 60, 0, 'brisk', None], ['hip_circles', 1, 8, None, 10, 'controlled', None],
             ['leg_swings', 1, '8/side', None, 10, 'controlled', 'hip_circles'], ['ankle_rocks', 1, '10/side', None, 10, 'controlled', None],
             ['glute_bridge', 1, 10, None, 20, '2111', None], ['deep_squat_hold', 1, None, 30, 20, 'easy', None]],
    'full': [['march', 1, None, 60, 0, 'brisk', None], ['arm_swings', 1, 10, None, 10, 'controlled', 'shoulder_cars'],
             ['hip_circles', 1, 8, None, 10, 'controlled', None], ['cat_cow', 1, 6, None, 10, 'slow', None],
             ['scapular_pull', 1, 6, None, 20, '2111', None], ['scapular_pushup', 1, 8, None, 20, '2111', None]],
    'easy': [['march', 1, None, 90, 0, 'easy', None], ['cat_cow', 1, 6, None, 10, 'slow', None],
             ['hip_circles', 1, 8, None, 10, 'controlled', None]],
    # An exam's warm-up (the S&C review 6): general, wrists, the shoulder blades and an active hang, then the ramp sets.
    'exam': [['march', 1, None, 60, 0, 'brisk', None], ['arm_swings', 1, 10, None, 10, 'controlled', 'shoulder_cars'],
             ['hip_circles', 1, 8, None, 10, 'controlled', None], ['wrist_circles', 1, 10, None, 10, 'controlled', None],
             ['wrist_rocks', 1, 10, None, 10, 'controlled', None], ['wrist_pushup', 1, 10, None, 20, 'slow', 'wrist_rocks'],
             ['scapular_pull', 1, 6, None, 20, '2111', None], ['scapular_pushup', 1, 8, None, 20, '2111', None],
             ['active_hang', 1, None, 15, 30, 'controlled', None]],
}
# Wrist prep (review 6, the teacher and the S&C coach): any class that puts weight on the hands in a handstand, planche,
# press or one-arm push-up warms the wrists first, and the march shortens to make room. The L-sit on the bars is a
# neutral grip.
WRISTS = [['wrist_circles', 1, 10, None, 10, 'controlled', None], ['wrist_rocks', 1, 10, None, 10, 'controlled', None],
          ['wrist_pushup', 1, 10, None, 20, 'slow', 'wrist_rocks']]
HANDS = ({'pike_handstand_hold', 'wall_handstand', 'handstand_balance', 'freestanding_handstand', 'handstand_exit', 'handstand_toe_pull',
          'frog_stand', 'planche_lean', 'one_arm_handstand_hover', 'supported_handstand_shift', 'pseudo_planche_pushup', 'tuck_planche_pushup', 'pike_pushup', 'elevated_pike_pushup', 'wall_hspu_negative', 'wall_hspu',
          'deficit_wall_hspu', 'freestanding_hspu_negative', 'freestanding_hspu', 'one_arm_pushup', 'wall_kickup', 'parallel_bar_walk', 'bear_crawl'}
         | set(LADDERS['planche']) | set(LADDERS['oah']))


def with_wrists(warm, steps, main=()):
    """[warm] with the wrist drills when [steps] load the hands; the wrist push-ups only when the main part does, not
    just a short handstand practice."""
    if 'wrist_circles' in [w[0] for w in warm] or not any(s[0] in HANDS for s in steps):
        return warm
    warm = [list(w) for w in warm]
    if warm[0][0] == 'march':
        warm[0][3] = 30
    at = next((i + 1 for i, w in enumerate(warm) if w[0] in ('arm_swings', 'shoulder_cars', 'hip_circles')), 1)
    drills = WRISTS if any(s[0] in HANDS for s in main) else WRISTS[:2]
    return warm[:at] + [list(w) for w in drills] + warm[at:]


COOL = {
    'pull': [['lat_stretch', 2, None, 30, 10, 'easy', None], ['forearm_stretch', 1, None, 30, 10, 'easy', None], ['child_pose', 1, None, 45, 0, 'easy', None]],
    'push': [['chest_stretch', 1, None, 30, 10, 'easy', None], ['forearm_stretch', 2, None, 30, 10, 'easy', None], ['child_pose', 1, None, 45, 0, 'easy', None]],
    'legs': [['quad_stretch', 1, None, 30, 10, 'easy', None], ['hamstring_stretch', 1, None, 30, 10, 'easy', None],
             ['ninety_ninety_hip_stretch', 1, None, 30, 10, 'easy', None], ['calf_stretch', 1, None, '30/side', 0, 'easy', None]],
    'full': [['lat_stretch', 1, None, 30, 10, 'easy', None], ['chest_stretch', 1, None, 30, 10, 'easy', None],
             ['hip_flexor_stretch', 1, None, '30/side', 10, 'easy', None]],
    'easy': [['supine_twist', 2, None, 30, 10, 'easy', None], ['hip_flexor_stretch', 1, None, '30/side', 10, 'easy', None],
             ['slow_breathing', 1, None, 120, 0, 'easy', None]],
    'exam': [['lat_stretch', 1, None, 30, 10, 'easy', None], ['chest_stretch', 1, None, 30, 10, 'easy', None],
             ['forearm_stretch', 1, None, 30, 10, 'easy', None], ['hip_flexor_stretch', 1, None, '30/side', 0, 'easy', None]],
}
# The straddle and full shapes ramp two rungs down (the S&C review 6): one rung down is still a working set.
HARD_SHAPES = {'straddle_front', 'full_front', 'straddle_back', 'full_back', 'straddle_planche', 'full_planche', 'straddle_flag', 'full_flag',
               'one_arm_handstand'}
HAND_LINE = {'pike_handstand_hold', 'wall_handstand', 'handstand_balance', 'freestanding_handstand'} | set(LADDERS['oah'])


def ramp_of(M, L, n, exam_day=False, P=(), record=False):
    """The ramp-up set (the teacher's review 6): the day's lead at about half of today's set. A rung under two weeks old
    ramps on the rung below; a straddle or full shape two rungs down. No ramp when the practice part already opens the
    same line. On an exam day the first test leads. Warm-up, so it earns nothing."""
    skip = ('slow',) if exam_day else ('assessment', 'slow')
    lead = next((s for s in M if s[0] in DOSED and s[5] not in skip), None)
    if lead is None:
        return None
    if P and P[0][0] in HAND_LINE and lead[0] in HAND_LINE:
        return None
    mid, v = lead[0], value_of(lead)
    lad = next((rs for rs in LADDERS.values() if mid in rs), None)
    down = 2 if mid in HARD_SHAPES else 1 if pos(L, n) - pos(*DOSED[mid]['start']) < 14 else 0
    if record:
        down = max(down, 1)  # a record's ramp is the rung below (review 9: the assisted Hefesto before the Hefesto record)
    if lad and down and lad.index(mid) >= down:
        mid = lad[lad.index(mid) - down]
    elif down and LIB[mid].get('regression_id') in DOSED and started(LIB[mid]['regression_id'], L, n):
        mid = LIB[mid]['regression_id']
    if mid != lead[0]:
        v = dose(mid, L, n)
        if v is None:
            return None
    v = tidy(mid, max(1, int(v * 0.5 + 0.5)), min(L, 2))
    if mid == lead[0]:
        v = min(v, max(1, value_of(lead) // 2))
    if P and mid == P[0][0]:
        return None
    return st(mid, 1, value=v, rest=45, tempo='ramp-up')


# ----- the rotation: seven class positions, never weekdays (the athlete trains at their own pace) -----
def rotation_day(n):
    return (n - 1) % 7 + 1


def kind_of(L, n):
    """What class (L, n) is: exam, re-entry, carry, build week A/B, hold, trim, deload, light, easy, primer, recovery."""
    ex = EXAMS[L]
    if n in ex:
        return 'exam'
    if rotation_day(n) == 7:
        return 'recovery'
    b = block_of(L, n)
    e = ex[b - 1]
    if L >= 4:
        # The exam falls on class 6: classes 4 and 5 stay the press and bar days, light, and the exam's warm-up is its
        # primer (the S&C review). Every week keeps all its days.
        if b < 5 and n == e - 1:
            return 'barlight'
        if b < 5 and n == e - 2:
            return 'presslight'
        if b == 1:
            return 'reentry' if n == 1 else 'carry' if n <= 6 else 'build_a'
        s = ex[b - 2] + 1  # the recovery class after the last exam
        off = n - s
        if b == 5:
            third = {92: 'deload', 93: 'deload', 94: 'deload', 95: 'hold', 96: 'light', 97: 'easy', 99: 'primer'}
            if n in third:
                return third[n]
        elif off >= 15:
            if b == 3:
                return 'deload' if off <= 17 else 'hold'
            return 'trim' if off in (15, 17) else 'hold'
        return 'build_a' if off <= 7 else 'build_b'
    # Levels 1-3: the exam replaces its class, the class before is a primer; a level starts with a re-entry and ends
    # with an easy class and a primer.
    if n == 1 and L > 1:
        return 'reentry'
    if n == e - 1:
        return 'primer'
    if rotation_day(e) == 7 and n == e - 2:
        return 'easy'
    if b > 1 and rotation_day(ex[b - 2]) == 7 and n == ex[b - 2] + 1:
        return 'reentry'
    if n == 98:
        return 'easy'
    if L == 3 and b > 1 and n == ex[b - 2] + 1:
        return 'reentry'
    s = ex[b - 2] + 1 if b > 1 else 1
    return 'build_a' if n - s < 8 else 'build_b'


def role_of(L, n):
    d = rotation_day(n)
    if L == 1:
        return {1: 'circuit_a', 2: 'practice_hand', 3: 'circuit_b', 4: 'practice_bar', 5: 'core_balance', 6: 'circuit_c', 7: 'recovery'}[d]
    if d == 6 and L >= 8:
        return 'battle' if L == 8 or kind_of(L, n) != 'build_a' else 'statics_chain'
    return {1: 'front_onearm' if L >= 8 else 'front_pull', 2: 'legs_core', 3: 'planche_push', 4: 'handstand_press', 5: 'back_flag',
            6: 'basics', 7: 'recovery'}[d]


BLOCK_NAMES = {
    1: ['First Positions', 'Wall & Negatives', 'First Pull', 'Strong Basics', 'Foundations Finale'],
    2: ['Bar Basics', 'Pull-Up Strength', 'First Lever', 'Lean & Press', 'Bar Strength Finale'],
}
# The skill each named exam of Levels 1-2 is about: missing it offers the block repeat.
EXAM_HEADLINE = {(1, 20): 'pushup', (1, 40): 'wall_handstand', (1, 60): 'eccentric_pullup', (1, 81): 'pullup', (1, 100): 'pullup',
                 (2, 20): 'pullup', (2, 40): 'pullup', (2, 62): 'tuck_front_hold', (2, 84): 'planche_lean', (2, 100): 'pullup'}
EXAM_NAMES = {
    (1, 20): 'The Basics', (1, 40): 'Wall & Negatives', (1, 60): 'First Pull', (1, 81): 'First Pull-Up', (1, 100): 'Level 1 Test: Foundations',
    (2, 20): 'Bar Basics', (2, 40): 'Pull-Up Strength', (2, 62): 'First Lever', (2, 84): 'Lean & Press', (2, 100): 'Level 2 Test: Bar Strength',
}
LEVEL_NAMES = ['Foundations', 'Bar Strength', 'First Skills', 'Skill Control', 'Balance & Levers', 'Confident Calisthenics', 'Full Shapes',
               'Strength Endurance', 'Superhuman', 'Beyond Gravity']


def block_name(L, n):
    b = block_of(L, n)
    return BLOCK_NAMES[L][b - 1] if L in BLOCK_NAMES else f'{LEVEL_NAMES[L - 1]}, block {b}'


# Recovery classes rotate four themes, each after an easy walk.
RECOVERY_THEMES = [
    ('Hips & Ankles', [('ankle_rocks', 1, '10/side'), ('ninety_ninety_hip_switch', 1, 8), ('deep_squat_hold', 2, 30), ('hip_flexor_stretch', 1, '30/side')]),
    ('Shoulders & Upper Back', [('wall_slide', 1, 10), ('thoracic_rotation', 1, '8/side'), ('wall_shoulder_flexion', 1, 30), ('child_pose', 1, 45)]),
    ('Spine & Breath', [('cat_cow', 1, 8), ('bird_dog', 1, '8/side'), ('dead_bug', 1, '8/side'), ('supine_twist', 2, 30), ('slow_breathing', 1, 120)]),
    ('Wrists & Legs', [('wrist_circles', 1, 10), ('wrist_rocks', 1, 10), ('calf_raise', 1, 15), ('glute_bridge', 1, 12), ('quad_stretch', 1, 30)]),
]


# ----- class builders: each returns (practice, main) as lists of (step, priority); 0 keeps its sets longest -----
def ladder(name, L, n, top_sets, step=0, frac=1.0, backoff=2, rest=150, fresh_below=None, prio=0):
    top, below = rung(name, L, n)
    if top is None:
        return []
    sL, sn = DOSED[top]['start']
    fresh = pos(L, n) - pos(sL, sn) < 14
    out = []
    if fresh and below:
        # A new rung enters with short sets beside the rung below.
        out.append((st(top, 2 if top_sets >= 3 else 1, ctx=(L, n), rest=rest, frac=frac), prio))
        out.append((st(below, fresh_below or min(3, top_sets), ctx=(L, n), rest=120, frac=frac), prio))
    else:
        out.append((st(top, top_sets, ctx=(L, n), rest=rest, step=step, frac=frac), prio))
        if below and backoff:
            out.append((st(below, backoff, ctx=(L, n), rest=120, frac=frac), 1))
    return out


def handstand_practice(L, n, sets, step=0):
    """The two-arm handstand line in the practice part."""
    c = (L, n)
    if started('freestanding_handstand', L, n):
        return [(st('freestanding_handstand', sets, ctx=c, rest=60, tempo='easy technique'), 0)]
    if started('handstand_balance', L, n):
        return [(st('handstand_balance', sets, ctx=c, rest=45, tempo='easy technique'), 0),
                (st('wall_handstand', 1, ctx=c, rest=60, tempo='easy technique'), 1)]
    if started('wall_handstand', L, n):
        return [(st('wall_handstand', sets, ctx=c, rest=60, tempo='easy technique', step=step), 0)]
    return [(st('pike_handstand_hold', sets, ctx=c, rest=45, tempo='easy technique', step=step), 0)]


def one_arm_attempts(L, n):
    """From the straddle handstand on, one set of the one-arm line's rung in the practice part of days 1 and 6: the
    one-arm handstand three times a week instead of once (the statics and S&C reviews)."""
    top = rung('oah', L, n)[0]
    if top is None:
        return []
    return [(st(top, 1, ctx=(L, n), rest=60, tempo='easy technique'), 1)]


def first_started(L, n, *mids):
    for mid in mids:
        if started(mid, L, n):
            return mid
    return mids[-1]


def pull_basic(L, n):
    return first_started(L, n, 'pullup', 'assisted_pullup', 'inverted_row') if L == 1 else first_started(L, n, 'pullup', 'eccentric_pullup', 'inverted_row')


def dip_basic(L, n):
    return first_started(L, n, 'parallel_dip', 'eccentric_dip', 'assisted_dip', 'support_hold')


def leg_basic(L, n):
    return 'pistol_squat' if L >= 5 else 'squat'


def hang_core(L, n):
    return first_started(L, n, 'toes_to_bar', 'hanging_leg_raise', 'knee_raise')


def dragon(L, n):
    return first_started(L, n, 'dragon_flag', 'full_dragon_negative', 'one_leg_dragon_negative', 'bent_dragon_negative', 'candlestick')


def circuit(stations, rounds, L, n, rest=45, round_rest=90, frac=1.0, step=0):
    """Stations as repeated one-set steps: A, B, C, then A, B, C again. Each round carries its own priority, 3, 4, ...:
    the fit drops a whole round, last first, never a station (the 10-year coach, review 9). The tempo 'circuit' marks
    them until the fit is done."""
    out = []
    for r in range(rounds):
        for i, mid in enumerate(stations):
            last = i == len(stations) - 1
            out.append((st(mid, 1, ctx=(L, n), rest=round_rest if last else rest, frac=frac, step=step, tempo='circuit'), 3 + r))
    return out


def bar_lines(L, n):
    """The lines the back-lever, flag and Hefesto day rotates, in their order."""
    lines = []
    if rung('back', L, n)[0] or started('skin_cat_partial', L, n):
        lines.append('back')
    if rung('flag', L, n)[0] or started('flag_ground_support', L, n):
        lines.append('flag')
    if rung('hefesto', L, n)[0]:
        lines.append('hefesto')
    return lines


# The prep rungs that can claim the bar day's lead. The vertical flag (batch 2) is not one: it enters on the flag's own
# sets, so the tuck back lever keeps its Level 4 lead.
BAR_PREP = {'back': ['skin_cat_partial'], 'flag': ['flag_ground_support', 'assisted_tuck_flag'], 'hefesto': []}


def bar_lead(L, n):
    """The line that leads the bar day this block: the one whose next rung comes soonest from the block's first class,
    so the rung below gets its volume before the jump and the new rung enters on its lead day (the statics review); with
    none due within two blocks, the lines take turns."""
    lines = bar_lines(L, n)
    if not lines:
        return None
    b = block_of(L, n)
    first = pos(L, EXAMS[L][b - 2] + 1 if b > 1 else 1)
    due = {}
    for line in lines:
        rungs = (BAR_PREP[line] + LADDERS[line])[1:]  # a line's first rung has nothing below it to build
        nxt = [pos(*DOSED[r]['start']) for r in rungs if pos(*DOSED[r]['start']) > first]
        if nxt and min(nxt) - first <= 48:
            due[line] = min(nxt) - first
    if due:
        return min(due, key=lambda k: (due[k], lines.index(k)))
    if 'flag' in lines:
        # The flag trains on two days only, so with nothing due the full flag leads the two blocks after it starts.
        fL, fn = DOSED['full_flag']['start']
        if 1 <= (L * 5 + b) - (fL * 5 + block_of(fL, fn)) <= 2:
            return 'flag'
    turns = [x for x in lines if x != 'hefesto'] or lines
    return turns[((L - 1) * 5 + b) % len(turns)]


def bent_hefesto(line, L, n):
    """The Hefesto line from the Korean dip on: bent-arm work, so it closes the bar day."""
    return line == 'hefesto' and started('assisted_korean_dip', L, n)


def line_steps(line, L, n, lead, step, three):
    """One line of the bar day. The lead runs 3 sets (and a back-off when two lines share the day); the others keep 2
    working sets of their rung, so no rung drops to one set before its jump. A new rung enters beside the rung below."""
    c = (L, n)
    if line in ('back', 'flag') and not rung(line, L, n)[0]:
        prep = 'skin_cat_partial' if line == 'back' else first_started(L, n, 'vertical_flag', 'assisted_tuck_flag', 'flag_ground_support')
        return [(st(prep, 3 if lead else 2, ctx=c, rest=90, tempo='slow' if line == 'back' else None), 0)]
    rest = 180 if line == 'hefesto' else 150
    # Before the full flag the negative, lowered from the vertical through the line, takes the back-off's place, or a
    # straddle set's, so the day's elbow count stays.
    negative = line == 'flag' and started('flag_negative', L, n) and not started('full_flag', L, n)
    if lead:
        if not negative:
            return ladder(line, L, n, 3, step, backoff=0 if three else 1, rest=rest, fresh_below=1 if three else 2)
        out = ladder(line, L, n, 2 if three else 3, step, backoff=0, rest=rest, fresh_below=1 if three else 2)
        return out + [(st('flag_negative', 1, ctx=c, rest=150, tempo='5010'), 0)]
    if negative:
        out = ladder(line, L, n, 2 if not three else 1, 0, backoff=0, rest=rest, fresh_below=1, prio=0)
        return out + [(st('flag_negative', 1, ctx=c, rest=150, tempo='5010'), 0)]
    # The flag keeps 3 sets beside the back lever alone: it never trains two days running, so the bar day carries its week.
    # On the full flag one of its sets is the straddle at its own dose, so the straddle still reaches its top.
    if line == 'flag' and rung('flag', L, n)[0] == LADDERS['flag'][-1] and settled('flag', L, n) == LADDERS['flag'][-1]:
        return [(st('full_flag', 1, ctx=c, rest=rest), 0), (st('straddle_flag', 1, ctx=c, rest=rest), 0)]
    return ladder(line, L, n, 3 if line == 'flag' and not three else 2, 0, backoff=0, rest=rest, fresh_below=1, prio=0)


def settled(line, L, n):
    """A line's top rung once it has had two weeks; before that the rung below (a new rung starts on its lead day)."""
    top, below = rung(line, L, n)
    if top and below and pos(L, n) - pos(*DOSED[top]['start']) < 14:
        return below
    return top


def second_exposures(L, n):
    """Day 6's lighter set of the front lever and the back lever (the statics review): each line trains twice a week. The
    flag never trains two days running, so it stays on the bar day. On the full shape the set is the straddle at its own
    dose, so the straddle keeps going to its top."""
    out = []
    for line in ('front', 'back'):
        r = settled(line, L, n)
        if r and (line == 'front' and L >= 3 or line != 'front'):
            rungs = LADDERS[line]
            if r == rungs[-1] and r == rung(line, L, n)[0]:
                out.append((st(rungs[-2], 1, ctx=(L, n), rest=120, frac=1.0 if L == 10 else 0.7), 1))
            else:
                out.append((st(r, 1, ctx=(L, n), rest=120, frac=0.7), 1))
    return out


def day6_press(L, n):
    """Day 6's two press sets, so overhead pushing comes near 6 sets a week (the S&C review)."""
    if L < 3:
        return []
    return [(st('elevated_pike_pushup' if started('elevated_pike_pushup', L, n) else 'pike_pushup', 2, ctx=(L, n), rest=120), 1)]


def bar_light(L, n):
    """The exam week's bar day: one set of each line at 70% of the block's dose, never a step up; the Hefesto line on
    the rung below its top, the day before an exam. Then the hanging core and the rows. The exam the next class tests
    these same lines, so it meets fresh elbows (the statics review: two sets at the exam dose came the class before)."""
    c = (L, n)
    lead = bar_lead(L, n)
    M = []
    for line in sorted(bar_lines(L, n), key=lambda k: (bent_hefesto(k, L, n), k != lead)):
        if line == 'flag':
            continue  # tomorrow's exam holds the flag and the planche: no flag the day before (the statics review)
        top = settled(line, L, n) or ('skin_cat_partial' if line == 'back' else first_started(L, n, 'vertical_flag', 'assisted_tuck_flag', 'flag_ground_support'))
        if top == 'hefesto':
            top = rung('hefesto', L, n)[1]  # tomorrow's exam tests the strict Hefesto
        # A family tomorrow's exam tests gets one set; the Hefesto line, untested before the strict Hefesto, keeps two
        # (the statics review 6), so its rung below still gets two sets a week before the next rung.
        tested_tomorrow = line != 'hefesto' or started('hefesto', L, n)
        M.append((st(top, 1 if tested_tomorrow else 2, ctx=c, rest=150, frac=0.7), 0))
    if L == 2 or not M:
        M.insert(0, (st('straight_bar_support', 2, ctx=c, rest=75), 0))
    # Tomorrow's exam tests the hanging core too: one set at 70% (the statics review 6).
    M += [(st(hang_core(L, n), 1, ctx=c, rest=75, frac=0.7), 1), (st('feet_elevated_row' if started('feet_elevated_row', L, n) else 'inverted_row', 2, ctx=c, rest=90), 1),
          (st('arch_hold', 1, ctx=c, rest=45), 1)]
    return [], M, 'pull', 'pull', 45, 5


def press_light(L, n):
    """The exam week's handstand and press day: the handstand practice, the press at 2 sets of the block's dose, one
    set of the one-arm line, then easy shoulder extension and compression. Elbow-light."""
    c = (L, n)
    P = handstand_practice(L, n, 2)
    M = []
    oah = rung('oah', L, n)[0]
    if oah:
        M.append((st(settled('oah', L, n) or oah, 1, ctx=c, rest=90), 0))
    # No slow negatives two days before an exam (the S&C review 6): the press at its last full-range rung.
    # Review 8: the press a full class trained at least a week ago, so a new press arrives on its own day, not here.
    press = next((m for m in ('deficit_wall_hspu', 'wall_hspu', 'elevated_pike_pushup') if pos(*DOSED[m]['start']) <= pos(L, n) - 7), 'pike_pushup')
    M.append((st(press, 2, ctx=c, rest=150), 0))
    M += [(st('german_hang', 1, ctx=c, rest=60, tempo='slow'), 1), (st('arch_hold', 1, ctx=c, rest=45), 1),
          (st('seated_pike_lift', 1, ctx=c, rest=60), 1), (st('hollow_body_hold', 1, ctx=c, rest=45), 1)]
    return P, M, 'push', 'push', 40, 5


def build_role(role, L, n, step):
    """A role's class at (L, n): practice, main, warm-up kind, cool-down kind, minutes cap, RPE."""
    c = (L, n)
    P, M = [], []
    if role == 'recovery':
        k = pos(L, n) // 7
        theme, items = RECOVERY_THEMES[k % 4]
        M = [(st('easy_walk', 1, value=480 if k % 2 else 600, rest=0, tempo='easy'), 0)]
        M += [(st(mid, sets, value=v, rest=20, tempo='slow'), 0) for mid, sets, v in items]
        return P, M, 'easy', 'easy', 30, 2
    # ----- Level 1: circuits and short practice classes -----
    if role in ('circuit_a', 'circuit_b', 'circuit_c'):
        # Whole rounds of the same stations (the teacher's review), then a short finisher.
        rounds = 2 if n <= 7 else 3
        row = 'feet_elevated_row' if started('feet_elevated_row', L, n) else 'inverted_row'
        if role == 'circuit_a':
            stations = [pull_basic(L, n), 'pushup', dip_basic(L, n), 'hollow_body_hold']
            finisher = [(st('dead_hang', 1, ctx=c, rest=60, step=step), 1)]
        elif role == 'circuit_b':
            # Block 3 swaps the lunge for the Cossack squat (review 7).
            lunge = 'cossack_squat' if 41 <= n < 61 else first_started(L, n, 'reverse_lunge', 'split_squat') if n < 61 else 'split_squat'
            stations = ['pause_squat' if n >= 82 else 'squat', lunge, 'glute_bridge']  # review 8: a pause at depth
            finisher = [(st('side_plank', 2, ctx=c, rest=45, step=step), 1), (st('knee_raise', 2, ctx=c, rest=60, step=step), 1)]
        else:
            # The engine keeps the rows all level: the main pulling volume on the way to the first pull-up.
            stations = ['pushup', 'squat', row, dip_basic(L, n)]
            # From block 3 the support walk along the bars closes the engine (review 7).
            finisher = [(st('parallel_bar_walk', 1, rest=60, tempo='controlled'), 1) if n >= 41 else (st('plank', 1, ctx=c, rest=45, step=step), 1)]
        M = circuit(stations, rounds, L, n, rest=45, round_rest=90, step=step) + finisher
        P = handstand_practice(L, n, 1, step)
        rpe = 5 if n <= 14 else 6
        return P, M, ('legs' if role == 'circuit_b' else 'full'), ('legs' if role == 'circuit_b' else 'full'), 45, rpe
    if role == 'practice_hand':
        # An easy class: the handstand line is its main work, so no separate practice part.
        M = handstand_practice(L, n, 3, step)
        # Review 9 (the 5- and 20-year coaches): the cartwheel exit from block 2, right after the wall holds; the kick-up
        # from L1:072, once the holds reach 30 s, always after an exit rehearsal.
        out = started('handstand_exit', L, n)
        kick = n >= 72
        if out:
            M.append((st('handstand_exit', 1 if kick else 2, ctx=c, rest=60, tempo='controlled'), 0))
        if kick:
            M.append((st('wall_kickup', 1, rest=60, tempo='controlled'), 0))
        if started('frog_stand', L, n):
            M += [(st('frog_stand', 2, ctx=c, rest=60, step=step), 0), (st('pike_pushup', 2, ctx=c, rest=90, step=step), 0),
                  (st('support_hold', 2, ctx=c, rest=60, step=step), 1)]
        else:
            M += [(st('support_hold', 2, ctx=c, rest=60, step=step), 0)]
            if not out:
                M.append((st('plank', 2, ctx=c, rest=45, step=step), 0))
            M.append((st('incline_pushup', 2, ctx=c, rest=60, step=step), 1))
        return P, M, 'push', 'push', 30, 4
    if role == 'practice_bar':
        M = [(st('dead_hang', 3, ctx=c, rest=60, step=step), 0)]
        if n >= 88:
            # The owner's "go fix" (the teacher's review 8): after the first pull-up, chin-ups for the negatives, the grip that
            # gives a novice an extra rep or two, lowered in 3 s. Same sets, no extra elbow load.
            M.append((st('chinup', 2, rest=90, tempo='3010'), 0))
        elif started('eccentric_pullup', L, n):
            M.append((st('eccentric_pullup', 2, ctx=c, rest=90, tempo='5010', step=step), 0))
        else:
            M.append((st('inverted_row', 2, ctx=c, rest=60, step=step), 0))
        # From block 3 the hollow-to-arch swing on the bar takes the floor arch's place (review 7).
        M += [(st('hollow_arch_swing', 2, rest=45, tempo='controlled'), 1) if n >= 46 else (st('arch_hold', 2, ctx=c, rest=45, step=step), 1),
              (st('hollow_body_hold', 1, ctx=c, rest=45), 1)]
        return P, M, 'pull', 'pull', 30, 4
    if role == 'core_balance':
        M = handstand_practice(L, n, 2, step) + [(st('hollow_body_hold', 3, ctx=c, rest=45, step=step), 0), (st('arch_hold', 2, ctx=c, rest=45, step=step), 0)]
        M.append((st('tuck_lsit', 3, ctx=c, rest=60), 0) if started('tuck_lsit', L, n) else (st('dead_bug', 2, ctx=c, rest=30, tempo='slow'), 1))
        # From block 2 the bear crawl takes the calf raise's place (review 7).
        M.append((st('bear_crawl', 2, rest=45, tempo='controlled'), 2) if n >= 26 else (st('calf_raise', 2, ctx=c, rest=45), 2))
        return P, M, 'full', 'easy', 30, 4
    # ----- Levels 2-10: the skill week -----
    if role in ('front_pull', 'front_onearm'):
        M = ladder('front', L, n, 4 if L >= 4 else 3, step) or [(st('tuck_front_row', 3, ctx=c, rest=90), 0)]
        flag = settled('flag', L, n)
        if started('strict_muscleup', L, n) and pos(L, n) < pos(4, 77):
            # Review 9 (the 10-year coach): the first strict blocks keep the negative beside the new rung.
            M.append((st('strict_muscleup', 2, ctx=c, rest=180, tempo='controlled', step=step), 0))
            M.append((st('negative_muscleup', 1, ctx=c, rest=150, tempo='5010'), 1))
        elif started('strict_muscleup', L, n):
            M.append((st('strict_muscleup', 3, ctx=c, rest=180, tempo='controlled', step=step), 0))
        elif started('band_muscleup', L, n):
            M.append((st('band_muscleup', 2, ctx=c, rest=120, tempo='controlled'), 0))
            M.append((st('negative_muscleup' if started('negative_muscleup', L, n) else 'chest_to_bar', 2, ctx=c, rest=120,
                         tempo='5010' if started('negative_muscleup', L, n) else '2111'), 1))
            if pos(L, n) >= pos(3, 78):
                # The jump-assisted muscle-up joins the line until the strict one (review 7: something new in L3's end).
                M.append((st('jump_muscleup', 1, rest=120, tempo='controlled'), 1))
        elif started('low_bar_transition', L, n):
            M.append((st('low_bar_transition', 2, ctx=c, rest=90, tempo='controlled'), 1))
        if flag:
            # The flag's second day: never beside the bar day or the planche day (the statics review). It follows the
            # whole front-lever ladder and the muscle-up, so the muscle-up comes fresh (review 6). A full flag whose 70%
            # floors to the 3 s minimum would be the full working hold: the straddle at 70% instead (the 10-year coach).
            fs = st(flag, 1, ctx=c, rest=120, frac=0.7)
            if flag == 'full_flag' and value_of(fs) <= 3:
                fs = st('straddle_flag', 1, ctx=c, rest=120, frac=0.7)
            M.append((fs, 0))
        if role == 'front_onearm':
            oa = first_started(L, n, 'one_arm_pullup', 'one_arm_pullup_negative', 'assisted_one_arm_pullup')
            M.append((st(oa, 2, ctx=c, rest=150, tempo='5010' if oa == 'one_arm_pullup_negative' else '3010'), 0))
            M.append((st('archer_pullup', 1, ctx=c, rest=120), 0))  # kept: with the flag set, the muscle-up gives a set
        elif started('assisted_one_arm_pullup', L, n):
            M.append((st('assisted_one_arm_pullup', 2, ctx=c, rest=150, tempo='3010'), 1))
            M.append((st('archer_pullup', 1, ctx=c, rest=120), 0))  # kept: with the flag set, the muscle-up gives a set
        elif started('archer_pullup', L, n):
            M.append((st('archer_pullup', 3, ctx=c, rest=120, step=step), 1))
        # From Level 8 the high-rep pull-ups move to day 6: day 1 keeps 2 sets beside the one-arm work (the S&C review).
        pulls = 2 if L >= 8 else 3 if L >= 3 else 4
        bridge = started('negative_muscleup', L, n) and not started('strict_muscleup', L, n)
        M.append((st(pull_basic(L, n), pulls - 1 if bridge else pulls, ctx=c, rest=150, step=step), 0))
        if bridge:
            # Chest-to-bar, the high pull the muscle-up needs, keeps the third pull-up set until the strict muscle-up.
            M.append((st('chest_to_bar', 1, ctx=c, rest=150), 0))
        M.append((st(hang_core(L, n), 2, ctx=c, rest=75), 1))
        if L == 2:
            M.append((st('inverted_row', 2, ctx=c, rest=90), 2))
        P = handstand_practice(L, n, 2) + one_arm_attempts(L, n)
        return P, M, 'pull', 'pull', 55, 7
    if role == 'legs_core':
        both = started('pistol_squat', L, n) and rung('shrimp', L, n)[0]
        shrimp_lead = both and block_of(L, n) % 2 == 0
        lb = 'pistol_squat' if started('pistol_squat', L, n) else 'squat'
        if lb == 'squat':
            # Two squat sets and three single-leg sets: strength, not conditioning (the teacher's review).
            M = [(st('squat', 2, ctx=c, rest=120, step=step), 0)]
            if started('assisted_pistol_squat', L, n):
                M.append((st('assisted_pistol_squat', 3, ctx=c, rest=90), 0))
            else:
                M.append((st('bulgarian_split_squat' if started('bulgarian_split_squat', L, n) else 'split_squat', 3, ctx=c, rest=90), 0))
        else:
            M = [(st('pistol_squat', 2 if shrimp_lead else (4 if L >= 5 else 3), ctx=c, rest=120, step=0 if shrimp_lead else step), 0)]  # the knee line not leading keeps 2 steady sets (the S&C review)
        if rung('shrimp', L, n)[0]:
            if shrimp_lead:
                M = ladder('shrimp', L, n, 4, step, backoff=1, rest=120) + M
            else:
                M.append((st(rung('shrimp', L, n)[0], 2, ctx=c, rest=120), 0))
        M.append((st('single_leg_rdl' if started('single_leg_rdl', L, n) else 'single_leg_bridge', 2, ctx=c, rest=75), 0))
        if started('sliding_leg_curl', L, n) and not started('nordic_negative', L, n):
            M.append((st('sliding_leg_curl', 2, ctx=c, rest=75), 1))
        # The Nordic at its protective dose, two sets a week (the S&C review).
        if started('nordic_curl', L, n):
            M.append((st('nordic_curl', 2, ctx=c, rest=150, tempo='5010'), 0))
        elif started('nordic_negative', L, n):
            M.append((st('nordic_negative', 2, ctx=c, rest=120, tempo='5010'), 0))
        if started('v_sit', L, n):
            M.append((st('v_sit', 3, ctx=c, rest=120, step=step), 0))
            if L <= 9:
                M.append((st('lsit', 1, ctx=c, rest=90), 2))
        elif started('lsit', L, n):
            M.append((st('lsit', 3, ctx=c, rest=90, step=step), 0))
        else:
            M.append((st(first_started(L, n, 'one_leg_lsit', 'tuck_lsit'), 2, ctx=c, rest=60), 1))
        df = dragon(L, n)
        M.append((st(df, 3, ctx=c, rest=120 if df != 'candlestick' else 60, tempo='4010' if 'negative' in df else '2111', step=step if df == 'dragon_flag' else 0), 0))
        if 6 <= L <= 9 and block_of(L, n) in (2, 4):
            # Review 8 (the S&C): ten quiet landings a week keep the tendons springy; first, at full rest.
            M.insert(0, (st('squat_jump', 2, rest=90, tempo='controlled'), 0))
        else:
            M.append((st('hollow_body_hold', 1, ctx=c, rest=60), 1))
        if L == 3 and n >= 86:
            # Review 7: squat jumps, quiet and submaximal, for the side plank; first, fresh (the 5-year coach, review 9).
            M.insert(0, (st('squat_jump', 2, rest=90, tempo='controlled'), 0))
        else:
            M.append((st('straight_side_plank' if L >= 3 else 'side_plank', 1, ctx=c, rest=45), 2))
        return P, M, 'legs', 'legs', 50, 6
    if role == 'planche_push':
        M = ladder('planche', L, n, 4, step, backoff=1 if started('pseudo_planche_pushup', L, n) else 2)
        if not M:
            if started('planche_lean', L, n):
                M = [(st('frog_stand', 3, ctx=c, rest=90), 0), (st('planche_lean', 2, ctx=c, rest=90), 1)]
            else:
                M = [(st('frog_stand', 2, ctx=c, rest=90), 0)]
        elif L <= 5:
            M.append((st('planche_lean', 1, ctx=c, rest=60), 2))
        if L == 7 and block_of(L, n) in (2, 4):
            # Dynamic planche strength (the owner's "go fix", the 10-year coach): in blocks 2 and 4 the tuck back-off becomes
            # its push-up; blocks 1, 3 and 5 keep the hold, which reaches its Elite target there.
            M = [(st('tuck_planche_pushup', s[1], ctx=c, rest=150), p) if s[0] == 'tuck_planche' else (s, p) for s, p in M]
        if started('pseudo_planche_pushup', L, n):
            M.append((st('pseudo_planche_pushup', 1 if rung('planche', L, n)[0] else 2, ctx=c, rest=120, step=step), 1))
        if L == 2:
            M.append((st('pike_pushup', 2, ctx=c, rest=90), 1))
        M.append((st(dip_basic(L, n), 3, ctx=c, rest=120, step=step), 0))
        M.append((st('pushup', 2, ctx=c, rest=90, step=step if L == 2 else 0), 1 if L >= 3 else 0))
        return handstand_practice(L, n, 2), M, 'push', 'push', 55, 7
    if role == 'handstand_press':
        P = handstand_practice(L, n, 3)
        if started('freestanding_handstand', L, n) and L <= 5:
            # The wall hold stays as the handstand's endurance back-off until it reaches its own top.
            P.append((st('wall_handstand', 1, ctx=c, rest=60, tempo='easy technique'), 1))
        if L == 2:
            M = []
            if started('handstand_exit', L, n):
                M.append((st('handstand_exit', 2, ctx=c, rest=60, tempo='controlled'), 0))
            if started('handstand_toe_pull', L, n):
                M.append((st('handstand_toe_pull', 2, ctx=c, rest=60), 1))
            M += [(st('pike_pushup', 3, ctx=c, rest=120, step=step), 0), (st('german_hang' if started('german_hang', L, n) else 'arch_hold', 2, ctx=c, rest=75, tempo='slow'), 1),
                  (st('hollow_body_hold', 2, ctx=c, rest=60), 1), (st('seated_pike_lift', 2, ctx=c, rest=60), 2)]
            return P, M, 'push', 'push', 45, 5
        # The one-arm handstand ladder leads the main part, before any pressing.
        M = ladder('oah', L, n, 3, step, backoff=1, rest=90)
        free = []
        if started('freestanding_hspu', L, n):
            # The full freestanding rep, its negative kept as the back-off (review 7, the owner); before the wall reps, fresh
            # (the 20-year coach, review 9).
            free = [(st('freestanding_hspu', 2, ctx=c, rest=180), 0), (st('freestanding_hspu_negative', 1, ctx=c, rest=150, tempo='5010'), 1)]
        elif started('freestanding_hspu_negative', L, n):
            free = [(st('freestanding_hspu_negative', 2, ctx=c, rest=150, tempo='5010'), 1)]
        if pos(8, 78) <= pos(L, n) < pos(*DOSED['straddle_one_arm_handstand']['start']):
            # Review 8 (the statics coach): the one-arm hover takes the finished weight shift's back-off set, the library's
            # step between the fingertip hold and the straddle one-arm handstand.
            M = [(st('one_arm_handstand_hover', 1, rest=90, tempo='easy technique'), 1) if s[0] == 'handstand_weight_shift' else (s, p)
                 for s, p in M]
        if pos(6, 57) <= pos(L, n) < pos(*DOSED['handstand_weight_shift']['start']):
            # Review 8: the shift with the toes on the wall takes one straddle handstand set, the wall step before the free one.
            at = next((i for i, (s, p) in enumerate(M) if s[0] == 'straddle_handstand' and s[1] >= 2), None)
            if at is not None:
                s, p = M[at]
                M[at] = (s[:1] + [s[1] - 1] + s[2:], p)
                M.insert(at + 1, (st('supported_handstand_shift', 1, rest=60, tempo='easy technique'), 0))
        M += free
        if started('deficit_wall_hspu', L, n):
            M += [(st('deficit_wall_hspu', 3, ctx=c, rest=150, step=step), 0), (st('wall_hspu', 1, ctx=c, rest=120), 1)]
        elif started('wall_hspu', L, n):
            M.append((st('wall_hspu', 3, ctx=c, rest=150, step=step), 0))
        elif started('wall_hspu_negative', L, n):
            M.append((st('wall_hspu_negative', 3, ctx=c, rest=120, tempo='5010'), 0))
        if not started('deficit_wall_hspu', L, n):
            # Once the deficit press runs, the pike sets go: the day stays near 12 wrist sets (the S&C review).
            M.append((st('elevated_pike_pushup' if started('elevated_pike_pushup', L, n) else 'pike_pushup', 2, ctx=c, rest=120, step=step), 1))
        if L <= 4 and started('elevated_pike_pushup', L, n):
            # The pike push-up stays as the press's back-off until it reaches its own top.
            M.append((st('pike_pushup', 1, ctx=c, rest=90), 1))
        if started('pistol_squat', L, n):
            knee = rung('shrimp', L, n)[0] if (rung('shrimp', L, n)[0] and block_of(L, n) % 2 == 1) else 'pistol_squat'
            M.append((st(knee, 2, ctx=c, rest=90, frac=0.7), 2))
        else:
            M.append((st('bulgarian_split_squat', 2, ctx=c, rest=90), 2))
        # Shoulder extension: the skin-the-cat or the German hang. The supinated hang stays on the bar day, never the class
        # before the Korean dip (the statics and S&C reviews).
        ext = 'skin_the_cat' if started('skin_the_cat', L, n) else 'german_hang'
        M.append((st(ext, 1, ctx=c, rest=60, tempo='slow'), 1))
        M.append((st('arch_hold', 1, ctx=c, rest=45), 2))
        # Review 8 (all three): the pancake lift from L4:081, then in blocks 3 and 5, for the straddle shapes and the V-sit.
        pancake = L == 4 and n >= 77 or L >= 5 and block_of(L, n) in (3, 5)
        M.append((st('pancake_compression' if pancake else 'seated_pike_lift', 1, ctx=c, rest=60), 2))
        return P, M, 'push', 'push', 55, 6
    if role == 'back_flag':
        lines = bar_lines(L, n)
        lead = bar_lead(L, n)
        M = []
        # The leading line first, fresh, but the Hefesto line last once it bends the arms (from the Korean dip): its
        # bent-arm work tires the biceps the back lever then stretches (the statics review 6). The lead keeps its sets.
        for line in sorted(lines, key=lambda k: (bent_hefesto(k, L, n), k != lead)):
            M += line_steps(line, L, n, line == lead, step, len(lines) == 3)
        if L == 2 or not lines:
            M = [(st('straight_bar_support', 2, ctx=c, rest=75), 0)] + M
            if started('low_bar_transition', L, n):
                M.append((st('low_bar_transition', 2, ctx=c, rest=90, tempo='controlled'), 0))
        if L == 2:
            M.append((st('dead_hang', 1, ctx=c, rest=60), 1))
        if started('german_hang', L, n) and not rung('back', L, n)[0]:
            M.append((st('german_hang', 1 if L >= 3 else 2, ctx=c, rest=60, tempo='slow'), 1))
        if started('straight_bar_dip', L, n) and not started('strict_muscleup', L, n):
            M.append((st('straight_bar_dip', 2, ctx=c, rest=120), 1))
        if pos(3, 82) <= pos(L, n) < pos(*DOSED['strict_muscleup']['start']):
            # Review 7: the low-bar pullover, on the way to the muscle-up with the jump-assisted one, until the strict one.
            M.append((st('pullover_lowbar', 2, rest=90, tempo='controlled'), 2))
        M.append((st(hang_core(L, n), 3, ctx=c, rest=75), 1))
        if 5 <= L <= 7:
            # Dynamic lever strength (the owner's "go fix", the 10-year coach): in Levels 5-7 the tuck front-lever row on the
            # high bar takes the feet-elevated rows' place, the next step in horizontal pulling.
            M.append((st('tuck_front_lever_row', 2, ctx=c, rest=120), 1))
        else:
            M.append((st('feet_elevated_row' if started('feet_elevated_row', L, n) else 'inverted_row', 2, ctx=c, rest=90), 1))
        if L < 3:
            M.append((st('arch_hold', 1, ctx=c, rest=45), 2))
        P = [] if L >= 3 else handstand_practice(L, n, 1)
        return P, M, 'pull', 'pull', 55, 7 if L >= 3 else 5
    if role == 'basics':
        sbd = started('strict_muscleup', L, n) and not started('assisted_korean_dip', L, n)
        # The lever sets come straight after the practice, before any basics (the statics review 6).
        M = second_exposures(L, n)
        # Three pull-up sets from Level 4: vertical pulling reaches about 11 sets a week (the S&C review).
        M += [(st(pull_basic(L, n), 3 if L >= 4 else 2, ctx=c, rest=150, step=step), 0), (st(dip_basic(L, n), 2, ctx=c, rest=120, step=step), 0),
              (st('pushup', 2, ctx=c, rest=90, step=step), 0)]
        if sbd:
            # From the muscle-up to the Korean dip, straight-bar dips keep the Hefesto's gate (10 reps) in reach.
            M.append((st('straight_bar_dip', 2, ctx=c, rest=120, step=step), 1))
        hard_push = first_started(L, n, 'one_arm_pushup', 'archer_pushup')
        if started(hard_push, L, n):
            M.append((st(hard_push, 2, ctx=c, rest=150, step=step if hard_push == 'one_arm_pushup' else 0), 0))
        M += day6_press(L, n)
        M += circuit([pull_basic(L, n), dip_basic(L, n), 'pause_pushup' if L == 2 and n >= 55 else 'pushup', leg_basic(L, n), hang_core(L, n)], 2, L, n, rest=45, round_rest=120, frac=0.7)
        return handstand_practice(L, n, 2) + one_arm_attempts(L, n), M, 'full', 'full', 50, 6
    if role == 'battle' and (L, n) == FULL_BATTLE:
        # The Full Battle (the owner, 2.6.8): a challenge class, no longer the last exam. Five rounds of 10 pull-ups, 20
        # dips, 30 push-ups and 40 squats, 30 s between stations and 90 s between rounds, every set as written; nothing
        # else, so the class keeps its length.
        M = []
        for r in range(5):
            for i, (mid, v) in enumerate((('pullup', 10), ('parallel_dip', 20), ('pushup', 30), ('squat', 40))):
                M.append((st(mid, 1, value=v, rest=90 if i == 3 else 30, tempo='circuit'), 0))
        return [], M, 'full', 'full', 55, 7
    if role == 'battle':
        # One muscle-up set at the working dose: the muscle-up trains twice a week (the statics review).
        # The pull-up set day 1 gave up opens the battle at its full dose (the S&C review).
        # The order (the teacher's and statics reviews 6): the lever sets, the muscle-up and pull-ups, the pushing and the
        # press, then the rounds last.
        M = second_exposures(L, n)
        M += [(st('strict_muscleup', 1, ctx=c, rest=180, tempo='controlled'), 0), (st('pullup', 1, ctx=c, rest=150), 0)]
        M.append((st('one_arm_pushup', 2, ctx=c, rest=150), 1))
        M += day6_press(L, n)
        M += circuit(['pullup', 'parallel_dip', 'pushup', 'pistol_squat'], 3, L, n, rest=30, round_rest=120, frac=0.75)
        M.append((st('toes_to_bar', 2, ctx=c, rest=75, frac=0.8), 2))
        return one_arm_attempts(L, n), M, 'full', 'full', 50, 7
    if role == 'statics_chain':
        # The flag and the planche stay out: yesterday's bar day held the flag, and the two never meet on consecutive days
        # (the statics review). 60 s between the shapes (the S&C review).
        chain = [r for r in (rung('front', L, n)[0], rung('back', L, n)[0]) if r]
        M = [(st(r, 1, ctx=c, rest=60, frac=0.6), 0) for r in chain]
        M += [(st('v_sit', 1, ctx=c, rest=60, frac=0.6), 0), (st('freestanding_handstand', 1, ctx=c, rest=120, frac=0.6), 0),
              (st('strict_muscleup', 1, ctx=c, rest=180, tempo='controlled'), 0),
              (st('pullup', 3, ctx=c, rest=150), 0), (st('parallel_dip', 2, ctx=c, rest=120, frac=0.7), 1),
              (st('one_arm_pushup', 2, ctx=c, rest=150), 1), (st('pistol_squat', 2, ctx=c, rest=90, frac=0.7), 2)]
        M += day6_press(L, n)
        return P, M, 'full', 'full', 50, 7
    raise ValueError(role)


# ----- the special classes -----
def line_drills(L, n):
    """One easy set of each skill line, below working intensity: keeps every line in the week of a lighter class."""
    c = (L, n)
    out = []
    if L >= 3:
        top, below = rung('front', L, n)
        out.append((st(below or top or 'tuck_front_row', 1, ctx=c, rest=45, frac=0.5), 1))
        out.append((st(first_started(L, n, 'german_hang', 'skin_cat_partial', 'arch_hold'), 1, ctx=c, rest=60, tempo='slow', frac=0.5), 1))
        out.append((st('straight_bar_support', 1, ctx=c, rest=60, frac=0.5), 1))
        out.append((st('planche_lean' if started('planche_lean', L, n) else 'frog_stand' if started('frog_stand', L, n) else 'elevated_plank', 1,
                       ctx=c, rest=60, frac=0.5), 1))
        out.append((st('candlestick', 1, value=5, rest=45), 1))
    return out


def easy_handstand(L, n, sets):
    """The handstand in an easy class: the athlete's own rung at half its dose, never back to the wall once free
    (the teacher's review 6)."""
    hs = first_started(L, n, 'freestanding_handstand', 'wall_handstand', 'pike_handstand_hold')
    return (st(hs, sets, ctx=(L, n), rest=60, tempo='easy technique', frac=0.5 if hs == 'freestanding_handstand' else 0.6), 0)


def reentry(L, n):
    c = (L, n)
    M = [easy_handstand(L, n, 2),
         (st('hollow_body_hold', 2, ctx=c, rest=45, frac=0.6), 0), (st('dead_hang', 1, ctx=c, rest=60, frac=0.6), 0),
         (st('pushup', 2, ctx=c, rest=60, frac=0.5), 0), (st(leg_basic(L, n), 2, ctx=c, rest=60, frac=0.5), 0),
         (st('glute_bridge', 1, ctx=c, rest=30, frac=0.6), 1)]  # the week's hinge when the exam week has no legs day
    M += line_drills(L, n)
    return [], M, 'easy', 'easy', 35, 4


def primer(L, n):
    c = (L, n)
    M = [(st('pushup', 1, ctx=c, rest=60, frac=0.5), 0), (st(pull_basic(L, n), 1, ctx=c, rest=90, frac=0.5), 0),
         (st(dip_basic(L, n), 1, ctx=c, rest=60, frac=0.5), 0), (st(leg_basic(L, n), 1, ctx=c, rest=60, frac=0.5), 0),
         (st('hollow_body_hold', 1, ctx=c, rest=45, frac=0.5), 0), (st('glute_bridge', 1, ctx=c, rest=30, frac=0.5), 1)]
    if started('pike_pushup', L, n):
        M.append((st('pike_pushup', 1, ctx=c, rest=45, frac=0.5), 1))
    M += line_drills(L, n)
    if L >= 3:
        # The day before a level test stays at 8 sets (the S&C review 6): the line drills go first, then the bridge.
        while len(M) > 8:
            # In Level 3 the candlestick stays: the dragon line's only set in the test's week (the S&C review 8).
            M.remove(next(x for x in reversed(M) if x[1] > 0 and (L > 3 or x[0][0] != 'candlestick')))
    return [], M, 'full', 'easy', 40, 4


def easy_class(L, n):
    c = (L, n)
    M = [easy_handstand(L, n, 2),
         (st('pushup', 2, ctx=c, rest=60, frac=0.5), 0), (st('squat', 2, ctx=c, rest=60, frac=0.5), 0),
         (st('hollow_body_hold', 2, ctx=c, rest=45, frac=0.6), 0), (st('dead_hang', 1, ctx=c, rest=60, frac=0.6), 1)]
    if L >= 8 and started('one_arm_pushup', L, n):
        # The level test tests the one-arm push-up (2.6.8); day 6, its class, is the Full Battle in Level 10.
        M.append((st('one_arm_pushup', 1, ctx=c, rest=60, frac=0.5), 1))
    M += line_drills(L, n)
    return [], M, 'full', 'easy', 40, 4


def scaled(steps, set_frac=1.0, dose_frac=None, L=None, minus=0):
    out = []
    for s, p in steps:
        s = list(s)
        if minus:
            s[1] = max(1, s[1] - minus)
        elif set_frac != 1.0:
            s[1] = max(1, int(s[1] * set_frac + 0.5))
        if dose_frac is not None:
            for k in (2, 3):
                if s[k] is not None:
                    v = s[k]
                    side = isinstance(v, str) and v.endswith('/side')
                    num = int(str(v).split('/')[0])
                    num = tidy(s[0], max(1, int(num * dose_frac + 0.5)), L) if s[0] in DOSED else max(1, int(num * dose_frac + 0.5))
                    s[k] = f'{num}/side' if side else num
        out.append((s, p))
    return out


# ----- exams (review 6): one test for every skill family the block trained -----
# The order a test day runs (the three coaches, review 6): balance first, then the straight-arm holds, the bent-arm
# strength, the basics, the trunk last. A leg test sits inside the rest after a hold. Each slot tests its hardest rung
# that has had two weeks (a rung under two weeks old leaves the slot to the rung below). Tiers: H balance, A max
# strength, L legs, B the rep basics, C the trunk.
EXAM_SLOTS = [
    ('H', ['one_arm_handstand', 'freestanding_handstand', 'wall_handstand']),
    ('A', ['full_planche', 'straddle_planche', 'advanced_tuck_planche', 'tuck_planche', 'frog_stand']),
    ('A', ['planche_lean']),
    ('L', ['pistol_squat', 'squat']),
    ('A', ['full_front', 'straddle_front', 'advanced_tuck_front', 'tuck_front_hold']),
    ('L', ['shrimp_squat']),
    ('A', ['full_back', 'straddle_back', 'advanced_tuck_back', 'tuck_back_hold']),
    ('A', ['full_flag', 'straddle_flag', 'tuck_flag']),
    ('A', ['strict_muscleup', 'band_muscleup']),
    ('A', ['one_arm_pullup', 'archer_pullup']),
    ('A', ['deficit_wall_hspu', 'wall_hspu', 'pike_pushup']),
    ('A', ['hefesto']),
    ('A', ['one_arm_pushup']),
    ('A', ['pseudo_planche_pushup']),
    ('B', ['pullup', 'eccentric_pullup', 'assisted_pullup', 'inverted_row']),
    ('B', ['parallel_dip', 'eccentric_dip', 'assisted_dip', 'support_hold']),
    ('B', ['pushup']),
    ('C', ['v_sit', 'lsit']),
    ('C', ['dragon_flag', 'hollow_body_hold']),
    ('C', ['toes_to_bar', 'hanging_leg_raise']),
    ('C', ['dead_hang']),
]
TIER = {m: t for t, ms in EXAM_SLOTS for m in ms}
FAMILY = {m: rungs[0] for rungs in BELT_LADDERS for m in rungs}
HOLDS = set(LADDERS['planche'])
exam_seen = {}  # family -> the last exam that tested it (rotation over a cap)


def exam_caps(L, n):
    """Tests a day can hold before results measure fatigue (review 6): at most 15, 14 in a block exam, with at most 8
    max-strength tests (7 in a block exam), 3 rep basics and 5 legs and trunk tests, and 8 elbow-load sets. Levels 1-2:
    10 and 12. The owner (2.6.8): an exam shows where you stand, so the legs and trunk keep a fifth test (time allowing),
    and the Level 9 and 10 tests hold all nine max-strength families."""
    if L <= 2:
        return dict(total=13 if n == 100 else 11, A=6, B=3, LC=5, H=1)
    level = n == 100
    return dict(total=15 if level else 14, A=(9 if L >= 9 else 8) if level else 7, B=3, LC=5, H=1)


# Supporting strength tests: over a cap they rotate out before the muscle-up, the one-arm pull-up and the Hefesto. The
# four static ladders (planche, front lever, back lever, flag) are never left out (the statics review 6).
SECONDARY = {'one_arm_pushup', 'pseudo_planche_pushup', 'deficit_wall_hspu', 'wall_hspu', 'pike_pushup', 'archer_pullup', 'band_muscleup'}
PRESS_TESTS = ('deficit_wall_hspu', 'wall_hspu', 'pike_pushup')
PUSH_TESTS = PRESS_TESTS + ('one_arm_pushup',)
EXAM_ELBOW = 8  # elbow-load sets on an exam day, the German hang included (the S&C review 6)


def exam_picks(L, n, raw):
    """The skills exam (L, n) tests: in each slot the hardest rung with two weeks behind it, when its family had at least
    4 sets in the 21 classes before. The lower rungs of a tested ladder are held through it, so they aren't tested; the
    pull-up and push-up are, beside the one-arm rungs, because reps measure something else (the statics review 6)."""
    here = pos(L, n)
    sets = defaultdict(int)
    for (L2, n2), r in raw.items():
        if r['kind'] != 'exam' and 0 < here - pos(L2, n2) <= 21:
            for s, _ in r['P'] + r['M']:
                sets[FAMILY.get(s[0], s[0])] += s[1]
    picks = []
    for tier, cands in EXAM_SLOTS:
        ok = [m for m in cands if started(m, L, n) and here - pos(*DOSED[m]['start']) >= 14]
        if not ok:
            continue
        m = ok[0]
        family = [FAMILY.get(x, x) for x in cands]
        if sum(sets[f] for f in dict.fromkeys(family)) < 4:
            continue
        if m == 'planche_lean' and not any(p == 'frog_stand' for p in picks):
            continue  # the lean stands in beside the frog stand only, before the tuck planche
        if m == 'pseudo_planche_pushup' and any(p in HOLDS for p in picks):
            continue  # a planche hold tests the family
        if m == 'dead_hang' and 'pullup' in picks and L >= 3:
            continue  # from Level 3 the pull-ups hold the hang; in Levels 1-2 the hang is a skill of its own (the owner, 2.6.8)
        if m == 'band_muscleup' and L >= 5:
            continue
        picks.append(m)
    return picks


def exam_cut(picks, L, n, keep=()):
    """Holds the picks to the caps: over a cap, the family tested most recently waits for the next exam, never one in
    [keep] (the newest skill, the level's standards)."""
    caps = exam_caps(L, n)

    def tier(m):
        t = TIER[m]
        return 'LC' if t in ('L', 'C') else t

    def drop(cands):
        cands = [m for m in cands if m not in keep]
        cands = [m for m in cands if m in SECONDARY] or cands  # the supporting strength tests rotate first
        if not cands:
            return False
        picks.remove(max(cands, key=lambda m: (exam_seen.get(FAMILY.get(m, m), -1), -picks.index(m))))
        return True
    for t in ('H', 'A', 'B', 'LC'):
        while sum(1 for m in picks if tier(m) == t) > caps[t] and drop([m for m in picks if tier(m) == t]):
            pass
    hang = any(m in LADDERS['back'] + ['hefesto'] for m in picks)
    while sum(1 for m in picks if m in ELBOW) + hang > EXAM_ELBOW and drop([m for m in picks if m in ELBOW]):
        pass
    # At most 10 advanced sets on an RPE 8 day (the class limits): the advanced legs and trunk tests rotate out first.
    adv = [m for m in picks if LIB[m]['category'] in ('advanced_skill', 'mastery_skill', 'advanced_strength', 'skill_strength')]
    while len(adv) > 10 and (drop([m for m in adv if tier(m) == 'LC']) or drop(adv)):
        adv = [m for m in adv if m in picks]
    while len(picks) > caps['total'] and (drop([m for m in picks if tier(m) == 'LC']) or drop([m for m in picks if tier(m) == 'B'])):
        pass
    return picks


def exam_value(mid, L, n):
    """An exam item from the plan: the block's week-B dose. The build then sets it to what the athlete trained
    (honest_exam)."""
    return dose(mid, L, n, step=1)


def exam_rest(mid, L, nxt=None, short=False):
    """Rest after a test (review 6): 150 s after a max-strength test (120 s in Levels 3-4, or when the class runs long),
    120 s after the handstand and the basics, 90 s after a leg test and 60 s between trunk tests. A leg test right after
    a hold splits its rest: 60 s, the leg test, then 90 s, so holds stay about three minutes apart."""
    if L <= 2:
        return 90
    t = TIER.get(mid, 'C')
    if t == 'A':
        return 60 if nxt and TIER.get(nxt) == 'L' else 120 if L <= 4 or short else 150
    if t == 'B':
        return 150 if mid == 'parallel_dip' and nxt == 'pushup' else 120
    return 120 if t == 'H' else 90 if t == 'L' else 60


ADVANCED_STANDARD = [('freestanding_handstand', 30), ('strict_muscleup', 4), ('advanced_tuck_planche', 8), ('straddle_front', 5),
                     ('full_back', 8), ('full_flag', 3), ('wall_hspu', 7), ('pullup', 15), ('parallel_dip', 20), ('pushup', 30),
                     ('pistol_squat', 8), ('lsit', 20)]
EXAM_FLOOR = {(7, 100): dict(ADVANCED_STANDARD)}  # the Advanced Athlete exam: its standards are floors (the statics review 6)


def exam_steps(picks, L, n, short=False):
    """The tests in slot order, and the prep sets they need: the German hang before a back lever or Hefesto test, and one
    easy set of the tuck rung before a straddle or full lever or flag (the statics review 7). The owner (2.6.8): an exam
    lists only targets, each family at its hardest rung, so the prep sets close the warm-up instead of sitting between
    the tests."""
    c = (L, n)
    M, prep = [], []
    hang = False
    floor = EXAM_FLOOR.get((L, n), {})
    order = [m for _, ms in EXAM_SLOTS for m in ms]
    picks = sorted(picks, key=order.index)
    # A push test always parts the one-arm pull and the Hefesto (the S&C review 7): the biceps never meet six elbow
    # tests in a row.
    pull = next((i for i, m in enumerate(picks) if m in ('one_arm_pullup', 'archer_pullup')), None)
    if pull is not None and 'hefesto' in picks and not any(m in PUSH_TESTS for m in picks[pull:picks.index('hefesto')]):
        push = next((m for m in picks if m in PUSH_TESTS), None)
        if push:
            picks.remove(push)
            picks.insert(picks.index('hefesto'), push)
    for i, mid in enumerate(picks):
        if mid in LADDERS['back'] + ['hefesto'] and started('german_hang', L, n) and not hang:
            prep.append(st('german_hang', 1, ctx=c, rest=30, tempo='slow'))
            hang = True
        line = next((k for k in ('front', 'back', 'flag') if mid in LADDERS[k]), None)
        if line and mid in HARD_SHAPES:
            prep.append(st(LADDERS[line][0], 1, value=half_dose(LADDERS[line][0], L, n), rest=30, tempo='ramp-up'))
        v = max(floor.get(mid, 0), exam_value(mid, L, n))
        M.append((st(mid, 1, value=v, rest=exam_rest(mid, L, picks[i + 1] if i + 1 < len(picks) else None, short), tempo='assessment'), 0))
    return M, prep


def half_dose(mid, L, n):
    """Half of [mid]'s dose at (L, n), or half its Advanced target once the plan no longer doses it: a ramp set."""
    d = dose(mid, L, n) if started(mid, L, n) else None
    if d is None:
        d = SKILLS[mid]['t'][2] if mid in SKILLS else 10
    return tidy(mid, max(1, int(d * 0.5 + 0.5)), L)


def exam(L, n, raw):
    """An exam class: one fresh set of every skill family the block trained (review 6), in the slot order. Every exam is
    a skills test (the owner, 2.6.8): Level 10 ends on a level test like Levels 7-9, and the Full Battle is a class."""
    c = (L, n)
    picks = exam_picks(L, n, raw)
    for mid in EXAM_FLOOR.get((L, n), {}):
        slot = next(ms for _, ms in EXAM_SLOTS if mid in ms)
        picks = [m for m in picks if m not in slot] + [mid]  # the standard's rung takes its slot
    keep = set(EXAM_FLOOR.get((L, n), {}))
    if L >= 3:
        keep |= {m for m in picks if any(m in LADDERS[k] for k in STATIC_LADDERS)}
    if picks:
        keep.add(max(picks, key=lambda m: (pos(*DOSED[m]['start']), -picks.index(m))))  # the newest skill: the headline
    full = list(picks)
    picks = exam_cut(picks, L, n, keep)
    pulls = [m for m in ('one_arm_pullup', 'archer_pullup') if m in picks]
    press = next((m for m in full if m in PRESS_TESTS), None)
    if (pulls or 'hefesto' in picks) and press and press not in picks:
        # The press parts the one-arm pull and the Hefesto (the S&C review 7): it stays whenever either is tested. Over
        # the strength cap the one-arm push-up waits first, then a block exam tests one of the two, the newest skill or
        # else the one tested longer ago.
        picks.append(press)
        if sum(1 for m in picks if TIER[m] == 'A') > exam_caps(L, n)['A']:
            if 'one_arm_pushup' in picks and 'one_arm_pushup' not in keep:
                picks.remove('one_arm_pushup')
            elif pulls and 'hefesto' in picks:
                two = [m for m in pulls + ['hefesto'] if m not in keep] or pulls + ['hefesto']
                picks.remove(max(two, key=lambda m: (exam_seen.get(FAMILY.get(m, m), -1), -picks.index(m))))
    cap = 45 if L <= 2 else 60  # an exam may take an hour (the statics and teacher reviews 6)
    short = False
    while True:
        M, prep = exam_steps(picks, L, n, short)
        warm = WARM['exam'] + [st(picks[0], 1, value=1, rest=45, tempo='ramp-up')] * (2 if L >= 7 else 1) + prep
        if class_seconds([warm, [s for s, _ in M], COOL['exam']]) <= (cap - 2) * 60:
            break
        # Over the length: the strength rests shorten to 120 s first, so every family keeps its test (the owner, 2.6.8:
        # an exam shows where you stand); then the trunk and legs tests rotate out, and only then a basic waits; a
        # max-strength test never does.
        if not short:
            short = True
            continue
        lc = [m for m in picks if TIER[m] in ('L', 'C') and m not in keep]
        b = [m for m in picks if TIER[m] == 'B' and m not in keep]
        cands = lc or b
        if not cands:
            break
        picks.remove(max(cands, key=lambda m: (exam_seen.get(FAMILY.get(m, m), -1), -picks.index(m))))
    for m in picks:
        exam_seen[FAMILY.get(m, m)] = pos(L, n)
    if (L, n) == (7, 100):
        dtype = 'exam_level_advanced_athlete'
    else:
        dtype = 'exam_level' if n == 100 else 'exam_block'
    return [], M, 'exam', 'exam', cap, 8 if L >= 4 else 7, dtype, prep


# ----- fitting a class: the set budget, the elbow cap, the length -----
def secs_of(step):
    mid, sets, reps, secs, rest, tempo, _ = step

    def units(v):
        if v is None:
            return 0
        t = str(v)
        side = 2 if t.endswith('/side') else 1
        return int(t.split('/')[0]) * side
    work = 0
    if secs is not None:
        work = units(secs) * sets
    elif reps is not None:
        tp = sum(int(ch) for ch in str(tempo) if ch.isdigit()) if str(tempo).isdigit() and len(str(tempo)) == 4 else 2
        work = units(reps) * sets * max(tp, 2)
    return work, (sets - 1) * rest, rest


def class_seconds(sections):
    """A class's length: the work, the rests between sets, a change-over between exercises and, after a single set, its
    rest before the next one. Steps of one movement in a row (a heavy set, then lighter ones) time as one exercise."""
    total = 0
    for steps in sections:
        for i, s in enumerate(steps):
            work, rests, rest = secs_of(s)
            if i + 1 < len(steps) and steps[i + 1][0] == s[0]:
                total += work + rests + rest
                continue
            group, j = s[1], i - 1
            while j >= 0 and steps[j][0] == s[0]:
                group, j = group + steps[j][1], j - 1
            total += work + rests + 25 + (rest if group == 1 else 0)
    return total


def fit(P, M, L, cap_minutes, extra=0, rpe=0, exam_day=False, elbow_cap=ELBOW_CAP, budget=None):
    """Trim to the level's set budget, the per-step limit, the elbow cap and the class length; supporting work goes
    first."""
    budget = budget or LIMITS[L]['main_sets_including_practice']
    per = LIMITS[L]['sets_per_exercise']
    steps = [[list(s), p, 'P'] for s, p in P] + [[list(s), p, 'M'] for s, p in M]
    for x in steps:
        x[0][1] = min(x[0][1], per)

    def total():
        return sum(x[0][1] for x in steps)

    def main_count():
        return sum(1 for x in steps if x[2] == 'M')
    ADV = ('advanced_skill', 'mastery_skill', 'advanced_strength', 'skill_strength')

    def adv():
        return sum(x[0][1] for x in steps if x[2] == 'M' and LIB[x[0][0]]['category'] in ADV)

    def main_sets():
        return sum(x[0][1] for x in steps if x[2] == 'M')

    def elbow():
        return sum(x[0][1] for x in steps if x[0][0] in ELBOW)
    while not exam_day and elbow() > elbow_cap:
        cand = sorted([x for x in steps if x[0][0] in ELBOW and x[0][1] > 1], key=lambda x: (-x[1], -x[0][1]))
        if cand and (cand[0][1] > 0 or not [x for x in steps if x[0][0] in ELBOW and x[1] > 0]):
            cand[0][0][1] -= 1
            continue
        drop = [x for x in steps if x[0][0] in ELBOW and x[1] > 0]
        if drop:
            x = sorted(drop, key=lambda x: -x[1])[0]
            for y in [y for y in steps if y[1] == x[1]] if x[0][5] == 'circuit' else [x]:
                steps.remove(y)  # a circuit round goes whole
            continue
        if cand:
            cand[0][0][1] -= 1
            continue
        break
    while adv() > 10 and rpe >= 8:
        cand = sorted([x for x in steps if x[2] == 'M' and x[0][1] > 1 and LIB[x[0][0]]['category'] in ADV], key=lambda x: (-x[1], -x[0][1]))
        if not cand:
            break
        cand[0][0][1] -= 1
    while total() > budget or main_sets() > 20 or class_seconds([[x[0] for x in steps]]) + extra > (cap_minutes - 1) * 60:
        cand = sorted([x for x in steps if x[0][1] > 1], key=lambda x: (-x[1], -x[0][1]))
        cand = [x for x in cand if x[1] > 0] or cand
        if cand and cand[0][1] > 0:
            cand[0][0][1] -= 1
            continue
        droppable = [x for x in steps if x[1] == 2 and x[2] == 'M']
        if droppable and main_count() > 4:
            steps.remove(droppable[-1])
            continue
        rounds = [x for x in steps if x[1] >= 3]
        if rounds and main_count() > 4:
            last_round = max(x[1] for x in rounds)
            for x in [x for x in rounds if x[1] == last_round]:
                steps.remove(x)
            continue
        if cand:
            cand[0][0][1] -= 1
            continue
        # Last resort: a single supporting set goes.
        droppable = [x for x in steps if x[1] == 1 and x[2] == 'M']
        if droppable and main_count() > 4:
            steps.remove(droppable[-1])
            continue
        break
    return [x[0] for x in steps if x[2] == 'P'], [x[0] for x in steps if x[2] == 'M']


def name(mid):
    return LIB[mid]['name']['en']


def dose_text(s):
    mid, sets, reps, secs, *_ = s
    v = reps if reps is not None else secs
    t = str(v)
    side = t.endswith('/side')
    num = t.split('/')[0]
    unit = ' s' if secs is not None else ''
    return f'{name(mid)} {sets} × {num}{unit}' + (' a side' if side else '')


ROLE_TITLE = {
    'circuit_a': 'Pull & Push Circuit', 'practice_hand': 'Handstand & Wrists', 'circuit_b': 'Legs & Core Circuit', 'practice_bar': 'Bar & Shoulders',
    'core_balance': 'Balance & Core', 'circuit_c': 'Engine Circuit', 'recovery': 'Recovery', 'front_pull': 'Pull & Front Lever',
    'front_onearm': 'Front Lever & One-Arm', 'legs_core': 'Legs & Core', 'planche_push': 'Planche & Push', 'handstand_press': 'Handstand & Press',
    'back_flag': 'Back Lever & Flag', 'basics': 'Basics & Endurance', 'battle': 'The Battle', 'statics_chain': 'Statics Chain',
}
# One plain line on what the class is (the owner, 2026-10-30: short texts people read; the exercise list sits right under it).
ROLE_SENTENCE = {
    'circuit_a': 'A full-body circuit: one set per station, then repeat the round.',
    'practice_hand': 'Easy practice: handstand, straight-arm support and wrists.',
    'circuit_b': 'A legs, hips and trunk circuit: one set per station, then repeat the round.',
    'practice_bar': 'Easy practice on the bar: hangs, slow lowering and the trunk.',
    'core_balance': 'Easy practice: trunk, compression and balance, with reps to spare.',
    'circuit_c': 'The engine circuit: the basics, one set each, round after round.',
    'recovery': 'A walk, then gentle mobility. Finish fresher than you started.',
    'front_pull': "Pull day: front lever while you're fresh, then muscle-up work and pulling.",
    'front_pull_early': "Pull day: front lever while you're fresh, then pulling.",
    'front_onearm': 'Pull day: front lever, muscle-up, one-arm pulling, then pull-ups.',
    'legs_core': 'Legs and trunk. A day off for the elbows.',
    'planche_push': 'Planche day: a short handstand, the planche, then pushing.',
    'handstand_press': 'Handstand day, easy on the elbows: balance, pressing overhead and gentle shoulder work.',
    'back_flag': 'Bar day: one line leads this block, the others keep two working sets.',
    'basics': 'Basics day: a few sets at full dose, then lighter rounds.',
    'basics_strength': 'Basics day: levers, then the basics and the press, one strong set at a time.',
    'battle': 'The Battle: the basics at fixed rests, every set as written. No racing the clock.',
    'full_battle': ('The Full Battle: five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats, 30 s between stations and '
                    '90 s between rounds. Every set as written.'),
    'statics_chain': 'The Statics Chain: one run through the shapes, short holds, quick changes.',
}
KIND_TITLE = {'deload': 'Lighter', 'light': 'Light', 'reentry': 'Re-Entry', 'primer': 'Exam Prep', 'easy': 'Easy Basics', 'barlight': 'Light Bar',
              'presslight': 'Light Press'}
KIND_SENTENCE = {
    'build_a': '', 'build_b': 'One step up since last time.', 'carry': "New level: last level's doses, one set fewer on the leads.",
    'hold': 'Before the exam: hold these doses, nothing new.', 'trim': 'Before the exam: main sets at the raised dose, no back-off sets.',
    'deload': 'Lighter class: one set fewer of each, same reps and holds.',
    'light': 'A light class: the same work at lower doses.',
    'reentry': 'An easy start after the exam: short sets with plenty in reserve.',
    'primer': 'Tomorrow is the exam. One easy set of each, nothing hard, so you arrive fresh.',
    'easy': 'An easy class before the exam: the basics at half dose.',
    'barlight': "Bar day before the exam. The work is done: one set of each at about 70% of the block's dose.",
    'presslight': "Press day before the exam. The work is done: handstand and press at the block's dose, then stop.",
}
# Kinds whose own line already says what the class is: no role line after it.
KIND_ONLY = ('reentry', 'primer', 'easy', 'barlight', 'presslight')
EXAM_SENTENCE = {
    'exam_block': ('One clean set of each skill at the belt you reached in the last three weeks, in order: balance, holds, strength, '
                   'basics, trunk. Rest fully between tests. Missing the headline or three targets offers a block repeat.'),
    'exam_level': ('One clean set of each skill the level trained, at the belt you reached, in order: balance, holds, strength, basics, '
                   'trunk. Rest fully between tests. Missing the headline or three targets offers a block repeat.'),
    'exam_level_advanced_athlete': ("The Advanced Athlete exam: this program's standard for an advanced athlete, plus one fresh set of every "
                                    'other skill you train. Rest fully between tests.'),
}


# Short names for class titles (the owner: nice, representative names).
SHORT = {'pullup': 'Pull-Ups', 'parallel_dip': 'Dips', 'pushup': 'Push-Ups', 'squat': 'Squats', 'strict_muscleup': 'Muscle-Ups',
         'toes_to_bar': 'Toes-to-Bar', 'hanging_leg_raise': 'Leg Raises', 'dead_hang': 'Hangs', 'inverted_row': 'Rows',
         'feet_elevated_row': 'Rows', 'pike_pushup': 'Pike Push-Ups', 'elevated_pike_pushup': 'Pike Push-Ups', 'wall_hspu': 'Handstand Push-Ups',
         'deficit_wall_hspu': 'Deficit Handstand Push-Ups', 'pistol_squat': 'Pistols', 'shrimp_squat': 'Shrimp Squats',
         'one_arm_pullup': 'One-Arm Pull-Ups', 'archer_pullup': 'Archer Pull-Ups', 'one_arm_pushup': 'One-Arm Push-Ups',
         'pseudo_planche_pushup': 'Pseudo-Planche Push-Ups', 'wall_handstand': 'Wall Handstand', 'freestanding_handstand': 'Handstand',
         'support_hold': 'Support', 'hollow_body_hold': 'Hollow Body', 'eccentric_pullup': 'Slow Pull-Ups', 'assisted_pullup': 'Assisted Pull-Ups',
         'eccentric_dip': 'Slow Dips', 'assisted_dip': 'Assisted Dips', 'band_muscleup': 'Band Muscle-Ups', 'low_bar_transition': 'Low-Bar Transitions',
         'bulgarian_split_squat': 'Split Squats', 'split_squat': 'Split Squats', 'assisted_pistol_squat': 'Assisted Pistols',
         'nordic_curl': 'Nordic Curls', 'nordic_negative': 'Nordic Negatives', 'pike_handstand_hold': 'Pike Handstand',
         'straight_bar_support': 'Bar Support', 'straight_bar_dip': 'Bar Dips', 'tuck_front_row': 'Tuck Front-Lever Rows',
         'one_leg_lsit': 'One-Leg L-Sit', 'tuck_lsit': 'Tuck L-Sit', 'pause_pushup': 'Paused Push-Ups', 'pause_squat': 'Paused Squats', 'chinup': 'Chin-Ups',
         'tuck_front_lever_row': 'Tuck Front-Lever Rows', 'tuck_planche_pushup': 'Tuck Planche Push-Ups', 'assisted_shrimp': 'Assisted Shrimp Squats',
         'assisted_partial_shrimp': 'Partial Shrimp Squats', 'freestanding_hspu': 'Freestanding Handstand Push-Ups', 'tuck_front_hold': 'Tuck Front Lever', 'tuck_back_hold': 'Tuck Back Lever'}
# Movements a class can be named after: skills and strength work, not stretches, mobility or the warm-up drills.
NOTABLE = {'fundamental', 'advanced_fundamental', 'skill', 'advanced_skill', 'mastery_skill', 'strength', 'advanced_strength', 'skill_strength'}


# Supporting work a class is never named after.
ACCESSORY = {'seated_pike_lift', 'calf_raise', 'glute_bridge', 'single_leg_bridge', 'side_plank', 'straight_side_plank', 'plank',
             'elevated_plank', 'dead_bug', 'arch_hold', 'candlestick', 'single_leg_rdl', 'sliding_leg_curl', 'knee_raise', 'reverse_lunge'}


def short(mid):
    n = SHORT.get(mid) or name(mid)
    return n[:-5] if n.endswith(' Hold') else n


# Lines a name counts as one: the planche with its leans and push-ups, the press with its pike and negative steps.
GROUP = {'pseudo_planche_pushup': 'planche', 'planche_lean': 'planche', 'frog_stand': 'planche', 'pike_pushup': 'press',
         'elevated_pike_pushup': 'press', 'wall_hspu_negative': 'press', 'wall_hspu': 'press', 'deficit_wall_hspu': 'press',
         'freestanding_hspu_negative': 'press', 'freestanding_hspu': 'press', 'tuck_front_row': 'front', 'vertical_flag': 'flag', 'flag_negative': 'flag',
         'squat': 'squat', 'split_squat': 'squat', 'bulgarian_split_squat': 'squat', 'assisted_pistol_squat': 'squat'}


def line_of(mid):
    return GROUP.get(mid) or next((k for k, rs in LADDERS.items() if mid in rs), FAMILY.get(mid, mid))


def pair(M):
    """What a class trains: its lead and the next line of another family, "Full Front Lever & Muscle-Ups"."""
    steps = [s for s in M if s[5] not in ('ramp-up', 'assessment') and s[0] not in ('easy_walk', 'german_hang', 'squat_jump')]
    if not steps:
        return None
    a = steps[0]
    b = next((s for s in steps[1:] if line_of(s[0]) != line_of(a[0]) and short(s[0]) != short(a[0])
              and LIB[s[0]]['category'] in NOTABLE and s[0] not in ACCESSORY), None)
    if not b:
        return None
    both = f'{short(a[0])} & {short(b[0])}'
    return both if len(both) <= 40 else short(a[0])  # a name fits a phone row


def next_exam_title(L, n):
    e = next(x for x in EXAMS[L] if x > n)
    return EXAM_NAMES.get((L, e)) or (f'Level {L} Test' if e == 100 else f'Block {block_of(L, e)} Exam')


def class_name(kind, role, L, n, P, M, new, rose=(), tempo=False):
    """A class's name (the owner, review 7): what it trains; "First ..." on the class a new movement arrives; the record
    it holds; or the exam it prepares for. Lighter classes say so first. Review 8 (the teacher): the second half names the
    step that rose since the last class of its type, so a name changes when the class does; a repeat week lowered in 3 s
    says "Tempo"."""
    recs = [s for s in M if s[5] == 'assessment']
    lead = next((s for s in M if s[5] not in ('ramp-up', 'assessment') and s[0] not in ('easy_walk', 'german_hang', 'squat_jump')), M[0])
    # Never a rung of the lead's own ladder: "Full Front Lever & Straddle Front Lever" names a skill with its back-off.
    ladder_of = lambda mid: next((k for k, rs in LADDERS.items() if mid in rs), mid)
    risen = next((m for m in rose if short(m) != short(lead[0]) and ladder_of(m) != ladder_of(lead[0])), None)
    duo = f'{short(lead[0])} & {short(risen)}' if risen else None
    # Without a second line the class keeps its kind's name: "Legs & Core · Squats".
    what = (duo if duo and len(duo) <= 40 else None) or pair(M) or (
        f'{ROLE_TITLE[role]} · {short(lead[0])}' if role in ROLE_TITLE and kind not in KIND_TITLE else short(lead[0]))
    if kind == 'primer':
        return f'Exam Prep · {next_exam_title(L, n)}'
    if kind == 'reentry':
        return 'Re-Entry · Easy Basics'
    if kind == 'easy':
        return 'Easy Basics'
    if kind in KIND_TITLE:
        return f'{KIND_TITLE[kind]} · {what}'
    if recs:
        return f'Record · {name(recs[0][0])}'
    if new and (L, n) != (1, 1):
        return f'First {name(new[0])}'
    fixed = role in ('circuit_a', 'circuit_b', 'circuit_c', 'core_balance', 'basics', 'battle', 'statics_chain')
    if tempo:
        # "Legs & Core · Tempo", not "Tempo · Legs & Core · Squats".
        return f'Tempo · {ROLE_TITLE[role]}' if fixed else f'{what.split(" · ")[0]} · Tempo' if ' · ' in what else f'Tempo · {what}'
    if fixed:
        return ROLE_TITLE[role]
    return what


def tempo_steps(steps):
    """Review 8 (the S&C): a repeat week's rep steps lowered in 3 s, same sets and reps. Never the elbow list, the
    handstand push-ups, holds, or pull-ups over 6 reps."""
    out = []
    for s in steps:
        mid = s[0]
        if (s[5] == '2111' and s[2] is not None and mid in DOSED and mid not in ELBOW and 'hspu' not in mid
                and int(str(s[2]).split('/')[0]) <= (6 if mid == 'pullup' else 12)):
            s = list(s)
            s[5] = '3111'
        out.append(s)
    return out


def build_raw(L, n, raw=None):
    """The class before fitting: practice, main, warm-up and cool-down kinds, minute cap, RPE, day type, role, kind. An
    exam reads the classes before it ([raw]) for what they trained."""
    kind = kind_of(L, n)
    role = role_of(L, n)
    if kind == 'exam':
        P, M, wk, ck, cap, rpe, dtype, prep = exam(L, n, raw)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype=dtype, role=role, kind=kind, prep=prep)
    if kind in ('reentry', 'primer', 'easy'):
        P, M, wk, ck, cap, rpe = {'reentry': reentry, 'primer': primer, 'easy': easy_class}[kind](L, n)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype={'reentry': 'reentry_recovery', 'primer': 'primer_easy', 'easy': 'easy_recovery_basics'}[kind], role=role, kind=kind)
    if kind == 'recovery' or role == 'recovery':
        P, M, wk, ck, cap, rpe = build_role('recovery', L, n, 0)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype='recovery', role='recovery', kind='recovery')
    if kind == 'presslight':
        P, M, wk, ck, cap, rpe = press_light(L, n)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype='light_handstand_press', role=role, kind=kind)
    if kind == 'barlight':
        # The exam week's bar day: the lead line's sets only (the fit holds it to 4 elbow-load sets), the rest as written.
        P, M, wk, ck, cap, rpe = bar_light(L, n)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=45, rpe=5, dtype='light_back_flag', role=role, kind=kind)
    step = 1 if kind in ('build_b', 'hold', 'trim') else 0
    P, M, wk, ck, cap, rpe = build_role(role, L, n, step)
    dtype = role
    if kind == 'deload':
        P, M = scaled(P, minus=1), scaled(M, minus=1)
        rpe, dtype = max(5, rpe - 1), 'deload_' + role
    elif kind == 'light':
        P, M = scaled(P, 0.75, 0.6, L), scaled(M, 0.75, 0.6, L)
        rpe, dtype = 5, 'light_' + role
    elif kind == 'trim':
        M = [x for x in M if x[1] != 1 or x[0][0] not in {r for rs in LADDERS.values() for r in rs}]
    elif kind == 'carry':
        M = [((s[:1] + [max(1, s[1] - 1)] + s[2:]) if p == 0 and s[1] >= 3 else s, p) for s, p in M]  # 2-set steps stay at 2
    if (L, n) == FULL_BATTLE:
        dtype = 'full_battle'
    if role in ('practice_hand', 'practice_bar', 'core_balance'):
        dtype = {'practice_hand': 'practice_push_line', 'practice_bar': 'practice_bar_line', 'core_balance': 'practice_core_line'}[role]
    return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype=dtype, role=role, kind=kind)


RECORD_ROLE = {}
for _r in LADDERS['front']:
    RECORD_ROLE[_r] = ('front_onearm',)
for _r in LADDERS['back'] + LADDERS['flag'] + ['hefesto', 'hanging_leg_raise']:
    RECORD_ROLE[_r] = ('back_flag',)
for _r in LADDERS['planche'] + ['pseudo_planche_pushup', 'one_arm_pushup', 'parallel_dip', 'pushup']:
    RECORD_ROLE[_r] = ('planche_push',)
for _r in ('wall_handstand', 'freestanding_handstand', 'one_arm_handstand', 'wall_hspu', 'deficit_wall_hspu', 'pike_pushup'):
    RECORD_ROLE[_r] = ('handstand_press',)
for _r in ('pullup', 'archer_pullup', 'one_arm_pullup', 'toes_to_bar', 'dead_hang', 'strict_muscleup'):
    RECORD_ROLE[_r] = ('front_onearm',)
for _r in ('squat', 'pistol_squat', 'shrimp_squat', 'dragon_flag', 'hollow_body_hold', 'lsit', 'v_sit'):
    RECORD_ROLE[_r] = ('legs_core',)
RECORD_ROLE = {k: v for k, v in RECORD_ROLE.items() if k in SKILLS}
assert set(RECORD_ROLE) == set(SKILLS), set(SKILLS) - set(RECORD_ROLE)


def add_records(raw, skip=()):
    """Level 10 is for records: a skill the classes never take to its Elite target gets one set at it, an assessment.
    The skills in `skip` need none: their fitted classes reach the target, or they hold Elite through a harder rung."""
    best = defaultdict(int)
    for (L, n), r in raw.items():
        for s, _ in r['P'] + r['M']:
            if s[0] in SKILLS:
                v = s[2] if s[2] is not None else s[3]
                best[s[0]] = max(best[s[0]], int(str(v).split('/')[0]))
    placed = {}
    used = set()
    for sid, sk in SKILLS.items():
        elite = sk['t'][4]
        if sid in skip:
            continue
        roles = RECORD_ROLE[sid]
        for n in range(91, 14, -1):
            r = raw[(10, n)]
            if (10, n) in used or r['kind'] not in ('build_a', 'build_b') or r['role'] not in roles:
                continue
            sect = 'P' if any(s[0] == sid for s, _ in r['P']) else 'M'
            steps = r[sect]
            idx = next((i for i, (s, _) in enumerate(steps) if s[0] == sid), None)
            rec = st(sid, 1, value=elite, rest=180, tempo='assessment')
            if idx is not None and steps[idx][0][1] > 1:
                s, p = steps[idx]
                steps[idx] = ([s[0], s[1] - 1] + s[2:], p)
            elif idx is not None:
                steps.pop(idx)
            # Review 9 (the 5- and 20-year coaches): a record is tried fresh, first, with the working sets after it as the
            # back-off; the long hang record goes last, so the grip is fresh for the pulling.
            if sid == 'dead_hang':
                r['M'].append((rec, 0))
            elif sect == 'P':
                steps.insert(idx or 0, (rec, 0))
            else:
                r['M'].insert(0, (rec, 0))
            used.add((10, n))
            placed[sid] = n
            break
        else:
            placed[sid] = None
    return placed, dict(best)


FORBIDDEN = r'(?i)(choose|choice|optional|independent|\broutes?\b|routing|non[-_ ]?blocking)'
TOKENS = {
    'pull': r'(?:^|\s)(?:assisted_pullup|eccentric_pullup|pullup|chinup|archer_pullup|typewriter_pullup|assisted_one_arm_pullup|one_arm_pullup_negative|inverted_row|high_inverted_row|feet_elevated_row)(?:\s|$)',
    'push': r'(?:^|\s)(?:wall_pushup|incline_pushup|pushup|pause_pushup|close_pushup|archer_pushup|pike_pushup|elevated_pike_pushup|wall_hspu_negative|wall_hspu|deficit_wall_hspu|freestanding_hspu_negative|freestanding_hspu)(?:\s|$)',
    'dip': r'(?:^|\s)(?:assisted_dip|eccentric_dip|parallel_dip|straight_bar_dip|support_hold|foot_assisted_support|straight_bar_support)(?:\s|$)',
    'legs': r'(legs|squat|lunge|single_leg|calf)', 'core': r'(core|bodyline|hollow|plank|dragon|l_sit|compression)',
}
GOALS = {'muscle_up': r'muscle.?up', 'front_lever': r'front_lever', 'back_lever': r'back_lever', 'dragon_flag': r'dragon', 'planche': r'planche'}


def tokens(prog):
    out = []
    for s in prog['main']:
        out.append(s[0])
        out += LIB[s[0]]['patterns']
    return ' '.join(out).lower()


def build():
    """All 1,000 classes. A first pass without records finds the skills whose fitted classes reach their Elite target and
    the rungs that hold Elite through a harder rung inside the program; the second gives the records to the rest."""
    programs, _, _ = build_once(set(SKILLS))
    landed, _, via = belts(programs)
    reach = best_sets(programs)
    # Review 11: a skill already holding Elite (the hanging leg raise, through the toes-to-bar sets) needs no record.
    programs, records, best = build_once({sid for sid in SKILLS if landed[sid][4] or via[sid][4] or reach[sid] >= SKILLS[sid]['t'][4]})
    return hold_elite_share(programs), records, best


def hold_elite_share(programs):
    """Review 11's ceiling (90% of a top rung's Elite sets inside the program) after the level tests' own sets (2.6.8):
    over it, the latest training sets at the Elite target before the level test drop a step, one class at a time. A
    one-class edit at the end, so no earlier dose moves (an ELITE_FROM shift lowers whole weeks through the rise rule)."""
    _, count, _ = belts(programs)
    for sid in ELITE_FROM:
        if sid in ELITE_FULL:
            continue  # its Elite finishes inside the program
        over = count[sid][4] - ELITE_SHARE[1]
        elite = SKILLS[sid]['t'][4]
        for p in reversed(programs):
            if over <= 0:
                break
            if p['schedule']['day_type'].startswith('exam'):
                continue
            for sect in ('main', 'practice'):
                for i, x in enumerate(p[sect]):
                    if over > 0 and x[0] == sid and x[5] not in ('assessment', 'ramp-up') and value_of(x) >= elite and x[1] <= over:
                        p[sect][i] = with_value(x, elite - 1)
                        over -= x[1]
    return programs


def best_sets(programs):
    """Each skill's biggest set in the practice and main parts, readiness checks left out."""
    reach = defaultdict(int)
    for p in programs:
        for s in p['practice'] + p['main']:
            if s[0] in SKILLS and s[5] != 'readiness_check':
                reach[s[0]] = max(reach[s[0]], int(str(s[2] if s[2] is not None else s[3]).split('/')[0]))
    return reach


def value_of(s):
    v = s[2] if s[2] is not None else s[3]
    return int(str(v).split('/')[0])


def with_value(s, v):
    s = list(s)
    k = 2 if s[2] is not None else 3
    s[k] = f'{v}/side' if str(s[k]).endswith('/side') else v
    return s


TRAINING_KINDS = ('build_a', 'build_b', 'hold', 'trim', 'carry')


def no_double_rise(P, M, r, last, L):
    """The S&C review's rule, against the last training class of the same type: sets and dose never rise together, and
    sets rise by one (or half) at a time. A line taking the lead first adds its sets at last week's dose, then the dose."""
    if r['kind'] not in TRAINING_KINDS or r['role'] == 'recovery':
        return P, M
    role = 'front_pull' if r['role'] == 'front_onearm' else r['role']
    steps = P + M
    count = defaultdict(int)
    for s in steps:
        count[s[0]] += 1
    out = []
    for s in steps:
        key = (role, s[0])
        if count[s[0]] == 1 and s[5] not in ('assessment', 'ramp-up') and (s[2] is not None or s[3] is not None):
            if key in last:
                s0, v0 = last[key]
                if s[1] > s0:
                    s = list(s)
                    s[1] = min(s[1], max(s0 + 1, int(1.5 * s0)))
                    if value_of(s) > v0:
                        s = with_value(s, v0)
                elif value_of(s) > 1.5 * v0 and v0 >= 2:
                    # Half again at most, but one step (a palette step in Levels 1-2: 5 to 8 s) is always allowed.
                    v = max(int(1.5 * v0), step_up(s[0], v0, L) if s[0] in DOSED else 0)
                    if L <= 2:
                        v = max(x for x in (R_PAL if s[3] is None else S_PAL) if x <= v)
                    s = with_value(s, min(v, value_of(s)))
            last[key] = (s[1], value_of(s))
        out.append(s)
    return out[:len(P)], out[len(P):]


HEAVY = {'pullup': 12, 'parallel_dip': 18}


def one_heavy_set(M, L):
    """High-rep pull-ups and dips (the S&C review 6): one set a class at the dose, every other set at 75%, so the plan's
    biggest biceps and inner-elbow load comes once a class, not two or three times after the skill work. The rounds
    already run lighter."""
    out, heavy = [], {}
    for s in M:
        mid = s[0]
        if mid in HEAVY and s[5] not in ('assessment', 'ramp-up', 'controlled') and value_of(s) >= HEAVY[mid]:
            if mid in heavy:
                out.append(with_value(s, min(value_of(s), heavy[mid])))
                continue
            light = heavy[mid] = tidy(mid, max(1, int(value_of(s) * 0.75 + 0.5)), L)
            if s[1] > 1:
                out.append([s[0], 1] + list(s[2:]))
                out.append(with_value([s[0], s[1] - 1] + list(s[2:]), light))
                continue
        out.append(s)
    return out


# Review 9 (the 10- and 20-year coaches): one more muscle-up, one-arm pull-up or Hefesto is not "one step"; these are
# tested at what was trained.
NO_STEP = {'strict_muscleup', 'one_arm_pullup', 'hefesto'}


EXAM_BASICS = ('pushup', 'squat', 'pullup', 'parallel_dip')


EXAM_SHARE = 0.7


def exam_target(mid, best, L):
    """An exam checks the skill, then rests; it doesn't hunt a record (the three coaches, 2.8.1; the owner: "less
    repetition in exams, the concept is to check skill and rest", "70% is ok"): about 70% of the best trained set, but
    never under the highest belt target that set reached, because exam sets count for belts (without that floor the
    classes lose five belts and the full flag's Master); the rep basics at most their Master target, as they come last,
    after the skills."""
    v = min(best, tidy(mid, max(1, int(best * EXAM_SHARE + 0.5)), L))
    t = SKILLS.get(mid, {}).get('t')
    if not t:
        return v
    under = [x for x in t if x <= best]
    v = max(v, under[-1]) if under else best
    return min(v, t[3]) if mid in EXAM_BASICS else v


def honest_exam(M, L, n, recent, headline):
    """An exam tests what the athlete trained: each item at the belt target its best set of the last 21 classes reached
    (exam_target). Only the headline skill may go one step up, and only when that step reaches its next belt target
    (the teacher's review)."""
    out = []
    for s in M:
        if s[5] == 'assessment' and s[0] in DOSED and recent[s[0]]:
            best = max([v for q, v in recent[s[0]] if pos(L, n) - q <= 21] or [0])
            if best:
                v = exam_target(s[0], best, L)
                if s[0] == headline and s[0] not in NO_STEP and pos(L, n) - pos(*DOSED[s[0]]['start']) >= 21:
                    up = step_up(s[0], best, L)
                    hit = [t for t in SKILLS.get(s[0], {}).get('t', []) if best < t <= up]
                    v = hit[0] if hit else v
                s = with_value(s, v)
        out.append(s)
    return out


CIRCUIT_ERRORS = []


def build_once(skip):
    CIRCUIT_ERRORS.clear()
    rehearsed.clear()  # each pass rehearses from the first class again
    exam_seen.clear()
    raw = {(L, n): build_raw(L, n, {}) for L in range(1, 11) for n in range(1, 101) if kind_of(L, n) != 'exam'}
    for L in range(1, 11):
        for n in EXAMS[L]:
            raw[(L, n)] = build_raw(L, n, raw)
    raw = {(L, n): raw[(L, n)] for L in range(1, 11) for n in range(1, 101)}
    records, best = add_records(raw, skip)
    programs = []
    fitted = {}
    last = {}
    recent = defaultdict(list)
    headlined = defaultdict(set)
    met = set()  # every movement trained so far: a class's new one names it
    sigs = {}  # each class's steps: a week that would repeat one becomes a tempo week
    last_of = {}  # the last training class of each type: what rose since, for its name and text
    for L in range(1, 11):
        for n in range(1, 101):
            r = raw[(L, n)]
            warm = with_wrists([list(x) for x in WARM[r['wk']]], [s for s, _ in r['P'] + r['M']], [s for s, _ in r['M']])
            # The cool-down skips what the class already did (the teacher's review 6); compression work ends with the
            # hip flexors, hand balance with the forearms (the S&C review 6).
            done = {s[0] for s, _ in r['P'] + r['M']}
            cool = [list(x) for x in COOL[r['ck']] if x[0] not in done]
            # Each swaps in for the last stretch, so the cool-down keeps its length.
            if done & {'lsit', 'v_sit', 'dragon_flag', 'tuck_lsit', 'one_leg_lsit'} and 'hip_flexor_stretch' not in [x[0] for x in cool]:
                cool[-1:] = [['hip_flexor_stretch', 1, None, '30/side', 0, 'easy', None]]
            if done & HANDS and 'forearm_stretch' not in [x[0] for x in cool] and r['kind'] != 'recovery':
                cool[-1:] = [['forearm_stretch', 1, None, 30, 0, 'easy', None]] if cool[-1][0] != 'hip_flexor_stretch' else                     [['forearm_stretch', 1, None, 30, 10, 'easy', None], cool[-1]]
            for extra in ('child_pose', 'lat_stretch', 'quad_stretch'):
                if len(cool) < 3 and extra not in done and extra not in [x[0] for x in cool]:
                    cool.append([extra, 1, None, 45 if extra == 'child_pose' else 30, 0, 'easy', None])
            if r['kind'] != 'recovery' and r['role'] != 'recovery':
                # The main part's lead first; on an exam day the first test (the statics review).
                rec = r['kind'] != 'exam' and r['M'][0][0][5] == 'assessment'
                if r['kind'] != 'exam' and pos(3, 27) <= pos(L, n) <= pos(4, 15) and \
                        {s[0] for s, _ in r['P'] + r['M']} & {'handstand_balance', 'freestanding_handstand'}:
                    # Review 9 (the 5-year coach): the exit rehearsed before the first balance attempts.
                    warm.append(['handstand_exit', 1, '2/side', None, 30, 'controlled', None])
                if r['role'] == 'back_flag' and r['kind'] != 'exam' and pos(7, 19) <= pos(L, n) <= pos(9, 40):
                    # Review 9 (the 20-year coach): the Korean dip's starting position, taught before and beside it.
                    warm.append(['assisted_behind_bar_support', 1, None, 10, 30, 'isometric', None])
                ramp = ramp_of([s for s, _ in r['M'] + r['P']], L, n, r['kind'] == 'exam' or rec, [s for s, _ in r['P']], record=rec)
                if ramp and r['kind'] == 'exam' and r['M'][0][0][0] in EXAM_BASICS:
                    ramp = None  # a rep basic needs no ramp before its test (the three coaches, 2.8.1)
                if ramp:
                    warm.append(ramp)
                if r['kind'] != 'exam':
                    # Every straddle or full lever or flag line meets an easy set of its own before its first set (the
                    # statics review 7), not only the class's lead.
                    ramped = {k for k in ('front', 'back', 'flag') for w in warm if w[5] == 'ramp-up' and w[0] in LADDERS[k]}
                    for s, _ in r['M']:
                        line = next((k for k in ('front', 'back', 'flag') if s[0] in LADDERS[k]), None)
                        if line is None or line in ramped:
                            continue
                        ramped.add(line)
                        extra = ramp_of([s], L, n) if s[0] in HARD_SHAPES and s[5] != 'assessment' else None
                        if extra:
                            warm.append(extra)
                if r['kind'] == 'exam' and L >= 7:
                    # The levers get their own ramp set (the statics review).
                    lever = [s for s, _ in r['M'] if s[0] in LADDERS['planche'] + LADDERS['front']][:1]
                    ramp = ramp_of(lever, L, n, True) if lever else None
                    if ramp and ramp[0] != warm[-1][0]:
                        warm.append(ramp)
                if r['kind'] == 'exam':
                    # The exam's prep sets close the warm-up, in test order; a line already ramped keeps one set.
                    warm += [list(x) for x in r['prep'] if x[0] not in {w[0] for w in warm}]
            # The bar day with three lines (back lever, flag, Hefesto) may hold one elbow set more; day 6 follows the bar
            # day, so it stays under the heavy mark, as does the exam week's light bar class.
            if r['kind'] == 'barlight':
                cap = 3  # the class before an exam: one set of each tested line, two of an untested hang (review 6)
            elif r['role'] in ('basics', 'battle', 'statics_chain') or L >= 4 and n - 2 in EXAMS[L] and r['kind'] == 'build_a':
                # The first full class after an exam's recovery comes back gently too (the S&C review 6).
                cap = 4
            else:
                cap = ELBOW_CAP + 1 if r['role'] == 'back_flag' and len(bar_lines(L, n)) == 3 else ELBOW_CAP
            # Level 2's handstand and bar days are the week's two lighter classes.
            light2 = 10 if L == 2 and r['role'] in ('handstand_press', 'back_flag') else None
            if r['dtype'] == 'full_battle':
                # Every station as written: the Full Battle stands outside the class budget, as the exams' tests do.
                P, M = [list(s) for s, _ in r['P']], [list(s) for s, _ in r['M']]
            else:
                P, M = fit(r['P'], r['M'], L, r['cap'], class_seconds([warm, cool]), r['rpe'], r['kind'] == 'exam', cap, light2)
            prev = fitted.get((L, n - 7))
            if r['kind'] == 'deload' and prev is not None:
                # The lighter week keeps at most 70% of the sets of the same day a week earlier.
                limit = int(0.7 * sum(x[1] for x in prev))
                while sum(x[1] for x in M) > limit:
                    multi = [x for x in M if x[1] > 1]
                    if multi:
                        max(multi, key=lambda x: x[1])[1] -= 1
                    elif len(M) > 4:
                        M.pop()
                    else:
                        break
            ring = [s[0] for s in M if s[5] == 'circuit']
            stations = list(dict.fromkeys(ring))
            if ring and ring != stations * (len(ring) // len(stations)):
                CIRCUIT_ERRORS.append(f'L{L}:{n:03d} cuts a circuit round')
            rounds_left = len(ring) // len(stations) if stations else 0
            M = [s[:5] + [('isometric' if unit_of(s[0]) == 's' else 'controlled')] + s[6:] if s[5] == 'circuit' else s for s in M]
            fitted[(L, n)] = M
            if L >= 2:
                P, M = no_double_rise(P, M, r, last, L)
            if r['kind'] != 'exam':
                M = one_heavy_set(M, L)
            sig = (r['dtype'], tuple((s[0], s[1], str(s[2]), str(s[3]), s[5]) for s in P + M))
            if (r['kind'] in ('build_a', 'build_b') and sigs.get((L, n - 7)) == sig
                    and next(x for x in EXAMS[L] if x >= n) - n > 3):
                # Only when every set still fits: a longer class must not cost a set (the S&C review 8).
                TP, TM = tempo_steps(P), tempo_steps(M)
                if (sum(1 for s in TP + TM if s[5] == '3111') >= 2
                        and class_seconds([TP + TM]) + class_seconds([warm, cool]) <= (r['cap'] - 1) * 60):
                    P, M = TP, TM
            sigs[(L, n)] = (r['dtype'], tuple((s[0], s[1], str(s[2]), str(s[3]), s[5]) for s in P + M))
            headline = None
            if r['kind'] == 'exam':
                # The headline decides the block repeat, so it is a skill the exam is about (the teacher's review 7): a
                # named exam's own skill, else the newest belted skill tested that no earlier exam of the level named.
                tests = sorted((s for s in M if s[5] == 'assessment'), key=lambda s: -pos(*DOSED[s[0]]['start']))
                belted = [s for s in tests if s[0] in SKILLS] or tests
                headline = EXAM_HEADLINE.get((L, n))
                if headline not in {s[0] for s in tests}:
                    headline = next((s[0] for s in belted if s[0] not in headlined[L]), belted[0][0])
                headlined[L].add(headline)
                M = honest_exam(M, L, n, recent, headline)
            for x in P + M:
                if x[5] not in ('assessment', 'ramp-up') and r['kind'] != 'exam':
                    recent[x[0]].append((pos(L, n), value_of(x)))
            warm, P, M = finish(warm), finish(P, rehearse=False), finish(M)
            # Review 8: a skill line's foundation drill is named on its first class too; supporting work never is.
            new = [s[0] for s in M + P if s[5] not in ('ramp-up', 'assessment') and s[0] not in met and s[0] not in ACCESSORY
                   and (LIB[s[0]]['category'] in NOTABLE | {'skill_foundation', 'mobility_strength'} or s[0] in FIXED)]
            met.update(s[0] for s in M + P)
            minutes = min(r['cap'], max(15, math.ceil(class_seconds([warm, P, M, cool]) / 60) + 2))
            kind, role = r['kind'], r['role']
            # What rose since the last training class of this type (review 8): the name shows it, the text stops promising
            # a step that isn't there.
            prev, rose, rose_any = last_of.get(r['dtype']), [], False
            if kind in TRAINING_KINDS and role != 'recovery':
                now = defaultdict(lambda: (0, 0))
                for s in M:
                    if s[5] not in ('ramp-up', 'assessment'):
                        now[s[0]] = (now[s[0]][0] + s[1], max(now[s[0]][1], value_of(s)))
                if prev is not None:
                    for mid, (sets, v) in now.items():
                        if mid in prev and (v > prev[mid][1] or sets > prev[mid][0]):
                            rose_any = True
                            if mid not in ACCESSORY and LIB[mid]['category'] in NOTABLE and mid not in ('squat_jump', 'german_hang'):
                                rose.append(mid)
                last_of[r['dtype']] = dict(now)
            tempo = sum(1 for s in P + M if s[5] == '3111') >= 2
            # Day 6 opens with its lever sets but is named for its basics.
            rungs = {r for rs in LADDERS.values() for r in rs} if role in ('basics', 'battle') else set()
            lead = next((s for s in M if s[0] not in ('german_hang', 'easy_walk') and s[0] not in rungs), M[0])
            if kind == 'exam':
                head = EXAM_NAMES.get((L, n)) or {'exam_level_advanced_athlete': 'Level 7 Test: Advanced Athlete'}.get(r['dtype'])
                if not head:
                    head = f'Level {L} Test: {LEVEL_NAMES[L - 1]}' if n == 100 else f'Block {block_of(L, n)} Exam'
                title = head if (L, n) in EXAM_NAMES or n == 100 or r['dtype'] != 'exam_block' else f'{head} · {name(headline)}'
            elif role == 'recovery' or kind == 'recovery':
                title = f'Recovery · {RECOVERY_THEMES[(pos(L, n) // 7) % 4][0]}'
            else:
                title = class_name(kind, role, L, n, P, M, new, rose, tempo)
                if (L, n) == FULL_BATTLE:
                    title = 'The Full Battle'
                if role == 'basics' and not rounds_left:
                    title = title.replace('Basics & Endurance', 'Basics & Strength')  # no round of the basics left: no endurance part (the 10-year coach)
            if kind == 'exam':
                first = EXAM_SENTENCE[r['dtype']]
            elif (L, n) == FULL_BATTLE:
                first = ROLE_SENTENCE['full_battle']
            elif role == 'recovery' or kind == 'recovery':
                first = ROLE_SENTENCE['recovery']
            else:
                # Before the muscle-up line starts (L2:043), the pull class names no muscle-up (the teacher's review).
                says = 'front_pull_early' if role == 'front_pull' and not started('low_bar_transition', L, n) else role
                said = KIND_SENTENCE.get(kind, '')
                if tempo:
                    said = 'Same doses this week: lower every rep in 3 s.'
                elif kind == 'build_b' and not rose_any:
                    said = 'Same doses as last time: make every rep cleaner.'
                elif kind == 'build_b':
                    said = 'One step up since last time.'
                says = 'basics_strength' if says == 'basics' and not rounds_left else says
                first = (said + ' ' + ('' if kind in KIND_ONLY else ROLE_SENTENCE[says])).strip()
            # The owner (2026-10-30): a short, plain description. The exercise list shows right under it, and the move-up
            # and reserve advice lives in the class's readiness and safety notes, so neither is repeated here.
            desc = f'{block_name(L, n)}. {first}'
            if headline:
                desc += f' Headline: {name(headline)}.'
            if kind not in ('exam', 'primer') and n < 100 and n > 86 and next(x for x in EXAMS[L] if x > n) == 100:
                desc += f' {100 - n} classes to the level test.'  # review 8 (the teacher); the primer already says tomorrow
            recs = [x for x in P + M if x[5] == 'assessment'] if kind != 'exam' else []
            if recs:
                desc += (' Record set: ' + ', '.join(dose_text(x) for x in recs) +
                         ': one fresh set at the Elite target. Elite takes 30 of them.')
            focus = list(dict.fromkeys(p for s in M[:4] for p in LIB[s[0]]['patterns']))[:5]
            programs.append({
                'id': f'r9-l{L:02d}-p{n:03d}', 'origin': 'round9', 'level': L, 'number': n,
                'title': {'en': title}, 'description': {'en': desc},
                'schedule': {'week': (n - 1) // 7 + 1, 'day': rotation_day(n), 'day_type': r['dtype'], 'minutes': minutes,
                             'rpe': r['rpe'], 'focus': focus, **({'headline': headline} if headline else {})},
                'warmup': warm, 'practice': P, 'main': M, 'cooldown': cool,
            })
    return programs, records, best


def check(programs):
    """The validators' rules, checked while writing so a failure names the class."""
    import re
    errors = list(CIRCUIT_ERRORS)
    by = {(p['level'], p['number']): p for p in programs}
    run = 0
    for p in programs:
        L, n = p['level'], p['number']
        where = f'L{L}:{n:03d}'
        lim = LIMITS[L]
        allsets = sum(s[1] for s in p['practice'] + p['main'])
        if allsets > lim['main_sets_including_practice'] and p['schedule']['day_type'] != 'full_battle':
            errors.append(f'{where} has {allsets} sets, budget {lim["main_sets_including_practice"]}')
        if any(s[1] > lim['sets_per_exercise'] for s in p['practice'] + p['main']):
            errors.append(f'{where} has a step above {lim["sets_per_exercise"]} sets')
        if len(p['main']) < 4 or len(p['warmup']) < 3 or len(p['cooldown']) < 3:
            errors.append(f'{where} has too few steps')
        sec = class_seconds([p['warmup'], p['practice'], p['main'], p['cooldown']])
        if sec > p['schedule']['minutes'] * 60:
            errors.append(f'{where} needs {sec / 60:.1f} min > {p["schedule"]["minutes"]}')
        if p['schedule']['minutes'] > (60 if p['schedule']['day_type'].startswith('exam') else 55):
            errors.append(f'{where} is over its minutes')
        adv = sum(s[1] for s in p['main'] if LIB[s[0]]['category'] in ('advanced_skill', 'mastery_skill', 'advanced_strength', 'skill_strength'))
        if sum(s[1] for s in p['main']) > 20 or (p['schedule']['rpe'] >= 8 and adv > 10):
            errors.append(f'{where} main sets {sum(s[1] for s in p["main"])}, advanced {adv}')
        if not p['schedule']['day_type'].startswith('exam'):
            el = sum(s[1] for s in p['practice'] + p['main'] if s[0] in ELBOW)
            if el > ELBOW_CAP + (1 if p['schedule']['day_type'].endswith('back_flag') else 0):
                errors.append(f'{where} has {el} elbow-load sets')
        for s in p['practice'] + p['main']:
            if s[3] is not None and s[0] in DOSED:
                v = int(str(s[3]).split('/')[0])
                if v < (3 if s[0] in MIN3 else 5):
                    errors.append(f'{where} holds {s[0]} for {v} s')
        text = ' '.join([p['title']['en'], p['description']['en'], p['schedule']['day_type']] + [str(s[5]) for s in p['warmup'] + p['main'] + p['cooldown']])
        if re.search(FORBIDDEN, text):
            errors.append(f'{where} uses choice wording')
        rpe = p['schedule']['rpe']
        if re.search(r'(?i)(recovery|restore|reset)', p['schedule']['day_type']) and rpe > 4:
            errors.append(f'{where} recovery-like at RPE {rpe}')
        run = run + 1 if rpe >= 7 else 0
        if run >= 4:
            errors.append(f'{where} ends a run of 4 classes at RPE 7+')
    for L in range(1, 11):
        for s0 in range(1, 95):
            window = [by[(L, n)] for n in range(s0, s0 + 7)]
            tt = ' '.join(tokens(p) for p in window)
            for k, rx in TOKENS.items():
                if not re.search(rx, tt):
                    errors.append(f'L{L}:{s0:03d}-{s0 + 6:03d} misses {k}')
            if L >= 3:
                for k, rx in GOALS.items():
                    if not re.search(rx, tt):
                        errors.append(f'L{L}:{s0:03d}-{s0 + 6:03d} misses {k}')
            if not any(re.search(r'(?i)(recovery|restore|reset)', p['schedule']['day_type']) and p['schedule']['rpe'] <= 4 for p in window):
                errors.append(f'L{L}:{s0:03d}-{s0 + 6:03d} has no recovery day')
    elbow_days = [sum(s[1] for s in p['practice'] + p['main'] if s[0] in ELBOW) >= 5 for p in programs]
    for i in range(1, len(programs)):
        if elbow_days[i] and elbow_days[i - 1]:
            a, b = programs[i - 1], programs[i]
            if not (a['schedule']['day_type'].startswith('exam') or b['schedule']['day_type'].startswith('exam')):
                errors.append(f'L{b["level"]}:{b["number"]:03d} follows a heavy elbow class')
    landed, _, _ = belts(programs)
    reach = best_sets(programs)
    for p in programs:
        ids = {s[0] for s in p['practice'] + p['main']}
        for s in p['practice'] + p['main']:
            # A progression peaks while it is trained: never a record set beside a harder rung of its ladder.
            if s[5] == 'assessment' and not p['schedule']['day_type'].startswith('exam'):
                for rungs in LADDERS.values():
                    if s[0] in rungs and ids & set(rungs[rungs.index(s[0]) + 1:]):
                        errors.append(f'L{p["level"]}:{p["number"]:03d} has a {s[0]} record beside a harder rung')
    for sid, sk in SKILLS.items():
        if reach[sid] < sk['t'][4] and not landed[sid][4]:
            errors.append(f'{sid} never reaches its Elite target {sk["t"][4]} (best {reach[sid]})')
    # Review 11 (the owner): every belt to Master inside the program; Elite at least 70% of the way, the top rungs 70-90%.
    landed, count, _ = belts(programs)
    for sid in SKILLS:
        if not landed[sid][3]:
            errors.append(f'{sid} Master does not land inside the program')
        if sid in ELITE_FULL:
            if not landed[sid][4]:
                errors.append(f'{sid} Elite does not land inside the program')
        elif sid in ELITE_FROM and not ELITE_SHARE[0] <= count[sid][4] <= ELITE_SHARE[1]:
            errors.append(f'{sid} gives {count[sid][4]} of 30 Elite sets, not {ELITE_SHARE[0]}-{ELITE_SHARE[1]}')
        elif sid in ELITE_FROM and landed[sid][4]:
            errors.append(f'{sid} completes Elite inside the program')
        elif not landed[sid][4] and count[sid][4] < ELITE_SHARE[0]:
            errors.append(f'{sid} Elite is {count[sid][4]} of 30 sets, under {ELITE_SHARE[0]}')
    return errors


# The easier rungs a set counts for: same ladder, same measure (review 11).
EASIER = {sid: [e for e in rungs[:i] if SKILLS[e]['unit'] == SKILLS[sid]['unit']] for rungs in BELT_LADDERS for i, sid in enumerate(rungs)}


def belts(programs):
    """Where each belt lands if every set is done as written on the Normal toggle, as the app counts it: the first class
    whose running count of sets at or above the target reaches 10/15/20/25/30, the belt below already held, or the
    first class where a harder rung of its ladder holds that belt (`via` names that rung). A set of a harder rung counts
    for the easier rungs of its ladder too, at the belt it reaches on its own rung, when both are measured alike (review
    11): a 6 s full planche is an Elite set of the straddle planche. Practice and main count; warm-up, cool-down and
    readiness checks do not."""
    count = {sid: [0] * 5 for sid in SKILLS}
    landed = {sid: [None] * 5 for sid in SKILLS}
    via = {sid: [None] * 5 for sid in SKILLS}
    for p in programs:
        for s in p['practice'] + p['main']:
            sid = s[0]
            if sid not in SKILLS or s[5] == 'readiness_check':
                continue
            v = int(str(s[2] if s[2] is not None else s[3]).split('/')[0])
            for k, t in enumerate(SKILLS[sid]['t']):
                if v >= t:
                    for x in [sid] + EASIER[sid]:
                        count[x][k] += s[1]
            for x in [sid] + EASIER[sid]:
                for k in range(5):
                    if landed[x][k] is None and count[x][k] >= BELT_SETS[k] and (k == 0 or landed[x][k - 1] is not None):
                        landed[x][k] = (p['level'], p['number'])
    own = {sid: list(v) for sid, v in landed.items()}
    for rungs in BELT_LADDERS:
        for i, sid in enumerate(rungs):
            for k in range(5):
                for harder in rungs[i + 1:]:
                    x = own[harder][k]
                    if x and (landed[sid][k] is None or pos(*x) < pos(*landed[sid][k])):
                        landed[sid][k], via[sid][k] = x, harder
    return landed, count, via


def main():
    programs, records, best = build()
    errors = check(programs)
    landed, count, via = belts(programs)
    print('records in Level 10:', {k: v for k, v in records.items()})
    inplan = sum(1 for v in landed.values() for x in v if x)
    per_level = defaultdict(int)
    for v in landed.values():
        for x in v:
            if x:
                per_level[x[0]] += 1
    for sid, v in landed.items():
        # A * marks a belt held through a harder rung of the ladder.
        print('%-24s %-16s %s' % (sid, '/'.join(map(str, SKILLS[sid]['t'])),
                                  ' '.join('%9s' % ('L%d:%03d%s' % (x + ('*' if h else '',)) if x else 'own') for x, h in zip(v, via[sid]))))
    print('belts in plan', inplan, 'of', 5 * len(SKILLS), 'per level', [per_level[L] for L in range(1, 11)])
    print('Elite sets of 30:', {sid: count[sid][4] for sid in SKILLS if not landed[sid][4]})
    print(len(errors), 'check errors')
    for e in errors[:40]:
        print('  ', e)
    out = {'schema_version': 'round11', 'generated_by': 'tools/build_curriculum.py',
           'step_tuple': ['movement_id', 'sets', 'reps', 'seconds', 'rest_seconds', 'tempo', 'regression_id'], 'programs': programs}
    (ROOT / 'curriculum/program.json').write_text(json.dumps(out, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    if len(sys.argv) > 2 and sys.argv[1] == '--report':
        Path(sys.argv[2]).write_text(json.dumps({
            'landed': {k: ['L%d:%03d' % x if x else None for x in v] for k, v in landed.items()}, 'via': via,
            'targets': {k: SKILLS[k]['t'] for k in SKILLS}, 'records': records, 'best': best,
            'debut': {k: 'L%d:%03d' % SKILLS[k]['start'] for k in SKILLS},
            'doses': {k: {L: [dose(k, L, e) for e in EXAMS[L]] for L in range(1, 11) if base_dose(k, L, 100) is not None and started(k, L, 100)} for k in SKILLS},
            'per_level': per_level, 'errors': errors}, indent=1), encoding='utf-8')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
