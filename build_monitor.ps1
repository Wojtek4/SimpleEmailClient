# GitHub Actions CI & Local Build Monitoring Script
# Usage: .\build_monitor.ps1 [-PollIntervalSec 10] [-MaxWaitMinutes 15]

param(
    [int]$PollIntervalSec = 10,
    [int]$MaxWaitMinutes = 15
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Simple Mail - Build Monitor Agent Script " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Determine Repository
$remoteUrl = & "C:\Program Files\Git\cmd\git.exe" remote get-url origin 2>$null
if (-not $remoteUrl) {
    Write-Host "No git remote found. Falling back to local build..." -ForegroundColor Yellow
    & "$PSScriptRoot\gradlew.bat" assembleDebug
    exit $LASTEXITCODE
}

# Extract owner/repo
if ($remoteUrl -match "github\.com[/:]([^/]+)/([^/\.]+)") {
    $owner = $matches[1]
    $repo = $matches[2]
} else {
    Write-Host "Could not parse GitHub repo from $remoteUrl" -ForegroundColor Red
    exit 1
}

$repoPath = "$owner/$repo"
Write-Host "Monitoring GitHub Actions for: $repoPath" -ForegroundColor Green
Write-Host ""

$startTime = Get-Date
$maxTime = $startTime.AddMinutes($MaxWaitMinutes)

while ((Get-Date) -lt $maxTime) {
    try {
        $runsResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoPath/actions/runs?per_page=1" -Headers @{ "User-Agent" = "PowerShell-Build-Monitor" }
        $latestRun = $runsResponse.workflow_runs | Select-Object -First 1

        if (-not $latestRun) {
            Write-Host "No workflow runs found." -ForegroundColor Yellow
            Start-Sleep -Seconds $PollIntervalSec
            continue
        }

        $runId = $latestRun.id
        $status = $latestRun.status
        $conclusion = $latestRun.conclusion
        $commitMsg = $latestRun.head_commit.message
        $runUrl = $latestRun.html_url

        $elapsed = [int]((Get-Date) - $startTime).TotalSeconds
        Write-Host "[$elapsed`s] Run #$runId ('$commitMsg') -> Status: $status | Conclusion: $conclusion" -ForegroundColor $(
            if ($conclusion -eq "success") { "Green" }
            elseif ($conclusion -eq "failure") { "Red" }
            else { "Yellow" }
        )

        if ($status -eq "completed") {
            if ($conclusion -eq "success") {
                Write-Host ""
                Write-Host "============================================================" -ForegroundColor Green
                Write-Host " BUILD SUCCEEDED!                                           " -ForegroundColor Green
                Write-Host " Run URL: $runUrl" -ForegroundColor White
                
                # Check artifacts
                try {
                    $artRes = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoPath/actions/runs/$runId/artifacts" -Headers @{ "User-Agent" = "PowerShell-Build-Monitor" }
                    if ($artRes.artifacts.Count -gt 0) {
                        Write-Host " Available Artifacts:" -ForegroundColor Cyan
                        foreach ($art in $artRes.artifacts) {
                            $sizeMb = [math]::Round($art.size_in_bytes / 1MB, 2)
                            Write-Host "  - $($art.name) ($sizeMb MB)" -ForegroundColor White
                        }
                    }
                } catch { }
                Write-Host "============================================================" -ForegroundColor Green
                exit 0
            } else {
                Write-Host ""
                Write-Host "============================================================" -ForegroundColor Red
                Write-Host " BUILD FAILED! Fetching failure diagnostics...              " -ForegroundColor Red
                Write-Host " Run URL: $runUrl" -ForegroundColor White
                Write-Host "============================================================" -ForegroundColor Red

                # Fetch failed jobs and steps
                try {
                    $jobsRes = Invoke-RestMethod -Uri "https://api.github.com/repos/$repoPath/actions/runs/$runId/jobs" -Headers @{ "User-Agent" = "PowerShell-Build-Monitor" }
                    foreach ($job in $jobsRes.jobs) {
                        if ($job.conclusion -eq "failure") {
                            Write-Host ""
                            Write-Host "Failed Job: $($job.name)" -ForegroundColor Red
                            foreach ($step in $job.steps) {
                                if ($step.conclusion -eq "failure") {
                                    Write-Host "  Failed Step: $($step.name) (Step number: $($step.number))" -ForegroundColor Red
                                }
                            }
                        }
                    }
                } catch {
                    Write-Host "Could not fetch detailed job diagnostics: $_" -ForegroundColor Yellow
                }
                exit 1
            }
        }
    } catch {
        Write-Host "Error checking API: $_" -ForegroundColor DarkGray
    }

    Start-Sleep -Seconds $PollIntervalSec
}

Write-Host "Timeout reached ($MaxWaitMinutes minutes). Build is still running." -ForegroundColor Yellow
exit 2
