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

function Stable-ProgramId([int]$Level, [int]$Number) {
    return "sg:l{0:D2}:p{1:D4}" -f $Level, $Number
}

function Localized([string]$English) {
    return [ordered]@{ en = $English }
}

$script:flagPracticeIds = @("flag_ground_support", "assisted_tuck_flag", "tuck_flag", "straddle_flag", "full_flag")
# Round 6: tuck-or-harder flags are maximal straight-arm holds, so they lead the main work rather than the practice.
$script:hardFlagIds = @("tuck_flag", "straddle_flag", "full_flag")
$script:leverAndMuscleUpIds = @("tuck_front_hold", "advanced_tuck_front", "one_leg_front", "straddle_front", "full_front", "tuck_back_hold",
    "advanced_tuck_back", "one_leg_back", "straddle_back", "full_back", "strict_muscleup", "band_muscleup", "negative_muscleup", "jump_muscleup")
$script:lowerBodyPatterns = @("legs", "squat", "lunge", "lateral_lunge", "single_leg", "single_leg_squat", "hinge",
    "posterior_chain", "knee_flexion", "hip_extension")

function Limit-TrainingSteps($Steps, [int]$SetBudget, [int]$SetsPerExercise) {
    # Project the authored prescription without adding work or replacing movements.
    $rows = @($Steps)
    if ($SetBudget -lt $rows.Count) { throw "The set budget must retain every authored exercise" }
    $counts = New-Object int[] $rows.Count
    $allIndexes = @(for ($index = 0; $index -lt $rows.Count; $index++) { $index })
    # Second sets go to leg and hinge work first, so the steps listed last are not the ones always trimmed.
    $lowerIndexes = @($allIndexes | Where-Object {
        @($movementById[[string]$rows[$_][0]].patterns | Where-Object { $_ -in $script:lowerBodyPatterns }).Count -gt 0
    })
    # The class's lead skill (its first advanced skill, else its first skill rung) keeps its authored sets, so a
    # rung's start gate never rests on a trimmed session; the trims fall on the supporting work (round 5b).
    $rank = @{ advanced_skill = 2; mastery_skill = 2; skill = 1 }
    $lead = -1
    foreach ($index in $allIndexes) {
        $leadRank = [int]$rank[[string]$movementById[[string]$rows[$index][0]].category]
        if ($leadRank -gt 0 -and ($lead -lt 0 -or $leadRank -gt [int]$rank[[string]$movementById[[string]$rows[$lead][0]].category])) { $lead = $index }
    }
    for ($round = 0; $round -lt $SetsPerExercise; $round++) {
        $order = if ($round -eq 1) { @($lowerIndexes) + @($allIndexes | Where-Object { $_ -notin $lowerIndexes }) } else { $allIndexes }
        foreach ($index in $order) {
            if ($SetBudget -gt 0 -and $round -lt [int]$rows[$index][1] -and $counts[$index] -eq $round) {
                $counts[$index]++
                $SetBudget--
            }
        }
        if ($round -eq 0 -and $lead -ge 0) {
            while ($SetBudget -gt 0 -and $counts[$lead] -lt [Math]::Min([int]$rows[$lead][1], $SetsPerExercise)) {
                $counts[$lead]++
                $SetBudget--
            }
        }
    }
    $projected = New-Object System.Collections.ArrayList
    for ($index = 0; $index -lt $rows.Count; $index++) {
        $step = @($rows[$index]).Clone()
        $originalSets = [int]$step[1]
        $step[1] = $counts[$index]
        foreach ($doseIndex in @(2, 3)) {
            # Keep per-set ladders aligned when their final sets are removed.
            if ($step[$doseIndex] -is [string] -and $step[$doseIndex] -match '^(\d+(?:-\d+)+)(/side)?$') {
                $series = $Matches[1] -split '-'
                $side = $Matches[2]
                if ($series.Count -ne $originalSets) { throw "Invalid authored dose ladder" }
                $step[$doseIndex] = ($series[0..($counts[$index] - 1)] -join '-') + $side
            }
        }
        [void]$projected.Add($step)
    }
    return ,$projected.ToArray()
}

# ----- Original-source projection (morning_class.json early_projection) -----
# An ordered rule list. A rule names the source step it matches: "exercise", optional "exercise_description",
# "set" (a compound element's name) and "set_description"; a field that is left out matches anything, "" matches a
# blank source field and a list matches any of its entries. "where" limits a rule to app classes ("L1:001-L1:040",
# "L3:015" or a list). "action" is project_exercise (the whole exercise becomes the library movement), project_set
# (only the matching sets do) or keep (the prepared source step stays, optionally capped). Doses: "reps" or
# "seconds" (fixed), "reps_cap" or "seconds_cap" (the source dose, capped); "per_class" overrides them per class.
# "like" is a regex the exercise and set descriptions together must match; "rest_min" raises the set's rest; a
# "{source}" in "set_note" stands for the source set's (or exercise's) own description.
function Test-ProjectionField($Rule, [string]$Name, [string]$Actual) {
    $property = $Rule.PSObject.Properties[$Name]
    if ($null -eq $property) { return $true }
    $actualText = ([string]$Actual).Trim()
    foreach ($expected in @($property.Value)) {
        if ($actualText -ceq ([string]$expected).Trim()) { return $true }
    }
    return $false
}

function Test-ProjectionLike($Rule, [string]$Text) {
    $property = $Rule.PSObject.Properties["like"]
    if ($null -eq $property) { return $true }
    return [regex]::IsMatch($Text, [string]$property.Value)
}

function Test-ProjectionWhere($Rule, [int]$Level, [int]$Number) {
    $property = $Rule.PSObject.Properties["where"]
    if ($null -eq $property) { return $true }
    foreach ($range in @($property.Value)) {
        if ([string]$range -notmatch '^L(\d):(\d{3})(?:-L(\d):(\d{3}))?$') { throw "Invalid early_projection where '$range'" }
        $fromLevel = [int]$Matches[1]; $fromNumber = [int]$Matches[2]
        $toLevel = $(if ($Matches[3]) { [int]$Matches[3] } else { $fromLevel })
        $toNumber = $(if ($Matches[4]) { [int]$Matches[4] } else { $fromNumber })
        $position = $Level * 1000 + $Number
        if ($position -ge ($fromLevel * 1000 + $fromNumber) -and $position -le ($toLevel * 1000 + $toNumber)) { return $true }
    }
    return $false
}

function Get-ProjectionDose($Rule, [int]$Level, [int]$Number) {
    $dose = [ordered]@{}
    foreach ($name in @("reps", "seconds", "reps_cap", "seconds_cap")) {
        if ($null -ne $Rule.PSObject.Properties[$name]) { $dose[$name] = [int]$Rule.$name }
    }
    $perClass = $Rule.PSObject.Properties["per_class"]
    if ($null -ne $perClass) {
        $override = $perClass.Value.PSObject.Properties[("L{0}:{1:D3}" -f $Level, $Number)]
        if ($null -ne $override) {
            $dose = [ordered]@{}
            foreach ($name in @("reps", "seconds", "reps_cap", "seconds_cap")) {
                if ($null -ne $override.Value.PSObject.Properties[$name]) { $dose[$name] = [int]$override.Value.$name }
            }
        }
    }
    return $dose
}

function Set-ProjectionDose($Set, $Dose, [int]$MaxSetRepetitions) {
    if ($Dose.Count -eq 0) { return }
    $sourceReps = $(if ($Set.repetitions_max) { $MaxSetRepetitions } else { $Set.repetitions })
    $sourceSeconds = $Set.duration_seconds
    if ($Dose.Contains("reps")) { $Set.repetitions = $Dose.reps; $Set.duration_seconds = $null }
    elseif ($Dose.Contains("seconds")) { $Set.repetitions = $null; $Set.duration_seconds = $Dose.seconds }
    elseif ($Dose.Contains("reps_cap")) {
        if ($null -eq $sourceReps) { throw "reps_cap needs a repetition dose in the source set" }
        $Set.repetitions = [Math]::Min([int]$sourceReps, [int]$Dose.reps_cap); $Set.duration_seconds = $null
    }
    elseif ($Dose.Contains("seconds_cap")) {
        if ($null -eq $sourceSeconds) { throw "seconds_cap needs a timed source set" }
        $Set.duration_seconds = [Math]::Min([int]$sourceSeconds, [int]$Dose.seconds_cap); $Set.repetitions = $null
    }
    $Set.repetitions_max = $false
}

function Update-PerSideNote($Set, $SourceRepetitions) {
    # Capped totals keep their per-side wording true: "10 reps on each leg" over 12 repetitions reads "6 reps on each leg".
    if ($null -eq $Set.description -or $null -eq $Set.repetitions -or $null -eq $SourceRepetitions) { return }
    if ([int]$Set.repetitions -eq [int]$SourceRepetitions -or ([int]$Set.repetitions % 2) -ne 0) { return }
    $text = [string]$Set.description.en
    $match = [regex]::Match($text, '\b(\d+)( (?:reps?|times?) on each (?:leg|side))')
    if (-not $match.Success) { return }
    $perSide = [int]$Set.repetitions / 2
    $Set.description = Localized ($text.Substring(0, $match.Index) + $perSide + $match.Groups[2].Value + $text.Substring($match.Index + $match.Length))
}

$script:polishKey = ([string][char]112) + ([string][char]108)
function Test-DormantVideoMetadataKey([string]$Name) {
    $normalized = [regex]::Replace($Name, '[_\-\s]', '').ToLowerInvariant()
    return $normalized -in @(
        "video",
        "videoid",
        "videourl",
        "videoprovider",
        "url",
        "provider",
        "vimeo",
        "vimeoid",
        "vimeourl",
        "vimeoprovider"
    )
}

function Remove-AppOnlyMetadata($Value) {
    if ($null -eq $Value -or $Value -is [string]) { return }
    if ($Value -is [System.Management.Automation.PSCustomObject]) {
        foreach ($property in @($Value.PSObject.Properties)) {
            if ($property.Name -ceq $script:polishKey -or (Test-DormantVideoMetadataKey $property.Name)) {
                $Value.PSObject.Properties.Remove($property.Name)
                continue
            }
            Remove-AppOnlyMetadata $property.Value
        }
        return
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        foreach ($item in $Value) { Remove-AppOnlyMetadata $item }
    }
}

$baselineHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $BaselinePath).Hash
if ($baselineHash -ne $expectedBaselineSha256) {
    throw "Baseline SHA-256 mismatch. Expected $expectedBaselineSha256, got $baselineHash"
}

$baseline = Read-JsonFile $BaselinePath
$catalogMetadata = Read-JsonFile (Join-Path $CurriculumDirectory "catalog_metadata.json")
$authenticTitles = (Read-JsonFile (Join-Path $CurriculumDirectory "authentic_titles.json")).titles
$morningClass = Read-JsonFile (Join-Path $CurriculumDirectory "morning_class.json")
$classProgression = Read-JsonFile (Join-Path $CurriculumDirectory "class_progression.json")
# App positions ("level-number") where an authored class takes the place of an original-source class (round 6 exam days).
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
if ($coachingProfiles.Count -eq 0) { throw "No coaching profiles were found" }

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
                Remove-AppOnlyMetadata $legacyProgram
                $profile = $coachingProfiles[($number - 1) % $coachingProfiles.Count]
                $assembled = [ordered]@{
                    id = $stableId
                    origin = "apk_authentic"
                    level = $levelNumber
                    number = $number
                    title = $authenticTitles.PSObject.Properties["$levelNumber-$sourceSlot"].Value
                    description = $catalogMetadata.authentic_description
                    schedule = [ordered]@{
                        week = [Math]::Floor(($number - 1) / 7) + 1
                        day = (($number - 1) % 7) + 1
                        day_type = "authentic_base"
                        minutes = $null
                        rpe = $null
                        focus = @("authentic_base")
                    }
                    legacy = [ordered]@{
                        workout_id = [string]$legacyProgram.id
                        pro = [bool]$legacyProgram.pro
                        has_details = $true
                    }
                    readiness = $catalogMetadata.progression_readiness
                    safety = $catalogMetadata.authentic_scaling
                    content = $legacyProgram
                    coaching_overlay = [ordered]@{
                        origin = "new_coaching_overlay"
                        profile_id = [string]$profile.id
                        warmup = $profile.warmup
                        cooldown = $profile.cooldown
                    }
                }
                [void]$programs.Add($assembled)
                $authenticCount++
                continue
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
                main = $authored.main
                cooldown = $authored.cooldown
                readiness = $(if ($null -ne $authored.readiness) { $authored.readiness } else { $catalogMetadata.progression_readiness })
                safety = $(if ($null -ne $authored.safety) { $authored.safety } else { $catalogMetadata.training_recovery })
            }
            [void]$programs.Add($assembled)
            $newLevelCount++
        }
    }

    $classLevel = $morningClass.levels | Where-Object { [int]$_.level -eq $levelNumber } | Select-Object -First 1
    $progressionLimits = $classProgression.levels | Where-Object { [int]$_.level -eq $levelNumber } | Select-Object -First 1
    if ($null -eq $progressionLimits) { throw "Missing workload limits for level $levelNumber" }
    foreach ($program in $programs) {
        $classDay = (([int]$program.number - 1) % 7) + 1
        # Practice doses rise in phases inside each level; the first phase that covers the class applies.
        $classPhase = @($classLevel.phases | Where-Object { [int]$program.number -le [int]$_.through_program } | Select-Object -First 1)
        if ($classPhase.Count -ne 1) { throw "No morning-class phase covers Level $levelNumber program $($program.number)" }
        $classSlot = $classPhase[0].days | Where-Object { [int]$_.day -eq $classDay } | Select-Object -First 1
        $program["practice"] = @($classSlot.practice)
        if ($program.origin -ne "apk_authentic") {
            # Human-flag practice is left out of classes that train levers or muscle-ups (pulling-tendon load).
            $mainIds = @($program.main | ForEach-Object { [string]$_[0] })
            if (@($mainIds | Where-Object { $_ -in $script:leverAndMuscleUpIds }).Count -gt 0) {
                $kept = New-Object System.Collections.ArrayList
                foreach ($step in @($classSlot.practice)) {
                    if ([string]$step[0] -notin $script:flagPracticeIds) { [void]$kept.Add($step) }
                }
                $program["practice"] = $kept.ToArray()
            }
            # Deload weeks unload the practice too: one set per side of flag work and half the other practice sets.
            if ([string]$program.schedule.day_type -match "deload") {
                $halved = New-Object System.Collections.ArrayList
                foreach ($step in @($program.practice)) {
                    $copy = @($step).Clone()
                    $copy[1] = $(if ([string]$copy[0] -in $script:flagPracticeIds) { 1 } else { [int][Math]::Ceiling([int]$copy[1] / 2) })
                    [void]$halved.Add($copy)
                }
                $program["practice"] = $halved.ToArray()
            }
        } elseif ($classDay -notin @(4, 7)) {
            # Original-source classes add the level's source practice (pike push-ups: the source blocks press too little).
            $sourceSlot = @($classLevel.source_practice | Where-Object { [int]$_.day -eq $classDay } | Select-Object -First 1)
            if ($sourceSlot.Count -eq 1) {
                $combined = New-Object System.Collections.ArrayList
                foreach ($step in @($classSlot.practice) + @($sourceSlot[0].practice)) { [void]$combined.Add($step) }
                $program["practice"] = $combined.ToArray()
            }
        }
        if ($program.origin -eq "apk_authentic" -and $classDay -in @(4, 7)) {
            $program["morning_recovery"] = $true
            $program["practice"] = @()
            $recoveryMain = New-Object System.Collections.ArrayList
            foreach ($step in @($morningClass.early_recovery)) { [void]$recoveryMain.Add($step) }
            # From Level 2 the source recovery days add hinge work (the source blocks hold almost none).
            if ($levelNumber -ge [int]$morningClass.early_recovery_hinge.from_level) {
                foreach ($step in @($morningClass.early_recovery_hinge.steps)) { [void]$recoveryMain.Add($step) }
            }
            $program["main"] = $recoveryMain.ToArray()
            $program["title"] = Localized ("Mobility & Recovery - Session {0}" -f $program.number)
            $program["description"] = Localized "An easy morning class: gentle walking, shoulder mobility and trunk control. Finish feeling fresher for the next strength session."
            $program["safety"] = $catalogMetadata.training_recovery
        }
        if ($program.origin -eq "apk_authentic" -and -not $program["morning_recovery"]) {
            # A separate working projection keeps the original export auditable.
            $training = $program.content | ConvertTo-Json -Depth 100 | ConvertFrom-Json
            $limits = $morningClass.early_strength_limits
            $projectionRules = @($morningClass.early_projection)
            foreach ($exercise in $training.exercises) {
                $exerciseName = [string]$exercise.name.en
                $exerciseDescription = [string]$exercise.description.en
                # A project_exercise rule turns the whole source exercise into one library movement with its easier rung.
                $exerciseRule = $projectionRules | Where-Object {
                    [string]$_.action -ceq "project_exercise" -and (Test-ProjectionWhere $_ $levelNumber ([int]$program.number)) -and
                    (Test-ProjectionField $_ "exercise" $exerciseName) -and (Test-ProjectionField $_ "exercise_description" $exerciseDescription)
                } | Select-Object -First 1
                if ($null -ne $exerciseRule) {
                    $movement = $movementById[[string]$exerciseRule.movement_id]
                    $regression = $movementById[[string]$movement.regression_id]
                    if ($null -eq $movement -or $null -eq $regression) { throw "early_projection needs a library movement with a regression: $($exerciseRule.movement_id)" }
                    $exercise | Add-Member -NotePropertyName movement_id -NotePropertyValue $movement.id -Force
                    $exercise.name = $movement.name
                    $exercise.description = Localized ("{0} {1} Easier option: {2}." -f $movement.description.en, $movement.safety.en, $regression.name.en)
                    $dose = Get-ProjectionDose $exerciseRule $levelNumber ([int]$program.number)
                    $note = $(if ($null -ne $exerciseRule.PSObject.Properties["set_note"]) { [string]$exerciseRule.set_note } else { "Use the easier option whenever needed; stop with reserve effort." })
                    foreach ($set in $exercise.sets) {
                        Set-ProjectionDose $set $dose ([int]$limits.max_set_repetitions)
                        $set.name = Localized ""
                        $set.description = Localized $note
                    }
                    continue
                }
                # Otherwise each set (a per-set variation, or one element of a compound exercise) takes the first
                # project_set or keep rule that matches it.
                foreach ($set in $exercise.sets) {
                    $setName = [string]$set.name.en
                    $setDescription = [string]$set.description.en
                    $setRule = $projectionRules | Where-Object {
                        [string]$_.action -cne "project_exercise" -and (Test-ProjectionWhere $_ $levelNumber ([int]$program.number)) -and
                        (Test-ProjectionField $_ "exercise" $exerciseName) -and (Test-ProjectionField $_ "exercise_description" $exerciseDescription) -and
                        (Test-ProjectionField $_ "set" $setName) -and (Test-ProjectionField $_ "set_description" $setDescription) -and
                        (Test-ProjectionLike $_ "$exerciseDescription $setDescription")
                    } | Select-Object -First 1
                    if ($null -eq $setRule) { continue }
                    $sourceRepetitions = $set.repetitions
                    Set-ProjectionDose $set (Get-ProjectionDose $setRule $levelNumber ([int]$program.number)) ([int]$limits.max_set_repetitions)
                    if ($null -ne $setRule.PSObject.Properties["rest_min"]) {
                        $set.break_seconds = [Math]::Max([int]$set.break_seconds, [int]$setRule.rest_min)
                    }
                    if ([string]$setRule.action -ceq "keep") {
                        Update-PerSideNote $set $sourceRepetitions
                        continue
                    }
                    $movement = $movementById[[string]$setRule.movement_id]
                    $regression = $movementById[[string]$movement.regression_id]
                    if ($null -eq $movement -or $null -eq $regression) { throw "early_projection needs a library movement with a regression: $($setRule.movement_id)" }
                    $set.name = $movement.name
                    $sourceNote = $(if (-not [string]::IsNullOrWhiteSpace($setDescription)) { $setDescription.Trim() } else { $exerciseDescription.Trim() })
                    if ($sourceNote -and -not $sourceNote.EndsWith(".")) { $sourceNote += "." }
                    $lead = $(if ($null -ne $setRule.PSObject.Properties["set_note"]) { ([string]$setRule.set_note).Replace("{source}", $sourceNote).Trim() + " " } else { "" })
                    $set.description = Localized ("{0}Easier option: {1}; stop with reserve effort." -f $lead, $regression.name.en)
                    $set | Add-Member -NotePropertyName movement_id -NotePropertyValue $movement.id -Force
                }
            }
            $practiceSetCount = 0
            foreach ($step in @($program.practice)) { $practiceSetCount += [int]$step[1] }
            $remaining = [Math]::Min([int]$limits.main_sets_including_practice, [int]$progressionLimits.main_sets_including_practice) - $practiceSetCount
            if ($remaining -lt @($training.exercises).Count) { throw "The set budget must retain every source exercise" }
            $selectedByExercise = @{}
            foreach ($exercise in $training.exercises) { $selectedByExercise[[string]$exercise.id] = New-Object System.Collections.ArrayList }
            for ($round = 0; $round -lt [int]$limits.sets_per_exercise; $round++) {
                foreach ($exercise in $training.exercises) {
                    if ($remaining -le 0) { break }
                    $available = @($exercise.sets)
                    if ($round -lt $available.Count) {
                        $set = $available[$round]
                        if ($set.repetitions_max) {
                            $set.repetitions_max = $false
                            $set.repetitions = [int]$limits.max_set_repetitions
                        } elseif ($null -ne $set.repetitions) {
                            $uncapped = $set.repetitions
                            $set.repetitions = [Math]::Min([int]$set.repetitions, [int]$limits.repetitions)
                            Update-PerSideNote $set $uncapped
                        }
                        if ($null -ne $set.duration_seconds) {
                            $set.duration_seconds = [Math]::Min([int]$set.duration_seconds, [int]$limits.hold_seconds)
                        }
                        $set.break_seconds = [Math]::Max([int]$set.break_seconds, [int]$limits.minimum_rest_seconds)
                        if ($null -ne $limits.PSObject.Properties["maximum_rest_seconds"]) {
                            $set.break_seconds = [Math]::Min([int]$set.break_seconds, [int]$limits.maximum_rest_seconds)
                        }
                        [void]$selectedByExercise[[string]$exercise.id].Add($set)
                        $remaining--
                    }
                }
            }
            foreach ($exercise in $training.exercises) { $exercise.sets = @($selectedByExercise[[string]$exercise.id]) }
            $program["training_content"] = $training
            $program["training_rpe"] = $(if ($levelNumber -eq 1) { 6 } else { [int]$limits.rpe })
        }
        if ($null -ne $program.schedule.rpe -and [int]$program.schedule.rpe -le 4) {
            $program["practice"] = @()
        }
        # An exam day tests the handstand in its main work, so it skips the practice block (round 6).
        if ([string]$program.schedule.day_type -match "^exam") {
            $program["practice"] = @()
        }
        if ($program.origin -ne "apk_authentic") {
            # Tuck-or-harder flags lead the main work; the set budget still counts them as before.
            $leading = New-Object System.Collections.ArrayList
            $kept = New-Object System.Collections.ArrayList
            foreach ($step in @($program.practice)) {
                if ([string]$step[0] -in $script:hardFlagIds) { [void]$leading.Add($step) } else { [void]$kept.Add($step) }
            }
            $program["practice"] = $kept.ToArray()
            $practiceSets = 0
            foreach ($step in @($program.practice) + $leading.ToArray()) { $practiceSets += [int]$step[1] }
            $limited = Limit-TrainingSteps $program.main `
                ([int]$progressionLimits.main_sets_including_practice - $practiceSets) `
                ([int]$progressionLimits.sets_per_exercise)
            foreach ($step in @($limited)) { [void]$leading.Add($step) }
            $program["training_main"] = $leading.ToArray()
        }
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
        content_generation = "Static sessions authored by language-model review; scripts only assemble and validate."
        content_revision = 2
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
Write-Host "Movement definitions: $($movementById.Count); coaching profiles: $($coachingProfiles.Count)."
Write-Host "Output: $OutputPath"
