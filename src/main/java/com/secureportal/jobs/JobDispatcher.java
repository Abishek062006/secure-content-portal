package com.secureportal.jobs;

import java.util.UUID;

/** Hands a saved job to whatever will run it: a RabbitMQ queue when it's on, a local thread pool otherwise. */
public interface JobDispatcher {

    void dispatch(UUID jobId);
}
