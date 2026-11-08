$roomId = 4
$message = "Ciao a tutti"

$lines = @(
    "SEND"
    "destination:/app/rooms/$roomId/messages"
    "content-type:application/json"
    ""
)

$body = @{
    content = $message
} | ConvertTo-Json -Compress

$frame = ($lines -join "`n") + "`n" + $body + [char]0

$utf8 = [System.Text.UTF8Encoding]::new($false)
$bytes = $utf8.GetBytes($frame)

[Convert]::ToBase64String($bytes)