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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.cloud.services.query.app.repository.elastic.DocumentFinder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class VariableUpdater {

	private static final String VARIABLES_FIELD = "variables";
	
	private ProcessInstanceRepository processInstanceRepository;
	private TaskRepository taskRepository;
	private ObjectMapper objectMapper;
	private ESIndexesConfiguration esIndexesConfiguration;
	private Client esClient;
	private DocumentFinder documentFinder;

	@Autowired
	public VariableUpdater(ProcessInstanceRepository processInstanceRepository, TaskRepository taskRepository,
			ObjectMapper objectMapper, ESIndexesConfiguration esIndexesConfiguration, Client esClient,
			DocumentFinder documentFinder) {
		this.processInstanceRepository = processInstanceRepository;
		this.taskRepository = taskRepository;
		this.objectMapper = objectMapper;
		this.esIndexesConfiguration = esIndexesConfiguration;
		this.esClient = esClient;
		this.documentFinder = documentFinder;
	}

	public void updateVariable(Variable updatedVariableEntity) {
		String variableName = updatedVariableEntity.getName();
		String processInstanceId = updatedVariableEntity.getProcessInstanceId();
		String taskId = updatedVariableEntity.getTaskId();
		String variableType = updatedVariableEntity.getType();
		boolean isTaskVariable = updatedVariableEntity.isTaskVariable();

		String partialExceptionMessage = isTaskVariable ? " from task with the given id: " + taskId
				: " from process instance with the given id: " + processInstanceId;

		Map<String, Set<Variable>> variables = findVariablesFromParent(updatedVariableEntity);

		Set<Variable> variablesByType = variables.get(updatedVariableEntity.getType());
		if (CollectionUtils.isEmpty(variablesByType)) {
			throw new QueryException("Unable to find variables for type: " + variableType + partialExceptionMessage);
		}

		if (!variablesByType.contains(updatedVariableEntity)) {
			throw new QueryException("Unable to find variable with name: " + variableName + partialExceptionMessage);
		}

		for (Variable var : variablesByType) {
			if (var.equals(updatedVariableEntity)) {
				var.setLastUpdatedTime(updatedVariableEntity.getLastUpdatedTime());
				var.setValue(updatedVariableEntity.getValue());
				break;
			}
		}

		UpdateRequest updateRequest = getUpdateRequest(updatedVariableEntity);
		try {
			updateVariables(updateRequest, variables);
		} catch (Exception e) {
			throw new QueryException("Unable to update variable with name: " + variableName + partialExceptionMessage,
					e);
		}
	}

	private Map<String, Set<Variable>> findVariablesFromParent(VariableInstance variableInstance) {
		Map<String, Set<Variable>> variables;
		if (variableInstance.isTaskVariable()) {
			Task task = documentFinder.findById(taskRepository, variableInstance.getTaskId(),
					"Unable to find task with the given id: " + variableInstance.getTaskId());
			variables = task.getVariables();
		} else {
			ProcessInstance processInstance = documentFinder.findById(processInstanceRepository,
					variableInstance.getProcessInstanceId(),
					"Unable to find process instance with the given id: " + variableInstance.getProcessInstanceId());
			variables = processInstance.getVariables();
		}

		return variables == null ? new HashMap<>() : variables;
	}

	private void updateVariables(UpdateRequest updateRequest, Map<String, Set<Variable>> variables)
			throws InterruptedException, ExecutionException, JsonProcessingException {
		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.set(VARIABLES_FIELD, objectMapper.valueToTree(variables));

		updateRequest.doc(objectMapper.writeValueAsString(objectNode), XContentType.JSON);
		esClient.update(updateRequest).get();
	}

	private UpdateRequest getUpdateRequest(VariableInstance variableInstance) {
		UpdateRequest updateRequest = new UpdateRequest();
		if (!variableInstance.isTaskVariable()) {
			updateRequest.index(esIndexesConfiguration.getProcessInstanceIndex());
			updateRequest.type(esIndexesConfiguration.getProcessInstanceDocumentType());
			updateRequest.id(variableInstance.getProcessInstanceId());
			return updateRequest;
		}

		updateRequest.index(esIndexesConfiguration.getTaskIndex());
		updateRequest.type(esIndexesConfiguration.getTaskDocumentType());
		updateRequest.id(variableInstance.getTaskId());
		return updateRequest;
	}

	public void markVariableAsDeleted(VariableInstance variableInstance) {
		String taskId = variableInstance.getTaskId();
		String processInstanceId = variableInstance.getProcessInstanceId();
		String variableName = variableInstance.getName();
		boolean isTaskVariable = variableInstance.isTaskVariable();

		Map<String, Set<Variable>> variables = findVariablesFromParent(variableInstance);
		Set<Variable> variablesByType = variables.get(variableInstance.getType());
		if (CollectionUtils.isEmpty(variablesByType)) {
			String emptyVariablesByTypeExceptionMessage = isTaskVariable
					? "Task with the given id: '" + taskId + "' has no variables"
					: "ProcessInstance with the given id: '" + processInstanceId + "' has no variables";
			throw new QueryException(emptyVariablesByTypeExceptionMessage);
		}

		boolean variableFound = false;
		for (Variable variable : variablesByType) {
			if (variableName.equals(variable.getName())) {
				variable.setMarkedAsDeleted(true);
				variableFound = true;
				break;
			}
		}

		if (!variableFound) {
			String variableNotFoundExceptionMessage = isTaskVariable
					? "Unable to find variable with  name '" + variableName + "' from task '" + taskId + "'"
					: "Unable to find variable with  name '" + variableName + "' from process instance '"
							+ processInstanceId + "'";
			throw new QueryException(variableNotFoundExceptionMessage);
		}

		UpdateRequest updateRequest = getUpdateRequest(variableInstance);
		try {
			updateVariables(updateRequest, variables);
		} catch (Exception e) {
			String updateErrorExceptionMessage = isTaskVariable
					? "Unable to mark as deleted variable with name: " + variableName + " from task with the given id: "
							+ taskId
					: "Unable to mark as deleted variable with name: " + variableName
							+ " from process instance with the given id: " + processInstanceId;
			throw new QueryException(updateErrorExceptionMessage, e);
		}
	}

}
