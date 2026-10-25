# 트루로그 음성 안내 파일 만들기 (윈도우 내장 한국어 음성 사용)
# 실행: PowerShell에서  powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\StudioProjects\TrueLog\tools\make_voice.ps1"
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'ko-KR' } | Select-Object -First 1
if (-not $voice) {
    Write-Output "한국어 음성이 없어요. 설치된 음성 목록:"
    $s.GetInstalledVoices() | ForEach-Object { Write-Output (" - " + $_.VoiceInfo.Name + " / " + $_.VoiceInfo.Culture.Name) }
    exit 1
}
$s.SelectVoice($voice.VoiceInfo.Name)
$s.Rate = 0
$dir = Join-Path $env:USERPROFILE "StudioProjects\TrueLog\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$lines = [ordered]@{
    "tl_power_down"   = "출력 제한 경고가 들어왔어요. 계기판 안내를 확인하고, 안전한 곳에 정차하세요. 지금 상황을 기억하고 있어요."
    "tl_hazard"       = "비상등이 켜졌어요. 지금 상황을 기억하고 있어요."
    "tl_low_battery"  = "배터리가 20퍼센트 아래로 내려갔어요. 충전 계획을 확인하세요. 이 순간을 기억했어요."
    "tl_hard_brake"   = "급제동 순간을 기억했어요."
    "tl_manual_save"  = "지금 상황을 저장할게요."
}
foreach ($name in $lines.Keys) {
    $path = Join-Path $dir ($name + ".wav")
    $s.SetOutputToWaveFile($path)
    $s.Speak($lines[$name])
}
$s.SetOutputToNull()
$s.Dispose()
Write-Output ("완료: " + $voice.VoiceInfo.Name + " 음성으로 5개 파일을 만들었어요 -> " + $dir)
