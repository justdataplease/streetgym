"""Writes docs/ACHIEVEMENTS.html: what the program gives, skill by skill and belt by belt, counted from the classes.

Reads curriculum/program.json (from tools/build_curriculum.py) and counts each belt the way the app does: sets at the
target or more, the belt below already held, every set done as written on the Normal toggle, and a belt held on a harder
rung of a ladder held on its easier rungs too. Run it after the generator.

    python tools/build_achievements_page.py                  # writes docs/ACHIEVEMENTS.html
    python tools/build_achievements_page.py --fragment X     # also writes the page body alone (for an artifact) to X
"""
import html
import importlib.util
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_curriculum', ROOT / 'tools/build_curriculum.py')
bc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bc)

programs = json.loads((ROOT / 'curriculum/program.json').read_text(encoding='utf-8'))['programs']
landed, counts, via = bc.belts(programs)
BADGES = (ROOT / 'tools/achievement_badges.html').read_text(encoding='utf-8').split('-->', 1)[1].strip()
NEED = bc.BELT_SETS
VERSION = re.search(r'versionName = "([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)

# The app's families (SkillBook.DEFAULT_FAMILIES), in its order, each rung easiest first.
NAMES = ['Pull-up', 'Muscle-up', 'Front lever', 'Back lever', 'Dip', 'Push-up', 'Handstand push-up', 'Planche', 'Handstand',
         'Pistol squat', 'Toes to bar', 'L-sit', 'Dragon flag', 'Human flag', 'Shrimp squat', 'Hefesto']
SKILL_COUNT = len(bc.SKILLS)
FAMILIES = list(zip(NAMES, bc.BELT_LADDERS, strict=True))


def pos(L, n):
    return (L - 1) * 100 + n


def at(x):
    return f'L{x[0]}:{x[1]:03d}'


# The first class where a set of each skill reaches each belt's target: where the athlete first meets that belt (the
# owner, review 8). A belt held through a harder rung is first met where that rung first reaches its own target.
first_seen = {sid: [None] * 5 for sid in bc.SKILLS}
for p in programs:
    for st in p['practice'] + p['main']:
        sid = st[0]
        if sid in bc.SKILLS and st[5] != 'readiness_check':
            v = int(str(st[2] if st[2] is not None else st[3]).split('/')[0])
            for k, t in enumerate(bc.SKILLS[sid]['t']):
                if v >= t and first_seen[sid][k] is None:
                    first_seen[sid][k] = (p['level'], p['number'])
for sid in bc.SKILLS:
    for k in range(5):
        if first_seen[sid][k] is None and via[sid][k]:
            first_seen[sid][k] = first_seen[via[sid][k]][k]


# The first class whose set reaches each skill's Elite target: the max. Every skill reaches it once, or holds Elite
# itself through a harder rung of its ladder.
first_max = {}
for p in programs:
    for st in p['practice'] + p['main']:
        sid = st[0]
        if sid in bc.SKILLS and sid not in first_max and st[5] != 'readiness_check':
            v = int(str(st[2] if st[2] is not None else st[3]).split('/')[0])
            if v >= bc.SKILLS[sid]['t'][4]:
                first_max[sid] = (p['level'], p['number'])
assert all(sid in first_max or landed[sid][4] for sid in bc.SKILLS), [s for s in bc.SKILLS if s not in first_max and not landed[s][4]]


def target(sid, k):
    s = bc.SKILLS[sid]
    t = s['t'][k]
    unit = ' s' if s['unit'] == 's' else (' rep' if t == 1 else ' reps')
    return f'{t}{unit}' + (' a side' if s['side'] else '')


def cell(sid, k):
    x = landed[sid][k]
    t = html.escape(target(sid, k))
    seen = first_seen[sid][k]
    first = f'<span class="seen">first {at(seen)}</span>' if seen else ''
    if x:
        through = via[sid][k]
        note = f'<span class="via">via {html.escape(bc.LIB[through]["name"]["en"])}</span>' if through else ''
        return (f'<div class="chip done{" carried" if through else ""}"><b>{at(x)}</b><span>{t}</span>{first}{note}'
                f'<span class="mo" data-c="{pos(*x)}"></span></div>')
    # Not completed in the classes: how far the classes take it, by the sets they give at the target (the owner).
    pct = min(99, round(100 * counts[sid][k] / NEED[k]))
    first = first or '<span class="seen">not in the classes</span>'
    return (f'<div class="chip window"><b>{pct}%</b><span class="bar"><i style="width:{pct}%"></i></span>'
            f'<span>{t} · {counts[sid][k]}/{NEED[k]} sets</span>{first}'
            + (f'<span class="mo" data-c="{pos(*seen)}"></span>' if seen else '') + '</div>')


rows = []
for fam, ids in FAMILIES:
    rows.append(f'<tr class="fam"><th scope="rowgroup" colspan="6">{html.escape(fam)}</th></tr>')
    for sid in ids:
        rows.append(f'<tr><th scope="row">{html.escape(bc.LIB[sid]["name"]["en"])}</th>' + ''.join(f'<td>{cell(sid, k)}</td>' for k in range(5)) + '</tr>')

inplan = sum(1 for v in landed.values() for x in v if x)
per_level = [sum(1 for v in landed.values() for x in v if x and x[0] == L) for L in range(1, 11)]
first_belt = min((x for v in landed.values() for x in v if x), key=lambda x: pos(*x))
beginner_all = max((v[0] for v in landed.values()), key=lambda x: pos(*x))

MILESTONES = [
    (first_belt, 'First belt: hollow body Beginner. The push-up and squat follow within a few classes.'),
    (landed['pullup'][0], 'Pull-up Beginner: ten sets of 3 strict pull-ups.'),
    (landed['strict_muscleup'][0], 'Strict muscle-up Beginner.'),
    (landed['freestanding_handstand'][0], 'Freestanding handstand Beginner, 5 s.'),
    (landed['full_back'][0], 'Full back lever Beginner, 3 s.'),
    ((7, 100), 'The Advanced Athlete exam.'),
    (landed['full_flag'][0], 'Full human flag Beginner, 3 s a side.'),
    (landed['shrimp_squat'][0], 'Shrimp squat Beginner, 2 a side.'),
    (landed['full_front'][0], 'Full front lever Beginner, 3 s.'),
    (landed['straddle_planche'][0], 'Straddle planche Beginner, 3 s.'),
    (landed['one_arm_pullup'][0], 'One-arm pull-up Beginner, 1 a side.'),
    (landed['hefesto'][0], 'Hefesto Beginner, 1 rep.'),
    (landed['full_planche'][0], 'Full planche Beginner, 3 s.'),
    (landed['one_arm_handstand'][0], 'One-arm handstand Beginner, 3 s a side.'),
    ((9, 100), 'The Level 9 exam tests every full shape.'),
    ((10, 90), 'The Full Battle: five rounds of 10 pull-ups, 20 dips, 30 push-ups and 40 squats.'),
    ((10, 100), 'The Level 10 test checks every skill family at its top rung. By now every skill holds Master, and every Elite is at least 70% of the way.'),
]
mil = ''.join(f'<li><b>{at(x)}</b><span class="mo" data-c="{pos(*x)}"></span><span>{html.escape(t)}</span></li>' for x, t in MILESTONES)
lvl = ''.join(f'<td class="num">{n}</td>' for n in per_level)
reached = sum(1 for sid in bc.SKILLS if sid in first_max or landed[sid][4])
elite_in = sum(1 for sid in bc.SKILLS if landed[sid][4])

CSS = """
:root {
  --bg: #E3E1DB; --surface: #F2F1EC; --ink: #1B1B19; --muted: #66645D; --line: #CBC8BF; --tape: #F3C318;
  --ok: #23934F; --ok-ink: #18703B;
  --display: "Anton", "Impact", "Arial Narrow", sans-serif;
  --body: "Albert Sans", "Segoe UI", system-ui, sans-serif;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #151513; --surface: #1F1E1B; --ink: #ECEAE3; --muted: #A3A097; --line: #34332E; --ok: #45C27A; --ok-ink: #6FD89A; color-scheme: dark; } }
:root[data-theme="dark"] {
  --bg: #151513; --surface: #1F1E1B; --ink: #ECEAE3; --muted: #A3A097; --line: #34332E; --ok: #45C27A; --ok-ink: #6FD89A; color-scheme: dark; }
body { margin: 0; background: var(--bg); color: var(--ink); font-family: var(--body); font-size: 15px; line-height: 1.5;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 28px 64px; }
main { max-width: 1120px; margin: 0 auto; display: grid; gap: 40px; }
main > * { min-width: 0; }
h1, h2 { font-family: var(--display); font-weight: 400; text-transform: uppercase; letter-spacing: .01em; line-height: 1.02; margin: 0; text-wrap: balance; }
h1 { font-size: clamp(38px, 7vw, 64px); }
h2 { font-size: clamp(26px, 4vw, 34px); }
p { margin: 0; max-width: 68ch; }
.lede { display: grid; gap: 14px; }
.lede p { color: var(--muted); font-size: 17px; }
.eyebrow { font-size: 12px; font-weight: 700; letter-spacing: .14em; text-transform: uppercase; color: var(--muted); }
.pace { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.pace span { font-size: 14px; color: var(--muted); margin-right: 4px; }
.pace button { font: 600 14px var(--body); color: var(--ink); background: var(--surface); border: 1px solid var(--line); border-radius: 999px; padding: 6px 14px; cursor: pointer; }
.pace button[aria-pressed="true"] { background: var(--ink); color: var(--bg); border-color: var(--ink); }
.facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; }
.fact { background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 14px 16px; display: grid; gap: 4px; align-content: start; }
.fact b { font-family: var(--display); font-weight: 400; font-size: 34px; line-height: 1; }
.fact > span { color: var(--muted); font-size: 14px; }
section > .head { display: grid; gap: 8px; margin-bottom: 16px; }
.staged { color: var(--muted); font-size: 14px; }
.miles { list-style: none; margin: 0; padding: 0; display: grid; gap: 0; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.miles li { display: grid; grid-template-columns: 84px 110px 1fr; gap: 12px; padding: 10px 14px; border-bottom: 1px solid var(--line); align-items: baseline; }
.miles li:last-child { border-bottom: 0; }
.miles b { font-variant-numeric: tabular-nums; }
.miles .mo { color: var(--muted); font-size: 13px; }
@media (max-width: 560px) { .miles li { grid-template-columns: 74px 1fr; } .miles li > span:last-child { grid-column: 1 / -1; } }
.scroll { overflow-x: auto; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
table { border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }
th, td { padding: 10px 12px; text-align: left; vertical-align: top; border-bottom: 1px solid var(--line); }
tbody tr:last-child th, tbody tr:last-child td { border-bottom: 0; }
thead th { font-size: 12px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); vertical-align: bottom; white-space: nowrap; }
.num { text-align: right; white-space: nowrap; }
.plate { width: 26px; height: auto; flex: none; }
.plate.wide { width: 46px; }
.map { min-width: 640px; }
.map th, .map td { padding: 6px 6px; }
.map tbody th { font-weight: 600; font-size: 14px; min-width: 150px; }
.map thead th { text-align: center; }
.map thead th span { display: block; margin-top: 4px; }
.map thead th small { display: block; font-weight: 500; letter-spacing: 0; text-transform: none; }
.map tr.fam th { font-family: var(--display); font-weight: 400; font-size: 17px; text-transform: uppercase; padding-top: 14px; }
.chip { display: grid; gap: 1px; text-align: center; border: 2px solid transparent; border-radius: 8px; padding: 5px 4px; }
.chip b { font-size: 15px; }
.chip span { font-size: 11px; color: var(--muted); white-space: nowrap; }
.chip.done { border-color: var(--ok); }
.chip.done b { color: var(--ok-ink); }
.chip.carried { border-style: dashed; }
.chip .via { color: var(--ok-ink); font-weight: 600; white-space: normal; }
.key-via { display: inline-block; width: 22px; height: 14px; border: 2px dashed var(--ok); border-radius: 5px; vertical-align: -2px; }
.chip.window b { color: var(--muted); font-weight: 600; font-size: 13px; }
.chip .seen { color: var(--ink); }
.chip .bar { display: block; height: 4px; border-radius: 2px; background: var(--line); margin: 3px 6px 1px; overflow: hidden; }
.chip .bar i { display: block; height: 100%; background: var(--muted); }
.key-ok { display: inline-block; width: 22px; height: 14px; border: 2px solid var(--ok); border-radius: 5px; vertical-align: -2px; }
.notes { margin: 0; padding-left: 1.1em; display: grid; gap: 6px; max-width: 76ch; color: var(--muted); font-size: 14px; }
"""

JS = """
(() => {
  const btns = [...document.querySelectorAll('.pace button')];
  const fmt = (c, w) => { const m = c / (w * 4.345); return m < 1 ? 'week ' + Math.max(1, Math.ceil(c / w)) : 'month ' + Math.max(1, Math.round(m)); };
  function set(w) {
    btns.forEach(b => b.setAttribute('aria-pressed', String(+b.dataset.w === w)));
    document.querySelectorAll('[data-c]').forEach(e => { e.textContent = '≈ ' + fmt(+e.dataset.c, w); });
    document.getElementById('years').textContent = (1000 / (w * 52.18)).toFixed(1);
    try { localStorage.setItem('sg-pace', String(w)); } catch (e) {}
  }
  btns.forEach(b => b.addEventListener('click', () => set(+b.dataset.w)));
  let w = 5; try { w = +localStorage.getItem('sg-pace') || 5; } catch (e) {}
  set([5, 6, 7].includes(w) ? w : 6);
})();
"""

BODY = f"""<title>What You Can Achieve</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anton&family=Albert+Sans:wght@400;500;600;700&display=swap">
<style>{CSS}</style>
<main>
<header class="lede">
  <span class="eyebrow">Street Gymnastics {VERSION} · the program as written</span>
  <h1>What you can achieve</h1>
  <p>Every date on this page is counted from the app's 1,000 classes: if you do every set as written on the Normal toggle, this is the class where each belt lands. Repeating a block after a missed exam moves it later, and Harder days or your own workouts move it sooner.</p>
  <div class="pace" role="group" aria-label="Classes a week"><span>Classes a week</span><button type="button" data-w="2">2</button><button type="button" data-w="3">3</button><button type="button" data-w="5">5</button><button type="button" data-w="7">7</button></div>
</header>
<section class="facts" aria-label="In numbers">
  <div class="fact"><b>{inplan}/{5 * SKILL_COUNT}</b><span>belts that land inside the program</span></div>
  <div class="fact"><b>{elite_in}/{SKILL_COUNT}</b><span>Elite belts inside the program; every other Elite at least 70% of the way</span></div>
  <div class="fact"><b>{at(first_belt)}</b><span>your first belt</span></div>
  <div class="fact"><b>L9:100</b><span>every full shape tested: planche, both levers, the flag, the one-arm pull-up</span></div>
  <div class="fact"><b><span id="years">3.2</span> yrs</b><span>for all 1,000 classes at this pace</span></div>
</section>
<section aria-labelledby="m-h"><div class="head"><h2 id="m-h">The milestones</h2>
  <p class="staged">The class where each one comes, and roughly when at the pace you picked.</p></div>
  <ol class="miles">{mil}</ol></section>
<section aria-labelledby="t-h"><div class="head"><h2 id="t-h">Every skill, every belt</h2>
  <p class="staged">A belt is its target in good form: 10 sets for Beginner, 15 for Intermediate, 20 for Advanced, 25 for Master and 30 for Elite. A set on a harder rung counts for the easier rungs of its ladder too, at the belt it reaches: a 6 s full planche is an Elite set of the straddle planche. <span class="key-ok"></span> A green outline is a belt the classes give: the class where it lands, its target and when. <span class="key-via"></span> A dashed one comes through a harder rung of the same ladder: a belt held on the full back lever is held on the straddle and tuck back levers too, never higher. "first" is the class where you first do a set at that belt's target. The classes take every skill to Master. A grey cell is an Elite the classes take most of the way, the top rung of a ladder: its percentage is the sets they give at the target out of the 30 it needs, and a repeated block or your own training does the rest.</p></div>
  <div class="scroll"><table class="map">{BADGES}<tbody>{''.join(rows)}</tbody></table></div></section>
<section aria-labelledby="l-h"><div class="head"><h2 id="l-h">Belts by level</h2></div>
  <div class="scroll"><table><thead><tr><th scope="col">Level</th>{''.join(f'<th scope="col" class="num">{L}</th>' for L in range(1, 11))}</tr></thead>
  <tbody><tr><th scope="row">Belts that land</th>{lvl}</tr></tbody></table></div></section>
<section aria-labelledby="n-h"><div class="head"><h2 id="n-h">Read it honestly</h2></div>
  <ul class="notes">
    <li>These are working-set targets, done with a rep or two still in you, not single maximal efforts. Elite is that target thirty times over.</li>
    <li>The full planche and the one-arm pull-up come at about the research medians for committed, lighter athletes. Most people take longer: expect block repeats there.</li>
    <li>Heavier, taller, female and over-40 athletes typically take 20–50% longer on the levers, flag, planche, muscle-up and one-arm work. The handstand, the core and the legs change much less.</li>
    <li>The last of the {SKILL_COUNT} first belts lands at {at(beginner_all)}.</li>
  </ul></section>
</main>
<script>{JS}</script>
"""

page = f'<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n{BODY}\n</html>\n'
(ROOT / 'docs/ACHIEVEMENTS.html').write_text(page, encoding='utf-8')
if len(sys.argv) > 2 and sys.argv[1] == '--fragment':
    Path(sys.argv[2]).write_text(BODY, encoding='utf-8')
print('docs/ACHIEVEMENTS.html', len(page), 'bytes;', inplan, 'belts in plan; first belt', at(first_belt))
