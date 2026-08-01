# Publishing to the RuneLite Plugin Hub

Everything on the plugin's side is ready. The remaining steps need your GitHub
account, so they have to be done by you — I can't authenticate as you.

## What is already done

- `LICENSE` — BSD 2-Clause, which the Plugin Hub requires
- `icon.png` — 48x72, the maximum the hub allows
- `runelite-plugin.properties` — displayName, author, description, tags,
  plugins, version
- `build.gradle` — `runeLiteVersion = 'latest.release'`, no third-party
  dependencies (Lombok was declared but unused, and has been removed; extra
  dependencies significantly slow down hub review)
- `.gitignore` excludes `build/` and `.gradle/`; nothing generated is tracked
- 79 tests passing

## Blocked on: GitHub access

I cannot do steps 2-4 myself. This machine has no GitHub CLI installed, no
`GITHUB_TOKEN` / `GH_TOKEN`, no stored git credentials and no remote — and
creating a repository, forking and opening a pull request all require
authenticated GitHub API access as you.

**Fastest way to unblock me:** install the GitHub CLI and log in, then I can
do steps 2 and 3 and open the PR.

```
winget install --id GitHub.cli
gh auth login
```

Run `gh auth login` yourself — it is interactive. In this session you can
prefix it with `!` to run it here so the output lands in the conversation.

## Step 0 — fix the commit identity first

Every commit in this repo is currently authored by `unknown
<davidcottrellcoding@gmail.com>` because `user.name` was never set. Two
things to decide before anything becomes public:

```
git config user.name "<your name or GitHub handle>"
```

- That fixes future commits. Rewriting the existing history is possible
  (`git rebase -r --root --exec 'git commit --amend --no-edit --reset-author'`)
  but is only worth it if you care about the back catalogue.
- **Your email address will be public** in every commit once the repo is
  pushed. If you would rather it were not, set a GitHub noreply address
  instead: `git config user.email "<id>+<user>@users.noreply.github.com"`,
  which you can find under GitHub Settings → Emails.

## Step 1 — decide the author name

`runelite-plugin.properties` currently says `author=David`. This is shown
publicly on the Plugin Hub listing. Change it to whatever handle you want
associated with the plugin (usually your GitHub username) before submitting.

## Step 2 — create a public GitHub repository

The hub builds your plugin from its own public repo, so it cannot be a local
repository or a private one.

```
# from Desktop/Code/cox-gear-planner
git remote add origin https://github.com/<your-username>/cox-gear-planner.git
git push -u origin main
```

If you have the GitHub CLI (`gh`) installed you can do it in one step:

```
gh repo create cox-gear-planner --public --source=. --push
```

The **full 40-character commit hash** to submit, as of now:

```
d8dcaafcbf446e3d9964e0584c7b1fb8c7ef7403
```

Re-check with `git rev-parse HEAD` if you make any further commits.

## Step 3 — fork the plugin-hub repository

Fork <https://github.com/runelite/plugin-hub>, then add a single new file at
`plugins/cox-gear-planner` (no extension) containing exactly:

```
repository=https://github.com/<your-username>/cox-gear-planner.git
commit=d8dcaafcbf446e3d9964e0584c7b1fb8c7ef7403
```

## Step 4 — open the pull request

Push that branch to your fork and open a PR against `runelite/plugin-hub`.
CI will build your plugin; fix anything it flags. A RuneLite developer then
reviews it to check it isn't malicious and doesn't break Jagex's rules.

## What review will likely look at

- **It reads your bank and shared storage.** This is ordinary client-side
  data the player has already seen, and other plugins do the same, but be
  ready to explain that the plugin only records item ids and quantities, keeps
  them in RuneLite's own config, and sends nothing anywhere. There is no
  network access in this plugin at all.
- **No automation.** The plugin only reads state and renders a panel. It
  never sends input, and has no timers, overlays or menu manipulation.
- **Reproducible build.** No third-party dependencies, so no dependency hash
  verification is needed.

## Updating after it is published

Push new commits to your repo, then open another PR to plugin-hub changing
only the `commit=` line to the new hash. Bump `version` in
`runelite-plugin.properties` at the same time, and keep the `VERSION` constant
in `CoxGearPlannerPlugin.java` in sync — it is shown in the panel title so
users can tell which build they are running.
