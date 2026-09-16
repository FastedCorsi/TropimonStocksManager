param(
    [Parameter(Mandatory=$true)][string]$LauncherRoot,
    [ValidateSet('official','coexistence')][string]$Profile = 'official'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$launcher = (Resolve-Path -LiteralPath $LauncherRoot).Path
$version = Get-Content -LiteralPath (Join-Path $launcher '1.21.1.json') -Raw | ConvertFrom-Json
$fabric = Get-Content -LiteralPath (Join-Path $launcher 'fabric-loader-0.17.3-1.21.1.json') -Raw | ConvertFrom-Json
$run = Join-Path $projectRoot ('build/smoke-runs/' + $Profile + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$runMods = Join-Path $run 'mods'
New-Item -ItemType Directory -Path $runMods -Force | Out-Null
$installedMods = Join-Path $launcher 'mods'
$officialNames = @('fabric-api-0.116.6+1.21.1.jar', 'Cobblemon-fabric-1.7.2+1.21.1.jar',
    'TropimodClient-1.0.0+1.21.1.jar', 'XaerosWorldMap_1.39.12_Fabric_1.21.jar',
    'mega_showdown-fabric-1.6.4+1.7.2+1.21.1.jar', 'architectury-13.0.8-fabric-OPTIONAL.jar',
    'geckolib-fabric-1.21.1-4.8.2.jar', 'trinkets-3.10.0.jar')
foreach ($name in $officialNames) {
    Copy-Item -LiteralPath (Join-Path $installedMods $name) -Destination $runMods
}
if ($Profile -eq 'coexistence') {
    Get-ChildItem -LiteralPath $installedMods -Filter 'Tropimon*-LOCAL.jar' |
        Where-Object { $_.Name -notlike 'TropimonStocksManager-*' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $runMods }
}
$modVersion = ((Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties')) |
    Where-Object { $_ -like 'mod_version=*' } | Select-Object -First 1).Split('=', 2)[1]
Copy-Item -LiteralPath (Join-Path $projectRoot ("build/libs/TropimonStocksManager-$modVersion.jar")) -Destination $runMods
Copy-Item -LiteralPath (Join-Path $projectRoot ("build/smoke/TropimonStocksManager-$modVersion-smoke.jar")) -Destination $runMods

$classPath = [System.Collections.Generic.List[string]]::new()
foreach ($library in @($fabric.libraries) + @($version.libraries)) {
    $allowed = -not $library.rules
    foreach ($rule in $library.rules) {
        if ($rule.os -and $rule.os.name -ne 'windows') { continue }
        if ($rule.features) { continue }
        $allowed = $rule.action -eq 'allow'
    }
    if (-not $allowed) { continue }
    $relative = $library.downloads.artifact.path
    if (-not $relative) {
        $parts = $library.name.Split(':')
        $relative = $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar'
    }
    $jar = Join-Path (Join-Path $launcher 'libraries') $relative
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing runtime library: $jar" }
    $classPath.Add($jar)
}
$classPath.Add((Join-Path $launcher 'client.jar'))
$java = Join-Path $launcher 'runtime/x64/jdk-21.0.6+7/bin/java.exe'
$arguments = @('-Xmx3G', ('-Djava.library.path=' + (Join-Path $launcher 'natives')),
    '-cp', ($classPath -join ';'), $fabric.mainClass,
    '--username', 'StocksSmoke', '--uuid', '00000000000000000000000000000000', '--accessToken', '0',
    '--version', '1.21.1', '--gameDir', $run, '--assetsDir', (Join-Path $launcher 'assets'),
    '--assetIndex', $version.assetIndex.id, '--userType', 'legacy', '--versionType', 'release',
    '--width', '960', '--height', '540')
$quoted = ($arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
$output = Join-Path $run 'stdout.log'
$errors = Join-Path $run 'stderr.log'
$process = Start-Process -FilePath $java -ArgumentList $quoted -WorkingDirectory $run -WindowStyle Hidden `
    -RedirectStandardOutput $output -RedirectStandardError $errors -PassThru
[pscustomobject]@{ ProcessId=$process.Id; Profile=$Profile; Directory=$run; Output=$output; Errors=$errors } | ConvertTo-Json
