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
import java.util.Optional;

import org.activiti.api.process.model.events.ProcessRuntimeEvent;
import org.activiti.cloud.api.model.shared.events.CloudRuntimeEvent;
import org.activiti.cloud.api.process.model.events.CloudProcessStartedEvent;
import org.activiti.cloud.services.query.app.repository.elastic.ProcessInstanceRepository;
import org.activiti.cloud.services.query.model.elastic.ProcessInstance;
import org.activiti.cloud.services.query.model.elastic.QueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessStartedEventHandler implements QueryEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessStartedEventHandler.class);

    private ProcessInstanceRepository processInstanceRepository;

    @Autowired
    public ProcessStartedEventHandler(ProcessInstanceRepository processInstanceRepository) {
        this.processInstanceRepository = processInstanceRepository;
    }

    @Override
    public void handle(CloudRuntimeEvent<?, ?> event) {
        CloudProcessStartedEvent startedEvent = (CloudProcessStartedEvent) event;
        String processInstanceId = startedEvent.getEntity().getId();
        LOGGER.debug("Handling process started Instance " + processInstanceId);

        Optional<ProcessInstance> findResult = processInstanceRepository.findById(processInstanceId);
        ProcessInstance processInstanceEntity = findResult.orElseThrow(
                () -> new QueryException("Unable to find process instance with the given id: " + processInstanceId));
        if (ProcessInstance.ProcessInstanceStatus.CREATED.equals(processInstanceEntity.getStatus())) {
            processInstanceEntity.setStatus(ProcessInstance.ProcessInstanceStatus.RUNNING);
            processInstanceEntity.setLastModified(new Date(startedEvent.getTimestamp()));
            processInstanceRepository.save(processInstanceEntity);
        }
    }

    @Override
    public String getHandledEvent() {
        return ProcessRuntimeEvent.ProcessEvents.PROCESS_STARTED.name();
    }
}
