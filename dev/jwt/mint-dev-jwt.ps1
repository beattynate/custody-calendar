param(
    [string]$Subject = "dev|parent-a",
    [string]$Name = "Parent A",
    [int]$ExpiresInHours = 12
)

$root = Resolve-Path (Join-Path $PSScriptRoot "..\\..")
$privatePath = Join-Path $PSScriptRoot "private-key.json"
$jwksPath = Join-Path $PSScriptRoot "jwks.json"
$resourceJwksPath = Join-Path $root "services\\api\\src\\main\\resources\\dev\\jwks.json"

if (-not (Test-Path $privatePath)) {
    throw "Private key not found at $privatePath"
}

$privateObj = Get-Content $privatePath | ConvertFrom-Json

function Base64UrlDecode([string]$input) {
    $s = $input.Replace('-', '+').Replace('_', '/')
    switch ($s.Length % 4) {
        2 { $s += "==" }
        3 { $s += "=" }
    }
    return [Convert]::FromBase64String($s)
}

function Base64UrlEncode([byte[]]$bytes) {
    $s = [Convert]::ToBase64String($bytes)
    $s = $s.TrimEnd('=').Replace('+', '-').Replace('/', '_')
    return $s
}

$rsa = [System.Security.Cryptography.RSA]::Create()
try {
    $params = New-Object System.Security.Cryptography.RSAParameters
    $params.Modulus = Base64UrlDecode $privateObj.n
    $params.Exponent = Base64UrlDecode $privateObj.e
    $params.D = Base64UrlDecode $privateObj.d
    $params.P = Base64UrlDecode $privateObj.p
    $params.Q = Base64UrlDecode $privateObj.q
    $params.DP = Base64UrlDecode $privateObj.dp
    $params.DQ = Base64UrlDecode $privateObj.dq
    $params.InverseQ = Base64UrlDecode $privateObj.qi
    $rsa.ImportParameters($params)
} catch {
    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    $params = $rsa.ExportParameters($true)
    $n = Base64UrlEncode $params.Modulus
    $e = Base64UrlEncode $params.Exponent
    $kid = [Guid]::NewGuid().ToString()

    $jwksObj = @{ keys = @(@{ kty = "RSA"; use = "sig"; alg = "RS256"; kid = $kid; n = $n; e = $e }) }
    $privateObj = @{
        kty = "RSA"
        kid = $kid
        n = $n
        e = $e
        d = (Base64UrlEncode $params.D)
        p = (Base64UrlEncode $params.P)
        q = (Base64UrlEncode $params.Q)
        dp = (Base64UrlEncode $params.DP)
        dq = (Base64UrlEncode $params.DQ)
        qi = (Base64UrlEncode $params.InverseQ)
    }

    $jwksObj | ConvertTo-Json -Depth 5 | Set-Content -Path $jwksPath -Encoding utf8
    $jwksObj | ConvertTo-Json -Depth 5 | Set-Content -Path $resourceJwksPath -Encoding utf8
    $privateObj | ConvertTo-Json -Depth 5 | Set-Content -Path $privatePath -Encoding utf8
}

$header = @{
    alg = "RS256"
    typ = "JWT"
    kid = $privateObj.kid
} | ConvertTo-Json -Compress

$now = [DateTimeOffset]::UtcNow
$payload = @{
    sub = $Subject
    name = $Name
    iat = [int]$now.ToUnixTimeSeconds()
    exp = [int]$now.AddHours($ExpiresInHours).ToUnixTimeSeconds()
    iss = "custody-calendar-dev"
    aud = "custody-calendar-api"
} | ConvertTo-Json -Compress

$headerB64 = Base64UrlEncode([Text.Encoding]::UTF8.GetBytes($header))
$payloadB64 = Base64UrlEncode([Text.Encoding]::UTF8.GetBytes($payload))
$data = [Text.Encoding]::UTF8.GetBytes("$headerB64.$payloadB64")
$signature = $rsa.SignData($data, [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
$sigB64 = Base64UrlEncode $signature

Write-Output "$headerB64.$payloadB64.$sigB64"
