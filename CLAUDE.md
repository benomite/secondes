## Équipe multi-agents (plugin brigade)

The bold labels of the bindings below stay in French: Brigade looks them up by these exact words.

- **Branche d'intégration** : `main`
- **Worktrees** : `<parent of the repo>/secondes-wt/<n>-<slug>` — a sibling of the repo, the parent is computed (`dirname "$(git rev-parse --show-toplevel)"`)
- **Setup worktree** : `.claude/brigade/worktree-setup.sh <n> <WT>`
- **Gates** : `.claude/brigade/gates.sh <WT>`
- **Zones de fichiers** : app/ (single Android module: Kotlin + Jetpack Compose)
- **Dev local** : JDK 17 + Android SDK (platform 35) with ANDROID_HOME set; ./gradlew assembleDebug builds app/build/outputs/apk/debug/app-debug.apk, install with adb install -r
- **Doc vivante** : none
