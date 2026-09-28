# Fork management (Psyquix/ReadYou)

This fork tracks upstream with two local additions. Everything below is
designed to survive `track-upstream` merges without manual work.

## What's different from upstream

1. **Feed-icon resolution** (`RssHelper.queryRssIcon`, used at subscribe,
   sync, and manual reload): feed-declared `<image>` first, then the site
   homepage's icons (Twine-style scraping), then the legacy feed-host lookup.
   Upstream only does the last step, so feeds hosted away from the publisher
   (e.g. on GitHub Pages) showed the host's icon.
2. **OOM guard** (`SubscribeViewModel` + `FeedPreview`): the parsed `SyndFeed`
   is kept in a plain ViewModel field, never in compose state. Previously the
   whole feed sat in `SubscribeState.Configure`, and Compose hashing it
   (ROME `toString`, ~2x feed size in memory) crashed large feeds with
   `OutOfMemoryError` on 256 MB-heap devices.

## Automation (`.github/workflows/`)

- `track-upstream` (weekly Mondays + manual): merges the latest
  `Ashinch/ReadYou` release into `main` and pushes (which triggers the stock
  `Build Commit` APK build). Opens an issue on merge conflict — resolve by
  hand, keeping both sides where sensible.
- `release` (on green `Build Commit` runs whose head commit message contains
  `merge upstream`): publishes `vX.Y.Z+feedicon.N` with the APK attached.
- `build_commit.yaml` (upstream file, lightly edited): pinned to
  `ubuntu-24.04` and Node-24-ready actions (`checkout@v4`, `setup-java@v4`,
  `gradle/actions/setup-gradle@v4`, `upload-artifact@v5`).

## Installing

Releases carry signed-per-fork APKs (`v...+feedicon.N`). Migrating from stock
requires uninstall (different signature): export OPML first, then import it
in the fork. Updates within the fork install over each other, no data loss.

## If upstream moves

Upstream branding already points at `ReadYouApp/ReadYou`. If `Ashinch/ReadYou`
goes stale, retarget `track-upstream.yml` (remote URL + API repo in the
`gh api` call) at the new home.
