#!/bin/sh
# Builds and ships a release to the AWS stack that infra/terraform created:
#   1. builds the API image (linux/arm64, matching the Fargate tasks) and pushes it to ECR
#   2. builds the React app and syncs it to the site bucket, then clears CloudFront's cache
#   3. tells ECS to replace the running tasks with the new image (rolling; rolls back by itself if the new tasks are unhealthy)
#
#   scripts/deploy-aws.sh              everything
#   scripts/deploy-aws.sh api          backend only
#   scripts/deploy-aws.sh web          frontend only
#
# Needs: aws CLI signed in to the right account (aws sts get-caller-identity), docker, node/npm, terraform.
set -eu

WHAT="${1:-all}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TF="$ROOT/infra/terraform"
DOCKER="${DOCKER:-$(command -v docker || echo /Applications/Docker.app/Contents/Resources/bin/docker)}"

out() { terraform -chdir="$TF" output -raw "$1"; }

REGION="$(out region)"
REPO="$(out ecr_repository_url)"
CLUSTER="$(out ecs_cluster)"
SERVICE="$(out ecs_service)"
WEB_BUCKET="$(out frontend_bucket)"
DIST="$(out cloudfront_distribution_id)"
SHA="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || date +%s)"
export AWS_DEFAULT_REGION="$REGION"

deploy_api() {
  echo "==> Building API image ($SHA)"
  aws ecr get-login-password | "$DOCKER" login --username AWS --password-stdin "${REPO%%/*}"
  "$DOCKER" build --platform linux/arm64 -t "$REPO:$SHA" -t "$REPO:latest" "$ROOT"
  "$DOCKER" push "$REPO:$SHA"
  "$DOCKER" push "$REPO:latest"
  echo "==> Rolling out"
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" --force-new-deployment >/dev/null
  echo "    Watching the rollout (Ctrl-C is safe; it continues in AWS)..."
  aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICE"
  echo "    API is live on the new image."
}

deploy_web() {
  echo "==> Building the React app"
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
  echo "==> Uploading"
  # Hashed assets never change, so browsers may keep them for a year; index.html must always be re-checked.
  aws s3 sync "$ROOT/frontend/dist" "s3://$WEB_BUCKET" --delete \
    --exclude index.html --cache-control "public,max-age=31536000,immutable"
  aws s3 cp "$ROOT/frontend/dist/index.html" "s3://$WEB_BUCKET/index.html" \
    --cache-control "no-cache" --content-type "text/html; charset=utf-8"
  aws cloudfront create-invalidation --distribution-id "$DIST" --paths "/index.html" "/" >/dev/null
  echo "    Site updated."
}

case "$WHAT" in
  api) deploy_api ;;
  web) deploy_web ;;
  all) deploy_api; deploy_web ;;
  *) echo "usage: $0 [all|api|web]" >&2; exit 2 ;;
esac
echo "Done: $(out site_url)"
