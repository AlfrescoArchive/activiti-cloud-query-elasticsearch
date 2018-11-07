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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.activiti.api.model.shared.event.VariableEvent;
import org.activiti.cloud.api.model.shared.events.CloudRuntimeEvent;
import org.activiti.cloud.api.model.shared.events.CloudVariableCreatedEvent;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.activiti.cloud.services.query.model.elastic.QueryException;
import org.activiti.cloud.services.query.model.elastic.Task;
import org.activiti.cloud.services.query.model.elastic.Variable;
import org.activiti.cloud.services.query.rest.config.ESIndexesConfiguration;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class VariableCreatedEventHandler implements QueryEventHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(VariableCreatedEventHandler.class);

	private static final String VARIABLES_FIELD = "variables";
	private final ProcessInstanceRepository processInstanceRepository;
	private final TaskRepository taskRepository;
	private Client esClient;
	private ESIndexesConfiguration esIndexesConfiguration;
	// TODO check if it is better to use
	// org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder() to get the
	// JSON representations when updating partially.
	private ObjectMapper objectMapper;

	@Autowired
	public VariableCreatedEventHandler(ProcessInstanceRepository processInstanceRepository,
			TaskRepository taskRepository, ElasticsearchTemplate esTemplate, Client esClient,
			ESIndexesConfiguration esIndexesConfiguration, ObjectMapper objectMapper) {
		this.processInstanceRepository = processInstanceRepository;
		this.taskRepository = taskRepository;
		this.esClient = esClient;
		this.esIndexesConfiguration = esIndexesConfiguration;
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(CloudRuntimeEvent<?, ?> event) {
		CloudVariableCreatedEvent variableCreatedEvent = (CloudVariableCreatedEvent) event;
		LOGGER.debug("Handling variableEntity created event: " + variableCreatedEvent.getEntity().getName());
		Variable variableEntity = new Variable(null, variableCreatedEvent.getEntity().getType(),
				variableCreatedEvent.getEntity().getName(), variableCreatedEvent.getEntity().getProcessInstanceId(),
				variableCreatedEvent.getServiceName(), variableCreatedEvent.getServiceFullName(),
				variableCreatedEvent.getServiceVersion(), variableCreatedEvent.getAppName(),
				variableCreatedEvent.getAppVersion(), variableCreatedEvent.getEntity().getTaskId(),
				new Date(variableCreatedEvent.getTimestamp()), new Date(variableCreatedEvent.getTimestamp()), null);
		variableEntity.setValue(variableCreatedEvent.getEntity().getValue());

		setProcessInstance(variableCreatedEvent, variableEntity);

		setTask(variableCreatedEvent, variableEntity);

		persist(event, variableEntity);
	}

	private void persist(CloudRuntimeEvent<?, ?> event, Variable variable) {
		try {
			if (variable.isTaskVariable()) {
				UpdateRequest updateRequest = getUpdateRequestForTask(variable);
				esClient.update(updateRequest).get();
				return;
			}

			UpdateRequest updateRequest = getUpdateRequestForProcessInstance(variable);
			esClient.update(updateRequest).get();

		} catch (Exception cause) {
			throw new QueryException("Error handling VariableCreatedEvent[" + event + "]", cause);
		}
	}

	private UpdateRequest getUpdateRequestForProcessInstance(Variable variable) throws JsonProcessingException {
		ProcessInstance processInstance = variable.getProcessInstance();
		String indexName = esIndexesConfiguration.getProcessInstanceIndex();
		String docType = esIndexesConfiguration.getProcessInstanceDocumentType();
		String docId = processInstance.getId();

		if (processInstance.getVariables() == null) {
			processInstance.setVariables(new HashMap<>());
		}

		Map<String, Set<Variable>> variables = processInstance.getVariables();
		removeExistingVariable(variable, variables);

		return generateUpdateRequest(indexName, docType, docId, variables);
	}

	private UpdateRequest generateUpdateRequest(String indexName, String docType, String docId,
			Map<String, Set<Variable>> variables) throws JsonProcessingException {
		UpdateRequest updateRequest = new UpdateRequest();
		updateRequest.index(indexName);
		updateRequest.type(docType);
		updateRequest.id(docId);

		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.set(VARIABLES_FIELD, objectMapper.valueToTree(variables));

		updateRequest.doc(objectMapper.writeValueAsString(objectNode), XContentType.JSON);
		return updateRequest;
	}

	private void removeExistingVariable(Variable variable, Map<String, Set<Variable>> variables) {
		String variableType = variable.getType();
		if (variables.get(variableType) == null) {
			variables.put(variableType, new HashSet<>());
		}

		Set<Variable> currentVariablesByType = variables.get(variableType);
		currentVariablesByType.remove(variable);
		currentVariablesByType.add(variable);
	}

	private UpdateRequest getUpdateRequestForTask(Variable variable) throws JsonProcessingException {
		Task task = variable.getTask();
		String indexName = esIndexesConfiguration.getTaskIndex();
		String docType = esIndexesConfiguration.getTaskDocumentType();
		String docId = task.getId();

		if (task.getVariables() == null) {
			task.setVariables(new HashMap<>());
		}

		Map<String, Set<Variable>> variables = task.getVariables();
		removeExistingVariable(variable, variables);

		return generateUpdateRequest(indexName, docType, docId, variables);
	}

	private void setTask(CloudVariableCreatedEvent variableCreatedEvent, Variable variableEntity) {
		if (variableCreatedEvent.getEntity().isTaskVariable()) {
			taskRepository.findById(variableCreatedEvent.getEntity().getTaskId())
					.ifPresent(task -> variableEntity.setTask(task));
		}
	}

	private void setProcessInstance(CloudVariableCreatedEvent variableCreatedEvent, Variable variableEntity) {
		processInstanceRepository.findById(variableCreatedEvent.getEntity().getProcessInstanceId())
				.ifPresent(processInstance -> variableEntity.setProcessInstance(processInstance));
	}

	@Override
	public String getHandledEvent() {
		return VariableEvent.VariableEvents.VARIABLE_CREATED.name();
	}
}
