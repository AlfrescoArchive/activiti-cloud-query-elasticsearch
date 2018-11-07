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
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.activiti.api.model.shared.event.VariableEvent;
import org.activiti.api.runtime.model.impl.VariableInstanceImpl;
import org.activiti.cloud.api.model.shared.impl.events.CloudVariableCreatedEventImpl;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.activiti.cloud.services.query.model.elastic.Task;
import org.activiti.cloud.services.query.model.elastic.Variable;
import org.activiti.cloud.services.query.rest.config.ESIndexesConfiguration;
import org.activiti.test.Assertions;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VariableCreatedEventHandlerTest {

    private static final String PROCESS_INSTANCE_INDEX = "process_instance";
    private static final String TASK_INDEX = "task";
    private static final String DOC_TYPE = "_doc";

    @InjectMocks
    private VariableCreatedEventHandler handler;

    @Mock
    private ProcessInstanceRepository processInstanceRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ESIndexesConfiguration esIndexesConfiguration;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Client esClient;

    @Before
    public void setUp() {
        // Next configuratuon id because variable instances have the field taskVariable
        // (from isTaskVariable)
        // that can't be mapped to any property...
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        initMocks(this);
    }

    @Test
    public void handleShouldCreateAndStoreProcessInstanceVariable() throws InterruptedException, ExecutionException {
        // given
        VariableInstanceImpl<String> variableInstance = buildVariable();
        CloudVariableCreatedEventImpl event = new CloudVariableCreatedEventImpl(variableInstance);

        setUpMocks(variableInstance, event);
        setUpUpdateExecution();
        // when
        handler.handle(event);

        // then
        Variable variableCreated = verifyUpdateAndGetVariableCreated(variableInstance, PROCESS_INSTANCE_INDEX);

        Assertions.assertThat(variableCreated).hasProcessInstanceId(event.getEntity().getProcessInstanceId())
                .hasName(event.getEntity().getName()).hasTaskId(event.getEntity().getTaskId())
                .hasType(event.getEntity().getType()).hasTask(null).hasProcessInstance(null);
    }

    private void setUpMocks(VariableInstanceImpl<String> variableInstance, CloudVariableCreatedEventImpl event)
            throws InterruptedException, ExecutionException {
        HashMap<String, Set<Variable>> variables = new HashMap<>();
        variables.put(variableInstance.getType(), new HashSet<>());
        ProcessInstance processInstance = mock(ProcessInstance.class);

        when(processInstance.getVariables()).thenReturn(variables);
        when(processInstanceRepository.findById(event.getEntity().getProcessInstanceId()))
                .thenReturn(Optional.of(processInstance));

        when(esIndexesConfiguration.getProcessInstanceDocumentType()).thenReturn(DOC_TYPE);
        when(esIndexesConfiguration.getTaskDocumentType()).thenReturn(DOC_TYPE);
        when(esIndexesConfiguration.getProcessInstanceIndex()).thenReturn(PROCESS_INSTANCE_INDEX);
        when(esIndexesConfiguration.getTaskIndex()).thenReturn(TASK_INDEX);
    }

    @SuppressWarnings("unchecked")
    private void setUpUpdateExecution() throws InterruptedException, ExecutionException {
        ActionFuture<UpdateResponse> actionFuture = mock(ActionFuture.class);
        UpdateResponse updateResponse = mock(UpdateResponse.class);

        when(actionFuture.get()).thenReturn(updateResponse);
        when(esClient.update(ArgumentMatchers.any(UpdateRequest.class))).thenReturn(actionFuture);
    }

    @Test
    public void handleShouldCreateAndStoreTaskVariable() throws InterruptedException, ExecutionException {
        // given
        VariableInstanceImpl<String> variableInstance = buildVariable();
        variableInstance.setTaskId(UUID.randomUUID().toString());
        CloudVariableCreatedEventImpl event = new CloudVariableCreatedEventImpl(variableInstance);

        setUpMocks(variableInstance, event);

        Task task = mock(Task.class);
        when(taskRepository.findById(event.getEntity().getTaskId())).thenReturn(Optional.of(task));

        setUpUpdateExecution();

        // when
        handler.handle(event);

        // then
        Variable variableCreated = verifyUpdateAndGetVariableCreated(variableInstance, TASK_INDEX);

        Assertions.assertThat(variableCreated).hasProcessInstanceId(event.getEntity().getProcessInstanceId())
                .hasName(event.getEntity().getName()).hasTaskId(event.getEntity().getTaskId())
                .hasType(event.getEntity().getType()).hasTask(null).hasProcessInstance(null);
    }

    private Variable verifyUpdateAndGetVariableCreated(VariableInstanceImpl<String> variableInstance,
            String indexName) {
        ArgumentCaptor<UpdateRequest> captor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(esClient).update(captor.capture());
        UpdateRequest updateRequest = captor.getValue();

        assertEquals(indexName, updateRequest.index());

        Map<String, Object> docSourceAsMap = updateRequest.doc().sourceAsMap();
        if (variableInstance.isTaskVariable()) {
            Task task = objectMapper.convertValue(docSourceAsMap, Task.class);
            return task.getVariables().get(variableInstance.getType()).iterator().next();
        }

        ProcessInstance processInstanceUpdated = objectMapper.convertValue(docSourceAsMap, ProcessInstance.class);
        return processInstanceUpdated.getVariables().get(variableInstance.getType()).iterator().next();
    }

    private VariableInstanceImpl<String> buildVariable() {
        return new VariableInstanceImpl<>("var", "string", "v1", UUID.randomUUID().toString());
    }

    @Test
    public void getHandledEventShouldReturnVariableCreatedEvent() {
        // when
        String handledEvent = handler.getHandledEvent();

        // then
        assertThat(handledEvent).isEqualTo(VariableEvent.VariableEvents.VARIABLE_CREATED.name());
    }
}