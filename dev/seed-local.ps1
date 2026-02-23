param(
    [string]$Container = "custody-calendar-postgres",
    [string]$Database = "custody_calendar",
    [string]$User = "custody"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sqlPath = Join-Path $scriptDir "seed-local.sql"

if (-not (Test-Path $sqlPath)) {
    throw "Seed SQL not found at $sqlPath"
}

Get-Content $sqlPath | docker exec -i $Container psql -U $User -d $Database
