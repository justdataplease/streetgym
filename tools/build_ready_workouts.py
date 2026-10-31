"""Writes Pro's ready workouts (Training > Library) to app/src/main/assets/workouts.json.

Each workout is a class like the program's: warm-up, main work and cool-down in the curriculum's step tuples
([movement_id, sets, reps, seconds, rest_seconds, tempo, regression_id]), using only movements of the movement library,
so every exercise has its animation, notes and easier option. The workouts are this project's own (the owner,
2026-10-30); their names and doses aren't taken from any other app. Minutes are worked out here, like a custom day's:
about 3 s a rep, both sides for "/side", plus rest and 15 s to change exercises, rounded up to 5 minutes.

Grades follow the skill belts and the program's levels: 1 Beginner (Levels 1-2), 2 Intermediate (3-4), 3 Advanced (5-6),
4 Master (7-8) and 5 Elite (9-10). Skill sets sit at about 70-85% of the belt target for the grade, balance skills come as
short attempts, the hardest skills as singles or doubles with 120-180 s rests, and the basics stay short after
straight-arm or one-arm work (2026-10-31: 36 workouts became 100).

    python tools/build_ready_workouts.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LIB = {m['id']: m for m in json.loads((ROOT / 'curriculum/movement_library.json').read_text(encoding='utf-8'))['movements']}
OUT = ROOT / 'app/src/main/assets/workouts.json'

GROUPS = ['full', 'pull', 'push', 'legs', 'core', 'skills', 'short', 'mobility']
SAFETY = 'Stop a set when your form breaks, and take the easier option whenever you need it.'


def reps(movement, sets, count, rest, tempo='2111', easier=None):
    return [movement, sets, count, None, rest, tempo, easier]


def hold(movement, sets, seconds, rest, tempo='isometric', easier=None):
    return [movement, sets, None, seconds, rest, tempo, easier]


def stretch(movement, seconds=30, sets=1):
    return [movement, sets, None, seconds, 10, 'easy', None]


def dose(value):
    """A dose and whether it is per side: 8, '8/side', '1-2-3' (one per set) or 'max'."""
    if value is None:
        return [], False
    text = str(value)
    side = text.endswith('/side')
    text = text.removesuffix('/side')
    if text == 'max':
        return [10], side
    return [int(x) for x in text.split('-')], side


def work(count=None, held=None):
    """One set's working seconds, as the minutes count them: about 3 s a rep, both sides for "/side"."""
    rep_values, rep_side = dose(count)
    hold_values, hold_side = dose(held)
    return ((rep_values[0] * 3 * (2 if rep_side else 1) if rep_values else 0)
            + (hold_values[0] * (2 if hold_side else 1) if hold_values else 0))


def minute(movement, sets, count=None, held=None, tempo=None):
    """Every minute on the minute: each set starts on the minute, so its rest is what the minute leaves."""
    rest = 60 - work(count, held)
    if count is not None:
        return reps(movement, sets, count, rest, tempo or '2111')
    return hold(movement, sets, held, rest, tempo or 'isometric')


def circuit(rounds, *stations):
    """One set a station, the round repeated; the last station's rest is the rest between rounds."""
    return [list(step) for _ in range(rounds) for step in stations]


WARMUP = {
    'bar': [hold('march', 1, 30, 0, 'brisk'), reps('arm_swings', 1, 10, 10, 'controlled'),
            reps('wrist_circles', 1, 10, 10, 'controlled'), reps('scapular_pull', 1, 6, 20),
            reps('scapular_pushup', 1, 8, 20), reps('inverted_row', 1, 5, 45, 'ramp-up')],
    'floor': [hold('march', 1, 30, 0, 'brisk'), reps('arm_swings', 1, 10, 10, 'controlled'),
              reps('wrist_circles', 1, 10, 10, 'controlled'), reps('hip_circles', 1, 8, 10, 'controlled'),
              reps('cat_cow', 1, 6, 10, 'slow'), reps('scapular_pushup', 1, 8, 20)],
    'legs': [hold('march', 1, 30, 0, 'brisk'), reps('hip_circles', 1, 8, 10, 'controlled'),
             reps('ankle_rocks', 1, '10/side', 10, 'controlled'), reps('leg_swings', 1, '8/side', 10, 'controlled'),
             reps('glute_bridge', 1, 10, 20), reps('squat', 1, 6, 45, 'ramp-up')],
    'hands': [hold('march', 1, 30, 0, 'brisk'), reps('wrist_circles', 1, 10, 10, 'controlled'),
              reps('wrist_rocks', 1, 10, 10, 'controlled'), reps('wrist_pushup', 1, 10, 20),
              reps('arm_swings', 1, 10, 10, 'controlled'), reps('wall_slide', 1, 8, 20, 'slow'),
              reps('scapular_pushup', 1, 8, 20)],
    'core': [hold('march', 1, 30, 0, 'brisk'), reps('cat_cow', 1, 6, 10, 'slow'),
             reps('hip_circles', 1, 8, 10, 'controlled'), reps('dead_bug', 1, '6/side', 20, 'slow'),
             hold('arch_hold', 1, 15, 20)],
    'short': [hold('march', 1, 30, 0, 'brisk'), reps('arm_swings', 1, 10, 10, 'controlled'),
              reps('wrist_circles', 1, 10, 10, 'controlled'), reps('hip_circles', 1, 8, 10, 'controlled')],
    'easy': [hold('march', 1, 60, 0, 'easy')],
    'planche': [hold('march', 1, 30, 0, 'brisk'), reps('wrist_circles', 1, 10, 10, 'controlled'),
                reps('wrist_rocks', 1, 10, 10, 'controlled'), reps('wrist_pushup', 1, 10, 20),
                reps('arm_swings', 1, 10, 10, 'controlled'), reps('scapular_pushup', 1, 8, 20),
                hold('planche_lean', 1, 10, 45, 'ramp-up')],
    'statics': [hold('march', 1, 30, 0, 'brisk'), reps('arm_swings', 1, 10, 10, 'controlled'),
                reps('wrist_circles', 1, 10, 10, 'controlled'), reps('wrist_rocks', 1, 10, 10, 'controlled'),
                reps('scapular_pull', 1, 6, 20), reps('scapular_pushup', 1, 8, 20),
                hold('planche_lean', 1, 10, 45, 'ramp-up'), hold('tuck_front_hold', 1, 6, 45, 'ramp-up')],
    'short_bar': [hold('march', 1, 30, 0, 'brisk'), reps('arm_swings', 1, 10, 10, 'controlled'),
                  reps('scapular_pull', 1, 6, 20), reps('scapular_pushup', 1, 8, 20)],
    'short_hands': [hold('march', 1, 30, 0, 'brisk'), reps('wrist_circles', 1, 10, 10, 'controlled'),
                    reps('wrist_rocks', 1, 10, 10, 'controlled'), reps('arm_swings', 1, 10, 10, 'controlled')],
    'short_legs': [hold('march', 1, 30, 0, 'brisk'), reps('hip_circles', 1, 8, 10, 'controlled'),
                   reps('ankle_rocks', 1, '10/side', 10, 'controlled'), reps('squat', 1, 6, 30, 'ramp-up')],
}
COOLDOWN = {
    'upper': [stretch('lat_stretch'), stretch('chest_stretch'), stretch('forearm_stretch'), hold('child_pose', 1, 45, 0, 'easy')],
    'full': [stretch('lat_stretch'), stretch('chest_stretch'), stretch('hip_flexor_stretch', '30/side'),
             stretch('hamstring_stretch'), hold('child_pose', 1, 45, 0, 'easy')],
    'legs': [stretch('quad_stretch'), stretch('hamstring_stretch'), stretch('hip_flexor_stretch', '30/side'),
             stretch('calf_stretch')],
    'core': [hold('child_pose', 1, 45, 0, 'easy'), stretch('supine_twist'), stretch('hip_flexor_stretch', '30/side')],
    'short': [stretch('lat_stretch'), stretch('hamstring_stretch')],
    'easy': [hold('slow_breathing', 1, 120, 0, 'easy')],
    'short_legs': [stretch('quad_stretch'), stretch('hamstring_stretch')],
}

# (slug, name, group, grade 1-5, rpe, description, warm-up, main, cool-down)
WORKOUTS = [
    ('first_park_session', 'First Park Session', 'full', 1, 6,
     'Row, push, squat and hold: the basics, three sets each.', 'bar',
     [reps('inverted_row', 3, 8, 60), reps('pushup', 3, 6, 60), reps('squat', 3, 10, 60),
      hold('plank', 2, 30, 45), hold('dead_hang', 2, 20, 45)], 'full'),
    ('hotel_room', 'Hotel Room', 'full', 1, 6,
     'No bar needed: floor work for a trip or a rainy day.', 'floor',
     [reps('pushup', 4, 10, 60), reps('squat', 4, 15, 60), reps('reverse_lunge', 3, '8/side', 60),
      reps('glute_bridge', 3, 15, 45), hold('plank', 3, 30, 45), hold('side_plank', 2, '20/side', 45)], 'full'),
    ('bars_and_floor', 'Bars and Floor', 'full', 2, 7,
     'Pull-ups and dips on the bars, then push-ups, lunges and leg raises.', 'bar',
     [reps('pullup', 4, 5, 90), reps('parallel_dip', 4, 6, 90), reps('pushup', 3, 12, 60),
      reps('reverse_lunge', 3, '10/side', 60), reps('hanging_leg_raise', 3, 6, 75)], 'full'),
    ('pull_dip_jump', 'Pull, Dip, Jump', 'full', 2, 7,
     'Three moves, five sets each. Rest well and keep every rep clean.', 'bar',
     [reps('pullup', 5, 4, 90), reps('parallel_dip', 5, 6, 90), reps('squat_jump', 5, 5, 75, 'controlled')], 'full'),
    ('every_muscle', 'Every Muscle', 'full', 2, 7,
     'Chin-ups, pike push-ups, split squats and holds for the whole body.', 'bar',
     [reps('chinup', 3, 6, 90), reps('pike_pushup', 3, 8, 75), reps('bulgarian_split_squat', 3, '8/side', 75),
      reps('single_leg_rdl', 3, '6/side', 60), hold('hollow_body_hold', 3, 20, 60)], 'full'),
    ('strength_round', 'Strength Round', 'full', 3, 8,
     'Muscle-ups, dips, pistols, toes-to-bar and an L-sit.', 'bar',
     [reps('strict_muscleup', 4, 2, 150), reps('parallel_dip', 4, 10, 90), reps('pistol_squat', 3, '3/side', 120),
      reps('toes_to_bar', 3, 5, 90), hold('lsit', 3, 10, 90)], 'full'),

    ('row_day', 'Row Day', 'pull', 1, 6,
     'Rows and assisted pull-ups on the way to your first strict pull-up.', 'bar',
     [reps('inverted_row', 4, 8, 60), reps('assisted_pullup', 3, 6, 60), reps('scapular_pull', 3, 8, 45),
      hold('dead_hang', 3, 20, 45), reps('prone_ytw', 2, 8, 30, 'controlled')], 'upper'),
    ('pullup_ladder', 'Pull-Up Ladder', 'pull', 2, 7,
     'Climb from 1 to 5 pull-ups and back down, then rows and a hang.', 'bar',
     [reps('pullup', 5, '1-2-3-4-5', 45), reps('pullup', 4, '4-3-2-1', 60), reps('inverted_row', 3, 10, 60),
      hold('active_hang', 2, 20, 45)], 'upper'),
    ('back_and_grip', 'Back and Grip', 'pull', 2, 7,
     'Pull-ups, chin-ups, rows and long hangs for your back and grip.', 'bar',
     [reps('pullup', 5, 4, 90), reps('chinup', 3, 6, 90), reps('feet_elevated_row', 3, 10, 60),
      hold('active_hang', 3, 20, 45), hold('dead_hang', 2, 40, 60)], 'upper'),
    ('front_lever_start', 'Front Lever Start', 'pull', 2, 7,
     'Tuck rows and tuck holds, the first steps to a front lever.', 'bar',
     [reps('tuck_front_row', 3, 6, 90), hold('tuck_front_hold', 5, 8, 120), hold('active_hang', 3, 15, 45),
      hold('hollow_body_hold', 3, 20, 60)], 'upper'),
    ('lever_builder', 'Lever Builder', 'pull', 3, 8,
     'Front-lever holds and rows, with the core work behind them.', 'bar',
     [hold('advanced_tuck_front', 5, 6, 120), reps('tuck_front_lever_row', 4, 5, 120), reps('toes_to_bar', 3, 5, 90),
      hold('hollow_body_hold', 3, 20, 60)], 'upper'),
    ('muscleup_practice', 'Muscle-Up Practice', 'pull', 3, 8,
     'Swings, chest-to-bar pull-ups, single muscle-ups and straight-bar dips.', 'bar',
     [reps('hollow_arch_swing', 2, 8, 45, 'controlled'), reps('chest_to_bar', 4, 3, 120),
      reps('strict_muscleup', 5, 1, 150), reps('straight_bar_dip', 3, 6, 90)], 'upper'),

    ('pushup_pyramid', 'Push-Up Pyramid', 'push', 1, 6,
     'Push-ups from 4 up to 8 and back, then close-grip and pike push-ups.', 'floor',
     [reps('pushup', 5, '4-6-8-6-4', 60), reps('close_pushup', 3, 6, 60), reps('pike_pushup', 3, 5, 75),
      hold('plank', 2, 30, 45)], 'upper'),
    ('dip_station', 'Dip Station', 'push', 2, 7,
     'Support holds, then dips on both bars and push-ups to finish.', 'bar',
     [hold('support_hold', 2, 30, 45), reps('parallel_dip', 5, 8, 90), reps('straight_bar_dip', 3, 5, 90),
      reps('pushup', 2, 'max', 90)], 'upper'),
    ('shoulders_overhead', 'Shoulders Overhead', 'push', 2, 7,
     'Pike push-ups and wall handstands for strong, healthy shoulders.', 'hands',
     [reps('pike_pushup', 4, 8, 75), reps('elevated_pike_pushup', 3, 6, 90), hold('wall_handstand', 3, 30, 60),
      reps('prone_ytw', 2, 8, 30, 'controlled'), reps('wall_slide', 2, 10, 30, 'slow')], 'upper'),
    ('planche_push', 'Planche Push', 'push', 3, 8,
     'Leans, pseudo-planche push-ups, tuck planche holds and archer push-ups.', 'hands',
     [hold('planche_lean', 4, 15, 90), reps('pseudo_planche_pushup', 4, 6, 120), hold('tuck_planche', 4, 6, 150),
      reps('archer_pushup', 3, '4/side', 120)], 'upper'),
    ('handstand_press', 'Handstand Press', 'push', 3, 8,
     'Wall handstand push-ups, then pike push-ups and a long wall hold.', 'hands',
     [reps('wall_hspu', 5, 4, 150), reps('elevated_pike_pushup', 3, 8, 90), hold('wall_handstand', 3, 40, 60)], 'upper'),

    ('legs_no_weights', 'Legs, No Weights', 'legs', 1, 6,
     'Squats, lunges, bridges and calf raises, then a deep squat hold.', 'legs',
     [reps('squat', 4, 15, 60), reps('reverse_lunge', 3, '10/side', 60), reps('glute_bridge', 3, 15, 45),
      reps('calf_raise', 3, 15, 45), hold('deep_squat_hold', 2, 30, 30)], 'legs'),
    ('one_leg_at_a_time', 'One Leg at a Time', 'legs', 2, 7,
     'Split squats, supported pistols and single-leg hinges and bridges.', 'legs',
     [reps('bulgarian_split_squat', 4, '8/side', 90), reps('assisted_pistol_squat', 3, '5/side', 90),
      reps('single_leg_rdl', 3, '8/side', 60), reps('single_leg_bridge', 3, '10/side', 60),
      reps('calf_raise', 3, 15, 45)], 'legs'),
    ('jump_and_hold', 'Jump and Hold', 'legs', 2, 7,
     'Squat jumps, paused squats, side squats and hamstring curls.', 'legs',
     [reps('squat_jump', 5, 6, 75, 'controlled'), reps('pause_squat', 3, 10, 60),
      reps('cossack_squat', 3, '5/side', 60), reps('sliding_leg_curl', 3, 8, 75), hold('deep_squat_hold', 2, 45, 30)], 'legs'),
    ('pistols_and_shrimps', 'Pistols and Shrimps', 'legs', 3, 8,
     'Pistol squats, shrimp squats, Nordic negatives and jumps.', 'legs',
     [reps('pistol_squat', 5, '3/side', 120), reps('assisted_shrimp', 3, '4/side', 120),
      reps('nordic_negative', 3, 4, 120, '5010'), reps('squat_jump', 3, 6, 75, 'controlled')], 'legs'),

    ('floor_core', 'Floor Core', 'core', 1, 5,
     'Dead bugs, planks, side planks and bird dogs on the floor.', 'core',
     [reps('dead_bug', 3, '8/side', 30, 'slow'), hold('plank', 3, 30, 45), hold('side_plank', 2, '20/side', 45),
      reps('bird_dog', 2, '8/side', 30, 'slow'), reps('glute_bridge', 2, 12, 30)], 'core'),
    ('hanging_core', 'Hanging Core', 'core', 2, 7,
     'Knee and leg raises on the bar, then hollow, arch and side holds.', 'bar',
     [reps('knee_raise', 3, 10, 60), reps('hanging_leg_raise', 3, 8, 75), hold('hollow_body_hold', 3, 25, 60),
      hold('arch_hold', 3, 20, 45), hold('straight_side_plank', 2, '30/side', 45)], 'core'),
    ('compression', 'Compression', 'core', 2, 7,
     'Pike lifts and tuck L-sits for a stronger fold.', 'core',
     [reps('seated_pike_lift', 4, 10, 60), reps('pancake_compression', 3, 12, 60), hold('tuck_lsit', 4, 12, 60),
      hold('one_leg_lsit', 3, '8/side', 60), hold('hollow_body_hold', 2, 30, 60)], 'core'),
    ('abs_on_the_bar', 'Abs on the Bar', 'core', 3, 8,
     'Toes-to-bar, dragon-flag negatives, L-sits and a long hollow hold.', 'bar',
     [reps('toes_to_bar', 5, 6, 90), reps('bent_dragon_negative', 3, 4, 120, '5010'), hold('lsit', 4, 12, 90),
      hold('hollow_body_hold', 2, 40, 60)], 'core'),

    ('handstand_basics', 'Handstand Basics', 'skills', 1, 5,
     'Box pikes, kick-ups, wall holds and the cartwheel exit.', 'hands',
     [hold('pike_handstand_hold', 3, 20, 45), reps('handstand_exit', 3, '2/side', 60, 'easy technique'),
      reps('wall_kickup', 3, '3/side', 60, 'easy technique'), hold('wall_handstand', 3, 25, 60),
      hold('wall_shoulder_flexion', 2, 30, 20, 'slow')], 'upper'),
    ('balance_practice', 'Balance Practice', 'skills', 2, 6,
     'Short, fresh attempts to find your freestanding handstand.', 'hands',
     [reps('handstand_exit', 2, '2/side', 60, 'easy technique'), hold('handstand_toe_pull', 3, 20, 60),
      hold('handstand_balance', 6, 10, 45), hold('freestanding_handstand', 5, 8, 60)], 'upper'),
    ('frog_to_planche', 'Frog to Planche', 'skills', 2, 7,
     'Frog stands and planche leans, then pseudo-planche push-ups.', 'hands',
     [hold('frog_stand', 5, 20, 60), hold('planche_lean', 4, 15, 90), reps('pseudo_planche_pushup', 3, 6, 120),
      hold('support_hold', 2, 30, 45)], 'upper'),
    ('back_lever_day', 'Back Lever Day', 'skills', 2, 7,
     'German hangs and skin-the-cats, then tuck back-lever holds.', 'bar',
     [hold('german_hang', 3, 15, 75), reps('skin_the_cat', 3, 3, 90, 'slow'), hold('tuck_back_hold', 5, 8, 90),
      hold('arch_hold', 2, 20, 45)], 'upper'),
    ('flag_prep', 'Flag Prep', 'skills', 3, 8,
     'Grounded flag support, vertical and tuck flags, then side planks.', 'hands',
     [hold('flag_ground_support', 3, '8/side', 60), hold('vertical_flag', 4, '6/side', 90),
      hold('tuck_flag', 4, '5/side', 150), hold('straight_side_plank', 2, '30/side', 45)], 'upper'),

    ('quick_basics', 'Quick Basics', 'short', 1, 6,
     'Push-ups, rows and squats when time is short.', 'short',
     [reps('pushup', 3, 10, 45), reps('inverted_row', 3, 10, 45), reps('squat', 2, 15, 45)], 'short'),
    ('before_work', 'Before Work', 'short', 1, 6,
     'Bear crawls, squats, push-ups and a plank to start the day.', 'short',
     [hold('bear_crawl', 3, 20, 40, 'controlled'), reps('squat', 3, 12, 40), reps('pushup', 3, 8, 45),
      hold('plank', 2, 30, 40)], 'short'),
    ('lunch_break', 'Lunch Break', 'short', 2, 7,
     'Pull-ups, push-ups and squat jumps in a short break.', 'short',
     [reps('pullup', 4, 5, 75), reps('pushup', 4, 15, 60), reps('squat_jump', 3, 8, 60, 'controlled')], 'short'),

    ('easy_day', 'Easy Day', 'mobility', 1, 3,
     'Gentle mobility for a rest day: spine, hips, shoulders, breathing.', 'easy',
     [reps('cat_cow', 2, 6, 10, 'slow'), reps('thoracic_rotation', 2, '8/side', 10, 'slow'),
      reps('ninety_ninety_hip_switch', 2, 8, 10, 'slow'), hold('deep_squat_hold', 2, 30, 20, 'easy'),
      reps('wall_slide', 2, 10, 10, 'slow'), hold('child_pose', 1, 45, 0, 'easy')], 'easy'),
    ('shoulders_and_wrists', 'Shoulders and Wrists', 'mobility', 1, 3,
     'Wrist and shoulder care before or after hand-balancing work.', 'easy',
     [reps('wrist_circles', 2, 10, 10, 'controlled'), reps('wrist_rocks', 2, 10, 10, 'controlled'),
      reps('wrist_pushup', 2, 10, 20), reps('wall_slide', 2, 10, 20, 'slow'), reps('prone_ytw', 2, 8, 20, 'controlled'),
      hold('wall_shoulder_flexion', 2, 30, 20, 'slow'), stretch('lat_stretch'), stretch('chest_stretch'),
      stretch('forearm_stretch')], 'easy'),
    ('hips_and_hamstrings', 'Hips and Hamstrings', 'mobility', 1, 3,
     'Hip switches, side squats and long stretches for the legs.', 'easy',
     [reps('ninety_ninety_hip_switch', 2, 8, 10, 'slow'), reps('cossack_squat', 2, '5/side', 30, 'slow'),
      hold('deep_squat_hold', 2, 30, 20, 'easy'), stretch('hamstring_stretch', 30, 2), stretch('adductor_stretch', 30, 2),
      stretch('ninety_ninety_hip_stretch', 30, 2), stretch('supine_twist')], 'easy'),

    # 2026-10-31: 64 more, to 100, with grades 4 (Master, Levels 7-8) and 5 (Elite, Levels 9-10).
    ('floor_circuit', 'Floor Circuit', 'full', 1, 6,
     'No bar: squats, push-ups, lunges, planks, bridges. Three rounds.', 'floor',
     circuit(3, reps('squat', 1, 12, 40), reps('pushup', 1, 8, 40), reps('reverse_lunge', 1, '6/side', 40),
             hold('plank', 1, 30, 40), reps('glute_bridge', 1, 12, 90)), 'full'),
    ('park_circuit', 'Park Circuit', 'full', 2, 7,
     'Pull-ups, dips, squats, rows, knee raises: one set each, three rounds.', 'bar',
     circuit(3, reps('pullup', 1, 4, 40), reps('parallel_dip', 1, 6, 40), reps('squat', 1, 15, 40),
             reps('inverted_row', 1, 8, 40), reps('knee_raise', 1, 8, 90)), 'full'),
    ('pull_and_dip_ladder', 'Pull and Dip Ladder', 'full', 3, 8,
     'Pull-ups 2 up to 8 and back, dips 3 up to 12 and back, then legs.', 'bar',
     [reps('pullup', 7, '2-4-6-8-6-4-2', 75), reps('parallel_dip', 7, '3-6-9-12-9-6-3', 75),
      reps('bulgarian_split_squat', 3, '8/side', 75), hold('hollow_body_hold', 2, 35, 45)], 'full'),
    ('no_bar_strength', 'No-Bar Strength', 'full', 3, 8,
     'No bar: archer, planche and handstand push-ups, pistols, hinges.', 'hands',
     [reps('archer_pushup', 3, '5/side', 120), reps('pseudo_planche_pushup', 3, 7, 120), reps('wall_hspu', 3, 4, 120),
      reps('pistol_squat', 3, '4/side', 120), reps('single_leg_rdl', 2, '8/side', 60),
      hold('hollow_body_hold', 2, 35, 60)], 'full'),
    ('bar_circuit', 'Bar Circuit', 'full', 3, 8,
     'Chest-to-bar, dips, pistols, toes-to-bar, rows: three rounds.', 'bar',
     circuit(3, reps('chest_to_bar', 1, 3, 45), reps('parallel_dip', 1, 10, 45), reps('pistol_squat', 1, '3/side', 45),
             reps('toes_to_bar', 1, 4, 45), reps('feet_elevated_row', 1, 10, 120)), 'full'),
    ('advanced_basics', 'Advanced Basics', 'full', 4, 8,
     'Muscle-up doubles, archer pull-ups, dips, pistols and toes-to-bar.', 'bar',
     [reps('strict_muscleup', 5, 2, 150), reps('archer_pullup', 3, '3/side', 120), reps('parallel_dip', 2, 15, 90),
      reps('pistol_squat', 3, '6/side', 120), reps('toes_to_bar', 3, 8, 75)], 'full'),
    ('strength_circuit', 'Strength Circuit', 'full', 4, 8,
     'Four rounds of chest-to-bar, dips, pistols, pseudo-planche push-ups.', 'bar',
     circuit(4, reps('chest_to_bar', 1, 5, 45), reps('parallel_dip', 1, 12, 45), reps('pistol_squat', 1, '5/side', 45),
             reps('pseudo_planche_pushup', 1, 8, 120)), 'full'),
    ('floor_and_wall', 'Floor and Wall', 'full', 4, 8,
     'No bar: tuck planche, wall handstand push-ups, shrimp squats.', 'planche',
     [hold('tuck_planche', 4, 10, 120), reps('wall_hspu', 4, 5, 150), reps('shrimp_squat', 3, '3/side', 120),
      reps('archer_pushup', 2, '6/side', 90), hold('hollow_body_hold', 2, 40, 60)], 'full'),
    ('one_arm_strength', 'One-Arm Strength', 'full', 5, 9,
     'One-arm pull-ups and push-ups, shrimp squats, dragon flags.', 'bar',
     [reps('one_arm_pullup', 5, '1/side', 180), reps('one_arm_pushup', 4, '3/side', 150),
      reps('shrimp_squat', 3, '5/side', 120), reps('dragon_flag', 3, 5, 150)], 'full'),
    ('statics_round', 'Statics Round', 'full', 5, 9,
     'Straddle planche, full back and straddle front levers, V-sit, pistols.', 'statics',
     [hold('straddle_planche', 4, 5, 180), hold('full_back', 3, 10, 150), hold('straddle_front', 3, 8, 150),
      hold('v_sit', 3, 5, 120), reps('pistol_squat', 2, '8/side', 120)], 'full'),

    ('archer_pullups', 'Archer Pull-Ups', 'pull', 3, 8,
     'Archer pull-ups, the first step to one arm, then pull-ups and rows.', 'bar',
     [reps('archer_pullup', 4, '2/side', 120), reps('pullup', 2, 8, 90), reps('feet_elevated_row', 3, 10, 60),
      hold('dead_hang', 2, 45, 60), reps('prone_ytw', 2, 8, 30, 'controlled')], 'upper'),
    ('muscleup_doubles', 'Muscle-Up Doubles', 'pull', 4, 7,
     'Six sets of two strict muscle-ups, then bar dips and chest-to-bar.', 'bar',
     [reps('hollow_arch_swing', 2, 8, 45, 'controlled'), reps('strict_muscleup', 6, 2, 150),
      reps('straight_bar_dip', 3, 10, 90), reps('chest_to_bar', 2, 5, 90), hold('hollow_body_hold', 2, 40, 60)], 'upper'),
    ('typewriter_pullups', 'Typewriter Pull-Ups', 'pull', 4, 8,
     'Archer, typewriter and assisted one-arm pull-ups: the one-arm path.', 'bar',
     [reps('archer_pullup', 3, '4/side', 120), reps('typewriter_pullup', 3, '2/side', 150),
      reps('assisted_one_arm_pullup', 3, '2/side', 150), reps('pullup', 2, 10, 90), hold('dead_hang', 2, 60, 60)], 'upper'),
    ('chest_to_bar_ladder', 'Chest-to-Bar Ladder', 'pull', 4, 8,
     'Chest-to-bar 2 up to 5 and back, then archer pull-ups and toes-to-bar.', 'bar',
     [reps('chest_to_bar', 7, '2-3-4-5-4-3-2', 90), reps('archer_pullup', 2, '3/side', 120), reps('toes_to_bar', 3, 8, 75),
      hold('active_hang', 2, 30, 45)], 'upper'),
    ('one_arm_pullup', 'One-Arm Pull-Up', 'pull', 5, 9,
     'Single one-arm pull-ups with long rests, then slow one-arm negatives.', 'bar',
     [reps('archer_pullup', 2, '4/side', 120), reps('one_arm_pullup', 6, '1/side', 180),
      reps('one_arm_pullup_negative', 2, '2/side', 150, '5010'), hold('dead_hang', 2, 60, 60),
      reps('prone_ytw', 2, 8, 30, 'controlled')], 'upper'),
    ('bar_strength', 'Bar Strength', 'pull', 5, 8,
     'Muscle-up doubles, archer pull-ups, bar dips and toes-to-bar.', 'bar',
     [reps('strict_muscleup', 6, 2, 150), reps('archer_pullup', 3, '6/side', 150), reps('straight_bar_dip', 2, 12, 90),
      reps('toes_to_bar', 3, 12, 75)], 'upper'),

    ('dip_ladder', 'Dip Ladder', 'push', 2, 7,
     'Dips 2 up to 8 and back, a push-up ladder, then pike push-ups.', 'bar',
     [reps('parallel_dip', 7, '2-4-6-8-6-4-2', 75), reps('pushup', 5, '4-8-12-8-4', 60), reps('pike_pushup', 3, 8, 75),
      hold('plank', 2, 40, 45)], 'upper'),
    ('archer_pushups', 'Archer Push-Ups', 'push', 3, 7,
     'No bar: archer push-ups toward one arm, then planche and pike push-ups.', 'floor',
     [reps('archer_pushup', 4, '5/side', 120), reps('pseudo_planche_pushup', 3, 7, 120), reps('close_pushup', 2, 12, 75),
      reps('pike_pushup', 2, 10, 75), hold('hollow_body_hold', 2, 35, 60)], 'upper'),
    ('advanced_tuck_planche', 'Advanced Tuck Planche', 'push', 4, 8,
     'Tuck and advanced tuck planche holds, tuck planche push-ups, dips.', 'planche',
     [hold('tuck_planche', 2, 12, 120), hold('advanced_tuck_planche', 5, 8, 150), reps('tuck_planche_pushup', 3, 2, 150),
      reps('pseudo_planche_pushup', 2, 10, 120), reps('parallel_dip', 2, 15, 90)], 'upper'),
    ('deep_handstand_pushups', 'Deep Handstand Push-Ups', 'push', 4, 8,
     'Freestanding handstands, then wall and deficit handstand push-ups.', 'hands',
     [hold('freestanding_handstand', 4, 20, 120), reps('wall_hspu', 2, 5, 120), reps('deficit_wall_hspu', 4, 3, 150),
      reps('elevated_pike_pushup', 2, 10, 90), reps('prone_ytw', 2, 8, 30, 'controlled')], 'upper'),
    ('one_arm_pushup', 'One-Arm Push-Up', 'push', 4, 8,
     'No bar: archer and one-arm push-ups, planche push-ups, side planks.', 'floor',
     [reps('archer_pushup', 3, '6/side', 120), reps('one_arm_pushup', 5, '2/side', 150),
      reps('pseudo_planche_pushup', 3, 10, 120), reps('pushup', 2, 20, 90), hold('straight_side_plank', 2, '40/side', 45)],
     'upper'),
    ('straddle_planche', 'Straddle Planche', 'push', 5, 9,
     'Straddle planche holds after an advanced tuck, then planche push-ups.', 'planche',
     [hold('advanced_tuck_planche', 2, 10, 150), hold('straddle_planche', 6, 5, 180), reps('tuck_planche_pushup', 3, 3, 150),
      reps('parallel_dip', 2, 15, 90), hold('hollow_body_hold', 2, 50, 60)], 'upper'),
    ('free_handstand_pushups', 'Free Handstand Push-Ups', 'push', 5, 9,
     'Freestanding handstand push-up singles and negatives, then deficit sets.', 'hands',
     [hold('freestanding_handstand', 4, 30, 120), reps('freestanding_hspu', 4, 1, 180),
      reps('freestanding_hspu_negative', 3, 2, 150, '5010'), reps('deficit_wall_hspu', 3, 5, 150),
      reps('prone_ytw', 2, 8, 30, 'controlled')], 'upper'),

    ('squat_ladder', 'Squat Ladder', 'legs', 1, 6,
     'Squats 4 up to 16 and back, then split squats, bridges and calves.', 'legs',
     [reps('squat', 7, '4-8-12-16-12-8-4', 45), reps('split_squat', 3, '8/side', 60), reps('glute_bridge', 3, 15, 45),
      reps('calf_raise', 3, 15, 45)], 'legs'),
    ('legs_circuit', 'Legs Circuit', 'legs', 2, 7,
     'Split squats, bridges, step-ups, side squats, calves: three rounds.', 'legs',
     circuit(3, reps('bulgarian_split_squat', 1, '8/side', 40), reps('single_leg_bridge', 1, '10/side', 40),
             reps('step_up', 1, '8/side', 40), reps('cossack_squat', 1, '5/side', 40), reps('calf_raise', 1, 15, 90)),
     'legs'),
    ('back_of_the_legs', 'Back of the Legs', 'legs', 3, 7,
     'Nordic negatives, leg curls, single-leg hinges and bridges.', 'legs',
     [reps('nordic_negative', 4, 4, 120, '5010'), reps('sliding_leg_curl', 3, 10, 75),
      reps('single_leg_rdl', 3, '10/side', 60), reps('single_leg_bridge', 3, '12/side', 60),
      reps('cossack_squat', 2, '6/side', 60)], 'legs'),
    ('shrimp_squats', 'Shrimp Squats', 'legs', 4, 8,
     'Held then free shrimp squats, pistols and single-leg hinges.', 'legs',
     [reps('assisted_held_shrimp', 2, '4/side', 90), reps('shrimp_squat', 4, '3/side', 120),
      reps('pistol_squat', 3, '6/side', 120), reps('single_leg_rdl', 2, '12/side', 60), reps('calf_raise', 2, 20, 45)],
     'legs'),
    ('pistols_and_nordics', 'Pistols and Nordics', 'legs', 4, 8,
     'Pistol squats, Nordic curls, squat jumps and side squats.', 'legs',
     [reps('pistol_squat', 4, '6/side', 120), reps('nordic_curl', 4, 2, 150), reps('squat_jump', 3, 6, 90, 'controlled'),
      reps('cossack_squat', 2, '8/side', 60), hold('deep_squat_hold', 1, 60, 30)], 'legs'),
    ('shrimps_and_nordics', 'Shrimps and Nordics', 'legs', 5, 9,
     'Shrimp squats, pistols, Nordic curls and squat jumps.', 'legs',
     [reps('shrimp_squat', 4, '6/side', 120), reps('pistol_squat', 3, '8/side', 120), reps('nordic_curl', 4, 3, 150),
      reps('squat_jump', 3, 5, 90, 'controlled'), reps('single_leg_rdl', 2, '12/side', 60)], 'legs'),

    ('core_circuit', 'Core Circuit', 'core', 1, 6,
     'Dead bugs, planks, knee raises, side planks, bridges: three rounds.', 'core',
     circuit(3, reps('dead_bug', 1, '8/side', 30, 'slow'), hold('plank', 1, 30, 30), reps('lying_knee_raise', 1, 10, 30),
             hold('side_plank', 1, '20/side', 30), reps('glute_bridge', 1, 12, 75)), 'core'),
    ('hollow_and_arch', 'Hollow and Arch', 'core', 2, 6,
     'No bar: hollow and arch holds, candlesticks, leg lowers, side planks.', 'core',
     [hold('hollow_body_hold', 4, 25, 60), hold('arch_hold', 3, 25, 45), reps('candlestick', 3, 6, 60),
      reps('lying_leg_raise', 3, 10, 60), hold('straight_side_plank', 2, '30/side', 45)], 'core'),
    ('dragon_flag_steps', 'Dragon Flag Steps', 'core', 3, 8,
     'Dragon-flag negatives from bent to one leg to full, then a hollow hold.', 'core',
     [reps('candlestick', 2, 6, 60), reps('bent_dragon_negative', 2, 4, 120, '5010'),
      reps('one_leg_dragon_negative', 3, '3/side', 120, '5010'), reps('full_dragon_negative', 3, 2, 120, '5010'),
      hold('hollow_body_hold', 2, 35, 60)], 'core'),
    ('dragon_flag_day', 'Dragon Flag Day', 'core', 4, 8,
     'Full dragon flags after slow negatives, then toes-to-bar and holds.', 'core',
     [reps('full_dragon_negative', 2, 3, 120, '5010'), reps('dragon_flag', 4, 4, 150), reps('toes_to_bar', 3, 8, 75),
      hold('hollow_body_hold', 2, 40, 60), hold('arch_hold', 2, 30, 45)], 'core'),
    ('lsit_to_vsit', 'L-Sit to V-Sit', 'core', 4, 8,
     'Pike lifts, L-sits and first V-sit holds, with compression work.', 'core',
     [reps('seated_pike_lift', 3, 15, 60), hold('lsit', 3, 15, 90), hold('v_sit', 5, 3, 120),
      reps('pancake_compression', 2, 12, 60), reps('toes_to_bar', 2, 8, 75)], 'core'),
    ('vsit_and_dragon_flag', 'V-Sit and Dragon Flag', 'core', 5, 9,
     'V-sits, full dragon flags, toes-to-bar and long hollow holds.', 'core',
     [hold('lsit', 2, 20, 90), hold('v_sit', 5, 5, 120), reps('dragon_flag', 4, 6, 150), reps('toes_to_bar', 3, 12, 75),
      hold('hollow_body_hold', 2, 50, 60)], 'core'),

    ('handstand_shapes', 'Handstand Shapes', 'skills', 3, 6,
     'Freestanding and straddle handstand attempts, then wall weight shifts.', 'hands',
     [reps('handstand_exit', 2, '2/side', 60, 'easy technique'), hold('freestanding_handstand', 6, 15, 90),
      hold('straddle_handstand', 4, 10, 90), hold('supported_handstand_shift', 3, '5/side', 60),
      hold('wall_shoulder_flexion', 2, 30, 20, 'slow')], 'upper'),
    ('back_lever_steps', 'Back Lever Steps', 'skills', 3, 7,
     'German hangs, advanced tuck back lever holds, first straddle tries.', 'bar',
     [hold('german_hang', 2, 20, 60), reps('skin_the_cat', 2, 3, 90, 'slow'), hold('advanced_tuck_back', 4, 8, 120),
      hold('straddle_back', 3, 3, 150), hold('arch_hold', 2, 30, 45)], 'upper'),
    ('one_leg_front_lever', 'One-Leg Front Lever', 'skills', 3, 8,
     'Advanced tuck and one-leg front lever holds, then lever rows.', 'bar',
     [hold('advanced_tuck_front', 3, 10, 120), hold('one_leg_front', 4, '5/side', 120),
      reps('tuck_front_lever_row', 3, 4, 120), reps('pullup', 2, 8, 90), hold('hollow_body_hold', 2, 35, 60)], 'upper'),
    ('statics_on_the_minute', 'Statics on the Minute', 'skills', 3, 7,
     'Every minute on the minute: lever and planche tucks, then L-sits.', 'statics',
     circuit(5, minute('advanced_tuck_front', 1, held=8), minute('tuck_planche', 1, held=8))
     + [minute('lsit', 4, held=12)], 'upper'),
    ('hefesto_prep', 'Hefesto Prep', 'skills', 3, 7,
     'German hangs, skin-the-cats, supinated hangs and behind-bar supports.', 'bar',
     [hold('german_hang', 2, 25, 60), reps('skin_the_cat', 3, 3, 90, 'slow'),
      hold('assisted_supinated_german_hang', 4, 12, 150), hold('assisted_behind_bar_support', 3, 15, 90),
      hold('advanced_tuck_back', 3, 8, 120)], 'upper'),
    ('front_lever_day', 'Front Lever Day', 'skills', 4, 8,
     'Straddle front lever holds after tuck and one-leg sets, then rows.', 'bar',
     [hold('advanced_tuck_front', 2, 12, 120), hold('one_leg_front', 3, '8/side', 120), hold('straddle_front', 5, 4, 150),
      reps('tuck_front_lever_row', 3, 5, 120), reps('pullup', 2, 10, 90)], 'upper'),
    ('full_back_lever', 'Full Back Lever', 'skills', 4, 8,
     'Straddle and full back lever holds, German hangs, skin-the-cats.', 'bar',
     [hold('german_hang', 2, 30, 60), hold('straddle_back', 3, 6, 120), hold('full_back', 5, 5, 150),
      reps('skin_the_cat', 2, 5, 90, 'slow'), hold('arch_hold', 2, 30, 45)], 'upper'),
    ('straddle_flag', 'Straddle Flag', 'skills', 4, 8,
     'Tuck flags, then short straddle flag attempts and slow flag negatives.', 'hands',
     [hold('tuck_flag', 3, '8/side', 120), hold('straddle_flag', 6, '4/side', 150),
      reps('flag_negative', 2, '1/side', 150, '5010'), hold('straight_side_plank', 2, '45/side', 45)], 'upper'),
    ('handstand_shifts', 'Handstand Shifts', 'skills', 4, 7,
     'Handstand and straddle holds, then weight shifts toward one arm.', 'hands',
     [hold('freestanding_handstand', 3, 30, 120), hold('straddle_handstand', 3, 20, 120),
      hold('supported_handstand_shift', 2, '8/side', 60), hold('handstand_weight_shift', 6, '4/side', 120),
      hold('fingertip_one_arm_handstand', 3, '3/side', 120)], 'upper'),
    ('hefesto_steps', 'Hefesto Steps', 'skills', 4, 8,
     'Korean dips and assisted Hefestos, from partial to full range.', 'bar',
     [hold('assisted_supinated_german_hang', 2, 15, 150), reps('assisted_korean_dip', 3, 4, 150),
      reps('assisted_partial_hefesto', 4, 2, 180), reps('assisted_hefesto', 2, 2, 180), hold('german_hang', 2, 30, 60)],
     'upper'),
    ('full_front_lever', 'Full Front Lever', 'skills', 5, 9,
     'Full front lever holds after straddle sets, then lever rows.', 'bar',
     [hold('straddle_front', 2, 8, 150), hold('full_front', 5, 5, 180), reps('tuck_front_lever_row', 3, 6, 120),
      reps('pullup', 2, 10, 90), hold('hollow_body_hold', 2, 50, 60)], 'upper'),
    ('back_lever_and_hefesto', 'Back Lever and Hefesto', 'skills', 5, 9,
     'Full back lever holds, then Hefesto singles and skin-the-cats.', 'bar',
     [hold('german_hang', 2, 30, 60), hold('full_back', 5, 10, 150), reps('hefesto', 4, 1, 180),
      reps('skin_the_cat', 2, 5, 90, 'slow'), reps('toes_to_bar', 2, 12, 75)], 'upper'),
    ('full_flag', 'Full Flag', 'skills', 5, 9,
     'Short full flag attempts after tuck and straddle sets, then negatives.', 'hands',
     [hold('tuck_flag', 2, '10/side', 120), hold('straddle_flag', 3, '6/side', 150), hold('full_flag', 6, '4/side', 150),
      reps('flag_negative', 2, '1/side', 150, '5010'), hold('straight_side_plank', 2, '45/side', 45)], 'upper'),
    ('one_arm_handstand', 'One-Arm Handstand', 'skills', 5, 8,
     'Fingertip and straddle one-arm holds, then short one-arm attempts.', 'hands',
     [hold('freestanding_handstand', 3, 30, 120), hold('fingertip_one_arm_handstand', 3, '6/side', 120),
      hold('straddle_one_arm_handstand', 4, '5/side', 120), hold('one_arm_handstand', 8, '4/side', 120)], 'upper'),
    ('full_planche', 'Full Planche', 'skills', 5, 9,
     'Full planche holds after straddle sets, then planche push-ups and dips.', 'planche',
     [hold('straddle_planche', 3, 5, 180), hold('full_planche', 5, 4, 180), reps('pseudo_planche_pushup', 2, 12, 120),
      reps('parallel_dip', 2, 15, 90), hold('hollow_body_hold', 2, 45, 60)], 'upper'),

    ('floor_only_15', 'Floor Only 15', 'short', 1, 6,
     'No bar: squats, push-ups and planks in about 15 minutes.', 'short',
     [reps('squat', 3, 12, 45), reps('pushup', 3, 8, 45), hold('plank', 2, 20, 20)], 'short'),
    ('pullup_minutes', 'Pull-Up Minutes', 'short', 2, 7,
     'Every minute on the minute: 3 pull-ups, for 8 minutes.', 'short_bar',
     [minute('pullup', 8, 3)], 'short'),
    ('quick_ladder', 'Quick Ladder', 'short', 2, 7,
     'No bar: push-ups 3 up to 9 and back, squats 4 up to 12 and back.', 'short',
     [reps('pushup', 5, '3-6-9-6-3', 30), reps('squat', 5, '4-8-12-8-4', 30)], 'short'),
    ('pistol_minutes', 'Pistol Minutes', 'short', 3, 7,
     'Every minute on the minute: 2 pistol squats a side, for 8 minutes.', 'short_legs',
     [minute('pistol_squat', 8, '2/side')], 'short_legs'),
    ('quick_handstand', 'Quick Handstand', 'short', 3, 6,
     'A wall hold, then short freestanding handstand attempts.', 'short_hands',
     [hold('wall_handstand', 1, 35, 60), hold('freestanding_handstand', 5, 15, 60)], 'short'),
    ('quick_planche', 'Quick Planche', 'short', 3, 7,
     'No bar: planche leans and pseudo-planche push-ups, three sets each.', 'short_hands',
     [hold('planche_lean', 3, 15, 60), reps('pseudo_planche_pushup', 3, 7, 75)], 'short'),
    ('toes_to_bar_minutes', 'Toes-to-Bar Minutes', 'short', 4, 8,
     'Every minute on the minute: 5 toes-to-bar, for 8 minutes.', 'short_bar',
     [minute('toes_to_bar', 8, 5)], 'short'),
    ('archer_ladder', 'Archer Ladder', 'short', 4, 8,
     'Archer pull-ups 1 up to 3 a side and back, then archer push-ups.', 'short_bar',
     [reps('archer_pullup', 5, '1-2-3-2-1/side', 40), reps('archer_pushup', 5, '2-3-4-3-2/side', 40)], 'short'),
    ('one_arm_one_leg', 'One Arm, One Leg', 'short', 5, 8,
     'Every minute on the minute: one-arm push-ups, shrimp squats in turn.', 'short',
     circuit(3, minute('one_arm_pushup', 1, '3/side'), minute('shrimp_squat', 1, '5/side')), 'short'),

    ('deep_squat', 'Deep Squat', 'mobility', 1, 3,
     'Ankles, hips and a long deep squat, for easier squats and pistols.', 'easy',
     [reps('ankle_rocks', 2, '10/side', 10, 'controlled'), reps('hip_circles', 2, 8, 10, 'controlled'),
      hold('deep_squat_hold', 3, 30, 20, 'easy'), reps('cossack_squat', 2, '4/side', 30, 'slow'),
      stretch('calf_stretch', 30, 2), stretch('quad_stretch'), stretch('adductor_stretch', 30, 2)], 'easy'),
    ('upper_back', 'Upper Back', 'mobility', 1, 3,
     'Spine rotations and extensions, wall slides and easy stretches.', 'easy',
     [reps('cat_cow', 2, 6, 10, 'slow'), reps('thoracic_rotation', 2, '8/side', 10, 'slow'),
      reps('thoracic_extension', 2, 6, 20, 'slow'), reps('bird_dog', 2, '6/side', 20, 'slow'),
      reps('wall_slide', 2, 10, 20, 'slow'), stretch('lat_stretch'), stretch('supine_twist'),
      hold('child_pose', 1, 45, 0, 'easy')], 'easy'),
    ('pike_and_pancake', 'Pike and Pancake', 'mobility', 2, 4,
     'Pike and pancake stretches with active lifts, for L-sits and V-sits.', 'easy',
     [reps('seated_pike_lift', 2, 10, 30), reps('pancake_compression', 2, 10, 30), stretch('hamstring_stretch', 45, 2),
      stretch('adductor_stretch', 45, 2), reps('ninety_ninety_hip_switch', 2, 8, 10, 'slow'),
      stretch('ninety_ninety_hip_stretch', 30, 2), hold('deep_squat_hold', 1, 45, 0, 'easy')], 'easy'),
    ('shoulder_extension', 'Shoulder Extension', 'mobility', 2, 4,
     'Shoulder circles, wall work, easy skin-the-cats and German hangs.', 'easy',
     [reps('shoulder_cars', 2, 5, 10, 'slow'), reps('wall_slide', 2, 10, 20, 'slow'),
      hold('wall_shoulder_flexion', 2, 30, 20, 'slow'), reps('skin_cat_partial', 2, 3, 45, 'slow'),
      hold('german_hang', 3, 15, 45, 'easy'), stretch('chest_stretch', 30, 2), stretch('lat_stretch')], 'easy'),
    ('straight_arm_prep', 'Straight-Arm Prep', 'mobility', 3, 4,
     'Wrists and shoulders for planche and lever days: light leans and hangs.', 'easy',
     [reps('wrist_circles', 2, 10, 10, 'controlled'), reps('wrist_rocks', 2, 10, 10, 'controlled'),
      reps('wrist_pushup', 2, 10, 20), reps('scapular_pushup', 2, 10, 20), hold('planche_lean', 2, 10, 45, 'easy'),
      hold('german_hang', 2, 20, 45, 'easy'), hold('wall_shoulder_flexion', 2, 30, 20, 'slow'),
      stretch('forearm_stretch', 30, 2), stretch('chest_stretch')], 'easy'),
]


def seconds(step):
    _, sets, count, held, rest, _, _ = step
    rep_values, rep_side = dose(count)
    hold_values, hold_side = dose(held)
    total = 15
    for index in range(sets):
        work = 0
        if rep_values:
            work += rep_values[index if len(rep_values) > 1 else 0] * 3 * (2 if rep_side else 1)
        if hold_values:
            work += hold_values[index if len(hold_values) > 1 else 0] * (2 if hold_side else 1)
        total += work + (rest or 0)
    return total


def check(step, where):
    movement, sets, count, held, _, _, easier = step
    assert movement in LIB, f'{where}: unknown movement {movement}'
    assert easier is None or easier in LIB, f'{where}: unknown easier {easier}'
    assert (count is None) != (held is None), f'{where}: {movement} needs reps or seconds'
    for value in (count, held):
        values, _ = dose(value)
        assert len(values) in (0, 1, sets), f'{where}: {movement} has {len(values)} doses for {sets} sets'


def build():
    seen = set()
    out = []
    for slug, name, group, grade, rpe, description, warmup, main, cooldown in WORKOUTS:
        assert slug not in seen and slug.replace('_', '').isalnum() and slug == slug.lower(), slug
        seen.add(slug)
        assert group in GROUPS and grade in (1, 2, 3, 4, 5) and len(name) <= 24 and len(description) <= 80, slug
        steps = WARMUP[warmup] + main + COOLDOWN[cooldown]
        for step in steps:
            check(step, slug)
        minutes = max(5, math.ceil(sum(map(seconds, steps)) / 60 / 5) * 5)
        assert 10 <= minutes <= 60, f'{slug}: {minutes} min'
        out.append({
            'id': f'ready:{slug}', 'title': {'en': name}, 'description': {'en': description},
            'group': group, 'grade': grade,
            'schedule': {'minutes': minutes, 'rpe': rpe},
            'warmup': WARMUP[warmup], 'main': main, 'cooldown': COOLDOWN[cooldown],
            'safety': {'en': SAFETY},
        })
    out.sort(key=lambda w: (GROUPS.index(w['group']), w['grade']))
    return out


def write(workouts):
    """One field a line and one step a line, so a change reads as a small diff."""
    blocks = []
    for workout in workouts:
        fields = []
        for key, value in workout.items():
            if key in ('warmup', 'main', 'cooldown'):
                steps = ',\n'.join('    ' + json.dumps(step, ensure_ascii=False) for step in value)
                fields.append(f'   "{key}": [\n{steps}\n   ]')
            else:
                fields.append(f'   "{key}": {json.dumps(value, ensure_ascii=False)}')
        blocks.append('  {\n' + ',\n'.join(fields) + '\n  }')
    OUT.write_text('{\n "schema_version": 1,\n "workouts": [\n' + ',\n'.join(blocks) + '\n ]\n}\n',
                   encoding='utf-8', newline='\n')


if __name__ == '__main__':
    workouts = build()
    write(workouts)
    for w in workouts:
        print(f"{w['group']:9} {w['grade']} {w['schedule']['minutes']:3} min  {w['title']['en']}")
    print(len(workouts), 'workouts ->', OUT.relative_to(ROOT))
