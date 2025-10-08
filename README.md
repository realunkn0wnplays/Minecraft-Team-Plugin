# TeamsPlugin (1.21.9) 


This repository contains a stable, production-ready Minecraft plugin for Spigot/Paper (1.21.9) that implements teams with encrypted YAML persistence.


**Notable features:**
- `/team create [name]`
- `/team invite [playername]`
- `/team join [teamname]` (requires invite)
- `/team kick [playername]` (owner only)
- `/team transfer [playername]` (transfer ownership to another member)
- `/team leave` (leave your current team)
- `/team disband [teamname]` (owner only)
- `/team top` (top 10 teams by score)
- `/team chat [message]` or `/team chat` (toggle team chat)
- Encrypted `teams.yml` using AES-GCM with a passphrase-derived key (PBKDF2-HMAC-SHA256)
- Autosave and safe async disk IO
- Basic input validation and error handling