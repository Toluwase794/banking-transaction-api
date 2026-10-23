param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'

function Post-Json([string]$Path, $Payload) {
    Invoke-RestMethod "$BaseUrl$Path" -Method Post -ContentType 'application/json' -Body ($Payload | ConvertTo-Json)
}

$sender = Post-Json '/api/accounts' @{ ownerName = 'Ada Lovelace' }
$recipient = Post-Json '/api/accounts' @{ ownerName = 'Grace Hopper' }
$deposit = Post-Json "/api/accounts/$($sender.id)/deposits" @{ amount = 500.00 }
$transfer = @{
    fromAccountId = $sender.id
    toAccountId = $recipient.id
    amount = 150.00
    idempotencyKey = "demo-$([guid]::NewGuid())"
}
$first = Post-Json '/api/transfers' $transfer
$replay = Post-Json '/api/transfers' $transfer
$sender = Invoke-RestMethod "$BaseUrl/api/accounts/$($sender.id)"
$recipient = Invoke-RestMethod "$BaseUrl/api/accounts/$($recipient.id)"
if ($first.id -ne $replay.id -or $sender.balance -ne 350 -or $recipient.balance -ne 150) {
    throw 'Demo failed: balances or replayed transaction do not match.'
}
Write-Output 'Verified: transfer replay returned the same transaction; balances are 350 and 150.'
@($sender, $recipient) | Format-Table id, ownerName, balance
Invoke-RestMethod "$BaseUrl/api/accounts/$($sender.id)/transactions" | ConvertTo-Json -Depth 5
