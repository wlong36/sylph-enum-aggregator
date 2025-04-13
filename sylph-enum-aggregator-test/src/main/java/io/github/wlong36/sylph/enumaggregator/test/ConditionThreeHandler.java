package io.github.wlong36.sylph.enumaggregator.test;

import io.github.wlong36.sylph.enumaggregator.generated.ConditionType;

public class ConditionThreeHandler implements ConditionHandler{
	@Override
	public ConditionType type() {
		return ConditionType.Three;
	}

	@Override
	public boolean isValid(Object value) {
		return false;
	}
}
