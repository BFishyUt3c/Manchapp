$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskSdk = Join-Path $taskRoot '.tools\android'
New-Item -ItemType Directory -Force -Path $taskSdk | Out-Null
$taskPackages = @(
    @{ Name = 'platform'; Url = 'https://dl.google.com/android/repository/platform-35_r02.zip'; File = 'platform-35.zip' },
    @{ Name = 'build-tools'; Url = 'https://dl.google.com/android/repository/build-tools_r35_windows.zip'; File = 'build-tools-35.zip' },
    @{ Name = 'platform-tools'; Url = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip'; File = 'platform-tools.zip' }
)
foreach ($taskPackage in $taskPackages) {
    $taskDestination = Join-Path $taskSdk $taskPackage.Name
    if (!(Test-Path -LiteralPath $taskDestination)) {
        $taskArchive = Join-Path $taskSdk $taskPackage.File
        if (!(Test-Path -LiteralPath $taskArchive)) {
            Write-Output ('Descargando herramientas oficiales: ' + $taskPackage.Name)
            Invoke-WebRequest -Uri $taskPackage.Url -OutFile $taskArchive -TimeoutSec 240
        }
        Expand-Archive -LiteralPath $taskArchive -DestinationPath $taskDestination -Force
    }
}
Write-Output 'Herramientas Android disponibles en .tools/android.'
