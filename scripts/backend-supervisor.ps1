#Requires -Version 5.1
param(
    [string]$ControlDir = '',
    [string]$ConfigPath = '',
    [string]$JavaPath = '',
    [string]$JarPath = '',
    [string]$WorkingDirectory = '',
    [string]$LogDirectory = '',
    [ValidateRange(1, 65535)][int]$Port = 8081,
    [ValidateRange(1, 3600)][int]$StartupTimeoutSeconds = 180,
    [ValidateRange(1, 3600)][int]$RecoveryTimeoutSeconds = 180,
    [ValidateRange(1, 30)][int]$HttpTimeoutSeconds = 5,
    [ValidateRange(100, 60000)][int]$PollIntervalMilliseconds = 1000,
    [string]$Profile = 'personal',
    [ValidateRange(0, 65535)][int]$ServerPort = 0,
    [string[]]$JvmArgs = @(),
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:SupervisorScriptPath = $MyInvocation.MyCommand.Path
$script:BackendProcess = $null
$script:ActiveRestartId = $null
$script:SupervisorExitCode = 0
$script:KeepBackendAlive = $false
$script:TerminalStates = @('SUCCEEDED', 'ROLLED_BACK', 'RECOVERY_FAILED')

function Initialize-SupervisorPaths {
    $userHome = [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
    $resolvedControlDir = if ([string]::IsNullOrWhiteSpace($ControlDir)) {
        Join-Path $userHome '.metadata-registry\restart'
    } else {
        $ControlDir
    }
    $resolvedConfigPath = if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
        Join-Path $userHome '.metadata-registry\application-local.yml'
    } else {
        $ConfigPath
    }
    $resolvedJavaPath = if ([string]::IsNullOrWhiteSpace($JavaPath)) {
        if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
            (Join-Path $env:ProgramFiles 'Java\jdk-17\bin\javaw.exe')
        } else {
            Join-Path $env:JAVA_HOME 'bin\javaw.exe'
        }
    } else {
        $JavaPath
    }
    $resolvedJarPath = if ([string]::IsNullOrWhiteSpace($JarPath)) {
        Join-Path $script:ProjectRoot 'metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar'
    } else {
        $JarPath
    }
    $resolvedWorkingDirectory = if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $script:ProjectRoot
    } else {
        $WorkingDirectory
    }
    $resolvedLogDirectory = if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
        Join-Path $script:ProjectRoot 'logs'
    } else {
        $LogDirectory
    }

    $script:ControlDir = [IO.Path]::GetFullPath($resolvedControlDir)
    $script:ConfigPath = [IO.Path]::GetFullPath($resolvedConfigPath)
    $script:JavaPath = [IO.Path]::GetFullPath($resolvedJavaPath)
    $script:JarPath = [IO.Path]::GetFullPath($resolvedJarPath)
    $script:WorkingDirectory = [IO.Path]::GetFullPath($resolvedWorkingDirectory)
    $script:LogDirectory = [IO.Path]::GetFullPath($resolvedLogDirectory)
    $script:Port = $Port
    $script:StartupTimeoutSeconds = $StartupTimeoutSeconds
    $script:RecoveryTimeoutSeconds = $RecoveryTimeoutSeconds
    $script:HttpTimeoutSeconds = $HttpTimeoutSeconds
    $script:PollIntervalMilliseconds = $PollIntervalMilliseconds
    $script:Profile = $Profile
    $script:ServerPort = $ServerPort
    $script:JvmArgs = @($JvmArgs)

    [IO.Directory]::CreateDirectory($script:ControlDir) | Out-Null
    [IO.Directory]::CreateDirectory($script:LogDirectory) | Out-Null
    $script:RequestPath = Join-Path $script:ControlDir 'restart-request.json'
    $script:StatusPath = Join-Path $script:ControlDir 'restart-status.json'
    $script:StopPath = Join-Path $script:ControlDir 'stop.request'
    $script:SupervisorPidPath = Join-Path $script:ControlDir 'supervisor.pid.json'
    $script:BackendPidPath = Join-Path $script:ControlDir 'backend.pid.json'
    $script:FrontendPidPath = Join-Path $script:ControlDir 'frontend.pid.json'
    $script:SupervisorErrorPath = Join-Path $script:ControlDir 'supervisor.error'
    $script:SupervisorLogPath = Join-Path $script:LogDirectory 'backend-supervisor.log'
    $script:BackendStdoutPath = Join-Path $script:LogDirectory 'backend-stdout.log'
    $script:BackendStderrPath = Join-Path $script:LogDirectory 'backend-stderr.log'
}

function Move-Atomically([string]$TempPath, [string]$Path) {
    if (-not [IO.File]::Exists($Path)) {
        [IO.File]::Move($TempPath, $Path)
        return
    }
    # [IO.File]::Replace with a $null backup always fails on .NET Framework
    # (Windows PowerShell 5.1) and the Move-Item fallback is delete+rename, so a
    # concurrent reader can observe the file as missing. That window is enough to
    # make the backend answer 404 and the wizard abort the restart. Replace with a
    # real backup path keeps the swap atomic on NTFS; the backup is removed right
    # after the swap.
    $backupPath = $Path + '.replace-backup'
    try {
        [IO.File]::Replace($TempPath, $Path, $backupPath, $true)
    } catch {
        # Fall back to a non-atomic replace only when Replace is unsupported
        # (for example a different volume); readers may briefly miss the file.
        Move-Item -LiteralPath $TempPath -Destination $Path -Force
    } finally {
        if ([IO.File]::Exists($backupPath)) {
            Remove-Item -LiteralPath $backupPath -Force -ErrorAction SilentlyContinue
        }
    }
}

function Write-AtomicText([string]$Path, [string]$Content) {
    $directory = [IO.Path]::GetDirectoryName($Path)
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    $tempPath = Join-Path $directory ('.' + [IO.Path]::GetFileName($Path) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($tempPath, $Content, [Text.UTF8Encoding]::new($false))
        Move-Atomically -TempPath $tempPath -Path $Path
    } finally {
        if ([IO.File]::Exists($tempPath)) {
            Remove-Item -LiteralPath $tempPath -Force
        }
    }
}

function Write-AtomicJson([string]$Path, $Value) {
    Write-AtomicText -Path $Path -Content ($Value | ConvertTo-Json -Depth 6 -Compress)
}

function Get-JsonContent([string]$Path) {
    # A control file may be locked or briefly unreadable while it is being
    # replaced. Retry a few times and treat a persistent failure as "absent"
    # instead of letting the error tear down the backend.
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        if (-not [IO.File]::Exists($Path)) {
            return $null
        }
        try {
            return [IO.File]::ReadAllText($Path) | ConvertFrom-Json
        } catch {
            if ($attempt -eq 2) {
                return $null
            }
            Start-Sleep -Milliseconds 50
        }
    }
    return $null
}

function Get-ApiPayload($Response) {
    if ($null -eq $Response) {
        return $null
    }
    if ($null -ne $Response.PSObject.Properties['data']) {
        return $Response.data
    }
    return $Response
}

function Write-SupervisorEvent([string]$Event, [string]$RestartId = '', [string]$Detail = '') {
    try {
        $entry = [ordered]@{
            timestamp = [DateTimeOffset]::UtcNow.ToString('o')
            event = $Event
            restartId = $RestartId
            detail = $Detail
        }
        $line = ($entry | ConvertTo-Json -Compress) + [Environment]::NewLine
        [IO.File]::AppendAllText($script:SupervisorLogPath, $line, [Text.UTF8Encoding]::new($false))
    } catch {
    }
}

function Write-RestartStatus(
    $Request,
    [ValidateSet('PENDING', 'STARTING', 'VERIFYING', 'ROLLING_BACK', 'SUCCEEDED', 'ROLLED_BACK', 'RECOVERY_FAILED')]
    [string]$State,
    $ReasonCode = $null
) {
    $status = [ordered]@{
        restartId = [string]$Request.restartId
        state = $State
        reasonCode = $ReasonCode
        fingerprint = [string]$Request.fingerprint
        updatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    # A failing status write must never bubble up: the top level handler tears
    # the backend down when it does, which leaves the wizard without a terminal
    # state. Retry a few times and only record the failure in the event log.
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        try {
            Write-AtomicJson -Path $script:StatusPath -Value $status
            Write-SupervisorEvent -Event $State -RestartId ([string]$Request.restartId)
            return
        } catch {
            if ($attempt -eq 2) {
                Write-SupervisorEvent -Event 'STATUS_WRITE_FAILED' -RestartId ([string]$Request.restartId) -Detail $State
                return
            }
            Start-Sleep -Milliseconds 100
        }
    }
}

function Test-StopRequested {
    return [IO.File]::Exists($script:StopPath)
}

function Test-BackupPath($Request) {
    if ($Request.previousConfigMissing) {
        return $true
    }
    if ([string]::IsNullOrWhiteSpace([string]$Request.backupPath)) {
        return $false
    }
    try {
        $root = [IO.Path]::GetFullPath($script:ControlDir).TrimEnd('\') + [IO.Path]::DirectorySeparatorChar
        $candidate = [IO.Path]::GetFullPath([string]$Request.backupPath)
        if (-not $candidate.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        # The backup must belong to this transaction: a request pointing at the
        # backup of an older transaction would restore the wrong configuration.
        $expectedName = [string]$Request.restartId + '.yml'
        if (-not [string]::Equals([IO.Path]::GetFileName($candidate), $expectedName, [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        return [IO.File]::Exists($candidate)
    } catch {
        return $false
    }
}

function Restore-PreviousConfiguration($Request) {
    if ($Request.previousConfigMissing) {
        if ([IO.File]::Exists($script:ConfigPath)) {
            [IO.File]::Delete($script:ConfigPath)
        }
        return
    }
    if (-not (Test-BackupPath $Request)) {
        # The backup is gone or does not belong to this transaction. The
        # configuration on disk is already the restored one in that case, so
        # keep it and let the backend start instead of failing the whole run.
        Write-SupervisorEvent -Event 'BACKUP_UNAVAILABLE' -RestartId ([string]$Request.restartId)
        return
    }
    $directory = [IO.Path]::GetDirectoryName($script:ConfigPath)
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    $tempPath = Join-Path $directory ('.application-local.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::Copy([string]$Request.backupPath, $tempPath, $true)
        Move-Atomically -TempPath $tempPath -Path $script:ConfigPath
    } finally {
        if ([IO.File]::Exists($tempPath)) {
            Remove-Item -LiteralPath $tempPath -Force
        }
    }
}

function Remove-TransactionArtifacts($Request) {
    $currentRequest = Get-JsonContent $script:RequestPath
    if ($null -ne $currentRequest -and [string]$currentRequest.restartId -eq [string]$Request.restartId) {
        Remove-Item -LiteralPath $script:RequestPath -Force
    }
    if (-not $Request.previousConfigMissing -and (Test-BackupPath $Request)) {
        $backupPath = [string]$Request.backupPath
        if ([IO.File]::Exists($backupPath)) {
            Remove-Item -LiteralPath $backupPath -Force
        }
    }
}

function Write-ProcessRecord($Process, [string]$Path, [int]$OwnerPid = 0) {
    $record = [ordered]@{
        pid = $Process.Id
        startedAt = $Process.StartTime.ToUniversalTime().ToString('o')
        processName = $Process.ProcessName
    }
    try {
        $record.image = $Process.Path
    } catch {
    }
    if ($OwnerPid -gt 0) {
        $record.supervisorPid = $OwnerPid
    }
    Write-AtomicJson -Path $Path -Value $record
}

function Get-ProcessRecord([string]$Path) {
    return Get-JsonContent $Path
}

function Test-ProcessRecordOwnership($Record) {
    if ($null -eq $Record) {
        return $false
    }
    try {
        $processId = [int]$Record.pid
        if ($processId -le 0) {
            return $false
        }
        $process = Get-Process -Id $processId -ErrorAction Stop
        if (-not [string]::Equals([string]$Record.processName, $process.ProcessName, [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
        # A recycled PID can carry the same process name; the executable path
        # is the stronger signal that this is still the process we started.
        if ($null -ne $Record.PSObject.Properties['image']) {
            $recordedImage = [string]$Record.image
            $actualImage = ''
            try {
                $actualImage = [string]$process.Path
            } catch {
            }
            if (-not [string]::IsNullOrWhiteSpace($recordedImage) -and
                -not [string]::IsNullOrWhiteSpace($actualImage) -and
                -not [string]::Equals($recordedImage, $actualImage, [StringComparison]::OrdinalIgnoreCase)) {
                return $false
            }
        }
        $recordedStart = [DateTimeOffset]::Parse([string]$Record.startedAt, [Globalization.CultureInfo]::InvariantCulture)
        $actualStart = [DateTimeOffset]$process.StartTime.ToUniversalTime()
        return [Math]::Abs(($recordedStart - $actualStart).TotalSeconds) -le 2
    } catch {
        return $false
    }
}

function Get-OwnedProcess($Record) {
    if (-not (Test-ProcessRecordOwnership $Record)) {
        return $null
    }
    return Get-Process -Id ([int]$Record.pid) -ErrorAction SilentlyContinue
}

function Stop-OwnedSingleProcess($Record) {
    $process = Get-OwnedProcess $Record
    if ($null -eq $process) {
        return
    }
    Stop-Process -Id $process.Id -Force -ErrorAction Stop
    [void]$process.WaitForExit(10000)
    if (-not $process.HasExited) {
        throw 'Owned process did not exit.'
    }
}

function Stop-OwnedProcessTree($Record) {
    $root = Get-OwnedProcess $Record
    if ($null -eq $root) {
        return
    }
    $allProcesses = @(Get-CimInstance Win32_Process)
    $seen = @{}
    $orderedIds = New-Object Collections.Generic.List[int]
    $queue = New-Object Collections.Generic.Queue[int]
    $queue.Enqueue($root.Id)
    $seen[$root.Id] = $true
    while ($queue.Count -gt 0) {
        $parentId = $queue.Dequeue()
        $orderedIds.Add($parentId)
        foreach ($candidate in $allProcesses) {
            $candidateId = [int]$candidate.ProcessId
            if ([int]$candidate.ParentProcessId -eq $parentId -and -not $seen.ContainsKey($candidateId)) {
                $seen[$candidateId] = $true
                $queue.Enqueue($candidateId)
            }
        }
    }
    $ids = $orderedIds.ToArray()
    [Array]::Reverse($ids)
    foreach ($processId in $ids) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
    [void]$root.WaitForExit(10000)
    if (-not $root.HasExited) {
        throw 'Owned process tree did not exit.'
    }
}

function Start-BackendProcess {
    if (Test-StopRequested) {
        return $null
    }
    if (-not [IO.File]::Exists($script:JavaPath) -or -not [IO.File]::Exists($script:JarPath)) {
        throw 'Backend executable or JAR is unavailable.'
    }
    $env:MDR_RESTART_CONTROL_DIR = $script:ControlDir
    $env:MDR_RESTART_SUPERVISED = 'true'
    # The backend must use the exact configuration file that this supervisor
    # backs up and restores; otherwise an isolated deployment would silently
    # fall back to the user profile configuration.
    $arguments = @(
        ('-Dmetadata.registry.config.file="{0}"' -f $script:ConfigPath)
    )
    # JVM options must precede -jar: everything after it is an application
    # argument and would be silently ignored by the JVM.
    foreach ($extra in $script:JvmArgs) {
        if (-not [string]::IsNullOrWhiteSpace($extra)) {
            $arguments += $extra
        }
    }
    $arguments += '-jar'
    $arguments += ('"{0}"' -f $script:JarPath)
    $arguments += ('--spring.profiles.active={0}' -f $script:Profile)
    if ($script:ServerPort -gt 0) {
        $arguments += ('--server.port={0}' -f $script:ServerPort)
    }
    # Each start attempt gets its own log file: Start-Process truncates the
    # redirect target, which would erase the startup failure we need to
    # diagnose when the target configuration is rolled back.
    $logStamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $stdoutPath = Join-Path $script:LogDirectory ('backend-stdout-{0}.log' -f $logStamp)
    $stderrPath = Join-Path $script:LogDirectory ('backend-stderr-{0}.log' -f $logStamp)
    $process = Start-Process -FilePath $script:JavaPath -ArgumentList $arguments `
        -WorkingDirectory $script:WorkingDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    $script:BackendProcess = $process
    try {
        Write-ProcessRecord -Process $process -Path $script:BackendPidPath -OwnerPid $PID
    } catch {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        $script:BackendProcess = $null
        throw
    }
    Write-SupervisorEvent -Event 'BACKEND_STARTED' -RestartId $script:ActiveRestartId -Detail ('pid=' + $process.Id)
    return $process
}

function Clear-ExitedBackendProcess {
    if ($null -eq $script:BackendProcess) {
        return
    }
    $script:BackendProcess = $null
    if ([IO.File]::Exists($script:BackendPidPath)) {
        Remove-Item -LiteralPath $script:BackendPidPath -Force
    }
}

function Remove-ProcessRecordIfNotOwned([string]$Path) {
    try {
        $record = Get-ProcessRecord $Path
        if ($null -eq $record -or $null -eq (Get-OwnedProcess $record)) {
            if ([IO.File]::Exists($Path)) {
                Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
    }
}

function Stop-ManagedBackend {
    $process = $script:BackendProcess
    $keepRecord = $false
    try {
        if ($null -ne $process) {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction Stop
                [void]$process.WaitForExit(10000)
                if (-not $process.HasExited) {
                    throw 'Managed backend did not exit.'
                }
            }
            Write-SupervisorEvent -Event 'BACKEND_STOPPED' -RestartId $script:ActiveRestartId -Detail ('pid=' + $process.Id)
        }
    } catch {
        $keepRecord = $true
        throw
    } finally {
        $script:BackendProcess = $null
        if (-not $keepRecord -and [IO.File]::Exists($script:BackendPidPath)) {
            Remove-Item -LiteralPath $script:BackendPidPath -Force -ErrorAction SilentlyContinue
        }
    }
}

function Wait-ForOrdinaryExit {
    while ($true) {
        if (Test-StopRequested) {
            return $true
        }
        if ($null -eq $script:BackendProcess -or $script:BackendProcess.HasExited) {
            return $false
        }
        Start-Sleep -Milliseconds $script:PollIntervalMilliseconds
    }
}

function Get-StartupReady {
    try {
        $uri = 'http://localhost:{0}/api/v1/startup/status' -f $script:Port
        $response = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec $script:HttpTimeoutSeconds
        $payload = Get-ApiPayload $response
        return $null -ne $payload -and $payload.ready -is [bool] -and $payload.ready
    } catch {
        return $false
    }
}

function Get-RestartVerification([string]$RestartId) {
    try {
        $uri = 'http://localhost:{0}/api/v1/datasource/restarts/{1}' -f $script:Port, [uri]::EscapeDataString($RestartId)
        $response = Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec $script:HttpTimeoutSeconds
        $payload = Get-ApiPayload $response
        if ($null -eq $payload) {
            return $null
        }
        return ([string]$payload.verification).ToUpperInvariant()
    } catch {
        return $null
    }
}

function Wait-ForTarget($Request) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($script:StartupTimeoutSeconds)
    $ready = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if (Test-StopRequested) {
            return [pscustomobject]@{ Kind = 'STOP'; ReasonCode = 'STOP_REQUESTED' }
        }
        if ($null -eq $script:BackendProcess -or $script:BackendProcess.HasExited) {
            return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'STARTUP_EXITED' }
        }
        if (-not $ready) {
            if (Get-StartupReady) {
                $ready = $true
                Write-RestartStatus -Request $Request -State 'VERIFYING' -ReasonCode $null
            }
        } else {
            $verification = Get-RestartVerification ([string]$Request.restartId)
            if ($verification -eq 'MISMATCHED') {
                return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'CONFIG_MISMATCH' }
            }
            if ($verification -eq 'MATCHED') {
                if (Test-StopRequested) {
                    return [pscustomobject]@{ Kind = 'STOP'; ReasonCode = 'STOP_REQUESTED' }
                }
                if ($script:BackendProcess.HasExited) {
                    return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'STARTUP_EXITED' }
                }
                return [pscustomobject]@{ Kind = 'SUCCESS'; ReasonCode = $null }
            }
        }
        Start-Sleep -Milliseconds $script:PollIntervalMilliseconds
    }
    return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'STARTUP_TIMEOUT' }
}

function Wait-ForRecovery {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($script:RecoveryTimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if (Test-StopRequested) {
            return [pscustomobject]@{ Kind = 'STOP'; ReasonCode = 'STOP_REQUESTED' }
        }
        if ($null -eq $script:BackendProcess -or $script:BackendProcess.HasExited) {
            return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'ROLLBACK_EXITED' }
        }
        if (Get-StartupReady) {
            if (Test-StopRequested) {
                return [pscustomobject]@{ Kind = 'STOP'; ReasonCode = 'STOP_REQUESTED' }
            }
            if ($script:BackendProcess.HasExited) {
                return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'ROLLBACK_EXITED' }
            }
            return [pscustomobject]@{ Kind = 'SUCCESS'; ReasonCode = $null }
        }
        Start-Sleep -Milliseconds $script:PollIntervalMilliseconds
    }
    return [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'ROLLBACK_TIMEOUT' }
}

function Get-RestartTransaction {
    $status = Get-JsonContent $script:StatusPath
    $request = Get-JsonContent $script:RequestPath
    if ($null -ne $status -and $script:TerminalStates -notcontains [string]$status.state) {
        if ($null -eq $request -or [string]$request.restartId -ne [string]$status.restartId) {
            return [pscustomobject]@{
                Request = $null
                RestartId = [string]$status.restartId
                Fingerprint = [string]$status.fingerprint
                IsPending = $false
                InvalidReason = 'TRANSACTION_INCOMPLETE'
            }
        }
        return [pscustomobject]@{
            Request = $request
            RestartId = [string]$request.restartId
            Fingerprint = [string]$request.fingerprint
            IsPending = [string]$status.state -eq 'PENDING'
            InvalidReason = $null
        }
    }
    if ($null -ne $request) {
        if ($null -ne $status -and [string]$request.restartId -eq [string]$status.restartId) {
            return $null
        }
        return [pscustomobject]@{
            Request = $request
            RestartId = [string]$request.restartId
            Fingerprint = [string]$request.fingerprint
            IsPending = $true
            InvalidReason = $null
        }
    }
    return $null
}

function Test-ValidTransaction($Transaction) {
    if ($null -eq $Transaction -or $null -eq $Transaction.Request) {
        return $false
    }
    $parsedId = [guid]::Empty
    return [guid]::TryParse([string]$Transaction.Request.restartId, [ref]$parsedId) -and
        -not [string]::IsNullOrWhiteSpace([string]$Transaction.Request.fingerprint) -and
        (Test-BackupPath $Transaction.Request)
}

function Complete-TransactionCleanup($Request) {
    try {
        Remove-TransactionArtifacts $Request
    } catch {
        Write-SupervisorEvent -Event 'CLEANUP_FAILED' -RestartId ([string]$Request.restartId)
    }
}

function Invoke-Recovery($Request, [string]$ReasonCode) {
    $script:ActiveRestartId = [string]$Request.restartId
    Write-RestartStatus -Request $Request -State 'ROLLING_BACK' -ReasonCode $ReasonCode
    if (Test-StopRequested) {
        return [pscustomobject]@{ Kind = 'STOP' }
    }
    try {
        Stop-ManagedBackend
        Restore-PreviousConfiguration $Request
    } catch {
        Write-RestartStatus -Request $Request -State 'RECOVERY_FAILED' -ReasonCode 'CONFIG_RESTORE_FAILED'
        return [pscustomobject]@{ Kind = 'FAILURE' }
    }
    if (Test-StopRequested) {
        return [pscustomobject]@{ Kind = 'STOP' }
    }
    try {
        Start-BackendProcess | Out-Null
        if ($null -eq $script:BackendProcess) {
            return [pscustomobject]@{ Kind = 'STOP' }
        }
    } catch {
        Write-RestartStatus -Request $Request -State 'RECOVERY_FAILED' -ReasonCode 'ROLLBACK_START_FAILED'
        return [pscustomobject]@{ Kind = 'FAILURE' }
    }
    $outcome = Wait-ForRecovery
    if ($outcome.Kind -eq 'STOP') {
        return $outcome
    }
    if ($outcome.Kind -eq 'SUCCESS') {
        Write-RestartStatus -Request $Request -State 'ROLLED_BACK' -ReasonCode $ReasonCode
        Complete-TransactionCleanup $Request
        $script:ActiveRestartId = $null
        return [pscustomobject]@{ Kind = 'SUCCESS' }
    }
    try {
        Stop-ManagedBackend
    } catch {
        $outcome.ReasonCode = 'ROLLBACK_STOP_FAILED'
    }
    Write-RestartStatus -Request $Request -State 'RECOVERY_FAILED' -ReasonCode $outcome.ReasonCode
    return [pscustomobject]@{ Kind = 'FAILURE' }
}

function Start-SupervisorHost {
    Initialize-SupervisorPaths
    $powerShellPath = (Get-Process -Id $PID).Path
    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', ('"{0}"' -f $script:SupervisorScriptPath),
        '-ControlDir', ('"{0}"' -f $script:ControlDir),
        '-ConfigPath', ('"{0}"' -f $script:ConfigPath),
        '-JavaPath', ('"{0}"' -f $script:JavaPath),
        '-JarPath', ('"{0}"' -f $script:JarPath),
        '-WorkingDirectory', ('"{0}"' -f $script:WorkingDirectory),
        '-LogDirectory', ('"{0}"' -f $script:LogDirectory),
        '-Port', [string]$Port,
        '-StartupTimeoutSeconds', [string]$StartupTimeoutSeconds,
        '-RecoveryTimeoutSeconds', [string]$RecoveryTimeoutSeconds,
        '-HttpTimeoutSeconds', [string]$HttpTimeoutSeconds,
        '-PollIntervalMilliseconds', [string]$PollIntervalMilliseconds,
        '-Profile', ('"{0}"' -f $script:Profile),
        '-ServerPort', [string]$script:ServerPort
    )
    foreach ($extra in $script:JvmArgs) {
        if (-not [string]::IsNullOrWhiteSpace($extra)) {
            $arguments += ('-JvmArgs', ('"{0}"' -f $extra))
        }
    }
    Start-Process -FilePath $powerShellPath -ArgumentList $arguments -WindowStyle Hidden | Out-Null
}

function Stop-OrphanedManagedBackend {
    # Returns $true when an orphaned backend was stopped. The function never
    # throws: a startup path that cannot prove ownership must not tear the
    # supervisor down, it must only report what it could not clean up.
    $record = Get-ProcessRecord $script:BackendPidPath
    if ($null -eq $record) {
        return $false
    }
    $process = Get-OwnedProcess $record
    if ($null -eq $process) {
        Remove-ProcessRecordIfNotOwned $script:BackendPidPath
        return $false
    }
    if ($null -eq $record.PSObject.Properties['supervisorPid']) {
        Write-SupervisorEvent -Event 'ORPHAN_BACKEND_OWNERSHIP_UNKNOWN' -Detail ('pid=' + $process.Id)
        return $false
    }
    $ownerPid = 0
    if (-not [int]::TryParse([string]$record.supervisorPid, [ref]$ownerPid)) {
        Write-SupervisorEvent -Event 'ORPHAN_BACKEND_OWNERSHIP_UNKNOWN' -Detail ('pid=' + $process.Id)
        return $false
    }
    if ($ownerPid -le 0 -or $ownerPid -eq $PID) {
        Write-SupervisorEvent -Event 'ORPHAN_BACKEND_OWNERSHIP_AMBIGUOUS' -Detail ('pid=' + $process.Id)
        return $false
    }
    $supervisorRecord = Get-ProcessRecord $script:SupervisorPidPath
    if ($null -ne $supervisorRecord) {
        if ($null -ne (Get-OwnedProcess $supervisorRecord)) {
            Write-SupervisorEvent -Event 'ORPHAN_BACKEND_SUPERVISOR_ALIVE' -Detail ('pid=' + $ownerPid)
            return $false
        }
        $recordedSupervisorPid = 0
        if (-not [int]::TryParse([string]$supervisorRecord.pid, [ref]$recordedSupervisorPid) -or
            $recordedSupervisorPid -ne $ownerPid) {
            Write-SupervisorEvent -Event 'ORPHAN_BACKEND_OWNERSHIP_MISMATCH' -Detail ('pid=' + $process.Id)
            return $false
        }
    }
    Stop-OwnedSingleProcess $record
    Remove-ProcessRecordIfNotOwned $script:BackendPidPath
    Write-SupervisorEvent -Event 'ORPHAN_BACKEND_STOPPED' -Detail ('pid=' + $process.Id)
    return $true
}

function Start-BackendWithCurrentConfiguration {
    # Starts the backend against the configuration that is currently on disk and
    # keeps it alive even when the supervisor later exits. Used when a restart
    # transaction cannot be completed: the user must still end up with a
    # running system instead of a stopped one.
    if ($null -ne $script:BackendProcess -and -not $script:BackendProcess.HasExited) {
        $script:KeepBackendAlive = $true
        return $true
    }
    try {
        Start-BackendProcess | Out-Null
    } catch {
        Write-SupervisorEvent -Event 'FALLBACK_START_FAILED' -RestartId $script:ActiveRestartId
        return $false
    }
    if ($null -eq $script:BackendProcess) {
        return $false
    }
    $script:KeepBackendAlive = $true
    return $true
}

function Get-ConfigurationBackupFiles {
    # Backups live either directly in the control directory or in its
    # "backups" subdirectory (the backend writes them to the latter).
    $files = New-Object System.Collections.Generic.List[string]
    try {
        foreach ($file in [IO.Directory]::GetFiles($script:ControlDir, '*.yml')) {
            $files.Add($file)
        }
    } catch {
    }
    $backupDirectory = Join-Path $script:ControlDir 'backups'
    try {
        if ([IO.Directory]::Exists($backupDirectory)) {
            foreach ($file in [IO.Directory]::GetFiles($backupDirectory, '*.yml')) {
                $files.Add($file)
            }
        }
    } catch {
    }
    return $files.ToArray()
}

function Remove-StaleTransactionBackups {
    # Configuration backups contain the database credentials in plain text. A
    # crashed transaction can leave them behind, so anything older than a week
    # that does not belong to the running transaction is removed.
    try {
        # Compare in UTC DateTime: comparing a DateTime with a DateTimeOffset
        # raises a conversion error in PowerShell, which would silently skip
        # every backup.
        $cutoff = [DateTime]::UtcNow.AddDays(-7)
        $keepName = ''
        $request = Get-JsonContent $script:RequestPath
        $status = Get-JsonContent $script:StatusPath
        $actionable = $null -eq $status -or $script:TerminalStates -notcontains [string]$status.state
        if ($actionable -and $null -ne $request -and -not [string]::IsNullOrWhiteSpace([string]$request.restartId)) {
            $keepName = [string]$request.restartId + '.yml'
        }
        foreach ($file in (Get-ConfigurationBackupFiles)) {
            $name = [IO.Path]::GetFileName($file)
            if ($name -eq $keepName) {
                continue
            }
            if ([string]::Equals([IO.Path]::GetFullPath($file), $script:ConfigPath, [StringComparison]::OrdinalIgnoreCase)) {
                continue
            }
            try {
                $written = [IO.File]::GetLastWriteTimeUtc($file)
                if ($written -ge $cutoff) {
                    continue
                }
                Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
                Write-SupervisorEvent -Event 'STALE_BACKUP_REMOVED' -Detail $name
            } catch {
                Write-SupervisorEvent -Event 'STALE_BACKUP_CHECK_FAILED' -Detail $name
            }
        }
    } catch {
    }
}

function Invoke-Supervisor {
    Initialize-SupervisorPaths
    if (Test-StopRequested) {
        Write-SupervisorEvent -Event 'STOP_REQUESTED'
        return
    }
    $env:MDR_RESTART_CONTROL_DIR = $script:ControlDir
    $env:MDR_RESTART_SUPERVISED = 'true'
    $hash = [Security.Cryptography.SHA256]::Create()
    try {
        $hashBytes = $hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($script:ControlDir.ToLowerInvariant()))
        $mutexName = 'Local\MetadataRegistry4j.BackendSupervisor.' + ([BitConverter]::ToString($hashBytes).Replace('-', '').Substring(0, 32))
    } finally {
        $hash.Dispose()
    }
    $mutex = [Threading.Mutex]::new($false, $mutexName)
    $acquired = $false
    try {
        try {
            $acquired = $mutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $acquired = $true
        }
        if (-not $acquired) {
            # Another supervisor already owns this control directory. Leave a
            # marker behind so start.bat can tell the user why the automatic
            # restart is unavailable instead of failing silently.
            try {
                Write-AtomicText -Path $script:SupervisorErrorPath -Content (
                    'Another supervisor instance already owns control directory: ' + $script:ControlDir)
            } catch {
            }
            return
        }
        try {
            if ([IO.File]::Exists($script:SupervisorErrorPath)) {
                Remove-Item -LiteralPath $script:SupervisorErrorPath -Force -ErrorAction SilentlyContinue
            }
        } catch {
        }
        Remove-StaleTransactionBackups
        $transaction = Get-RestartTransaction
        # An orphaned backend from a crashed supervisor still holds the port, so
        # it has to go before this supervisor starts its own backend.
        Stop-OrphanedManagedBackend | Out-Null
        Write-ProcessRecord -Process (Get-Process -Id $PID) -Path $script:SupervisorPidPath
        if ($null -ne $transaction -and -not (Test-ValidTransaction $transaction)) {
            if (-not [string]::IsNullOrWhiteSpace([string]$transaction.RestartId)) {
                $invalidRequest = [pscustomobject]@{ restartId = $transaction.RestartId; fingerprint = $transaction.Fingerprint }
                Write-RestartStatus -Request $invalidRequest -State 'RECOVERY_FAILED' -ReasonCode $(if ([string]::IsNullOrWhiteSpace([string]$transaction.InvalidReason)) { 'TRANSACTION_INVALID' } else { [string]$transaction.InvalidReason })
            }
            # The transaction cannot be completed, but the user must still end up
            # with a running system: continue with the configuration on disk.
            $transaction = $null
        }
        if ($null -ne $transaction) {
            $script:ActiveRestartId = [string]$transaction.Request.restartId
            $outcome = Invoke-Recovery -Request $transaction.Request -ReasonCode 'INCOMPLETE_RESTART'
            if ($outcome.Kind -ne 'SUCCESS') {
                # The rollback failed: keep the system available with the
                # configuration that is on disk instead of leaving it stopped.
                Start-BackendWithCurrentConfiguration | Out-Null
                if ($null -eq $script:BackendProcess) {
                    return
                }
            }
        } else {
            Start-BackendProcess | Out-Null
            if ($null -eq $script:BackendProcess) {
                return
            }
        }

        while ($true) {
            if (Wait-ForOrdinaryExit) {
                return
            }
            $exitDetail = ''
            if ($null -ne $script:BackendProcess) {
                try {
                    $exitDetail = 'exitCode=' + $script:BackendProcess.ExitCode
                } catch {
                }
            }
            Clear-ExitedBackendProcess
            Write-SupervisorEvent -Event 'BACKEND_EXITED' -Detail $exitDetail
            if (Test-StopRequested) {
                return
            }
            $transaction = Get-RestartTransaction
            if ($null -eq $transaction) {
                return
            }
            if (-not (Test-ValidTransaction $transaction)) {
                if (-not [string]::IsNullOrWhiteSpace([string]$transaction.RestartId)) {
                    $invalidRequest = [pscustomobject]@{ restartId = $transaction.RestartId; fingerprint = $transaction.Fingerprint }
                    Write-RestartStatus -Request $invalidRequest -State 'RECOVERY_FAILED' -ReasonCode $(if ([string]::IsNullOrWhiteSpace([string]$transaction.InvalidReason)) { 'TRANSACTION_INVALID' } else { [string]$transaction.InvalidReason })
                }
                # The transaction cannot be applied: bring the backend back up
                # with the configuration on disk so the user is not left with a
                # stopped system.
                Start-BackendWithCurrentConfiguration | Out-Null
                if ($null -eq $script:BackendProcess) {
                    return
                }
                continue
            }
            if (-not $transaction.IsPending) {
                $outcome = Invoke-Recovery -Request $transaction.Request -ReasonCode 'INCOMPLETE_RESTART'
                if ($outcome.Kind -ne 'SUCCESS') {
                    Start-BackendWithCurrentConfiguration | Out-Null
                    if ($null -eq $script:BackendProcess) {
                        return
                    }
                }
                continue
            }

            $script:ActiveRestartId = [string]$transaction.Request.restartId
            Write-RestartStatus -Request $transaction.Request -State 'STARTING' -ReasonCode $null
            try {
                Start-BackendProcess | Out-Null
                if ($null -eq $script:BackendProcess) {
                    return
                }
                $outcome = Wait-ForTarget $transaction.Request
            } catch {
                $outcome = [pscustomobject]@{ Kind = 'FAILURE'; ReasonCode = 'STARTUP_START_FAILED' }
            }
            if ($outcome.Kind -eq 'STOP') {
                return
            }
            if ($outcome.Kind -eq 'SUCCESS') {
                Write-RestartStatus -Request $transaction.Request -State 'SUCCEEDED' -ReasonCode $null
                Complete-TransactionCleanup $transaction.Request
                $script:ActiveRestartId = $null
                continue
            }
            $outcome = Invoke-Recovery -Request $transaction.Request -ReasonCode $outcome.ReasonCode
            if ($outcome.Kind -ne 'SUCCESS') {
                return
            }
        }
    } catch {
        Write-SupervisorEvent -Event 'SUPERVISOR_FAILED' -RestartId $script:ActiveRestartId
        if (-not [string]::IsNullOrWhiteSpace($script:ActiveRestartId) -and -not (Test-StopRequested)) {
            try {
                $request = Get-JsonContent $script:RequestPath
                if ($null -ne $request -and [string]$request.restartId -eq $script:ActiveRestartId) {
                    Write-RestartStatus -Request $request -State 'RECOVERY_FAILED' -ReasonCode 'SUPERVISOR_ERROR'
                }
            } catch {
            }
        }
        $script:SupervisorExitCode = 1
        # A supervisor error must not take a healthy backend down with it: keep
        # the running backend alive and let the user restart the supervisor.
        if (-not (Test-StopRequested)) {
            Start-BackendWithCurrentConfiguration | Out-Null
        }
    } finally {
        if ($acquired) {
            # Only the instance that owns the control directory may touch the
            # shared records: a second instance that lost the mutex race must
            # not delete the running instance's backend record.
            if (-not $script:KeepBackendAlive) {
                try {
                    Stop-ManagedBackend
                } catch {
                    Write-SupervisorEvent -Event 'BACKEND_STOP_FAILED' -RestartId $script:ActiveRestartId
                }
            }
            Remove-ProcessRecordIfNotOwned $script:BackendPidPath
            # Only drop the supervisor record when it is ours: another supervisor
            # may have taken over this control directory in the meantime.
            $supervisorRecord = Get-ProcessRecord $script:SupervisorPidPath
            $recordedPid = 0
            if ($null -ne $supervisorRecord -and
                [int]::TryParse([string]$supervisorRecord.pid, [ref]$recordedPid) -and
                $recordedPid -eq $PID) {
                Remove-Item -LiteralPath $script:SupervisorPidPath -Force -ErrorAction SilentlyContinue
            }
            $mutex.ReleaseMutex()
        }
        $mutex.Dispose()
    }
}

function Invoke-SupervisorStop {
    Initialize-SupervisorPaths
    if (-not (Test-StopRequested)) {
        Write-AtomicText -Path $script:StopPath -Content 'stop'
    }
    $failed = $false
    try {
        Stop-OwnedProcessTree (Get-ProcessRecord $script:FrontendPidPath)
    } catch {
        $failed = $true
    }
    $supervisorRecord = Get-ProcessRecord $script:SupervisorPidPath
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(10)
    while ($null -ne (Get-OwnedProcess $supervisorRecord) -and [DateTimeOffset]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 250
    }
    try {
        Stop-OwnedSingleProcess $supervisorRecord
    } catch {
        $failed = $true
    }
    try {
        Stop-OwnedSingleProcess (Get-ProcessRecord $script:BackendPidPath)
    } catch {
        $failed = $true
    }
    foreach ($path in @($script:FrontendPidPath, $script:SupervisorPidPath, $script:BackendPidPath)) {
        Remove-ProcessRecordIfNotOwned $path
    }
    return -not $failed
}

if ($MyInvocation.InvocationName -eq '.') {
    return
}

if ($Stop) {
    if (Invoke-SupervisorStop) {
        exit 0
    }
    exit 1
}

Invoke-Supervisor
exit $script:SupervisorExitCode
