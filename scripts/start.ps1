param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localJdk = Join-Path $projectRoot '.tools\jdk'
if (-not $JdkHome -and (Test-Path -LiteralPath "$localJdk\bin\javac.exe")) {
    $JdkHome = $localJdk
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath "$JdkHome\bin\javac.exe")) {
    throw 'Set JAVA_HOME to a JDK 21 directory, or pass -JdkHome to this script.'
}
$env:JAVA_HOME = $JdkHome
$env:PATH = "$JdkHome\bin;$env:PATH"

Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
    # Read only known datasource variables; never evaluate .env as PowerShell.
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') {
            $name = $Matches[1]
            if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
                [Environment]::SetEnvironmentVariable($name, $Matches[2].Trim(), 'Process')
            }
        }
    }
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Database startup failed. Make sure Docker Desktop is running.' }
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'API startup failed. Review the Maven output above.' }
} finally { Pop-Location }
