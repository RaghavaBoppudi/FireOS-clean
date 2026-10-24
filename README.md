# Debloat a Fire TV Stick with Claude Code (no remote needed)

Clean up an Amazon Fire TV Stick over Wi-Fi with [Claude Code](https://claude.com/claude-code), even when the
physical remote is lost: a remote in your browser, backup-first debloat scripts with a one-line undo, and a
gradient-background, app-only home screen in place of Amazon's ad-filled one. Built and tested on a Fire TV Stick 2nd gen
(AFTT, Fire OS 5.2.9.5) with no remote, and on a Fire TV Stick Lite (AFTSS, Fire OS 7) for the home screen.

> Network ADB is full control of your stick. Use this only on a stick you own, on a network you trust.

## What's in here

| Path | What it does |
|---|---|
| [`tools/remote.py`](tools/remote.py) | **A usable remote in your browser**: D-pad, OK, Back, Home, Menu, media and volume keys, arrow-key shortcuts, typing into the TV and a live screen preview. Works over ADB, or with `--no-adb` through the Fire TV app's API. |
| [`tools/firetv.py`](tools/firetv.py) | Pairs with the stick through the same local API Amazon's Fire TV phone app uses (a PIN shows on the TV) and presses keys, with no ADB and no remote. This is how you reach Developer options on a stick without a remote. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (shell, batch, push, pull); works where macOS blocks the `adb` binary. |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Record each package's state, then switch the list off; restore replays the record. `HIDE=1` for Fire OS 5. |
| [`firehome/`](firehome/) | **A replacement home screen** (see below). |
| [`examples/`](examples/) | Debloat lists for five Fire TV devices. |

## The home screen (`firehome/`)

A launcher-style app that takes over whenever Amazon's home starts. Fire OS will not let another app be the default
home, so it watches the system log for Amazon's home starting (`READ_LOGS`, granted over ADB), covers it with a
"Launching Home" screen, and brings itself up. The cover stays up until the new home really has the screen, so
Amazon's home is never seen; on a slow stick Android can take around five seconds to hand over.

- One line, `Saturday 24 October | 16:40` (24-hour), over a wallpaper, and a frosted dock of white app marks, the app
  used last first. Marks are cut out of each app's own artwork, so apps you install later get one too. The selected
  app sits on a brighter rectangle. Nothing is coloured: the only colour is the wallpaper.
- Buttons top right: **All apps** (every app, A to Z, including the Appstore) and **Settings** (Network, Controllers
  & Bluetooth, Applications, My Fire TV, Background, Refresh wallpapers). Display & Sounds is left out: Amazon locks
  it to its own launcher.
- **Background** (in Settings) shows how often the wallpaper changes inside its icon: press OK to step through
  30 seconds to daily (30 minutes by default). **Refresh wallpapers** checks GitHub straight away and says what it found.
- Starts early at boot through a notification-listener service, so Amazon's home is covered within about a second.
  Menu on the remote opens Amazon's home for 10 minutes.

### Build and install

You need a JDK 17, the Android SDK (build-tools and a platform) and the stick on your network with ADB debugging on
(see Quick start). On macOS:

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
export JAVA_HOME="$(brew --prefix openjdk@17)" PATH="$(brew --prefix openjdk@17)/bin:$PATH"
yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "build-tools;34.0.0" "platforms;android-28"
```

On Linux install a JDK 17 and the [command-line tools](https://developer.android.com/studio#command-line-tools-only),
then run the same `sdkmanager` line with `--sdk_root="$HOME/Android/Sdk"`. `firehome/build.sh` finds the SDK in
`$ANDROID_HOME`, `~/Library/Android/sdk` or `~/Android/Sdk`.

```bash
export TV_HOST=192.168.1.50                  # your stick's IP address
firehome/install.sh                          # build, install, grant permissions, open it
firehome/install.sh --remove                 # undo: Amazon's home is back on the next Home press
```

`install.sh` grants `READ_LOGS`, draw-over-apps and usage-access over ADB and allows the boot listener. Tested on
Fire OS 7 (Android 9); Fire OS 5 needs its own check.

### Your own wallpapers

Out of the box the home uses 12 gradients built into the app. To show your own images, the app downloads them from
a **public GitHub repository** of yours. Set it up once:

1. **Create the repository.** On GitHub choose **New repository**, give it a name such as `tv-wallpapers`, set it
   to **Public** and create it. (It can be empty, and it must be public: the TV has no GitHub login.)
2. **Add images.** Open the repository, choose **Add file > Upload files**, drag your images in and **Commit changes**.
   Use JPG, PNG or WebP at the **top level** (not in a folder). **16:9** images are ideal (1920 × 1080 is enough, 3840 × 2160
   is plenty). Any other shape or size is fine too: the TV cuts the middle 16:9 out of it and scales it to 1920 × 1080
   itself, one image at a time, in the background, so even a 100-megapixel photo is safe. GitHub's own limit of 100 MB
   per file applies, and smaller files simply download faster.
   From a terminal: `git add . && git commit -m "Add wallpapers" && git push`.
   The folder [`wallpaper-repo-template/`](wallpaper-repo-template/) has a ready-made README you can copy into the repository.
3. **Name the files to order and credit them.** Name each image `NUMBER-First-Last.jpg`, for example
   `1-Raghava-Boppudi.jpg`, `2-Allen-Daniel.jpg`. The number is the order the wallpapers rotate in (1, 2, 3 ... 10, 11
   are in numeric order). The words after it are the photographer: the screen shows `© Raghava Boppudi` bottom right.
   A file with no number in front is shown after the numbered ones, and with no credit. Rename a file on GitHub with
   the pencil icon on its page, or with `git mv`.
4. **Point the app at it.** Edit [`firehome/res/values/config.xml`](firehome/res/values/config.xml) and set
   `wallpaper_repo` to `YOUR-GITHUB-USERNAME/YOUR-REPO-NAME`. If the images are in a folder, put its name in
   `wallpaper_path`. Then run `firehome/install.sh` again.
5. **Check it.** On the TV open **Settings > Refresh wallpapers**. It says "N wallpapers from GitHub" when it found
   your images. After that new images are picked up automatically, every 30 minutes, whenever Home opens.

How it behaves:

| Situation | What the TV shows |
|---|---|
| The repository has at least one valid image | Only your images, in number order |
| The repository is empty or has no valid images | The built-in gradients |
| No repository is set in `config.xml` | The built-in gradients, and no network use |
| GitHub is unreachable | The last images it downloaded; the gradients if there are none |

If Refresh says "no images yet": check the repository is **public**, the images are on the default branch at the top
level, and that opening `https://api.github.com/repos/YOUR-GITHUB-USERNAME/YOUR-REPO-NAME/contents/` in a browser lists them.
Anyone who can push to the repository controls your wallpapers, so keep its write access to yourself, and only
add images you have the right to publish. The built-in gradients are generated by
[`firehome/wallpapers/generate.py`](firehome/wallpapers/generate.py) (needs Pillow) and are CC0. Icons are
[Lucide](https://lucide.dev) (ISC); see [`firehome/NOTICE.md`](firehome/NOTICE.md).

## Quick start

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
backup/restore scripts and the Fire OS 6 and 7 lists come from there. MIT licence, see [`LICENSE`](LICENSE).
