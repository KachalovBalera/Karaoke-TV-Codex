$ErrorActionPreference = 'Stop'
$signingDir = Join-Path $env:LOCALAPPDATA 'Karaoke-TV-Codex-Signing'
$keyFile = Join-Path $signingDir 'release.p12'
$passwordFile = Join-Path $signingDir 'password.dpapi'
if (!(Test-Path $signingDir)) {
    New-Item -ItemType Directory -Path $signingDir | Out-Null
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls $signingDir /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict signing directory' }
}
if (!(Test-Path $passwordFile)) {
    if (Test-Path $keyFile) { throw 'Existing key without password: recover credentials, do not replace key' }
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    $secure | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordFile
}
$secure = Get-Content -LiteralPath $passwordFile | ConvertTo-SecureString
$credential = New-Object System.Management.Automation.PSCredential('signing', $secure)
try {
    $env:KARAOKE_KEYSTORE = $keyFile
    $env:KARAOKE_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:KARAOKE_KEY_PASSWORD = $env:KARAOKE_STORE_PASSWORD
    $env:KARAOKE_KEY_ALIAS = 'karaoke-tv'
    if (!(Test-Path $keyFile)) {
        & keytool -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:env KARAOKE_STORE_PASSWORD -keypass:env KARAOKE_KEY_PASSWORD -alias karaoke-tv -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Karaoke TV Codex' -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
    }
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & .\gradlew.bat assembleRelease --no-daemon --max-workers=2 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:KARAOKE_STORE_PASSWORD, Env:KARAOKE_KEY_PASSWORD, Env:KARAOKE_KEYSTORE, Env:KARAOKE_KEY_ALIAS -ErrorAction SilentlyContinue
}
