# Sound asset attribution

## Gun sounds

SkyStrike's gun effects under `assets/sfx/guns/` are derived from **The Free Firearm Sound Library** by Ben Jaszczak, Brian Nelson, Kevin Heras, and Matthew Nanney.

- OpenGameArt source page: <https://opengameart.org/content/the-free-firearm-sound-library>
- License stated on that page: **CC0 1.0 Universal**, Creative Commons Zero, no rights reserved
- License text: <https://creativecommons.org/publicdomain/zero/1.0/>
- Source-library mirror used to obtain the individual files: <https://github.com/buddingmonkey/FreeFirearmsSFXLibrary>

The OpenGameArt page states that the library may be used for personal or professional applications without royalty or credit. This notice retains the creators and provenance voluntarily. OpenGameArt also notes that it is not affiliated with the library.

### Imported source recordings

The ten class-specific fire variants were selected from the mirror's prepared recordings:

- `Prepared SFX/Walther PPQ/X_39P.wav` -> pistol
- `Prepared SFX/Smith & Wesson 642/V_27P.wav` -> revolver
- `Prepared SFX/PPSh/P_30P.wav` -> SMG
- `Prepared SFX/Carl Gustav M45/G_31P.wav` -> PDW
- `Prepared SFX/AR-15/D_32P.wav` -> assault rifle
- `Prepared SFX/SKS/U_14P.wav` -> DMR
- `Prepared SFX/Marlin 336/I_22P.wav` -> battle rifle
- `Prepared SFX/Mosin Nagant/M_21P.wav` -> sniper
- `Prepared SFX/Model 12/K_22P.wav` -> shotgun
- `Prepared SFX/AK-47/C_28P.wav` -> LMG

Handling recordings use the source library's 1911 and Springfield 1917 mechanics:

- `Master Tracks/1911/A_0.wav` -> reload
- `Master Tracks/1911/A_19.wav` -> empty or dry fire
- `Master Tracks/1911/A_24.wav` -> equip
- `Master Tracks/1911/A_30.wav` -> ADS out
- `Master Tracks/1911/A_31.wav` -> ADS in
- `Master Tracks/1917/B_10.wav` -> bolt, pump, and break-action cycle

The source recordings were reduced to one usable take where the source file contained multiple takes, then downmixed and resampled to the existing project format: 22,050 Hz, mono, 16-bit WAV. The source archive, mirror checkout, spreadsheets, and unused recordings are not committed.

`assets/sfx/guns-sfx.json` records the packaged paths and the mapping from the existing `WeaponId` and `WeaponClass` registries. The game loads each path lazily through the client audio owner and caches it once.

The existing project had no shot-impact recording or impact playback hook. `PacketDamageEvent` is consumed by the HUD, while terrain impacts are retired inside the server bullet system. No unrelated or placeholder sound was imported for those paths.

## World and status sounds

`assets/sfx/world/*.wav` and `assets/sfx/status/tinnitus-ring.wav` are **procedurally synthesised in-repo** by `tools/scratch/synth_sfx.py`. They contain no third-party recording, and the generator and its output are dedicated to the public domain under **CC0 1.0 Universal** alongside the rest of the project.

- Generator: `tools/scratch/synth_sfx.py` — deterministic (every file's noise comes from a seed derived from its own name, so re-running it reproduces the committed files); `--check` re-verifies format, duration, peak and the loop seam.
- Manifest: `assets/sfx/world-sfx.json` — paths, durations, format and provenance for every generated file.
- Cross-check: `python3 tools/scratch/static_audio_check.py` verifies that every path and duration in `EffectSoundTable`/`SoundCatalog` matches the files on disk, and that neither manifest lists a file that is not shipped (or omits one that is).

The catalogue references these files by name, so any single file may be replaced by a recording without touching code — keep the format (22,050 Hz, mono, 16-bit WAV) and the declared duration (within 0.05 s), or update both together. `MUZZLE_FLASH` deliberately has no asset: the weapon-specific report already comes from the snapshot-driven gun catalogue, and playing both would double every shot.
