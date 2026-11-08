$token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjaWNjaW8iLCJ1c2VySWQiOjEsInVzZXJuYW1lIjoiY2ljY2lvIiwiaWF0IjoxNzgzOTU1NDE0LCJleHAiOjE3ODQwNDE4MTR9.wQk5vXe8U5MwKmGoM1lzLrZPdZT8lReReEqbSc9jZDw"

$lines = @(
    "CONNECT"
    "accept-version:1.2"
    "host:localhost"
    "Authorization:Bearer $token"
    ""
)

$frame = ($lines -join "`n") + "`n" + [char]0

$utf8 = [System.Text.UTF8Encoding]::new($false)
$bytes = $utf8.GetBytes($frame)

[Convert]::ToBase64String($bytes)