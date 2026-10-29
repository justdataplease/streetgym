"""Round 9: writes all 1,000 classes of the Street Gymnastics program from one plan (curriculum/program.json).

The plan is docs/CURRICULUM_PLAN.md: circuits in Levels 1-2, a fixed skill week from Level 3, the full shapes in
Levels 8-9 and records in Level 10, on today's frozen exam days. Everything here is deterministic: the same plan gives
the same classes. Run it, then tools/assemble_curriculum.ps1 and the validators.

    python tools/build_curriculum.py            # writes curriculum/program.json and prints the belt landings
    python tools/build_curriculum.py --report X # also writes the belt and dose report as JSON to X

Day ids are frozen (DayIdsTest): a class is identified by its level and number, so this rewrites classes in place.
"""
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
BELT_SETS = [10, 20, 30, 30, 30]
BELTS = ['Beginner', 'Intermediate', 'Advanced', 'Master', 'Elite']
REP_CAP = {'pushup': 50, 'squat': 40, 'parallel_dip': 30, 'pullup': 21, 'hanging_leg_raise': 15, 'toes_to_bar': 15}

# ----- skills: the 37 belted skills, their targets and their dose path -----
# unit: 'r' reps or 's' seconds; side: per side; start: (level, class) of the first counted set; d0: first dose;
# ends: the working dose at the end of each level (Normal toggle). Between levels the dose rises block by block.
SKILLS = {}


def skill(sid, targets, unit, start, d0, ends, side=False):
    SKILLS[sid] = dict(t=targets, unit=unit, start=start, d0=d0, ends=ends, side=side)


skill('pushup', [6, 12, 20, 30, 40], 'r', (1, 2), 6, {1: 15, 2: 20, 3: 22, 4: 25, 5: 30, 6: 32, 7: 35, 8: 40, 9: 45, 10: 50})
skill('squat', [15, 20, 25, 30, 40], 'r', (1, 1), 15, {1: 20, 2: 30, 3: 35, 4: 40, 5: 40, 6: 40, 7: 40, 8: 40, 9: 40, 10: 40})
skill('hollow_body_hold', [20, 30, 40, 50, 60], 's', (1, 1), 20, {1: 30, 2: 45, 3: 55, 4: 60, 5: 60, 6: 60, 7: 60, 8: 60, 9: 60, 10: 60})
skill('dead_hang', [30, 45, 60, 75, 90], 's', (1, 1), 20, {1: 50, 2: 70, 3: 80, 4: 90, 5: 90, 6: 90, 7: 90, 8: 90, 9: 90, 10: 90})
skill('pullup', [3, 6, 10, 15, 20], 'r', (1, 43), 1, None)
skill('parallel_dip', [4, 10, 15, 20, 25], 'r', (1, 64), 3, {1: 6, 2: 9, 3: 12, 4: 14, 5: 16, 6: 18, 7: 20, 8: 23, 9: 26, 10: 28})
skill('pike_pushup', [6, 8, 10, 12, 15], 'r', (1, 44), 4, {1: 6, 2: 10, 3: 13, 4: 15, 5: 15, 6: 15, 7: 15, 8: 15, 9: 15, 10: 15})
skill('wall_handstand', [20, 30, 45, 60, 75], 's', (1, 26), 10, {1: 25, 2: 40, 3: 50, 4: 60, 5: 75, 6: 75, 7: 75, 8: 75, 9: 75, 10: 75})
skill('hanging_leg_raise', [5, 8, 10, 12, 15], 'r', (2, 25), 3, {2: 6, 3: 9, 4: 12, 5: 15, 6: 15, 7: 15, 8: 15, 9: 15, 10: 15})
skill('tuck_front_hold', [5, 8, 12, 16, 20], 's', (2, 43), 3, {2: 7, 3: 12, 4: 16, 5: 20, 6: 20, 7: 20, 8: 20, 9: 20, 10: 20})
skill('pseudo_planche_pushup', [5, 8, 10, 12, 15], 'r', (3, 2), 5, {3: 7, 4: 9, 5: 10, 6: 12, 7: 13, 8: 15, 9: 15, 10: 15})
skill('tuck_back_hold', [5, 8, 12, 16, 20], 's', (3, 11), 5, {3: 12, 4: 16, 5: 20, 6: 20, 7: 20, 8: 20, 9: 20, 10: 20})
skill('lsit', [5, 10, 15, 20, 30], 's', (3, 23), 4, {3: 8, 4: 12, 5: 15, 6: 18, 7: 20, 8: 30, 9: 30, 10: 30})
skill('tuck_planche', [5, 8, 10, 12, 15], 's', (4, 2), 3, {4: 8, 5: 10, 6: 13, 7: 15, 8: 15, 9: 15, 10: 15})
skill('pistol_squat', [2, 4, 6, 8, 10], 'r', (4, 3), 1, {4: 3, 5: 5, 6: 6, 7: 8, 8: 9, 9: 10, 10: 10}, side=True)
skill('freestanding_handstand', [5, 10, 20, 30, 45], 's', (4, 5), 3, {4: 6, 5: 10, 6: 15, 7: 20, 8: 25, 9: 30, 10: 45})
skill('advanced_tuck_front', [5, 8, 10, 12, 15], 's', (4, 8), 3, {4: 5, 5: 8, 6: 10, 7: 15, 8: 15, 9: 15, 10: 15})
skill('toes_to_bar', [3, 6, 8, 12, 15], 'r', (4, 15), 2, {4: 3, 5: 5, 6: 7, 7: 9, 8: 12, 9: 15, 10: 15})
skill('strict_muscleup', [1, 2, 3, 4, 5], 'r', (4, 39), 1, {4: 1, 5: 2, 6: 2, 7: 3, 8: 4, 9: 5, 10: 5})
skill('advanced_tuck_back', [5, 8, 10, 15, 20], 's', (4, 60), 4, {4: 8, 5: 12, 6: 16, 7: 20, 8: 20, 9: 20, 10: 20})
skill('wall_hspu', [2, 4, 6, 8, 10], 'r', (4, 61), 1, {4: 2, 5: 4, 6: 5, 7: 7, 8: 8, 9: 10, 10: 10})
skill('tuck_flag', [5, 8, 10, 12, 15], 's', (5, 5), 3, {5: 8, 6: 15, 7: 15, 8: 15, 9: 15, 10: 15}, side=True)
skill('archer_pullup', [2, 3, 4, 6, 8], 'r', (5, 8), 1, {5: 2, 6: 3, 7: 4, 8: 6, 9: 8, 10: 8}, side=True)
skill('straddle_back', [3, 5, 8, 10, 12], 's', (5, 81), 3, {5: 4, 6: 7, 7: 10, 8: 12, 9: 12, 10: 12})
skill('advanced_tuck_planche', [3, 5, 8, 10, 12], 's', (6, 2), 2, {6: 5, 7: 8, 8: 12, 9: 12, 10: 12})
skill('dragon_flag', [1, 3, 5, 6, 8], 'r', (6, 17), 1, {6: 2, 7: 4, 8: 6, 9: 8, 10: 8})
skill('straddle_flag', [2, 4, 6, 8, 10], 's', (6, 61), 2, {6: 2, 7: 4, 8: 6, 9: 8, 10: 10}, side=True)
skill('one_arm_pushup', [1, 3, 5, 7, 10], 'r', (7, 2), 1, {7: 2, 8: 4, 9: 6, 10: 10}, side=True)
skill('full_back', [3, 5, 8, 10, 12], 's', (6, 46), 2, {6: 3, 7: 6, 8: 9, 9: 11, 10: 12})
skill('straddle_front', [3, 5, 6, 8, 10], 's', (7, 15), 2, {7: 4, 8: 6, 9: 8, 10: 10})
skill('v_sit', [2, 3, 4, 5, 6], 's', (8, 3), 1, {8: 3, 9: 5, 10: 6})
skill('deficit_wall_hspu', [2, 3, 5, 6, 8], 'r', (8, 5), 1, {8: 3, 9: 5, 10: 8})
skill('full_front', [2, 3, 4, 6, 8], 's', (8, 15), 1, {8: 2, 9: 4, 10: 8})
skill('straddle_planche', [2, 3, 4, 5, 6], 's', (8, 37), 1, {8: 2, 9: 4, 10: 6})
skill('full_flag', [2, 3, 4, 5, 6], 's', (7, 61), 1, {7: 2, 8: 3, 9: 5, 10: 6}, side=True)
skill('one_arm_pullup', [1, 1, 1, 2, 3], 'r', (9, 15), 1, {9: 1, 10: 2}, side=True)
skill('full_planche', [2, 2, 2, 3, 4], 's', (9, 16), 1, {9: 2, 10: 3})
assert len(SKILLS) == 37, len(SKILLS)

# Progression movements (no belt) that the classes also dose, start (level, class), first dose and level ends.
PROG = {}


def prog(mid, unit, start, d0, ends, side=False):
    PROG[mid] = dict(unit=unit, start=start, d0=d0, ends=ends, side=side)


prog('incline_pushup', 'r', (1, 1), 6, {1: 12})
prog('inverted_row', 'r', (1, 1), 5, {1: 10, 2: 12, 3: 12, 4: 12, 5: 12, 6: 12, 7: 12, 8: 12, 9: 12, 10: 12})
prog('eccentric_pullup', 'r', (1, 22), 2, {1: 3, 2: 4})
prog('support_hold', 's', (1, 1), 10, {1: 25, 2: 30})
prog('assisted_dip', 'r', (1, 22), 4, {1: 8})
prog('eccentric_dip', 'r', (1, 43), 3, {1: 4})
prog('glute_bridge', 'r', (1, 1), 10, {1: 15, 2: 15, 3: 15, 4: 15, 5: 15, 6: 15, 7: 15, 8: 15, 9: 15, 10: 15})
prog('single_leg_bridge', 'r', (2, 1), 6, {2: 10, 3: 12, 4: 12, 5: 12, 6: 12, 7: 12, 8: 12, 9: 12, 10: 12}, side=True)
prog('single_leg_rdl', 'r', (3, 1), 6, {3: 8, 4: 10, 5: 10, 6: 12, 7: 12, 8: 12, 9: 12, 10: 12}, side=True)
prog('split_squat', 'r', (1, 8), 6, {1: 8, 2: 10, 3: 10}, side=True)
prog('bulgarian_split_squat', 'r', (3, 1), 6, {3: 8, 4: 10, 5: 10, 6: 10, 7: 10, 8: 10, 9: 10, 10: 10}, side=True)
prog('assisted_pistol_squat', 'r', (2, 66), 3, {2: 4, 3: 6})
prog('side_plank', 's', (1, 1), 15, {1: 30, 2: 40}, side=True)
prog('straight_side_plank', 's', (3, 1), 20, {3: 30, 4: 40, 5: 45, 6: 45, 7: 45, 8: 45, 9: 45, 10: 45}, side=True)
prog('knee_raise', 'r', (1, 1), 6, {1: 10, 2: 12})
prog('plank', 's', (1, 1), 20, {1: 40, 2: 45})
prog('pike_handstand_hold', 's', (1, 1), 15, {1: 30, 2: 30})
prog('arch_hold', 's', (1, 1), 15, {1: 25, 2: 30, 3: 30, 4: 30, 5: 30, 6: 30, 7: 30, 8: 30, 9: 30, 10: 30})
prog('tuck_lsit', 's', (2, 1), 5, {2: 12})
prog('one_leg_lsit', 's', (2, 81), 5, {2: 6, 3: 8}, side=True)
prog('tuck_front_row', 'r', (2, 22), 4, {2: 6})
prog('handstand_exit', 'r', (2, 26), 3, {2: 4})
prog('wall_kickup', 'r', (2, 26), 3, {2: 4, 3: 4})
prog('handstand_balance', 's', (3, 26), 8, {3: 12, 4: 15})
prog('planche_lean', 's', (2, 65), 10, {2: 15, 3: 20, 4: 20, 5: 20, 6: 20, 7: 20, 8: 20, 9: 20, 10: 20})
prog('frog_stand', 's', (2, 65), 8, {2: 12, 3: 20})
prog('elevated_plank', 's', (1, 1), 20, {1: 30, 2: 30, 3: 30})
prog('skin_cat_partial', 'r', (2, 60), 2, {2: 3, 3: 3})
prog('german_hang', 's', (2, 67), 8, {2: 12, 3: 15, 4: 20, 5: 20, 6: 25, 7: 30, 8: 30, 9: 30, 10: 30})
prog('straight_bar_support', 's', (2, 1), 15, {2: 30, 3: 30})
prog('low_bar_transition', 'r', (2, 40), 3, {2: 5, 3: 5})
prog('chest_to_bar', 'r', (3, 46), 2, {3: 3, 4: 5, 5: 6})
prog('band_muscleup', 'r', (3, 46), 2, {3: 3, 4: 3})
prog('negative_muscleup', 'r', (3, 67), 2, {3: 3, 4: 3})
prog('candlestick', 'r', (2, 1), 5, {2: 8, 3: 8})
prog('bent_dragon_negative', 'r', (3, 46), 2, {3: 4, 4: 4})
prog('one_leg_dragon_negative', 'r', (4, 59), 2, {4: 3, 5: 4}, side=True)
prog('full_dragon_negative', 'r', (5, 38), 2, {5: 3, 6: 4})
prog('wall_hspu_negative', 'r', (3, 47), 2, {3: 3, 4: 4})
prog('elevated_pike_pushup', 'r', (2, 1), 5, {2: 8, 3: 10, 4: 12, 5: 12, 6: 12, 7: 12, 8: 12, 9: 12, 10: 12})
prog('flag_ground_support', 's', (3, 5), 10, {3: 15}, side=True)
prog('assisted_tuck_flag', 's', (3, 47), 5, {3: 8, 4: 10}, side=True)
prog('nordic_negative', 'r', (4, 17), 3, {4: 4, 5: 5, 6: 5, 7: 5, 8: 5, 9: 5, 10: 5})
prog('nordic_curl', 'r', (7, 17), 1, {7: 1, 8: 2, 9: 3, 10: 4})
prog('archer_pushup', 'r', (4, 37), 3, {4: 4, 5: 6, 6: 8}, side=True)
prog('assisted_one_arm_pullup', 'r', (7, 36), 1, {7: 2, 8: 3}, side=True)
prog('one_arm_pullup_negative', 'r', (8, 36), 1, {8: 2, 9: 3}, side=True)
prog('freestanding_hspu_negative', 'r', (10, 26), 1, {10: 3})
prog('pancake_compression', 'r', (3, 1), 8, {3: 10, 4: 12, 5: 12, 6: 12, 7: 12, 8: 12, 9: 12, 10: 12})
prog('seated_pike_lift', 'r', (2, 1), 6, {2: 10, 3: 12, 4: 15, 5: 15, 6: 15, 7: 15, 8: 15, 9: 15, 10: 15})
prog('calf_raise', 'r', (1, 1), 12, {1: 15, 2: 20, 3: 20, 4: 20, 5: 20, 6: 20, 7: 20, 8: 20, 9: 20, 10: 20})
prog('dead_bug', 'r', (1, 1), 6, {1: 10, 2: 10, 3: 10, 4: 10, 5: 10, 6: 10, 7: 10, 8: 10, 9: 10, 10: 10}, side=True)
DOSED = {**SKILLS, **PROG}
for _mid in DOSED:
    assert _mid in LIB, _mid

# The ladders: rungs in order. A class trains the hardest rung it has reached as its top sets and keeps two back-off
# sets on the rung below, so the lower rungs keep earning.
LADDERS = {
    'front': ['tuck_front_hold', 'advanced_tuck_front', 'straddle_front', 'full_front'],
    'back': ['tuck_back_hold', 'advanced_tuck_back', 'straddle_back', 'full_back'],
    'planche': ['tuck_planche', 'advanced_tuck_planche', 'straddle_planche', 'full_planche'],
    'flag': ['tuck_flag', 'straddle_flag', 'full_flag'],
}

# The app's belt ladders (SkillBook.DEFAULT_FAMILIES), easiest rung first. A level held on a harder rung is held on
# every easier rung of its ladder too (round 10): a full back lever at Elite makes the tuck back lever Elite.
BELT_LADDERS = [
    ['dead_hang', 'pullup', 'archer_pullup', 'one_arm_pullup'], ['strict_muscleup'],
    ['tuck_front_hold', 'advanced_tuck_front', 'straddle_front', 'full_front'],
    ['tuck_back_hold', 'advanced_tuck_back', 'straddle_back', 'full_back'], ['parallel_dip'],
    ['pushup', 'one_arm_pushup'], ['pike_pushup', 'wall_hspu', 'deficit_wall_hspu'],
    ['pseudo_planche_pushup', 'tuck_planche', 'advanced_tuck_planche', 'straddle_planche', 'full_planche'],
    ['wall_handstand', 'freestanding_handstand'], ['squat', 'pistol_squat'], ['hanging_leg_raise', 'toes_to_bar'],
    ['lsit', 'v_sit'], ['hollow_body_hold', 'dragon_flag'], ['tuck_flag', 'straddle_flag', 'full_flag'],
]
assert sorted(r for rungs in BELT_LADDERS for r in rungs) == sorted(SKILLS)


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


def dose(mid, L, n, step=0):
    """The working dose of a movement at class (L, n), plus `step` steps (a week-B step-up is +1)."""
    s = DOSED[mid]
    b = block_of(L, n)
    if mid == 'pullup':
        if L == 1:
            d = 1 if b <= 3 else 2 + (b - 4)
        else:
            d = max(1, 2 * L - 1 + (b - 1) // 2)
    else:
        ends = s['ends']
        sL, sn = s['start']
        last = max(k for k in ends if k <= L) if any(k <= L for k in ends) else None
        if last is None:
            return None
        if L not in ends:
            d = ends[last]
        elif L == sL:
            sb = block_of(sL, sn)
            d = ends[L] if sb == 5 else s['d0'] + (ends[L] - s['d0']) * (b - sb) / (5 - sb)
        else:
            prev = ends.get(L - 1, s['d0'])
            d = prev + (ends[L] - prev) * (b - 1) / 4
    d = int(round(d))
    if step:
        # Week B steps up, but never past the next block's dose: a dose never falls from one week to the next.
        nxt = next_block_dose(mid, L, n)
        d = min(d + step * (1 if s['unit'] == 'r' or d < 12 else 2), max(d, nxt if nxt is not None else d + 1))
    return max(1, min(d, REP_CAP.get(mid, 10 ** 6)))


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
    return rungs[-1], (rungs[-2] if len(rungs) > 1 else None)


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


PER_SIDE = {'ankle_rocks', 'bird_dog', 'cossack_squat', 'dead_bug', 'leg_swings', 'reverse_lunge', 'single_leg_bridge', 'split_squat',
            'bulgarian_split_squat', 'single_leg_rdl', 'archer_pullup', 'archer_pushup', 'typewriter_pullup', 'assisted_one_arm_pullup',
            'one_arm_pullup_negative', 'thoracic_rotation', 'side_plank', 'straight_side_plank', 'one_leg_dragon_negative'}


def amount(mid, value):
    side = DOSED[mid]['side'] if mid in DOSED else mid in PER_SIDE
    if isinstance(value, str):
        return value
    return f'{value}/side' if side else value


def st(mid, sets, value=None, rest=90, tempo=None, frac=1.0, ctx=None, step=0):
    """One step tuple. value None = the movement's working dose at ctx, scaled by frac."""
    if value is None:
        L, n = ctx
        d = dose(mid, L, n, step)
        assert d is not None, (mid, L, n)
        value = max(1, int(math.floor(d * frac + 0.5)) if frac != 1.0 else d)
    unit = DOSED[mid]['unit'] if mid in DOSED else ('s' if isinstance(value, int) and value >= 15 else 'r')
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


def nsets(steps):
    return sum(s[1] for s in steps)


WARM = {
    'base': [['march', 1, None, 90, 0, 'brisk', None], ['arm_swings', 1, 10, None, 10, 'controlled', 'shoulder_cars'],
             ['hip_circles', 1, 8, None, 10, 'controlled', None], ['cat_cow', 1, 6, None, 10, 'slow', None]],
    'wrist': [['wrist_circles', 1, 10, None, 10, 'controlled', None], ['wrist_pushup', 1, 10, None, 20, 'slow', 'wrist_rocks']],
    'pull': [['scapular_pull', 2, 6, None, 30, '2111', None]],
    'push': [['scapular_pushup', 2, 8, None, 25, '2111', None]],
    'shoulder': [['prone_ytw', 1, 6, None, 20, 'slow', 'wall_slide'], ['wall_shoulder_flexion', 1, None, 30, 20, 'controlled', 'wall_slide']],
    'legs': [['ankle_rocks', 1, '10/side', None, 10, 'controlled', None], ['leg_swings', 1, '8/side', None, 10, 'controlled', 'hip_circles']],
}
COOL = {
    'pull': [['lat_stretch', 2, None, 30, 10, 'easy', None], ['forearm_stretch', 1, None, 30, 10, 'easy', None], ['child_pose', 1, None, 45, 0, 'easy', None]],
    'push': [['chest_stretch', 2, None, 30, 10, 'easy', None], ['forearm_stretch', 2, None, 30, 10, 'easy', None], ['child_pose', 1, None, 45, 0, 'easy', None]],
    'legs': [['quad_stretch', 2, None, 30, 10, 'easy', None], ['hamstring_stretch', 2, None, 30, 10, 'easy', None], ['ninety_ninety_hip_stretch', 1, None, 45, 0, 'easy', None]],
    'all': [['lat_stretch', 1, None, 30, 10, 'easy', None], ['chest_stretch', 1, None, 30, 10, 'easy', None], ['hip_flexor_stretch', 2, None, 30, 10, 'easy', None]],
    'easy': [['supine_twist', 2, None, 30, 10, 'easy', None], ['hip_flexor_stretch', 2, None, 30, 10, 'easy', None], ['slow_breathing', 1, None, 120, 0, 'easy', None]],
}


def warmup(kind, L):
    w = list(WARM['base'])
    if 'wrist' in kind:
        w += WARM['wrist']
    if 'pull' in kind:
        w += WARM['pull']
    if 'push' in kind:
        w += WARM['push']
    if 'shoulder' in kind or (L >= 8 and ('pull' in kind or 'push' in kind)):
        w += WARM['shoulder']
    if 'legs' in kind:
        w += WARM['legs']
    return [list(x) for x in w]


# ----- the calendar -----
def kind_of(L, n):
    """What class (L, n) is: exam, re-entry, build week A/B, deload, recovery, primer, light, easy."""
    ex = EXAMS[L]
    if n in ex:
        return 'exam'
    b = block_of(L, n)
    s = ex[b - 2] + 1 if b > 1 else 1  # first class of the block
    e = ex[b - 1]
    if L >= 4:
        if n == s:
            return 'reentry'
        if b == 1:
            return {11: 'light', 12: 'primer'}.get(n, 'build_a')
        if b == 5:
            if n <= 84:
                return 'build_a'
            if n <= 91:
                return 'build_b'
            return {92: 'deload', 93: 'deload', 94: 'deload', 95: 'light', 96: 'light', 97: 'easy', 98: 'recovery', 99: 'primer'}[n]
        off = n - s
        if off <= 7:
            return 'build_a'
        if off <= 14:
            return 'build_b'
        return {15: 'deload', 16: 'deload', 17: 'deload', 18: 'light', 19: 'primer'}[off]
    if n == s and (n > 1 or L > 1):
        return 'reentry'
    if n == e - 1:
        return 'primer'
    if n == e - 2:
        return 'easy'
    if n == e - 3:
        return 'deload'
    return 'build_a' if n - s < 8 else 'build_b'


def weekday(n):
    return (n - 1) % 7 + 1


def role_of(L, n):
    d = weekday(n)
    early = {1: 'circuit_a', 2: 'practice_push', 3: 'circuit_b', 4: 'practice_bar', 5: 'easy_line', 6: 'circuit_c', 7: 'recovery'}
    if L == 1:
        return early[d]
    if L == 2:
        b = block_of(L, n)
        if b >= 3:
            early[1] = 'front_pull'
        if b >= 4:
            early[2], early[4] = 'planche_push', 'back_bar'
        if b >= 5:
            early[5], early[6] = 'handstand_press', 'basics'
        return early[d]
    if L <= 7:
        return {1: 'front_pull', 2: 'planche_push', 3: 'legs_core', 4: 'back_bar', 5: 'handstand_flag_press', 6: 'basics', 7: 'recovery'}[d]
    if d == 6:
        return 'battle' if L == 8 or kind_of(L, n) == 'build_b' else 'statics_chain'
    if d == 5 and n < 100 and role_of(L, n + 1) == 'statics_chain':
        return 'handstand_press'  # the chain tomorrow holds the flag
    return {1: 'front_onearm', 2: 'planche_push', 3: 'legs_core', 4: 'back_muscleup', 5: 'handstand_flag_press', 7: 'recovery'}[d]


ROLE_TITLE = {
    'circuit_a': 'Circuit A, Pull & Push', 'practice_push': 'Push Line & Wrists', 'circuit_b': 'Circuit B, Legs & Core',
    'practice_bar': 'Bar Line', 'easy_line': 'Easy Handstand Line', 'circuit_c': 'Circuit C, Engine',
    'recovery': 'Recovery', 'front_pull': 'Front Lever & Pull', 'planche_push': 'Planche & Push', 'legs_core': 'Legs & Core',
    'back_bar': 'Back Lever & Bar', 'handstand_flag_press': 'Handstand, Flag & Press', 'handstand_press': 'Handstand & Press',
    'basics': 'Basics Circuit', 'front_onearm': 'Front Lever & One-Arm', 'back_muscleup': 'Back Lever & Muscle-Up',
    'battle': 'Battle Day', 'statics_chain': 'Statics Chain',
}
ROLE_SENTENCE = {
    'circuit_a': 'A full-body circuit: each station is one set, then the round repeats.',
    'practice_push': 'An easy practice class for the push line: straight-arm support, the pike and the wrists.',
    'circuit_b': 'A legs, hips and trunk circuit: each station is one set, then the round repeats.',
    'practice_bar': 'An easy practice class on the bar: hangs, shoulder-blade control and slow lowering.',
    'easy_line': 'An easy class: the handstand line, wrists and trunk, all with reserve.',
    'circuit_c': 'The engine circuit: five stations of the basics, one set each, round after round.',
    'recovery': 'An easy recovery class: walking, gentle trunk work and stretching. Finish fresher than you started.',
    'front_pull': 'The front-lever day: the lever ladder first while the shoulders are fresh, then the pulling.',
    'planche_push': 'The planche day: a short handstand, the planche ladder, then the pushing.',
    'legs_core': 'Legs and trunk only, a day off for the arms: single-leg strength, the hinge and compression.',
    'back_bar': 'The bar day: the strict muscle-up first, the German hang, then the back-lever ladder.',
    'handstand_flag_press': 'The handstand block, the flag ladder and the overhead press.',
    'handstand_press': 'The handstand block and the overhead press.',
    'basics': 'The basics day: opening sets at the dose, then rounds of the basics at a lighter dose.',
    'front_onearm': 'The front-lever day: the lever ladder, the one-arm pulling path, then the pull-ups.',
    'back_muscleup': 'The bar day: muscle-up sets at a steady pace, the German hang, then the back-lever ladder.',
    'battle': 'The Battle: the basics at fixed rests, every set as written, no racing the clock.',
    'statics_chain': 'The Statics Chain: one run through the shapes with short transitions, holds kept short.',
}


# Recovery classes rotate their mobility so no two are the same class.
_MOB = [('cat_cow', 8), ('thoracic_rotation', 8), ('deep_squat_hold', 30), ('ninety_ninety_hip_switch', 8), ('wall_slide', 10),
        ('thoracic_extension', 8), ('wall_shoulder_flexion', 30), ('bird_dog', 8), ('dead_bug', 8)]
import itertools as _it
MOBILITY_TRIOS = list(_it.combinations(_MOB, 3))
RECOVERY_INDEX = {}


# ----- class builders: each returns (practice, main) as lists of (step, priority); 0 keeps its sets longest -----
def ladder(name, L, n, top_sets, step=0, frac=1.0, backoff=2):
    top, below = rung(name, L, n)
    if top is None:
        return []
    sL, sn = SKILLS[top]['start']
    fresh = pos(L, n) - pos(sL, sn) < 14
    out = []
    if fresh and below:
        out.append((st(top, 2, ctx=(L, n), rest=120, frac=frac), 0))
        out.append((st(below, min(3, top_sets), ctx=(L, n), rest=120, frac=frac), 1))
    else:
        out.append((st(top, top_sets, ctx=(L, n), rest=120, step=step, frac=frac), 0))
        if below and backoff:
            out.append((st(below, backoff, ctx=(L, n), rest=90, frac=frac), 1))
    return out


def handstand_practice(L, n, sets):
    """The handstand line in the practice part."""
    if started('freestanding_handstand', L, n):
        return [(st('freestanding_handstand', sets, ctx=(L, n), rest=60, tempo='easy technique'), 0)]
    if started('handstand_balance', L, n):
        return [(st('handstand_balance', sets, ctx=(L, n), rest=45, tempo='easy technique'), 0),
                (st('wall_handstand', 1, ctx=(L, n), rest=60, tempo='easy technique'), 1)]
    if started('wall_handstand', L, n):
        return [(st('wall_handstand', sets, ctx=(L, n), rest=60, tempo='easy technique'), 0)]
    return [(st('pike_handstand_hold', sets, ctx=(L, n), rest=45, tempo='easy technique'), 0)]


def first_started(L, n, *mids):
    for mid in mids:
        if started(mid, L, n):
            return mid
    return mids[-1]


def pull_basic(L, n):
    return first_started(L, n, 'pullup', 'eccentric_pullup', 'inverted_row')


def dip_basic(L, n):
    return first_started(L, n, 'parallel_dip', 'eccentric_dip', 'assisted_dip', 'support_hold')


def push_basic(L, n):
    return 'incline_pushup' if L == 1 and n <= 7 else 'pushup'


def leg_basic(L, n):
    return 'pistol_squat' if L >= 5 else 'squat'


def hang_core(L, n):
    return first_started(L, n, 'toes_to_bar', 'hanging_leg_raise', 'knee_raise')


def dragon(L, n):
    return first_started(L, n, 'dragon_flag', 'full_dragon_negative', 'one_leg_dragon_negative', 'bent_dragon_negative', 'candlestick')


def circuit(stations, rounds, L, n, rest=45, round_rest=90, frac=1.0):
    """Stations as repeated one-set steps: A, B, C, then A, B, C again."""
    out = []
    for r in range(rounds):
        for i, mid in enumerate(stations):
            last = i == len(stations) - 1
            out.append((st(mid, 1, ctx=(L, n), rest=round_rest if last else rest, frac=frac), 1 if r == 0 else 2))
    return out


def build_role(role, L, n, step):
    """A role's class at (L, n): practice, main, warm-up kind, cool-down kind, minutes cap, RPE."""
    c = (L, n)
    P, M = [], []
    if role == 'recovery':
        k = RECOVERY_INDEX.setdefault((L, n), len(RECOVERY_INDEX))
        trio = MOBILITY_TRIOS[k % len(MOBILITY_TRIOS)]
        M = [(st('easy_walk', 1, value=480 if k < len(MOBILITY_TRIOS) else 600, rest=0, tempo='easy'), 0)]
        M += [(st(mid, 2 if mid in ('deep_squat_hold', 'bird_dog', 'dead_bug') else 1, value=v, rest=20, tempo='slow'), 0) for mid, v in trio]
        M.append((st('dead_bug', 1, ctx=c, rest=20, tempo='slow'), 0) if all(m != 'dead_bug' for m, _ in trio) else (st('glute_bridge', 1, value=12, rest=20), 0))
        return P, M, '', 'easy', 30, 2
    if role == 'easy_line':
        hs = handstand_practice(L, n, 1)[0][0][0]
        M = [(st(hs, 2, ctx=c, rest=60, tempo='easy technique', frac=0.7), 0),
             (st('hollow_body_hold', 2, ctx=c, rest=45, frac=0.7), 0), (st('arch_hold', 2, ctx=c, rest=45, frac=0.7), 0),
             (st('dead_bug', 2, ctx=c, rest=30, tempo='slow'), 1), (st('calf_raise', 1, ctx=c, rest=30), 2)]
        return P, M, 'wrist', 'easy', 25, 3
    if role == 'practice_push':
        M = [(st('plank', 2, ctx=c, rest=45), 0), (st('support_hold', 2, ctx=c, rest=60), 0),
             (st(first_started(L, n, 'wall_handstand', 'pike_handstand_hold'), 2, ctx=c, rest=60, tempo='easy technique'), 0)]
        if started('pike_pushup', L, n):
            M.append((st('pike_pushup', 2, ctx=c, rest=60), 1))
        else:
            M.append((st('elevated_plank', 2, ctx=c, rest=45), 1))
        M.append((st(push_basic(L, n), 2, ctx=c, rest=60), 1))
        if L == 2:
            M.append((st(first_started(L, n, 'one_leg_lsit', 'tuck_lsit'), 2, ctx=c, rest=45), 1))
            M.append((st('candlestick', 1, ctx=c, rest=45), 2))
        return P, M, 'wrist push', 'push', 30, 4
    if role == 'practice_bar':
        M = [(st('dead_hang', 2, ctx=c, rest=60), 0)]
        if started('eccentric_pullup', L, n):
            M.append((st('eccentric_pullup', 2, ctx=c, rest=90, tempo='5010'), 0))
        else:
            M.append((st('inverted_row', 2, ctx=c, rest=60), 0))
        M += [(st('hollow_body_hold', 2, ctx=c, rest=45), 0), (st('arch_hold', 1, ctx=c, rest=45), 1)]
        if L == 2:
            M.append((st('straight_bar_support', 1, ctx=c, rest=60), 1))
            if started('tuck_front_row', L, n):
                M.append((st('tuck_front_row', 2, ctx=c, rest=75), 1))
            if started('skin_cat_partial', L, n):
                M.append((st('skin_cat_partial', 1, ctx=c, rest=60, tempo='slow'), 2))
        return P, M, 'pull', 'pull', 30, 4
    if role in ('circuit_a', 'circuit_b', 'circuit_c'):
        if role == 'circuit_a':
            pb = pull_basic(L, n)
            stations = [pb, push_basic(L, n), 'inverted_row' if pb != 'inverted_row' else 'squat', dip_basic(L, n), 'hollow_body_hold']
            P = handstand_practice(L, n, 1)
        elif role == 'circuit_b':
            stations = ['squat', 'glute_bridge' if L == 1 else 'single_leg_bridge', first_started(L, n, 'split_squat', 'calf_raise'),
                        'side_plank', first_started(L, n, 'hanging_leg_raise', 'knee_raise')]
            P = handstand_practice(L, n, 1)
        else:
            stations = [push_basic(L, n), 'squat', pull_basic(L, n), dip_basic(L, n), 'plank' if L == 1 else 'hollow_body_hold']
            P = handstand_practice(L, n, 1)
        M = circuit(stations, 2, L, n, rest=40 if role == 'circuit_c' else 45)
        if role == 'circuit_a':
            M.append((st('dead_hang', 1, ctx=c, rest=60), 1))
        if L == 2:
            M.append((st('candlestick' if role != 'circuit_b' else 'seated_pike_lift', 2, ctx=c, rest=60), 1))
            if role == 'circuit_a' and started('pullup', L, n):
                M.append((st('pullup', 2, ctx=c, rest=120), 0))
        rpe = 5 if L == 1 and n <= 14 else 6
        return P, M, ('legs' if role == 'circuit_b' else 'pull push'), ('legs' if role == 'circuit_b' else 'all'), 38, rpe
    if role in ('front_pull', 'front_onearm'):
        M = ladder('front', L, n, 4 if L >= 4 else 3, step) or [(st('tuck_front_row', 3, ctx=c, rest=90), 0)]
        M.append((st('pullup', 4 if L >= 3 else 3, ctx=c, rest=150, step=step), 0))
        if role == 'front_onearm':
            oa = first_started(L, n, 'one_arm_pullup', 'one_arm_pullup_negative', 'assisted_one_arm_pullup')
            M.append((st(oa, 2, ctx=c, rest=150, tempo='5010' if oa == 'one_arm_pullup_negative' else '3010'), 0))
            M.append((st('archer_pullup', 2, ctx=c, rest=120), 1))
        elif started('assisted_one_arm_pullup', L, n):
            M.append((st('assisted_one_arm_pullup', 2, ctx=c, rest=150, tempo='3010'), 1))
            M.append((st('archer_pullup', 2, ctx=c, rest=120), 1))
        elif started('archer_pullup', L, n):
            M.append((st('archer_pullup', 3, ctx=c, rest=120, step=step), 1))
        M.append((st(hang_core(L, n), 3, ctx=c, rest=75), 2))
        if L == 2:
            M.append((st('inverted_row', 2, ctx=c, rest=75), 2))
        if L <= 4:
            M.append((st('dead_hang', 1, ctx=c, rest=60), 2))
        P = handstand_practice(L, n, 2) if L >= 3 else []
        return P, M, 'pull shoulder', 'pull', 55, 8 if L >= 8 else 7
    if role == 'planche_push':
        M = ladder('planche', L, n, 4, step)
        if not M:
            M = [(st('frog_stand', 3 if L >= 3 else 2, ctx=c, rest=75), 0), (st('planche_lean', 2, ctx=c, rest=75), 1)]
        elif L <= 5:
            M.append((st('planche_lean', 1, ctx=c, rest=60), 2))
        if started('lsit', L, n):
            M.append((st('v_sit' if started('v_sit', L, n) else 'lsit', 3 if L < 8 else 2, ctx=c, rest=75), 1))
        else:
            M.append((st(first_started(L, n, 'one_leg_lsit', 'tuck_lsit'), 2, ctx=c, rest=60), 1))
        if started('one_arm_pushup', L, n):
            M.append((st('one_arm_pushup', 2, ctx=c, rest=120), 0))
        elif started('archer_pushup', L, n):
            M.append((st('archer_pushup', 2, ctx=c, rest=90), 1))
        if started('pseudo_planche_pushup', L, n):
            M.append((st('pseudo_planche_pushup', 3 if L < 7 else 2, ctx=c, rest=90, step=step), 1))
        M.append((st(dip_basic(L, n), 3, ctx=c, rest=120, step=step), 0))
        M.append((st('pushup', 2, ctx=c, rest=90), 1))
        if L == 2:
            M.append((st('pike_pushup', 2, ctx=c, rest=75), 1))  # the push practice day's pressing moves here
        return handstand_practice(L, n, 2), M, 'wrist push shoulder', 'push', 55, 7
    if role == 'legs_core':
        lb = leg_basic(L, n)
        M = [(st(lb, 3 if L < 8 else 4, ctx=c, rest=120, step=step), 0)]
        if lb == 'squat' and started('pistol_squat', L, n):
            M.append((st('pistol_squat', 3, ctx=c, rest=90, step=step), 0))
        elif lb == 'squat' and started('assisted_pistol_squat', L, n):
            M.append((st('assisted_pistol_squat', 2, ctx=c, rest=90), 1))
        if lb == 'pistol_squat':
            M.append((st('squat', 1, ctx=c, rest=90), 1))
        M.append((st('single_leg_rdl', 2, ctx=c, rest=75), 1))
        if started('nordic_curl', L, n):
            M.append((st('nordic_curl', 2, ctx=c, rest=150, tempo='5010'), 1))
        elif started('nordic_negative', L, n):
            M.append((st('nordic_negative', 2, ctx=c, rest=120, tempo='5010'), 1))
        else:
            M.append((st('bulgarian_split_squat', 2, ctx=c, rest=75), 2))
        df = dragon(L, n)
        M.append((st(df, 3, ctx=c, rest=120 if df != 'candlestick' else 60, tempo='4010' if 'negative' in df else '2111'), 0))
        M.append((st('hollow_body_hold', 2, ctx=c, rest=60), 1))
        if started('v_sit', L, n) and L <= 9:
            # Once the V-sit takes the planche day, the L-sit stays here as the compression hold until it reaches its top.
            M.append((st('lsit', 1, ctx=c, rest=75), 2))
        else:
            M.append((st('pancake_compression', 2, ctx=c, rest=60), 2))
        M.append((st('straight_side_plank', 1, ctx=c, rest=45), 2))
        return P, M, 'legs', 'legs', 45, 6
    if role in ('back_bar', 'back_muscleup'):
        M = []
        if started('strict_muscleup', L, n):
            M.append((st('strict_muscleup', 4, ctx=c, rest=180, tempo='controlled', step=step), 0))
        elif started('band_muscleup', L, n):
            M.append((st('band_muscleup', 3, ctx=c, rest=120, tempo='controlled'), 0))
            M.append((st('chest_to_bar', 2, ctx=c, rest=120), 1))
            if started('negative_muscleup', L, n):
                M.append((st('negative_muscleup', 2, ctx=c, rest=120, tempo='5010'), 1))
        elif started('low_bar_transition', L, n):
            M.append((st('low_bar_transition', 2, ctx=c, rest=75, tempo='controlled'), 1))
        else:
            M.append((st('straight_bar_support', 2, ctx=c, rest=75), 1))
        if started('german_hang', L, n):
            M.append((st('german_hang', 1, ctx=c, rest=60, tempo='slow'), 0))
        M += ladder('back', L, n, 4 if L >= 4 else 3, step) or [(st('skin_cat_partial', 2, ctx=c, rest=75, tempo='slow'), 0)]
        if L >= 4:
            top, below = rung('front', L, n)
            M.append((st(below or top, 2, ctx=c, rest=90, frac=0.6), 2))
        M.append((st(first_started(L, n, 'hanging_leg_raise', 'knee_raise'), 2, ctx=c, rest=75), 2))
        if L in (4, 5):
            M.append((st('chest_to_bar', 2, ctx=c, rest=120), 2))
        return (handstand_practice(L, n, 2) if L >= 3 else []), M, 'pull shoulder', 'pull', 55, 8 if L >= 8 else 7
    if role in ('handstand_flag_press', 'handstand_press'):
        flag_day = role == 'handstand_flag_press'
        M = ladder('flag', L, n, 3 if L < 8 else 4, step) if L >= 5 and flag_day else []
        if not M and flag_day and started('assisted_tuck_flag', L, n):
            M = [(st('assisted_tuck_flag', 2, ctx=c, rest=90), 0)]
        elif not M and flag_day and started('flag_ground_support', L, n):
            M = [(st('flag_ground_support', 2, ctx=c, rest=75), 0)]
        if started('deficit_wall_hspu', L, n):
            M += [(st('deficit_wall_hspu', 3, ctx=c, rest=150, step=step), 0), (st('wall_hspu', 2, ctx=c, rest=120), 1)]
        elif started('wall_hspu', L, n):
            M.append((st('wall_hspu', 3, ctx=c, rest=150, step=step), 0))
        elif started('wall_hspu_negative', L, n):
            M.append((st('wall_hspu_negative', 3, ctx=c, rest=120, tempo='5010'), 0))
        if started('freestanding_hspu_negative', L, n):
            M.append((st('freestanding_hspu_negative', 2, ctx=c, rest=150, tempo='5010'), 1))
        M.append((st('pike_pushup' if L < 5 else 'elevated_pike_pushup', 3 if L < 5 else 2, ctx=c, rest=90, step=step), 1))
        M.append((st('glute_bridge' if L < 3 else 'single_leg_bridge', 2, ctx=c, rest=60), 1))
        M.append((st('hollow_body_hold', 1, ctx=c, rest=60), 2))
        M.append((st('seated_pike_lift', 1, ctx=c, rest=60), 2))
        P = handstand_practice(L, n, 3)
        if started('freestanding_handstand', L, n) and L <= 5:
            # The wall hold stays as the handstand's endurance back-off until it reaches its own top.
            P.append((st('wall_handstand', 1, ctx=c, rest=60, tempo='easy technique'), 1))
        return P, M, 'wrist push shoulder', 'push', 55, 6
    if role == 'basics':
        M = [(st(rung('planche', L, n)[0] or 'planche_lean', 1, ctx=c, rest=90, frac=0.6), 2),
             (st(rung('front', L, n)[0] or 'tuck_front_row', 1, ctx=c, rest=90, frac=0.6), 2),
             (st(pull_basic(L, n), 2, ctx=c, rest=150), 0), (st(dip_basic(L, n), 2, ctx=c, rest=120), 0), (st('pushup', 2, ctx=c, rest=90), 0)]
        M += circuit([pull_basic(L, n), dip_basic(L, n), 'pushup', leg_basic(L, n), first_started(L, n, 'hanging_leg_raise', 'knee_raise')],
                     2, L, n, rest=45, round_rest=120, frac=0.7)
        return handstand_practice(L, n, 2), M, 'pull push', 'all', 50, 7
    if role == 'battle':
        M = [(st('strict_muscleup', 2, ctx=c, rest=180, tempo='controlled', frac=0.7), 1)]
        M += circuit(['pullup', 'parallel_dip', 'pushup', 'pistol_squat'], 3, L, n, rest=30, round_rest=120, frac=0.75)
        M.append((st('toes_to_bar', 2, ctx=c, rest=75, frac=0.8), 2))
        return P, M, 'pull push legs', 'all', 50, 7
    if role == 'statics_chain':
        chain = [r for r in (rung('front', L, n)[0], rung('back', L, n)[0], rung('planche', L, n)[0], rung('flag', L, n)[0]) if r]
        M = [(st(r, 1, ctx=c, rest=30, frac=0.6), 0) for r in chain]
        M += [(st('v_sit', 1, ctx=c, rest=30, frac=0.6), 0), (st('freestanding_handstand', 1, ctx=c, rest=120, frac=0.6), 0),
              (st('pullup', 2, ctx=c, rest=120, frac=0.7), 1), (st('parallel_dip', 2, ctx=c, rest=120, frac=0.7), 1),
              (st('pushup', 2, ctx=c, rest=90, frac=0.7), 1), (st('pistol_squat', 2, ctx=c, rest=90, frac=0.7), 2)]
        return P, M, 'wrist pull push shoulder', 'all', 50, 7
    raise ValueError(role)


# ----- the special classes -----
def line_drills(L, n):
    """One easy set of each skill line, below working intensity: keeps every line in the week of a lighter class."""
    c = (L, n)
    out = []
    if L >= 2:
        top, below = rung('front', L, n)
        out.append((st(below or top or 'tuck_front_row', 1, ctx=c, rest=45, frac=0.5), 1))
        out.append((st(first_started(L, n, 'german_hang', 'skin_cat_partial', 'arch_hold'), 1, ctx=c, rest=60, tempo='slow', frac=0.5), 1))
        out.append((st('straight_bar_support', 1, ctx=c, rest=60, frac=0.5), 1))
        out.append((st('planche_lean' if started('planche_lean', L, n) else 'elevated_plank', 1, ctx=c, rest=60, frac=0.5), 1))
        out.append((st('candlestick', 1, value=5, rest=45), 1))
    return out


def reentry(L, n):
    c = (L, n)
    hs = first_started(L, n, 'freestanding_handstand', 'wall_handstand', 'pike_handstand_hold')
    M = [(st(hs if hs != 'freestanding_handstand' else 'wall_handstand', 2, ctx=c, rest=60, tempo='easy technique', frac=0.6), 0),
         (st('hollow_body_hold', 2, ctx=c, rest=45, frac=0.6), 0), (st('dead_hang', 1, ctx=c, rest=60, frac=0.6), 0),
         (st(push_basic(L, n), 2, ctx=c, rest=60, frac=0.5), 0), (st(leg_basic(L, n), 2, ctx=c, rest=60, frac=0.5), 0),
         (st('deep_squat_hold', 1, value=30, rest=30, tempo='easy'), 1)]
    M += line_drills(L, n)
    return [], M, 'wrist', 'easy', 35, 4


def primer(L, n):
    c = (L, n)
    M = [(st(push_basic(L, n), 1, ctx=c, rest=60, frac=0.6), 0), (st(pull_basic(L, n), 1, ctx=c, rest=90, frac=0.6), 0),
         (st(dip_basic(L, n), 1, ctx=c, rest=60, frac=0.6), 0), (st(leg_basic(L, n), 1, ctx=c, rest=60, frac=0.6), 0),
         (st('hollow_body_hold', 1, ctx=c, rest=45, frac=0.6), 0), (st('glute_bridge', 1, ctx=c, rest=30, frac=0.6), 1)]
    if started('pike_pushup', L, n):
        M.append((st('pike_pushup', 1, ctx=c, rest=45, frac=0.6), 1))
    M += line_drills(L, n)
    return [], M, 'wrist', 'easy', 40, 4


def easy_class(L, n):
    c = (L, n)
    M = [(st(first_started(L, n, 'wall_handstand', 'pike_handstand_hold'), 2, ctx=c, rest=60, tempo='easy technique', frac=0.6), 0),
         (st(push_basic(L, n), 2, ctx=c, rest=60, frac=0.5), 0), (st('squat', 2, ctx=c, rest=60, frac=0.5), 0),
         (st('hollow_body_hold', 2, ctx=c, rest=45, frac=0.6), 0), (st('dead_hang', 1, ctx=c, rest=60, frac=0.6), 1),
         (st('dead_bug', 1, ctx=c, rest=30, tempo='slow'), 1), (st('glute_bridge', 1, ctx=c, rest=30, frac=0.6), 1)]
    if started('pike_pushup', L, n):
        M.append((st('pike_pushup', 1, ctx=c, rest=45, frac=0.6), 1))
    M += line_drills(L, n)
    return [], M, 'wrist', 'easy', 40, 4


def scaled(steps, set_frac=1.0, dose_frac=None):
    out = []
    for s, p in steps:
        s = list(s)
        s[1] = max(1, int(s[1] * set_frac + 0.5)) if set_frac != 1.0 else s[1]
        if dose_frac is not None:
            for k in (2, 3):
                if s[k] is not None:
                    v = s[k]
                    side = isinstance(v, str) and v.endswith('/side')
                    num = int(str(v).split('/')[0])
                    num = max(1, int(num * dose_frac + 0.5))
                    s[k] = f'{num}/side' if side else num
        out.append((s, p))
    return out


# The order a test day runs its sets in: fresh balance and the biggest straight-arm shapes first.
TEST_ORDER = ['freestanding_handstand', 'wall_handstand', 'strict_muscleup', 'band_muscleup', 'full_planche', 'straddle_planche',
              'advanced_tuck_planche', 'tuck_planche', 'frog_stand', 'full_front', 'straddle_front', 'advanced_tuck_front',
              'tuck_front_hold', 'german_hang', 'full_back', 'straddle_back', 'advanced_tuck_back', 'tuck_back_hold', 'full_flag',
              'straddle_flag', 'tuck_flag', 'deficit_wall_hspu', 'wall_hspu', 'pike_pushup', 'v_sit', 'lsit', 'one_arm_pullup',
              'archer_pullup', 'pullup', 'eccentric_pullup', 'inverted_row', 'parallel_dip', 'assisted_dip', 'support_hold',
              'one_arm_pushup', 'pseudo_planche_pushup', 'pushup', 'incline_pushup', 'pistol_squat', 'squat', 'toes_to_bar',
              'hanging_leg_raise', 'dragon_flag', 'hollow_body_hold', 'dead_hang']


def tested(L, n):
    """The skills a block's exam tests: each line's top rung once it has had two build weeks, else the rung below."""
    c = (L, n)
    b = block_of(L, n)
    s = EXAMS[L][b - 2] + 1 if b > 1 else 1
    picks = []
    for name in LADDERS:
        top, below = rung(name, L, n)
        if top is None:
            continue
        sL, sn = SKILLS[top]['start']
        picks.append(top if pos(L, n) - pos(sL, sn) >= 14 else below or top)
    picks = [p for p in picks if p]
    for mid in ('freestanding_handstand', 'strict_muscleup', 'deficit_wall_hspu', 'one_arm_pullup', 'archer_pullup', 'pullup',
                'parallel_dip', 'one_arm_pushup', 'pseudo_planche_pushup', 'pushup', 'pistol_squat', 'v_sit', 'lsit', 'toes_to_bar',
                'dragon_flag', 'wall_hspu', 'pike_pushup', 'wall_handstand', 'hanging_leg_raise', 'squat', 'hollow_body_hold', 'dead_hang'):
        sL, sn = DOSED[mid]['start']
        if started(mid, L, n) and pos(L, n) - pos(sL, sn) >= 14:
            picks.append(mid)
    if not started('pullup', L, n) or pos(L, n) - pos(*SKILLS['pullup']['start']) < 14:
        picks.append(pull_basic(L, n) if pull_basic(L, n) != 'pullup' else 'eccentric_pullup')
    if not started('parallel_dip', L, n) or pos(L, n) - pos(*SKILLS['parallel_dip']['start']) < 14:
        picks.append(dip_basic(L, n) if dip_basic(L, n) != 'parallel_dip' else 'assisted_dip')
    if L <= 2 and 'pushup' not in picks:
        picks.append('pushup' if not (L == 1 and n <= 7) else 'incline_pushup')
    # one exercise per family: the hardest started one
    families = [('freestanding_handstand', 'wall_handstand'), ('deficit_wall_hspu', 'wall_hspu', 'pike_pushup'),
                ('one_arm_pullup', 'archer_pullup'), ('v_sit', 'lsit'), ('toes_to_bar', 'hanging_leg_raise'),
                ('one_arm_pushup', 'pseudo_planche_pushup')]
    for fam in families:
        have = [m for m in fam if m in picks]
        for m in have[1:]:
            picks.remove(m)
    order = {m: i for i, m in enumerate(TEST_ORDER)}
    picks = sorted(dict.fromkeys(picks), key=lambda m: order.get(m, 99))
    keep = 12 if L < 7 else 10
    if len(picks) > keep:
        # drop the easiest supporting tests first, never a ladder's rung or a basic
        for m in ('dead_hang', 'hollow_body_hold', 'squat', 'hanging_leg_raise', 'wall_handstand', 'pike_pushup', 'wall_hspu',
                  'dragon_flag', 'toes_to_bar', 'lsit', 'pseudo_planche_pushup', 'archer_pullup'):
            if len(picks) > keep and m in picks:
                picks.remove(m)
    rungs = {r for rs in LADDERS.values() for r in rs}
    while len(picks) > keep:
        picks.remove(next(m for m in reversed(picks) if m not in rungs and m not in ('one_arm_pullup', 'strict_muscleup')))
    return picks


ADVANCED_STANDARD = [('freestanding_handstand', 20), ('strict_muscleup', 3), ('advanced_tuck_planche', 8), ('straddle_front', 3),
                     ('full_back', 5), ('full_flag', 2), ('wall_hspu', 7), ('pullup', 15), ('parallel_dip', 20), ('pushup', 35),
                     ('pistol_squat', 8), ('lsit', 20)]


def exam(L, n):
    c = (L, n)
    b = block_of(L, n)
    M = []
    if (L, n) == (10, 100):
        for r in range(5):
            for i, (mid, reps) in enumerate((('pullup', 10), ('parallel_dip', 20), ('pushup', 30), ('squat', 40))):
                M.append((st(mid, 1, value=reps, rest=90 if i == 3 else 30, tempo='assessment'), 0))
        return [], M, 'pull push', 'all', 55, 8, 'exam_level_full_battle'
    if (L, n) == (10, 76):
        chain = ['freestanding_handstand', 'full_planche', 'full_front', 'full_back', 'full_flag', 'v_sit']
        for mid in chain:
            M.append((st(mid, 1, ctx=c, rest=60, tempo='assessment', step=1), 0))
        for mid in ('strict_muscleup', 'one_arm_pullup', 'one_arm_pushup', 'deficit_wall_hspu'):
            M.append((st(mid, 1, ctx=c, rest=180, tempo='assessment', step=1), 0))
        return [], M, 'wrist pull push shoulder', 'all', 55, 8, 'exam_block_champion_statics_strength'
    if (L, n) == (7, 100):
        for mid, v in ADVANCED_STANDARD:
            if mid == 'full_back':
                M.append((st('german_hang', 1, ctx=c, rest=60, tempo='slow'), 0))
            M.append((st(mid, 1, value=v, rest=120, tempo='assessment'), 0))
        return [], M, 'wrist pull push', 'all', 55, 8, 'exam_level_advanced_athlete'
    for mid in tested(L, n):
        if mid in ('tuck_back_hold', 'advanced_tuck_back', 'straddle_back', 'full_back') and started('german_hang', L, n):
            M.append((st('german_hang', 1, ctx=c, rest=60, tempo='slow'), 0))
        M.append((st(mid, 1, ctx=c, rest=(100 if L >= 9 else 120) if L >= 3 else 90, tempo='assessment', step=1 if L >= 4 else 0), 0))
    kind = 'exam_level' if n == 100 else 'exam_block'
    return [], M, 'wrist pull', 'all', 55 if L >= 3 else 45, 8 if L >= 4 else 7, kind


# ----- fitting a class: the set budget, the length, the text -----
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
    total = 0
    for steps in sections:
        for s in steps:
            work, rests, rest = secs_of(s)
            total += work + rests + 25 + (rest if s[1] == 1 else 0)
    return total


def fit(P, M, L, cap_minutes, extra=0, rpe=0):
    """Trim to the level's set budget, the per-step set limit and the class length; supporting work goes first."""
    budget = LIMITS[L]['main_sets_including_practice']
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
        if cand:
            cand[0][0][1] -= 1
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


TITLE_KIND = {'deload': 'Lighter Week', 'light': 'Light', 'reentry': 'Re-Entry', 'primer': 'Primer', 'easy': 'Easy Basics'}
KIND_SENTENCE = {
    'build_a': '', 'build_b': 'Week B: one step up on the lead steps.',
    'deload': 'The lighter week before the exam: fewer sets at the same dose per set.',
    'light': 'A light class: the same lines at lower doses.',
    'reentry': 'An easy first class after the exam: short sets with plenty in reserve.',
    'primer': 'The primer before the exam: one easy set of each line, nothing hard.',
    'easy': 'An easy class before the exam: the basics at half the dose.',
}
EXAM_SENTENCE = {
    'exam_block': 'Exam day: one fresh set of each skill this block trained, at the block\'s dose, in the order shown. Tick each target you reach. A miss offers to repeat the block.',
    'exam_level': 'The level exam: one fresh set of each skill the level trained, at its top dose, in the order shown. Tick each target you reach. A miss offers to repeat the block.',
    'exam_level_advanced_athlete': 'The Advanced Athlete exam: the standard this program sets for an advanced athlete, one fresh set each. Tick each target you reach.',
    'exam_block_champion_statics_strength': 'Champion test, part one: every full shape and the hardest strength skills, one fresh set each with full rest. Tick each target you reach.',
    'exam_level_full_battle': 'Champion test, part two, the Full Battle: five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats, 30 s between stations and 90 s between rounds. Every set as written.',
}


def build_raw(L, n):
    """The class before fitting: practice, main, warm-up and cool-down kinds, minute cap, RPE, day type, role, kind."""
    kind = kind_of(L, n)
    role = role_of(L, n)
    if kind == 'exam':
        P, M, wk, ck, cap, rpe, dtype = exam(L, n)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype=dtype, role=role, kind=kind)
    if kind in ('reentry', 'primer', 'easy'):
        P, M, wk, ck, cap, rpe = {'reentry': reentry, 'primer': primer, 'easy': easy_class}[kind](L, n)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype={'reentry': 'reentry_recovery', 'primer': 'primer_easy', 'easy': 'easy_recovery_basics'}[kind], role=role, kind=kind)
    if kind == 'recovery' or role == 'recovery':
        P, M, wk, ck, cap, rpe = build_role('recovery', L, n, 0)
        return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype='recovery', role='recovery', kind='recovery')
    step = 1 if kind == 'build_b' else 0
    P, M, wk, ck, cap, rpe = build_role(role, L, n, step)
    dtype = role
    if kind == 'deload':
        if role.startswith('circuit') or role in ('basics', 'battle'):
            M = [x for x in M if x[1] != 2] + [x for x in M if x[1] == 2 and x[0][1] > 1]  # one round fewer
        P, M = scaled(P, 0.6), scaled(M, 0.7)
        if sum(1 for x in M if x[1] < 2) >= 4:
            M = [x for x in M if x[1] < 2]  # and no supporting extras
        rpe, dtype = max(5, rpe - 1), 'deload_' + role
        if role in ('practice_push', 'practice_bar', 'easy_line'):
            rpe = 4
            P = []
    elif kind == 'light':
        frac = 0.8 if n == 11 else 0.6
        P, M = scaled(P, 0.75, frac), scaled(M, 0.75, frac)
        rpe, dtype = 5, 'light_' + role
    elif L >= 4 and block_of(L, n) == 1 and n <= 7:
        M = [((s[:1] + [max(1, s[1] - 1)] + s[2:]) if p == 0 else s, p) for s, p in M]
    if role in ('easy_line', 'practice_push', 'practice_bar'):
        dtype = {'easy_line': 'easy_handstand_line', 'practice_push': 'practice_push_line', 'practice_bar': 'practice_bar_line'}[role]
    return dict(P=P, M=M, wk=wk, ck=ck, cap=cap, rpe=rpe, dtype=dtype, role=role, kind=kind)


RECORD_ROLE = {}
for _r in LADDERS['front']:
    RECORD_ROLE[_r] = ('front_onearm',)
for _r in LADDERS['back']:
    RECORD_ROLE[_r] = ('back_muscleup',)
for _r in LADDERS['planche']:
    RECORD_ROLE[_r] = ('planche_push',)
for _r in LADDERS['flag']:
    RECORD_ROLE[_r] = ('handstand_flag_press',)
for _g, _ids in {('planche_push',): ('lsit', 'v_sit', 'pseudo_planche_pushup', 'one_arm_pushup', 'parallel_dip', 'pushup'),
                 ('handstand_flag_press',): ('wall_handstand', 'freestanding_handstand', 'wall_hspu', 'deficit_wall_hspu', 'pike_pushup'),
                 ('front_onearm',): ('pullup', 'archer_pullup', 'one_arm_pullup', 'toes_to_bar', 'dead_hang'),
                 ('back_muscleup',): ('strict_muscleup', 'hanging_leg_raise'),
                 ('legs_core',): ('squat', 'pistol_squat', 'dragon_flag', 'hollow_body_hold')}.items():
    for _r in _ids:
        RECORD_ROLE[_r] = _g
assert set(RECORD_ROLE) == set(SKILLS)
ROLE_OF_SKILL = {'front': ('front_pull', 'front_onearm'), 'back': ('back_bar', 'back_muscleup'), 'planche': ('planche_push',),
                 'flag': ('handstand_flag_press',)}


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
        for n in range(15, 92):
            r = raw[(10, n)]
            if (10, n) in used or r['kind'] not in ('build_a', 'build_b') or r['role'] not in roles:
                continue
            idx = next((i for i, (s, _) in enumerate(r['M']) if s[0] == sid), None)
            rec = st(sid, 1, value=elite, rest=180, tempo='assessment')
            if idx is None:
                r['M'].insert(1, (rec, 0))
            elif r['M'][idx][0][1] > 1:
                s, p = r['M'][idx]
                r['M'][idx] = ([s[0], s[1] - 1] + s[2:], p)
                r['M'].insert(idx + 1, (rec, 0))
            else:
                r['M'][idx] = (rec, 0)
            used.add((10, n))
            placed[sid] = n
            break
        else:
            placed[sid] = None
    return placed, dict(best)


FORBIDDEN = r'(?i)(choose|choice|optional|independent|\broutes?\b|routing|non[-_ ]?blocking)'
TOKENS = {
    'pull': r'(?:^|\s)(?:assisted_pullup|eccentric_pullup|pullup|chinup|archer_pullup|typewriter_pullup|assisted_one_arm_pullup|one_arm_pullup_negative|inverted_row|high_inverted_row|feet_elevated_row)(?:\s|$)',
    'push': r'(?:^|\s)(?:wall_pushup|incline_pushup|pushup|pause_pushup|close_pushup|archer_pushup|pike_pushup|elevated_pike_pushup|wall_hspu_negative|wall_hspu|deficit_wall_hspu|freestanding_hspu_negative)(?:\s|$)',
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
    _, _, via = belts(programs)
    reach = best_sets(programs)
    return build_once({sid for sid in SKILLS if via[sid][4] or reach[sid] >= SKILLS[sid]['t'][4]})


def best_sets(programs):
    """Each skill's biggest set in the practice and main parts, readiness checks left out."""
    reach = defaultdict(int)
    for p in programs:
        for s in p['practice'] + p['main']:
            if s[0] in SKILLS and s[5] != 'readiness_check':
                reach[s[0]] = max(reach[s[0]], int(str(s[2] if s[2] is not None else s[3]).split('/')[0]))
    return reach


def build_once(skip):
    import re
    rehearsed.clear()  # each pass rehearses from the first class again
    raw = {(L, n): build_raw(L, n) for L in range(1, 11) for n in range(1, 101)}
    records, best = add_records(raw, skip)
    programs = []
    seen_titles, seen_desc, seen_main = set(), set(), set()
    fitted = {}
    for L in range(1, 11):
        for n in range(1, 101):
            r = raw[(L, n)]
            warm = warmup(r['wk'], L)
            cool = [list(x) for x in COOL[r['ck']]]
            P, M = fit(r['P'], r['M'], L, r['cap'], class_seconds([warm, cool]), r['rpe'])
            prev = fitted.get((L, n - 7))
            if r['kind'] == 'deload' and prev is not None:
                # The lighter week keeps at most 70% of the sets of the same day a week earlier.
                limit = int(0.7 * sum(x[1] for x in prev))
                pr = {id(s): p_ for s, p_ in zip(M, [q for _, q in r['M']])}
                while sum(x[1] for x in M) > limit:
                    multi = [x for x in M if x[1] > 1]
                    if multi:
                        max(multi, key=lambda x: x[1])[1] -= 1
                    elif len(M) > 4:
                        M.pop()
                    else:
                        break
            fitted[(L, n)] = M
            warm, P, M = finish(warm), finish(P, rehearse=False), finish(M)
            tries = 0
            while json.dumps(M) in seen_main:
                M[tries % len(M)][4] += 5
                tries += 1
                assert tries < 12, (L, n)
            seen_main.add(json.dumps(M))
            minutes = min(r['cap'], max(15, math.ceil(class_seconds([warm, P, M, cool]) / 60) + 2))
            kind, role = r['kind'], r['role']
            lead = next((s for s in M if s[0] not in ('german_hang',)), M[0])
            if kind == 'exam':
                head = {'exam_level_advanced_athlete': 'Advanced Athlete Exam', 'exam_block_champion_statics_strength': 'Champion Test: Statics & Strength',
                        'exam_level_full_battle': 'Champion Test: the Full Battle'}.get(r['dtype'], f'Level {L} Exam' if n == 100 else f'Block {block_of(L, n)} Exam')
                cands = [f'{head}, Level {L}' if n != 100 else head]
            elif kind in TITLE_KIND or role == 'recovery':
                head = 'Recovery' if role == 'recovery' or kind == 'recovery' else TITLE_KIND[kind]
                cands = [f'{head}: {dose_text(lead)}', f'{head}: {dose_text(lead)}, {dose_text(M[1])}']
            else:
                head = ROLE_TITLE[role]
                cands = [f'{head}: {dose_text(lead)}', f'{head}: {dose_text(lead)}, {dose_text(M[1])}']
            cands.append(f'{cands[-1]} (L{L}:{n:03d})')
            title = next(t for t in cands if t not in seen_titles)
            seen_titles.add(title)
            if kind == 'exam':
                first = EXAM_SENTENCE[r['dtype']]
            elif role == 'recovery' or kind == 'recovery':
                first = ROLE_SENTENCE['recovery']
            else:
                first = (KIND_SENTENCE.get(kind, '') + ' ' + ('' if kind in ('reentry', 'primer', 'easy') else ROLE_SENTENCE[role])).strip()
            body = ', '.join(dose_text(s) for s in M if s[0] not in ('easy_walk',))
            desc = f'{first} Today: {body}.'
            recs = [x for x in M if x[5] == 'assessment'] if kind != 'exam' else []
            if recs:
                desc += (' Record set: ' + ', '.join(dose_text(x) for x in recs) +
                         ", one fresh set at the skill's Elite target. Elite itself takes 30 such sets, from repeats and your own training.")
            if desc in seen_desc:
                desc += f' Class {n} of Level {L}.'
            seen_desc.add(desc)
            focus = list(dict.fromkeys(p for s in M[:4] for p in LIB[s[0]]['patterns']))[:5]
            programs.append({
                'id': f'r9-l{L:02d}-p{n:03d}', 'origin': 'round9', 'level': L, 'number': n,
                'title': {'en': title}, 'description': {'en': desc},
                'schedule': {'week': (n - 1) // 7 + 1, 'day': weekday(n), 'day_type': r['dtype'], 'minutes': minutes,
                             'rpe': r['rpe'], 'focus': focus},
                'warmup': warm, 'practice': P, 'main': M, 'cooldown': cool,
            })
    return programs, records, best


def check(programs):
    """The validators' rules, checked while writing so a failure names the class."""
    import re
    errors = []
    by = {(p['level'], p['number']): p for p in programs}
    run = 0
    for p in programs:
        L, n = p['level'], p['number']
        where = f'L{L}:{n:03d}'
        lim = LIMITS[L]
        allsets = sum(s[1] for s in p['practice'] + p['main'])
        if allsets > lim['main_sets_including_practice']:
            errors.append(f'{where} has {allsets} sets, budget {lim["main_sets_including_practice"]}')
        if any(s[1] > lim['sets_per_exercise'] for s in p['practice'] + p['main']):
            errors.append(f'{where} has a step above {lim["sets_per_exercise"]} sets')
        if len(p['main']) < 4 or len(p['warmup']) < 3 or len(p['cooldown']) < 3:
            errors.append(f'{where} has too few steps')
        sec = class_seconds([p['warmup'], p['practice'], p['main'], p['cooldown']])
        if sec > p['schedule']['minutes'] * 60:
            errors.append(f'{where} needs {sec / 60:.1f} min > {p["schedule"]["minutes"]}')
        if p['schedule']['minutes'] > 55:
            errors.append(f'{where} is over 55 minutes')
        adv = sum(s[1] for s in p['main'] if LIB[s[0]]['category'] in ('advanced_skill', 'mastery_skill', 'advanced_strength', 'skill_strength'))
        if sum(s[1] for s in p['main']) > 20 or (p['schedule']['rpe'] >= 8 and adv > 10):
            errors.append(f'{where} main sets {sum(s[1] for s in p["main"])}, advanced {adv}')
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
    landed, _, _ = belts(programs)
    reach = best_sets(programs)
    for p in programs:
        ids = {s[0] for s in p['main']}
        for s in p['main']:
            # A progression peaks while it is trained: never a record set beside a harder rung of its ladder.
            if s[5] == 'assessment' and not p['schedule']['day_type'].startswith('exam'):
                for rungs in LADDERS.values():
                    if s[0] in rungs and ids & set(rungs[rungs.index(s[0]) + 1:]):
                        errors.append(f'L{p["level"]}:{p["number"]:03d} has a {s[0]} record beside a harder rung')
    for sid, sk in SKILLS.items():
        if reach[sid] < sk['t'][4] and not landed[sid][4]:
            errors.append(f'{sid} never reaches its Elite target {sk["t"][4]} (best {reach[sid]})')
    return errors


def belts(programs):
    """Where each belt lands if every set is done as written on the Normal toggle, as the app counts it: the first class
    whose running count of sets at or above the target reaches 10/20/30/30/30, the belt below already held, or the
    first class where a harder rung of its ladder holds that belt (`via` names that rung). Practice and main count;
    warm-up, cool-down and readiness checks do not."""
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
                    count[sid][k] += s[1]
                if landed[sid][k] is None and count[sid][k] >= BELT_SETS[k] and (k == 0 or landed[sid][k - 1] is not None):
                    landed[sid][k] = (p['level'], p['number'])
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
    print(len(errors), 'check errors')
    for e in errors[:40]:
        print('  ', e)
    out = {'schema_version': 'round9', 'generated_by': 'tools/build_curriculum.py',
           'step_tuple': ['movement_id', 'sets', 'reps', 'seconds', 'rest_seconds', 'tempo', 'regression_id'], 'programs': programs}
    (ROOT / 'curriculum/program.json').write_text(json.dumps(out, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    if len(sys.argv) > 2 and sys.argv[1] == '--report':
        Path(sys.argv[2]).write_text(json.dumps({
            'landed': {k: ['L%d:%03d' % x if x else None for x in v] for k, v in landed.items()}, 'via': via,
            'targets': {k: SKILLS[k]['t'] for k in SKILLS}, 'records': records, 'best': best,
            'debut': {k: 'L%d:%03d' % SKILLS[k]['start'] for k in SKILLS},
            'doses': {k: {L: [dose(k, L, e) for e in EXAMS[L]] for L in range(1, 11) if dose(k, L, 100) is not None and started(k, L, 100)} for k in SKILLS},
            'per_level': per_level, 'errors': errors}, indent=1), encoding='utf-8')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())

