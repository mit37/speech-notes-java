# Regenerates eval/audio/*.wav from eval/fixtures/*.txt with the Windows SAPI voice.
#
#   powershell -File scripts/make_fixtures.ps1
#
# The WAVs are committed (16 kHz mono 16-bit PCM, ~1 MB each) so that CI and anyone cloning
# the repo never needs a TTS engine. Re-run this only if a fixture script changes.
# Requires Windows with a Speech API voice installed (David or Zira ship with Windows).
param(
    [string]$FixtureDir = "eval/fixtures",
    [string]$AudioDir = "eval/audio",
    [string]$Voice = "Microsoft Zira Desktop"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech

$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    16000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

New-Item -ItemType Directory -Force -Path $AudioDir | Out-Null

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$installed = $synth.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo.Name }
if ($installed -contains $Voice) {
    $synth.SelectVoice($Voice)
}
else {
    Write-Warning "voice '$Voice' is not installed; using the default voice"
}

Get-ChildItem -Path $FixtureDir -Filter *.txt | Sort-Object Name | ForEach-Object {
    $out = Join-Path $AudioDir ($_.BaseName + ".wav")
    $synth.SetOutputToWaveFile($out, $format)
    $synth.Speak((Get-Content -Raw $_.FullName))
    $synth.SetOutputToNull()
    Write-Host ("wrote {0}" -f $out)
}

$synth.Dispose()
