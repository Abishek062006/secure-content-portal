#!/bin/sh
# Copies the files in ./local-storage into the docker `s3` bucket, so courses uploaded while STORAGE_PROVIDER=local
# keep working after you switch to STORAGE_PROVIDER=s3. Creates the bucket if needed; safe to run more than once.
#   scripts/copy-local-storage-to-s3.sh            (bucket "content")
#   scripts/copy-local-storage-to-s3.sh my-bucket
BUCKET="${1:-${STORAGE_BUCKET:-content}}"
DOCKER="${DOCKER:-$(command -v docker || echo /Applications/Docker.app/Contents/Resources/bin/docker)}"
aws() {
  "$DOCKER" run --rm --network host -v "$PWD/local-storage":/data:ro \
    -e AWS_ACCESS_KEY_ID="${STORAGE_ACCESS_KEY:-minioadmin}" -e AWS_SECRET_ACCESS_KEY="${STORAGE_SECRET_KEY:-minioadmin}" \
    -e AWS_DEFAULT_REGION=us-east-1 amazon/aws-cli --endpoint-url "${STORAGE_ENDPOINT:-http://localhost:8333}" "$@"
}
aws s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1 || aws s3 mb "s3://$BUCKET"
aws s3 sync /data "s3://$BUCKET"
echo "Copied local-storage to s3://$BUCKET"
