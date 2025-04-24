package io.github.wlong36.sylph.enumaggregator.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import proto.ConditionType;

import java.util.Map;

class ConditionHandlerTest {

	@Test
	void test() {
		Assertions.assertDoesNotThrow(() -> {
			Map<ConditionType, ConditionHandler> handlerMap = Map.of(
					ConditionType.One, new ConditionOneHandler(),
					ConditionType.Two, new ConditionTwoHandler(),
					ConditionType.Three, new ConditionThreeHandler());
		});
	}

}