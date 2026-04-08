#!/usr/bin/env bash
set -euo pipefail

FORCE=false
VERSION=""
VERSION_ONLY=false

usage() {
  echo "Usage: ./tag-release.sh [-f] [-v] <version>"
  echo ""
  echo "Sets the project version, commits, and creates a git tag."
  echo ""
  echo "Arguments:"
  echo "  version    Release version (e.g. 0.2.0)"
  echo ""
  echo "Options:"
  echo "  -f         Force update the tag if it already exists"
  echo "  -v         Version only: update version numbers without committing or tagging."
  echo "             If no version is given, prints the current version."
  echo "  -h         Show this help message"
  echo ""
  echo "Examples:"
  echo "  ./tag-release.sh 0.2.0"
  echo "  ./tag-release.sh -f 0.2.0    # overwrite existing v0.2.0 tag"
  echo "  ./tag-release.sh -v                  # print current version"
  echo "  ./tag-release.sh -v 0.3.0-SNAPSHOT  # just set versions, no commit/tag"
}

while getopts ":fvh" opt; do
  case $opt in
    f) FORCE=true ;;
    v) VERSION_ONLY=true ;;
    h) usage; exit 0 ;;
    *) echo "Error: Unknown option -$OPTARG"; echo ""; usage; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

VERSION="${1:-}"

if [ -z "$VERSION" ]; then
  if [ "$VERSION_ONLY" = true ]; then
    # No version provided with -v: print the current version and exit
    CURRENT_VERSION=$(cat version.txt | tr -d '[:space:]')
    echo "$CURRENT_VERSION"
    exit 0
  fi
  echo "Error: Version argument is required"
  echo ""
  usage
  exit 1
fi

if [ "$VERSION_ONLY" = false ] && [[ "$VERSION" == *SNAPSHOT* ]]; then
  echo "Error: Release version must not contain SNAPSHOT (use -v for version-only mode)"
  exit 1
fi

# Update version.txt (single source of truth)
echo "$VERSION" > version.txt

echo "Updated version.txt"

if [ "$VERSION_ONLY" = true ]; then
  echo ""
  echo "Version set to $VERSION (no commit or tag created)."
  exit 0
fi

# Ensure working tree is clean (aside from the version files we just changed)
if [ -n "$(git status --porcelain -- ':!version.txt')" ]; then
  echo "Error: Working tree is not clean. Commit or stash changes first."
  exit 1
fi

TAG="v${VERSION}"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  if [ "$FORCE" = true ]; then
    echo "Warning: Tag $TAG already exists, will be overwritten (-f)"
  else
    echo "Error: Tag $TAG already exists (use -f to overwrite)"
    exit 1
  fi
fi

echo "Releasing version $VERSION..."

git add version.txt
git commit -m "Release $VERSION"

if [ "$FORCE" = true ]; then
  git tag -f "$TAG"
else
  git tag "$TAG"
fi

echo ""
echo "Release $VERSION complete."
echo "  Commit: $(git rev-parse --short HEAD)"
echo "  Tag:    $TAG"
echo ""
echo "To publish:"
if [ "$FORCE" = true ]; then
  echo "  git push origin main $TAG --force"
else
  echo "  git push origin main $TAG"
fi
