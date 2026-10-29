<p align="center"><img src="assets/roco.png" alt="ROCO robot health and fitness coach for Android" width="160"></p>

# ROCO — personal scores & AI coaching for Google Health

**Turn your Google Health data into understandable scores, visual insights, and a personal AI coach.** ROCO is a native Android companion that helps you understand your body energy, sleep, HRV, recovery, and weekly training strain — and decide what to do next.

<p align="center">
  <a href="https://github.com/wabbo-hundeluft/ROCO/releases/download/v2.0.20/ROCO-2.0.20.apk"><img alt="Download Android APK" src="https://img.shields.io/badge/Download_APK-2.0.20-2E7D58?style=for-the-badge&amp;logo=android&amp;logoColor=white"></a>
  <a href="https://github.com/wabbo-hundeluft/ROCO/releases"><img alt="All releases" src="https://img.shields.io/badge/All_Releases-ROCO-25392E?style=for-the-badge&amp;logo=github&amp;logoColor=white"></a>
  <a href="https://github.com/wabbo-hundeluft/ROCO/releases/tag/v2.0.20"><img alt="Release notes" src="https://img.shields.io/badge/Release_Notes-2.0.20-E5A94F?style=for-the-badge&amp;logoColor=white"></a>
</p>

## What is ROCO for?

Your watch records the measurements. **ROCO adds the interpretation:** locally calculated scores, comparisons with your own history, and optional AI conversations about your health and training data. Ask your robot coach “Why is my energy low today?”, explore what contributed to your sleep score, or discuss your upcoming training plan.

- **Scores for Google Health data:** body energy, sleep and adaptive weekly training strain, explained with charts and contributing measurements.
- **AI coaching for Google Health data:** discuss available HRV, resting heart rate, sleep, workouts and journal patterns with your named robot coach.
- **From insight to action:** plan training and rest days, prepare for tonight’s sleep, and track habits in your journal.

Google Health is the preferred configured data connection; **Health Connect is the fallback**. ROCO calculates its own scores rather than displaying official Google scores. AI coaching is optional and uses your own provider API keys.

ROCO is an independent app, not an official Google product or affiliated with Google.

**English · Deutsch · Français · Español** · [Deutsch lesen](README.de.md)

## Download ROCO for Android

[Download ROCO 2.0.20 for Android](https://github.com/wabbo-hundeluft/ROCO/releases/download/v2.0.20/ROCO-2.0.20.apk) · [All releases](https://github.com/wabbo-hundeluft/ROCO/releases). Install it on your Android phone and allow installation from your chosen browser or file manager when Android asks.

| Release | Details |
| --- | --- |
| Version | 2.0.20 preview |
| Android | Android 9 / API 28 or later; health-data availability depends on your device and connected services |
| App name | ROCO |
| Package | `com.wabbos.healthcoach.v2preview` |
| Distribution | APK for personal preview testing, currently signed with a development certificate |
| Integrity | Compare the APK’s SHA-256 with `SHA256SUMS.txt` in the release |

This is an early preview. Install updates over the existing ROCO package to retain its local data. An installation with a different package or signing certificate cannot update it directly.

## What ROCO helps you understand

- **Body energy:** a dynamic model of recovery and energy use, with an interactive battery view and a dated daily timeline.
- **Adaptive weekly training strain:** a personal weekly target normalized to 100 points. See how recorded workouts contribute, and leave room for recovery days.
- **Sleep and recovery:** explore sleep duration, stages, personal comparisons, HRV, resting heart rate, and the planner for your next night.
- **Personal vital signs:** compare available measurements with your own history instead of treating one universal number as everyone’s baseline.
- **Training plans:** choose your goals, schedule workouts, mark sessions complete, and discuss supported plan changes with your coach.
- **Habit journal:** answer preset or custom questions, maintain a check-in streak, and explore local associations between habits and recovery.
- **Visual trends:** browse previous days and interactive 7-, 30-, and 90-day charts. Long views group daily observations for readability.

## A coach with personality

Give your robot a name during onboarding. It greets you, unfolds and stretches on your first morning visit, listens while you type, reacts to progress, and goes back into its shell to sleep. Its animations are drawn in code, respect disabled system animations, and pause when not visible.

Start a **new chat** for a fresh conversation and a large robot greeting. The previous chat can be resumed. Every follow-up request includes a short local recap of the last exchange to help keep answers consistent across model changes.

Optional AI providers: **Groq → Mistral → OpenRouter → Gemini**. Add your own API keys and select an available model or enter its model ID. Only configured providers are tried. Pricing, free-tier limits, and model access depend on your provider and account.

## Health data and privacy

ROCO prefers a configured **Google Health** connection and uses **Health Connect** as a fallback. It keeps each imported dataset’s source consistent when calculating scores. Direct Google Health access requires an eligible Google Cloud/OAuth configuration; availability is not guaranteed for every account. Health Connect also requires compatible software and data from your watch or apps.

Scores, journal entries, plans, chat history, and API settings are stored locally with authenticated encryption. ROCO does not require its own cloud account. Google imports and optional AI requests use network access. The coach’s access to personal app data is controlled in settings; third-party providers process the context sent to them.

## Getting started

1. Install the APK and name yourself and your robot.
2. Choose **System language**, **Deutsch**, **English**, **Français**, or **Español** under **Profile → Language**. Unsupported system languages fall back to English.
3. Connect a health-data source and grant the permissions you want to use.
4. Agree on your own training and sleep goals. More complete measurement history improves personal comparisons.
5. Optionally configure AI providers, then explore your scores and fill in your journal.

Scores are transparent local wellness estimates, not medical measurements or clinically validated diagnoses. Missing data can leave a score unavailable. Journal associations do not establish cause and effect; ROCO does not reproduce proprietary WHOOP, Garmin, or other third-party algorithms.

## Preview quality and feedback

Version 2.0.20 passed 46 targeted automated checks, including language selection, chat continuity, provider fallbacks, morning animations, and keyboard layout. The debug APK build completed successfully. Automated checks do not replace testing every watch, Google account, Android version, or live AI model.

When reporting an issue, include your ROCO version, Android version, data source, and steps to reproduce. Remove API keys and personal health details from screenshots or logs before posting publicly.
