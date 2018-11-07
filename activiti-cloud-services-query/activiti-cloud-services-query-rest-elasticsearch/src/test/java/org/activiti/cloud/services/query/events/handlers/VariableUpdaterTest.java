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

import static org.mockito.MockitoAnnotations.initMocks;

import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.app.repository.elastic.TaskRepository;
import org.activiti.cloud.services.query.rest.config.ESIndexesConfiguration;
import org.elasticsearch.client.Client;
import org.junit.Before;
import org.junit.Test;
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

    @Before
    public void setUp() {    	
        initMocks(this);
    }

    @Test
    public void updateVariablesShould() {
    	// given
    	
    	// when
//    	updater.findVariablesFromParent(variableInstance)
    	// then
    }
    
    @Test
    public void updateShouldUpdateVariableRetrievedByPredicate() {
//        //given
//        VariableEntity currentVariableEntity = new VariableEntity();
//
//        Predicate predicate = mock(Predicate.class);
//        given(entityFinder.findOne(variableRepository, predicate, "error")).willReturn(currentVariableEntity);
//
//        Date now = new Date();
//        VariableEntity updatedVariableEntity = new VariableEntity();
//        updatedVariableEntity.setType("string");
//        updatedVariableEntity.setValue("content");
//        updatedVariableEntity.setLastUpdatedTime(now);
//
//        //when
//        updater.update(updatedVariableEntity,
//                       predicate, "error");
//
//        //then
//        assertThat(currentVariableEntity)
//                .hasType("string")
//                .hasValue("content")
//                .hasLastUpdatedTime(now);
//        verify(variableRepository).save(currentVariableEntity);
    }

}