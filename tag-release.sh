#!/usr/bin/env bash
set -euo pipefail

FORCE=false
VERSION=""

usage() {
  echo "Usage: ./tag-release.sh [-f] <version>"
  echo ""
  echo "Sets the project version, commits, and creates a git tag."
  echo ""
  echo "Arguments:"
  echo "  version    Release version (e.g. 0.2.0). Must not contain SNAPSHOT."
  echo ""
  echo "Options:"
  echo "  -f         Force update the tag if it already exists"
  echo "  -h         Show this help message"
  echo ""
  echo "Examples:"
  echo "  ./tag-release.sh 0.2.0"
  echo "  ./tag-release.sh -f 0.2.0    # overwrite existing v0.2.0 tag"
}

while getopts ":fh" opt; do
  case $opt in
    f) FORCE=true ;;
    h) usage; exit 0 ;;
    *) echo "Error: Unknown option -$OPTARG"; echo ""; usage; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

VERSION="${1:-}"

if [ -z "$VERSION" ]; then
  echo "Error: Version argument is required"
  echo ""
  usage
  exit 1
fi

if [[ "$VERSION" == *SNAPSHOT* ]]; then
  echo "Error: Release version must not contain SNAPSHOT"
  exit 1
fi

# Ensure working tree is clean
if [ -n "$(git status --porcelain)" ]; then
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

# Update preflight-core/build.gradle.kts
sed -i "s/^val PROJECT_VERSION = \".*\"/val PROJECT_VERSION = \"$VERSION\"/" preflight-core/build.gradle.kts

# Update preflight-spec/build.gradle.kts
sed -i "s/^version = \".*\"/version = \"$VERSION\"/" preflight-spec/build.gradle.kts

echo "Updated preflight-core/build.gradle.kts"
echo "Updated preflight-spec/build.gradle.kts"

git add preflight-core/build.gradle.kts preflight-spec/build.gradle.kts
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
