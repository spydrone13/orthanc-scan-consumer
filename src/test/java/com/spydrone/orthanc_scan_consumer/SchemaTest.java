package com.spydrone.orthanc_scan_consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The generated schema. ddl-auto=update never changes an existing column, so a native ENUM type or a
 * CHECK constraint listing an enum's values would reject any value added to that enum later. Stored
 * enums are mapped as plain String fields (converted in their getters), since Hibernate adds the ENUM
 * type or CHECK constraint for any attribute it knows is an enum, even through an AttributeConverter.
 */
@DataJpaTest
class SchemaTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void noColumnIsANativeEnum() {
		List<String> enumColumns = jdbc.queryForList(
				"select table_name || '.' || column_name from information_schema.columns"
						+ " where table_schema = 'PUBLIC' and data_type = 'ENUM'",
				String.class);

		assertThat(enumColumns).isEmpty();
	}

	/** Other checks are fine, e.g. Hibernate's idx >= 0 on @OrderColumn tables. H2 stores lists as "S" IN('A', 'B'). */
	@Test
	void noCheckConstraintListsAllowedValues() {
		List<String> checks = jdbc.queryForList(
				"select check_clause from information_schema.check_constraints"
						+ " where constraint_schema = 'PUBLIC' and regexp_like(check_clause, ' IN *\\(', 'i')",
				String.class);

		assertThat(checks).isEmpty();
	}

	@Test
	void storedEnumsAreVarchar() {
		List<String> types = jdbc.queryForList(
				"select data_type from information_schema.columns where table_schema = 'PUBLIC'"
						+ " and (table_name, column_name) in (('LOT_STAGE_EVENTS', 'SCAN_TYPE'),"
						+ " ('LOT_STAGE_EVENTS', 'EXCEPTION'), ('SCANS', 'SCAN_TYPE'), ('LOTS', 'STATUS'))",
				String.class);

		assertThat(types).hasSize(4).containsOnly("CHARACTER VARYING");
	}
}
