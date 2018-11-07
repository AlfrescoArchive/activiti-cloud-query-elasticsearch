package org.activiti.cloud.services.query.rest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("esIndexesConfiguration")
public class ESIndexesConfiguration {

	@Value("${elasticsearch.process.instance.index:process_instance}")
	private String processInstanceIndex;

	@Value("${elasticsearch.process.instance.document.type:_doc}")
	private String processInstanceDocumentType;

	@Value("${elasticsearch.task.index:task}")
	private String taskIndex;

	@Value("${elasticsearch.task.document.type:_doc}")
	private String taskDocumentType;

	public String getProcessInstanceDocumentType() {
		return processInstanceDocumentType;
	}

	public String getProcessInstanceIndex() {
		return processInstanceIndex;
	}

	public String getTaskDocumentType() {
		return taskDocumentType;
	}

	public String getTaskIndex() {
		return taskIndex;
	}

}
