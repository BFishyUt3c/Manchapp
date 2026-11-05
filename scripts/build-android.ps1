param([ValidateSet('Both','Seller','Customer')][string]$App='Both')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskStudioJava='C:\Program Files\Android\Android Studio1\jbr'
if (!(Get-Command java -ErrorAction SilentlyContinue) -and (Test-Path -LiteralPath $taskStudioJava)) { $env:JAVA_HOME=$taskStudioJava }
if (Test-Path -LiteralPath $taskStudioJava) { $env:JAVA_HOME=$taskStudioJava }
Push-Location -LiteralPath $taskRoot
try {
    & node (Join-Path $PSScriptRoot 'prepare-native-assets.mjs')
    if($LASTEXITCODE -ne 0){throw 'No se pudieron preparar los recursos Android.'}
    $taskTargets=@()
    if($App -in @('Both','Seller')){$taskTargets+=':app:assembleDebug'}
    if($App -in @('Both','Customer')){$taskTargets+=':clientes:assembleDebug'}
    & (Join-Path $taskRoot 'gradlew.bat') @taskTargets --console=plain
    if($LASTEXITCODE -ne 0){throw 'No se pudieron compilar las apps Android.'}
    $taskOutput=Join-Path $taskRoot 'output'
    New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
    if($App -in @('Both','Seller')){
        $taskSeller=Join-Path $taskRoot 'android\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk'
        Copy-Item -LiteralPath $taskSeller -Destination (Join-Path $taskOutput 'ManchApp-Emprendedora.apk') -Force
        Copy-Item -LiteralPath $taskSeller -Destination (Join-Path $taskOutput 'ManchApp-beta.apk') -Force
        Copy-Item -LiteralPath (Join-Path $taskRoot 'android\app\build\outputs\apk\debug\app-armeabi-v7a-debug.apk') -Destination (Join-Path $taskOutput 'ManchApp-Emprendedora-Android32.apk') -Force
        Copy-Item -LiteralPath (Join-Path $taskRoot 'android\app\build\outputs\apk\debug\app-x86_64-debug.apk') -Destination (Join-Path $taskOutput 'ManchApp-Emprendedora-Emulador.apk') -Force
    }
    if($App -in @('Both','Customer')){
        Copy-Item -LiteralPath (Join-Path $taskRoot 'android\clientes\build\outputs\apk\debug\clientes-debug.apk') -Destination (Join-Path $taskOutput 'ManchApp-Pedidos.apk') -Force
    }
    Get-ChildItem -LiteralPath $taskOutput -Filter 'ManchApp-*.apk' | Select-Object Name,Length
} finally { Pop-Location }
