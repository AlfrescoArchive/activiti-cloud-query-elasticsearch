package org.activiti.services.query.app.repository.es;

import com.querydsl.core.types.dsl.StringPath;
import org.activiti.services.query.app.model.QTask;
import org.activiti.services.query.app.model.es.Task;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;

public interface TaskRepositoryES extends ElasticsearchRepository<Task, String>, QuerydslPredicateExecutor<Task>, QuerydslBinderCustomizer<QTask> {

    @Override
    default public void customize(QuerydslBindings bindings, QTask root) {

        bindings.bind(String.class).first(
                                          (StringPath path, String value) -> path.eq(value));
        bindings.bind(root.lastModifiedFrom).first((path, value) -> root.lastModified.after(value));
        bindings.bind(root.lastModifiedTo).first((path, value) -> root.lastModified.before(value));
        bindings.bind(root.nameLike).first((path, value) -> root.name.contains(value));
    }
}