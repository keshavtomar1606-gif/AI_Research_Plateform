# Quick check that the running backend, MySQL and Ollama work together.
# Usage (from the project folder, with the backend running):
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 -SkipAi      # skip the slow AI call
#   Online:  ... -BaseUrl https://YOUR-BACKEND.onrender.com/api -AccessKey YOUR_ACCESS_KEY
# NOTE: it creates (and then deletes) one test project, and loads the sample dataset + one training run.
param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$AccessKey = "",
    [switch]$SkipAi
)
$ErrorActionPreference = "Stop"
$failed = 0
function Pass($msg) { Write-Host "PASS  $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "FAIL  $msg" -ForegroundColor Red; $script:failed++ }
function Body($obj) { $obj | ConvertTo-Json -Depth 5 }

try {
    $h = Invoke-RestMethod "$BaseUrl/health" -TimeoutSec 120   # free hosting may need ~1 min to wake up
    if ($h.status -eq "UP") { Pass "backend is up" } else { Fail "backend status: $($h.status)" }
    if ($h.database -eq "UP") { Pass "MySQL connection" } else { Fail "MySQL is DOWN - check MySQL80 service and DB_PASSWORD" }
    if ($h.ai -eq "UP") { Pass "AI reachable ($($h.aiProvider), $($h.model))" } else { Fail "AI is DOWN - start Ollama (local) or check AI_API_KEY (cloud)" }
    if ($h.accessKeyRequired) {
        if (-not $AccessKey) { Write-Host "FAIL  this server needs an access key: add -AccessKey <key>" -ForegroundColor Red; exit 1 }
        $PSDefaultParameterValues["Invoke-RestMethod:Headers"] = @{ "X-Access-Key" = $AccessKey }
    }
} catch {
    Write-Host "FAIL  cannot reach $BaseUrl - is the backend running? ($($_.Exception.Message))" -ForegroundColor Red
    exit 1
}

# Project create -> read -> update -> delete
$p = Invoke-RestMethod "$BaseUrl/projects" -Method Post -ContentType "application/json" -Body (Body @{ title = "Smoke test project"; status = "Planning" })
$read = Invoke-RestMethod "$BaseUrl/projects/$($p.id)"
if ($read.title -eq "Smoke test project") { Pass "project saved and read back from MySQL (id $($p.id))" } else { Fail "project read-back" }
$u = Invoke-RestMethod "$BaseUrl/projects/$($p.id)" -Method Put -ContentType "application/json" -Body (Body @{ title = "Smoke test project (updated)"; status = "Active" })
if ($u.status -eq "Active") { Pass "project updated" } else { Fail "project update" }
Invoke-RestMethod "$BaseUrl/projects/$($p.id)" -Method Delete | Out-Null
try { Invoke-RestMethod "$BaseUrl/projects/$($p.id)" | Out-Null; Fail "project still exists after delete" } catch { Pass "project deleted from MySQL" }

# Validation error
try {
    Invoke-RestMethod "$BaseUrl/projects" -Method Post -ContentType "application/json" -Body '{"title":""}' | Out-Null
    Fail "empty title was accepted"
} catch { Pass "empty title rejected with HTTP $($_.Exception.Response.StatusCode.value__)" }

# Real ML training on the sample dataset
$d = Invoke-RestMethod "$BaseUrl/datasets/sample" -Method Post
Pass "sample dataset available (id $($d.id), $($d.rowCount) rows)"
$job = Invoke-RestMethod "$BaseUrl/training/run" -Method Post -ContentType "application/json" -Body (Body @{
    datasetId = $d.id; targetColumn = "final_score"; testRatio = 0.2; randomSeed = 42
    featureColumns = @("hours_studied", "attendance_pct", "previous_score", "sleep_hours") })
for ($i = 0; $i -lt 30 -and $job.status -notin @("Completed", "Failed"); $i++) {
    Start-Sleep -Milliseconds 500
    $job = Invoke-RestMethod "$BaseUrl/training/$($job.id)"
}
if ($job.status -eq "Completed") {
    Pass ("training run #{0}: R2={1:N3} MAE={2:N2} RMSE={3:N2} (baseline MAE {4:N2})" -f $job.id, $job.r2, $job.mae, $job.rmse, $job.baselineMae)
} else { Fail "training run #$($job.id) ended as $($job.status): $($job.errorMessage)" }

if (-not $SkipAi) {
    Write-Host "...asking the local AI model (can take 10-60 s the first time)"
    try {
        $r = Invoke-RestMethod "$BaseUrl/ai/chat" -Method Post -ContentType "application/json" -TimeoutSec 200 -Body (Body @{ prompt = "Reply with one short sentence: what is linear regression?" })
        Pass "AI answered: $($r.response)"
    } catch { Fail "AI chat failed: $($_.ErrorDetails.Message)" }
}

if ($failed -eq 0) { Write-Host "`nAll checks passed." -ForegroundColor Green } else { Write-Host "`n$failed check(s) failed." -ForegroundColor Red; exit 1 }
