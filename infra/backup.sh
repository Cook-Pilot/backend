#!/bin/bash
# CookPilot DB 백업: pg_dump → gzip → S3 업로드, 최근 14개만 유지
set -euo pipefail
BUCKET="s3://cookpilot-backup-167403240280/db"
STAMP=$(date +%Y%m%d-%H%M%S)
FILE="/tmp/cookpilot-${STAMP}.sql.gz"

sudo docker exec cookpilot-db-1 pg_dump -U cookpilot -d cookpilot | gzip > "$FILE"
aws s3 cp "$FILE" "${BUCKET}/cookpilot-${STAMP}.sql.gz" --only-show-errors
rm -f "$FILE"

# 오래된 백업 정리 (최근 14개 초과분 삭제)
aws s3 ls "${BUCKET}/" | awk "{print \$4}" | sort | head -n -14 | while read -r old; do
  [ -n "$old" ] && aws s3 rm "${BUCKET}/${old}" --only-show-errors
done
echo "backup done: cookpilot-${STAMP}.sql.gz"
