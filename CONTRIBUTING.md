# Contributing to ROCO

ROCO is an Apache-2.0 Android health companion. Useful contributions include reproducible bug reports, device/data-path observations, accessibility fixes, translations and tests for score edge cases.

1. Check existing issues and discuss substantial score or product changes before implementing them.
2. Never submit API keys, OAuth tokens, signing keys, personal health records or private device logs.
3. Build with JDK 21, Android SDK 37 and the included Gradle wrapper; see the README.
4. Use synthetic fixtures for tests and screenshots. Explain which checks ran and any that could not run.
5. Preserve original artwork and third-party license notices. Contributions must be yours to submit under the project license.
6. Describe score changes in terms of inputs, temporal windows, missing-data behavior and regression tests. Do not claim clinical validation.

Website content lives in `website/content.py`; generated GitHub Pages files live in `docs/`. After editing:

```sh
python3 scripts/build_site.py
python3 scripts/check_site.py
```

Commit both source and generated output. Images in `docs/assets/` must contain synthetic or explicitly authorized anonymized data. Do not change the public site base URL without updating canonical URLs, hreflang, sitemap and link checks.
