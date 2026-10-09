$ErrorActionPreference = 'Stop'
$project = 'dc-backup-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$envFile = Join-Path ([IO.Path]::GetTempPath()) ($project + '.env')
$compose = Join-Path $PSScriptRoot 'compose.lab.yml'
$primaryError = $null
$cleanupFailed = $false
try {
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    [IO.File]::WriteAllText($envFile, "LAB_PASSWORD=$password`n")
    # Capture migration output: database diagnostics must not enter operational logs.
    $build = & docker compose --env-file $envFile -p $project -f $compose build runner 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Laboratory image build failed' }
    $setup = & docker compose --env-file $envFile -p $project -f $compose up -d --wait pgsource pgtarget 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Disposable databases unavailable' }
    $migration = & docker compose --env-file $envFile -p $project -f $compose run --rm migrate 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic schema migration failed' }
    & docker compose --env-file $envFile -p $project -f $compose run --rm --no-deps runner verify
    if ($LASTEXITCODE -ne 0) { throw 'Backup verification failed' }
} catch {
    $primaryError = $_
} finally {
    try {
        $cleanupOutput = & docker compose --env-file $envFile -p $project -f $compose down --volumes --remove-orphans 2>&1
        $cleanupFailed = $LASTEXITCODE -ne 0
    } catch {
        $cleanupFailed = $true
    }
    if ($cleanupFailed) {
        Write-Warning "Laboratory cleanup failed for project $project. Containers, networks or volumes may remain; inspect only this project."
    }
    try {
        if (Test-Path -LiteralPath $envFile) { Remove-Item -LiteralPath $envFile }
    } catch {
        $cleanupFailed = $true
        Write-Warning 'Disposable credential file cleanup failed; remove the matching laboratory file.'
    }
}
if ($null -ne $primaryError) { throw $primaryError }
if ($cleanupFailed) { throw 'Laboratory cleanup failed; verification cannot be declared complete' }
