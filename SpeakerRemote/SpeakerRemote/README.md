# Speaker Remote (IR blaster app)

Android app that copies the 21-key "With Hard I.C." MP3/speaker remote using the phone's IR blaster
(NEC protocol, 38 kHz, address 0x00).

## Build the APK (no Android Studio needed)
1. Create a free GitHub account, make a new repository, upload everything in this folder
   (keep the `.github` folder).
2. Open the **Actions** tab -> **Build APK** -> wait ~3 min.
3. Open the finished run -> download **SpeakerRemote-apk** -> unzip -> install `app-debug.apk` on your phone.

Or open the folder in Android Studio and press Run.

## Requirements
Phone must have an IR blaster (many Xiaomi / Redmi / Poco, some older Samsung, Huawei, Oppo models).
Check: the app shows "IR blaster ready" at the top.

## Codes (NEC, address 00)
| Key | Command |
|---|---|
| Power | `45` |
| Mode | `46` |
| Mute | `47` |
| Play/Pause | `44` |
| Prev | `40` |
| Next | `43` |
| EQ | `07` |
| VOL- | `15` |
| VOL+ | `09` |
| 0 | `16` |
| RPT | `19` |
| U/SD | `0D` |
| 1 | `0C` |
| 2 | `18` |
| 3 | `5E` |
| 4 | `08` |
| 5 | `1C` |
| 6 | `5A` |
| 7 | `42` |
| 8 | `52` |
| 9 | `4A` |

If a key does nothing, turn on **Edit codes**, tap the key and change its hex command.
`pronto_codes.txt` has the same codes in Pronto format for other IR apps.
