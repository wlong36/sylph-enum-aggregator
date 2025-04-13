package io.github.wlong36.sylph.enumaggregator.test;

import io.github.wlong36.sylph.enumaggregator.generated.ConditionType;

/**
 * 条件处理接口
 *
 * @author wlong
 * @since 2025/4/12
 */
public interface ConditionHandler {

	ConditionType type();

	boolean isValid(Object value);

}
