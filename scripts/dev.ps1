param([ValidateSet('Setup', 'Infra', 'Migrate', 'Seed', 'Dev', 'Test')][string]$Command = 'Dev')
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $envFile) {
  foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
      [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim().Trim('"').Trim("'"), 'Process')
    }
  }
}
function Invoke-InProject([string]$RelativePath, [scriptblock]$Action) {
  Push-Location -LiteralPath (Join-Path $projectRoot $RelativePath)
  try { & $Action; if ($LASTEXITCODE -ne 0) { throw "Command failed in $RelativePath ($LASTEXITCODE)" } }
  finally { Pop-Location }
}
switch ($Command) {
  'Setup' {
    if (!(Test-Path -LiteralPath $envFile)) { Copy-Item -LiteralPath (Join-Path $projectRoot '.env.example') -Destination $envFile }
    Invoke-InProject 'apps/web' { npm.cmd ci }
    Invoke-InProject 'apps/ai-service' { python -m venv .venv; if ($LASTEXITCODE -ne 0) { throw 'venv creation failed' }; & .venv/Scripts/python.exe -m pip install -r requirements.txt }
  }
  'Infra' { Invoke-InProject '.' { docker compose up -d postgres redis redpanda temporal temporal-ui prometheus grafana } }
  'Migrate' { Invoke-InProject 'apps/api' { mvn.cmd spring-boot:run '-Dspring-boot.run.arguments=--recoverai.cli.migrate=true --server.port=0 --recoverai.worker-enabled=false' } }
  'Seed' { Invoke-InProject 'apps/api' { mvn.cmd spring-boot:run '-Dspring-boot.run.arguments=--recoverai.cli.seed=true --server.port=0 --recoverai.worker-enabled=false' } }
  'Test' {
    Invoke-InProject 'apps/api' { mvn.cmd test }
    Invoke-InProject 'apps/ai-service' { & .venv/Scripts/python.exe -m pytest -q }
    Invoke-InProject 'apps/web' { npm.cmd run typecheck; if ($LASTEXITCODE -ne 0) { throw 'Typecheck failed' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'Lint failed' }; npm.cmd run test }
  }
  'Dev' {
    $logDirectory = Join-Path $projectRoot 'reports/dev'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $services = @(
      @{ Name = 'api'; Directory = 'apps/api'; File = 'mvn.cmd'; Arguments = @('spring-boot:run') },
      @{ Name = 'ai'; Directory = 'apps/ai-service'; File = (Join-Path $projectRoot 'apps/ai-service/.venv/Scripts/python.exe'); Arguments = @('-m','uvicorn','app.main:app','--port','8100') },
      @{ Name = 'web'; Directory = 'apps/web'; File = 'npm.cmd'; Arguments = @('run','dev') }
    )
    foreach ($service in $services) {
      $process = Start-Process -FilePath $service.File -ArgumentList $service.Arguments -WorkingDirectory (Join-Path $projectRoot $service.Directory) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDirectory "$($service.Name).log") -RedirectStandardError (Join-Path $logDirectory "$($service.Name).error.log") -PassThru
      Write-Output "$($service.Name) started as PID $($process.Id). Logs: $logDirectory"
    }
  }
}
