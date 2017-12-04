package org.activiti.services.query.app.repository.es;

import com.querydsl.core.types.dsl.StringPath;
import org.activiti.services.query.app.model.es.ProcessInstance;
import org.activiti.services.query.app.model.QProcessInstance;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;

public interface ProcessInstanceRepositoryES extends ElasticsearchRepository
<ProcessInstance, Long>, QuerydslPredicateExecutor<ProcessInstance>, 
QuerydslBinderCustomizer<QProcessInstance> {

    @Override
    default void customize(QuerydslBindings bindings,
                           QProcessInstance root) {

        bindings.bind(String.class).first(
                                          (StringPath path, String value) -> path.eq(value));
        bindings.bind(root.lastModifiedFrom).first((path, value) -> root.lastModified.after(value));
        bindings.bind(root.lastModifiedTo).first((path, value) -> root.lastModified.before(value));
    }

}
