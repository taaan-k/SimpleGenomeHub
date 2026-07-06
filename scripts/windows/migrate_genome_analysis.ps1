param(
    [string]$DataRoot
)

$ErrorActionPreference = "Stop"
$analysisFolders = @("AdvanceCircos", "GenomeCompare", "MultipleCompare")

function Convert-JavaPropertiesValue {
    param([string]$Value)

    $builder = New-Object System.Text.StringBuilder
    $escaped = $false
    foreach ($char in $Value.ToCharArray()) {
        if ($escaped) {
            switch ($char) {
                "t" { [void]$builder.Append("`t") }
                "r" { [void]$builder.Append("`r") }
                "n" { [void]$builder.Append("`n") }
                default { [void]$builder.Append($char) }
            }
            $escaped = $false
            continue
        }

        if ($char -eq '\') {
            $escaped = $true
        } else {
            [void]$builder.Append($char)
        }
    }

    if ($escaped) {
        [void]$builder.Append('\')
    }

    return $builder.ToString()
}

function Get-DataRootFromConfig {
    $configPath = Join-Path $HOME ".SimpleGenomeHub\\SimpleGenomeHub.config"
    if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        throw "Config file not found: $configPath"
    }

    foreach ($line in Get-Content -LiteralPath $configPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line.StartsWith("#") -or $line.StartsWith("!")) {
            continue
        }
        if ($line.StartsWith("data.root.directory=")) {
            return Convert-JavaPropertiesValue($line.Substring("data.root.directory=".Length))
        }
    }

    throw "data.root.directory was not found in $configPath"
}

function Get-AvailableTargetPath {
    param([string]$BasePath)

    if (-not (Test-Path -LiteralPath $BasePath)) {
        return $BasePath
    }

    $index = 2
    while ($true) {
        $candidate = $BasePath + "_migrated_" + $index
        if (-not (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
        $index++
    }
}

function Move-DirectoryContents {
    param(
        [string]$SourceDir,
        [string]$TargetDir
    )

    if (-not (Test-Path -LiteralPath $TargetDir -PathType Container)) {
        New-Item -ItemType Directory -Path $TargetDir | Out-Null
    }

    $children = Get-ChildItem -LiteralPath $SourceDir -Force
    foreach ($child in $children) {
        $targetPath = Join-Path $TargetDir $child.Name
        if (Test-Path -LiteralPath $targetPath) {
            $targetPath = Get-AvailableTargetPath $targetPath
        }
        Move-Item -LiteralPath $child.FullName -Destination $targetPath
        Write-Host ("Moved: {0} -> {1}" -f $child.FullName, $targetPath)
    }

    if ((Get-ChildItem -LiteralPath $SourceDir -Force | Measure-Object).Count -eq 0) {
        Remove-Item -LiteralPath $SourceDir
    }
}

function Move-AnalysisFolder {
    param(
        [System.IO.DirectoryInfo]$SpeciesDir,
        [string]$FolderName
    )

    $functionalDir = Join-Path $SpeciesDir.FullName "FunctionalAnnotation"
    $sourceDir = Join-Path $functionalDir $FolderName
    if (-not (Test-Path -LiteralPath $sourceDir -PathType Container)) {
        return $false
    }

    $genomeAnalysisDir = Join-Path $SpeciesDir.FullName "GenomeAnalysis"
    if (-not (Test-Path -LiteralPath $genomeAnalysisDir -PathType Container)) {
        New-Item -ItemType Directory -Path $genomeAnalysisDir | Out-Null
    }

    $targetDir = Join-Path $genomeAnalysisDir $FolderName
    if (Test-Path -LiteralPath $targetDir -PathType Container) {
        Move-DirectoryContents -SourceDir $sourceDir -TargetDir $targetDir
    } else {
        Move-Item -LiteralPath $sourceDir -Destination $targetDir
        Write-Host ("Moved: {0} -> {1}" -f $sourceDir, $targetDir)
    }

    return $true
}

if ([string]::IsNullOrWhiteSpace($DataRoot)) {
    $DataRoot = Get-DataRootFromConfig
}

if (-not (Test-Path -LiteralPath $DataRoot -PathType Container)) {
    throw "Data root directory does not exist: $DataRoot"
}

$movedCount = 0
$speciesDirs = Get-ChildItem -LiteralPath $DataRoot -Directory | Sort-Object Name
foreach ($speciesDir in $speciesDirs) {
    foreach ($folderName in $analysisFolders) {
        if (Move-AnalysisFolder -SpeciesDir $speciesDir -FolderName $folderName) {
            $movedCount++
        }
    }
}

Write-Host ""
Write-Host ("Finished. Migrated {0} analysis folder(s)." -f $movedCount)
