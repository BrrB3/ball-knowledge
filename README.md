# Ball Knowledge

A personal Android companion for logging thoughts while watching football.

## Getting the app

Every push to `main` builds the app on GitHub. The newest APK is on the
[Releases page](../../releases/latest) as **BallKnowledge.apk** — open it on the phone to install or update.
Updates install over the old version and keep all your data (every build is signed with the same key).

## How it's put together

- `app/src/main/java/com/ballknowledge/`
  - `model/` — data types (fixtures, match pages, fields, competitions, default teams)
  - `logic/` — time formatting (IST / UK, venue-timezone star), match clock, auto-fill rules, fixture merging
  - `data/` — storage (three JSON files), the ESPN data source and parser, the repository that ties it together
  - `export/` — HTML export, sharing, Save-as-PDF
  - `ui/` — the screens (Fixtures, Match page, Archive, Settings)
- Match data comes from ESPN's public but undocumented JSON endpoints (`data/EspnSource.kt`, `data/EspnParser.kt`).
  If those ever break, only those two files need changing; everything else works by hand.
- No accounts, no analytics, no background work. All data stays on the phone; use Settings → Back up.
