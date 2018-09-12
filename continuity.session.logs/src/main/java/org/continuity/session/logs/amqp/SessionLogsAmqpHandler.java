package org.continuity.session.logs.amqp;

import java.util.Date;

import org.continuity.api.amqp.AmqpApi;
import org.continuity.api.entities.artifact.SessionLogs;
import org.continuity.api.entities.config.ModularizationApproach;
import org.continuity.api.entities.config.ModularizationOptions;
import org.continuity.api.entities.config.TaskDescription;
import org.continuity.api.entities.links.ExternalDataLinkType;
import org.continuity.api.entities.links.LinkExchangeModel;
import org.continuity.api.entities.report.TaskError;
import org.continuity.api.entities.report.TaskReport;
import org.continuity.api.rest.RestApi;
import org.continuity.commons.storage.MixedStorage;
import org.continuity.session.logs.config.RabbitMqConfig;
import org.continuity.session.logs.managers.SessionLogsPipelineManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SessionLogsAmqpHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SessionLogsAmqpHandler.class);

	@Autowired
	@Qualifier("plainRestTemplate")
	private RestTemplate plainRestTemplate;

	@Autowired
	private RestTemplate eurekaRestTemplate;

	@Autowired
	private AmqpTemplate amqpTemplate;

	@Autowired
	private MixedStorage<SessionLogs> storage;

	@RabbitListener(queues = RabbitMqConfig.TASK_CREATE_QUEUE_NAME)
	public void createSessionLogs(TaskDescription task) {
		TaskReport report;
		String tag = task.getTag();
		String link = task.getSource().getExternalDataLinks().getLink();
		boolean useOpenXtrace = task.getSource().getExternalDataLinks().getLinkType().equals(ExternalDataLinkType.OPEN_XTRACE) ? true : false;
		boolean applyModularization = false;

		if (null != task.getModularizationOptions()) {
			ModularizationOptions modularizationOptions = task.getModularizationOptions();
			applyModularization = modularizationOptions.getModularizationApproach().equals(ModularizationApproach.SESSION_LOGS) ? true : false;
		}
		Date timestamp = task.getSource().getExternalDataLinks().getTimestamp();

		if ((tag == null) || (link == null) || (timestamp == null)) {
			LOGGER.error("Task {}: cannot create session logs for tag {}, link {}, and timestamp {}. All values are required!", task.getTaskId(), tag, link, timestamp);
			report = TaskReport.error(task.getTaskId(), TaskError.MISSING_SOURCE);
		} else {

			SessionLogsPipelineManager manager = new SessionLogsPipelineManager(link, tag, plainRestTemplate, eurekaRestTemplate);

			String sessionLog;

			if (applyModularization) {
				LOGGER.info("Task {}: Creating modularized session logs for tags {} from data {} ...", task.getTaskId(), task.getModularizationOptions().getServices().keySet(), link);
				sessionLog = manager.runPipeline(useOpenXtrace, task.getModularizationOptions().getServices());
			} else {
				LOGGER.info("Task {}: Creating session logs for tag {} from data {} ...", task.getTaskId(), tag, link);
				sessionLog = manager.runPipeline(useOpenXtrace);
			}
			String id = storage.put(new SessionLogs(task.getSource().getExternalDataLinks().getTimestamp(), sessionLog), tag);
			String sessionLink = RestApi.SessionLogs.GET.requestUrl(id).withoutProtocol().get();

			report = TaskReport.successful(task.getTaskId(), new LinkExchangeModel().getSessionLogsLinks().setLink(sessionLink).parent());

			LOGGER.info("Task {}: Session logs created for tag {} from data {}", task.getTaskId(), tag, link);
		}

		amqpTemplate.convertAndSend(AmqpApi.Global.EVENT_FINISHED.name(), AmqpApi.Global.EVENT_FINISHED.formatRoutingKey().of(RabbitMqConfig.SERVICE_NAME), report);
	}

}
