# Fork management (Psyquix/ReadYou)

This fork tracks upstream with three local additions. Everything below is
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
3. **Mark above/below as unread** (`ArticleListReaderViewModel.markAsUnreadFromListByDate`):
   adds the inverse of upstream's positional marks to the long-press article
   menu. Upstream only offers "Mark above/below as read"; this adds the
   matching unread pair, scoped to the currently loaded articles exactly as the
   read pair is. The unread items only appear when there is something read
   above or below the long-pressed article, so on an unread filter the menu
   stays the same length as upstream's.

### How the patches are shaped

Every change to an upstream-owned file is a pure insertion: new parameters
(defaulted to `null`), new functions, new menu items, new strings. No upstream
function body is modified. `markAsReadFromListByDate` is deliberately left
byte-identical and the unread path is a sibling function, so an upstream
conflict in the article menu can never take the new feature down with it.
Selection logic lives in the pure top-level `selectPositionalArticles`, covered
by `SelectPositionalArticlesTest` (JVM, no emulator).

When adding a patch, keep that property: it is what makes the weekly automated
merge boring.

## Automation (`.github/workflows/`)

- `track-upstream` (weekly Mondays + manual): merges the latest
  `Ashinch/ReadYou` release into `main` and pushes (which triggers the stock
  `Build Commit` APK build). Opens an issue on merge conflict — resolve by
  hand, keeping both sides where sensible.
- `release` publishes `vX.Y.Z+psyquix.N` with the APK attached, two ways:
  - **automatically** — on green `Build Commit` runs whose head commit contains
    `merge upstream`
  - **manually** — `workflow_dispatch` with a `source_run` input (a Build Commit
    run id). Tags as `vX.Y.Z+psyquix.manual.N` and the notes lead with
    "Manual release. Not an upstream merge." A run that did not conclude
    `success` is refused, so a red build cannot be published by hand. The
    workflow checks out the source run's commit, so `versionName` is read from
    the tree the APK was built from.

  ```sh
  gh workflow run release.yml -f source_run=<Build Commit run id>
  ```
- `build_commit.yaml` (upstream file, lightly edited): pinned to
  `ubuntu-24.04` and Node-24-ready actions (`checkout@v4`, `setup-java@v4`,
  `gradle/actions/setup-gradle@v4`, `upload-artifact@v5`), plus a
  `testGithubReleaseUnitTest` step. `release` only fires on a green
  `Build Commit`, so **unit tests are a release precondition**. This matters
  because `testing.yml` is `pull_request`-only and would otherwise never run on
  an automated upstream merge — a merge that compiles but is semantically wrong
  would ship. Add a test with every patch; this gate is what runs it.
- `testing.yml` (upstream file): unchanged, PR-triggered unit tests.

Committing a patch: keep `merge upstream` out of the commit message, or
`release` will trigger on it.

## Installing

Releases carry signed-per-fork APKs (`v...+psyquix.N`, or `v...+psyquix.manual.N`
for a manual build). Migrating from stock
requires uninstall (different signature): export OPML first, then import it
in the fork. Updates within the fork install over each other, no data loss.

## If upstream moves

Upstream branding already points at `ReadYouApp/ReadYou`. If `Ashinch/ReadYou`
goes stale, retarget `track-upstream.yml` (remote URL + API repo in the
`gh api` call) at the new home.
