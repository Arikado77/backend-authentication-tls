param(
    [string]$KeyStorePassword
)

$ErrorActionPreference = "Stop"

function New-RandomBase64([int]$Length) {
    $bytes = New-Object byte[] $Length
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

$projectRoot = Split-Path $PSScriptRoot -Parent
$keyStorePath = Join-Path $projectRoot "certs\localhost.p12"
$envFile = Join-Path $projectRoot ".env"
$keytool = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) {
        $keytool = $candidate
    }
}
if (-not $keytool) {
    $keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty Source
}
if (-not $keytool) {
    $jdkCandidates = Get-ChildItem (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "bin\keytool.exe" } |
        Where-Object { Test-Path $_ }
    $keytool = $jdkCandidates | Select-Object -First 1
}
if (-not $keytool) {
    throw "No se encontró keytool.exe. Configura JAVA_HOME con el directorio del JDK 17."
}

if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([^#\s=]+)\s*=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim(), "Process")
        }
    }
}
if ($KeyStorePassword) {
    $env:TLS_KEYSTORE_PASSWORD = $KeyStorePassword
}
if ([string]::IsNullOrWhiteSpace($env:TLS_KEYSTORE_PASSWORD) -or
    $env:TLS_KEYSTORE_PASSWORD.StartsWith("REEMPLAZAR_")) {
    if (Test-Path $keyStorePath) {
        throw "Ya existe el keystore pero no se encontró su contraseña. Restaura TLS_KEYSTORE_PASSWORD en .env."
    }
    $env:TLS_KEYSTORE_PASSWORD = New-RandomBase64 24
}
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET_BASE64) -or
    $env:JWT_SECRET_BASE64.StartsWith("REEMPLAZAR_")) {
    $env:JWT_SECRET_BASE64 = New-RandomBase64 32
}
if ([string]::IsNullOrWhiteSpace($env:DEMO_USER_PASSWORD) -or
    $env:DEMO_USER_PASSWORD.StartsWith("REEMPLAZAR_")) {
    $env:DEMO_USER_PASSWORD = New-RandomBase64 24
}
if ([string]::IsNullOrWhiteSpace($env:DEMO_ADMIN_PASSWORD) -or
    $env:DEMO_ADMIN_PASSWORD.StartsWith("REEMPLAZAR_")) {
    $env:DEMO_ADMIN_PASSWORD = New-RandomBase64 24
}
if (-not $env:TLS_KEYSTORE) {
    $env:TLS_KEYSTORE = "file:./certs/localhost.p12"
}
if (-not $env:CORS_ALLOWED_ORIGIN) {
    $env:CORS_ALLOWED_ORIGIN = "http://localhost:4200"
}
if (-not $env:JWT_ISSUER) {
    $env:JWT_ISSUER = "backend-auth-tls"
}
if (-not $env:JWT_EXPIRATION_SECONDS) {
    $env:JWT_EXPIRATION_SECONDS = "900"
}

New-Item -ItemType Directory -Path (Split-Path $keyStorePath) -Force | Out-Null
if (-not (Test-Path $keyStorePath)) {
    & $keytool -genkeypair -alias localhost -keyalg RSA -keysize 3072 -sigalg SHA256withRSA `
        -validity 365 -storetype PKCS12 -keystore $keyStorePath `
        -storepass $env:TLS_KEYSTORE_PASSWORD -keypass $env:TLS_KEYSTORE_PASSWORD `
        -dname "CN=localhost, OU=Development, O=Local, L=Local, ST=Local, C=MX" `
        -ext "SAN=dns:localhost,ip:127.0.0.1"
    if ($LASTEXITCODE -ne 0) {
        throw "keytool no pudo crear el certificado local."
    }
} else {
    & $keytool -list -keystore $keyStorePath -storetype PKCS12 `
        -storepass $env:TLS_KEYSTORE_PASSWORD | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "La contraseña TLS_KEYSTORE_PASSWORD no abre el keystore existente."
    }
}

@(
    "TLS_KEYSTORE_PASSWORD=$env:TLS_KEYSTORE_PASSWORD"
    "TLS_KEYSTORE=$env:TLS_KEYSTORE"
    "JWT_SECRET_BASE64=$env:JWT_SECRET_BASE64"
    "JWT_ISSUER=$env:JWT_ISSUER"
    "JWT_EXPIRATION_SECONDS=$env:JWT_EXPIRATION_SECONDS"
    "DEMO_USER_PASSWORD=$env:DEMO_USER_PASSWORD"
    "DEMO_ADMIN_PASSWORD=$env:DEMO_ADMIN_PASSWORD"
    "CORS_ALLOWED_ORIGIN=$env:CORS_ALLOWED_ORIGIN"
) | Set-Content -Path $envFile -Encoding ascii

Write-Host "Certificado PKCS12 listo en $keyStorePath"
Write-Host "Secretos locales configurados en variables de esta sesión y en .env (ignorado por Git)."
Write-Host "Consulta DEMO_USER_PASSWORD y DEMO_ADMIN_PASSWORD en esta sesión para iniciar sesión."
Write-Host "No compartas ni guardes los secretos en archivos versionados."
