# No Docker dependency: exercise launcher failure paths using a command double.
$ErrorActionPreference = 'Stop'
$global:DcBackupCleanupScenario = 'success'
function global:docker {
    if ($args -contains 'down' -and $global:DcBackupCleanupScenario -ne 'success') {
        $global:LASTEXITCODE = 1
    } elseif ($args -contains 'build' -and $global:DcBackupCleanupScenario -eq 'primary-and-cleanup') {
        $global:LASTEXITCODE = 7
    } else {
        $global:LASTEXITCODE = 0
    }
}
try {
    foreach ($script:scenario in @('success', 'cleanup', 'primary-and-cleanup')) {
        $global:DcBackupCleanupScenario = $script:scenario
        $caught = $null
        $warningRecords = @(& {
            try { & (Join-Path $PSScriptRoot 'run-lab.ps1') } catch { $script:caughtError = $_ }
        } 3>&1)
        $caught = $script:caughtError
        $script:caughtError = $null
        if ($script:scenario -eq 'success' -and $null -ne $caught) { throw 'Success-path cleanup test failed' }
        if ($script:scenario -eq 'cleanup' -and ($null -eq $caught -or $caught.Exception.Message -notlike '*cleanup failed*')) {
            throw 'Cleanup failure was hidden'
        }
        if ($script:scenario -eq 'primary-and-cleanup' -and ($null -eq $caught -or $caught.Exception.Message -ne 'Laboratory image build failed')) {
            throw 'Original failure was not preserved'
        }
        if ($script:scenario -ne 'success' -and $warningRecords.Count -eq 0) { throw 'Missing cleanup warning' }
    }
    Write-Output '{"event":"cleanup_tests_completed","tests":3,"failures":0}'
} finally {
    Remove-Item -Path Function:\docker
    Remove-Variable -Name DcBackupCleanupScenario -Scope Global
}
