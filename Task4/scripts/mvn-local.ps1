param(
    [string[]]$Goals = @('verify'),
    [string]$MavenCommand
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $MavenCommand) {
    $availableMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($availableMaven) {
        $MavenCommand = $availableMaven.Source
    } elseif ($env:MAVEN_HOME) {
        $MavenCommand = Join-Path $env:MAVEN_HOME 'bin/mvn.cmd'
    } else {
        throw '未找到 Maven。请在 IDEA 中使用 Bundled Maven，配置 PATH/MAVEN_HOME，或通过 -MavenCommand 指定命令路径。'
    }
}
if (-not (Test-Path -LiteralPath $MavenCommand -PathType Leaf)) {
    throw '指定的 Maven 路径不存在。'
}
$localRepository = Join-Path $projectRoot '.maven-cache'
Push-Location -LiteralPath $projectRoot
try {
    & $MavenCommand "-Dmaven.repo.local=$localRepository" -B -ntp @Goals
    if ($LASTEXITCODE -ne 0) {
        throw "Maven 执行失败，退出码为 $LASTEXITCODE。"
    }
} finally {
    Pop-Location
}
