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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.activiti.api.model.shared.model.VariableInstance;
import org.activiti.api.runtime.model.impl.VariableInstanceImpl;
import org.activiti.cloud.api.model.shared.impl.events.CloudVariableDeletedEventImpl;
import org.activiti.cloud.services.query.app.repository.elastic.DocumentFinder;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.Task;
import org.activiti.cloud.services.query.model.elastic.Variable;
import org.activiti.cloud.services.query.rest.config.ESIndexesConfiguration;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskVariableDeletedEventHandlerTest {

	private TaskVariableDeletedEventHandler handler;

	private VariableUpdater variableUpdater;

	@Mock
	private ProcessInstanceRepository processInstanceRepository;

	@Mock
	private TaskRepository taskRepository;

	@Spy
	private ObjectMapper objectMapper;

	@Mock
	private ESIndexesConfiguration esIndexesConfiguration;

	@Mock
	private Client esClient;

	@Mock
	private DocumentFinder documentFinder;

	@Before
	public void setUp() {
		initMocks(this);
		variableUpdater = spy(new VariableUpdater(processInstanceRepository, taskRepository, objectMapper,
				esIndexesConfiguration, esClient, documentFinder));
		handler = new TaskVariableDeletedEventHandler(variableUpdater);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void handleShouldSoftDeleteIt() throws JsonProcessingException, InterruptedException, ExecutionException {
		// given
		VariableInstanceImpl<String> variableInstance = new VariableInstanceImpl<>("var", "string", "v1",
				UUID.randomUUID().toString());
		variableInstance.setTaskId(UUID.randomUUID().toString());
		CloudVariableDeletedEventImpl event = new CloudVariableDeletedEventImpl(variableInstance);

		Task task = buildTask(variableInstance);
		Variable variable = task.getVariables().get(variableInstance.getType()).iterator().next();
		String notFoundMessage = "Unable to find task with the given id: " + variableInstance.getTaskId();
		when(documentFinder.findById(taskRepository, variableInstance.getTaskId(), notFoundMessage)).thenReturn(task);

		ActionFuture<UpdateResponse> actionFuture = mock(ActionFuture.class);
		UpdateResponse updateResponse = mock(UpdateResponse.class);
		when(actionFuture.get()).thenReturn(updateResponse);
		when(esClient.update(ArgumentMatchers.any(UpdateRequest.class))).thenReturn(actionFuture);

		// when
		handler.handle(event);

		// then
		verify(variableUpdater).markVariableAsDeleted(variableInstance);
		assertThat(variable.getMarkedAsDeleted()).isTrue();
	}

	private Task buildTask(VariableInstance variableInstance) {
		Task task = new Task();
		Map<String, Set<Variable>> variables = new HashMap<>();

		Variable variable = new Variable();
		variable.setProcessInstanceId(variableInstance.getProcessInstanceId());
		variable.setName(variableInstance.getName());
		variable.setType(variableInstance.getType());
		variable.setTaskId(variableInstance.getTaskId());
		variable.setValue(variableInstance.getValue());

		Set<Variable> variablesSet = new HashSet<>();
		variablesSet.add(variable);
		variables.put(variable.getType(), variablesSet);
		task.setVariables(variables);

		return task;
	}
}