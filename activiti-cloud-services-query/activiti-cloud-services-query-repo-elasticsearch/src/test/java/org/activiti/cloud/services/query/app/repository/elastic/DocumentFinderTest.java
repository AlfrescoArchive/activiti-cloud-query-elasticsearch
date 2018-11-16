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

package org.activiti.cloud.services.query.app.repository.elastic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Optional;

import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DocumentFinderTest {

    @InjectMocks
    private DocumentFinder documentFinder;

    @Mock
    private ProcessInstanceRepository repository;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void findByIdShouldReturnResultWhenIsPresent() throws Exception {
        //given
        String processInstanceId = "5";
        ProcessInstance processInstance = mock(ProcessInstance.class);
        given(repository.findById(processInstanceId)).willReturn(Optional.of(processInstance));

        //when
        ProcessInstance retrieveProcessInstance = documentFinder.findById(repository,
                                                                                    processInstanceId,
                                                                                    "error");

        //then
        assertThat(retrieveProcessInstance).isEqualTo(processInstance);
    }

    @Test
    public void findByIdShouldThrowExceptionWhenNotPresent() throws Exception {
        //given
        String processInstanceId = "5";
        given(repository.findById(processInstanceId)).willReturn(Optional.empty());

        //then
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Error");

        //when
        documentFinder.findById(repository, processInstanceId, "Error");

    }

}