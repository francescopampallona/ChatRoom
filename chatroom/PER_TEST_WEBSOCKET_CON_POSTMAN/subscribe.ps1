$roomId = 4

$lines = @(
    "SUBSCRIBE"
    "id:sub-1"
    "destination:/topic/rooms/$roomId"
    ""
)

$frame = ($lines -join "`n") + "`n" + [char]0

$utf8 = [System.Text.UTF8Encoding]::new($false)
$bytes = $utf8.GetBytes($frame)

[Convert]::ToBase64String($bytes)