# Worktree Setup

This repo is set up to support parallel development with `git worktree` and per-worktree environment overrides.

## Create a worktree

```bash
git worktree add ../AstraAssistant-feature feature/your-branch
git worktree add ../AstraAssistant-fix fix/your-fix
```

## Per-worktree environment

We use `direnv` to avoid global environment drift.

1. Copy the example overrides:

```bash
cp .envrc.local.example .envrc.local
```

2. Edit `.envrc.local` for your machine (JDK + Android SDK paths). Example:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
```
3. Allow the environment:

```bash
direnv allow
```

If `direnv` isn’t installed, install it and hook it into your shell:

```bash
brew install direnv
echo 'eval \"$(direnv hook zsh)\"' >> ~/.zshrc
```

## Android SDK path

Each worktree should have its own `local.properties` with:

```
sdk.dir=/absolute/path/to/Android/sdk
```

(Do not commit `local.properties`.)

## JDK

This repo targets JDK 21 (OpenJDK 21.x.x). Make sure Android Studio and CLI use the same JDK path.
