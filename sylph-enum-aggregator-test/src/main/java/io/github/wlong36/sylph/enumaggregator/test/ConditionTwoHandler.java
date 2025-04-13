package io.github.wlong36.sylph.enumaggregator.test;

import io.github.wlong36.sylph.enumaggregator.generated.ConditionType;

public class ConditionTwoHandler implements ConditionHandler {
	@Override
	public ConditionType type() {
		return ConditionType.Two;
	}

	@Override
	public boolean isValid(Object value) {
		return false;
	}
}
