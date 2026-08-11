#!/bin/bash
# 새 EC2(Ubuntu)를 운영 서버 상태로 만든다. 여러 번 실행해도 안전하다(멱등).
#
#   sudo 가 필요한 명령이 있으므로 sudo 가 되는 계정으로 실행한다.
#   실행:  cd ~/backend && ./infra/setup.sh
#
# 이 스크립트가 하지 않는 것:
#   - .env 생성(비밀번호가 들어가므로 사람이 만든다. 아래 안내가 나온다)
#   - 컨테이너 기동(설정을 확인한 뒤 사람이 올린다)
#   - DB 복원(infra/README.md 의 복구 절차 참고)
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$HOME/cookpilot"

echo "== 1/6 Docker 설치 =="
if ! command -v docker > /dev/null; then
	sudo apt-get update -qq
	sudo apt-get install -y -qq docker.io docker-compose-v2
	sudo usermod -aG docker "$USER"
	echo "   설치 완료. (그룹 반영은 재로그인 후 — 그전까지는 sudo docker 로 쓴다)"
else
	echo "   이미 설치됨: $(docker --version)"
fi

echo "== 2/6 도커 로그 로테이션 =="
# 기본값은 무제한이라 몇 달이면 디스크를 채운다. daemon.json 은 기존 컨테이너에
# 소급 적용되지 않으므로, 이미 도는 컨테이너가 있으면 재생성해야 한다(아래 안내).
sudo install -m 644 "$REPO_DIR/infra/docker/daemon.json" /etc/docker/daemon.json
sudo systemctl restart docker
echo "   적용: 컨테이너당 10MB x 3"

echo "== 3/6 스왑 2GB =="
# RAM 2GB 인스턴스에서 Spring+Postgres 는 빠듯하다. 스왑이 없으면 스파이크에
# OOM Killer 가 컨테이너를 죽인다. swappiness=10 = 정말 급할 때만 사용.
if ! sudo swapon --show | grep -q swapfile; then
	sudo fallocate -l 2G /swapfile
	sudo chmod 600 /swapfile
	sudo mkswap /swapfile > /dev/null
	sudo swapon /swapfile
	grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
	echo "   생성 완료"
else
	echo "   이미 있음"
fi
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf > /dev/null
sudo sysctl -q -w vm.swappiness=10

echo "== 4/6 nginx (리버스 프록시 + rate limit) =="
sudo apt-get install -y -qq nginx
sudo install -m 644 "$REPO_DIR/infra/nginx/ratelimit.conf" /etc/nginx/conf.d/ratelimit.conf
sudo install -m 644 "$REPO_DIR/infra/nginx/cookpilot.conf" /etc/nginx/sites-available/default
sudo nginx -t
sudo systemctl enable --now nginx > /dev/null
sudo systemctl reload nginx
echo "   80 -> 8080 프록시, 업로드/익명발급 rate limit 적용"

echo "== 5/6 AWS CLI (DB 백업 업로드용) =="
if ! command -v aws > /dev/null; then
	sudo snap install aws-cli --classic > /dev/null
	echo "   설치 완료"
else
	echo "   이미 설치됨: $(aws --version 2>&1 | head -1)"
fi
# 자격증명은 설정하지 않는다 — EC2 인스턴스 역할(S3 권한 필요)이 자동 주입한다.

echo "== 6/6 앱 디렉터리와 백업 cron =="
mkdir -p "$APP_DIR"
cp "$REPO_DIR/docker-compose.prod.yml" "$APP_DIR/"
cp "$REPO_DIR/infra/docker-compose.override.yml" "$APP_DIR/"
install -m 755 "$REPO_DIR/infra/backup.sh" "$APP_DIR/backup.sh"
# 19:00 UTC = 04:00 KST. 서버 시간대는 UTC 다.
CRON_LINE="0 19 * * * $APP_DIR/backup.sh >> $APP_DIR/backup.log 2>&1"
( crontab -l 2>/dev/null | grep -v 'backup.sh' ; echo "$CRON_LINE" ) | crontab -
echo "   $APP_DIR 구성 완료, 백업 cron 등록(매일 04:00 KST)"

echo
echo "================ 남은 수동 작업 ================"
if [ -f "$APP_DIR/.env" ]; then
	echo "  .env 있음 — 값 확인만 하면 된다."
else
	echo "  1) .env 생성:"
	echo "       cp $REPO_DIR/.env.example $APP_DIR/.env && chmod 600 $APP_DIR/.env"
	echo "     POSTGRES_PASSWORD 는 새로 만든다:  openssl rand -base64 24"
	echo "     PHOTOS_BUCKET 은 .env.example 주석의 운영 버킷명을 넣는다."
	echo "     기존 DB 를 복원할 거라면 비밀번호는 그때 쓰던 값이어야 한다."
fi
echo "  2) 기동:"
echo "       cd $APP_DIR && sudo docker compose -f docker-compose.prod.yml -f docker-compose.override.yml up -d"
echo "  3) 확인:  curl localhost:8080/actuator/health"
echo "  4) DB 복원이 필요하면 infra/README.md 의 '재해 복구' 절 참고"
echo "==============================================="
