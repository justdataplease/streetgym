[CmdletBinding()]
param(
    [string]$BaselinePath = "street_gymnastic_programs_levels.json",
    [string]$CurriculumDirectory = "curriculum",
    [string]$OutputPath = "generated/curriculum_v2.json"
)

$ErrorActionPreference = "Stop"
$expectedBaselineSha256 = "AC8AAF41F477050EDDF9387DF1CD214FCBE3A29608AE4D5AE1E0C34D9F5EDA3D"
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

function Localized([string]$English) {
    return [ordered]@{ en = $English }
}

$baselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselinePath).Hash
if ($baselineHash -ne $expectedBaselineSha256) {
    throw "Baseline SHA-256 mismatch. Expected $expectedBaselineSha256, got $baselineHash"
}

$baseline = Read-JsonFile $BaselinePath
$catalogMetadata = Read-JsonFile (Join-Path $CurriculumDirectory "catalog_metadata.json")
$classProgression = Read-JsonFile (Join-Path $CurriculumDirectory "class_progression.json")
# App positions ("level-number") where an authored class takes the place of an original-source class. Since round 9
# every original-source position is replaced: tools/build_curriculum.py writes all 1,000 classes.
$replacedSourceSlots = @($catalogMetadata.replaced_source_slots | ForEach-Object { [string]$_ })
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
$authenticCount = 0
$reconstructedCount = 0
$replacedSourceCount = 0
$newLevelCount = 0
$newExtensionCount = 0
$archivedLegacyPlaceholderCount = 0

foreach ($levelDefinition in $levelDefinitions) {
    $levelNumber = [int]$levelDefinition.number
    $programs = New-Object System.Collections.ArrayList

    if ($levelNumber -le 3) {
        $baselineLevel = $baseline.levels | Where-Object { [int]$_.number -eq $levelNumber } | Select-Object -First 1
        if ($null -eq $baselineLevel) { throw "Baseline level $levelNumber is missing" }

        $archivedLegacyPrograms = @($baselineLevel.programs | Where-Object {
            [int]$_.number -gt $targetProgramsPerLevel
        })
        if (@($archivedLegacyPrograms | Where-Object { [bool]$_.has_details }).Count -gt 0) {
            throw "Level $levelNumber would archive authentic APK content above slot $targetProgramsPerLevel"
        }
        $archivedLegacyPlaceholderCount += $archivedLegacyPrograms.Count
        $includedBaselinePrograms = @($baselineLevel.programs | Where-Object {
            [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })
        # catalog_metadata.json app_slot_order places a source slot at each app position (Level 2 runs its
        # reconstructed classes 31-70 before the original-source block). Source content, titles and legacy ids
        # follow their slot; reconstructed records are numbered by app position.
        $slotOrder = @($catalogMetadata.app_slot_order.levels | Where-Object { [int]$_.level -eq $levelNumber } | Select-Object -First 1)
        $baselineBySourceSlot = @{}
        foreach ($legacyProgram in $includedBaselinePrograms) { $baselineBySourceSlot[[int]$legacyProgram.number] = $legacyProgram }
        $orderedBaselinePrograms = $includedBaselinePrograms
        if ($slotOrder.Count -eq 1) {
            $sourceSlots = @(foreach ($range in @($slotOrder[0].source_slots)) { [int]$range[0]..[int]$range[1] })
            $expectedSlots = @($includedBaselinePrograms | ForEach-Object { [int]$_.number })
            if ((@($sourceSlots | Sort-Object) -join ",") -cne ($expectedSlots -join ",")) {
                throw "app_slot_order for Level $levelNumber must list every included source slot exactly once"
            }
            $orderedBaselinePrograms = @($sourceSlots | ForEach-Object { $baselineBySourceSlot[$_] })
        }

        $position = 0
        foreach ($legacyProgram in $orderedBaselinePrograms) {
            $position++
            $sourceSlot = [int]$legacyProgram.number
            $number = $position
            $stableId = Stable-ProgramId $levelNumber $number
            $slot = "$levelNumber-$number"

            if ([bool]$legacyProgram.has_details -and $slot -in $replacedSourceSlots) {
                # An authored class takes this original-source position; the original stays in the forensic export.
                if (-not $authoredBySlot.ContainsKey($slot)) { throw "No authored class replaces original-source slot $slot" }
                $authored = $authoredBySlot[$slot]
                $assembled = [ordered]@{
                    id = $stableId
                    authored_id = [string]$authored.id
                    origin = "reconstructed_placeholder"
                    level = $levelNumber
                    number = $number
                    title = $authored.title
                    description = $authored.description
                    schedule = $authored.schedule
                    legacy = [ordered]@{
                        workout_id = [string]$legacyProgram.id
                        pro = [bool]$legacyProgram.pro
                        has_details = $true
                    }
                    warmup = $authored.warmup
                    practice = $authored.practice
                    main = $authored.main
                    cooldown = $authored.cooldown
                    readiness = $(if ($null -ne $authored.readiness) { $authored.readiness } else { $catalogMetadata.progression_readiness })
                    safety = $(if ($null -ne $authored.safety) { $authored.safety } else { $catalogMetadata.training_recovery })
                }
                [void]$programs.Add($assembled)
                $replacedSourceCount++
                continue
            }

            if ([bool]$legacyProgram.has_details) {
                throw "Original-source slot $slot must be listed in catalog_metadata.json replaced_source_slots"
            }

            if (-not $authoredBySlot.ContainsKey($slot)) {
                throw "No LLM-authored replacement exists for legacy placeholder $slot"
            }
            $authored = $authoredBySlot[$slot]
            $assembled = [ordered]@{
                id = $stableId
                authored_id = [string]$authored.id
                origin = "reconstructed_placeholder"
                level = $levelNumber
                number = $number
                title = $authored.title
                description = $authored.description
                schedule = $authored.schedule
                legacy = [ordered]@{
                    workout_id = [string]$legacyProgram.id
                    pro = [bool]$legacyProgram.pro
                    has_details = $false
                }
                warmup = $authored.warmup
                practice = $authored.practice
                main = $authored.main
                cooldown = $authored.cooldown
                readiness = $(if ($null -ne $authored.readiness) { $authored.readiness } else { $catalogMetadata.progression_readiness })
                safety = $(if ($null -ne $authored.safety) { $authored.safety } else { $catalogMetadata.training_recovery })
            }
            [void]$programs.Add($assembled)
            $reconstructedCount++
        }

        $includedBaselineMaximum = @(
            $includedBaselinePrograms |
                ForEach-Object { [int]$_.number } |
                Measure-Object -Maximum
        )[0].Maximum
        $extensionPrograms = @($authoredBySlot.Values | Where-Object {
            [int]$_.level -eq $levelNumber -and
            [int]$_.number -gt $includedBaselineMaximum -and
            [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })
        foreach ($authored in $extensionPrograms) {
            $number = [int]$authored.number
            $assembled = [ordered]@{
                id = Stable-ProgramId $levelNumber $number
                authored_id = [string]$authored.id
                origin = "llm_extension"
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
            $newExtensionCount++
        }
    }
    else {
        $authoredPrograms = @($authoredBySlot.Values | Where-Object {
            [int]$_.level -eq $levelNumber -and [int]$_.number -le $targetProgramsPerLevel
        } | Sort-Object { [int]$_.number })
        if ($authoredPrograms.Count -eq 0) { throw "No LLM-authored programs exist for new level $levelNumber" }
        foreach ($authored in $authoredPrograms) {
            $number = [int]$authored.number
            $assembled = [ordered]@{
                id = Stable-ProgramId $levelNumber $number
                authored_id = [string]$authored.id
                origin = "new_level"
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
            $newLevelCount++
        }
    }

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
        # The Full Battle (L10:100) tests the basics, five rounds of four stations, outside the class budget.
        if ($sets -gt [int]$progressionLimits.main_sets_including_practice -and [string]$program.schedule.day_type -ne "exam_level_full_battle") {
            throw "Level $levelNumber program $($program.number) has $sets sets; the budget is $($progressionLimits.main_sets_including_practice)"
        }
        $program["practice"] = @($program.practice)
    }

    if ($programs.Count -ne $targetProgramsPerLevel) {
        throw "Level $levelNumber must contain exactly $targetProgramsPerLevel app workouts; found $($programs.Count)"
    }

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
    source_baseline = [ordered]@{
        sha256 = $baselineHash
        immutable = $true
        apk_sha256 = [string]$baseline.source.apk_sha256
        authentic_program_count = 90
        placeholder_count = 289
        included_placeholder_count = $reconstructedCount
        archived_placeholder_count = $archivedLegacyPlaceholderCount
        app_slot_limit_per_level = $targetProgramsPerLevel
    }
    catalog_provenance = [ordered]@{
        classification = "llm_authored_reconstructed_curriculum"
        notice = $catalogMetadata.catalog_notice
        content_generation = "Static sessions written by tools/build_curriculum.py from the round 9 plan; scripts assemble and validate."
        content_revision = 3
    }
    summary = [ordered]@{
        level_count = $assembledLevels.Count
        program_count = $authenticCount + $reconstructedCount + $replacedSourceCount + $newExtensionCount + $newLevelCount
        authentic_program_count = $authenticCount
        reconstructed_placeholder_count = $reconstructedCount
        replaced_source_program_count = $replacedSourceCount
        new_extension_program_count = $newExtensionCount
        new_level_program_count = $newLevelCount
        archived_legacy_placeholder_count = $archivedLegacyPlaceholderCount
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
Write-Host "Authentic: $authenticCount; reconstructed placeholders: $reconstructedCount; replaced original-source classes: $replacedSourceCount; new-level: $newLevelCount."
Write-Host "LLM extensions: $newExtensionCount; archived legacy placeholders retained in forensic export: $archivedLegacyPlaceholderCount."
Write-Host "Movement definitions: $($movementById.Count)."
Write-Host "Output: $OutputPath"
