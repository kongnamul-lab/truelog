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
$s.Volume = 100
$dir = Join-Path $env:USERPROFILE "StudioProjects\TrueLog\app\src\main\res\raw"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# 소리 키우기 (v0.8.1): 윈도우 음성은 녹음 크기가 작다 → 가장 큰 소리가 최대치의 80%가 되도록 키운다(최대 6배, 깨짐 방지 여유).
function Normalize-Wav($path) {
    $b = [IO.File]::ReadAllBytes($path)
    $i = 12
    while ($i -lt $b.Length - 8) {
        $id = [Text.Encoding]::ASCII.GetString($b, $i, 4)
        $size = [BitConverter]::ToInt32($b, $i + 4)
        if ($id -eq 'data') { break }
        $i += 8 + $size
    }
    $start = $i + 8
    $end = [Math]::Min($b.Length, $start + $size) - 1
    $peak = 1
    for ($k = $start; $k -lt $end; $k += 2) {
        $v = [Math]::Abs([int][BitConverter]::ToInt16($b, $k))
        if ($v -gt $peak) { $peak = $v }
    }
    $gain = [Math]::Min(26214.0 / $peak, 6.0)
    for ($k = $start; $k -lt $end; $k += 2) {
        $v = [int]([BitConverter]::ToInt16($b, $k) * $gain)
        if ($v -gt 32767) { $v = 32767 } elseif ($v -lt -32768) { $v = -32768 }
        $bytes = [BitConverter]::GetBytes([int16]$v)
        $b[$k] = $bytes[0]; $b[$k + 1] = $bytes[1]
    }
    [IO.File]::WriteAllBytes($path, $b)
    Write-Output ("  " + [IO.Path]::GetFileName($path) + " 소리 x" + [Math]::Round($gain, 1))
}

$lines = [ordered]@{
    "tl_power_down"   = "출력 제한 경고가 들어왔어요. 계기판 안내를 확인하고, 안전한 곳에 정차하세요. 지금 상황을 기억하고 있어요."
    "tl_hazard"       = "비상등이 켜졌어요. 지금 상황을 기억하고 있어요."
    "tl_low_battery"  = "배터리가 20퍼센트 아래로 내려갔어요. 충전 계획을 확인하세요. 이 순간을 기억했어요."
    "tl_hard_brake"   = "급제동 순간을 기억했어요."
    "tl_manual_save"  = "지금 상황을 저장할게요."
    "tl_ask_symptom"  = "삐 소리가 나면, 어떤 느낌이었는지 한마디 해 주세요."
    "tl_symptom_saved" = "말씀도 같이 기억했어요."
}
foreach ($name in $lines.Keys) {
    $path = Join-Path $dir ($name + ".wav")
    # 48kHz 16비트로 저장 (기본 22kHz는 에뮬레이터가 다시 변환하면서 지직거릴 수 있음)
    $fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(48000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $s.SetOutputToWaveFile($path, $fmt)
    $s.Speak($lines[$name])
}
$s.SetOutputToNull()
$s.Dispose()
foreach ($name in $lines.Keys) { Normalize-Wav (Join-Path $dir ($name + ".wav")) }
Write-Output ("완료: " + $voice.VoiceInfo.Name + " 음성으로 7개 파일을 만들었어요 -> " + $dir)
