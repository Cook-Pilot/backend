#!/bin/bash
# SSM 배포가 서버에서 실행하는 스크립트. CI 는 이 파일을 호출만 한다.
#
# 배포 절차를 워크플로 YAML 안에 문자열로 넣었더니 줄바꿈이 리터럴 "\n" 으로 전달돼
# 서버 bash 가 문법 오류를 냈다(#55 머지 직후 첫 배포 실패). 스크립트로 빼면
# 이스케이프 문제가 사라지고, 배포 절차를 코드 리뷰로 볼 수 있다.
#
# 레포 갱신(git pull)은 CI 가 이 스크립트를 부르기 전에 따로 한다 —
# 실행 중인 스크립트 파일이 자기 자신을 바꾸면 bash 가 엉뚱한 지점을 읽을 수 있다.
set -euo pipefail

REPO_DIR=/home/ubuntu/backend
APP_DIR=/home/ubuntu/cookpilot

cp "$REPO_DIR/docker-compose.prod.yml" "$APP_DIR/"
cd "$APP_DIR"

docker compose -f docker-compose.prod.yml pull --quiet app
# --remove-orphans: compose 에서 사라진 서비스의 컨테이너(watchtower)를 정리한다.
# 프로젝트(cookpilot) 범위라 다른 스택(web-preview 등)은 건드리지 않는다.
docker compose -f docker-compose.prod.yml up -d --remove-orphans

echo "deployed: $(git -C "$REPO_DIR" rev-parse --short HEAD)"
