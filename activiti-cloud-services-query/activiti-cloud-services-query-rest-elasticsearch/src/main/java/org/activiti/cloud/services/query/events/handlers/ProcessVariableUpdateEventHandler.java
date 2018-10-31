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

import java.util.Map;
import java.util.Set;

import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.activiti.cloud.services.query.model.elastic.QueryException;
import org.activiti.cloud.services.query.model.elastic.Variable;
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
public class ProcessVariableUpdateEventHandler {

	private final VariableUpdater variableUpdater;
	private ProcessInstanceRepository processInstanceRepository;
	private ObjectMapper objectMapper;
	private Client esClient;

	@Autowired
	public ProcessVariableUpdateEventHandler(VariableUpdater variableUpdater,
			ProcessInstanceRepository processInstanceRepository, Client esClient, ObjectMapper objectMapper) {
		this.variableUpdater = variableUpdater;
		this.processInstanceRepository = processInstanceRepository;
		this.esClient = esClient;
		this.objectMapper = objectMapper;
	}

	public void handle(Variable updatedVariableEntity) {
		String variableName = updatedVariableEntity.getName();
		String processInstanceId = updatedVariableEntity.getProcessInstanceId();

		ProcessInstance processInstance;
		processInstance = processInstanceRepository.findById(processInstanceId).orElseThrow(
				() -> new QueryException("Unable to find process instance with the given id: " + processInstanceId));

		Map<String, Set<Variable>> variables = processInstance.getVariables();
		if (CollectionUtils.isEmpty(variables)) {
			throw new QueryException(
					"Unable to find variables for process instance with the given id: " + processInstanceId);
		}

		Set<Variable> variablesByType = variables.get(updatedVariableEntity.getType());
		if (CollectionUtils.isEmpty(variablesByType)) {
			throw new QueryException("Unable to find variables for type: " + variableName
					+ " from process instance with the given id: " + processInstanceId);
		}

		if (!variablesByType.contains(updatedVariableEntity)) {
			throw new QueryException("Unable to find variable with name: " + variableName
					+ " from process instance with the given id: " + processInstanceId);
		}

		variablesByType.remove(updatedVariableEntity);
		variablesByType.add(updatedVariableEntity);
		
		String indexName = "process_instance";
		String docType = "_doc";

		UpdateRequest updateRequest = new UpdateRequest();
		updateRequest.index(indexName);
		updateRequest.type(docType);
		updateRequest.id(processInstanceId);

		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.set("variables", objectMapper.valueToTree(variables));

		System.out.println("UPDATING VARS FROM UPDATE!!!: " + objectNode.toString());
		try {
			updateRequest.doc(objectMapper.writeValueAsString(objectNode), XContentType.JSON);
			esClient.update(updateRequest).get();
		} catch (Exception e) {
			throw new QueryException("Unable to update variable with name: " + variableName
					+ " from process instance with the given id: " + processInstanceId, e);
		}
	}
}
