[CmdletBinding()]
param(
    [string]$CurriculumDirectory = "curriculum",
    [string]$OutputPath = "generated/curriculum_v2.json"
)

# Joins the classes that tools/build_curriculum.py writes (curriculum/program.json) with the movement library and the
# level definitions into the app's catalog. Every class is authored for Street Gymnastics; nothing is read from, or
# shipped about, any other app.

$ErrorActionPreference = "Stop"
$targetProgramsPerLevel = 100

function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw "Required JSON file not found: $Path" }
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $Path | ConvertFrom-Json
}

# Day ids are frozen (2.5): athletes' completed days, history, exams and cloud copies refer to them. Edit a day's
# content in place; never insert, remove or reorder days or move an exam. DayIdsTest fails if this changes.
function Stable-ProgramId([int]$Level, [int]$Number) {
    return "sg:l{0:D2}:p{1:D4}" -f $Level, $Number
}

$catalogMetadata = Read-JsonFile (Join-Path $CurriculumDirectory "catalog_metadata.json")
$classProgression = Read-JsonFile (Join-Path $CurriculumDirectory "class_progression.json")
$authoredBySlot = @{}
$movementById = [ordered]@{}
$coachingProfiles = New-Object System.Collections.ArrayList
$batchFiles = Get-ChildItem -LiteralPath $CurriculumDirectory -Filter "*.json" -File | Sort-Object Name

foreach ($file in $batchFiles) {
    $batch = Read-JsonFile $file.FullName
    foreach ($movement in @($batch.movements)) {
        if ($null -eq $movement) { continue }
        $id = [string]$movement.id
        if ([string]::IsNullOrWhiteSpace($id)) { throw "Movement without an id in $($file.Name)" }
        if ($movementById.Contains($id)) { throw "Duplicate movement id $id in $($file.Name)" }
        $movementById[$id] = $movement
    }
    foreach ($profile in @($batch.coaching_profiles)) {
        if ($null -ne $profile) { [void]$coachingProfiles.Add($profile) }
    }
    foreach ($program in @($batch.programs)) {
        if ($null -eq $program) { continue }
        $level = [int]$program.level
        $number = [int]$program.number
        $slot = "$level-$number"
        if ($authoredBySlot.ContainsKey($slot)) { throw "Duplicate authored program slot $slot in $($file.Name)" }
        $authoredBySlot[$slot] = $program
    }
}

if ($movementById.Count -eq 0) { throw "No movement definitions were found" }

$levelDefinitions = @($catalogMetadata.level_definitions)
$assembledLevels = New-Object System.Collections.ArrayList
$programCount = 0

foreach ($levelDefinition in $levelDefinitions) {
    $levelNumber = [int]$levelDefinition.number
    $programs = New-Object System.Collections.ArrayList

    # Class N of a level is the authored class numbered N: the app position and the day id follow the number.
    for ($number = 1; $number -le $targetProgramsPerLevel; $number++) {
        $slot = "$levelNumber-$number"
        if (-not $authoredBySlot.ContainsKey($slot)) { throw "No authored class for Level $levelNumber class $number" }
        $authored = $authoredBySlot[$slot]
        $assembled = [ordered]@{
            id = Stable-ProgramId $levelNumber $number
            authored_id = [string]$authored.id
            level = $levelNumber
            number = $number
            title = $authored.title
            description = $authored.description
            schedule = $authored.schedule
            warmup = $authored.warmup
            practice = $authored.practice
            main = $authored.main
            cooldown = $authored.cooldown
            readiness = $(if ($null -ne $authored.readiness) { $authored.readiness } else { $catalogMetadata.progression_readiness })
            safety = $(if ($null -ne $authored.safety) { $authored.safety } else { $catalogMetadata.training_recovery })
        }
        [void]$programs.Add($assembled)
    }
    $extra = @($authoredBySlot.Values | Where-Object { [int]$_.level -eq $levelNumber -and [int]$_.number -gt $targetProgramsPerLevel })
    if ($extra.Count -gt 0) { throw "Level $levelNumber has $($extra.Count) authored classes past class $targetProgramsPerLevel" }

    $progressionLimits = $classProgression.levels | Where-Object { [int]$_.level -eq $levelNumber } | Select-Object -First 1
    if ($null -eq $progressionLimits) { throw "Missing workload limits for level $levelNumber" }
    foreach ($program in $programs) {
        # The plan writes each class's practice and main as the athlete does them; the assembler only checks the budget.
        if ($null -eq $program.practice) { throw "Level $levelNumber program $($program.number) has no practice list" }
        $sets = 0
        foreach ($step in @($program.practice) + @($program.main)) {
            if ([int]$step[1] -gt [int]$progressionLimits.sets_per_exercise) { throw "Level $levelNumber program $($program.number) has a step above $($progressionLimits.sets_per_exercise) sets" }
            $sets += [int]$step[1]
        }
        # The Full Battle (L10:090, a challenge class) runs the basics, five rounds of four stations, outside the class budget.
        if ($sets -gt [int]$progressionLimits.main_sets_including_practice -and [string]$program.schedule.day_type -ne "full_battle") {
            throw "Level $levelNumber program $($program.number) has $sets sets; the budget is $($progressionLimits.main_sets_including_practice)"
        }
        $program["practice"] = @($program.practice)
    }

    $programCount += $programs.Count
    [void]$assembledLevels.Add([ordered]@{
        id = "sg:l{0:D2}" -f $levelNumber
        number = $levelNumber
        name = $levelDefinition.name
        description = $levelDefinition.description
        programs = $programs
    })
}

$result = [ordered]@{
    schema_version = "2.0.0"
    summary = [ordered]@{
        level_count = $assembledLevels.Count
        program_count = $programCount
        movement_count = $movementById.Count
    }
    step_tuple = @("movement_id", "sets", "reps", "seconds", "rest_seconds", "tempo", "regression_id")
    movement_library = @($movementById.Values)
    coaching_profiles = @($coachingProfiles)
    levels = $assembledLevels
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
$json = $result | ConvertTo-Json -Depth 100 -Compress
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutputPath), $json + [Environment]::NewLine, $utf8WithoutBom)

Write-Host "Assembled $($result.summary.program_count) programs across $($result.summary.level_count) levels."
Write-Host "Movement definitions: $($movementById.Count)."
Write-Host "Output: $OutputPath"
