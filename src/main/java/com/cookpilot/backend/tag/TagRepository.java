package com.cookpilot.backend.tag;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, String> {

	List<TagEntity> findByActiveTrueOrderBySortOrderAscCodeAsc();

	/** 요청한 코드 중 사전에 실제로 있는 것만. 오타를 400 으로 돌려주기 위해 쓴다. */
	@Query("SELECT tag.code FROM TagEntity tag WHERE tag.code IN :codes")
	List<String> findExistingCodes(@Param("codes") Collection<String> codes);

	/**
	 * 요청한 코드가 걸친 축의 수.
	 *
	 * 필터 규칙이 "축 안은 OR, 축 사이는 AND" 라, 레시피가 만족해야 할 축의 개수가
	 * 곧 이 값이다. 클라이언트가 축을 몰라도 되도록 서버가 센다.
	 */
	@Query("SELECT COUNT(DISTINCT tag.axisCode) FROM TagEntity tag WHERE tag.code IN :codes")
	long countAxes(@Param("codes") Collection<String> codes);
}
