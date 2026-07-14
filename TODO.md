# TODO

CI/CD 브랜치(`feat/ci-cd-setting`) 머지 이후에 해야 하는 작업들.

---

## 1. 머지 직후 — 배포 파이프라인 활성화

순서가 중요하다. 이미지가 GHCR에 한 번 올라가야 그 다음 단계가 가능하다.

- [ ] **PR 머지** → CI의 `docker` 잡이 `ghcr.io/cook-pilot/backend:latest` 푸시
- [ ] **GHCR 패키지 public 전환**
  - `https://github.com/orgs/Cook-Pilot/packages` → `backend` → Package settings
  - Danger Zone → Change visibility → Public
  - ※ 이미지가 푸시되기 전에는 패키지 자체가 없어서 이 화면이 안 나온다
  - 확인: `docker manifest inspect ghcr.io/cook-pilot/backend:latest` (로그인 없이 성공해야 함)
- [ ] **VPS 세팅** (ARM64)
  ```bash
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker $USER      # 재로그인 필요
  git clone https://github.com/Cook-Pilot/backend.git && cd backend
  cp .env.example .env
  sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -base64 24)|" .env
  chmod 600 .env
  docker compose -f docker-compose.prod.yml up -d
  ```
- [ ] **검증**
  - `docker compose -f docker-compose.prod.yml ps` → app/db/watchtower 전부 Up, db는 healthy
  - `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`
  - `docker inspect ghcr.io/cook-pilot/backend:latest --format '{{.Architecture}}'` → `arm64`
  - main에 커밋 하나 푸시 → 5분 내 app 컨테이너 자동 교체되는지 확인 (파이프라인 전체 검증)

---

## 2. DB 실연결 (가장 큰 블로커)

**현재 `spring.datasource` 설정은 동작하지 않는다.** `spring-boot-starter-jdbc` / `data-jpa`가
클래스패스에 없어서 `DataSourceAutoConfiguration`이 아예 안 뜬다. `DB_URL`을 완벽히 주입해도
Spring이 무시하며, 앱은 여전히 서비스 내 인메모리 저장으로 동작한다.

- [ ] `build.gradle`에 `spring-boot-starter-data-jpa` 추가
- [ ] Entity / Repository 계층 작성 (현재 각 서비스 안의 인메모리 Map을 대체)
- [ ] 스키마 마이그레이션 도구 도입 (Flyway 권장 — `ddl-auto`를 프로덕션에서 쓰지 않기 위해)
- [ ] 연결 확인: 로그에 HikariPool 생성이 찍히는지
  ```bash
  docker compose -f docker-compose.prod.yml logs app | grep -i hikari
  ```

---

## 3. 보안 / 운영 (실사용자 붙기 전)

- [ ] **8080 포트가 인터넷에 그대로 노출됨.** 방화벽(`ufw`) 또는 리버스 프록시로 차단
- [ ] **TLS 없음.** Caddy 또는 Nginx + Let's Encrypt로 HTTPS 종단
- [ ] **API 인증 없음.** 현재 고정 목유저 1명(`00000000-...-0001`)으로 동작
- [ ] DB 백업 전략 (현재 `pgdata` 볼륨만 있고 백업 없음 — VPS 날아가면 전부 소실)
- [ ] Watchtower가 `/var/run/docker.sock`을 마운트한다 = 호스트 root 권한과 동등.
      감시 대상은 `--label-enable`로 app 컨테이너 하나로 좁혀둔 상태이나, 리스크는 인지할 것

---

## 4. 나중에 (AWS 이전 시)

- 이미지가 multi-arch(`linux/amd64,linux/arm64`)라 **워크플로/compose 변경 없이 그대로 동작한다.**
  EC2 t시리즈(amd64)든 Graviton(arm64)이든 pull 시 호스트가 맞는 변형을 자동 선택한다.
- 관리형 DB(RDS/Neon)로 옮길 경우: `docker-compose.prod.yml`에서 `db` 서비스를 지우고
  `.env`의 `DB_URL`만 외부 엔드포인트로 교체하면 된다. 앱 이미지는 재빌드 불필요.

---

## 5. AI 파트

- [ ] 현재 AI 피드백은 목데이터. 실제 모델 연동 필요 (`docs/08_mvp_api.md` 참조)
