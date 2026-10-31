$ErrorActionPreference = "Stop"

$gradleVersion = "8.11.1"
$zipPath = Join-Path $env:TEMP "gradle-$gradleVersion-bin.zip"
$installRoot = Join-Path $env:USERPROFILE ".gradle\bootstrap"
$gradleHome = Join-Path $installRoot "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome "bin\gradle.bat"

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $installRoot | Out-Null
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $zipPath -DestinationPath $installRoot -Force
}

Write-Host "Generating Gradle Wrapper..."
& $gradleExe wrapper --gradle-version $gradleVersion --distribution-type bin

if ($LASTEXITCODE -ne 0) {
    throw "Gradle Wrapper generation failed."
}

Write-Host ""
Write-Host "Wrapper generated. You can now use .\gradlew.bat"
