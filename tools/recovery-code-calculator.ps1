param(
    [string]$DeviceId = "",
    [string]$Date = "",
    [switch]$NoGui
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# Must match RecoveryCodeManager.MASTER_SECRET in the Android app.
$script:MasterSecret = "45250811B5D0C9934D02ADDC38EA65B745D05A73F85521C6C22E7B6BFE89881E"
$script:PayloadVersion = "KPG_RESET_V1"

function Normalize-RecoveryId {
    param([Parameter(Mandatory = $true)][string]$Value)

    $normalized = ($Value -replace "[^A-Za-z0-9]", "").ToUpperInvariant()
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        throw "Recovery device ID is required."
    }
    return $normalized
}

function Normalize-RecoveryDate {
    param([Parameter(Mandatory = $true)][string]$Value)

    $parsed = [datetime]::MinValue
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $style = [System.Globalization.DateTimeStyles]::None
    if (-not [datetime]::TryParseExact(
        $Value,
        "yyyy-MM-dd",
        $culture,
        $style,
        [ref]$parsed
    )) {
        throw "Date must use yyyy-MM-dd, for example 2026-07-26."
    }
    return $parsed.ToString("yyyy-MM-dd", $culture)
}

function Get-RecoveryCode {
    param(
        [Parameter(Mandatory = $true)][string]$RecoveryId,
        [Parameter(Mandatory = $true)][string]$RecoveryDate
    )

    $normalizedId = Normalize-RecoveryId -Value $RecoveryId
    $normalizedDate = Normalize-RecoveryDate -Value $RecoveryDate
    $payload = "$($script:PayloadVersion)|$normalizedId|$normalizedDate"
    $secretBytes = [System.Text.Encoding]::UTF8.GetBytes($script:MasterSecret)
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($secretBytes)
    try {
        $hash = $hmac.ComputeHash($payloadBytes)
    }
    finally {
        $hmac.Dispose()
    }

    # RFC 4226/HOTP-style dynamic truncation; must match RecoveryCodeEngine.
    $offset = [int]($hash[$hash.Length - 1] -band 0x0F)
    [long]$binaryCode =
        ([long]($hash[$offset] -band 0x7F) * 16777216) +
        ([long]$hash[$offset + 1] * 65536) +
        ([long]$hash[$offset + 2] * 256) +
        [long]$hash[$offset + 3]
    return "{0:D8}" -f ($binaryCode % 100000000)
}

function Assert-GoldenVector {
    $actual = Get-RecoveryCode `
        -RecoveryId "7D4A-92FC-381B-6E20" `
        -RecoveryDate "2026-07-26"
    if ($actual -ne "19381938") {
        throw "Calculator self-check failed. Expected 19381938 but got $actual."
    }
}

Assert-GoldenVector

if ($NoGui -or -not [string]::IsNullOrWhiteSpace($DeviceId)) {
    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        throw "-DeviceId is required in command-line mode."
    }
    if ([string]::IsNullOrWhiteSpace($Date)) {
        $Date = (Get-Date).ToString(
            "yyyy-MM-dd",
            [System.Globalization.CultureInfo]::InvariantCulture
        )
    }
    Get-RecoveryCode -RecoveryId $DeviceId -RecoveryDate $Date
    exit 0
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$form = New-Object System.Windows.Forms.Form
$form.Text = "KidsPhoneGuard Recovery Code Calculator"
$form.StartPosition = "CenterScreen"
$form.ClientSize = New-Object System.Drawing.Size(560, 390)
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false

$instruction = New-Object System.Windows.Forms.Label
$instruction.Location = New-Object System.Drawing.Point(24, 20)
$instruction.Size = New-Object System.Drawing.Size(510, 42)
$instruction.Text = "Enter the recovery device ID and the exact date shown on the phone."
$instruction.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$form.Controls.Add($instruction)

$deviceLabel = New-Object System.Windows.Forms.Label
$deviceLabel.Location = New-Object System.Drawing.Point(24, 78)
$deviceLabel.Size = New-Object System.Drawing.Size(180, 24)
$deviceLabel.Text = "Recovery device ID"
$form.Controls.Add($deviceLabel)

$deviceTextBox = New-Object System.Windows.Forms.TextBox
$deviceTextBox.Location = New-Object System.Drawing.Point(24, 104)
$deviceTextBox.Size = New-Object System.Drawing.Size(510, 32)
$deviceTextBox.Font = New-Object System.Drawing.Font("Consolas", 14)
$form.Controls.Add($deviceTextBox)

$dateLabel = New-Object System.Windows.Forms.Label
$dateLabel.Location = New-Object System.Drawing.Point(24, 154)
$dateLabel.Size = New-Object System.Drawing.Size(180, 24)
$dateLabel.Text = "Date shown on phone"
$form.Controls.Add($dateLabel)

$datePicker = New-Object System.Windows.Forms.DateTimePicker
$datePicker.Location = New-Object System.Drawing.Point(24, 180)
$datePicker.Size = New-Object System.Drawing.Size(220, 30)
$datePicker.Format = [System.Windows.Forms.DateTimePickerFormat]::Custom
$datePicker.CustomFormat = "yyyy-MM-dd"
$form.Controls.Add($datePicker)

$calculateButton = New-Object System.Windows.Forms.Button
$calculateButton.Location = New-Object System.Drawing.Point(350, 174)
$calculateButton.Size = New-Object System.Drawing.Size(184, 42)
$calculateButton.Text = "Calculate"
$form.Controls.Add($calculateButton)

$resultLabel = New-Object System.Windows.Forms.Label
$resultLabel.Location = New-Object System.Drawing.Point(24, 238)
$resultLabel.Size = New-Object System.Drawing.Size(180, 24)
$resultLabel.Text = "8-digit recovery code"
$form.Controls.Add($resultLabel)

$resultTextBox = New-Object System.Windows.Forms.TextBox
$resultTextBox.Location = New-Object System.Drawing.Point(24, 264)
$resultTextBox.Size = New-Object System.Drawing.Size(330, 50)
$resultTextBox.Font = New-Object System.Drawing.Font(
    "Consolas",
    24,
    [System.Drawing.FontStyle]::Bold
)
$resultTextBox.ReadOnly = $true
$resultTextBox.TextAlign = [System.Windows.Forms.HorizontalAlignment]::Center
$form.Controls.Add($resultTextBox)

$copyButton = New-Object System.Windows.Forms.Button
$copyButton.Location = New-Object System.Drawing.Point(370, 264)
$copyButton.Size = New-Object System.Drawing.Size(164, 50)
$copyButton.Text = "Copy code"
$copyButton.Enabled = $false
$form.Controls.Add($copyButton)

$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Location = New-Object System.Drawing.Point(24, 330)
$statusLabel.Size = New-Object System.Drawing.Size(510, 36)
$statusLabel.ForeColor = [System.Drawing.Color]::Firebrick
$form.Controls.Add($statusLabel)

$calculateAction = {
    try {
        $resultTextBox.Text = Get-RecoveryCode `
            -RecoveryId $deviceTextBox.Text `
            -RecoveryDate $datePicker.Value.ToString(
                "yyyy-MM-dd",
                [System.Globalization.CultureInfo]::InvariantCulture
            )
        $statusLabel.Text = ""
        $copyButton.Enabled = $true
    }
    catch {
        $resultTextBox.Text = ""
        $copyButton.Enabled = $false
        $statusLabel.Text = $_.Exception.Message
    }
}

$calculateButton.Add_Click($calculateAction)
$deviceTextBox.Add_KeyDown({
    if ($_.KeyCode -eq [System.Windows.Forms.Keys]::Enter) {
        & $calculateAction
        $_.SuppressKeyPress = $true
    }
})
$copyButton.Add_Click({
    if (-not [string]::IsNullOrWhiteSpace($resultTextBox.Text)) {
        [System.Windows.Forms.Clipboard]::SetText($resultTextBox.Text)
        $statusLabel.ForeColor = [System.Drawing.Color]::DarkGreen
        $statusLabel.Text = "Recovery code copied."
    }
})

$form.Add_Shown({ $deviceTextBox.Focus() })
[void]$form.ShowDialog()
