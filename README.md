# Curtain

An Android accessibility service that blacks out short-form video inside
Instagram and YouTube while leaving the parts of those apps you actually
want — messages, profiles, notifications — completely alone.

Built for a Pixel 8. `minSdk` is 30, `targetSdk` 35.

## What it does

**Instagram**

| Screen | Behaviour |
| --- | --- |
| Home feed | Blacked out, except the top-right corner (notification + DM icons) and the bottom tab row |
| Reels tab | Blacked out, bottom tab row left visible so you can navigate away |
| Explore / search grid | Blacked out below the search bar |
| DM inbox and threads | Untouched |
| Profiles | Untouched |
| A reel someone sent you in a DM | **Plays normally**, but vertical swipes are swallowed so it cannot advance to the next one |
| A reel opened from anywhere else | Blacked out |
| Stories | Untouched by default, toggleable |

**YouTube**

| Screen | Behaviour |
| --- | --- |
| Shorts player | Blacked out, bottom nav left visible |
| Shorts shelves inside feeds | Just the shelf is covered; the rest of the feed still works |
| Home feed | Untouched by default, toggleable |
| Normal videos | Untouched |

Any full-screen blackout can be dismissed by tapping it, which sends a Back —
you are never stuck looking at an opaque rectangle with no way out.

## Building and installing

There is no Play Store route for this. Google's policy on the accessibility
API does not cover this use case, so it is a sideload.

1. Open the project in Android Studio (Ladybug or newer).
2. Plug the Pixel in, enable USB debugging, and hit Run.

Or from the command line, with `ANDROID_HOME` set:

```
./gradlew installDebug
```

Then on the phone:

1. Open **Curtain** and grant the notification permission.
2. Tap **Open accessibility settings** → Installed apps → **Curtain —
   short-form blocker** → turn it on.
3. Android will warn that the service can "view and control the screen". It
   can. That is how it knows which Instagram screen you are looking at. It
   reads screen structure only: nothing is recorded, stored, or sent anywhere,
   and the app has no internet permission at all.

## Tuning the cut-outs

The gaps in the home-feed mask are set in dp and default to values that
should be close on a Pixel 8, but Instagram moves its chrome around between
releases. Three sliders in the app control them:

- **Top bar height** — how far down the mask starts
- **Top-right cut-out width** — how much of the top-right stays visible
- **Bottom strip height** — how much of the tab row stays visible

Adjust, tap **Open Instagram and check**, come back, repeat. **Show the mask
over this screen** draws the same geometry over Curtain's own screen if you
want to see the shape on a blank background.

## Pausing

The ongoing notification has a **Pause 15 min** action. The master switch in
the app turns blocking off entirely. Neither requires disabling the
accessibility service.

## When an app update breaks it

Detection works by matching view resource IDs in the accessibility tree.
Instagram and YouTube obfuscate a lot of their layouts, and the IDs Curtain
relies on can disappear in any release. When that happens the symptom is
usually a screen that stops being blocked, or the wrong screen going black.

Everything Curtain matches on lives in one file:
`app/src/main/java/com/curtain/blocker/detect/Signatures.kt`.

To find the new IDs, put the phone on the offending screen and run:

```
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```

Search the dump for `resource-id` attributes that look distinctive to that
screen, and add them to the relevant list. Matching is a substring check
against the part after `id/`.

## Architecture

```
CurtainAccessibilityService   event loop, navigation history, tap replay
  └─ NodeScan                 one bounded walk of the window's node tree
  └─ Classifier               node scan  →  Screen (IG_FEED, YT_SHORTS, …)
  └─ MaskPlanner              Screen + settings  →  MaskPlan (rectangles)
  └─ OverlayController        MaskPlan  →  actual windows
       └─ SwipeLockView       swallows swipes, replays taps
```

Two details worth knowing if you change this:

- **Each blackout rectangle is its own window.** A window only receives
  touches inside its own bounds, so the uncovered strips stay fully
  interactive. Doing it as one full-screen view with transparent holes would
  need `ViewTreeObserver`'s hidden touchable-region API.
- **The DM allowance is provenance-based.** The service keeps a short
  history of the screens you passed through. A full-screen Reels player is
  allowed only if a DM screen appears in the last few entries, and the
  decision is latched until the player closes.

## Honest limitations

- **This is a speed bump, not a lock.** You can disable the accessibility
  service in Settings in about ten seconds. It works by making the habit
  cost something, not by being unbypassable.
- **Detection is heuristic and will occasionally be wrong** — a screen that
  should be blocked slipping through, or a brief flash of feed before the
  mask lands (roughly 60–200 ms after the screen changes).
- **The swipe lock swallows all gestures over the video area,** including
  horizontal swipes. Taps are replayed through the accessibility gesture
  API, so like/comment/profile still work, but they can feel slightly
  laggy. The screen edges are left clear so the back gesture still works.
- **The service listens to accessibility events from every app** so it can
  tell when you leave Instagram. It does no work for other packages beyond
  a string comparison, but it does cost some battery.
- **Instagram's "for you" is read as the Explore tab.** If you meant
  something else by that, say so — it's a one-line change in `MaskPlanner`.
