package io.github.ianverderame.urlshortener;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches only in a child {@code ApplicationContext} (one with a parent bean factory), such as the
 * management context when it runs on a separate port. {@code @ManagementContextConfiguration}
 * classes are themselves {@code @Configuration}-annotated, so without this guard, the main
 * application's own classpath component scan also finds them (they live in the same scanned
 * package) and registers a second, independent copy of their beans in the main context.
 */
class OnChildApplicationContextCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    BeanFactory beanFactory = context.getBeanFactory();
    return beanFactory instanceof HierarchicalBeanFactory hierarchical
        && hierarchical.getParentBeanFactory() != null;
  }
}
