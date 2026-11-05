$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
Write-Output 'ManchApp - prototipo local. Abre http://localhost:4173 y conserva esta ventana abierta.'
& npm.cmd run demo
