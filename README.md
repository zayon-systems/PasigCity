# Pasig Emergency Ready (PER Pasig)

Every Pasig emergency hotline in one app, ready to dial.

**Prepared. Educated. Ready.**

## Features
- Hotlines by emergency type (tap, then press Call): Fire, Police, Ambulance/Rescue, DRRMO, Command Center, Barangay, 911
- Backup call order: if a line is busy, the next number is offered instantly
- Text for Help: SMS to Pasig Ka-TXT with GPS location (no internet needed)
- Official numbers update from `contacts.json` without rebuilding the app
- Emergency profile, ICE card, geotag camera, family plan, hospitals, alerts, go bag
- Works offline after first open

## Updating hotline numbers
Edit `contacts.json`, change `lastVerified`, commit. Installed apps pick it up next time they open online.

## City seal
Add the official seal as `pasig-seal.png` (square, transparent PNG) in the repo root — it appears on the splash screen automatically.

## Developed by
Zayon Systems — For the people of Pasig City

## Android app (APK) — built from this same repo
- **Actions → Build PER Pasig APK → Run workflow** builds a signed APK (5–8 min) and publishes it under **Releases**.
- Needs 4 secrets in this repo (Settings → Secrets and variables → Actions): `PER_KEYSTORE_BASE64`, `PER_KEYSTORE_PASSWORD`, `PER_KEY_ALIAS`, `PER_KEY_PASSWORD` — same values as in `keystore-info.txt`.
- Editing only `contacts.json` doesn't rebuild the APK: installed apps download the new numbers on their own.
- Never upload the `.jks` key or `keystore-info.txt` here.
