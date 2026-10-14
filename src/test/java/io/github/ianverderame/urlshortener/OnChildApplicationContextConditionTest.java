package io.github.ianverderame.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ConditionContext;

class OnChildApplicationContextConditionTest {

  private final OnChildApplicationContextCondition condition =
      new OnChildApplicationContextCondition();

  @Test
  void matchesWhenTheBeanFactoryHasAParent() {
    DefaultListableBeanFactory parent = new DefaultListableBeanFactory();
    DefaultListableBeanFactory child = new DefaultListableBeanFactory(parent);

    assertThat(condition.matches(contextWith(child), null)).isTrue();
  }

  @Test
  void doesNotMatchWhenTheBeanFactoryHasNoParent() {
    DefaultListableBeanFactory root = new DefaultListableBeanFactory();

    assertThat(condition.matches(contextWith(root), null)).isFalse();
  }

  private static ConditionContext contextWith(DefaultListableBeanFactory beanFactory) {
    ConditionContext context = mock(ConditionContext.class);
    when(context.getBeanFactory()).thenReturn(beanFactory);
    return context;
  }
}
