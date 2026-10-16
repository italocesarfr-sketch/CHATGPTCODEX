$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$cache = Join-Path $env:USERPROFILE '.gradle\diary-bootstrap'
$gradle = Join-Path $cache 'gradle-8.9\bin\gradle.bat'
if (!(Test-Path $gradle)) {
    New-Item -ItemType Directory -Force $cache | Out-Null
    $url = 'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
    $zip = Join-Path $cache 'gradle.zip'
    Invoke-WebRequest $url -OutFile $zip
    $expected = (Invoke-WebRequest "$url.sha256").Content.Trim()
    if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw 'Falha na integridade do Gradle' }
    Expand-Archive $zip $cache -Force
    Remove-Item $zip
}
& $gradle -p $root @args
exit $LASTEXITCODE
