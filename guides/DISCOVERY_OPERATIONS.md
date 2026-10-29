# ROCO discovery operations

Implementation date: 2026-10-29. The original planning snapshot preceded publication of the full Apache-2.0 source and is not the current license status.

## Implemented assets

- EN/DE problem-oriented README, honest WHOOP comparison and source/license links.
- Fourteen static EN/DE pages in `docs/`, generated from `website/content.py`.
- Canonical URLs, reciprocal hreflang, OpenGraph, MobileApplication metadata without fabricated ratings/prices, sitemap, no JavaScript required for content.
- Six real English UI renders using synthetic data; social card composed from existing robot artwork and app renders.
- Compatibility/data-path matrix, local score explanation and privacy/data-flow disclosure.
- Contributor/security guidance and privacy-conscious issue forms.
- Local site validation: `python3 scripts/check_site.py`.

## Hosting configuration

GitHub Settings → Pages → Deploy from a branch → `main` → `/docs`. Site: https://wabbo-hundeluft.github.io/ROCO/ . Public repository metadata should point here. Website source and generated files must be committed together.

### Important robots.txt limitation

A crawler reads robots policy from **https://wabbo-hundeluft.github.io/robots.txt**, not from `/ROCO/robots.txt`. The project copy documents intended crawl access but cannot control the origin. A missing origin robots.txt normally does not prohibit crawling. Check the actual origin response after deployment. If a restrictive origin policy exists, the account owner must change their user-site repository or domain configuration; do not claim the project file overrides it. OAI-SearchBot and GPTBot are separate. No training-bot permission changes are needed for this project.

## Account-dependent verification: do not mark as completed without proof

1. Google Search Console: add URL-prefix property `https://wabbo-hundeluft.github.io/ROCO/`; use the HTML verification file or meta value supplied by your account. Commit that exact public verification artifact (never credentials), verify, then submit `https://wabbo-hundeluft.github.io/ROCO/sitemap.xml`.
2. Bing Webmaster Tools: add this site or import a verified Search Console property. Use its supplied verification artifact; submit the same sitemap. IndexNow is optional and is not configured.
3. Record the returned verification/submission state. A sitemap is not proof of indexing or ranking.
4. No analytics scripts or third-party accounts have been invented for this site. No public community posts, directory submissions or outreach are authorized by this document.

## Product trust work remaining

- A 20–30 second demo needs a current real recording or reproducible full animation capture; the still-image gallery is not labelled as a video.
- A dedicated production signing policy and secure signing setup need a separate release task. Existing preview checksums remain valid.
- CI is now configured in `.github/workflows/verify.yml`. Website generation/checks have passed on GitHub. The first Android job failed because the SDK package is named `platforms;android-37.0`, not `platforms;android-37`; this is corrected. The new Android job is still running at this checkpoint. No full-suite pass is claimed.
- Recruit 2–5 consenting testers and publish only non-identifying device/source compatibility results. The matrix describes implementation scope rather than unperformed tests.
- Discussions can be enabled when there is a moderation commitment; it is not required for search discovery.
- F-Droid eligibility needs its own dependency review (including Google Play services). No listing or eligibility is promised.

## Measuring results

Use `DISCOVERY_MEASUREMENT.md` and `discovery-results.csv`. Repeat consistent prompts and record the system, exact query, language/region, search use, citation URL and correctness. Do not convert release downloads into installations. No AI mention, ranking, star or traffic guarantee is made.

Useful references reviewed 2026-10-29:
- https://developers.google.com/search/docs/appearance/ai-features
- https://developers.openai.com/api/docs/bots
- https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site

## Deployment evidence

GitHub Pages is enabled from `main` / `/docs`. All 14 canonical sitemap pages returned HTTP 200 after deployment. Description, homepage and 18 topics are applied. The repository social preview references the uploaded robot/app social card. The effective origin `/robots.txt` returned 404, not a crawl prohibition. Google Search Console URL-prefix setup has started; its public HTML verification file is included. Ownership verification and sitemap submission still need their returned success state. Bing sign-in is in progress.
