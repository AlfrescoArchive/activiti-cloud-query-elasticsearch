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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
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
import org.activiti.cloud.api.model.shared.events.CloudVariableDeletedEvent;
import org.activiti.cloud.api.model.shared.impl.events.CloudVariableDeletedEventImpl;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.activiti.cloud.services.query.model.elastic.Variable;
import org.elasticsearch.action.update.UpdateRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.fasterxml.jackson.core.JsonProcessingException;

public class ProcessVariableDeletedHandlerTest {

	@InjectMocks
	private ProcessVariableDeletedEventHandler handler;

	@Mock
	private ProcessInstanceRepository processInstanceRepository;

//	@Mock
//	private EntityFinder entityFinder;

	@Mock
	private VariableUpdater variableUpdater;

	@Before
	public void setUp() {
		initMocks(this);
	}

	@Test
	public void handleRemoveVariableFromProcessAndSoftDeleteIt()
			throws JsonProcessingException, InterruptedException, ExecutionException {
		// given
		CloudVariableDeletedEvent event = buildVariableDeletedEvent();
		VariableInstance variableInstance = event.getEntity();

		ProcessInstance processInstance = new ProcessInstance();
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
		processInstance.setVariables(variables);
		when(variableUpdater.findVariablesFromParent(variableInstance)).thenReturn(variables);

		UpdateRequest updateRequest = mock(UpdateRequest.class);
		when(variableUpdater.getUpdateRequest(variableInstance)).thenReturn(updateRequest);
		doCallRealMethod().when(variableUpdater).markVariableAsDeleted(variableInstance);

		// when
		handler.handle(event);

		// then
		verify(variableUpdater).markVariableAsDeleted(variableInstance);
		assertThat(variable.getMarkedAsDeleted()).isTrue();
	}

	private CloudVariableDeletedEvent buildVariableDeletedEvent() {
		return new CloudVariableDeletedEventImpl(
				new VariableInstanceImpl<>("var", "string", "test", UUID.randomUUID().toString()));
	}

}