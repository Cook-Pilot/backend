#!/usr/bin/env python3
"""PR 이 추가한 Flyway 마이그레이션이 base 브랜치의 최대 버전보다 큰지 검사한다.

Flyway 는 outOfOrder=false(스프링 기본)로 돈다. 이미 적용된 것보다 낮은 번호가
뒤늦게 들어오면 다음 기동이 validate 단계에서 죽는다. 이 레포는 main 푸시가 곧
배포(deploy 잡)라, 그 사고는 머지 직후 운영에서 터진다.

즉 마이그레이션 번호는 파일 이름이 아니라 **착지 순서에 대한 약속**이다.
브랜치를 여러 개 띄워두면 각자 그때의 최신 번호를 집기 때문에 반드시 부딪힌다 —
2026-08-18 에 세 브랜치가 동시에 V12 를 집었다(소셜 로그인 / 재료 그룹 / 카탈로그 정규화).

그래서 base 의 최대 버전보다 큰 번호만 통과시킨다. 같은 번호 중복도 이 규칙에 함께 걸린다.

CI 뿐 아니라 자기 머신에서 그대로 돌려볼 수 있다:
    python3 .github/scripts/check_migration_order.py origin/main
"""
import pathlib
import re
import subprocess
import sys

MIGRATION_DIR = "src/main/resources/db/migration"
# Flyway 버전은 '_' 를 '.' 로 읽는다. V4_1 => 4.1
FILENAME = re.compile(r"^V(\d+(?:_\d+)*)__.+\.sql$")


def version_of(filename):
    matched = FILENAME.match(filename)
    if matched is None:
        return None
    return tuple(int(part) for part in matched.group(1).split("_"))


def dotted(version):
    return ".".join(str(part) for part in version)


def names_on(ref):
    """base ref 의 마이그레이션 파일명. ref 를 못 찾으면 None (검사를 건너뛴다)."""
    listed = subprocess.run(
        ["git", "ls-tree", "--name-only", ref, f"{MIGRATION_DIR}/"],
        capture_output=True, text=True,
    )
    if listed.returncode != 0:
        return None
    return [line.rsplit("/", 1)[-1] for line in listed.stdout.splitlines() if line.strip()]


def names_here():
    directory = pathlib.Path(MIGRATION_DIR)
    if not directory.is_dir():
        return []
    return sorted(path.name for path in directory.iterdir() if path.is_file())


def fail(message, hint=None):
    print(f"::error::{message}")
    if hint:
        print(f"::error::{hint}")
    return 1


def main(argv):
    if len(argv) != 2:
        print(f"usage: {argv[0]} <base-ref>   (예: origin/main)", file=sys.stderr)
        return 2
    base_ref = argv[1]

    listed = names_on(base_ref)
    if listed is None:
        # 새 브랜치의 첫 푸시 등으로 비교 대상이 없는 경우. 막을 근거가 없으니 통과시킨다.
        print(f"::warning::base ref 를 찾을 수 없어 마이그레이션 순서 검사를 건너뜁니다: {base_ref}")
        return 0

    base = {}
    for name in listed:
        version = version_of(name)
        if version is not None:
            base[version] = name

    head = {}
    for name in names_here():
        version = version_of(name)
        if version is None:
            return fail(
                f"마이그레이션 파일 이름이 Flyway 규칙과 다릅니다: {name}",
                "V<버전>__<설명>.sql 형식이어야 합니다. 예: V15__add_recipe_tags.sql",
            )
        if version in head:
            return fail(
                f"같은 버전을 쓰는 마이그레이션이 이 브랜치 안에 둘 있습니다: "
                f"{head[version]} / {name}",
                "둘 중 하나의 번호를 올리세요.",
            )
        head[version] = name

    added = {v: n for v, n in head.items() if base.get(v) != n}
    if not added:
        print(f"마이그레이션 변경 없음 (base: {base_ref})")
        return 0

    if not base:
        print("base 에 마이그레이션이 없어 순서를 검사하지 않습니다.")
        return 0

    base_max = max(base)
    print(f"base({base_ref}) 최대 버전: {dotted(base_max)} ({base[base_max]})")

    problems = sorted(v for v in added if v <= base_max)
    for version in problems:
        taken = base.get(version)
        if taken:
            print(f"::error::V{dotted(version)} 는 이미 {base_ref} 가 쓰고 있습니다 "
                  f"({taken}). 이 브랜치의 {added[version]} 와 충돌합니다.")
        else:
            print(f"::error::{added[version]} (V{dotted(version)}) 가 base 최대 버전 "
                  f"V{dotted(base_max)} 보다 낮습니다. 먼저 배포된 뒤에 들어오면 "
                  f"Flyway 가 validate 에서 실패합니다.")

    if problems:
        nxt = (base_max[0] + 1,)
        return fail(
            "마이그레이션 번호가 base 보다 앞서지 않습니다.",
            f"V{dotted(nxt)} 이상으로 올리고, 파일 이름을 참조하는 문서·테스트도 함께 고치세요.",
        )

    for version in sorted(added):
        print(f"통과: {added[version]} (V{dotted(version)})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
