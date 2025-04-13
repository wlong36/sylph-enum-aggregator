package io.github.wlong36.sylph.enumaggregator.api;

import java.lang.annotation.*;

/**
 * SylphEnumReg 重复配置支持
 *
 * @author wlong
 * @since 2025/4/12
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface SylphEnumRegs {

	SylphEnumReg[] value();
}
