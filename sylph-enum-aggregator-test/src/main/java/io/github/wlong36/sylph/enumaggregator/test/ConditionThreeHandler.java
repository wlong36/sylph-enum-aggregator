package io.github.wlong36.sylph.enumaggregator.test;

import proto.ConditionType;

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
