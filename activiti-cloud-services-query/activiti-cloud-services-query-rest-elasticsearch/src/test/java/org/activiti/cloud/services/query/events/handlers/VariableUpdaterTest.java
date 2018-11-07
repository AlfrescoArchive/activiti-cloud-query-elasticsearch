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

import static org.activiti.test.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.activiti.cloud.services.query.app.repository.elastic.DocumentFinder;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import com.fasterxml.jackson.databind.ObjectMapper;

public class VariableUpdaterTest {

	@InjectMocks
	private VariableUpdater updater;

	@Mock
	private ProcessInstanceRepository processInstanceRepository;
	@Mock
	private TaskRepository taskRepository;
	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();
	@Mock
	private ESIndexesConfiguration esIndexesConfiguration;
	@Mock
	private Client esClient;
	@Mock
	private DocumentFinder documentFinder;

	@Before
	public void setUp() {
		initMocks(this);
	}

	@Test
	public void updateVariableShouldUpdateVariableRetrievedFromProcessInstanceRepository()
			throws InterruptedException, ExecutionException {
		// given
		ProcessInstance processInstance = buildProcessInstance();
		String processInstanceId = processInstance.getId();

		Variable currentVariable = processInstance.getVariables().get("string").iterator().next();

		Date now = new Date();
		Variable updatedVariable = buildUpdatedVariable(currentVariable, now);
		updatedVariable.setProcessInstanceId(processInstanceId);

		String notFoundMessage = "Unable to find process instance with the given id: " + processInstanceId;
		when(documentFinder.findById(processInstanceRepository, processInstanceId, notFoundMessage))
				.thenReturn(processInstance);

		setUpUpdate();

		// when
		updater.updateVariable(updatedVariable);

		// then
		assertThat(currentVariable).hasType("string").hasValue("content").hasLastUpdatedTime(now);
		verify(esClient).update(ArgumentMatchers.any(UpdateRequest.class));
	}

	private ProcessInstance buildProcessInstance() {
		String processInstanceId = "processInstanceId";
		ProcessInstance processInstance = new ProcessInstance();
		processInstance.setId(processInstanceId);
		Map<String, Set<Variable>> variables = new HashMap<>();

		Variable variable = buildVariable(processInstanceId, null);

		Set<Variable> variablesSet = new HashSet<>();
		variablesSet.add(variable);
		variables.put(variable.getType(), variablesSet);
		processInstance.setVariables(variables);

		return processInstance;
	}

	private Variable buildVariable(String processInstanceId, String taskId) {
		Variable variable = new Variable();
		variable.setProcessInstanceId(processInstanceId);
		variable.setTaskId(taskId);
		variable.setName("var");
		variable.setType("string");
		Calendar c = Calendar.getInstance();
		c.set(Calendar.YEAR, 1986);
		c.set(Calendar.MONTH, 6);
		c.set(Calendar.DAY_OF_MONTH, 15);
		variable.setLastUpdatedTime(c.getTime());
		variable.setValue("oldValue");

		return variable;
	}

	private Variable buildUpdatedVariable(Variable currentVariable, Date now) {
		Variable updatedVariable = new Variable();
		updatedVariable.setType(currentVariable.getType());
		updatedVariable.setValue("content");
		updatedVariable.setLastUpdatedTime(now);
		updatedVariable.setName(currentVariable.getName());
		return updatedVariable;
	}

	@SuppressWarnings("unchecked")
	private void setUpUpdate() throws InterruptedException, ExecutionException {
		ActionFuture<UpdateResponse> actionFuture = mock(ActionFuture.class);
		UpdateResponse updateResponse = mock(UpdateResponse.class);
		when(actionFuture.get()).thenReturn(updateResponse);
		when(esClient.update(ArgumentMatchers.any(UpdateRequest.class))).thenReturn(actionFuture);
	}

	@Test
	public void updateVariableShouldUpdateVariableRetrievedFromTaskRepository()
			throws InterruptedException, ExecutionException {
		// given
		Task task = buildTask();
		String taskId = task.getId();

		Variable currentVariable = task.getVariables().get("string").iterator().next();

		Date now = new Date();
		Variable updatedVariable = buildUpdatedVariable(currentVariable, now);
		updatedVariable.setTaskId(taskId);

		String notFoundMessage = "Unable to find task with the given id: " + taskId;
		when(documentFinder.findById(taskRepository, taskId, notFoundMessage)).thenReturn(task);

		setUpUpdate();

		// when
		updater.updateVariable(updatedVariable);

		// then
		assertThat(currentVariable).hasType("string").hasValue("content").hasLastUpdatedTime(now);
		verify(esClient).update(ArgumentMatchers.any(UpdateRequest.class));
	}

	@Test
	public void markVariableAsDeletedShouldWorkForProcessInstance() throws InterruptedException, ExecutionException {
		// given
		ProcessInstance processInstance = buildProcessInstance();
		String processInstanceId = processInstance.getId();

		Variable currentVariable = processInstance.getVariables().get("string").iterator().next();

		Variable updatedVariable = buildUpdatedVariable(currentVariable, new Date());
		updatedVariable.setProcessInstanceId(processInstanceId);

		String notFoundMessage = "Unable to find process instance with the given id: " + processInstanceId;
		when(documentFinder.findById(processInstanceRepository, processInstanceId, notFoundMessage))
				.thenReturn(processInstance);

		setUpUpdate();

		// when
		updater.markVariableAsDeleted(updatedVariable);

		// then
		assertThat(currentVariable).hasMarkedAsDeleted(true);
		verify(esClient).update(ArgumentMatchers.any(UpdateRequest.class));
	}

	@Test
	public void markVariableAsDeletedShouldWorkForTask() throws InterruptedException, ExecutionException {
		// given
		Task task = buildTask();
		String taskId = task.getId();

		Variable currentVariable = task.getVariables().get("string").iterator().next();

		Variable updatedVariable = buildUpdatedVariable(currentVariable, new Date());
		updatedVariable.setTaskId(taskId);

		String notFoundMessage = "Unable to find task with the given id: " + taskId;
		when(documentFinder.findById(taskRepository, taskId, notFoundMessage)).thenReturn(task);

		setUpUpdate();

		// when
		updater.markVariableAsDeleted(updatedVariable);

		// then
		assertThat(currentVariable).hasMarkedAsDeleted(true);
		verify(esClient).update(ArgumentMatchers.any(UpdateRequest.class));
	}

	private Task buildTask() {
		String taskId = "taskId";
		Task task = new Task();
		task.setId(taskId);
		Map<String, Set<Variable>> variables = new HashMap<>();

		Variable variable = buildVariable(null, taskId);
		Set<Variable> variablesSet = new HashSet<>();
		variablesSet.add(variable);
		variables.put(variable.getType(), variablesSet);
		task.setVariables(variables);

		return task;
	}

}