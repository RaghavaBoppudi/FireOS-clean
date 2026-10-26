# FireOS-clean

**A minimal, flat home screen for Fire TV Stick.**

Amazon's home is full of banners, sponsored rows and autoplay. This replaces it with a calm, flat screen: one line of
date and time, and a row of glass discs for the apps you choose, in the middle of the screen. Light by default, with a
dark look in Settings.

![Home](docs/screenshots/home.jpg)

| Dark | Settings |
|---|---|
| ![Dark](docs/screenshots/home-dark.jpg) | ![Settings](docs/screenshots/settings.jpg) |

| Editing the dock | Screensaver |
|---|---|
| ![Editing the dock](docs/screenshots/edit.jpg) | ![Screensaver](docs/screenshots/screensaver.jpg) |

(The clock reads 09:41 in every picture: it is fixed for the screenshots.)

Looking for the earlier version with rotating wallpapers? It is tagged [`wallpaper-home`](https://github.com/RaghavaBoppudi/FireOS-clean/tree/wallpaper-home).

## What you get

- **A flat, quiet screen.** An off-white ground (or a near-black one), up to six apps as discs of frosted glass, the name of the selected one fading in under it. Every other screen uses the same discs.
- **Apps you choose.** Up to six on the dock, picked with the pencil at the bottom (see [Make the dock yours](#make-the-dock-yours)). Well-known apps show their own mark; any other has its mark cut from its icon (an app that is only a name shows its first letter), so apps you install later fit in automatically. Every other app, the Appstore included, is under the all apps button.
- **Light and dark.** **Settings > Theme** switches between them. The dark look is tonal glass: graphite discs a shade lighter than the screen, with a rim of light and a soft glow.
- **A screensaver that is only the time.** After a few idle minutes the screen is bare, with the date and time drifting across it so no pixel is held in place. Any button wakes it.
- **Settings in the same style.** Network, Controllers, Apps, My Fire TV, the theme, the dock order and the screensaver delay. Each shows its name, and the ones you change show their current value.
- **Covers Amazon's home completely.** A "Launching Home" screen is held until the new home is really on screen, so Amazon's home is never seen. The handover takes about a second. It also starts early at boot and survives the app being updated.
- **No account, no cloud, no tracking.** The app has no network permission at all.
- **Reversible.** One command puts Amazon's home back.

## How it works

Fire OS won't let another app be the default home. So the app watches the system log for Amazon's home starting (a permission
you grant once over ADB), covers it, and brings its own home up. Android blocks an app from opening a screen for five seconds after the Home button, so the app has the alarm service open it instead, which is exempt. Because it relies on ADB, it works on Fire TV devices that
let you switch on **ADB debugging** (Settings > My Fire TV > Developer options). Tested on a Fire TV Stick Lite (Fire OS 7,
Android 9); other Fire OS 7 devices should work, and Fire OS 5 is untested for the home screen. It is not affiliated with Amazon.

## Size

The app is about 0.07 MB, with no images in it: the screen is drawn, and the only pictures are the vector logos and
glyphs. It has no network permission, and its whole source is about 2,400 lines of Java.

## Requirements

- A Fire TV with ADB debugging on, and a computer on the same network (macOS or Linux; a JDK 17 and the Android SDK to build).

## Build and install

1. **On the Fire TV:** Settings > My Fire TV > Developer options > **ADB debugging** on. No Developer options? Go to My Fire TV >
   About, press OK seven times on the device name, then go back. Note the IP address under About > Network.
2. **On your computer:** clone this repository and set up the Python tools:
   ```bash
   git clone https://github.com/RaghavaBoppudi/FireOS-clean.git && cd FireOS-clean
   python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
   ```
3. **Install a JDK 17 and the Android SDK** (build-tools and a platform). On macOS:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
export JAVA_HOME="$(brew --prefix openjdk@17)" PATH="$(brew --prefix openjdk@17)/bin:$PATH"
yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "build-tools;34.0.0" "platforms;android-28"
```

On Linux install a JDK 17 and the [command-line tools](https://developer.android.com/studio#command-line-tools-only),
then run the same `sdkmanager` line with `--sdk_root="$HOME/Android/Sdk"`. `firehome/build.sh` finds the SDK in
`$ANDROID_HOME`, `~/Library/Android/sdk` or `~/Android/Sdk`.

4. **Build and install.** The first time, accept "Allow USB debugging?" on the TV.

```bash
export TV_HOST=192.168.1.50                  # your stick's IP address
firehome/install.sh                          # build, install, grant permissions, open it
firehome/install.sh --remove                 # undo: Amazon's home is back on the next Home press
```

`install.sh` grants `READ_LOGS`, draw-over-apps and usage-access over ADB and allows the boot listener. Tested on
Fire OS 7 (Android 9); Fire OS 5 needs its own check.

## Make the dock yours

Press **Down** from the dock to the buttons at the bottom, move to the pencil, and press **OK**. The discs rock, and a
line under them says what each button does. The controls:

| You press | What happens |
|---|---|
| **OK** on an app | Picks it up. It grows again, so you can tell it from one you are only pointing at. |
| **← →** while holding | Moves it along the dock. |
| **OK** while holding | Sets it down. |
| **↑** while holding | Lifts it off the dock. **OK** then removes it; **↓** puts it back. |
| **OK** on **+** | Opens the apps that are not on the dock. **OK** on one adds it and returns to the dock. |
| **Back** | Steps out one level: cancels a move, closes the list of apps, then leaves edit mode. |

The dock holds one app to six. Asking for a seventh, or to remove the last one, shakes the dock and says why. Every change
is saved the moment you make it, so nothing is lost if the power goes. How the dock is ordered is a setting under
**Settings > Dock order**: last used first (the default) or A to Z. In last-used order a mark you moved stays where you
put it until you next open an app; in A to Z order it settles into its place at once.

To start a new install with the right apps already on the dock, name them (as they appear on screen, or by package name):

```bash
firehome/install.sh --dock "Netflix,YouTube,Prime Video"
```

Up to six keep the order you give. With more, the first six by name go on the dock and the rest wait under **+**. The
installer prints which names it found and which it did not. A dock you have already edited is left alone unless you add
`--reset-dock`. The first time the home opens it also shows a one-time line pointing at the pencil.

## Screensaver

After the home has sat idle it shows the bare screen with the date and time on it, moving to a new place every ten
seconds. Any button wakes it, and that press does nothing else. Choose the delay under **Settings > Screensaver**: Off,
1, 5 (the default), 10, 15 or 30 minutes. It never starts while you are editing the dock.

---

# Also in this repo: debloat tools for Fire TV

The repository began as tooling to clean up an Amazon Fire TV Stick over Wi-Fi even with **no remote**: a remote in your
browser, backup-first debloat scripts with a one-line undo, and debloat lists for five devices. These are optional and
independent of the home screen.

## What's in the toolbox

| Path | What it does |
|---|---|
| [`tools/remote.py`](tools/remote.py) | **A usable remote in your browser**: D-pad, OK, Back, Home, Menu, media and volume keys, arrow-key shortcuts, typing into the TV and a live screen preview. Works over ADB, or with `--no-adb` through the Fire TV app's API. |
| [`tools/firetv.py`](tools/firetv.py) | Pairs with the stick through the same local API Amazon's Fire TV phone app uses (a PIN shows on the TV) and presses keys, with no ADB and no remote. This is how you reach Developer options on a stick without a remote. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (shell, batch, push, pull, `--screenshot`, `--reboot`); works where macOS blocks the `adb` binary. Keeps its ADB key readable only by you. |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Record each package's state, then switch the list off; restore replays the record. `HIDE=1` for Fire OS 5. |
| [`examples/`](examples/) | Debloat lists for five Fire TV devices. |

## Quick start (debloat tools)

```bash
git clone https://github.com/RaghavaBoppudi/FireOS-clean.git && cd FireOS-clean
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                      # the stick: Settings > My Fire TV > About > Network
```

**No remote? Pair the browser remote first.** A 4-digit PIN appears on the TV:

```bash
python3 tools/firetv.py pair
python3 tools/firetv.py verify 1234                 # the PIN on screen
tools/venv/bin/python -I tools/remote.py --no-adb   # opens http://127.0.0.1:8765
```

Use it to turn on **Settings > My Fire TV > Developer options > ADB debugging**. No Developer options? Go to My
Fire TV > About, press OK seven times on the device name, then go back. Accept "Allow USB debugging?" on the TV.

```bash
tools/venv/bin/python -I tools/tv.py --probe                                    # model, Fire OS, Android
HIDE=1 tools/disable.sh examples/amazon-fire-tv-stick-2nd-gen-aftt/debloat.txt   # back up, then switch off
firehome/install.sh                                                             # the new home screen
```

Fire OS 5 allows one ADB connection at a time, so keep the remote on `--no-adb` while the scripts run. Fire OS
6 and 7 don't need `HIDE=1`.

## Or let Claude Code do it

Open Claude Code in this folder and tell it something like:

> Debloat my Fire TV Stick at 192.168.1.50 with this repo's tools. Back up first, ask me which apps I use,
> keep Wi-Fi, remote, Bluetooth and Appstore services, switch things off in small batches and check each one
> with a screenshot, and give me the undo command.

## What Fire OS 5.2.9.5 allows, and what it blocks

| Tried | Result on the 2nd-gen stick |
|---|---|
| `pm disable-user` | Refused for every package: `SecurityException: Permission Denial` |
| `pm hide` | Works for apps you installed; refused for Amazon's system apps |
| `pm uninstall -k --user 0` | Refused (`DELETE_FAILED_INTERNAL_ERROR`), and Android 5.1 couldn't undo it anyway |
| Accessibility service over ADB | Silently dropped, so `firehome` watches the system log (`READ_LOGS`) instead |
| Animation scales, `pm grant READ_LOGS` | Allowed |

So on this firmware the debloat hid seven unused apps and sped up animations, while Prime Video, Alexa,
Amazon's ads and telemetry stay installed. The home-screen swap is what makes the stick feel lighter: Amazon's
home, its full-screen ads and sponsored tiles stop coming up. Newer Fire OS lets ADB disable much more.

## Fire TV lists

| TV | OS |
|---|---|
| Amazon Fire TV Stick 2nd gen (AFTT, tank), India | Fire OS 5.2.9.5 / Android 5.1 |
| Amazon Fire TV Stick Lite (AFTSS, sheldon) | Fire OS 7 / Android 9 |
| Amazon Fire TV Stick 4K, 1st gen 2018 (AFTMM, mantis) | Fire OS 6.7.1.1 / Android 7.1 |
| Insignia 55" F30 4K Fire TV (NS-55F301NA25, AFTDCT31, duckie) | Fire OS 7 / Android 9 |
| Toshiba Fire TV (AFTHA004, hazel) | Fire OS 7 / Android 9 |

## Safety and undo

- Nothing is uninstalled. `disable.sh` writes each package's state to `~/.cleanyourtv/` before changing
  anything; `HIDE=1 tools/restore.sh <list> com.amazon.tv.launcher` puts it back. `firehome/install.sh --remove`
  brings Amazon's home back.
- **With no remote, Wi-Fi is the only way in.** The lists keep every Wi-Fi provisioning, remote-API, Bluetooth
  and pairing service. A factory reset is no fallback either: setup after a reset needs a physical remote.
- After every restart Fire OS shows "We cannot detect your remote"; press OK in the browser remote.
- Network ADB is full control of the stick. Switch ADB debugging off when you're done, once you have another way
  to control it (a replacement remote, or the Fire TV phone app).

## Credits

Built on [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin23 (MIT): the ADB client, the
backup/restore scripts and the Fire OS 6 and 7 lists come from there. The browser remote (`tools/remote.py`), the PIN
pairing tool (`tools/firetv.py`) and the Fire TV Stick 2nd gen list were added by [whyashthakker](https://github.com/whyashthakker),
who adapted CleanYourTV for sticks without a remote. The home screen in `firehome/` is by Raghava Boppudi. App marks are from [Simple Icons](https://simpleicons.org) (CC0), and the glyphs from [Material Symbols](https://github.com/google/material-design-icons) (Apache 2.0); see [`firehome/NOTICE.md`](firehome/NOTICE.md). MIT licence, see [`LICENSE`](LICENSE).
