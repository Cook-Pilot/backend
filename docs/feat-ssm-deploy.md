# feat/ssm-deploy — Watchtower 를 걷어내고 SSM 배포로 전환

## 무엇을, 왜

배포를 Watchtower(서버가 레지스트리를 폴링) 에서 **CI 가 SSM 으로 서버에 명령을 보내는 방식**으로 바꾼다.

**계기**: `containrrr/watchtower` 가 2025-12-17 아카이브됐다. 릴리스·버그픽스·**보안 업데이트가 영구 중단**이다. 8/7 배포 때 겪은 `client version 1.25 is too old`(Docker 29 API 비호환)가 그 여파였고, `DOCKER_API_VERSION` 을 고정하는 override 로 임시로 막아 둔 상태였다. **도커 소켓(`/var/run/docker.sock`)을 마운트하는 컴포넌트**라 패치가 끊긴 채로 두는 건 위험하다.

**부수 효과가 더 크다**: Watchtower 는 이미지만 교체하고 `docker-compose.prod.yml` 은 건드리지 않는다. 실제로 `PHOTOS_BUCKET` 을 추가했을 때 서버의 compose 파일이 낡아 환경변수가 먹지 않았다. 이번 전환으로 **설정 변경도 배포에 포함**된다.

## 핵심 설계 결정

1. **SSH 가 아니라 SSM.** SSH 방식(`appleboy/ssh-action`)은 ① SSH 개인키를 GitHub Secrets 에 저장하고 ② GitHub Actions 러너 IP 가 매번 달라 **22번 포트를 사실상 전면 개방**해야 한다. 지금 "SSH 는 관리자 IP 만" 정책을 되돌리게 된다. SSM 은 **키를 저장하지 않고**(OIDC 임시 자격증명) **포트를 열지 않는다**. 에이전트와 인스턴스 역할은 이미 있어 추가 비용이 없다.
2. **폴링이 애초에 우리 상황과 안 맞았다.** Watchtower 류는 *"남이 만든 이미지가 언제 갱신될지 모를 때"* 쓰는 도구다. 우리는 이미지를 직접 빌드하고 배포 시점(main 머지)도 안다 — 밀어 넣는 편이 맞다. 배포 지연도 최대 5분에서 즉시로 줄어든다.
3. **서버가 레포를 pull 한다.** 이미지 태그만 갱신하는 대신 `/home/ubuntu/backend` 를 `origin/main` 으로 맞추고 compose 를 복사한다. 이래야 설정 변경이 함께 반영된다. **경로가 계약**이므로 `infra/README.md` 와 `setup.sh` 에 고정으로 적었다.
4. **명령은 `runuser -l ubuntu` 로 실행한다.** SSM 은 root 로 돌아서 git 소유권 경고(`dubious ownership`)가 나고, docker 그룹 설정도 우회하게 된다.
5. **`deploy` 성공 ≠ 배포 성공.** 명령이 성공해도 새 컨테이너가 기동에 실패할 수 있어(운영 데이터에서만 깨지는 마이그레이션 등), 기존 `smoke` 잡을 `needs: deploy` 로 이어 붙여 **실물 리비전과 health 까지 확인**하고 워크플로를 닫는다.

## 변경 사항

- `.github/workflows/ci.yml`: `deploy` 잡 추가(OIDC → `aws ssm send-command` → 상태 폴링 → 서버 출력 표시), `smoke` 는 `needs: deploy`
- `docker-compose.prod.yml`: `watchtower` 서비스와 관련 라벨 제거
- `infra/docker-compose.override.yml`: 삭제(임시 조치 소멸)
- `.env.example`: `WATCHTOWER_INTERVAL` 제거
- `infra/setup.sh`, `infra/README.md`: 레포 경로 계약·수동 배포 절차 반영

## 필요한 사전 설정 (AWS 콘솔, 1회)

1. **GitHub OIDC 자격증명 공급자** — IAM → 자격 증명 공급자 → OpenID Connect
   - URL `https://token.actions.githubusercontent.com`, 대상 `sts.amazonaws.com`
2. **배포용 역할** `cookpilot-github-deploy`
   - 신뢰 정책: 위 공급자, `sub` 를 **`repo:Cook-Pilot/backend:ref:refs/heads/main`** 으로 제한(main 브랜치에서만 배포 가능)
   - 권한: 대상 인스턴스에 대한 `ssm:SendCommand`(+ `AWS-RunShellScript` 문서), `ssm:GetCommandInvocation`
3. **레포 변수**: `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `EC2_INSTANCE_ID`, `DEPLOY_BASE_URL`
4. **서버**: `/home/ubuntu/backend` 에 레포 clone

## 알려진 약점·후속

- **롤백 절차가 없다.** 배포 실패 시 이전 이미지로 되돌리려면 수동이다(`docker compose ... up -d` 로 이전 태그 지정). 커밋 SHA 태그가 GHCR 에 있으므로 절차만 문서화하면 된다.
- **무중단 배포가 아니다.** 컨테이너 교체 중 수십 초 끊긴다. 단일 인스턴스 구성의 한계로, 필요해지면 ALB + 다중 인스턴스로 간다.
- 배포 권한이 main 브랜치에 묶여 있어 **긴급 배포도 main 을 거쳐야 한다.** 의도한 제약이다.
