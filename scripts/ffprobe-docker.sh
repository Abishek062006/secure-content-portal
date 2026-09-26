#!/bin/sh
# Runs ffmpeg (or ffprobe, if this file is called ffprobe) inside Docker, for machines without ffmpeg installed.
# Point the app at it:   FFMPEG_PATH=$PWD/scripts/ffmpeg-docker.sh   FFPROBE_PATH=$PWD/scripts/ffprobe-docker.sh
# Files are shared at the same path inside the container, so the app's temp folders just work.
TOOL=ffmpeg
case "$(basename "$0")" in ffprobe*) TOOL=ffprobe ;; esac
DOCKER="${DOCKER:-$(command -v docker || echo /Applications/Docker.app/Contents/Resources/bin/docker)}"
exec "$DOCKER" run --rm -i --platform linux/amd64 -v /private/tmp:/private/tmp -v /tmp:/tmp -v /var/folders:/var/folders \
  -v "$PWD":"$PWD" -w "$PWD" --entrypoint "$TOOL" jrottenberg/ffmpeg:7.1-alpine "$@"
