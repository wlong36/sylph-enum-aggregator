package io.github.wlong36.sylph.enumaggregator.runtime;

import io.github.wlong36.sylph.enumaggregator.api.SylphEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class SylphEnumsTest {

	private SylphEnums sylphEnums;

	@BeforeEach
	public void setUp() {
		sylphEnums = new SylphEnums();
	}

	@Test
	public void register_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.register(null));
	}

	@Test
	public void register_ValidEnumType_RegistersSuccessfully() {
		sylphEnums.register(SampleEnum.class);
		Optional<SylphEnumDefinition> definition = sylphEnums.findDefinition("SampleEnum");
		assertTrue(definition.isPresent());
		assertEquals(SampleEnum.class, definition.get().getEnumType());
	}

	@Test
	public void findDefinition_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.findDefinition(null));
	}

	@Test
	public void findDefinition_UnregisteredEnumType_ReturnsEmpty() {
		Optional<SylphEnumDefinition> definition = sylphEnums.findDefinition("SampleEnum");
		assertFalse(definition.isPresent());
	}

	@Test
	public void getByOrdinal_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByOrdinal(null, 0));
	}

	@Test
	public void getByOrdinal_UnregisteredEnumType_NullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByOrdinal("SampleEnum", 0));
	}

	@Test
	public void getByOrdinal_ValidOrdinal_ReturnsEnum() {
		sylphEnums.register(SampleEnum.class);
		SylphEnum enumValue = sylphEnums.getByOrdinal("SampleEnum", 0);
		assertEquals(SampleEnum.VALUE1, enumValue);
	}

	@Test
	public void getByOrdinal_InvalidOrdinal_ThrowsIndexOutOfBoundsException() {
		sylphEnums.register(SampleEnum.class);
		assertThrows(IndexOutOfBoundsException.class, () -> sylphEnums.getByOrdinal("SampleEnum", 2));
	}

	@Test
	public void getById_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getById(null, 1));
	}

	@Test
	public void getById_UnregisteredEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getById("SampleEnum", 1));
	}

	@Test
	public void getById_ValidId_ReturnsEnum() {
		sylphEnums.register(SampleEnum.class);
		SylphEnum enumValue = sylphEnums.getById("SampleEnum", 1);
		assertEquals(SampleEnum.VALUE1, enumValue);
	}

	@Test
	public void getById_InvalidId_ThrowsNoSuchElementException() {
		sylphEnums.register(SampleEnum.class);
		assertThrows(NoSuchElementException.class, () -> sylphEnums.getById("SampleEnum", 3));
	}

	@Test
	public void getByIdOrNull_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByIdOrNull(null, 1));
	}

	@Test
	public void getByIdOrNull_UnregisteredEnumType_ReturnsNull() {
		assertNull(sylphEnums.getByIdOrNull("SampleEnum", 1));
	}

	@Test
	public void getByIdOrNull_ValidId_ReturnsEnum() {
		sylphEnums.register(SampleEnum.class);
		SylphEnum enumValue = sylphEnums.getByIdOrNull("SampleEnum", 1);
		assertEquals(SampleEnum.VALUE1, enumValue);
	}

	@Test
	public void getByIdOrNull_InvalidId_ReturnsNull() {
		sylphEnums.register(SampleEnum.class);
		assertNull(sylphEnums.getByIdOrNull("SampleEnum", 3));
	}

	@Test
	public void getByName_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByName(null, "VALUE1"));
	}

	@Test
	public void getByName_UnregisteredEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByName("SampleEnum", "VALUE1"));
	}

	@Test
	public void getByName_ValidName_ReturnsEnum() {
		sylphEnums.register(SampleEnum.class);
		SylphEnum enumValue = sylphEnums.getByName("SampleEnum", "VALUE1");
		assertEquals(SampleEnum.VALUE1, enumValue);
	}

	@Test
	public void getByName_InvalidName_ThrowsNoSuchElementException() {
		sylphEnums.register(SampleEnum.class);
		assertThrows(NoSuchElementException.class, () -> sylphEnums.getByName("SampleEnum", "INVALID"));
	}

	@Test
	public void getByNameOrNull_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getByNameOrNull(null, "VALUE1"));
	}

	@Test
	public void getByNameOrNull_UnregisteredEnumType_ReturnsNull() {
		assertNull(sylphEnums.getByNameOrNull("SampleEnum", "VALUE1"));
	}

	@Test
	public void getByNameOrNull_ValidName_ReturnsEnum() {
		sylphEnums.register(SampleEnum.class);
		SylphEnum enumValue = sylphEnums.getByNameOrNull("SampleEnum", "VALUE1");
		assertEquals(SampleEnum.VALUE1, enumValue);
	}

	@Test
	public void getByNameOrNull_InvalidName_ReturnsNull() {
		sylphEnums.register(SampleEnum.class);
		assertNull(sylphEnums.getByNameOrNull("SampleEnum", "INVALID"));
	}

	@Test
	public void getAll_NullEnumType_ThrowsNullPointerException() {
		assertThrows(NullPointerException.class, () -> sylphEnums.getAll(null));
	}

	@Test
	public void getAll_UnregisteredEnumType_ReturnsEmptyList() {
		assertTrue(sylphEnums.getAll("SampleEnum").isEmpty());
	}

	@Test
	public void getAll_ValidEnumType_ReturnsAllEnums() {
		sylphEnums.register(SampleEnum.class);
		List<SylphEnum> allEnums = sylphEnums.getAll("SampleEnum");
		assertEquals(2, allEnums.size());
		assertTrue(allEnums.contains(SampleEnum.VALUE1));
		assertTrue(allEnums.contains(SampleEnum.VALUE2));
	}

	// 用于测试的示例枚举
	private enum SampleEnum implements SylphEnum {
		VALUE1(1),
		VALUE2(2);

		private final int id;

		SampleEnum(int id) {
			this.id = id;
		}

		@Override
		public int id() {
			return id;
		}

	}

}
