# Loads .env and .env.local into the environment and runs the app locally on Windows.
$ErrorActionPreference = "Stop"

function Load-EnvFile($filePath) {
    if (Test-Path $filePath) {
        Get-Content $filePath | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $idx = $line.IndexOf("=")
                $key = $line.Substring(0, $idx).Trim()
                $val = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
}

Load-EnvFile ".env"
Load-EnvFile ".env.local"

mvn spring-boot:run
