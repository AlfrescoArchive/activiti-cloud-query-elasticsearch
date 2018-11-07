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

import org.activiti.api.task.model.events.TaskRuntimeEvent;
import org.activiti.cloud.api.model.shared.events.CloudRuntimeEvent;
import org.activiti.cloud.api.task.model.events.CloudTaskCreatedEvent;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.QueryException;
import org.activiti.cloud.services.query.model.elastic.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskCreatedEventHandler implements QueryEventHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskCreatedEventHandler.class);

	private TaskRepository taskRepository;

	public TaskCreatedEventHandler(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	@Override
	public void handle(CloudRuntimeEvent<?, ?> event) {
		CloudTaskCreatedEvent taskCreatedEvent = (CloudTaskCreatedEvent) event;
		LOGGER.debug("Handling created task Instance " + taskCreatedEvent.getEntity().getId());

		org.activiti.api.task.model.Task eventEntity = taskCreatedEvent.getEntity();
		Task queryTaskEntity = new Task(eventEntity.getId(),
				eventEntity.getAssignee(),
				eventEntity.getName(),
				eventEntity.getDescription(),
				eventEntity.getCreatedDate(),
				eventEntity.getDueDate(),
				eventEntity.getPriority(),
				null,
				eventEntity.getProcessDefinitionId(),
				eventEntity.getProcessInstanceId(),
				event.getServiceName(),
				event.getServiceFullName(),
				event.getServiceVersion(),
				event.getAppName(),
				event.getAppVersion(),
				Task.TaskStatus.CREATED,
				eventEntity.getCreatedDate(),
				eventEntity.getClaimedDate(),
				eventEntity.getOwner(),
				eventEntity.getParentTaskId());

		persistIntoDatabase(event, queryTaskEntity);
	}

	private void persistIntoDatabase(CloudRuntimeEvent<?, ?> event, Task queryTaskEntity) {
		try {
			taskRepository.save(queryTaskEntity);
		} catch (Exception cause) {
			throw new QueryException("Error handling TaskCreatedEvent[" + event + "]", cause);
		}
	}

	@Override
	public String getHandledEvent() {
		return TaskRuntimeEvent.TaskEvents.TASK_CREATED.name();
	}
}
