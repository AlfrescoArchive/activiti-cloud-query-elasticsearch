package org.activiti.services.query.app.repository.es;

import com.querydsl.core.types.dsl.StringPath;
import org.activiti.services.query.app.model.QVariable;
import org.activiti.services.query.app.model.es.Variable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;

public interface VariableRepositoryES extends ElasticsearchRepository<Variable, String>, QuerydslPredicateExecutor<Variable>, QuerydslBinderCustomizer<QVariable> {

    @Override
    default public void customize(QuerydslBindings bindings, QVariable root) {

        bindings.bind(String.class).first(
                                          (StringPath path, String value) -> path.eq(value));

    }
}