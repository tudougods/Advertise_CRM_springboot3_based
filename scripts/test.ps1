$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$mavenWrapper = Join-Path $projectRoot "mvnw.cmd"
$fullOutput = New-Object System.Collections.Generic.List[string]
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$utf8Options = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$env:JAVA_TOOL_OPTIONS = (($previousJavaToolOptions + " " + $utf8Options).Trim())

try {
    Push-Location $projectRoot
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & $mavenWrapper -q test 2>&1 | ForEach-Object {
                $line = [string]$_
                [void]$fullOutput.Add($line)

                if ($line.StartsWith("test")) {
                    [Console]::WriteLine($line)
                    [Console]::Out.Flush()
                }
            }
            $testExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
}

if ($testExitCode -ne 0) {
    [Console]::Error.WriteLine("")
    [Console]::Error.WriteLine("Tests failed. The full Maven log follows:")
    foreach ($line in $fullOutput) {
        [Console]::Error.WriteLine($line)
    }
}

exit $testExitCode
