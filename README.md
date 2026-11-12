<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="images/brand/streamtube-logo-dark.png">
    <img src="images/brand/streamtube-logo-light.png" width="420" alt="SmartTube+">
  </picture>
</p>

<h1 align="center">SmartTube+ (Phone & Tablet Edition)</h1>

<p align="center">
  <b>Ad-free YouTube client for Android phones, foldables, and tablets.</b><br>
  A touch-optimized app with intuitive gestures, 3-zone fullscreen, card previews, tablet split-view,<br>
  in-app mini player, comments with writing & reply support, downloads, background play, and SponsorBlock.
</p>

<p align="center">
  <a href="https://github.com/CaptnBook4Git/smarttube-phone-plus/releases/latest/download/SmartTube-Phone-arm64.apk"><b>Download latest APK (SmartTube+)</b></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/CaptnBook4Git/smarttube-phone-plus/releases">All releases</a>
  &nbsp;·&nbsp;
  <a href="#install">How to install</a>
  &nbsp;·&nbsp;
  <a href="#credits">Credits</a>
</p>

<p align="center">
  <img src="images/phone/01_home.jpg" width="180" alt="Home">
  <img src="images/phone/02_player.jpg" width="180" alt="Player">
  <img src="images/phone/03_comments.jpg" width="180" alt="Comments">
  <img src="images/phone/04_mini_player.jpg" width="180" alt="Mini player">
  <img src="images/phone/05_shorts.jpg" width="180" alt="Shorts">
</p>

> [!NOTE]
> **SmartTube+** is an enhanced fork of [SmartTube](https://github.com/yuliskov/SmartTube) (by [Yuriy Liskov](https://github.com/yuliskov)) and the phone port by [shreyas-bangera](https://github.com/shreyas-bangera).
> This Plus edition enriches the phone port with comprehensive tablet optimizations, intuitive player gestures, interactive card previews, quick curation actions, a dedicated stop button, and a unified Plus settings menu.
> It is an independent open-source project, not affiliated with SmartTube's original developer, YouTube, or Google.

---

## ✨ What's new in SmartTube+? (Features & Gestures)

The **Plus version** expands SmartTube with numerous quality-of-life enhancements and gestures tailored specifically for modern touch devices (phones, foldables, and tablets):

### 👆 Player Touch Gestures

| Gesture | Where & How | Action |
|---|---|---|
| **Brightness control** | Swipe up / down on the **left 20%** in fullscreen | Smooth stepless display brightness adjustment. |
| **Volume control** | Swipe up / down on the **right 20%** in fullscreen | Smooth stepless media volume adjustment. |
| **Exit fullscreen** | Swipe down on the **middle 60%** in fullscreen | Exits fullscreen back to portrait / split-view player (without accidentally minimizing to mini-player). |
| **Enter fullscreen** | Swipe up on the normal player | Instantly transitions into fullscreen mode. |
| **Mini-player (PiP)** | Swipe down on the normal player (or press Back) | Shrinks the video into a floating / in-app mini player for seamless multitasking. |
| **⚡ Tap & Hold Speed Boost** | Touch and hold the bare video area (> 600&nbsp;ms) | Temporarily accelerates playback to e.g. **2.0x** (or your preferred speed chosen in settings). Releasing immediately restores the previous normal playback speed. |
| **Show player controls** | Single tap on the video | Instantly and reliably reveals the play/pause and stop controls without lag. |

---

### 🎬 Interactive Card Preview (Feeds & Lists)

- **Touch & hold a video card:** Starts video playback directly inside the card's thumbnail across Home, Subscriptions, or Search.
- **Swipe up while holding:** Seamlessly transitions into the main player, continuing playback right from where the preview was.
- **Simply release:** Stops the preview immediately. The video is automatically reset to **unwatched** — preventing quick feed browsing from cluttering your YouTube watch history!
- **Configurable in Settings:** Choose whether previews play muted or with audio, and whether they start from the beginning or from a random timestamp.

---

### ⏹️ Enhanced Player Controls

- **Dedicated Stop Button (`■`):** Located right next to Play/Pause in the center of the player. Instantly stops playback and closes the player, returning you straight to your feed.
- **SponsorBlock on Timeline:** Sponsor segments, intros, and outros are visually highlighted in distinct colors along the scrub bar.
- **Quick Action Buttons:** Direct access to *Like*, *Dislike*, *Not interested*, and *Don't recommend channel* right beneath the player.

---

### 📱 Tablet & Large Screen Optimizations

- **Configurable Feed Grid:** Choose the number of grid columns (e.g. 2, 3, or 4 columns) for video lists on tablets and landscape orientations.
- **Landscape Split-View:** On tablets, watch the video on the left while browsing recommendations and details on the right.
- **Side Comments Panel:** Comments slide in as a dedicated right-hand side panel with an explicit close button — **without dimming the playing video**.
- **Top-Right Account Switcher Dropdown:** On tablets, tapping the profile avatar opens a compact popup dropdown right under your finger rather than an oversized bottom sheet across the entire screen.
- **Decluttered You Tab:** Removed redundant settings chips, keeping quick access via the top-right settings icon.

---

### ⚙️ Dedicated "Plus" Settings

- **Unified "Plus" Menu (with `+` icon):** All Plus features (speed boost multiplier, grid column counts, preview settings, etc.) are gathered in one convenient settings section.
- **"Check for updates" pinned to top:** Placed right at the very top of the main settings screen for immediate one-tap update checks.

---

## Contents

- [✨ What's new in SmartTube+? (Features & Gestures)](#-whats-new-in-smarttube-features--gestures)
- [Features](#features)
- [Requirements](#requirements)
- [Install](#install)
- [Update to a new build](#update-to-a-new-build)
- [Connect your YouTube account](#connect-your-youtube-account)
- [How to use](#how-to-use)
  - [Home](#home)
  - [Watching a video](#watching-a-video)
  - [Comments](#comments)
  - [Mini player and background play](#mini-player-and-background-play)
  - [Full screen gestures](#full-screen-gestures)
  - [Shorts](#shorts)
  - [Channels](#channels)
  - [Search](#search)
  - [Subscriptions and the You tab](#subscriptions-and-the-you-tab)
  - [Settings and appearance](#settings-and-appearance)
- [Troubleshooting](#troubleshooting)
- [Privacy](#privacy)
- [Building from source](#building-from-source)
- [Credits](#credits)
- [License](#license)

---

## Features

**Feels like YouTube**
- Bottom tabs: Home, Shorts, Subscriptions, Notifications and You
- Topic chips on Home (Kids, Sports, LIVE, Gaming, News and more)
- Full-width video cards with channel pictures, and horizontal shelves
- Videos open with a smooth expand-from-thumbnail animation

**Player**
- Portrait player: the video on top, then the title, channel, buttons, comments and recommendations
- Like, dislike, subscribe, share, download, save to a playlist, captions, quality and playback speed
- Rotate the phone (or tap the full screen button) for full screen, with gestures for seeking, brightness, volume and zoom
- Videos start quickly: 1080p by default on phones, and nothing extra is loaded before the first frame

**Mini player**
- Swipe the video down, or press Back: it shrinks into a small player at the bottom right of the screen and keeps playing while you browse Home, Search or a channel
- Drag it anywhere, tap it to go back to the full player, close it with X
- Background play with media controls in the notification and on the lock screen

**Comments**
- Total count, Top or Newest first, and reply threads
- Write comments and replies when signed in
- Long press your own comment or reply to delete it

**Downloads**
- Save a video from 1080p down to 360p (MP4), or audio only (M4A), to **Download/SmartTube**

**Shorts**
- Full screen vertical Shorts: swipe up for the next one, down for the previous one
- Like, dislike, comments, share and save on the right side, like the official app

**No ads, and more**
- No ads, and no tracking added by the app
- SponsorBlock skips sponsor segments, intros, outros and reminders to subscribe
- Up to 8K, 60 fps and HDR when your phone supports it
- Works without Google Play services
- Dark (pure black for AMOLED screens), Light, or Same as device
- Built-in updater: see what's new and install new builds from inside the app

## Requirements

- An Android phone with Android 8.0 or newer (the in-app mini player needs Android 11 or newer; older versions use a floating window instead)
- A 64-bit ARM processor (arm64-v8a). This covers almost every phone and tablet made since 2017.

## Install

1. On your phone, open this link: **[SmartTube-Phone-arm64.apk](https://github.com/CaptnBook4Git/smarttube-phone-plus/releases/latest/download/SmartTube-Phone-arm64.apk)**
2. When the download finishes, tap the notification (or open the file from your Downloads folder).
3. Android asks for permission to install apps from your browser the first time. Tap **Settings**, turn on **Allow from this source**, then go back.
4. Tap **Install**. If Google Play Protect warns about an unknown app, tap **More details** and then **Install anyway**. This warning appears for every app that is not from the Play Store.
5. Open **SmartTube+** from your app drawer. Home shows popular videos straight away; you can sign in whenever you like.

When Android asks to allow notifications, tap **Allow**. The app uses notifications for background playback controls and optionally for new video alerts from your subscriptions.

## Update to a new build

Each build is listed on the [releases page](https://github.com/CaptnBook4Git/smarttube-phone-plus/releases). The download link above always points to the newest one.

**From inside the app (recommended)**

1. Open the **You** tab, tap the **Settings** chip, then **Check for updates**. The **Software update** screen opens and checks right away. It shows the build you have.
2. If a newer build exists, it shows **Update available** with the build number, when it was released, the download size and a **What's new** list. Tap **Download and install**; a progress bar shows the download.
3. The first time, Android asks you to allow SmartTube to install apps: tap **Open settings**, turn on **Allow from this source** and come back. The update carries on by itself.
4. Android shows its usual confirmation. Tap **Update**. You stay signed in and all your settings are kept.

With **Notify me about new builds** turned on (on the same screen), SmartTube also checks periodically and shows a small **Update** card on Home when a new build is out.

## Connect your YouTube account

You don't need an account to watch. Signing in adds your own recommendations on Home, your subscriptions, history, playlists and Watch later, and lets you like, subscribe, save and comment.

SmartTube signs in the same way a smart TV does: the app shows a short code, and you approve it on Google's own website. **Your password is never typed into the app** and never passes through it.

<p align="center">
  <img src="images/phone/08_you.jpg" width="220" alt="The You tab before signing in">
  <img src="images/phone/10_sign_in.jpg" width="220" alt="The sign in screen with a code">
</p>

**On the same device**

1. Open the **You** tab (bottom right) and tap **Sign in**. You can also tap **Sign in** on the card at the top of Home.
2. The app shows your sign-in code. Wait a moment until the code appears.
3. Tap **Continue in browser**. Your browser opens Google's device activation page (yt.be/activate) with the code already filled in.
4. Choose your Google account and tap **Allow** to give access to YouTube.
5. Switch back to the app. It signs you in by itself within a few seconds.

**Using another device (a computer or a second phone)**

1. In SmartTube+, open the **You** tab and tap **Sign in**.
2. On the other device, go to **yt.be/activate** (or youtube.com/activate) and enter the code shown.
3. Pick your account and tap **Allow**. The app signs in automatically.

## How to use

### Home

<p align="center">
  <img src="images/phone/01_home.jpg" width="240" alt="Home">
</p>

- Scroll the feed and tap any video to play it.
- Tap a chip at the top (Kids, Sports, LIVE, Gaming, News, or custom search lists) to switch topics.
- Tap a channel picture to open that channel.
- Tap the three dots next to a video for more options, or use direct Quick Action buttons (Like, Dislike, Not Interested, Don't Recommend Channel).
- Top right: Search and account switcher.

### Watching a video

<p align="center">
  <img src="images/phone/02_player.jpg" width="240" alt="Player">
</p>

- **Tap the video** to show or hide the controls: play/pause, stop playback, previous/next, playback speed, captions (CC), settings (quality and more) and full screen.
- **Seek** by dragging along the progress bar with SponsorBlock markers at the bottom of the video.
- **Double tap** the left or right side of the video to jump back or forward.
- **Below the video**: the title, the channel with its Subscribe button, and quick buttons: Like, Dislike, Comments, Share, Download and Save.

### Comments

<p align="center">
  <img src="images/phone/03_comments.jpg" width="240" alt="Comments">
</p>

- Tap the comment bar below the video to view the full comment sheet (or side panel on tablets).
- Toggle between **Top** and **Newest** comments.
- Tap on replies to expand full reply threads.
- Post comments and replies with your signed-in account, and long-press your own comment to delete it.

### Mini player and background play

<p align="center">
  <img src="images/phone/04_mini_player.jpg" width="240" alt="Mini player">
</p>

- **Swipe down on the video** (or press Back) while a video is playing: it smoothly docks into the in-app mini player.
- Continue browsing your feeds while listening. Tap to expand, or swipe down / tap X to close.
- **Background play:** Lock your device or switch apps to keep audio playing via notification controls.

### Full screen gestures

Rotate the phone sideways or tap the fullscreen button. In fullscreen:

| Gesture | What it does |
|---|---|
| Tap | Show or hide player controls |
| Double tap left / right | Jump back / forward |
| Swipe up / down on left 20% | Adjust display brightness |
| Swipe up / down on right 20% | Adjust media volume |
| Swipe down on middle 60% | Exit fullscreen back to normal player |
| Pinch in / out | Zoom video to fill screen or return to original aspect ratio |

### Shorts

<p align="center">
  <img src="images/phone/05_shorts.jpg" width="240" alt="Shorts">
</p>

- Tap the **Shorts** tab: full-screen vertical Shorts start playing immediately.
- **Swipe up** for the next Short, **swipe down** for the previous one.
- **Tap** to pause or resume. Like, dislike, comments, share, and save are accessible on the right.

### Channels

<p align="center">
  <img src="images/phone/13_channel.jpg" width="240" alt="Channel page">
</p>

- Tap any channel avatar or name to open the channel page with banner, subscriber counts, and tabs for Videos, Shorts, Live, and Playlists.

### Search

<p align="center">
  <img src="images/phone/06_search_suggestions.jpg" width="220" alt="Search suggestions">
  <img src="images/phone/07_search_results.jpg" width="220" alt="Search results">
</p>

- Tap the search icon, enter text or use voice search.
- Use advanced search filters (date, duration, features, hide watched) and save searches as permanent custom lists on Home.

### Subscriptions and the You tab

- **Subscriptions** shows the latest uploads from your followed channels with active filter chips.
- **Notifications tab** provides updates when subscribed channels release new content.
- **You tab** offers quick access to History, Downloads, Playlists, and Settings.

### Settings and appearance

<p align="center">
  <img src="images/phone/09_settings.jpg" width="220" alt="Settings">
  <img src="images/phone/11_home_light.jpg" width="220" alt="Home in Light appearance">
</p>

Open the **You** tab and tap the **Settings** icon.
- **Check for updates**: Pinned directly to the top of settings.
- **Plus**: Unified menu for speed boost multiplier, grid column customization, card preview behavior, and quick actions.
- **Appearance**: Dark (pure AMOLED black), Light, or System theme.
- **Account, Playback, and General**: Full fine-grained control over quality, SponsorBlock, history, and backup.

## Troubleshooting

**"App not installed" when installing a new build**
Uninstall any older build with conflicting signature keys once, then install the latest APK.

**Google Play Protect warning**
Tap **More details** and then **Install anyway**.

**Videos buffer a lot**
Open player settings and choose a lower default resolution or adjust video codec settings.

**The app crashed**
When it opens again it offers a crash report. Please [open an issue](https://github.com/CaptnBook4Git/smarttube-phone-plus/issues) with the crash details.

## Privacy

- No ads, and no telemetry or tracking added by this app.
- Sign-in is handled securely through Google's device activation endpoint.
- Talks directly to YouTube, GitHub (for updates), and SponsorBlock (if enabled).

## Building from source

SmartTube+ is the Gradle flavor `stphone`. The phone/tablet code lives in [`smarttubetv/src/stphone/`](smarttubetv/src/stphone/).

Builds run in GitHub Actions ([`.github/workflows/phone.yml`](.github/workflows/phone.yml)). To build locally with JDK 17 and Android SDK:

```bash
git clone --recursive https://github.com/CaptnBook4Git/smarttube-phone-plus.git
cd smarttube-phone-plus && git -C MediaServiceCore apply --ignore-whitespace ../.github/patches/mediaservicecore.patch
./gradlew assembleStphoneRelease
```

## Credits

This project is built upon **[SmartTube](https://github.com/yuliskov/SmartTube)** by **[Yuriy Liskov (yuliskov)](https://github.com/yuliskov)** and its contributors, as well as the phone port by **[shreyas-bangera](https://github.com/shreyas-bangera)**. SmartTube+ adds tablet enhancements, custom gestures, speed boost, card previews, stop button, and UI refinements on top.

Support the original project:
- Star [SmartTube on GitHub](https://github.com/yuliskov/SmartTube)
- Donate to Yuriy Liskov through [SmartTube README](https://github.com/yuliskov/SmartTube#donation)

Thanks also to:
- [SponsorBlock](https://sponsor.ajay.app/)
- [ExoPlayer](https://github.com/google/ExoPlayer)

SmartTube+ is an independent, unofficial project. It is not affiliated with Google or YouTube.

## License

MIT. See [LICENSE](LICENSE). Copyright (c) 2020-present yuliskov, phone edition changes by shreyas-bangera, plus edition changes by CaptnBook4Git.
