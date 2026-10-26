# Wallpapers for my Fire TV home screen

Images in this repository are shown as the background of a [firehome](https://github.com/RaghavaBoppudi/FireOS-clean)
home screen. The TV downloads them from here. The repository can be public, or private if the TV has an access token (see the main README).

## Adding an image

- **Aspect ratio 16:9** is ideal: 1920 × 1080 is enough, 3840 × 2160 is plenty. Any other shape or size works too: the
  TV cuts the middle 16:9 out of it and scales it to 1920 × 1080 itself, one image at a time in the background, so even a
  100-megapixel photo is safe. Keep the subject near the middle.
- **JPG, PNG or WebP**, up to GitHub's 100 MB per file (smaller files download faster).
- Put the files at the **top level** of the repository (not in a folder), on the default branch.
- **Name each file `NUMBER-First-Last.jpg`**, for example `1-Raghava-Boppudi.jpg`, `2-Allen-Daniel.jpg`. The number is the
  order the wallpapers rotate in, and the name after it is the photographer, shown on screen as `© Raghava Boppudi`.
  A file with no number is shown after the numbered ones, without a credit.
- A file named `profile.jpg` is not a wallpaper: it is the small round picture beside "Hello, ..." at the bottom left of the home. Any size; the TV cuts a square out of the middle.
- Calm, darker areas at the top (the date) and the bottom (the dock) read best.
- Only add images you have the right to publish. This repository is public.

## How often the TV looks

Every 30 minutes, whenever the home screen opens. A renamed or added image shows up after the next check. To check straight away, open **Settings > Refresh
wallpapers** on the TV. If this repository has no images, the TV uses the wallpapers built into the app.
