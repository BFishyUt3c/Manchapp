param([ValidateRange(1024,65535)][int]$Port=4173)
$ErrorActionPreference='Stop'
$taskNode=(Get-Command node.exe).Source
$taskRule="ManchApp-Local-$Port"
if (!(Get-NetFirewallRule -Name $taskRule -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name $taskRule -DisplayName "ManchApp red local (puerto $Port)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort $Port -RemoteAddress LocalSubnet -Program $taskNode -Profile Private,Public | Out-Null
}
Write-Output "ManchApp puede recibir conexiones de la red local en el puerto $Port."
