/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.cloud.services.query.events.handlers;

import java.util.Date;

import org.activiti.api.task.model.events.TaskRuntimeEvent;
import org.activiti.cloud.api.model.shared.events.CloudRuntimeEvent;
import org.activiti.cloud.api.task.model.events.CloudTaskSuspendedEvent;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.QueryException;
import org.activiti.cloud.services.query.model.elastic.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TaskSuspendedEventHandler implements QueryEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskSuspendedEventHandler.class);

    private final TaskRepository taskRepository;

    @Autowired
    public TaskSuspendedEventHandler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void handle(CloudRuntimeEvent<?, ?> event) {
        CloudTaskSuspendedEvent taskSuspendedEvent = (CloudTaskSuspendedEvent) event;
        org.activiti.api.task.model.Task eventTask = taskSuspendedEvent.getEntity();
        LOGGER.debug("Handling suspended task Instance " + eventTask.getId());

        Task task = taskRepository.findById(taskSuspendedEvent.getEntity().getId())
                .orElseThrow(() -> new QueryException("Unable to find task with id: " + eventTask.getId()));
        task.setStatus(Task.TaskStatus.SUSPENDED);
        task.setLastModified(new Date(taskSuspendedEvent.getTimestamp()));
        taskRepository.save(task);
    }

    @Override
    public String getHandledEvent() {
        return TaskRuntimeEvent.TaskEvents.TASK_SUSPENDED.name();
    }
}
