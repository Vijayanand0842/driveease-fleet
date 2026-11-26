$Root = Split-Path -Parent $PSScriptRoot
$PidFile = Join-Path $Root ".driveease-pids.json"

if (Test-Path $PidFile) {
    $items = Get-Content $PidFile | ConvertFrom-Json
    foreach ($item in $items) {
        try {
            Stop-Process -Id $item.pid -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped $($item.name) (PID $($item.pid))"
        } catch {}
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "No .driveease-pids.json found."
}
