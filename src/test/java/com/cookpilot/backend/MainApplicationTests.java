package com.cookpilot.backend;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 전체 테스트 실행 진입점(JUnit Platform Suite).
 *
 * 이 클래스를 실행하면 com.cookpilot.backend 하위의 모든 테스트가 한 번에 돈다.
 * (IDE에서 원클릭으로 전체 스위트를 돌릴 때 쓴다.)
 *
 * 주의:
 *  - Testcontainers 를 쓰는 통합 테스트(CoreSchemaPersistenceTest)가 포함되므로
 *    이 스위트를 돌리려면 로컬에 Docker 데몬이 필요하다.
 *  - Gradle 의 test 태스크는 이미 개별 테스트를 전부 실행하므로 이 스위트를 제외한다
 *    (build.gradle 의 exclude 참고). 중복 실행 방지.
 */
@Suite
@SuiteDisplayName("CookPilot 전체 테스트")
@SelectPackages("com.cookpilot.backend")
class MainApplicationTests {
}
