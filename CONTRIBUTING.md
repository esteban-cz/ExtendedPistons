# Development workflow

Extended Pistons uses short-lived branches and pull requests. Keep `main` in a
releasable state and do not develop directly on it.

## Branches

Start each change from an up-to-date `main` branch:

```powershell
git switch main
git pull --ff-only
git switch -c TYPE/short-description
```

Use one of these prefixes:

- `feature/` for player-facing functionality.
- `fix/` for bug fixes.
- `chore/` for tooling, dependencies, and repository maintenance.
- `docs/` for documentation-only work.

Keep commits focused and use imperative messages such as
`Fix entity lifting at piston corners`.

## Validation

Run the same checks locally that GitHub runs for pull requests:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

Update tests for behavioral changes. Also perform an appropriate client or
modpack smoke test when a change affects rendering, interaction, networking, or
compatibility.

## Pull requests

Push the branch, open a pull request into `main`, and complete the pull-request
template. The `Verify (Java 21)` job in the `CI` workflow must pass before
merging. Prefer a squash merge so each pull request becomes one clear change on
`main`.

Do not commit generated runtime directories, local modpack files, development
logs, temporary artwork, or compiled release JARs. GitHub Releases are the
distribution location for JARs.

## Releases

Prepare releases on a branch. Update the version, changelog, documentation, and
tests in the pull request. After it is merged and CI passes on `main`, create an
annotated `vX.Y.Z` tag from that merge commit and attach the verified JAR and
checksum file to the matching GitHub Release.

This project is published as All Rights Reserved. Contact the maintainer before
beginning a substantial external contribution.
