param(
    [string]$LwjglVersion='3.3.1',
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$NativeClassifier='natives-windows',
    [string]$GradleCache=$env:GRADLE_USER_HOME
)
$ErrorActionPreference='Stop'
if (!$JavaHome) { throw '请指定 -JavaHome 或设置 JAVA_HOME（Java 17 以上）。' }
if (!$GradleCache) { $GradleCache=Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle' }
$audioRepo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$audioOutput=Join-Path $audioRepo ('build/native-audio-probe/'+$LwjglVersion)
New-Item -ItemType Directory -Path $audioOutput -Force | Out-Null
$audioClasspath=@()
foreach($module in @('lwjgl','lwjgl-openal')) {
    $moduleCache=Join-Path $GradleCache ('caches/modules-2/files-2.1/org.lwjgl/'+$module+'/'+$LwjglVersion)
    foreach($name in @(($module+'-'+$LwjglVersion+'.jar'),($module+'-'+$LwjglVersion+'-'+$NativeClassifier+'.jar'))) {
        $found=@(Get-ChildItem -LiteralPath $moduleCache -Recurse -File -Filter $name)
        if($found.Count -ne 1) { throw ('未找到唯一的缓存依赖 '+$name+'；请先构建对应平台。') }
        $audioClasspath+=$found[0].FullName
    }
}
$separator=[IO.Path]::PathSeparator
& (Join-Path $JavaHome 'bin/javac') --release 17 -encoding UTF-8 -cp ($audioClasspath -join $separator) -d $audioOutput `
    (Join-Path $audioRepo 'shared/src/main/java/com/november/mcphone/feature/music/client/playback/LocalAudioDevice.java') `
    (Join-Path $PSScriptRoot 'NativeAudioProbe.java')
if($LASTEXITCODE -ne 0) { throw '原生音频回归编译失败' }
$previousDriver=$env:ALSOFT_DRIVERS
try {
    $env:ALSOFT_DRIVERS='null'
    & (Join-Path $JavaHome 'bin/java') -cp ((@($audioOutput)+$audioClasspath) -join $separator) `
        com.november.mcphone.feature.music.client.playback.NativeAudioProbe
    if($LASTEXITCODE -ne 0) { throw '原生音频回归失败' }
} finally { $env:ALSOFT_DRIVERS=$previousDriver }
