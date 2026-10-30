"""Writes Pro's ready workouts (Training > Library) to app/src/main/assets/workouts.json.

Each workout is a class like the program's: warm-up, main work and cool-down in the curriculum's step tuples
([movement_id, sets, reps, seconds, rest_seconds, tempo, regression_id]), using only movements of the movement library,
so every exercise has its animation, notes and easier option. The workouts are this project's own (the owner,
2026-10-30); their names and doses aren't taken from any other app. Minutes are worked out here, like a custom day's:
about 3 s a rep, both sides for "/side", plus rest and 15 s to change exercises, rounded up to 5 minutes.

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
}

# (slug, name, group, grade 1-3, rpe, description, warm-up, main, cool-down)
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
]


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
        assert group in GROUPS and grade in (1, 2, 3) and len(name) <= 24 and len(description) <= 80, slug
        steps = WARMUP[warmup] + main + COOLDOWN[cooldown]
        for step in steps:
            check(step, slug)
        minutes = max(5, math.ceil(sum(map(seconds, steps)) / 60 / 5) * 5)
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
