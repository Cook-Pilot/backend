# infra — 운영 서버 구성

운영 서버(AWS EC2)에 손으로 넣었던 설정을 코드로 옮긴 것이다. **서버가 날아가도 여기서 다시 만들 수 있고**, "지금 서버가 어떻게 구성돼 있는지"를 읽는 문서이기도 하다.

## 운영 환경 요약

| 항목 | 값 |
| --- | --- |
| EC2 | 서울(ap-northeast-2) t3.small, Ubuntu, EBS 30GB gp3 |
| 주소 | 탄력적 IP `13.209.243.235` (재부팅해도 유지) |
| 보안 그룹 | 22(관리자 IP만) / 80 / 443. **8080·5432 는 열지 않는다** |
| 접속 | SSH 키 또는 **AWS 콘솔 → EC2 → 연결 → Session Manager**(키·IP 불필요) |
| 컨테이너 | `app`(Spring) + `db`(Postgres 16) |
| 사진 버킷 | `cookpilot-photos-167403240280` (`review-photos/*` 만 공개 읽기) |
| 백업 버킷 | `cookpilot-backup-167403240280` (완전 비공개) |
| IAM 역할 | `cookpilot-ec2-ssm` (SSM + S3). **자격증명 키는 서버에 두지 않는다** |

## 파일

```
infra/
├── setup.sh                     새 서버를 운영 상태로 만드는 멱등 스크립트
├── backup.sh                    DB 백업(pg_dump → gzip → S3, 최근 14개 유지)
├── docker/daemon.json           도커 로그 로테이션(10MB × 3)
└── nginx/
    ├── cookpilot.conf           80 → 8080 리버스 프록시
    └── ratelimit.conf           업로드·익명발급 rate limit zone
```

## 새 서버 구축

```bash
# 1. EC2 생성 (위 "운영 환경 요약"대로) 후 접속
#    경로 고정: CI 의 SSM 배포가 /home/ubuntu/backend 를 pull 한다
git clone https://github.com/Cook-Pilot/backend.git ~/backend
cd ~/backend && ./infra/setup.sh
# 2. 스크립트가 안내하는 .env 생성 → 기동
```

**IAM 역할 연결을 잊지 말 것.** 인스턴스에 `cookpilot-ec2-ssm` 역할이 붙어 있어야 백업 업로드와 사진 업로드가 된다(자격증명 키 대신 역할을 쓴다). EC2 → 인스턴스 → 작업 → 보안 → IAM 역할 수정.

## 재해 복구 (서버 전체 소실)

1. 새 EC2 생성 → 위 "새 서버 구축" 실행 (**IAM 역할 연결 필수** — 백업을 못 읽는다)
2. `.env` 작성 — **`POSTGRES_PASSWORD` 는 아무 값이나 새로 만들어도 된다.** 덤프 복원은 비밀번호와 무관하다
3. **DB 만 먼저 띄운다.**

```bash
cd ~/cookpilot
sudo docker compose -f docker-compose.prod.yml up -d db
```

> ⚠️ **앱을 먼저 올리면 안 된다.** Flyway 가 빈 DB 에 스키마를 만들어 버려서, 이어지는 복원이
> `relation ... already exists` 로 전부 실패한다. 명령은 성공한 것처럼 끝나고 **데이터만 빠진
> 채로 서비스가 뜨기 때문에** 알아채기 어렵다(리허설에서 실제로 겪었다).

4. 복원한다.

```bash
# 최신 백업 파일명 확인
aws s3 ls s3://cookpilot-backup-167403240280/db/ | tail -1

# 복원 (스키마·데이터 모두 덤프에 들어 있다). 오류 0건이어야 한다.
aws s3 cp s3://cookpilot-backup-167403240280/db/<파일명> - \
  | gunzip \
  | sudo docker exec -i cookpilot-db-1 psql -U cookpilot -d cookpilot
```

5. 나머지를 기동한다. Flyway 는 복원된 스키마를 보고 `No migration necessary` 로 지나간다.

```bash
sudo docker compose -f docker-compose.prod.yml up -d
```

6. **탄력적 IP 를 새 인스턴스로 옮긴다** — EC2 → 탄력적 IP → 연결. 이러면 앱·DNS 설정을 바꿀 필요가 없다
7. 확인: `curl localhost:8080/actuator/health`, 그리고 **행 수가 운영과 맞는지**

```bash
sudo docker exec cookpilot-db-1 psql -U cookpilot -d cookpilot -t \
  -c "SELECT (SELECT count(*) FROM users), (SELECT count(*) FROM recipes);"
```

> 2026-08-13 새 EC2 로 전 과정을 실제로 돌려 검증했다(users 16 / recipes 8 일치, 외부 nginx·API 정상).
> 그때 발견한 두 가지를 이 문서와 `setup.sh` 에 반영했다 — 위 3번의 순서 문제와 백업 cron 등록 실패.

## 운영 메모

**설정을 바꿀 때는 이 디렉터리를 고치고 서버에 반영한다.** 서버에서 직접 고치면 다음 재구축 때 사라진다.

```bash
# 서버에 반영
sudo install -m 644 infra/nginx/cookpilot.conf /etc/nginx/sites-available/default
sudo nginx -t && sudo systemctl reload nginx
```

**배포는 main 머지로 한다.** CI 의 `deploy` 잡이 SSM 으로 서버에서 `git pull` + `compose up` 을 실행하므로, 이미지와 `docker-compose.prod.yml` 변경이 함께 반영된다. 수동 배포가 필요하면:

```bash
cd ~/backend && git pull && cp docker-compose.prod.yml ~/cookpilot/
cd ~/cookpilot && sudo docker compose -f docker-compose.prod.yml up -d
```

**필수 환경변수가 빠지면 배포가 멈춘다.** `docker-compose.prod.yml` 에서 `JWT_SECRET` 같은 필수 값은 `${VAR:?메시지}` 로 표시해 두었다. 빈 값을 주입하면(`${VAR:-}`) 앱은 "값이 있다"고 보고 기본값을 쓰지 않아 **컨테이너를 교체한 뒤에야 기동 실패로 드러난다**(실제로 운영 장애를 겪었다). `:?` 는 compose 가 파일을 읽는 시점에 멈추므로 기존 컨테이너가 살아 있는 채 배포만 실패한다. 새 필수 env 를 추가할 때는 **서버 `.env` 를 먼저 채우고 머지**한다.

**`.env` 를 바꾸면 재기동이 필요하다.** 서버에만 있는 파일이라 배포로는 반영되지 않는다(위 명령의 마지막 줄).

**로그 보기**

```bash
sudo docker logs -f cookpilot-app-1        # 앱
tail -f ~/cookpilot/backup.log             # 백업 결과
sudo tail -f /var/log/nginx/access.log     # 요청
```

**DB 를 IDE 로 보기** — 포트를 열지 않고 SSH 터널로만 접근한다.

```bash
ssh -i cookpilot-key.pem -L 5432:localhost:5432 ubuntu@13.209.243.235
# 그다음 IDE 에서 localhost:5432 로 접속
```

## 알려진 이슈

- **서버 시간대가 UTC** 다. 로그 시각과 cron(`0 19` = KST 04:00)을 볼 때 9시간 차이를 감안한다.
- **EBS 스냅샷은 설정하지 않았다.** DB 는 S3 백업으로 커버되고 서버 설정은 이 디렉터리로 재현 가능하다는 판단. 필요해지면 AWS Backup 을 켠다.
