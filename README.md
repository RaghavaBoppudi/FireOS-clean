# FireOS-clean

**A minimal home screen for Fire TV Stick, flat or over your own wallpapers.**

Amazon's home is full of banners, sponsored rows and autoplay. This replaces it with a calm screen: one line of date
and time, and a row of glass discs for the apps you choose, in the middle. Over a flat ground (light or dark), or over
wallpapers you keep in a GitHub repository, with the discs as liquid glass.

![Home with a wallpaper](docs/screenshots/home-wallpaper.jpg)

| Flat, light | Flat, dark |
|---|---|
| ![Home](docs/screenshots/home.jpg) | ![Dark](docs/screenshots/home-dark.jpg) |

| Settings, with a wallpaper | Settings, flat |
|---|---|
| ![Settings with a wallpaper](docs/screenshots/settings-wallpaper.jpg) | ![Settings](docs/screenshots/settings.jpg) |

| Editing the dock | Screensaver |
|---|---|
| ![Editing the dock](docs/screenshots/edit.jpg) | ![Screensaver](docs/screenshots/screensaver.jpg) |

(The clock reads 09:41 in every picture: it is fixed for the screenshots. The photograph is by Raghava Boppudi.)

## What you get

- **A quiet screen.** Up to six apps as discs of glass in the middle, the name of the selected one fading in under it, the date and time at the top left, a greeting (with your picture, if you add one) at the bottom left, and Apps, Edit and Settings at the bottom right. Every other screen uses the same discs, with its name where the clock is.
- **Your own wallpapers.** Switch **Settings > Wallpaper** on and the home shows full-screen photographs from a GitHub repository of yours (public or private), shown in order of their file names and changing as often as you choose under **Settings > Shuffle** (every 30 seconds up to hourly, or daily at midnight; every 30 minutes by default), with a credit for the photographer. The discs become flat frosted glass: the wallpaper, softly blurred, under a light veil. With none of yours, it uses nine built-in dunes. See [Your own wallpapers](#your-own-wallpapers).
- **Apps you choose.** Up to six on the dock, picked with Edit at the bottom right (see [Make the dock yours](#make-the-dock-yours)). Well-known apps show their own mark; any other has its mark cut from its icon (an app that is only a name shows its first letter), so apps you install later fit in automatically. Every other app, the Appstore included, is under Apps.
- **Light and dark.** With the wallpaper off, **Settings > Theme** switches between a light and a dark ground. The dark look is tonal glass: graphite discs a shade lighter than the screen, with a rim of light and a soft glow. A wallpaper brings its own look (text and marks turn dark or white to suit the part of the picture behind them), so the theme is dimmed while it is on.
- **A screensaver that is only the time.** After a few idle minutes the screen goes black, with the date and time drifting across it so no pixel is held in place. Any button wakes it.
- **Settings in the same style.** Network, Controllers, Apps, My Fire TV, the wallpaper, how often it changes, the theme, the dock order, the screensaver delay and your greeting. Each shows its name, and the ones you change show their current value.
- **Covers Amazon's home completely.** A "Launching Home" screen is held until the new home is really on screen, so Amazon's home is never seen. The handover takes about a second. It also starts early at boot and survives the app being updated.
- **No account, no cloud, no tracking.** The only network use is reading your wallpaper repository, and only if you name one; with the wallpaper off, it is never used.
- **Reversible.** One command puts Amazon's home back.

## How it works

Fire OS won't let another app be the default home. So the app watches the system log for Amazon's home starting (a permission
you grant once over ADB), covers it, and brings its own home up. Android blocks an app from opening a screen for five seconds after the Home button, so the app has the alarm service open it instead, which is exempt. Because it relies on ADB, it works on Fire TV devices that
let you switch on **ADB debugging** (Settings > My Fire TV > Developer options). Tested on a Fire TV Stick Lite (Fire OS 7,
Android 9); other Fire OS 7 devices should work, and Fire OS 5 is untested for the home screen. It is not affiliated with Amazon.

## Size

The app is about 0.25 MB, most of it the nine built-in dunes; without them it is under 0.1 MB. The screen is drawn,
and the other pictures are vector logos and glyphs. Its whole source is about 3,000 lines of Java.

## Requirements

- A Fire TV with ADB debugging on, and a computer on the same network (macOS or Linux; a JDK 17 and the Android SDK to build).
- Optional: a GitHub account for your own wallpapers.

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

Press **Down** from the dock to the buttons at the bottom right, move to **Edit**, and press **OK**. The discs rock, and a
line at the bottom left says what each button does. The controls:

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
`--reset-dock`. The first time the home opens it also shows a one-time line pointing at Edit.

## Screensaver

After the home has sat idle it shows a black screen with the date and time on it, moving to a new place every ten
seconds. Any button wakes it, and that press does nothing else. Choose the delay under **Settings > Screensaver**: Off,
1, 5 (the default), 10, 15 or 30 minutes. It never starts while you are editing the dock.

## Your own wallpapers

The **Wallpaper** setting is on by default. Out of the box it uses nine sand-dune wallpapers built into the app. To show
your own images instead, the app downloads them from a GitHub repository of yours, public or private. Set it up once:

1. **Create the repository.** On GitHub choose **New repository**, give it a name such as `tv-wallpapers`, set it
   to **Public** (or Private, see below) and create it. (It can be empty. Public is simplest; for a private one see below.)
2. **Add images.** Open the repository, choose **Add file > Upload files**, drag your images in and **Commit changes**.
   Use JPG, PNG or WebP at the **top level** (not in a folder). **16:9** images are ideal (1920 × 1080 is enough, 3840 × 2160
   is plenty). Any other shape or size is fine too: the TV cuts the middle 16:9 out of it and scales it to 1920 × 1080
   itself, one image at a time, in the background, so even a 100-megapixel photo is safe. GitHub's own limit of 100 MB
   per file applies, and smaller files simply download faster.
   From a terminal: `git add . && git commit -m "Add wallpapers" && git push`.
   The folder [`wallpaper-repo-template/`](wallpaper-repo-template/) has a ready-made README you can copy into the repository.
3. **Name the files to order and credit them.** Name each image `NUMBER-First-Last.jpg`, for example
   `1-Raghava-Boppudi.jpg`, `2-Allen-Daniel.jpg`. The number is the order the wallpapers rotate in (1, 2, 3 ... 10, 11
   are in numeric order). The words after it are the photographer: the screen shows `© Raghava Boppudi` at the bottom.
   A file with no number in front is shown after the numbered ones, and with no credit. Rename a file on GitHub with
   the pencil icon on its page, or with `git mv`.
   **A picture for the greeting (optional).** The bottom left of the home says "Hello, NAME" with a small round picture
   beside it. Put a file named `profile.jpg` at the top level of the same repository (any size: the TV cuts the middle
   square out of it and shrinks it) and it is used for the picture; it is never shown as a wallpaper. Without one, only the
   words show. Set NAME as `profile_name` in `config.xml` (blank hides the greeting).
4. **Point the app at it.** Edit [`firehome/res/values/config.xml`](firehome/res/values/config.xml) and set
   `wallpaper_repo` to `YOUR-GITHUB-USERNAME/YOUR-REPO-NAME`. If the images are in a folder, put its name in
   `wallpaper_path`. Then run `firehome/install.sh` again.
5. **Check it.** On the TV open **Settings > Refresh** (it is dimmed while the wallpaper is off). It says "N wallpapers from GitHub" when it found
   your images. After that new images are picked up automatically, every 30 minutes, whenever Home opens.

### Keeping the repository private

Your images can stay private, so only you can see them and the TV still gets them. The TV then needs a read-only
**access token**:

1. On GitHub open **Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token**.
2. Set **Repository access** to **Only select repositories** and choose your wallpaper repository. Under **Permissions >
   Repository permissions** set **Contents** to **Read-only**. Nothing else. Pick an expiry (a year is reasonable).
3. Copy the token (it starts with `github_pat_`). Save it in the git-ignored file `firehome/wallpaper-token`, or pass it for one run:
   `WALLPAPER_TOKEN=github_pat_... firehome/install.sh`. The installer sends it to the TV over ADB; it is kept in the app's
   private storage, **not** in the APK and not in git, and is only ever sent to GitHub.
4. Open **Settings > Refresh** on the TV. "GitHub won't show this TV the repository" means the name or the token is wrong.
   Remove the token any time with `firehome/install.sh --clear-token`. When it expires, make a new one and run `install.sh` again.

Anyone with ADB access to your TV could read the token, which is why it should be read-only and for this one repository.
Before switching a repository to private, install with the token first: until then the TV can't see it.

How it behaves:

| Situation | What the TV shows |
|---|---|
| The repository has at least one valid image | Only your images, in number order |
| The repository is empty or has no valid images | The built-in dunes |
| No repository is set in `config.xml` | The built-in dunes, and no network use |
| GitHub is unreachable | The last images it downloaded; the dunes if there are none |

If Refresh says "The repository has no images yet": check the images are on the default branch at the top level (or in
the folder you set as `wallpaper_path`) and are JPG, PNG or WebP. For a public repository, opening
`https://api.github.com/repos/YOUR-GITHUB-USERNAME/YOUR-REPO-NAME/contents/` in a browser should list them. A private
repository shows "Not Found" there by design; use the token steps above instead. If it says "GitHub won't show this TV the
repository", the repository name in `config.xml` or the token is wrong.
Anyone who can push to the repository controls your wallpapers, so keep its write access to yourself, and only
add images you have the right to publish. The built-in dunes are generated by
[`firehome/wallpapers/generate.py`](firehome/wallpapers/generate.py) (needs numpy and Pillow) and are CC0.

Turn the wallpaper off under **Settings > Wallpaper** to get the flat look back, and the network is not used at all.

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
