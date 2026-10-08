param(
    [int]$Port,
    [string]$Names,
    [string]$Match
)
# Classifies the process listening on $Port for start.bat:
#   exit 0 - port is free
#   exit 1 - port held by this app's process (command line matches $Match)
#   exit 2 - port held by a foreign process
# $Names is a comma-separated allow list, e.g. "javaw.exe,java.exe" (with
# -File, PowerShell does not evaluate commas into arrays, so split here).
$allow = $Names -split ','
$conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $conn) { exit 0 }
$proc = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $conn.OwningProcess)
if ($allow -contains $proc.Name) {
    if ($proc.CommandLine -and $proc.CommandLine -like "*$Match*") { exit 1 }
}
exit 2
