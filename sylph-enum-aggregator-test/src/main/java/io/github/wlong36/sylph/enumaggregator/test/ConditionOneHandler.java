package io.github.wlong36.sylph.enumaggregator.test;

import proto.ConditionType;

public class ConditionOneHandler implements ConditionHandler {
	@Override
	public ConditionType type() {
		return ConditionType.One;
	}

	@Override
	public boolean isValid(Object value) {
		return true;
	}
}
