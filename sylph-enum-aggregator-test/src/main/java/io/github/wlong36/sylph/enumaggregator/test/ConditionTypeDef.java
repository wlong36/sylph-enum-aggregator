package io.github.wlong36.sylph.enumaggregator.test;

import io.github.wlong36.sylph.enumaggregator.api.SylphEnumReg;

@SylphEnumReg(type = "ConditionType", id = 1, name = "One", desc = "条件1")
@SylphEnumReg(type = "ConditionType", id = 2, name = "Two", desc = "条件2")
@SylphEnumReg(type = "ConditionType", id = 3, name = "Three", desc = "条件3")

@SylphEnumReg(type = "TargetType", id = 1, name = "TargetOne", desc = "目标1")
public interface ConditionTypeDef {
}
